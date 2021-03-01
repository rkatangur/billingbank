package com.netflix.billing.bank;

import java.util.List;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import com.netflix.billing.bank.controller.wire.CreditAmount;
import com.netflix.billing.bank.controller.wire.CreditType;
import com.netflix.billing.bank.controller.wire.CustomerBalance;
import com.netflix.billing.bank.controller.wire.DebitAmount;
import com.netflix.billing.bank.controller.wire.DebitHistory;
import com.netflix.billing.bank.controller.wire.Money;
import com.netflix.billing.bank.exception.ApiException;
import com.netflix.billing.bank.service.BillingBankStore;

@RunWith(SpringRunner.class)
@SpringBootTest
public class BillingbankApplicationTests {

	@Autowired
	private BillingBankStore billingBankStoreService;

	@Before
	public void setupBeforeTest() {

	}

	@After
	public void tearDownAfterTest() {
		billingBankStoreService.delete("cust-123");
	}

	@Test
	public void postSingleCreditOfUSDTypeAndCheckBalance() {
		CreditAmount creditAmt = buildCreditAmount("trans-123", CreditType.GIFTCARD, "USD", 15l);
		CustomerBalance customerBalance = billingBankStoreService.processCredit("cust-123", creditAmt);
		Assert.assertNotNull(customerBalance);
		Assert.assertEquals(1, customerBalance.getBalanceAmounts().size());
		List<Money> creditsByCurency = customerBalance.getBalanceAmounts().get(CreditType.GIFTCARD);
		Assert.assertEquals(1, creditsByCurency.size());
		Assert.assertEquals(15l, (long) creditsByCurency.get(0).getAmount());
		Assert.assertEquals("USD", creditsByCurency.get(0).getCurrency());
	}

	@Test
	public void postCreditWithSameTransactionIdAndDifferentCreditTypes() {

		// #1 first transaction
		CustomerBalance customerBalance = billingBankStoreService.processCredit("cust-123",
				buildCreditAmount("trans-123", CreditType.GIFTCARD, "USD", 15l));

		Assert.assertNotNull(customerBalance);
		Assert.assertEquals(1, customerBalance.getBalanceAmounts().size());
		List<Money> creditsByCurency = customerBalance.getBalanceAmounts().get(CreditType.GIFTCARD);
		Assert.assertEquals(1, creditsByCurency.size());
		Assert.assertEquals(15l, (long) creditsByCurency.get(0).getAmount());
		Assert.assertEquals("USD", creditsByCurency.get(0).getCurrency());

		// #2 second transaction
		customerBalance = billingBankStoreService.processCredit("cust-123",
				buildCreditAmount("trans-123", CreditType.CASH, "USD", 25l));
		Assert.assertNotNull(customerBalance);
		Assert.assertEquals(2, customerBalance.getBalanceAmounts().size());

		creditsByCurency = customerBalance.getBalanceAmounts().get(CreditType.GIFTCARD);
		Assert.assertEquals(1, creditsByCurency.size());
		Assert.assertEquals(15l, (long) creditsByCurency.get(0).getAmount());
		Assert.assertEquals("USD", creditsByCurency.get(0).getCurrency());

		creditsByCurency = customerBalance.getBalanceAmounts().get(CreditType.CASH);
		Assert.assertEquals(1, creditsByCurency.size());
		Assert.assertEquals(25l, (long) creditsByCurency.get(0).getAmount());
		Assert.assertEquals("USD", creditsByCurency.get(0).getCurrency());
	}

