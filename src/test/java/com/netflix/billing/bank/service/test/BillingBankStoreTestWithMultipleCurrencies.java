package com.netflix.billing.bank.service.test;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.netflix.billing.bank.controller.wire.CreditType;
import com.netflix.billing.bank.controller.wire.CustomerBalance;
import com.netflix.billing.bank.service.BillingBankStoreImpl;
import com.netflix.billing.bank.service.IdempotentRequestStore;
import com.netflix.billing.bank.service.Util;

public class BillingBankStoreTestWithMultipleCurrencies {

	private static final String INR = "INR";
	private static final String USD = "USD";
	private static final String CUST_123 = "cust-123";
	BillingBankStoreImpl bankStore = null;

	@Before
	public void setupBeforeTest() {
		bankStore = new BillingBankStoreImpl();
		bankStore.setRequestStore(new IdempotentRequestStore());
	}

	@After
	public void tearDownAfterTest() {
	}

	@Test
	public void postSingleCreditOfUSDTypeAndCheckBalance() {
		bankStore.processCredit(CUST_123, Util.buildCreditAmount("trans-123", CreditType.GIFTCARD, USD, 100));
		CustomerBalance custBal = bankStore.getCustomerAccountBalance(CUST_123);

		Assert.assertNotNull(custBal);
		Assert.assertEquals(1, custBal.getBalanceAmounts().size());
		Assert.assertEquals(1, custBal.getBalanceAmounts().get(CreditType.GIFTCARD).size());
		Assert.assertEquals(new Long(100), custBal.getBalanceAmounts().get(CreditType.GIFTCARD).get(0).getAmount());
		Assert.assertEquals(USD, custBal.getBalanceAmounts().get(CreditType.GIFTCARD).get(0).getCurrency());
	}

	@Test
	public void postACreditTypeWithMultipleCurrenciesAndCheckBalance() {
		bankStore.processCredit(CUST_123, Util.buildCreditAmount("trans-123", CreditType.GIFTCARD, USD, 100));
		CustomerBalance custBal = bankStore.getCustomerAccountBalance(CUST_123);
		Assert.assertNotNull(custBal);
		Assert.assertEquals(1, custBal.getBalanceAmounts().size());
		Assert.assertEquals(1, custBal.getBalanceAmounts().get(CreditType.GIFTCARD).size());

		Assert.assertEquals(new Long(100), custBal.getBalanceAmounts().get(CreditType.GIFTCARD).get(0).getAmount());
		Assert.assertEquals(USD, custBal.getBalanceAmounts().get(CreditType.GIFTCARD).get(0).getCurrency());

		bankStore.processCredit(CUST_123, Util.buildCreditAmount("trans-124", CreditType.GIFTCARD, USD, 15));
		bankStore.processCredit(CUST_123, Util.buildCreditAmount("trans-125", CreditType.GIFTCARD, INR, 1000));
		bankStore.processCredit(CUST_123, Util.buildCreditAmount("trans-126", CreditType.GIFTCARD, INR, 1250));

		custBal = bankStore.getCustomerAccountBalance(CUST_123);
		Assert.assertNotNull(custBal);
		Assert.assertEquals(1, custBal.getBalanceAmounts().size());
		Assert.assertEquals(2, custBal.getBalanceAmounts().get(CreditType.GIFTCARD).size());
		Assert.assertEquals(new Long(115), custBal.getBalanceAmounts().get(CreditType.GIFTCARD).get(0).getAmount());
		Assert.assertEquals(USD, custBal.getBalanceAmounts().get(CreditType.GIFTCARD).get(0).getCurrency());

		Assert.assertEquals(new Long(2250), custBal.getBalanceAmounts().get(CreditType.GIFTCARD).get(1).getAmount());
		Assert.assertEquals(INR, custBal.getBalanceAmounts().get(CreditType.GIFTCARD).get(1).getCurrency());
	}

	@Test
	public void postMultipleCreditTypeWithMultipleCurrenciesAndCheckBalance() {
		bankStore.processCredit(CUST_123, Util.buildCreditAmount("trans-123", CreditType.GIFTCARD, USD, 100));
		bankStore.processCredit(CUST_123, Util.buildCreditAmount("trans-124", CreditType.CASH, USD, 15));
		bankStore.processCredit(CUST_123, Util.buildCreditAmount("trans-125", CreditType.GIFTCARD, INR, 1000));
		bankStore.processCredit(CUST_123, Util.buildCreditAmount("trans-126", CreditType.CASH, INR, 1250));

		// using the same transactionid as above but the credit type is different
		bankStore.processCredit(CUST_123, Util.buildCreditAmount("trans-126", CreditType.GIFTCARD, INR, 1450));

		// using the same transactionid as above but the credit type is different
		bankStore.processCredit(CUST_123, Util.buildCreditAmount("trans-124", CreditType.GIFTCARD, USD, 55));

		CustomerBalance actBal = bankStore.getCustomerAccountBalance(CUST_123);
		Assert.assertNotNull(actBal);
		Assert.assertEquals(2, actBal.getBalanceAmounts().size());
		Assert.assertEquals(2, actBal.getBalanceAmounts().get(CreditType.GIFTCARD).size());
		Assert.assertEquals(new Long(155), actBal.getBalanceAmounts().get(CreditType.GIFTCARD).get(0).getAmount());
		Assert.assertEquals(USD, actBal.getBalanceAmounts().get(CreditType.GIFTCARD).get(0).getCurrency());

		Assert.assertEquals(new Long(2450), actBal.getBalanceAmounts().get(CreditType.GIFTCARD).get(1).getAmount());
		Assert.assertEquals(INR, actBal.getBalanceAmounts().get(CreditType.GIFTCARD).get(1).getCurrency());

		Assert.assertEquals(2, actBal.getBalanceAmounts().get(CreditType.CASH).size());
		Assert.assertEquals(new Long(15), actBal.getBalanceAmounts().get(CreditType.CASH).get(0).getAmount());
		Assert.assertEquals(USD, actBal.getBalanceAmounts().get(CreditType.CASH).get(0).getCurrency());

		Assert.assertEquals(new Long(1250), actBal.getBalanceAmounts().get(CreditType.CASH).get(1).getAmount());
		Assert.assertEquals(INR, actBal.getBalanceAmounts().get(CreditType.CASH).get(1).getCurrency());
	}

}
