package org.opensource.clearpool.util;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.sql.Connection;
import java.text.NumberFormat;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;

import javax.sql.DataSource;

public class ThreadProcessUtil {
	/**
	 * Fight for connection
	 */
	public static void process(final DataSource dataSource, String name,
			final int loop, int threadCount, final AtomicLong physicalConnStat)
			throws Exception {
		final CountDownLatch startLatch = new CountDownLatch(1);
		final CountDownLatch endLatch = new CountDownLatch(threadCount);
		final CountDownLatch dumpLatch = new CountDownLatch(1);

		Thread[] threads = new Thread[threadCount];
		for (int i = 0; i < threadCount; ++i) {
			Thread thread = new Thread() {

				@Override
				public void run() {
					try {
						startLatch.await();
						for (int i = 0; i < loop; i++) {
							Connection conn = dataSource.getConnection();
							conn.close();
						}
					} catch (Exception ex) {
						ex.printStackTrace();
					}
					endLatch.countDown();
					try {
						dumpLatch.await();
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			};
			threads[i] = thread;
			thread.start();
		}
		long startMillis = System.currentTimeMillis();
		long startYGC = GCUtil.getYoungGC();
		long startFullGC = GCUtil.getFullGC();

		startLatch.countDown();
		endLatch.await();

		long millis = System.currentTimeMillis() - startMillis;
		long ygc = GCUtil.getYoungGC() - startYGC;
		long fullGC = GCUtil.getFullGC() - startFullGC;

		long[] threadIdArray = new long[threads.length];
		for (int i = 0; i < threads.length; ++i) {
			threadIdArray[i] = threads[i].getId();
		}
		ThreadInfo[] threadInfoArray = ManagementFactory.getThreadMXBean()
				.getThreadInfo(threadIdArray);

		dumpLatch.countDown();

		long blockedCount = 0;
		long waitedCount = 0;
		for (int i = 0; i < threadInfoArray.length; ++i) {
			ThreadInfo threadInfo = threadInfoArray[i];
			blockedCount += threadInfo.getBlockedCount();
			waitedCount += threadInfo.getWaitedCount();
		}

		System.out.println("thread " + threadCount + " " + name + " millis : "
				+ NumberFormat.getInstance().format(millis) + "; YGC " + ygc
				+ "; FGC " + fullGC + "; blocked "
				+ NumberFormat.getInstance().format(blockedCount) + "; waited "
				+ NumberFormat.getInstance().format(waitedCount)
				+ "; physicalConn " + physicalConnStat.get() + ";");
	}
}