	@Test
	public void postDebitWithSameInvoiceIdAndCheckIdempotency() {
		CustomerBalance customerBalance = billingBankStoreService.processCredit("cust-123",
				buildCreditAmount("trans-123", CreditType.GIFTCARD, "USD", 15l));

		customerBalance = billingBankStoreService.processCredit("cust-123",
				buildCreditAmount("trans-124", CreditType.GIFTCARD, "USD", 25l));

		// #1 Posting first debit
		customerBalance = billingBankStoreService.processDebit("cust-123", buildDebitAmount("inv-123", "USD", 25l));

		Assert.assertNotNull(customerBalance);
		Assert.assertEquals(1, customerBalance.getBalanceAmounts().size());

		List<Money> creditsByCurency = customerBalance.getBalanceAmounts().get(CreditType.GIFTCARD);
		Assert.assertEquals(1, creditsByCurency.size());
		Assert.assertEquals(15l, (long) creditsByCurency.get(0).getAmount());
		Assert.assertEquals("USD", creditsByCurency.get(0).getCurrency());

		// #2 Posting second debit with the same invoice id, it should not have any
		// impact.
		customerBalance = billingBankStoreService.processDebit("cust-123", buildDebitAmount("inv-123", "USD", 25l));

		Assert.assertNotNull(customerBalance);
		Assert.assertEquals(1, customerBalance.getBalanceAmounts().size());

		creditsByCurency = customerBalance.getBalanceAmounts().get(CreditType.GIFTCARD);
		Assert.assertEquals(1, creditsByCurency.size());
		Assert.assertEquals(15l, (long) creditsByCurency.get(0).getAmount());
		Assert.assertEquals("USD", creditsByCurency.get(0).getCurrency());
	}

	@Test
	public void postMultipleCreditsOfUSDType() {
		CreditAmount creditAmt = buildCreditAmount("trans-123", CreditType.GIFTCARD, "USD", 15l);
		billingBankStoreService.processCredit("cust-123", creditAmt);

		CreditAmount creditAmt1 = buildCreditAmount("trans-124", CreditType.GIFTCARD, "USD", 20l);
		billingBankStoreService.processCredit("cust-123", creditAmt1);

		// Posting the same transaction again
		billingBankStoreService.processCredit("cust-123", creditAmt);

		CustomerBalance custBal = billingBankStoreService.getCustomerAccountBalance("cust-123");

		Assert.assertNotNull(custBal);
		Assert.assertEquals(1, custBal.getBalanceAmounts().size());
		List<Money> creditsByCurency = custBal.getBalanceAmounts().get(CreditType.GIFTCARD);
		Assert.assertEquals(1, creditsByCurency.size());
		Assert.assertEquals("USD", creditsByCurency.get(0).getCurrency());
		Assert.assertEquals(35l, (long) creditsByCurency.get(0).getAmount());
	}

	@Test
	public void postMultipleCreditsOfDiffCreditTypeAndCurrency() {
		// add multiple credits of type USD
		billingBankStoreService.processCredit("cust-123",
				buildCreditAmount("trans-123", CreditType.GIFTCARD, "USD", 15l));

		billingBankStoreService.processCredit("cust-123", buildCreditAmount("trans-124", CreditType.CASH, "USD", 10l));

		billingBankStoreService.processCredit("cust-123",
				buildCreditAmount("trans-125", CreditType.GIFTCARD, "USD", 10l));

		billingBankStoreService.processCredit("cust-123", buildCreditAmount("trans-126", CreditType.CASH, "USD", 17l));

		// Flipping the credit types but using the same transaction id- to validate the
		// we can use the same transaction id with different credit types.
		billingBankStoreService.processCredit("cust-123", buildCreditAmount("trans-123", CreditType.CASH, "INR", 150l));

		billingBankStoreService.processCredit("cust-123",
				buildCreditAmount("trans-124", CreditType.GIFTCARD, "INR", 1000l));

		billingBankStoreService.processCredit("cust-123", buildCreditAmount("trans-125", CreditType.CASH, "INR", 175l));

		billingBankStoreService.processCredit("cust-123",
				buildCreditAmount("trans-126", CreditType.GIFTCARD, "INR", 240l));

		CustomerBalance custBal = billingBankStoreService.getCustomerAccountBalance("cust-123");

		Assert.assertNotNull(custBal);
		Assert.assertEquals(2, custBal.getBalanceAmounts().size());
		List<Money> giftCardCreditsByCurency = custBal.getBalanceAmounts().get(CreditType.GIFTCARD);
		Assert.assertEquals(2, giftCardCreditsByCurency.size());
		Assert.assertEquals("USD", giftCardCreditsByCurency.get(0).getCurrency());
		Assert.assertEquals(25l, (long) giftCardCreditsByCurency.get(0).getAmount());

		Assert.assertEquals("INR", giftCardCreditsByCurency.get(1).getCurrency());
		Assert.assertEquals(1240l, (long) giftCardCreditsByCurency.get(1).getAmount());

		Assert.assertNotNull(custBal);
		Assert.assertEquals(2, custBal.getBalanceAmounts().size());
		List<Money> cashCreditsByCurency = custBal.getBalanceAmounts().get(CreditType.CASH);
		Assert.assertEquals(2, cashCreditsByCurency.size());
		Assert.assertEquals("USD", cashCreditsByCurency.get(0).getCurrency());
		Assert.assertEquals(27l, (long) cashCreditsByCurency.get(0).getAmount());

		Assert.assertEquals("INR", cashCreditsByCurency.get(1).getCurrency());
		Assert.assertEquals(325l, (long) cashCreditsByCurency.get(1).getAmount());
	}

