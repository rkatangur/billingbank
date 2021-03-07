package com.netflix.billing.bank.service;

import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import com.netflix.billing.bank.model.BankingTransaction;
import com.netflix.billing.bank.model.TransactionType;

@Service
public class IdempotentTransactionStoreImpl implements IdempotentTransactionStore {

	// Recording a key of customerid+credittype+transactionid ---> CreditRequest to
	// store the CreditAmount request that is tied to a request.
	private final ConcurrentHashMap<String, BankingTransaction> processedCredits = new ConcurrentHashMap<>();

	// Recording a key of customerid+invoiceid ---> DebitAmount to store all debits
	// that are processed.
	// store the DebitAmount request that is tied to the request.
	private final ConcurrentHashMap<String, BankingTransaction> processedDebits = new ConcurrentHashMap<>();

	public BankingTransaction getCurTransaction(String id, TransactionType transactionType) {
		if (TransactionType.CREDIT.equals(transactionType)) {
			return processedCredits.get(id);
		} else {
			return processedDebits.get(id);
		}
	}

	public boolean recordTransactionIfNotAvailable(BankingTransaction curTransaction) {
		BankingTransaction custTransInMap = null;
		if (TransactionType.CREDIT.equals(curTransaction.getTransactionType())) {
			custTransInMap = processedCredits.putIfAbsent(curTransaction.getId(), curTransaction);
		} else {
			custTransInMap = processedDebits.putIfAbsent(curTransaction.getId(), curTransaction);
		}
		return (custTransInMap == null) ? true : false;
	}

	public void clearAllRequests() {
		processedCredits.clear();
		processedDebits.clear();
	}
}
