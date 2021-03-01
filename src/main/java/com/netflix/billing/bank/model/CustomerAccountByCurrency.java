package com.netflix.billing.bank.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.billing.bank.controller.wire.CreditAmount;
import com.netflix.billing.bank.controller.wire.CreditType;
import com.netflix.billing.bank.controller.wire.DebitAmount;
import com.netflix.billing.bank.exception.ApiException;

/**
 * 
 * Holder for customer account by currency where the debits and credits and
 * stored.
 * 
 * @author rkata
 *
 */
public class CustomerAccountByCurrency {

	private static Logger LOGGER = LoggerFactory.getLogger(CustomerAccountByCurrency.class);

	private final String customerId;
	private final String currency;

	// build a key of credit-type to CustomerBalance
	private final ConcurrentMap<CreditType, Long> balanceByCreditType = new ConcurrentHashMap<>();

//	// build a key of credittype+transactionid and map the credits that are
//	// processed.
//	private final ConcurrentMap<String, ProcessedCredit> creditsMapByTypeAndTransId = new ConcurrentHashMap<>();

	// build a key of invoiceid to a map of processed Debits
	private final LinkedList<ProcessedDebit> processedDebits = new LinkedList<ProcessedDebit>();

	// Process the credits available in the below order
	private final PriorityQueue<ProcessedCredit> sortedCredits = new PriorityQueue<>(new Comparator<ProcessedCredit>() {
		@Override
		public int compare(ProcessedCredit o1, ProcessedCredit o2) {
			int creditTypeCompareRes = o1.getCreditType().compareTo(o2.getCreditType());
			if (creditTypeCompareRes == 0) {
				// compare timestamps of creditAmountReq
				long o1AppliedDatetime = o1.getTransactionDate().toEpochMilli();
				long o2AppliedDatetime = o2.getTransactionDate().toEpochMilli();
				if (o1AppliedDatetime < o2AppliedDatetime) {
					return -1;
				} else if (o1AppliedDatetime > o2AppliedDatetime) {
					return 1;
				} else {
					return 0;
				}
			}
			return creditTypeCompareRes;
		}
	});

	public CustomerAccountByCurrency(String customerId, String currency) {
		this.customerId = customerId;
		this.currency = currency;
	}

	void processCredit(CreditAmount creditAmt, BankingTransaction curTransaction) {

		CreditType creditType = creditAmt.getCreditType();
		Long amount = creditAmt.getMoney().getAmount();

		ProcessedCredit procCredit = new ProcessedCredit(creditType, curTransaction);
		procCredit.setAmount(amount);
		procCredit.setCreditType(creditType);
		procCredit.setCurrency(currency);

		sortedCredits.offer(procCredit);

		updateBalance(creditType, amount);
	}

	private void updateBalance(CreditType creditType, Long creditAmt) {
		Long curBalance = balanceByCreditType.get(creditType);
		if (curBalance == null) {
			curBalance = new Long(0);
		}

		curBalance = curBalance + creditAmt;
		// remove the balance entry mapped to credit type if it is used completely.
		if (curBalance == 0) {
			balanceByCreditType.remove(creditType);
		} else {
			balanceByCreditType.put(creditType, curBalance);
		}
	}

	public long totalCreditsAvailable() {
		long totalBalance = 0;
		for (Long balance : balanceByCreditType.values()) {
			totalBalance += balance;
		}
		return totalBalance;
	}