	@Test
	public void postDebitsThatFillMultipleCreditTypes() {
		// add multiple credits of type USD
		billingBankStoreService.processCredit("cust-123",
				buildCreditAmount("trans-123", CreditType.GIFTCARD, "USD", 15l));

		billingBankStoreService.processCredit("cust-123", buildCreditAmount("trans-124", CreditType.CASH, "USD", 10l));

		billingBankStoreService.processCredit("cust-123", buildCreditAmount("trans-126", CreditType.CASH, "USD", 17l));

		billingBankStoreService.processCredit("cust-123",
				buildCreditAmount("trans-125", CreditType.GIFTCARD, "USD", 10l));

		CustomerBalance custBal = billingBankStoreService.getCustomerAccountBalance("cust-123");

		Assert.assertNotNull(custBal);
		Assert.assertEquals(2, custBal.getBalanceAmounts().size());
		List<Money> giftCardCreditsByCurency = custBal.getBalanceAmounts().get(CreditType.GIFTCARD);
		Assert.assertEquals(1, giftCardCreditsByCurency.size());
		Assert.assertEquals("USD", giftCardCreditsByCurency.get(0).getCurrency());
		Assert.assertEquals(25l, (long) giftCardCreditsByCurency.get(0).getAmount());

		// #1 Post a debit req for 25$, it should use the credits of type GIFTCARD as
		// they fall under higher priority.
		custBal = billingBankStoreService.processDebit("cust-123", buildDebitAmount("inv-123", "USD", 25));

		custBal = billingBankStoreService.getCustomerAccountBalance("cust-123");

		Assert.assertNotNull(custBal);
		Assert.assertEquals(1, custBal.getBalanceAmounts().size());
		List<Money> cashCreditsByCurency = custBal.getBalanceAmounts().get(CreditType.CASH);
		Assert.assertEquals(1, cashCreditsByCurency.size());
		Assert.assertEquals("USD", cashCreditsByCurency.get(0).getCurrency());
		Assert.assertEquals(27l, (long) cashCreditsByCurency.get(0).getAmount());

		// #2 Post a debit req for 27$, it should use the credits of type CASH as that
		// is the only credit available.
		custBal = billingBankStoreService.processDebit("cust-123", buildDebitAmount("inv-124", "USD", 27));
		custBal = billingBankStoreService.getCustomerAccountBalance("cust-123");

		Assert.assertNotNull(custBal);
		Assert.assertEquals(0, custBal.getBalanceAmounts().size());

		// #3 Post a debit req for 27$, it should use the credits of type CASH as that
		// is the only credit available.
		DebitHistory debitHistory = billingBankStoreService.debitHistory("cust-123");

		Assert.assertNotNull(debitHistory);
		Assert.assertEquals(4, debitHistory.getDebits().size());

		// First debitlineitem should be of the first credit that got filled.
		Assert.assertEquals(15l, (long) debitHistory.getDebits().get(0).getAmount().getAmount());
		Assert.assertEquals("USD", debitHistory.getDebits().get(0).getAmount().getCurrency());
		Assert.assertEquals("trans-123", debitHistory.getDebits().get(0).getTransactionId());
		Assert.assertEquals(CreditType.GIFTCARD, debitHistory.getDebits().get(0).getCreditType());
		Assert.assertEquals("inv-123", debitHistory.getDebits().get(0).getInvoiceId());

		// Second debitlineitem should be of the second credit that got filled.
		Assert.assertEquals(10l, (long) debitHistory.getDebits().get(1).getAmount().getAmount());
		Assert.assertEquals("USD", debitHistory.getDebits().get(1).getAmount().getCurrency());
		Assert.assertEquals(CreditType.GIFTCARD, debitHistory.getDebits().get(1).getCreditType());
		Assert.assertEquals("trans-125", debitHistory.getDebits().get(1).getTransactionId());
		Assert.assertEquals("inv-123", debitHistory.getDebits().get(1).getInvoiceId());
	}

