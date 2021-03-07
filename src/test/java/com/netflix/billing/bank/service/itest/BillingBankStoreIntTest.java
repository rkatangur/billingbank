package com.netflix.billing.bank.service.itest;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
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
import com.netflix.billing.bank.service.IdempotentTransactionStore;

@RunWith(SpringRunner.class)
@SpringBootTest
public class BillingBankStoreIntTest {

	@Autowired
	private BillingBankStore billingBankStoreService;

	@Autowired
	private IdempotentTransactionStore requestStore;

	private static ExecutorService exeService = null;

	@BeforeClass
	public static void setupBeforeTestClass() {
		exeService = Executors.newFixedThreadPool(10);
	}

	@Before
	public void setupBeforeTest() {
	}

	@After
	public void tearDownAfterTest() {
		billingBankStoreService.delete("cust-123");
		requestStore.clearAllRequests();
	}

	@AfterClass
	public static void tearDownAfterClass() {
		exeService.shutdownNow();
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
		billingBankStoreService.processCredit("cust-123",
				buildCreditAmount("trans-123", CreditType.GIFTCARD, "USD", 15l));

		billingBankStoreService.processCredit("cust-123",
				buildCreditAmount("trans-124", CreditType.GIFTCARD, "USD", 20l));

		// Posting the same transaction again
		billingBankStoreService.processCredit("cust-123",
				buildCreditAmount("trans-124", CreditType.GIFTCARD, "USD", 20l));

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

	@Test
	public void postMultipleCreditReqsParallelyOfUSDTypeFromOneCustomer() {
		List<Future<?>> tasks = new ArrayList<Future<?>>();

		tasks.add(exeService.submit(() -> {
			billingBankStoreService.processCredit("cust-123",
					buildCreditAmount("trans-123", CreditType.GIFTCARD, "USD", 15l));
		}));

		tasks.add(exeService.submit(() -> {
			billingBankStoreService.processCredit("cust-123",
					buildCreditAmount("trans-124", CreditType.GIFTCARD, "USD", 20l));
		}));

		// Posting the same transaction again
		tasks.add(exeService.submit(() -> {
			billingBankStoreService.processCredit("cust-123",
					buildCreditAmount("trans-124", CreditType.GIFTCARD, "USD", 20l));
		}));

		waitOnFutures(tasks);

		CustomerBalance custBal = billingBankStoreService.getCustomerAccountBalance("cust-123");

		Assert.assertNotNull(custBal);
		Assert.assertEquals(1, custBal.getBalanceAmounts().size());
		List<Money> creditsByCurency = custBal.getBalanceAmounts().get(CreditType.GIFTCARD);
		Assert.assertEquals(1, creditsByCurency.size());
		Assert.assertEquals("USD", creditsByCurency.get(0).getCurrency());
		Assert.assertEquals(35l, (long) creditsByCurency.get(0).getAmount());
	}

	@Test
	public void postMultipleCreditReqsParallelyOfUSDTypeFromMoreThanOneCustomer() {
		List<Future<?>> tasks = new ArrayList<Future<?>>();

		tasks.add(exeService.submit(() -> {
			billingBankStoreService.processCredit("cust-123",
					buildCreditAmount("trans-123", CreditType.GIFTCARD, "USD", 15l));
		}));

		tasks.add(exeService.submit(() -> {
			billingBankStoreService.processCredit("cust-123",
					buildCreditAmount("trans-124", CreditType.GIFTCARD, "USD", 20l));
		}));

		// Posting the same transaction again
		tasks.add(exeService.submit(() -> {
			billingBankStoreService.processCredit("cust-124",
					buildCreditAmount("trans-126", CreditType.GIFTCARD, "USD", 120));
		}));

		// Posting the same transaction again
		tasks.add(exeService.submit(() -> {
			billingBankStoreService.processCredit("cust-125",
					buildCreditAmount("trans-127", CreditType.GIFTCARD, "USD", 225));
		}));

		waitOnFutures(tasks);

		CustomerBalance cust123Bal = billingBankStoreService.getCustomerAccountBalance("cust-123");
		Assert.assertNotNull(cust123Bal);
		Assert.assertEquals(1, cust123Bal.getBalanceAmounts().size());
		Assert.assertEquals(1, cust123Bal.getBalanceAmounts().get(CreditType.GIFTCARD).size());
		Assert.assertEquals("USD", cust123Bal.getBalanceAmounts().get(CreditType.GIFTCARD).get(0).getCurrency());
		Assert.assertEquals(35l, (long) cust123Bal.getBalanceAmounts().get(CreditType.GIFTCARD).get(0).getAmount());

		CustomerBalance cust124Bal = billingBankStoreService.getCustomerAccountBalance("cust-124");
		Assert.assertNotNull(cust124Bal);
		Assert.assertEquals(1, cust124Bal.getBalanceAmounts().size());
		Assert.assertEquals(1, cust124Bal.getBalanceAmounts().get(CreditType.GIFTCARD).size());
		Assert.assertEquals("USD", cust124Bal.getBalanceAmounts().get(CreditType.GIFTCARD).get(0).getCurrency());
		Assert.assertEquals(120l, (long) cust124Bal.getBalanceAmounts().get(CreditType.GIFTCARD).get(0).getAmount());

		CustomerBalance cust125Bal = billingBankStoreService.getCustomerAccountBalance("cust-125");
		Assert.assertNotNull(cust125Bal);
		Assert.assertEquals(1, cust125Bal.getBalanceAmounts().size());
		Assert.assertEquals(1, cust125Bal.getBalanceAmounts().get(CreditType.GIFTCARD).size());
		Assert.assertEquals("USD", cust125Bal.getBalanceAmounts().get(CreditType.GIFTCARD).get(0).getCurrency());
		Assert.assertEquals(225l, (long) cust125Bal.getBalanceAmounts().get(CreditType.GIFTCARD).get(0).getAmount());
	}

	private void waitOnFutures(List<Future<?>> tasks) {
		for (Future<?> f : tasks) {
			try {
				f.get();
			} catch (InterruptedException e) {
				e.printStackTrace();
			} catch (ExecutionException e) {
				e.printStackTrace();
			}
		}
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
