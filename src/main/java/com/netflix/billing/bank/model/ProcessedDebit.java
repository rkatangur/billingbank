package com.netflix.billing.bank.model;

import java.time.Instant;

import com.netflix.billing.bank.controller.wire.CreditType;

public class ProcessedDebit {

	// Id denoting the receipt for the charge. Should be unique for a given
	// customer.
	private String invoiceId;
	private Long amount;
	private String transactionId; // Credit transactionId it was charged against.
	private CreditType creditType; // Credit type it was charged against.
	private Instant transactionDate; // The time in UTC when the debit was applied to the account.

	public String getInvoiceId() {
		return invoiceId;
	}

	public void setInvoiceId(String invoiceId) {
		this.invoiceId = invoiceId;
	}

	public Long getAmount() {
		return amount;
	}

	public void setAmount(Long amount) {
		this.amount = amount;
	}

	public String getTransactionId() {
		return transactionId;
	}

	public void setTransactionId(String transactionId) {
		this.transactionId = transactionId;
	}

	public CreditType getCreditType() {
		return creditType;
	}

	public void setCreditType(CreditType creditType) {
		this.creditType = creditType;
	}

	public Instant getTransactionDate() {
		return transactionDate;
	}

	public void setTransactionDate(Instant transactionDate) {
		this.transactionDate = transactionDate;
	}
}
