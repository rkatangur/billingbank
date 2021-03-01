package com.netflix.billing.bank.model;

import java.time.Instant;

import com.netflix.billing.bank.controller.wire.CreditType;

public class ProcessedDebit {

	private BankingTransaction curTransaction;
	private Long amount;

	private String transactionId; // Credit transactionId it was charged against.
	private CreditType creditType; // Credit type it was charged against.

	public ProcessedDebit(BankingTransaction curTransaction) {
		this.curTransaction = curTransaction;
	}

	public String getInvoiceId() {
		return curTransaction.getTransactionId();
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
		return curTransaction.getTransactionTime();
	}

	@Override
	public String toString() {
		return "ProcessedDebit [customerId=" + curTransaction.getCustomerId() + ", invoiceId="
				+ curTransaction.getCustomerId() + "]";
	}

}
