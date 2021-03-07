package com.netflix.billing.bank.model;

import java.util.Comparator;
import java.util.LinkedList;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.billing.bank.controller.wire.CreditType;

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

	public void updateBalance(CreditType creditType, Long creditAmt) {
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

	public Map<CreditType, Long> getBalance() {
		return balanceByCreditType;
	}

	public LinkedList<ProcessedDebit> getProcessedDebits() {
		return processedDebits;
	}

	public String getCustomerId() {
		return customerId;
	}

	public String getCurrency() {
		return currency;
	}

	public ConcurrentMap<CreditType, Long> getBalanceByCreditType() {
		return balanceByCreditType;
	}

	public PriorityQueue<ProcessedCredit> getSortedCredits() {
		return sortedCredits;
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

	public void recordCredit(ProcessedCredit procCredit) {
		sortedCredits.offer(procCredit);
		updateBalance(procCredit.getCreditType(), procCredit.getAmount());
	}

}
