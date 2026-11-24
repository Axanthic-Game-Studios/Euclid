package com.axanthic.euclid.gui;

import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import javax.swing.ToolTipManager;

/** The three work-mode buttons (Outline, Boxes, a later feature) left of the viewer. The column stays centred at the vertical
 *  middle of the viewer; the active one grows into the viewer panel (its shape changes, nothing moves). */
final class ModeColumn extends JComponent {
	static final int PITCH = Theme.BTN_H + Theme.GAP, ENABLED = 3;
	private static final String[] TIPS = {"Lines", "Voxel Shape", "Model"};
	int mid;                 // y of the vertical middle of the viewer, in the coordinates of this component (set by the layout)
	private int sel, hover = -1;
	private final HoverFade fade = new HoverFade(this::repaint);
	private final double[] grow = {1, 0, 0};   // how far the neck of each button has grown towards the viewer (1 = arrived), springs towards its target
	private final double[] vel = new double[3];
	private long lastTick, startTick;   // startTick: the animation always ends exactly DURATION seconds after the last click
	private final javax.swing.Timer anim = new javax.swing.Timer(12, e -> tick());
	private static final double STIFF = 206.7, DAMP = 12.32, DURATION = 1.0;   // spring: overshoots by about a fifth and settles after a few swings
	java.util.function.IntConsumer onChange = i -> { };

