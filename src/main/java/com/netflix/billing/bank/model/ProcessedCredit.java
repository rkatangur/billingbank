package com.netflix.billing.bank.model;

import java.time.Instant;

import com.netflix.billing.bank.controller.wire.CreditType;

public class ProcessedCredit implements Cloneable {

	private final String CTypeTransId;
	private final BankingTransaction curTransaction;

	private volatile long amount = 0;
	private String currency;

	// Type of credit. Different types of credits cannot be merged with each other.
	private CreditType creditType;

	public String getCTypeTransId() {
		return CTypeTransId;
	}

	public ProcessedCredit(CreditType creditType, BankingTransaction curTransaction) {
		this.curTransaction = curTransaction;
		this.CTypeTransId = buildCreditTransactionId(creditType, curTransaction.getTransactionId());
	}

	// The time in UTC when the credit was applied to the account.
	public Instant getTransactionDate() {
		return this.curTransaction.getTransactionTime();
	}

	public String getTransactionId() {
		return curTransaction.getTransactionId();
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

	public static String buildCreditTransactionId(CreditType creditType, String transactionId) {
		String creditTransId = String.format("ctype-%s-%s", creditType.ordinal(), transactionId);
		return creditTransId;
	}

	@Override
	public ProcessedCredit clone() throws CloneNotSupportedException {
		return (ProcessedCredit) super.clone();
	}

	@Override
	public String toString() {
		return "ProcessedCredit [customerId=" + curTransaction.getCustomerId() + ", CTypeTransId=" + CTypeTransId + "]";
	}

}
