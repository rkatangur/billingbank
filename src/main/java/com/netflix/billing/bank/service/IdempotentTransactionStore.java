package com.netflix.billing.bank.service;

import com.netflix.billing.bank.model.BankingTransaction;
import com.netflix.billing.bank.model.TransactionType;

public interface IdempotentTransactionStore {

	BankingTransaction getCurTransaction(String id, TransactionType transactionType);

	boolean recordTransactionIfNotAvailable(BankingTransaction curTransaction);

	void clearAllRequests();
}
