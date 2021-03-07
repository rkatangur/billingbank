package com.netflix.billing.bank.service.test;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.netflix.billing.bank.service.AccountSynchronizer;
import com.netflix.billing.bank.service.ThreadUtil;

public class AccountSynchronizerTest {

	@Before
	public void setupBeforeTest() {

	}

	@After
	public void tearDownAfterTest() {
	}

	volatile boolean failedWhileAcquiringLock = false;
	volatile boolean endFirstThread = true;

	@Test
	public void postSingleCreditOfUSDTypeAndCheckBalance() {
		AccountSynchronizer acSynchronizer = new AccountSynchronizer("1234");

		Thread thToAcquireLock = new Thread(() -> {
			try {
				acSynchronizer.acquireWriteLock();
				System.out.println("Acquired lock on AccountSynchronizer " + acSynchronizer.getCustomerId());

				while (endFirstThread) {
					ThreadUtil.gotoSleep(1000);
				}
			} finally {
				acSynchronizer.releaseWriteLock();
			}
		});

		thToAcquireLock.setDaemon(true);
		thToAcquireLock.start();

		//Thread 2 is trying to acquire the same write lock again
		Thread thToAcquireLockOSameAct = new Thread(() -> {
			try {
				System.out.println("Trying to acquire lock on AccountSynchronizer " + acSynchronizer.getCustomerId());
				acSynchronizer.acquireWriteLockInterruptibly();
			} catch (InterruptedException e) {
				e.printStackTrace();
				System.out.println(
						"Exception while acquiring lock on AccountSynchronizer " + acSynchronizer.getCustomerId());
				failedWhileAcquiringLock = true;
			}
		});

		thToAcquireLockOSameAct.setDaemon(true);
		thToAcquireLockOSameAct.start();

		ThreadUtil.gotoSleep(100);
		thToAcquireLockOSameAct.interrupt();
		ThreadUtil.gotoSleep(1000);

		endFirstThread = true;
		//Thread 2 should fail with an interrupted exception and asserting that it can't acquire the writelock.
		Assert.assertTrue(failedWhileAcquiringLock);
	}
}
