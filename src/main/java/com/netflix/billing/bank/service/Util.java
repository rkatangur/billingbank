package com.netflix.billing.bank.service;

import com.netflix.billing.bank.controller.wire.CreditAmount;
import com.netflix.billing.bank.controller.wire.CreditType;
import com.netflix.billing.bank.controller.wire.DebitAmount;
import com.netflix.billing.bank.controller.wire.Money;
import com.netflix.billing.bank.model.BankingTransaction;
import com.netflix.billing.bank.model.TransactionStatus;
import com.netflix.billing.bank.model.TransactionType;

public class Util {

	public static String buildCreditTransId(String customerId, CreditType creditType, String transactionId) {
		return String.format("%s-%s-%s", customerId, creditType.ordinal(), transactionId);
	}

	public static String buildDebitTransId(String customerId, String invoiceId) {
		return String.format("%s-%s", customerId, invoiceId);
	}

	public static BankingTransaction buildBankingTransaction(String id, String customerId, String transactionId,
			TransactionType trnType, String rawReqAsJson) {

		BankingTransaction newCustTrans = new BankingTransaction(id, customerId, trnType, transactionId);
		newCustTrans.setRequest(rawReqAsJson);
		newCustTrans.setStatus(TransactionStatus.RECEIVED);

		return newCustTrans;
	}

	public static BankingTransaction buildBankingTransaction(CreditAmount creditAmt, String customerId) {
		String id = buildCreditTransId(customerId, creditAmt.getCreditType(), creditAmt.getTransactionId());
		return buildBankingTransaction(id, customerId, creditAmt.getTransactionId(), TransactionType.CREDIT,
				JsonUtils.writeValueAsString(creditAmt));
	}

	public static BankingTransaction buildBankingTransaction(DebitAmount debitAmt, String customerId) {
		String id = buildDebitTransId(customerId, debitAmt.getInvoiceId());
		return buildBankingTransaction(id, customerId, debitAmt.getInvoiceId(), TransactionType.DEBIT,
				JsonUtils.writeValueAsString(debitAmt));
	}

	public static CreditAmount buildCreditAmount(String transactionId, CreditType creditType, String currency,
			long amount) {
		CreditAmount creditAmt = new CreditAmount();
		creditAmt.setCreditType(creditType);
		creditAmt.setTransactionId(transactionId);
		creditAmt.setMoney(new Money(amount, currency));
		return creditAmt;
	}

	public static DebitAmount buildDebitAmount(String invoiceId, String currency, long amount) {
		DebitAmount debitAmt = new DebitAmount();
		debitAmt.setInvoiceId(invoiceId);
		debitAmt.setMoney(new Money(amount, currency));
		return debitAmt;
	}

}
