package com.netflix.billing.bank.model;

import java.time.Instant;

import com.netflix.billing.bank.controller.wire.CreditType;

public class ProcessedCredit implements Cloneable {

	private String transactionId;
	private volatile long amount = 0;
	private String currency;
	// Type of credit. Different types of credits cannot be merged with each other.
	private CreditType creditType;

	private Instant transactionDate; // The time in UTC when the debit was applied to the account.
	private final String CTypeTransId;

	public String getCTypeTransId() {
		return CTypeTransId;
	}

	public ProcessedCredit(CreditType creditType, String transactionId) {
		this.CTypeTransId = buildCreditTransactionId(creditType, transactionId);
	}

	public Instant getTransactionDate() {
		return transactionDate;
	}

	public String getTransactionId() {
		return transactionId;
	}

	public void setTransactionId(String transactionId) {
		this.transactionId = transactionId;
	}

	public long getAmount() {
		return amount;
	}

	public void setAmount(long amount) {
		this.amount = amount;
	}

	public String getCurrency() {
		return currency;
	}

	public void setCurrency(String currency) {
		this.currency = currency;
	}

	public CreditType getCreditType() {
		return creditType;
	}

	public void setCreditType(CreditType creditType) {
		this.creditType = creditType;
	}

	public void setTransactionDate(Instant transactionDate) {
		this.transactionDate = transactionDate;
	}

	public static String buildCreditTransactionId(CreditType creditType, String transactionId) {
		String creditTransId = String.format("ctype-%s-%s", creditType.ordinal(), transactionId);
		return creditTransId;
	}

	@Override
	protected ProcessedCredit clone() throws CloneNotSupportedException {
		return (ProcessedCredit) super.clone();
	}

}
