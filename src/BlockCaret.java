// Copyright (c) 2025 Douglas Miller <durgadas311@gmail.com>

import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import javax.swing.text.*;

class BlockCaret extends DefaultCaret {
	static final Color shadow = new Color(50, 50, 50, 100);
	Typer type;
	int fw;
	int fh;

	public BlockCaret(Typer t, int w, int h) {
		type = t;
		fw = w;
		fh = h;
	}

	// Ignore idea of Dot, always paint cursor at end of text
	// (as recorded by "Typer").
	public void paint(Graphics g) {
		JTextComponent comp = getComponent();
		Rectangle2D r = null;
		try {
			r = comp.modelToView2D(type.getCarr());
		} catch(Exception ee) { }
		if (r == null) return;
		int x = (int)r.getX();
		int y = (int)r.getY();
		g.setColor(shadow);
		g.fillRect(x, y, fw - 1, fh);
	}
}
