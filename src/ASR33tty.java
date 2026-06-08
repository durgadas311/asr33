// Copyright (c) 2026 Douglas Miller <durgadas311@gmail.com>

import java.util.Properties;
import java.util.concurrent.Semaphore;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import java.io.*;
import java.net.*;
// See: https://fazecast.github.io/jSerialComm/
// Currently using: jSerialComm-2.9.3.jar
import com.fazecast.jSerialComm.*;
// Run with:
//	java -cp <this-jar>:<jSerialComm-jar> Diablo630tty [args...]

public class ASR33tty implements TermContainer {
	private ASR33 front_end;
	private String title;
	private String tty = null;
	private int baud = -1;
	private SerialPort comm;

	private Object[] newcon_btns;
	private JTextField newcon_tty;
	private JTextField newcon_baud;
	private JPanel newcon_pn;
	private static final int OPTION_CANCEL = 0;
	private static final int OPTION_YES = 1;

	public ASR33tty(String[] args) {
		// Args may be prop=value expressions.
		// Other args are tty and optional baud...
		String rc = ASR33.getConfig(args);
		Properties props = new Properties();
		try {
			FileInputStream cfg = new FileInputStream(rc);
			props.load(cfg);
			cfg.close();
			//System.err.format("Using config in %s\n", rc);
			props.setProperty("configuration", rc);
		} catch(Exception ee) {
			//System.err.format("No config file\n");
		}
	
		String s;
		// Turn everything into properties...
		for (String arg : args) {
			if (arg.indexOf("=") >= 0) {
				String[] ss = arg.split("=", 2);
				props.setProperty("asr33_" + ss[0], ss[1]);
			} else if (tty == null) {
				tty = arg;
			} else if (baud <= 0) {
				baud = Integer.decode(arg);
			}
		}
		if (tty == null) {
			s = props.getProperty("asr33_tty");
			if (s != null) {
				tty = s;
			}
		}
		if (baud <= 0) {
			s = props.getProperty("asr33_baud");
			if (s != null) {
				baud = Integer.decode(s);
			} else {
				baud = 9600;
			}
		}
		if (tty == null) {
			System.err.format("Usage: ASR33tty tty [baud]\n");
			System.exit(1);
		}
		if (baud <= 0) {
			baud = 9600;
		}
		comm = getPort(tty, baud);
		if (comm == null) {
			System.err.format("Bad tty: %s (%d)\n", tty, baud);
			System.exit(1);
		}
		comm.setDTR();  
		comm.setRTS();
		title = String.format("tty %s %d", tty, baud);
		front_end = new ASR33(props, this);
		front_end.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		front_end.pack();
		setupNewCon();
		front_end.setVisible(true);
	}

	private static SerialPort getPort(String tty, int baud) {
		try {
			SerialPort serialPort = SerialPort.getCommPort(tty);
			if (serialPort == null) {
				return null;
			}
			// TODO: timeout values...
			if (!serialPort.openPort()) {
				return null;
			}
			if (baud > 0) {
				if (!serialPort.setComPortParameters(baud, 8,
							SerialPort.ONE_STOP_BIT,
							SerialPort.NO_PARITY)) {
					serialPort.closePort();
					return null;
				}
			}
			if (!serialPort.setComPortTimeouts(SerialPort.TIMEOUT_READ_BLOCKING,
					0, 0)) {
				serialPort.closePort();
				return null;
			}
			return serialPort;
		} catch (Exception ee) {
			ee.printStackTrace();
			return null;
		}
	}

	private void setupNewCon() {
		newcon_pn = new JPanel();
		newcon_pn.setLayout(new BoxLayout(newcon_pn, BoxLayout.Y_AXIS));
		newcon_btns = new Object[2];
		newcon_btns[OPTION_YES] = "Accept";
		newcon_btns[OPTION_CANCEL] = "Cancel";
		newcon_tty = new JTextField();
		newcon_tty.setPreferredSize(new Dimension(200, 20));
		newcon_baud = new JTextField();
		newcon_baud.setPreferredSize(new Dimension(200, 20));
		JPanel pn = new JPanel();
		pn.add(new JLabel("Tty:"));
		pn.add(newcon_tty);
		newcon_pn.add(pn);
		pn = new JPanel();
		pn.add(new JLabel("Baud:"));
		pn.add(newcon_baud);
		newcon_pn.add(pn);
	}

	////////////////////////////////////////
	// TermContainer
	public synchronized String getTitle() { return title; }
	public InputStream getInputStream() {
		if (comm == null) return null;
		try {
			return comm.getInputStream();
		} catch (Exception ee) {
			return null;
		}
	}
	public OutputStream getOutputStream() {
		if (comm == null) return null;
		try {
			return comm.getOutputStream();
		} catch (Exception ee) {
			return null;
		}
	}

	public void addMenus(JMenuBar mb, JMenu main, ActionListener lstr) {
		JMenuItem mi;
		main.addSeparator();
		mi = new JMenuItem("New Connection", KeyEvent.VK_N);
		mi.setActionCommand("ext"); // just to differentiate
		mi.addActionListener(lstr);
		main.add(mi);
	}

	// client understands it must envChanged() itself
	public boolean menuActions(JMenuItem me) {
		if (me.getMnemonic() == KeyEvent.VK_N) {
			return newConnection();
		}
		return false;
	}

	// client understands it must envChanged() itself
	public void failing() {
		disconnect();
	}
	////////////////////////////////////////

	private void disconnect() {
	}

	// must be last in all change steps - notifies front_end
	private synchronized void changeTitle(String str) {
		title = str;
		if (front_end != null) {
			front_end.envChanged();
		}
	}

	private int reconnect() {
		return 0; // nothing done
	}

	// only called on client, from menuActions()
	private boolean newConnection() {
		// TODO:
		newcon_tty.setText(tty);
		newcon_baud.setText(String.format("%d", baud));
		int res = JOptionPane.showOptionDialog(front_end, newcon_pn,
				"New Connection", JOptionPane.YES_NO_OPTION,
				JOptionPane.QUESTION_MESSAGE, null,
				newcon_btns, newcon_btns[OPTION_YES]);
		front_end.setFocus();
		if (res != OPTION_YES) {
			return false;
		}
		try {
			int b = Integer.decode(newcon_baud.getText());
			baud = b;
		} catch (Exception ee) {
			// TODO: pop-up error
			return false;
		}
		tty = newcon_tty.getText();
		comm.closePort(); // TODO: check success?
		comm = getPort(tty, baud);
		if (comm == null) {
			System.err.format("Bad tty: %s (%d)\n", tty, baud);
		}
		title = String.format("tty %s %d", tty, baud);
		// front_end.envChanged();
		return true; // something changed, but may not be successful
	}

	public static void main(String[] args) {
		new ASR33tty(args);
	}
}
