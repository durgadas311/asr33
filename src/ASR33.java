// Copyright (c) 2025 Douglas Miller <durgadas311@gmail.com>

import java.io.*;
import java.util.Properties;
import java.util.Arrays;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import javax.sound.sampled.*;
import javax.swing.*;
import javax.swing.text.*;
import javax.swing.border.*;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;

public class ASR33 extends JFrame implements Typer, RdrContainer,
		KeyListener, MouseListener, ActionListener, WindowListener, Runnable {
	private static final int BEL = 0x07;
	private static final int DC1 = 0x11;	// ^Q - XON
	private static final int DC2 = 0x12;	// ^R - P-ON
	private static final int DC3 = 0x13;	// ^S - XOFF
	private static final int DC4 = 0x14;	// ^T - P-OFF
	private static final int WRU = 0x05;	// ^E (ENQ)
	private static final int RDR = 0x0f;	// ^O - new: advance reader once

	static final String title = "Virtual ASR33 Teletype";
	static final String[] sufx = { "txt" };
	static final String[] sufd = { "Text" };
	static final Color lighted = new Color(255, 255, 200);

	TermContainer fe;
	InputStream idev;
	OutputStream odev;
	Thread thrd;
	JTextArea text;
	FontMetrics fm;
	int fh;
	int fw;
	JScrollPane scroll;
	int carr;
	int col;
	int bol;
	int eol;
	File _last = null;
	GenericHelp _help;

	JCheckBox local;
	JCheckBox noprt;
	JCheckBox pun;
	JLabel pun_cnt;
	int pun_bytes;
	JCheckBox rdr;
	JButton rdr_start;
	boolean rdr_busy;
	JLabel rdr_cnt;
	int rdr_bytes;
	JMenuItem pun_mi;
	JMenuItem rdr_mi;
	JMenuItem rdr_pos;
	JMenuItem pun_vwr;
	RandomAccessFile pun_out;
	RandomAccessFile rdr_in;
	PaperTapeViewer rdr_vu;
	long rdr_idx;
	long rdr_tot;
	PaperTapeViewer pun_vu;
	JLabel spinner;
	int spinning;
	static final String[] spins = new String[]{ "/", "\u2013", "\\", "|" };

	byte[] ansbak;
	int rdr_view;
	ASR33Reader reader;
	Paster paster;
	int rdr_adv_char;
	Bell bell;

	boolean auto_nl;
	boolean auto_rdr;
	boolean auto_pun;
	boolean cr_on_lf;
	boolean lf_on_cr;
	boolean nonprint;
	boolean parity;
	boolean even;
	boolean col72_bell;
	int lines;

	public static String getConfig(String[] args) {
		String rc = System.getenv("ASR33_CONFIG");
		if (rc == null) {
			File f = new File("./asr33.rc");
			if (f.exists()) {
				rc = f.getAbsolutePath();
			}
		}
		if (rc == null) {
			rc = System.getProperty("user.home") + "/.asr33rc";
		}
		return rc;
	}

	public ASR33(Properties props, TermContainer fe) {
		super(title + " - " + fe.getTitle());
		this.fe = fe;
		idev = fe.getInputStream();
		odev = fe.getOutputStream();
		rdr_adv_char = RDR;
		paster = new Paster(props, "asr33", this);
		reader = new ASR33Reader(props, "asr33", this, this);
		bell = new Bell(props, "asr33");
		_last = new File(System.getProperty("user.dir"));
		getContentPane().setName("ASR33 Emulator");
		getContentPane().setBackground(new Color(100, 100, 100));
		setResizable(false);

		String s = props.getProperty("asr33_ansbak");
		setAnswerBack(s);
		s = props.getProperty("asr33_rdr_adv_char");
		if (s == null) {
			s = props.getProperty("tty_rdr_adv_char");
		}
		if (s != null) {
			rdr_adv_char = Integer.decode(s) & 0xff;
		}
		rdr_view = 8;
		s = props.getProperty("asr33_rdr_view");
		if (s != null) {
			rdr_view = Integer.valueOf(s);
			if (rdr_view < 1) rdr_view = 1;
			if (rdr_view > 30) rdr_view = 30; // what is practical...
		}
		lines = 24;
		int fz = 12;
		s = props.getProperty("asr33_lines");
		if (s != null) {
			int nl = Integer.decode(s);
			if (nl >= 10 && nl <= 200) {
				lines = nl;
			}
		}
		s = props.getProperty("asr33_font_size");
		if (s != null) {
			int z = Integer.decode(s);
			if (z >= 4 && z <= 30) {
				fz = z;
			}
		}

		// various teletype options
		auto_nl = true;
		auto_rdr = true;
		auto_pun = true;
		cr_on_lf = false;
		lf_on_cr = false;
		nonprint = false;
		parity = false;
		even = false;	// default to SPACE if no parity
		col72_bell = true;
		s = props.getProperty("asr33_auto_nl"); // "wrap" at EOL
		if (s != null) auto_nl = ExtBoolean.parseBoolean(s);
		s = props.getProperty("asr33_auto_rdr"); // reader via DC1/DC3
		if (s != null) auto_rdr = ExtBoolean.parseBoolean(s);
		s = props.getProperty("asr33_auto_pun"); // punch via DC1/DC4
		if (s != null) auto_pun = ExtBoolean.parseBoolean(s);
		s = props.getProperty("asr33_cr_on_lf"); // recv LF forces CR
		if (s != null) cr_on_lf = ExtBoolean.parseBoolean(s);
		s = props.getProperty("asr33_lf_on_cr"); // recv CR forces LF
		if (s != null) lf_on_cr = ExtBoolean.parseBoolean(s);
		s = props.getProperty("asr33_nonprint"); // print disable button
		if (s != null) nonprint = ExtBoolean.parseBoolean(s);
		s = props.getProperty("asr33_parity"); // parity enable
		if (s != null) parity = ExtBoolean.parseBoolean(s);
		if (parity) {
			even = true; // default to EVEN parity
		}
		s = props.getProperty("asr33_even"); // parity even (if enabled)
		if (s != null) even = ExtBoolean.parseBoolean(s);
		s = props.getProperty("asr33_col72_bell");
		if (s != null) col72_bell = ExtBoolean.parseBoolean(s);

		java.net.URL url;
		url = this.getClass().getResource("doc/help.html");
		_help = new GenericHelp("ASR33 Teletype Help", url);

		text = new JTextArea(lines, 81); // a little wider for breathing room
		text.setEditable(false); // this prevents caret... grrr.
		text.setBackground(Color.white);
		Font fnt = new Font("Monospaced", Font.PLAIN, 12);
		Font font = new Font("Monospaced", Font.PLAIN, fz);
		setupFont(font);
		text.setCaret(new BlockCaret(this, fw, fh));
		text.addKeyListener(this);
		text.addMouseListener(this);
		scroll = new JScrollPane(text);
		scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		scroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
		if (props.getProperty("asr33_live_ppt") != null) {
			liveView();
		} else {
			// simple layout, printer only
			setLayout(new BorderLayout()); // allow resizing
			add(scroll, BorderLayout.CENTER); // allow resizing
		}

		JMenuBar mb = new JMenuBar();
		JMenu mu = new JMenu("Print");
		JMenu main = mu;
		JMenuItem mi = new JMenuItem("Copy ", KeyEvent.VK_C);
		mi.setAccelerator(KeyStroke.getKeyStroke('C', InputEvent.ALT_DOWN_MASK));
		mi.addActionListener(this);
		mi.setActionCommand(".");
		mu.add(mi);
		mi = new JMenuItem("Paste", KeyEvent.VK_V);
		mi.setAccelerator(KeyStroke.getKeyStroke('V', InputEvent.ALT_DOWN_MASK));
		mi.addActionListener(this);
		mi.setActionCommand(".");
		mu.add(mi);
		mi = new JMenuItem("Save", KeyEvent.VK_S);
		mi.addActionListener(this);
		mi.setActionCommand(".");
		mu.add(mi);
		mi = new JMenuItem("Tear Off", KeyEvent.VK_T);
		mi.addActionListener(this);
		mi.setActionCommand(".");
		mu.add(mi);
		mb.add(mu);
		mu = new JMenu("PTape");
		mi = new JMenuItem("Punch", KeyEvent.VK_P);
		pun_mi = mi;
		mi.addActionListener(this);
		mi.setActionCommand(".");
		mu.add(mi);
		mi = new JMenuItem("Reader", KeyEvent.VK_R);
		rdr_mi = mi;
		mi.addActionListener(this);
		mi.setActionCommand(".");
		mu.add(mi);
		mi = new JMenuItem("Rdr Position", KeyEvent.VK_Z);
		mi.addActionListener(this);
		mi.setActionCommand(".");
		mu.add(mi);
		rdr_pos = mi;
		mi = new JMenuItem("Pun Viewer", KeyEvent.VK_Y);
		mi.addActionListener(this);
		mi.setActionCommand(".");
		mu.add(mi);
		pun_vwr = mi;
		mb.add(mu);
		fe.addMenus(mb, main, this);
		rdr_pos.setEnabled(false); // until tape in reader...
		pun_vwr.setEnabled(false); // until tape in punch...

		JPanel pn = new JPanel();
		pn.setPreferredSize(new Dimension(5, 30));
		pn.setOpaque(false);
		mb.add(pn);
		spinner = new JLabel(spins[0]);
		spinner.setFont(fnt);
		spinner.setBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED));
		spinning = 0;
		mb.add(spinner);

		pn = new JPanel();
		pn.setPreferredSize(new Dimension(5, 30));
		pn.setOpaque(false);
		mb.add(pn);
		// this must always exist, just maybe not visible
		noprt = new JCheckBox("NPRINT");
		noprt.setFocusable(false);
		noprt.setOpaque(false);
		if (nonprint) {
			mb.add(noprt);
			pn = new JPanel();
			pn.setPreferredSize(new Dimension(5, 30));
			pn.setOpaque(false);
			mb.add(pn);
		}
		local = new JCheckBox("LOCAL");
		local.setFocusable(false);
		//local.addActionListener(this);
		local.setOpaque(false);
		mb.add(local);
		pn = new JPanel();
		pn.setPreferredSize(new Dimension(10, 30));
		pn.setOpaque(false);
		mb.add(pn);
		pun = new JCheckBox("PUNCH");
		pun.setFocusable(false);
		//pun.addActionListener(this);
		pun.setEnabled(false);
		pun.setOpaque(false);
		mb.add(pun);
		pun_cnt = new JLabel("    ");
		pun_cnt.setFont(fnt);
		pun_cnt.setBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED));
		mb.add(pun_cnt);
		pn = new JPanel();
		pn.setPreferredSize(new Dimension(10, 30));
		pn.setOpaque(false);
		mb.add(pn);
		rdr = new JCheckBox("READER");
		rdr.setFocusable(false);
		rdr.addActionListener(this);
		rdr.setEnabled(false);
		rdr.setOpaque(false);
		mb.add(rdr);
		rdr_start = new JButton("start");
		rdr_start.setFocusable(false);
		rdr_start.addActionListener(this);
		rdr_start.setEnabled(false);
		rdr_start.setOpaque(true);
		mb.add(rdr_start);
		rdr_cnt = new JLabel("    ");
		rdr_cnt.setFont(fnt);
		rdr_cnt.setBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED));
		mb.add(rdr_cnt);

		pn = new JPanel();
		pn.setPreferredSize(new Dimension(10, 30));
		pn.setOpaque(false);
		mb.add(pn);

		mu = new JMenu("Help");
		mi = new JMenuItem("Show Help", KeyEvent.VK_H);
		mi.addActionListener(this);
		mi.setActionCommand(".");
		mu.add(mi);
		mb.add(mu);

		setJMenuBar(mb);

		tearOff();
		// bug in openjdk? does not remember current position
		setLocationByPlatform(true);

		restart();
	}

	private void liveView() {
		GridBagLayout gb = new GridBagLayout();
		setLayout(gb);
		GridBagConstraints gc = new GridBagConstraints();
		gc.fill = GridBagConstraints.NONE;
		gc.gridx = 0;
		gc.gridy = 0;
		gc.weightx = 0;
		gc.weighty = 0;
		gc.gridwidth = 1;
		gc.gridheight = 1;
		gc.anchor = GridBagConstraints.CENTER;
		pun_vu = new PaperTapeViewer(8, 100, true, false);
		rdr_vu = new PaperTapeViewer(8, 100, false, false);
		JLabel lb = new JLabel("Punch:");
		lb.setOpaque(true);
		lb.setPreferredSize(new Dimension(120, 20));
		gb.setConstraints(lb, gc);
		add(lb);
		++gc.gridy;
		gb.setConstraints(pun_vu, gc);
		add(pun_vu);
		++gc.gridy;
		lb = new JLabel("Reader:");
		lb.setOpaque(true);
		lb.setPreferredSize(new Dimension(120, 20));
		gb.setConstraints(lb, gc);
		add(lb);
		++gc.gridy;
		gb.setConstraints(rdr_vu, gc);
		add(rdr_vu);
		gc.gridy = 0;
		++gc.gridx;
		JPanel pan = new JPanel();
		pan.setPreferredSize(new Dimension(10, 10));
		pan.setOpaque(false);
		gb.setConstraints(pan, gc);
		add(pan);
		++gc.gridx;
		gc.gridheight = 4;
		gb.setConstraints(scroll, gc);
		add(scroll);
		++gc.gridx;
		pan = new JPanel();
		pan.setPreferredSize(new Dimension(10, 10));
		pan.setOpaque(false);
		gb.setConstraints(pan, gc);
		add(pan);
	}

	private void setupFont(Font f) {
		text.setFont(f);
		fm = text.getFontMetrics(f);
		//fa = fm.getAscent();
		fw = fm.charWidth('M');
		fh = fm.getHeight();
	}

	private synchronized void restart() {
		setTitle(title + " - " + fe.getTitle());
		idev = fe.getInputStream();
		odev = fe.getOutputStream();
		if (idev != null) {
			if (thrd != null && thrd.isAlive()) {
				// now we have a problem, and java doesn't help
				thrd.interrupt(); // probably does nothing
				// we have to hope it noticed closed InputStream
			}
			thrd = new Thread(this);
			thrd.start();
		} else if (thrd != null) {
			if (thrd.isAlive()) {
				thrd.interrupt(); // probably does nothing
			}
			// we have to hope it noticed closed InputStream
			thrd = null;
		}
	}

	public void setFocus() {
		text.requestFocus();
	}

	private void setAnswerBack(String s) {
		if (s == null) {
			ansbak = new byte[20]; // full of NULs
			return;
		}
		// answerback drum allows for character suppression,
		// so only use as many characters as supplied.
		// maximum is 20, though.
		byte[] n = s.getBytes();
		byte[] d = new byte[20];
		int y = 0;
		for (int x = 0; x < n.length && y < 20; ++x) {
			byte c = n[x];
			if (c == '\\') {
				if (x + 1 < n.length) {
					c = n[++x];
					switch (c) {
					case 'r': c = '\r'; break;
					case 'n': c = '\n'; break;
					case 'b': c = '\b'; break;
					case 't': c = '\t'; break;
					case 'f': c = '\f'; break;
					case '\'': c = '\''; break;
					case '"': c = '"'; break;
					}
				}
			}
			d[y++] = c;
		}
		ansbak = Arrays.copyOf(d, y);
	}

	private void newLine() {
		bol = eol = carr;
	}

	// '\n' has been appended, and carr updated. col not changed.
	private void lineFeed() {
		bol = carr;
		eol = bol + col;
		if (col == 0) {
			return;
		}
		byte[] b = new byte[col];
		Arrays.fill(b, (byte)' ');
		text.append(new String(b));
		carr += col;
	}

	private void doLineFeed() {
		text.append("\n");
		if (carr < eol) {
			carr = eol;
		}
		++carr;
		if (cr_on_lf) {
			col = 0; // bypasses messy part of lineFeed()
		}
		lineFeed();
		text.setCaretPosition(carr);
	}

	// printable characters only
	private void addChar(int c) {
		if (noprt.isSelected()) {
			return;
		}
		String s = new String(new char[]{(char)c});
		if (carr < eol) {
			text.replaceRange(s, carr, carr+1);
		} else {
			text.append(s);
			++eol;
		}
		if (col < 79) {
			++carr;
			++col;
		} else if (auto_nl) {
			++carr;
			col = 0;
			text.append("\n"); // in this case, we want CR/LF
			++carr;
			newLine();
		} else {
			// stay where we are
		}
		if (col72_bell && col == 73) {
			bell.ding();
		}
		text.setCaretPosition(carr);
	}

	private void tearOff() {
		text.setText("");
		carr = 0;
		col = 0;
		newLine();
	}

	// RdrContainer
	public void rdrStart() {
		rdr_start.setBackground(lighted);
		reader.start();
	}

	public void rdrStop() {
		rdr_start.setBackground(null);
		reader.stop();
	}

	public void rdrCountOne() {
		rdr_cnt.setText(String.format("%4d", ++rdr_bytes));
	}

	private void updateRdrView(long newIdx) {
		long pos = newIdx - rdr_vu.buf;
		int _beg = 0;
		int _end = rdr_vu.win;

		if (newIdx == rdr_idx) {
			return;
		}
		if (pos < 0) {
			_beg = (int)-pos;
			pos = 0;
		}
		if (pos + (_end - _beg) > rdr_tot) {
			_end = (int)(_beg + (rdr_tot - pos));
		}
		try {
			rdr_in.seek(pos);
			rdr_in.read(rdr_vu.tapeBuf, _beg, _end - _beg);
			rdr_idx = newIdx;
			rdr_vu.update(_beg, _end);
			rdr_vu.repaint();
		} catch (Exception ee) {}
	}

	private int popRdrChar() {
		if (rdr_idx >= rdr_tot) {
			return -1;
		}
		int c = rdr_vu.tapeBuf[rdr_vu.buf]; // 'buf' only valid for !top
		// advance tape...
		updateRdrView(rdr_idx + 1);
		return c;
	}

	public int rdrGetChar() {
		if (rdr_in == null) {
			return -1;
		}
		if (rdr_vu != null) {
			return popRdrChar();
		}
		try {
			return rdr_in.read(); // may also be -1
		} catch (Exception ee) {
			return -1;
		}
	}

	private void pushPunChar(int c) {
		pun_vu.tapeBuf[pun_vu.win - 1] = (byte)c;
		for (int x = 0; x < pun_vu.win - 1; ++x) {
			pun_vu.tapeBuf[x] = pun_vu.tapeBuf[x + 1];
		}
		pun_vu.tapeBuf[pun_vu.win - 1] = (byte)0;
		if (pun_vu.beg > 0) {
			pun_vu.update(pun_vu.beg - 1, pun_vu.end);
		}
		pun_vu.repaint();
	}

	// TODO: update pun_vu
	private void punChar(int c) {
		if (pun_out == null || !pun.isSelected()) {
			return;
		}
		if (pun_vu != null) {
			pushPunChar(c);
		}
		try {
			pun_out.write(c);
			pun_cnt.setText(String.format("%4d", ++pun_bytes));
		} catch (Exception ee) {}
	}

	private void ctrlChar(int c) {
		if (c == rdr_adv_char) {
			// special char to implement TTY paper tape read 1 char
			if (rdr_in != null && rdr.isSelected()) {
				int rc = rdrGetChar();
				if (rc >= 0) {
					rdrCountOne();
					typeChar(rc);
				}
			}
			return;
		}
		switch (c) {
		case '\n':	// LF
			doLineFeed();
			break;
		case '\r':	// CR
			col = 0;
			if (lf_on_cr) {
				doLineFeed();
				break;
			}
			carr = bol;
			text.setCaretPosition(carr);
			break;
		case 0x07:	// BEL
			if (bell != null) {
				bell.ding();
			}
			break;
		case DC2:	// P-ON
			if (auto_pun) {
				pun.setSelected(true);
			}
			break;
		case DC1:	// X-ON
			if (auto_rdr && rdr.isSelected()) {
				rdrStart();
			}
			break;
		case DC4:	// P-OFF
			if (auto_pun) {
				pun.setSelected(false);
			}
			break;
		case DC3:	// X-OFF
			if (auto_rdr && rdr.isSelected()) {
				rdrStop();
			}
			break;
		case WRU:
			// reader is stopped during answerback,
			// and not automatically started.
			// TODO: only if auto_rdr?
			if (rdr.isSelected()) {
				rdrStop();
			}
			paster.addText(ansbak);
			break;
		}
	}

	private void printChar(int c) {
		if (pun.isSelected()) {
			punChar(c);
		}
		c &= 0x7f; // TODO: check parity? then what?
		if (c < ' ') {
			ctrlChar(c);
			return;
		}
		// redundant, depending on how we got here.
		if (c >= '`' && c < 0x7f) {
			c &= 0x5f;
		}
		if (c > 0x5f) {
			return;
		}
		addChar(c);
	}

	private File pickFile(String purpose) {
		File file = null;
		SuffFileChooser ch = new SuffFileChooser(purpose, sufx, sufd, _last, null);
		int rv = ch.showDialog(this);
		if (rv == JFileChooser.APPROVE_OPTION) {
			file = ch.getSelectedFile();
		}
		return file;
	}

	public void envChanged() {
		restart();
	}

	public void run() {
		while (true) {
			int c;
			try {
				c = idev.read();
			} catch (Exception ee) {
				//ee.printStackTrace();
				break;
			}
			if (c < 0) {
				//System.err.format("Thread dying from input error\n");
				break;
			}
			if (!local.isSelected()) {
				printChar(c);
			}
		}
		fe.failing(); // notify container of our predicament
		// might race with server going to listen
		setTitle(title + " - " + fe.getTitle());
	}

	public void typeChar(int c) {
		++spinning;
		spinner.setText(spins[spinning & 3]);
		if (local.isSelected()) {
			printChar(c);
		} else {
			try {
				odev.write((byte)c);
			} catch (Exception ee) {
				// TODO: anything?
			}
		}
	}

	public void typeCharDelay(int c) {
		typeChar(c);
		paster.charDelay(c);
	}

	public int getCarr() { return carr; }

	public void keyTyped(KeyEvent e) {}
	public void keyPressed(KeyEvent e) {
		int c = (int)e.getKeyChar();
		int k = e.getKeyCode();
		int m = e.getModifiersEx();

		//System.err.format("keyPressed %02x %04x %04x\n", c, k, m);
		if (k == KeyEvent.VK_F12) {
			paster.addText(ansbak);
			return;
		}
		if (k == KeyEvent.VK_F1) {
			paster.cancel();
			return;
		}
		if ((m & InputEvent.ALT_DOWN_MASK) != 0) {
			// none of these should be sent, but least of all
			// Alt-C and Alt-V for copy/paste.
			return;
		}
		if (c == 0xffff) { // just meta keys
			return;
		}
		// Assume if CTRL is down, must be ^J not ENTER...
		if (k == KeyEvent.VK_ENTER && (m & InputEvent.CTRL_DOWN_MASK) == 0) {
			c = '\r';
		}
		if (k == KeyEvent.VK_DELETE) {
			c = 0x7f;
		}
		// The keyboard can only produce upper case
		if (c >= '`' && c < 0x7f) {
			c &= 0x5f;
		}
		// only the keyboard generates parity (paper tape already has it)
		if (parity) {
			c = ParityGenerator.parity(c, even);
		} else {
			c = ParityGenerator.noParity(c, even);
		}
		typeChar(c);
	}
	public void keyReleased(KeyEvent e) {}

	private void copyFromTty() {
		String s = text.getSelectedText();
		if (s == null) {
			return;
		}
		StringSelection ss = new StringSelection(s);
		Toolkit.getDefaultToolkit().getSystemClipboard().setContents(ss, null);
	}

	private void pasteToTty() {
		Transferable t = Toolkit.getDefaultToolkit().
				getSystemClipboard().getContents(null);
		try {
			if (t != null && t.isDataFlavorSupported(DataFlavor.stringFlavor)) {
				String text = (String)t.getTransferData(DataFlavor.stringFlavor);
				paster.addText(text);
			}
		} catch (Exception ee) {
		}
	}

	public void mouseClicked(MouseEvent e) {
		if (e.getButton() != MouseEvent.BUTTON2) {
			return;
		}
		pasteToTty();
	}
	public void mouseEntered(MouseEvent e) { }
	public void mouseExited(MouseEvent e) { }
	public void mousePressed(MouseEvent e) { }
	public void mouseReleased(MouseEvent e) { }
	public void mouseDragged(MouseEvent e) { }
	public void mouseMoved(MouseEvent e) { }

	public void actionPerformed(ActionEvent e) {
		if (e.getSource() instanceof JButton) {
			if (e.getSource() == rdr_start) {
				if (reader.isRunning()) {
					rdrStop();
				} else {
					rdrStart();
				}
			}
		}
		if (e.getSource() instanceof JCheckBox) {
			if (e.getSource() == rdr) {
				if (rdr_busy) {
					rdr.setSelected(false);
					return;
				}
				if (!rdr.isSelected()) {
					rdrStop();
				}
				rdr_start.setEnabled(rdr.isSelected());
			}
			return;
		}
		if (!(e.getSource() instanceof JMenuItem)) {
			return;
		}
		JMenuItem m = (JMenuItem)e.getSource();
		if (!m.getActionCommand().equals(".")) {
			if (!fe.menuActions(m)) {
				return; // no changes for us
			}
			envChanged();
			return;
		}
		if (m.getMnemonic() == KeyEvent.VK_C) {
			copyFromTty();
			return;
		}
		if (m.getMnemonic() == KeyEvent.VK_V) {
			pasteToTty();
			return;
		}
		if (m.getMnemonic() == KeyEvent.VK_S) {
			File sav = pickFile("Save");
			if (sav != null) {
				try {
					FileOutputStream fo = new FileOutputStream(sav);
					fo.write(text.getText(0, eol).getBytes());
					fo.close();
					_last = sav;
					// TODO: tear off?
				} catch (Exception ee) {
					// ...
				}
			}
			return;
		}
		if (m.getMnemonic() == KeyEvent.VK_T) {
			tearOff();
			return;
		}
		if (m.getMnemonic() == KeyEvent.VK_P) {
			if (pun_out != null) try {
				pun_out.close();
			} catch (Exception ee) {}
			if (pun_vu != null) {
				pun_vu.update(0, 0);
				pun_vu.repaint();
			}
			pun_cnt.setText("    ");
			pun_bytes = 0;
			pun_mi.setText("Punch");
			pun_out = null;
			pun.setSelected(false);
			pun_vwr.setEnabled(false);
			pun.setEnabled(false);
			File file = pickFile("Punch");
			if (file == null) {
				return;
			}
			try {
				pun_out = new RandomAccessFile(file, "rw");
				pun_mi.setText("Punch - " + file.getName());
				pun.setEnabled(true);
				pun_cnt.setText("   0");
				pun_bytes = 0;
				pun_vwr.setEnabled(true);
				if (pun_vu != null) {
					pun_vu.update(pun_vu.win - 1, pun_vu.win);
					pun_vu.repaint();
				}
			} catch (Exception ee) {
				System.err.println(ee.getMessage());
			}
			return;
		}
		if (m.getMnemonic() == KeyEvent.VK_R) {
			if (rdr_in != null) try {
				rdr_in.close();
			} catch (Exception ee) {}
			if (rdr_vu != null) {
				rdr_vu.update(0, 0);
				rdr_vu.repaint();
			}
			rdr_cnt.setText("    ");
			rdr_bytes = 0;
			rdr_mi.setText("Reader");
			rdr_in = null;
			rdr.setSelected(false);
			rdr.setEnabled(false);
			rdr_pos.setEnabled(false);
			rdr_start.setEnabled(false);
			File file = pickFile("Reader");
			if (file == null) {
				return;
			}
			try {
				rdr_in = new RandomAccessFile(file, "r");
				rdr_mi.setText("Reader - " + file.getName());
				rdr_cnt.setText("   0");
				rdr_bytes = 0;
				rdr.setEnabled(true);
				rdr_pos.setEnabled(true);
				if (rdr_vu != null) {
					rdr_idx = -1;
					rdr_tot = rdr_in.length();
					updateRdrView(0);
					rdr_vu.repaint();
				}
			} catch (Exception ee) {
				System.err.println(ee.getMessage());
			}
			return;
		}
		if (m.getMnemonic() == KeyEvent.VK_Z) {
			if (rdr_in == null) {
				return;
			}
			rdrStop(); // just in case
			rdr.setSelected(false);
			rdr_start.setEnabled(false);
			if (rdr_vu != null) {
				// file pointer is out-of-position
				try {
					rdr_in.seek(rdr_idx);
				} catch (Exception ee) {}
			}
			JFrame jf = new PaperTapePositioner(this, rdr_in, 8, this);
			// cannot use reader until this finishes...
			rdr_busy = true;
		}
		if (m.getMnemonic() == KeyEvent.VK_Y) {
			if (pun_out == null) {
				return;
			}
			// TODO: anything to halt punch?
			// Don't disable punch, see how it goes.
			//pun.setSelected(false);
			if (pun_vu != null) {
				// file pointer is out-of-position?
			}
			JFrame jf = new PaperTapePositioner(this, pun_out, 8, this,
					"PTP", true, true);
		}
		if (m.getMnemonic() == KeyEvent.VK_H) {
			if (_help != null) {
				_help.setVisible(true);
			}
			return;
		}
	}

	public void windowActivated(WindowEvent e) { }
	public void windowClosed(WindowEvent e) { }
	public void windowIconified(WindowEvent e) { }
	public void windowOpened(WindowEvent e) { }
	public void windowDeiconified(WindowEvent e) { }
	public void windowDeactivated(WindowEvent e) { }
	public void windowClosing(WindowEvent e) {
		// rdr_in has new position set
		try {
			rdr_bytes = (int)rdr_in.getFilePointer();
		} catch (Exception ee) {}
		rdr_cnt.setText(String.format("%4d", rdr_bytes));
		rdr_busy = false;
		if (rdr_vu != null) {
			updateRdrView(rdr_bytes);
		}
	}
}
