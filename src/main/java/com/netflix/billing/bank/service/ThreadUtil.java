package com.netflix.billing.bank.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ThreadUtil {
	private static Logger _log = LoggerFactory.getLogger(ThreadUtil.class);

	/**
	 * The last thing we want to do is to catch InterruptedException and do nothing.
	 * All code that needs to put a thread into sleep should call this method so we
	 * have a consistent way of handing InterruptedException, unless you really know
	 * what you're doing.
	 * 
	 * @param millis : the duration we want a thread to sleep
	 */
	public static void gotoSleep(long millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException e) {
			_log.error("Interrupted exception", e);
			Thread.currentThread().interrupt();
		}
	}
}
