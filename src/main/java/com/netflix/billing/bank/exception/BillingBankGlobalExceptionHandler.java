package com.netflix.billing.bank.exception;

import java.nio.file.AccessDeniedException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.util.InvalidMimeTypeException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@Order(Ordered.HIGHEST_PRECEDENCE)
@ControllerAdvice
public class BillingBankGlobalExceptionHandler extends ResponseEntityExceptionHandler {

	private Logger log = LoggerFactory.getLogger(getClass());

	/**
	 * Handle MissingServletRequestParameterException. Triggered when a 'required'
	 * request parameter is missing.
	 *
	 * @param ex      MissingServletRequestParameterException
	 * @param headers HttpHeaders
	 * @param status  HttpStatus
	 * @param request WebRequest
	 * @return the ApiError object
	 */
	@Override
	protected ResponseEntity<Object> handleMissingServletRequestParameter(MissingServletRequestParameterException ex,
			HttpHeaders headers, HttpStatus status, WebRequest request) {
		final ApiError apiError = message(HttpStatus.BAD_REQUEST, ex);
		return handleExceptionInternal(ex, apiError, headers, apiError.getStatus(), request);
	}

	/**
	 * Handle HttpMediaTypeNotSupportedException. This one triggers when JSON is
	 * invalid as well.
	 *
	 * @param ex      HttpMediaTypeNotSupportedException
	 * @param headers HttpHeaders
	 * @param status  HttpStatus
	 * @param request WebRequest
	 * @return the ApiError object
	 */
	@Override
	protected ResponseEntity<Object> handleHttpMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex,
			HttpHeaders headers, HttpStatus status, WebRequest request) {
		StringBuilder builder = new StringBuilder();
		builder.append(ex.getContentType());
		builder.append(" media type is not supported. Supported media types are ");
		ex.getSupportedMediaTypes().forEach(t -> builder.append(t).append(", "));

		final ApiError apiError = message(HttpStatus.UNSUPPORTED_MEDIA_TYPE, builder.substring(0, builder.length() - 2),
				ex);
		return handleExceptionInternal(ex, apiError, headers, apiError.getStatus(), request);
	}

	/**
	 * Handle MethodArgumentNotValidException. Triggered when an object fails @Valid
	 * validation.
	 *
	 * @param ex      the MethodArgumentNotValidException that is thrown when @Valid
	 *                validation fails
	 * @param headers HttpHeaders
	 * @param status  HttpStatus
	 * @param request WebRequest
	 * @return the ApiError object
	 */
	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
			HttpHeaders headers, HttpStatus status, WebRequest request) {
		log.info("Bad Request: {}", ex.getMessage());
		log.debug("Bad Request: ", ex);

		ApiError apiError = buildApiError("Validation error", ex);
		apiError.addValidationErrors(ex.getBindingResult().getFieldErrors());
		apiError.addValidationError(ex.getBindingResult().getGlobalErrors());

		return handleExceptionInternal(ex, apiError, headers, apiError.getStatus(), request);
	}

	/**
	 * Handles javax.validation.ConstraintViolationException. Thrown when @Validated
	 * fails.
	 *
	 * @param ex the ConstraintViolationException
	 * @return the ApiError object
	 */
	@ExceptionHandler(javax.validation.ConstraintViolationException.class)
	protected ResponseEntity<Object> handleConstraintViolation(javax.validation.ConstraintViolationException ex,
			final WebRequest request) {
		ApiError apiError = buildApiError("Validation error", ex);
		apiError.addValidationErrors(ex.getConstraintViolations());

		return handleExceptionInternal(ex, apiError, new HttpHeaders(), apiError.getStatus(), request);
	}

	/**
	 * Handle HttpMessageNotReadableException. Happens when request JSON is
	 * malformed.
	 *
	 * @param ex      HttpMessageNotReadableException
	 * @param headers HttpHeaders
	 * @param status  HttpStatus
	 * @param request WebRequest
	 * @return the ApiError object
	 */
	@Override
	protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
			HttpHeaders headers, HttpStatus status, WebRequest request) {
		ServletWebRequest servletWebRequest = (ServletWebRequest) request;
		log.info("{} to {}", servletWebRequest.getHttpMethod(), servletWebRequest.getRequest().getServletPath());
		String error = "Malformed JSON request";

		ApiError apiRespose = message(HttpStatus.BAD_REQUEST, error, ex);

		return handleExceptionInternal(ex, apiRespose, new HttpHeaders(), apiRespose.getStatus(), request);
	}

	/**
	 * Handle HttpMessageNotWritableException.
	 *
	 * @param ex      HttpMessageNotWritableException
	 * @param headers HttpHeaders
	 * @param status  HttpStatus
	 * @param request WebRequest
	 * @return the ApiError object
	 */
	@Override
	protected ResponseEntity<Object> handleHttpMessageNotWritable(HttpMessageNotWritableException ex,
			HttpHeaders headers, HttpStatus status, WebRequest request) {
		String error = "Error writing JSON output";
		ApiError apiRespose = message(HttpStatus.INTERNAL_SERVER_ERROR, error, ex);

		return handleExceptionInternal(ex, apiRespose, new HttpHeaders(), apiRespose.getStatus(), request);
	}

	/**
	 * Handle NoHandlerFoundException.
	 *
	 * @param ex
	 * @param headers
	 * @param status
	 * @param request
	 * @return
	 */
	@Override
	protected ResponseEntity<Object> handleNoHandlerFoundException(NoHandlerFoundException ex, HttpHeaders headers,
			HttpStatus status, WebRequest request) {
		String message = String.format("Could not find the %s method for URL %s", ex.getHttpMethod(),
				ex.getRequestURL());
		ApiError apiRespose = message(HttpStatus.BAD_REQUEST, message, ex);

		return handleExceptionInternal(ex, apiRespose, new HttpHeaders(), apiRespose.getStatus(), request);
	}

	/**
	 * Handle Exception, handle generic Exception.class
	 *
	 * @param ex the Exception
	 * @return the ApiError object
	 */
	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	protected ResponseEntity<Object> handleMethodArgumentTypeMismatch(MethodArgumentTypeMismatchException ex,
			WebRequest request) {
		String message = String.format("The parameter '%s' of value '%s' could not be converted to type '%s'",
				ex.getName(), ex.getValue(), ex.getRequiredType().getSimpleName());

		final ApiError apiResponse = message(HttpStatus.BAD_REQUEST, message, ex);
		return handleExceptionInternal(ex, apiResponse, new HttpHeaders(), apiResponse.getStatus(), request);
	}

	@ExceptionHandler({ AccessDeniedException.class })
	public ResponseEntity<Object> handleEverything(final AccessDeniedException ex, final WebRequest request) {
		logger.error("403 Status Code", ex);

		final ApiError apiResponse = message(HttpStatus.FORBIDDEN, ex);

		return handleExceptionInternal(ex, apiResponse, new HttpHeaders(), HttpStatus.FORBIDDEN, request);
	}

	@ExceptionHandler({ InvalidMimeTypeException.class, InvalidMediaTypeException.class })
	protected ResponseEntity<Object> handleInvalidMimeTypeException(final IllegalArgumentException ex,
			final WebRequest request) {
		log.warn("Unsupported Media Type: {}", ex.getMessage());

		final ApiError apiResponse = message(HttpStatus.UNSUPPORTED_MEDIA_TYPE, ex);
		return handleExceptionInternal(ex, apiResponse, new HttpHeaders(), HttpStatus.UNSUPPORTED_MEDIA_TYPE, request);
	}

	// 500
	@ExceptionHandler({ NullPointerException.class, IllegalArgumentException.class, IllegalStateException.class })
	public ResponseEntity<Object> handle500s(final RuntimeException ex, final WebRequest request) {
		logger.error("500 Status Code", ex);

		final ApiError apiResponse = message(HttpStatus.INTERNAL_SERVER_ERROR, ex);

		return handleExceptionInternal(ex, apiResponse, new HttpHeaders(), HttpStatus.INTERNAL_SERVER_ERROR, request);
	}

	private ApiError message(HttpStatus httpStatus, String customMessage, Throwable ex) {
		ApiError apiError = buildApiError(customMessage, ex);
//		ApiResponse apiResponse = new ApiResponse();
		apiError.setStatus(httpStatus);
//		apiResponse.setErrors(Arrays.asList(apiError));
		return apiError;
	}

	private ApiError buildApiError(String customMessage, Throwable ex) {
		String msgFromExp = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
		String message = (customMessage == null) ? msgFromExp : customMessage;
//		String debugMessage = ExceptionUtils.getStackTrace(ex);		
		ApiError apiError = new ApiError(message, ex);
//		apiError.setDebugMessage(debugMessage);
		return apiError;
	}

	protected ApiError message(HttpStatus httpStatus, Throwable ex) {
		return message(httpStatus, null, ex);
	}

}