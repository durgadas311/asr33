// Copyright (c) 2025 Douglas Miller <durgadas311@gmail.com>

import java.util.Properties;
import java.util.concurrent.Semaphore;

class ASR33Reader implements Runnable {
	Typer type;
	RdrContainer rdr;
	Thread thr;
	boolean running;
	Semaphore wait;

	public ASR33Reader(Properties props, String pfx, RdrContainer r, Typer t) {
		rdr = r;
		type = t;
		wait = new Semaphore(0);
		thr = new Thread(this);
		thr.start();
	}

	private synchronized void setRunning(boolean b) { running = b; }

	public synchronized boolean isRunning() { return running; }

	public void start() {
		setRunning(true);
		wait.release();
	}

	public void stop() {
		setRunning(false);
	}

	public void run() {
		int c;

		while (true) {
			while (!isRunning()) {
				try {
					wait.acquire();
				} catch (Exception ee) {
					ee.printStackTrace();
					break;
				}
			}
			c = rdr.rdrGetChar();
			if (c < 0) {
				// notify ASR33...
				rdr.rdrStop();
				continue;
			}
			rdr.rdrCountOne();
			type.typeCharDelay(c);
		}
	}
}
