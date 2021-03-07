package com.netflix.billing.bank.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.netflix.billing.bank.controller.wire.CreditAmount;
import com.netflix.billing.bank.controller.wire.CreditType;
import com.netflix.billing.bank.controller.wire.CustomerBalance;
import com.netflix.billing.bank.controller.wire.DebitAmount;
import com.netflix.billing.bank.controller.wire.DebitHistory;
import com.netflix.billing.bank.controller.wire.DebitLineItem;
import com.netflix.billing.bank.controller.wire.Money;
import com.netflix.billing.bank.exception.ApiException;
import com.netflix.billing.bank.model.BankingTransaction;
import com.netflix.billing.bank.model.CustomerAccount;
import com.netflix.billing.bank.model.CustomerAccountByCurrency;
import com.netflix.billing.bank.model.ProcessedCredit;
import com.netflix.billing.bank.model.ProcessedDebit;
import com.netflix.billing.bank.model.TransactionStatus;

/**
 * 
 * Bank store service provides a facade service for all operations associated
 * with the customer. CsustomerAccount should not be accessed directly, all the
 * operations associated with the customer should go through
 * BillingBankStoreImpl
 * 
 * @author rkata
 *
 */
@Service
public class BillingBankStoreImpl implements BillingBankStore {

	private static final Logger LOGGER = LoggerFactory.getLogger(BillingBankStoreImpl.class);

	@Autowired
	private IdempotentRequestStore requestStore;

	// Every customer carries an accountSynchronizer which is used to synchronize
	// calls that modify customerAccount.
	private final ConcurrentHashMap<String, AccountSynchronizer> custActSyncMap = new ConcurrentHashMap<>();

	private final ConcurrentHashMap<String, CustomerAccount> customers = new ConcurrentHashMap<>();

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

	/**
	 * 
	 * Performs credit operation on the customer account, write lock needs to be
	 * acquired before any operation that modifies the state of the customer account
	 * 
	 */
	public CustomerBalance processCredit(String customerId, CreditAmount creditReq) {
		AccountSynchronizer custActSync = getOrCreateCustomerSynchronizer(customerId);
		try {
			custActSync.acquireWriteLock();

			CustomerAccount custAccount = getOrCreateCustomerAccount(customerId);
			BankingTransaction creditTransaction = Util.buildBankingTransaction(creditReq, customerId);
			boolean canProcess = requestStore.recordTransactionIfNotAvailable(creditTransaction);

			// if the credit request key is not seen before only then process the process
			// the request
			if (canProcess) {
				LOGGER.info("Processing credit request with key " + creditTransaction.getId() + ".");
				// go ahead and process the credit and update all indexes.
				processCreditInternal(custAccount, creditReq, creditTransaction);
			} else {
				// duplicate request log and leave it.
				LOGGER.info("Ignoring process credit request with key " + creditTransaction.getId()
						+ " as it is already processed.");
			}
		} finally {
			custActSync.releaseWriteLock();
		}

		return getCustomerAccountBalance(customerId);
	}

	/**
	 * Need to be called in a thread safe manner from the bank store.
	 * 
	 * @param creditReq
	 */
	private void processCreditInternal(CustomerAccount custAct, CreditAmount creditReq,
			BankingTransaction curTransaction) {

		TransactionStatus status = TransactionStatus.RECEIVED;
		try {
			if (creditReq != null) {
				CustomerAccountByCurrency custActBalByCur = custAct
						.getOrCreateCustomerAccountBalance(creditReq.getMoney().getCurrency());

				ProcessedCredit procCredit = new ProcessedCredit(creditReq.getCreditType(), curTransaction);
				procCredit.setAmount(creditReq.getMoney().getAmount());
				procCredit.setCreditType(creditReq.getCreditType());
				procCredit.setCurrency(creditReq.getMoney().getCurrency());

				custActBalByCur.recordCredit(procCredit);
			}
			status = TransactionStatus.SUCESS;
		} catch (Exception e) {
			status = TransactionStatus.FAILURE;
		} finally {
			curTransaction.setStatus(status);
		}
	}

	/**
	 * 
	 * Performs debit operation on the customer account, write lock needs to be
	 * acquired before any operation that modifies the state of the customer account
	 * 
	 */
	public CustomerBalance processDebit(String customerId, DebitAmount debitAmount) {

		AccountSynchronizer custActSync = getOrCreateCustomerSynchronizer(customerId);
		try {
			custActSync.acquireWriteLock();

			CustomerAccount custAccount = getOrCreateCustomerAccount(customerId);
			BankingTransaction debitTransaction = Util.buildBankingTransaction(debitAmount, customerId);
			boolean canProcess = requestStore.recordTransactionIfNotAvailable(debitTransaction);
			// if debit request key is not seen before only then process the request
			if (canProcess) {
				LOGGER.info("Processing Debit request with key " + debitTransaction.getId() + ".");
				// go ahead and process the debit transaction and update all indexes.
				processDebitInternal(custAccount, debitAmount, debitTransaction);
			} else {
				// duplicate request log and leave it.
				LOGGER.info("Ignoring process Debit request with key " + debitTransaction.getId()
						+ " as it is already processed.");
			}
		} finally {
			custActSync.releaseWriteLock();
		}

		return getCustomerAccountBalance(customerId);
	}

