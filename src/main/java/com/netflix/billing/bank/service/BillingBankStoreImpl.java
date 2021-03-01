package com.netflix.billing.bank.service;

import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.netflix.billing.bank.controller.wire.CreditAmount;
import com.netflix.billing.bank.controller.wire.CustomerBalance;
import com.netflix.billing.bank.controller.wire.DebitAmount;
import com.netflix.billing.bank.controller.wire.DebitHistory;
import com.netflix.billing.bank.model.CustomerAccount;

@Service
public class BillingBankStoreImpl implements BillingBankStore {

	private static final Logger LOGGER = LoggerFactory.getLogger(BillingBankStoreImpl.class);

	private ConcurrentHashMap<String, AccountSynchronizer> custActSyncMap = new ConcurrentHashMap<>();
	private ConcurrentHashMap<String, CustomerAccount> customers = new ConcurrentHashMap<>();

	// Recording a key of customerid+credittype+transactionid ---> CreditRequest to
	// store all credits.
	private final ConcurrentHashMap<String, CreditAmount> processedCredits = new ConcurrentHashMap<>();

	// Recording a key of customerid+invoiceid ---> DebitAmount to store all debits
	// that are processed.
	private final ConcurrentHashMap<String, DebitAmount> processedDebits = new ConcurrentHashMap<>();

	private AccountSynchronizer getOrCreateCustomerSynchronizer(String customerId) {
		AccountSynchronizer newAcctSync = new AccountSynchronizer(customerId);
		AccountSynchronizer acctSyncInMap = custActSyncMap.putIfAbsent(customerId, newAcctSync);
		if (acctSyncInMap == null) {
			return newAcctSync;
		} else {
			return acctSyncInMap;
		}
	}

	private AccountSynchronizer getCustomerSynchronizer(String customerId) {
		return custActSyncMap.get(customerId);
	}

	private CustomerAccount getOrCreateCustomerAccount(String customerId) {
		CustomerAccount custAcct = new CustomerAccount(customerId);
		CustomerAccount custAcctInMap = customers.putIfAbsent(customerId, custAcct);
		if (custAcctInMap == null) {
			return custAcct;
		} else {
			return custAcctInMap;
		}
	}

	private CustomerAccount getCustomerAccount(String customerId) {
		return customers.get(customerId);
	}

	public CustomerBalance processCredit(String customerId, CreditAmount creditReq) {
		AccountSynchronizer custActSync = getOrCreateCustomerSynchronizer(customerId);
		try {
			custActSync.acquireWriteLock();

			String creditReqKey = String.format("%s-%s-%s", customerId, creditReq.getCreditType().ordinal(),
					creditReq.getTransactionId());

			CreditAmount processedCreditAmt = processedCredits.putIfAbsent(creditReqKey, creditReq);
			// if the credit request key is not seen before only then process the process
			// the request
			if (processedCreditAmt == null) {
				LOGGER.info("Processing credit request with key " + creditReqKey + ".");
				// go ahead and process the credit and update all indexes.
				CustomerAccount custAccount = getOrCreateCustomerAccount(customerId);
				custAccount.processCredit(creditReq);
			} else {
				// duplicate request log and leave it.
				LOGGER.info(
						"Ignoring process credit request with key " + creditReqKey + " as it is already processed.");
			}
		} finally {
			custActSync.releaseWriteLock();
		}

		return getCustomerAccountBalance(customerId);
	}

	public CustomerBalance processDebit(String customerId, DebitAmount debitAmount) {

		AccountSynchronizer custActSync = getOrCreateCustomerSynchronizer(customerId);
		try {
			custActSync.acquireWriteLock();

			String debitReqKey = String.format("%s-%s", customerId, debitAmount.getInvoiceId());
			DebitAmount processedDebitAmt = processedDebits.putIfAbsent(debitReqKey, debitAmount);

			// if debit request key is not seen before only then process the process
			// the request
			if (processedDebitAmt == null) {
				LOGGER.info("Processing Debit request with key " + debitReqKey + ".");
				// go ahead and process the credit and update all indexes.
				CustomerAccount custAccount = getOrCreateCustomerAccount(customerId);
				custAccount.processDebit(processedDebitAmt);
			} else {
				// duplicate request log and leave it.
				LOGGER.info("Ignoring process Debit request with key " + debitReqKey + " as it is already processed.");
			}
		} finally {
			custActSync.releaseWriteLock();
		}

		return getCustomerAccountBalance(customerId);
	}

	/**
	 * 
	 * 
	 */
	public CustomerBalance getCustomerAccountBalance(String customerId) {
		CustomerBalance custBal = null;
		AccountSynchronizer custActSync = getCustomerSynchronizer(customerId);
		if (custActSync != null) {
			try {
				custActSync.acquireReadLock();
				custBal = new CustomerBalance();
				CustomerAccount custAccount = getCustomerAccount(customerId);
				// TODO: Handle NPE error if custAccount is null
				custBal.setBalanceAmounts(custAccount.getAccountBalance());
				return custBal;
			} finally {
				custActSync.releaseReadLock();
			}
		}
		return custBal;
	}

	/**
	 * 
	 * 
	 */
	public DebitHistory debitHistory(String customerId) {
		DebitHistory debitHistory = null;
		AccountSynchronizer custActSync = getCustomerSynchronizer(customerId);
		if (custActSync != null) {
			try {
				custActSync.acquireReadLock();
				CustomerAccount custAccount = getCustomerAccount(customerId);

				// TODO: Handle NPE error if custAccount is null
				debitHistory = new DebitHistory(custAccount.getProcessedDebits());
			} finally {
				custActSync.releaseReadLock();
			}
		}
		return debitHistory;
	}
}
