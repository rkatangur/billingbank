package com.netflix.billing.bank.service;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Account synchronizer which guards the customer Account is composed of
 * ReadWriteLock which synchornizes the calls that modify the state of the
 * customerAccount.
 * 
 * @author rkata
 *
 */
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

	public void acquireReadLockInterruptibly() throws InterruptedException {
		readLock.lockInterruptibly();
	}

	public void acquireWriteLock() {
		writeLock.lock();
	}

	public void acquireWriteLockInterruptibly() throws InterruptedException {
		writeLock.lockInterruptibly();
	}

	public void releaseReadLock() {
		readLock.unlock();
	}

	public void releaseWriteLock() {
		writeLock.unlock();
	}
}
