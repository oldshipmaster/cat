package com.dianping.cat.storage.message;

import java.io.IOException;
import java.util.Date;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import junit.framework.Assert;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.dianping.cat.message.spi.MessageTree;
import com.dianping.cat.message.spi.internal.DefaultMessageTree;
import com.dianping.cat.storage.Bucket;
import com.site.lookup.ComponentTestCase;

public class LocalLogviewBucketTest extends ComponentTestCase {

	protected final static int threadNum = 10;// notice: max 9, for creating asc order id bellow

	protected final static int timesPerThread = 1000; // notice: must be powers 10, fro creating asc order id bellow

	protected void printFails(final int fails, final long start) {
		System.out.println(new Throwable().getStackTrace()[1].toString() + " threads:" + threadNum + " total:" + threadNum * timesPerThread + " fails:" + fails + " waste:" + (System.currentTimeMillis() - start) + "ms");
		if (fails > 0) {
			Assert.fail("fails:" + fails);
		}
	}

	protected void print(final long start) {
		System.out.println(new Throwable().getStackTrace()[1].toString() + " threads:" + threadNum + " total:" + threadNum * timesPerThread + " waste:" + (System.currentTimeMillis() - start) + "ms");
	}

	protected void resetSerial(final AtomicInteger serial) {
		serial.set(10 * timesPerThread);
	}

	protected AtomicInteger createSerial() {
		return new AtomicInteger(10 * timesPerThread);
	}

	final ExecutorService pool = Executors.newFixedThreadPool(threadNum);

	protected void submit(Runnable run) {
		for (int p = 0; p < threadNum; p++) {
			pool.submit(run);
		}
	}

	protected CountDownLatch createLatch() {
		return new CountDownLatch(threadNum);
	}

	Bucket<MessageTree> bucket = null;

	@SuppressWarnings("unchecked")
	@Before
	public void setUp() throws IOException {
		try {
			super.setUp();
		} catch (Exception e1) {
			e1.printStackTrace();
		}
		try {
			bucket = lookup(Bucket.class, MessageTree.class.getName() + "-logview");
			bucket.initialize(null, "cat", new Date());
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@After
	public void tearDown() throws Exception {
		super.tearDown();
		bucket.close();
		bucket.deleteAndCreate();
	}

	@Test
	public void testConcurrentRead() throws Exception {
		final AtomicInteger serial = createSerial();
		final AtomicInteger fail = new AtomicInteger();
		final CountDownLatch latch = createLatch();
		this.serialWrite(serial);
		resetSerial(serial);
		long start = System.currentTimeMillis();
		submit(new Runnable() {
			public void run() {
				for (int i = 0; i < timesPerThread; i++) {
					String id = null;
					String expect = null;
					try {
						id = "" + serial.incrementAndGet();
						DefaultMessageTree mt = new DefaultMessageTree();
						mt.setMessageId(id);
						MessageTree target = bucket.findById(id);
						Assert.assertEquals(id, target.getMessageId());
					} catch (Throwable e) {
						System.out.println(Thread.currentThread().getName() + ":" + id + ":" + expect);
						e.printStackTrace();
						fail.incrementAndGet();
					}
				}
				latch.countDown();
			}
		});
		latch.await();
		printFails(fail.get(), start);
	}

	@Test
	public void testConcurrentReadWrite() throws Exception {
		final AtomicInteger serial = createSerial();
		final AtomicInteger fail = new AtomicInteger();
		final CountDownLatch latch = createLatch();
		long start = System.currentTimeMillis();
		submit(new Runnable() {
			public void run() {
				for (int i = 0; i < timesPerThread; i++) {
					String id = null;
					String expect = null;
					try {
						id = "" + serial.incrementAndGet();
						DefaultMessageTree mt = new DefaultMessageTree();
						mt.setMessageId(id);
						Assert.assertTrue(bucket.storeById(id, mt));
						MessageTree target = bucket.findById(id);
						Assert.assertEquals(id, target.getMessageId());
					} catch (Throwable e) {
						System.out.println(Thread.currentThread().getName() + ":" + id + ":" + expect);
						e.printStackTrace();
						fail.incrementAndGet();
					}
				}
				latch.countDown();
			}
		});
		latch.await();
		printFails(fail.get(), start);
	}

	@Test
	public void testConcurrentWrite() throws Exception {
		final AtomicInteger serial = createSerial();
		final AtomicInteger fail = new AtomicInteger();
		final CountDownLatch latch = createLatch();
		long start = System.currentTimeMillis();
		submit(new Runnable() {
			public void run() {
				for (int i = 0; i < timesPerThread; i++) {
					try {
						String id = "" + serial.incrementAndGet();
						DefaultMessageTree mt = new DefaultMessageTree();
						mt.setMessageId(id);
						boolean success = bucket.storeById(id, mt);
						if (!success) {
							fail.incrementAndGet();
						}
					} catch (Throwable e) {
						fail.incrementAndGet();
					}
				}
				latch.countDown();
			}
		});
		latch.await();
		printFails(fail.get(), start);

		resetSerial(serial);
		this.serialRead(serial);
	}

	@Test
	public void testSerialRead() throws Exception {
		final AtomicInteger serial = createSerial();
		this.serialWrite(serial);
		resetSerial(serial);
		long start = System.currentTimeMillis();
		serialRead(serial);
		print(start);
	}

	@Test
	public void testSerialWrite() throws Exception {
		final AtomicInteger serial = createSerial();
		long start = System.currentTimeMillis();
		this.serialWrite(serial);
		print(start);
		resetSerial(serial);
		this.serialRead(serial);
	}

	private void serialRead(final AtomicInteger serial) throws IOException {
		for (int p = 0; p < threadNum; p++) {
			for (int i = 0; i < timesPerThread; i++) {
				String id = "" + serial.incrementAndGet();
				MessageTree target = bucket.findById(id);
				Assert.assertEquals(id, target.getMessageId());
			}
		}
	}

	private void serialWrite(AtomicInteger serial) throws IOException {
		for (int p = 0; p < threadNum; p++) {
			for (int i = 0; i < timesPerThread; i++) {
				String id = "" + serial.incrementAndGet();
				DefaultMessageTree mt = new DefaultMessageTree();
				mt.setMessageId(id);
				Assert.assertTrue(bucket.storeById(id, mt));
			}
		}
	}

}