	/**
	 * 
	 * Process debit amount request for the customer.
	 * 
	 * @param debitAmount
	 * @param curTransaction
	 */
	void processDebit(DebitAmount debitAmount, BankingTransaction curTransaction) {
		Long amtToDebit = debitAmount.getMoney().getAmount();

		long totalCreditsAvail = totalCreditsAvailable();
		// if the total credit is less than the debit amount that is requested the call
		// would fail.
		if (totalCreditsAvail < amtToDebit) {
			String errorMsg = String.format(
					"Not enough credit amount is avialble for customer %s to process debit request, totalCreditsAvail %s, amtToDebit %s, currency %s ",
					customerId, totalCreditsAvail, amtToDebit, currency);
			throw new ApiException(errorMsg);
		}

		//maintaining all the credits that are used to full fill the BankingTransaction
		List<ProcessedCredit> creditsUsed = new ArrayList<>();
		try {
			while (amtToDebit > 0) {
				ProcessedCredit availCredit = sortedCredits.peek();

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
					sortedCredits.poll();

					// adjust the leftover amount on the processed credit or availCredit object
					availCredit.setAmount(remainingCreditAmt);
					sortedCredits.offer(availCredit);

					// Debit request is fullfilled completely.
					amtToDebit = 0l;
				} else if (remainingCreditAmt <= 0) {

					// Debit request amount is higher than the credit amount that is used.
					creditsUsed.add(availCredit);
					// remove the credit
					sortedCredits.poll();
					amtToDebit = Math.abs(remainingCreditAmt);
				}
			}

			postProcessOnCompletion(debitAmount, creditsUsed, curTransaction);
		} catch (ApiException e) {
			LOGGER.error("Exception while processing debit request", e);
			// handle the case by rollbacking the credits that were removed/adjusted.
			//Not needed - this is just a buffer
			rollbackDebitTransaction(debitAmount, creditsUsed);
			throw e;
		}
	}

	/**
	 * 
	 * Implementaion fo rollback the credits if needed.
	 * 
	 * @param debit
	 * @param creditsUsed
	 */
	private void rollbackDebitTransaction(DebitAmount debit, List<ProcessedCredit> creditsUsed) {
		// credits are rollbacked here by adding them back to the sorted queue.
		// No debit lineitems are built as the transaction has failed.
		for (ProcessedCredit creditUsed : creditsUsed) {
			ProcessedCredit creditAvailInQ = sortedCredits.peek();
			if (!creditAvailInQ.getCTypeTransId().equals(creditUsed.getCTypeTransId())) {
				// put the credit back to the sorted queue.
				sortedCredits.offer(creditUsed);
			} else {
				sortedCredits.poll();
				// put the credit back by updating the amount to its original value.
				creditAvailInQ.setAmount(creditAvailInQ.getAmount() + creditUsed.getAmount());
				sortedCredits.offer(creditAvailInQ);
			}
		}
	}

	/**
	 * 
	 * Build the ProcessedDebit lineitems and add it to processedDebits collection
	 * update the credit balance by deducting the credit amounts that are used.
	 * 
	 * @param debit
	 * @param creditsUsed
	 * @param curTransaction
	 */
	private void postProcessOnCompletion(DebitAmount debit, List<ProcessedCredit> creditsUsed,
			BankingTransaction curTransaction) {
		// After adjusting the credit amount left, put the entry back int the map and
		// into the sorted queue.
		for (ProcessedCredit creditUsed : creditsUsed) {
			// create debit lineitem for the credit amount that was used.
			processedDebits.add(createDebitTransaction(creditUsed.getAmount(), creditUsed, curTransaction));
			updateBalance(creditUsed.getCreditType(), creditUsed.getAmount() * -1);

//			ProcessedCredit creditAvailInQ = sortedCredits.peek();
//			if (!creditAvailInQ.getCTypeTransId().equals(creditUsed.getCTypeTransId())) {
////				creditsMapByTypeAndTransId.remove(creditUsed.getCTypeTransId());
//			} else {
//				// do nothing here as the amount is adjust in the while loop above.
//			}
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

	public Map<CreditType, Long> getBalance() {
		return balanceByCreditType;
	}

	public LinkedList<ProcessedDebit> getProcessedDebits() {
		return processedDebits;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((currency == null) ? 0 : currency.hashCode());
		result = prime * result + ((customerId == null) ? 0 : customerId.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		CustomerAccountByCurrency other = (CustomerAccountByCurrency) obj;
		if (currency == null) {
			if (other.currency != null)
				return false;
		} else if (!currency.equals(other.currency))
			return false;
		if (customerId == null) {
			if (other.customerId != null)
				return false;
		} else if (!customerId.equals(other.customerId))
			return false;
		return true;
	}

}
