package com.netflix.billing.bank.model;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 
 * CustomerAccount with the balance
 * 
 * @author rkata
 *
 */
public class CustomerAccount {

	private static final Logger LOGGER = LoggerFactory.getLogger(CustomerAccount.class);

	private final String customerId;

	// build a key of currency to CustomerAccountBalance
	private final ConcurrentHashMap<String, CustomerAccountByCurrency> custActBalMapByCurrency = new ConcurrentHashMap<>();

//	// Recording a key of customerid+credittype+transactionid ---> CreditRequest to
//	// store the CreditAmount request that is tied to a request.
//	private final ConcurrentHashMap<String, BankingTransaction> processedCredits = new ConcurrentHashMap<>();
//
//	// Recording a key of customerid+invoiceid ---> DebitAmount to store all debits
//	// that are processed.
//	// store the DebitAmount request that is tied to the request.
//	private final ConcurrentHashMap<String, BankingTransaction> processedDebits = new ConcurrentHashMap<>();

	public CustomerAccount(String customerId) {
		this.customerId = customerId;
	}

	public CustomerAccountByCurrency getOrCreateCustomerAccountBalance(String currency) {
		CustomerAccountByCurrency custActBal = custActBalMapByCurrency.get(currency);

		if (custActBal == null) {
			LOGGER.info(
					"Creating a new CustomerAccountByCurrency for customerId " + customerId + ", currency " + currency);
			custActBal = new CustomerAccountByCurrency(customerId, currency);
			CustomerAccountByCurrency custActBalInMap = custActBalMapByCurrency.putIfAbsent(currency, custActBal);

			if (custActBalInMap != null) {
				custActBal = custActBalInMap;
			}
		}

		return custActBal;
	}

	public String getCustomerId() {
		return customerId;
	}

	public Collection<CustomerAccountByCurrency> getCustAccountByCurrency() {
		return custActBalMapByCurrency.values();
	}

}