	ModeColumn() {
		setOpaque(false);
		ToolTipManager.sharedInstance().registerComponent(this);
		MouseAdapter m = new MouseAdapter() {
			@Override public void mousePressed(MouseEvent e) {
				if (!SwingUtilities.isLeftMouseButton(e)) return;
				int i = at(e.getX(), e.getY());
				if (i >= 0 && i != sel && i < ENABLED) { setSelected(i); onChange.accept(i); }
			}
			@Override public void mouseMoved(MouseEvent e) {
				int i = at(e.getX(), e.getY());
				if (i == sel) i = -1;   // the active button has no hover state
				if (i != hover) {
					hover = i;
					fade.set(i);
					setCursor(i >= 0 && i < ENABLED ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
					repaint();
				}
			}
			@Override public void mouseExited(MouseEvent e) { hover = -1; fade.set(-1); setCursor(Cursor.getDefaultCursor()); repaint(); }
		};
		addMouseListener(m);
		addMouseMotionListener(m);
	}

	void setSelected(int i) {
		if (i == sel) return;
		sel = i;
		lastTick = startTick = System.nanoTime();
		anim.start();
		repaint();
	}

	/** Springs every neck towards its target (1 for the active button, 0 for the others). The overshoot and the swings back and forth make the
	 *  connection look like a drop of liquid; an interrupted animation just continues from where it is. */
	private void tick() {
		long now = System.nanoTime();
		double dt = Math.min(0.04, (now - lastTick) / 1e9);
		lastTick = now;
		boolean moving = false;
		for (int i = 0; i < grow.length; i++) {
			double target = i == sel ? 1 : 0;
			for (double s = 0; s < dt; s += 0.004) {
				double h = Math.min(0.004, dt - s);
				vel[i] += (-STIFF * (grow[i] - target) - DAMP * vel[i]) * h;
				grow[i] += vel[i] * h;
			}
			if (Math.abs(grow[i] - target) < 0.002 && Math.abs(vel[i]) < 0.02) { grow[i] = target; vel[i] = 0; }
			else moving = true;
		}
		if (moving && (now - startTick) / 1e9 >= DURATION) {   // the last tiny swings are invisible: end on the second
			for (int i = 0; i < grow.length; i++) { grow[i] = i == sel ? 1 : 0; vel[i] = 0; }
			moving = false;
		}
		if (!moving) anim.stop();
		repaint();
	}

	private int top(int i) { return mid - Theme.BTN_H / 2 + (i - 1) * PITCH; }   // fixed: the middle button sits at the middle of the viewer

	private int at(int x, int y) {
		if (x < 0 || x >= Theme.BTN_H) return -1;
		for (int i = 0; i < 3; i++) if (y >= top(i) && y < top(i) + Theme.BTN_H) return i;
		return -1;
	}

	@Override public String getToolTipText(MouseEvent e) {
		int i = at(e.getX(), e.getY());
		return i >= 0 ? TIPS[i] : null;
	}

	@Override protected void paintComponent(Graphics gr) {
		Graphics2D g = (Graphics2D) gr.create();
		Theme.aa(g);
		int w = getWidth();
		for (int i = 0; i < 3; i++) {
			int y = top(i);
			g.setColor(Theme.mix(Theme.DARK, Theme.LITE, i == sel ? 0 : fade.get(i)));
			g.fillRoundRect(0, y, Theme.BTN_H, Theme.BTN_H, Theme.ARC, Theme.ARC);
			double p = grow[i];
			if (p > 0) {   // at 0 both stubs are still hidden (inside the button / the viewer), so nothing pops in or out
				// The connector: a stub grows out of the button and another one out of the viewer's edge, both from the same horizontal line and
				// getting thicker while they come closer. They touch at p = TOUCH, the bridge between them thickens, and the half circles of the gap
				// grow in until the final form (neck 15 x 10) is reached at p = 1. Un-growing runs the same way backwards.
				final double TOUCH = .85;
				double gx = Theme.BTN_H, gw = w - Theme.BTN_H, r = gw / 2, cy = y + Theme.BTN_H / 2.0;
				double th = Math.max(3, 10 * (.35 + .65 * Math.min(p, 1)) + 40 * Math.max(0, p - 1) - 3 * Math.min(1, Math.abs(vel[i]) / 3));   // swells when it overshoots
				double reach = Math.min(gw / 2 + 4, gw / 2 * p / TOUCH);   // length of each stub
				double b = Math.max(0, Math.min(1, (p - TOUCH) / (1 - TOUCH)));   // 0 = stubs apart, 1 = fully connected
				java.awt.geom.Area a = new java.awt.geom.Area(new java.awt.geom.RoundRectangle2D.Double(gx - 8, cy - th / 2, reach + 8, th, th, th));
				a.add(new java.awt.geom.Area(new java.awt.geom.RoundRectangle2D.Double(w - reach, cy - th / 2, reach + 8, th, th, th)));
				if (b > 0) {
					double bt = th * Math.min(1, b * 1.5);   // the bridge between the stubs thickens first, the half circles follow
					a.add(new java.awt.geom.Area(new java.awt.geom.Rectangle2D.Double(gx, cy - bt / 2, gw, bt)));
					for (int s = 0; s < 2; s++) {
						double edge = s == 0 ? cy - th / 2 : cy + th / 2;
						double fy = s == 0 ? edge - r : edge;   // top of the fillet square
						java.awt.geom.Area fl = new java.awt.geom.Area(new java.awt.geom.Rectangle2D.Double(gx, fy, gw, r));
						fl.subtract(new java.awt.geom.Area(new java.awt.geom.Ellipse2D.Double(gx, s == 0 ? fy - r : fy, gw, gw)));
						java.awt.geom.AffineTransform at = java.awt.geom.AffineTransform.getTranslateInstance(0, edge);   // y' = edge + (y - edge) * b
						at.scale(1, b);
						at.translate(0, -edge);
						a.add(fl.createTransformedArea(at));
					}
				}
				Graphics2D gc = (Graphics2D) g.create();   // only outside the button: a hovered (lighter) button must not show the stub inside itself
				gc.clipRect((int) gx, 0, w - (int) gx, getHeight());
				gc.setColor(Theme.DARK);
				gc.fill(a);
				gc.dispose();
			}
			int ih = i == 2 ? 13 : 17;
			Icons.drawModeIcon(g, i, (Theme.BTN_H - 15) / 2.0, y + (Theme.BTN_H - ih) / 2.0, i == sel ? Theme.HERO : Theme.WHITE);
		}
		g.dispose();
	}
}
