package com.netflix.billing.bank.service.test;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.netflix.billing.bank.controller.wire.CreditAmount;
import com.netflix.billing.bank.controller.wire.CreditType;
import com.netflix.billing.bank.controller.wire.CustomerBalance;
import com.netflix.billing.bank.service.BillingBankStoreImpl;
import com.netflix.billing.bank.service.IdempotentRequestStore;
import com.netflix.billing.bank.service.Util;

public class BillingBankStoreTestWithOneCurrency {

	private static final String CURRENCY_USD = "USD";
	private static final String CUST_123 = "cust-123";
	private BillingBankStoreImpl bankStore = null;

	@Before
	public void setupBeforeTest() {
		bankStore = new BillingBankStoreImpl();
		bankStore.setRequestStore(new IdempotentRequestStore());
	}

	@After
	public void tearDownAfterTest() {
	}

	@Test
	public void checkBalanceWithNoCredits() {
		CustomerBalance customerActBal = bankStore.getCustomerAccountBalance(CUST_123);
		Assert.assertNull(customerActBal);
	}

	@Test
	public void postSingleCreditOfUSDTypeAndCheckBalance() {
		CreditAmount creditAmt = Util.buildCreditAmount("trans-123", CreditType.GIFTCARD, CURRENCY_USD, 100);
		CustomerBalance customerActBal = bankStore.processCredit(CUST_123, creditAmt);

		Assert.assertNotNull(customerActBal);
		Assert.assertEquals(1, customerActBal.getBalanceAmounts().size());
		Assert.assertEquals(1, customerActBal.getBalanceAmounts().get(CreditType.GIFTCARD).size());
		Assert.assertEquals(new Long(100),
				customerActBal.getBalanceAmounts().get(CreditType.GIFTCARD).get(0).getAmount());
		Assert.assertEquals(CURRENCY_USD,
				customerActBal.getBalanceAmounts().get(CreditType.GIFTCARD).get(0).getCurrency());
	}

	@Test
	public void postMultipleCreditTypeWithUSDCurrencyAndCheckBalance() {
		bankStore.processCredit(CUST_123, Util.buildCreditAmount("trans-123", CreditType.GIFTCARD, CURRENCY_USD, 100));
		bankStore.processCredit(CUST_123, Util.buildCreditAmount("trans-124", CreditType.CASH, CURRENCY_USD, 15));

		// using the same transactionid as above but the credit type is different
		bankStore.processCredit(CUST_123, Util.buildCreditAmount("trans-124", CreditType.GIFTCARD, CURRENCY_USD, 55));

		CustomerBalance custBal = bankStore.getCustomerAccountBalance(CUST_123);
		Assert.assertNotNull(custBal);
		Assert.assertEquals(2, custBal.getBalanceAmounts().size());
		Assert.assertEquals(CURRENCY_USD, custBal.getBalanceAmounts().get(CreditType.GIFTCARD).get(0).getCurrency());
		Assert.assertEquals(new Long(155), custBal.getBalanceAmounts().get(CreditType.GIFTCARD).get(0).getAmount());
		Assert.assertEquals(new Long(15), custBal.getBalanceAmounts().get(CreditType.CASH).get(0).getAmount());
	}

	@Test
	public void postMultipleCreditsAndCheckCreditsAreAppliedInRightOrderWhileProcessingDebits() {
		bankStore.processCredit(CUST_123, Util.buildCreditAmount("trans-120", CreditType.CASH, CURRENCY_USD, 15));
		bankStore.processCredit(CUST_123, Util.buildCreditAmount("trans-121", CreditType.CASH, CURRENCY_USD, 55));
		bankStore.processCredit(CUST_123, Util.buildCreditAmount("trans-122", CreditType.GIFTCARD, CURRENCY_USD, 25));
		bankStore.processCredit(CUST_123, Util.buildCreditAmount("trans-123", CreditType.PROMOTION, CURRENCY_USD, 45));
		// using the same transactionid as above but the credit type is different
		bankStore.processCredit(CUST_123, Util.buildCreditAmount("trans-123", CreditType.GIFTCARD, CURRENCY_USD, 15));

		// checking the balance
		CustomerBalance customerBalance = bankStore.getCustomerAccountBalance(CUST_123);
		Assert.assertNotNull(customerBalance);
		Assert.assertEquals(3, customerBalance.getBalanceAmounts().size());
		Assert.assertEquals(new Long(40),
				customerBalance.getBalanceAmounts().get(CreditType.GIFTCARD).get(0).getAmount());
		Assert.assertEquals(CURRENCY_USD,
				customerBalance.getBalanceAmounts().get(CreditType.GIFTCARD).get(0).getCurrency());
		Assert.assertEquals(new Long(45),
				customerBalance.getBalanceAmounts().get(CreditType.PROMOTION).get(0).getAmount());
		Assert.assertEquals(CURRENCY_USD,
				customerBalance.getBalanceAmounts().get(CreditType.PROMOTION).get(0).getCurrency());
		Assert.assertEquals(new Long(70), customerBalance.getBalanceAmounts().get(CreditType.CASH).get(0).getAmount());
		Assert.assertEquals(CURRENCY_USD,
				customerBalance.getBalanceAmounts().get(CreditType.CASH).get(0).getCurrency());

		bankStore.processDebit(CUST_123, Util.buildDebitAmount("inv-123", CURRENCY_USD, 10));

		// checking the balance
		customerBalance = bankStore.getCustomerAccountBalance(CUST_123);
		Assert.assertNotNull(customerBalance);
		Assert.assertEquals(3, customerBalance.getBalanceAmounts().size());
		Assert.assertEquals(new Long(30),
				customerBalance.getBalanceAmounts().get(CreditType.GIFTCARD).get(0).getAmount());
		Assert.assertEquals(new Long(45),
				customerBalance.getBalanceAmounts().get(CreditType.PROMOTION).get(0).getAmount());
		Assert.assertEquals(new Long(70), customerBalance.getBalanceAmounts().get(CreditType.CASH).get(0).getAmount());

		// Posting a debit for 47 dollars
		// 30 would come from GIFT CARD
		// 17 from promotion
		bankStore.processDebit(CUST_123, Util.buildDebitAmount("inv-124", CURRENCY_USD, 47));

		// checking the balance
		customerBalance = bankStore.getCustomerAccountBalance(CUST_123);
		Assert.assertNotNull(customerBalance);
		Assert.assertEquals(2, customerBalance.getBalanceAmounts().size());
		Assert.assertEquals(new Long(28),
				customerBalance.getBalanceAmounts().get(CreditType.PROMOTION).get(0).getAmount());
		Assert.assertEquals(CURRENCY_USD,
				customerBalance.getBalanceAmounts().get(CreditType.PROMOTION).get(0).getCurrency());

		Assert.assertEquals(new Long(70), customerBalance.getBalanceAmounts().get(CreditType.CASH).get(0).getAmount());
		Assert.assertEquals(CURRENCY_USD,
				customerBalance.getBalanceAmounts().get(CreditType.CASH).get(0).getCurrency());
	}

}
