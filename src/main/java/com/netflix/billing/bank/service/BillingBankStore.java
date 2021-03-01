package com.netflix.billing.bank.service;

import com.netflix.billing.bank.controller.wire.CreditAmount;
import com.netflix.billing.bank.controller.wire.CustomerBalance;
import com.netflix.billing.bank.controller.wire.DebitAmount;
import com.netflix.billing.bank.controller.wire.DebitHistory;

public interface BillingBankStore {

	CustomerBalance getCustomerAccountBalance(String customerId);

	CustomerBalance processDebit(String customerId, DebitAmount debitAmount);

	CustomerBalance processCredit(String customerId, CreditAmount creditReq);

	DebitHistory debitHistory(String customerId);
}
