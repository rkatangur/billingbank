package com.netflix.billing.bank.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.netflix.billing.bank.controller.wire.CreditAmount;
import com.netflix.billing.bank.controller.wire.CustomerBalance;
import com.netflix.billing.bank.controller.wire.DebitAmount;
import com.netflix.billing.bank.controller.wire.DebitHistory;
import com.netflix.billing.bank.service.BillingBankStore;

/**
 * Implement the following methods to complete the exercise.
 */
@RestController
public class BankController {

//    E.g. way to wire in dependencies. Note that there are other ways like constructor injection, setter injection etc.
//    @Autowired
//    private SomeDependency someDependency;

	@Autowired
	private BillingBankStore billingBankStore;

	/**
	 *
	 * @param customerId String id representing the customer/account id.
	 * @return How much money is left in the customer's account, i.e, After adding
	 *         all the credits, and subtracting all the debits, how much money is
	 *         left.
	 */
	@GetMapping("customer/{customerId}/balance")
	public CustomerBalance getBalance(@PathVariable String customerId) {
		CustomerBalance customerBal = billingBankStore.getCustomerAccountBalance(customerId);
		if (customerBal == null) {
			customerBal = new CustomerBalance();
		}
		return customerBal;
	}

	/**
	 *
	 * @param customerId   String id representing the customer/account id.
	 * @param creditAmount How much credit should be applied to the account
	 * @return How much money is left in the customer's account after the credit was
	 *         applied.
	 */
	@PostMapping("customer/{customerId}/credit")
	public CustomerBalance postCredit(@PathVariable String customerId, @RequestBody CreditAmount creditAmount) {
		return billingBankStore.processCredit(customerId, creditAmount);
	}

	/**
	 *
	 * @param customerId  String id representing the customer/account id.
	 * @param debitAmount How much money should be deducted from the customer's
	 *                    balance
	 * @return How much money is left in the customer's account after the debit
	 *         amount was deducted from balance.
	 */
	@PostMapping("customer/{customerId}/debit")
	public CustomerBalance debit(@PathVariable String customerId, @RequestBody DebitAmount debitAmount) {
		return billingBankStore.processDebit(customerId, debitAmount);
	}

	/**
	 *
	 * @param customerId String id representing the customer/account id.
	 * @return The debitHistory object representing all the debit transactions made
	 *         to the customer's account.
	 */
	@GetMapping("customer/{customerId}/history")
	public DebitHistory debitHistory(@PathVariable String customerId) {
		DebitHistory debitHistory = billingBankStore.debitHistory(customerId);
		if (debitHistory == null) {
			debitHistory = new DebitHistory();
		}
		return debitHistory;
	}

	/**
	 *
	 * @param customerId String id representing the customer/account id.
	 * @return The debitHistory object representing all the debit transactions made
	 *         to the customer's account.
	 */
	@DeleteMapping("customer/{customerId}")
	public CustomerBalance deleteCustomer(@PathVariable String customerId) {
		return billingBankStore.delete(customerId);
	}

}
