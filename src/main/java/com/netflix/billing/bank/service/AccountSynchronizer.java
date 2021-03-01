package com.netflix.billing.bank.service;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class AccountSynchronizer {

	private final String customerId;
	private final ReadWriteLock rqLock = new ReentrantReadWriteLock();
	private final Lock readLock = rqLock.readLock();
	private final Lock writeLock = rqLock.writeLock();

	public AccountSynchronizer(String customerId) {
		super();
		this.customerId = customerId;
	}

	public String getCustomerId() {
		return customerId;
	}

	public void acquireReadLock() {
		readLock.lock();
	}

	public void acquireWriteLock() {
		writeLock.lock();
	}

	public void releaseReadLock() {
		readLock.unlock();
	}

	public void releaseWriteLock() {
		writeLock.unlock();
	}
}