	/**
	 * 
	 * gets the latest customer balance, Performs credit operation on the customer
	 * account, write lock needs to be acquired before any operation that modifies
	 * the state of the customer account
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
					custBal.setBalanceAmounts(getAccountBalance(custAccount));
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

	/**
	 * Computes the account balance by using CustomerAccountByCurrency which
	 * maintains an aggregated balance by currency, creditType
	 * 
	 * @return
	 */
	private Map<CreditType, List<Money>> getAccountBalance(CustomerAccount customerAccount) {

		Map<CreditType, List<Money>> custActBalanceByCreditType = new HashMap<>();

		for (CustomerAccountByCurrency custActByCurrency : customerAccount.getCustAccountByCurrency()) {
			String currency = custActByCurrency.getCurrency();

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

	/**
	 * 
	 * Gets the debitHistory after acquiring readLock()
	 * 
	 */
	public DebitHistory debitHistory(String customerId) {
		DebitHistory debitHistory = null;
		AccountSynchronizer custActSync = getCustomerSynchronizer(customerId);
		if (custActSync != null) {
			try {
				custActSync.acquireReadLock();
				CustomerAccount custAccount = getCustomerAccount(customerId);
				if (custAccount != null) {
					debitHistory = new DebitHistory(getProcessedDebits(custAccount));
				} else {
					LOGGER.info("No CustomerAccount found with id " + customerId + " to get debitHistory.");
				}
			} finally {
				custActSync.releaseReadLock();
			}
		}
		return debitHistory;
	}

	private List<DebitLineItem> getProcessedDebits(CustomerAccount custAccount) {
		List<DebitLineItem> debitLineItems = new ArrayList<DebitLineItem>();
		Collection<CustomerAccountByCurrency> custActByCurrencies = custAccount.getCustAccountByCurrency();

		for (CustomerAccountByCurrency custActByCurrency : custActByCurrencies) {
			String currency = custActByCurrency.getCurrency();
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
					custBal.setBalanceAmounts(getAccountBalance(custAccount));
				}
			} finally {
				custActSync.releaseWriteLock();
			}
		}

		return custBal;
	}

	/**
	 * Need to be called in a thread safe manner from the bank store.
	 * 
	 * @param creditReq
	 */
	private void processDebitInternal(CustomerAccount custAccount, DebitAmount debitAmount,
			BankingTransaction debitTransaction) {
		TransactionStatus status = TransactionStatus.RECEIVED;
		try {
			if (debitAmount != null && debitAmount.getMoney() != null) {
				CustomerAccountByCurrency custActBalByCur = custAccount
						.getOrCreateCustomerAccountBalance(debitAmount.getMoney().getCurrency());

				if (custActBalByCur != null) {
					processDebitTransaction(custActBalByCur, debitAmount, debitTransaction);
					status = TransactionStatus.SUCESS;
				} else {
					status = TransactionStatus.FAILURE;
					String errorMsg = String.format(
							"No credits avialble to process debit request with currency  for customer %s, currency %s",
							custAccount.getCustomerId(), debitAmount.getMoney().getCurrency());
					LOGGER.error(errorMsg);
					throw new ApiException(errorMsg);
				}
			}
		} catch (Exception e) {
			status = TransactionStatus.FAILURE;
			throw e;
		} finally {
			debitTransaction.setStatus(status);
		}
	}

