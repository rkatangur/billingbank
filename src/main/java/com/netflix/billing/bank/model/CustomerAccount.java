package com.netflix.billing.bank.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.billing.bank.controller.wire.CreditAmount;
import com.netflix.billing.bank.controller.wire.CreditType;
import com.netflix.billing.bank.controller.wire.DebitAmount;
import com.netflix.billing.bank.controller.wire.DebitLineItem;
import com.netflix.billing.bank.controller.wire.Money;
import com.netflix.billing.bank.exception.ApiException;

public class CustomerAccount {

	private static final Logger LOGGER = LoggerFactory.getLogger(CustomerAccount.class);

	private final String customerId;

	// build a key of currency to CustomerAccountBalance
	private final ConcurrentHashMap<String, CustomerAccountByCurrency> custActBalMapByCurrency = new ConcurrentHashMap<>();

	public CustomerAccount(String customerId) {
		this.customerId = customerId;
	}

	/**
	 * Need to be called in a thread safe manner from the bank store.
	 * 
	 * @param creditReq
	 */
	public void processCredit(CreditAmount creditReq) {
		if (creditReq == null) {
			return;
		}

		CustomerAccountByCurrency custActBalByCur = getOrCreateCustomerAccountBalance(customerId,
				creditReq.getMoney().getCurrency());
		custActBalByCur.processCredit(creditReq);
	}

	/**
	 * Need to be called in a thread safe manner from the bank store.
	 * 
	 * @param creditReq
	 */
	public void processDebit(DebitAmount debitReq) {
		if (debitReq == null || debitReq.getMoney() == null) {
			return;
		}

		CustomerAccountByCurrency custActBal = custActBalMapByCurrency.get(debitReq.getMoney().getCurrency());
		if (custActBal != null) {
			custActBal.processDebit(debitReq);
		} else {
			// Not possible better log a statement.
			LOGGER.info("processDebit");
			throw new ApiException(
					"No credit avialble to process debit request with currency " + debitReq.getMoney().getCurrency());
		}
	}

	public CustomerAccountByCurrency getOrCreateCustomerAccountBalance(String customerId, String currency) {
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

	public Map<CreditType, List<Money>> getAccountBalance() {

		Map<CreditType, List<Money>> custActBalanceByCreditType = new HashMap<>();

		for (Map.Entry<String, CustomerAccountByCurrency> custActByCurrencyEntry : custActBalMapByCurrency.entrySet()) {
			String currency = custActByCurrencyEntry.getKey();
			CustomerAccountByCurrency custActByCurrency = custActByCurrencyEntry.getValue();

			for (Map.Entry<CreditType, Long> creditTypeBalEntry : custActByCurrency.getBalance().entrySet()) {
				CreditType creditType = creditTypeBalEntry.getKey();

				List<Money> cTypeMoney = custActBalanceByCreditType.get(creditType);
				if (cTypeMoney == null) {
					cTypeMoney = new ArrayList<>();
					custActBalanceByCreditType.put(creditType, cTypeMoney);
				}
				// record the balance now.
				cTypeMoney.add(new Money(creditTypeBalEntry.getValue(), currency));
			}
		}

		return custActBalanceByCreditType;
	}

	public String getCustomerId() {
		return customerId;
	}

	public List<DebitLineItem> getProcessedDebits() {
		List<DebitLineItem> debitLineItems = new ArrayList<DebitLineItem>();
		for (Map.Entry<String, CustomerAccountByCurrency> custActByCurrencyEntry : custActBalMapByCurrency.entrySet()) {
			String currency = custActByCurrencyEntry.getKey();
			CustomerAccountByCurrency custActByCurrency = custActByCurrencyEntry.getValue();
			for (ProcessedDebit p : custActByCurrency.getProcessedDebits()) {
				DebitLineItem dli = new DebitLineItem();
				dli.setCreditType(p.getCreditType());
				dli.setInvoiceId(p.getInvoiceId());
				dli.setTransactionId(p.getTransactionId());
				dli.setTransactionDate(p.getTransactionDate());
				dli.setAmount(new Money(p.getAmount(), currency));
				debitLineItems.add(dli);
			}
		}

		return debitLineItems;
	}

}
