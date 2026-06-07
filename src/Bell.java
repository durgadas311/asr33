// Copyright (c) 2025 Douglas Miller <durgadas311@gmail.com>

import java.util.Properties;
import java.io.*;
import java.awt.event.*;
import javax.sound.sampled.*;

public class Bell implements Runnable {
	java.util.concurrent.LinkedBlockingDeque<Integer> fifo;
	SourceDataLine line;
	byte[] buf;
	int frame;

	public Bell(Properties props, String pfx) {
		fifo = new java.util.concurrent.LinkedBlockingDeque<Integer>();
		String s = props.getProperty(pfx + "_bell");
		if (s == null) {
			s = "bell.wav";
		} else if (s.length() == 0) {
			line = null;
			return;
		}
		String bell_wav = s;
		try {
			InputStream is = SimResource.open(this, bell_wav);
			AudioInputStream wav =
				AudioSystem.getAudioInputStream(
					new BufferedInputStream(is));
			AudioFormat format = wav.getFormat();
			//frame = (int)wav.getFrameLength();
			buf = new byte[wav.available()];
			wav.read(buf);
			wav.close();

			DataLine.Info info = new DataLine.Info(
				SourceDataLine.class, format);
			line = (SourceDataLine)AudioSystem.getLine(info);
			line.open(format);
			frame = format.getFrameSize();
		} catch (Exception e) {
			e.printStackTrace();
			return;
		}
		int volume = 50;
		s = props.getProperty(pfx + "_bell_volume");
		if (s != null) {
			volume = Integer.valueOf(s);
			if (volume < 0) volume = 0;
			if (volume > 100) volume = 100;
		}
		FloatControl vol = null;
		if (line.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
			vol = (FloatControl)line.getControl(FloatControl.Type.MASTER_GAIN);
		} else if (line.isControlSupported(FloatControl.Type.VOLUME)) {
			vol = (FloatControl)line.getControl(FloatControl.Type.VOLUME);
		}
		if (vol != null) {
			float min = vol.getMinimum();
			float max = vol.getMaximum();
			float gain = (float)(min + ((max - min) * (volume / 100.0)));
			vol.setValue(gain);
		} else {
			System.err.format("ASR33:Bell: no volume control\n");
		}
		Thread t = new Thread(this);
//			try {
//				t.setPriority(Thread.MAX_PRIORITY);
//			} catch (Exception ee) { }
		t.start();
	}

	public void ding() {
		if (line == null) return;
		fifo.add(0);
	}

	public void run() {
		int idx;
		int max = buf.length - frame;
		int n;

		while (true) {
			try {
				idx = fifo.take(); // usually/always "0"
				// at most we block for 1 frame
				while (fifo.size() == 0 && idx < max) {
					n = frame;
					if (idx == 0) {
						line.start();
					}
					line.write(buf, idx, n);
					idx += n;
				}
				line.stop();
				line.flush();
			} catch (Exception ee) {
				ee.printStackTrace();
			}
		}
	}
}
