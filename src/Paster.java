// Copyright (c) 2025 Douglas Miller <durgadas311@gmail.com>

import java.util.Properties;

class Paster implements Runnable {
	Typer type;
	Thread thr;
	java.util.concurrent.LinkedBlockingDeque<String> fifo;
	boolean cancel;
	int paste_delay;
	int paste_cr_delay;

	public Paster(Properties props, String pfx, Typer t) {
		type = t;
		fifo = new java.util.concurrent.LinkedBlockingDeque<String>();

		paste_delay = 100; // mS, 10 char/sec
		paste_cr_delay = 1000; // mS, wait after CR
		String s = props.getProperty(pfx + "_delay");
		if (s != null) {
			paste_delay = Integer.valueOf(s);
		}
		s = props.getProperty(pfx + "_cr_delay");
		if (s != null) {
			paste_cr_delay = Integer.valueOf(s);
		}

		thr = new Thread(this);
		thr.start();
	}

	public void addText(byte[] txt) {
		if (txt == null) {
			return;
		}
		String s = new String(txt);
		fifo.add(s);
	}

	public void addText(String txt) {
		fifo.add(txt);
	}

	public synchronized void cancel() {
		cancel = true;
		fifo.clear();
	}

	public void charDelay(int c) {
		try {
			if (c == '\r')
				Thread.sleep(paste_cr_delay);
			else
				Thread.sleep(paste_delay);
		} catch (Exception ee) {}
	}

	public void run() {
		String s;
		while (true) {
			try {
				s = fifo.take();
				synchronized (this) {
					cancel = false; // assume stale
				}
			} catch (Exception ee) {
				ee.printStackTrace();
				break;
			}
			for (int x = 0; x < s.length(); ++x) {
				synchronized (this) {
					if (cancel) break;
				}
				int c = (int)s.charAt(x);
				type.typeChar(c);
				charDelay(c);
			}
		}
	}
}
