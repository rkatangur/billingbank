package com.netflix.billing.bank.model;

import java.time.Instant;

/**
 * All the idempotent transactions are identified by the id.
 * 
 * @author rkata
 *
 */
public class BankingTransaction {

//	customerid+credittype+transactionid --> for transactions of type credit
//	customerid+invoiceid --> for transactions of type debit
	private final String id;

	private final String customerId;

	// This will be invoice id for debits and transaction id for credits.
	private final String transactionId;

	// is it a debit or credit transaction.
	private final TransactionType transactionType;

	private String request;
	private TransactionStatus status;
	private final Instant transactionTime;

	public BankingTransaction(String id, String customerId, TransactionType transactionType, String transactionId) {
		super();
		this.id = id;
		this.customerId = customerId;
		this.transactionType = transactionType;
		this.transactionId = transactionId;
		this.transactionTime = Instant.now();
	}

	public String getCustomerId() {
		return customerId;
	}

	public TransactionType getTransactionType() {
		return transactionType;
	}

	public String getTransactionId() {
		return transactionId;
	}

	public String getRequest() {
		return request;
	}

	public void setRequest(String request) {
		this.request = request;
	}

	public TransactionStatus getStatus() {
		return status;
	}

	public void setStatus(TransactionStatus status) {
		this.status = status;
	}

	public Instant getTransactionTime() {
		return transactionTime;
	}

	public String getId() {
		return id;
	}

}
