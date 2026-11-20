package euclid.gui;

import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;

import javax.swing.JButton;
import javax.swing.JComponent;

/** Thin vertical scroll bar in the look of the list (the bar has a 15 px border on top, bottom and right). */
final class ThinBar extends javax.swing.plaf.basic.BasicScrollBarUI {
	private static JButton none() { JButton b = new JButton(); b.setPreferredSize(new Dimension(0, 0)); return b; }
	@Override protected JButton createDecreaseButton(int o) { return none(); }
	@Override protected JButton createIncreaseButton(int o) { return none(); }
	@Override protected void paintTrack(Graphics g, JComponent c, Rectangle r) { }
	@Override protected void paintThumb(Graphics g, JComponent c, Rectangle r) {
		if (r.isEmpty()) return;
		Graphics2D g2 = (Graphics2D) g.create();
		Theme.aa(g2);
		g2.setColor(Theme.SEMI);
		g2.fillRoundRect(r.x, r.y, r.width, r.height, 10, 10);
		g2.dispose();
	}
	@Override public Dimension getPreferredSize(JComponent c) { return new Dimension(20, 10); }   // 5 px thumb + 15 px to the edge
}
