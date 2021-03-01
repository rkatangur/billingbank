package com.netflix.billing.bank.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.billing.bank.controller.wire.CreditAmount;
import com.netflix.billing.bank.controller.wire.CreditType;
import com.netflix.billing.bank.controller.wire.DebitAmount;
import com.netflix.billing.bank.controller.wire.DebitLineItem;
import com.netflix.billing.bank.controller.wire.Money;
import com.netflix.billing.bank.exception.ApiException;

/**
 * 
 * CustomerAccount with the balance
 * 
 * @author rkata
 *
 */
public class CustomerAccount {

	private static final Logger LOGGER = LoggerFactory.getLogger(CustomerAccount.class);

	private final String customerId;

	// build a key of currency to CustomerAccountBalance
	private final ConcurrentHashMap<String, CustomerAccountByCurrency> custActBalMapByCurrency = new ConcurrentHashMap<>();

	// Recording a key of customerid+credittype+transactionid ---> CreditRequest to
	// store the CreditAmount request that is tied to a request.
	private final ConcurrentHashMap<String, BankingTransaction> processedCredits = new ConcurrentHashMap<>();

	// Recording a key of customerid+invoiceid ---> DebitAmount to store all debits
	// that are processed.
	// store the DebitAmount request that is tied to the request.
	private final ConcurrentHashMap<String, BankingTransaction> processedDebits = new ConcurrentHashMap<>();

	public CustomerAccount(String customerId) {
		this.customerId = customerId;
	}

	/**
	 * Need to be called in a thread safe manner from the bank store.
	 * 
	 * @param creditReq
	 */
	public void processCredit(CreditAmount creditReq, BankingTransaction curTransaction) {

		TransactionStatus status = TransactionStatus.RECEIVED;
		try {
			if (creditReq != null) {
				CustomerAccountByCurrency custActBalByCur = getOrCreateCustomerAccountBalance(customerId,
						creditReq.getMoney().getCurrency());
				custActBalByCur.processCredit(creditReq, curTransaction);
			}
			status = TransactionStatus.SUCESS;
		} catch (Exception e) {
			status = TransactionStatus.FAILURE;
		} finally {
			curTransaction.setStatus(status);
		}
	}

	/**
	 * Need to be called in a thread safe manner from the bank store.
	 * 
	 * @param creditReq
	 */
	public void processDebit(DebitAmount debitReq, BankingTransaction curTransaction) {
		TransactionStatus status = TransactionStatus.RECEIVED;
		try {
			if (debitReq != null && debitReq.getMoney() != null) {
				CustomerAccountByCurrency custActBal = custActBalMapByCurrency.get(debitReq.getMoney().getCurrency());
				if (custActBal != null) {
					custActBal.processDebit(debitReq, curTransaction);
					status = TransactionStatus.SUCESS;
				} else {
					status = TransactionStatus.FAILURE;
					String errorMsg = String.format(
							"No credits avialble to process debit request with currency  for customer %s, currency %s",
							customerId, debitReq.getMoney().getCurrency());
					LOGGER.error(errorMsg);
					throw new ApiException(errorMsg);
				}
			}
		} catch (Exception e) {
			status = TransactionStatus.FAILURE;
			throw e;
		} finally {
			curTransaction.setStatus(status);
		}
	}

	public CustomerAccountByCurrency getOrCreateCustomerAccountBalance(String customerId, String currency) {
		CustomerAccountByCurrency custActBal = custActBalMapByCurrency.get(currency);

		if (custActBal == null) {
			LOGGER.info(
					"Creating a new CustomerAccountByCurrency for customerId " + customerId + ", currency " + currency);
			custActBal = new CustomerAccountByCurrency(customerId, currency);
			CustomerAccountByCurrency custActBalInMap = custActBalMapByCurrency.putIfAbsent(currency, custActBal);

			if (custActBalInMap != null) {
				custActBal = custActBalInMap;
			}
		}

		return custActBal;
	}

	/**
	 * Computes the account balance by using CustomerAccountByCurrency which
	 * maintains an aggregated balance by currency, creditType
	 * 
	 * @return
	 */
	public Map<CreditType, List<Money>> getAccountBalance() {

		Map<CreditType, List<Money>> custActBalanceByCreditType = new HashMap<>();

		for (Map.Entry<String, CustomerAccountByCurrency> custActByCurrencyEntry : custActBalMapByCurrency.entrySet()) {
			String currency = custActByCurrencyEntry.getKey();
			CustomerAccountByCurrency custActByCurrency = custActByCurrencyEntry.getValue();

			for (Map.Entry<CreditType, Long> creditTypeBalEntry : custActByCurrency.getBalance().entrySet()) {
				CreditType creditType = creditTypeBalEntry.getKey();

				List<Money> cTypeMoney = custActBalanceByCreditType.get(creditType);
				if (cTypeMoney == null) {
					cTypeMoney = new ArrayList<>();
					custActBalanceByCreditType.put(creditType, cTypeMoney);
				}
				// record the balance now.
				cTypeMoney.add(new Money(creditTypeBalEntry.getValue(), currency));
			}
		}

		return custActBalanceByCreditType;
	}

	public String getCustomerId() {
		return customerId;
	}

	public List<DebitLineItem> getProcessedDebits() {
		List<DebitLineItem> debitLineItems = new ArrayList<DebitLineItem>();
		for (Map.Entry<String, CustomerAccountByCurrency> custActByCurrencyEntry : custActBalMapByCurrency.entrySet()) {
			String currency = custActByCurrencyEntry.getKey();
			CustomerAccountByCurrency custActByCurrency = custActByCurrencyEntry.getValue();
			for (ProcessedDebit p : custActByCurrency.getProcessedDebits()) {
				DebitLineItem dli = new DebitLineItem();
				dli.setCreditType(p.getCreditType());
				dli.setInvoiceId(p.getInvoiceId());
				dli.setTransactionId(p.getTransactionId());
				dli.setTransactionDate(p.getTransactionDate());
				dli.setAmount(new Money(p.getAmount(), currency));
				debitLineItems.add(dli);
			}
		}

		return debitLineItems;
	}

	/**
	 * 
	 * Binds every transaction received with an id and uses it to decide whether it
	 * can be processed or not.
	 * 
	 * @param id
	 * @param transactionType
	 * @param transactionId
	 * @param rawReqAsJson
	 * @return
	 */
	public boolean canProcessReq(String id, TransactionType transactionType, String transactionId,
			String rawReqAsJson) {
		BankingTransaction newCustTrans = new BankingTransaction(id, customerId, transactionType, transactionId);
		newCustTrans.setRequest(rawReqAsJson);
		newCustTrans.setStatus(TransactionStatus.RECEIVED);

		BankingTransaction custTransInMap = null;
		if (TransactionType.CREDIT.equals(transactionType)) {
			custTransInMap = processedCredits.putIfAbsent(id, newCustTrans);
		} else {
			custTransInMap = processedDebits.putIfAbsent(id, newCustTrans);
		}

		return (custTransInMap == null) ? true : false;
	}

	public BankingTransaction getCurTransaction(String id, TransactionType transactionType) {
		if (TransactionType.CREDIT.equals(transactionType)) {
			return processedCredits.get(id);
		} else {
			return processedDebits.get(id);
		}
	}

}
