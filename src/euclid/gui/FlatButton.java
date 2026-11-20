package euclid.gui;

import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;

/** Flat button: Dark, Lite while hovered, label in Lite when disabled. */
final class FlatButton extends JComponent {
	private String label;
	private final IconPainter icon;
	private final HoverFade fade = new HoverFade(this::repaint);
	private boolean active;      // icon in Hero (the settings button while the settings are open)
	private final List<Runnable> actions = new ArrayList<>();

	interface IconPainter { void paint(Graphics2D g, int w, int h); }

	FlatButton(String label) { this(label, null, null); }

	/** Square icon-only button with a tooltip. */
	FlatButton(IconPainter icon, String tip) { this(null, icon, tip); }

	private FlatButton(String label, IconPainter icon, String tip) {
		this.label = label;
		this.icon = icon;
		if (tip != null) setToolTipText(tip);
		setOpaque(false);
		setFocusable(false);
		setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		addMouseListener(new MouseAdapter() {
			@Override public void mouseEntered(MouseEvent e) { fade.set(0); }
			@Override public void mouseExited(MouseEvent e) { fade.set(-1); }
			@Override public void mouseClicked(MouseEvent e) {
				if (isEnabled() && SwingUtilities.isLeftMouseButton(e)) for (Runnable r : actions) r.run();
			}
		});
	}

	void onClick(Runnable r) { actions.add(r); }


	void setActive(boolean on) { active = on; repaint(); }

	/** Runs the click actions (used by the hotkeys); does nothing while the button is disabled. */
	void click() { if (isEnabled()) for (Runnable r : actions) r.run(); }

	@Override public void setEnabled(boolean b) {
		super.setEnabled(b);
		setCursor(Cursor.getPredefinedCursor(b ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
		repaint();
	}

	@Override protected void paintComponent(Graphics gr) {
		Graphics2D g = (Graphics2D) gr.create();
		Theme.aa(g);
		g.setColor(Theme.mix(Theme.DARK, Theme.LITE, isEnabled() ? fade.get(0) : 0));
		g.fillRoundRect(0, 0, getWidth(), getHeight(), Theme.ARC, Theme.ARC);
		g.setColor(!isEnabled() ? Theme.LITE : active ? Theme.HERO : Theme.WHITE);
		if (icon != null) icon.paint(g, getWidth(), getHeight());
		else {
			g.setFont(Theme.fontSemi);
			Theme.drawCentered(g, label, getWidth(), getHeight());
		}
		g.dispose();
	}
}