	/**
	 * 
	 * Process debit amount request for the customer.
	 * 
	 * @param custActBalByCur
	 * @param debitAmount
	 * @param curTransaction
	 */
	private void processDebitTransaction(CustomerAccountByCurrency custActBalByCur, DebitAmount debitAmount,
			BankingTransaction curTransaction) {
		Long amtToDebit = debitAmount.getMoney().getAmount();

		long totalCreditsAvail = custActBalByCur.totalCreditsAvailable();
		// if the total credit is less than the debit amount that is requested the call
		// would fail.
		if (totalCreditsAvail < amtToDebit) {
			String errorMsg = String.format(
					"Not enough credit amount is avialble for customer %s to process debit request, totalCreditsAvail %s, amtToDebit %s, currency %s ",
					custActBalByCur.getCustomerId(), totalCreditsAvail, amtToDebit,
					debitAmount.getMoney().getCurrency());
			throw new ApiException(errorMsg);
		}

		// maintaining all the credits that are used to full fill the BankingTransaction
		List<ProcessedCredit> creditsUsed = new ArrayList<>();
		try {
			while (amtToDebit > 0) {
				ProcessedCredit availCredit = custActBalByCur.getSortedCredits().peek();

				// safety check to make sure th
				if (availCredit == null) {
					throw new ApiException("No more credits avialble to process debit request with currency ");
				}

				long remainingCreditAmt = availCredit.getAmount() - amtToDebit;

				// Left over credit to use, update the amount on the available credit amount.
				if (remainingCreditAmt > 0) {

					ProcessedCredit usedCredit = null;
					try {
						usedCredit = (ProcessedCredit) availCredit.clone();
						usedCredit.setAmount(amtToDebit);
					} catch (CloneNotSupportedException e) {
						e.printStackTrace();
					}

					creditsUsed.add(usedCredit);

					// Remove the credit
					custActBalByCur.getSortedCredits().poll();

					// adjust the leftover amount on the processed credit or availCredit object
					availCredit.setAmount(remainingCreditAmt);
					custActBalByCur.getSortedCredits().offer(availCredit);

					// Debit request is fullfilled completely.
					amtToDebit = 0l;
				} else if (remainingCreditAmt <= 0) {

					// Debit request amount is higher than the credit amount that is used.
					creditsUsed.add(availCredit);
					// remove the credit
					custActBalByCur.getSortedCredits().poll();
					amtToDebit = Math.abs(remainingCreditAmt);
				}
			}

			postProcessOnCompletion(custActBalByCur, creditsUsed, curTransaction);
		} catch (ApiException e) {
			LOGGER.error("Exception while processing debit request", e);
			// handle the case by rollbacking the credits that were removed/adjusted.
			// Not needed - this is just a buffer
			rollbackDebitTransaction(custActBalByCur, creditsUsed);
			throw e;
		}
	}

	/**
	 * 
	 * Implementation of rollback of creditsUsed if it is not fullfilled completely.
	 * 
	 * @param custActBalByCur
	 * @param creditsUsed
	 */
	private void rollbackDebitTransaction(CustomerAccountByCurrency custActBalByCur,
			List<ProcessedCredit> creditsUsed) {
		// credits are rollbacked here by adding them back to the sorted queue.
		// No debit lineitems are built as the transaction has failed.
		for (ProcessedCredit creditUsed : creditsUsed) {

			ProcessedCredit creditAvailInQ = custActBalByCur.getSortedCredits().peek();
			if (!creditAvailInQ.getCTypeTransId().equals(creditUsed.getCTypeTransId())) {
				// put the credit back to the sorted queue.
				custActBalByCur.getSortedCredits().offer(creditUsed);
			} else {
				custActBalByCur.getSortedCredits().poll();
				// put the credit back by updating the amount to its original value.
				creditAvailInQ.setAmount(creditAvailInQ.getAmount() + creditUsed.getAmount());
				custActBalByCur.getSortedCredits().offer(creditAvailInQ);
			}
		}
	}

	/**
	 * 
	 * Build the ProcessedDebit lineitems and add it to processedDebits collection
	 * update the credit balance by deducting the credit amounts that are used.
	 * 
	 * @param custActBalByCur
	 * @param creditsUsed
	 * @param curTransaction
	 */
	private void postProcessOnCompletion(CustomerAccountByCurrency custActBalByCur, List<ProcessedCredit> creditsUsed,
			BankingTransaction curTransaction) {
		// After adjusting the credit amount left, put the entry back int the map and
		// into the sorted queue.
		for (ProcessedCredit creditUsed : creditsUsed) {
			// create debit lineitem for the credit amount that was used.
			custActBalByCur.getProcessedDebits()
					.add(createDebitTransaction(creditUsed.getAmount(), creditUsed, curTransaction));
			custActBalByCur.updateBalance(creditUsed.getCreditType(), creditUsed.getAmount() * -1);
		}
	}

	private ProcessedDebit createDebitTransaction(Long amtDebitted, ProcessedCredit creditUsed,
			BankingTransaction curTransaction) {
		ProcessedDebit processedDebit = new ProcessedDebit(curTransaction);
		processedDebit.setAmount(amtDebitted);

		// mapping the credit trn information that is used.
		processedDebit.setCreditType(creditUsed.getCreditType());
		processedDebit.setTransactionId(creditUsed.getTransactionId());

		return processedDebit;
	}

	public IdempotentRequestStore getRequestStore() {
		return requestStore;
	}

	public void setRequestStore(IdempotentRequestStore requestStore) {
		this.requestStore = requestStore;
	}
}
