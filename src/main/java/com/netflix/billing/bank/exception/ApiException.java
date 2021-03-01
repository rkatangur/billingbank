package com.netflix.billing.bank.exception;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Represents often fatal errors that occur within the API infrastructure. All
 * service methods should be marked as possibly throwing this exception. The
 * preferred methods to use in this exception is the
 * {@link #APIException(String, Throwable)} or the {@link #APIException(String)}
 */
public class ApiException extends RuntimeException {

	public static final long serialVersionUID = 12121212L;

	private static final String DEFAULT_ERROR = "unknown_error";

	static final int DEFAULT_STATUS = 400;

	private static final String ERROR = "error";

	private static final String DESCRIPTION = "error_description";

	private static final String STATUS = "status";

	private Map<String, String> additionalInformation = null;

	private final int status;

	private final String error;

	public ApiException(String msg, Throwable t) {
		super(msg, t);
		this.error = DEFAULT_ERROR;
		this.status = DEFAULT_STATUS;
	}

	public ApiException(String msg) {
		this(DEFAULT_ERROR, msg, 400);
	}

	public ApiException(String msg, int status) {
		this(DEFAULT_ERROR, msg, status);
	}

	public ApiException(String error, String description, int status) {
		super(description);
		this.error = error;
		this.status = status;
	}

	public ApiException(Throwable cause, String error, String description, int status) {
		super(description, cause);
		this.error = error;
		this.status = status;
	}

	/**
	 * The error code.
	 *
	 * @return The error code.
	 */
	public String getErrorCode() {
		return error;
	}

	/**
	 * The HTTP status associated with this error.
	 *
	 * @return The HTTP status associated with this error.
	 */
	public int getHttpStatus() {
		return status;
	}

	/**
	 * Get any additional information associated with this error.
	 *
	 * @return Additional information, or null if none.
	 */
	public Map<String, String> getAdditionalInformation() {
		return this.additionalInformation;
	}

	/**
	 * Add some additional information with this OAuth error.
	 *
	 * @param key   The key.
	 * @param value The value.
	 */
	public void addAdditionalInformation(String key, String value) {
		if (this.additionalInformation == null) {
			this.additionalInformation = new TreeMap<String, String>();
		}

		this.additionalInformation.put(key, value);

	}

	/**
	 * Creates an {@link UaaException} from a {@link Map}.
	 *
	 * @param errorParams a map with additional error information
	 * @return the exception with error information
	 */
	public static ApiException valueOf(Map<String, String> errorParams) {
		String errorCode = errorParams.get(ERROR);
		String errorMessage = errorParams.containsKey(DESCRIPTION) ? errorParams.get(DESCRIPTION) : null;
		int status = DEFAULT_STATUS;
		if (errorParams.containsKey(STATUS)) {
			try {
				status = Integer.valueOf(errorParams.get(STATUS));
			} catch (NumberFormatException e) {
				// ignore
			}
		}
		ApiException ex = new ApiException(errorCode, errorMessage, status);
		Set<Map.Entry<String, String>> entries = errorParams.entrySet();
		for (Map.Entry<String, String> entry : entries) {
			String key = entry.getKey();
			if (!ERROR.equals(key) && !DESCRIPTION.equals(key)) {
				ex.addAdditionalInformation(key, entry.getValue());
			}
		}

		return ex;
	}
}