	@Test(expected = ApiException.class)
	public void postDebitWithAHigherQuantityThanCreditAvailableAndCheckIfItFails() {
		// add multiple credits of type USD
		billingBankStoreService.processCredit("cust-123",
				buildCreditAmount("trans-123", CreditType.GIFTCARD, "USD", 15l));

		billingBankStoreService.processCredit("cust-123", buildCreditAmount("trans-124", CreditType.CASH, "USD", 10l));

		billingBankStoreService.processCredit("cust-123", buildCreditAmount("trans-125", CreditType.CASH, "USD", 17l));

		billingBankStoreService.processCredit("cust-123",
				buildCreditAmount("trans-126", CreditType.GIFTCARD, "USD", 10l));

		CustomerBalance custBal = billingBankStoreService.getCustomerAccountBalance("cust-123");

		Assert.assertNotNull(custBal);
		Assert.assertEquals(2, custBal.getBalanceAmounts().size());
		List<Money> giftCardCreditsByCurrency = custBal.getBalanceAmounts().get(CreditType.GIFTCARD);
		Assert.assertEquals(1, giftCardCreditsByCurrency.size());
		Assert.assertEquals("USD", giftCardCreditsByCurrency.get(0).getCurrency());
		Assert.assertEquals(25l, (long) giftCardCreditsByCurrency.get(0).getAmount());

		// #1 Post a debit req for 53$ which will fail but it should have any impact on
		// the customer balance.
		custBal = billingBankStoreService.processDebit("cust-123", buildDebitAmount("inv-123", "USD", 53));

		custBal = billingBankStoreService.getCustomerAccountBalance("cust-123");

		Assert.assertNotNull(custBal);
		Assert.assertEquals(2, custBal.getBalanceAmounts().size());
		giftCardCreditsByCurrency = custBal.getBalanceAmounts().get(CreditType.GIFTCARD);
		Assert.assertEquals(1, giftCardCreditsByCurrency.size());
		Assert.assertEquals("USD", giftCardCreditsByCurrency.get(0).getCurrency());
		Assert.assertEquals(25l, (long) giftCardCreditsByCurrency.get(0).getAmount());

		List<Money> cashCreditsByCurrency = custBal.getBalanceAmounts().get(CreditType.CASH);
		Assert.assertEquals(1, cashCreditsByCurrency.size());
		Assert.assertEquals("USD", cashCreditsByCurrency.get(0).getCurrency());
		Assert.assertEquals(27l, (long) cashCreditsByCurrency.get(0).getAmount());
	}

	private CreditAmount buildCreditAmount(String transactionId, CreditType creditType, String currency, long amount) {
		CreditAmount creditAmt = new CreditAmount();
		creditAmt.setCreditType(creditType);
		creditAmt.setTransactionId(transactionId);
		creditAmt.setMoney(new Money(amount, currency));
		return creditAmt;
	}

	private DebitAmount buildDebitAmount(String invoiceId, String currency, long amount) {
		DebitAmount debitAmt = new DebitAmount();
		debitAmt.setInvoiceId(invoiceId);
		debitAmt.setMoney(new Money(amount, currency));
		return debitAmt;
	}
}
