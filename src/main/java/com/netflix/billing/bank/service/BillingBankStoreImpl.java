package com.netflix.billing.bank.service;

import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.netflix.billing.bank.controller.wire.CreditAmount;
import com.netflix.billing.bank.controller.wire.CustomerBalance;
import com.netflix.billing.bank.controller.wire.DebitAmount;
import com.netflix.billing.bank.controller.wire.DebitHistory;
import com.netflix.billing.bank.model.BankingTransaction;
import com.netflix.billing.bank.model.CustomerAccount;
import com.netflix.billing.bank.model.TransactionType;

@Service
public class BillingBankStoreImpl implements BillingBankStore {

	private static final Logger LOGGER = LoggerFactory.getLogger(BillingBankStoreImpl.class);

	private ConcurrentHashMap<String, AccountSynchronizer> custActSyncMap = new ConcurrentHashMap<>();
	private ConcurrentHashMap<String, CustomerAccount> customers = new ConcurrentHashMap<>();

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

			CustomerAccount custAccount = getOrCreateCustomerAccount(customerId);

			String creditReqTrnId = String.format("%s-%s-%s", customerId, creditReq.getCreditType().ordinal(),
					creditReq.getTransactionId());

			boolean canProcess = custAccount.canProcessReq(creditReqTrnId, TransactionType.CREDIT,
					creditReq.getTransactionId(), JsonUtils.writeValueAsString(creditReq));

			// if the credit request key is not seen before only then process the process
			// the request
			if (canProcess) {
				LOGGER.info("Processing credit request with key " + creditReqTrnId + ".");
				// go ahead and process the credit and update all indexes.
				BankingTransaction curTrn = custAccount.getCurTransaction(creditReqTrnId, TransactionType.CREDIT);
				custAccount.processCredit(creditReq, curTrn);
			} else {
				// duplicate request log and leave it.
				LOGGER.info(
						"Ignoring process credit request with key " + creditReqTrnId + " as it is already processed.");
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

			CustomerAccount custAccount = getOrCreateCustomerAccount(customerId);

			String debitTrnId = String.format("%s-%s", customerId, debitAmount.getInvoiceId());
			boolean canProcess = custAccount.canProcessReq(debitTrnId, TransactionType.DEBIT,
					debitAmount.getInvoiceId(), JsonUtils.writeValueAsString(debitAmount));

			// if debit request key is not seen before only then process the process
			// the request
			if (canProcess) {
				LOGGER.info("Processing Debit request with key " + debitTrnId + ".");
				// go ahead and process the credit and update all indexes.
				custAccount.processDebit(debitAmount, custAccount.getCurTransaction(debitTrnId, TransactionType.DEBIT));
			} else {
				// duplicate request log and leave it.
				LOGGER.info("Ignoring process Debit request with key " + debitTrnId + " as it is already processed.");
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
				CustomerAccount custAccount = getCustomerAccount(customerId);
				if (custAccount != null) {
					custBal = new CustomerBalance();
					custBal.setBalanceAmounts(custAccount.getAccountBalance());
				} else {
					LOGGER.info("No CustomerAccount found with id " + customerId + " to get CustomerAccountBalance.");
				}
				return custBal;
			} finally {
				custActSync.releaseReadLock();
			}
		}
		return custBal;
	}

	public DebitHistory debitHistory(String customerId) {
		DebitHistory debitHistory = null;
		AccountSynchronizer custActSync = getCustomerSynchronizer(customerId);
		if (custActSync != null) {
			try {
				custActSync.acquireReadLock();
				CustomerAccount custAccount = getCustomerAccount(customerId);
				if (custAccount != null) {
					debitHistory = new DebitHistory(custAccount.getProcessedDebits());
				} else {
					LOGGER.info("No CustomerAccount found with id " + customerId + " to get debitHistory.");
				}
			} finally {
				custActSync.releaseReadLock();
			}
		}
		return debitHistory;
	}

	@Override
	public CustomerBalance delete(String customerId) {
		CustomerBalance custBal = null;
		AccountSynchronizer custActSync = getCustomerSynchronizer(customerId);
		if (custActSync != null) {
			try {
				custActSync.acquireWriteLock();
				CustomerAccount custAccount = customers.remove(customerId);
				if (custAccount != null) {
					LOGGER.info("Removed customer with " + customerId + " id.");
					custBal = new CustomerBalance();
					custBal.setBalanceAmounts(custAccount.getAccountBalance());
				}
			} finally {
				custActSync.releaseWriteLock();
			}
		}

		return custBal;
	}
}
