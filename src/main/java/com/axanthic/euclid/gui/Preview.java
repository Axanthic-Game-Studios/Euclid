package com.axanthic.euclid.gui;

import java.awt.BasicStroke;
import java.awt.Cursor;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Collections;
import java.util.List;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import javax.swing.ToolTipManager;

/**
 * The 3D viewer panel: outline lines, boxes, the reference cube and the status line.
 * Lines can be clicked to remove them, the view can be rotated, moved and zoomed.
 */
final class Preview extends JComponent {
	private List<double[]> lines = Collections.emptyList();
	private double yaw = Math.toRadians(-35), pitch = Math.toRadians(25);
	private Point last;
	private double zoom = 1.0;      // set with the mouse wheel
	private double fit = 1.0;       // shrinks the view when the outline (multiblock) leaves the unit cube
	private String message = "";
	private boolean isError;
	private boolean interactive = true;
	private boolean loading;         // a file is being loaded (progress ring instead of the check mark)
	private double progress;         // 0..1
	private int hover = -1;          // index of the line under the mouse (click removes it)
	private Point press;
	private int panX, panY;          // viewport shift in px (the "Move" mouse button)
	java.util.function.IntConsumer onRemove = i -> { };
	private final MouseButtons mouse;
	private int dragButton;          // button of the running drag (drag events do not report it reliably)
	private List<double[]> boxLines = Collections.emptyList();   // outline of the combined boxes, model units (Boxes view)
	private boolean codeMode;        // Model mode: only the panel; the text field lies on top of it
	private boolean boxMode;         // Boxes view: the outline of the shape is shown, the model outline is only a faint reference and cannot be edited

	Preview(MouseButtons mouse) {
		this.mouse = mouse;
		setOpaque(false);
		MouseAdapter m = new MouseAdapter() {
			@Override public void mousePressed(MouseEvent e) { last = e.getPoint(); press = e.getPoint(); dragButton = e.getButton(); }
			@Override public void mouseReleased(MouseEvent e) {
				if (interactive && press != null && SwingUtilities.isLeftMouseButton(e) && press.distance(e.getPoint()) < 4) {
					int i = lineAt(e.getPoint());
					if (i >= 0) { hover = -1; onRemove.accept(i); setCursor(Cursor.getDefaultCursor()); repaint(); }
				}
				press = null;
			}
			@Override public void mouseMoved(MouseEvent e) { updateHover(e.getPoint()); }
			@Override public void mouseExited(MouseEvent e) { updateHover(null); }
			@Override public void mouseDragged(MouseEvent e) {
				int dx = e.getX() - last.x, dy = e.getY() - last.y;
				if (mouse.triggers(1, dragButton)) { panX += dx; panY += dy; }
				else if (mouse.triggers(0, dragButton)) {
					yaw += dx * 0.01;
					pitch = Math.max(-Math.PI / 2, Math.min(Math.PI / 2, pitch + dy * 0.01));
				}
				last = e.getPoint();
				if (hover >= 0) { hover = -1; setCursor(Cursor.getDefaultCursor()); }
				repaint();
			}
		};
		addMouseListener(m);
		addMouseMotionListener(m);
		addMouseWheelListener(e -> {
			zoom = Math.max(0.3, Math.min(6.0, zoom * Math.pow(1.1, -e.getPreciseWheelRotation())));
			repaint();
		});
		ToolTipManager.sharedInstance().registerComponent(this);
	}

	private double scaleNow() { return Math.min(getWidth(), getHeight()) * 0.55 * zoom * fit; }

	/** Squared distance of a point to the segment p-q (screen pixels). */
	private static double segDist2(Point pt, Point p, Point q) {
		double dx = q.x - p.x, dy = q.y - p.y, len2 = dx * dx + dy * dy;
		double u = len2 == 0 ? 0 : Math.max(0, Math.min(1, ((pt.x - p.x) * dx + (pt.y - p.y) * dy) / len2));
		double ex = p.x + u * dx - pt.x, ey = p.y + u * dy - pt.y;
		return ex * ex + ey * ey;
	}

	/** Index of the outline line closest to the point (within 7 px), or -1 (none in the Boxes view). */
	private int lineAt(Point pt) {
		if (boxMode) return -1;
		double scale = scaleNow(), best = 7 * 7;
		int bi = -1;
		for (int i = 0; i < lines.size(); i++) {
			double[] l = lines.get(i);
			double d2 = segDist2(pt, project(l[0] / 16 - .5, l[1] / 16 - .5, l[2] / 16 - .5, scale), project(l[3] / 16 - .5, l[4] / 16 - .5, l[5] / 16 - .5, scale));
			if (d2 <= best) { best = d2; bi = i; }
		}
		return bi;
	}

	private void updateHover(Point pt) {
		int h = pt == null || !interactive ? -1 : lineAt(pt);
		if (h != hover) {
			hover = h;
			setCursor(h >= 0 ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
			repaint();
		}
	}

	void setLines(List<double[]> l) {
		hover = -1;
		lines = l == null ? Collections.<double[]>emptyList() : l;
		refit();
		repaint();
	}

	/** Scales the view so that everything shown (lines, in the Boxes view also the boxes) fits. */
	private void refit() {
		double ext = 0.5; // distance from the cube centre (in blocks) of the furthest point
		for (double[] ln : lines)
			for (int i = 0; i < 6; i++) ext = Math.max(ext, Math.abs(ln[i] / 16 - .5));
		if (boxMode)
			for (double[] b : boxLines)
				for (int i = 0; i < 6; i++) ext = Math.max(ext, Math.abs(b[i] / 16 - .5));
		fit = 0.5 / ext;
	}

	/** Boxes view: the outline of the shape (all boxes combined). */
	void setBoxes(List<double[]> outline) {
		boxLines = outline == null ? Collections.<double[]>emptyList() : outline;
		refit();
		repaint();
	}

	void setCodeMode(boolean on) { codeMode = on; repaint(); }

	void setBoxMode(boolean on) {
		boxMode = on;
		hover = -1;
		setCursor(Cursor.getDefaultCursor());
		refit();
		repaint();
	}

	/** Path of the loaded file (Lite) or an error message (red) at the bottom left. */
	void setMessage(String text, boolean error) { message = text == null ? "" : text; isError = error; loading = false; repaint(); }

	/** Shows the path of a file that is being loaded, with the progress ring at 0. */
	void setLoading(String text) { message = text == null ? "" : text; isError = false; loading = true; progress = 0; repaint(); }

	/** false = the lines can neither be hovered nor clicked (while a file is loading). */
	void setInteractive(boolean on) {
		interactive = on;
		if (!on) { hover = -1; setCursor(Cursor.getDefaultCursor()); }
		repaint();
	}

	void setProgress(double p) { progress = Math.max(0, Math.min(1, p)); repaint(); }

	/** Status symbol (15 x 15) left of the path: progress ring while loading, check mark when loaded, "!" on an error. */
	private void drawState(Graphics2D g, int x, int y) {
		if (loading) {
			g.setStroke(new BasicStroke(2f));
			g.setColor(Theme.LITE);
			g.draw(new java.awt.geom.Ellipse2D.Double(x + 1, y + 1, 13, 13));
			if (progress > 0) {
				g.setColor(Theme.HERO);
				g.draw(new java.awt.geom.Arc2D.Double(x + 1, y + 1, 13, 13, 90, -360 * progress, java.awt.geom.Arc2D.OPEN));
			}
		} else if (isError) {
			Icons.drawErrorIcon(g, x, y);
		} else {
			g.setColor(Theme.LITE);
			g.fill(new java.awt.geom.Ellipse2D.Double(x, y, 15, 15));
			g.setColor(Theme.DARK);
			g.setStroke(new BasicStroke(1.7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			java.awt.geom.Path2D.Double chk = new java.awt.geom.Path2D.Double();
			chk.moveTo(x + 4.2, y + 7.8);
			chk.lineTo(x + 6.5, y + 10.1);
			chk.lineTo(x + 10.9, y + 5.2);
			g.draw(chk);
		}
	}

	@Override public String getToolTipText(MouseEvent e) {
		if (message.isEmpty() || e.getY() < getHeight() - 45) return null;
		FontMetrics fm = getFontMetrics(Theme.fontLight);
		return fm.stringWidth(message) > getWidth() - 60 - Theme.GAP ? message : null;
	}

	private Point project(double x, double y, double z, double scale) {
		double x1 = x * Math.cos(yaw) + z * Math.sin(yaw);
		double z1 = -x * Math.sin(yaw) + z * Math.cos(yaw);
		double y2 = y * Math.cos(pitch) - z1 * Math.sin(pitch);
		return new Point((int) (getWidth() / 2 + panX + x1 * scale), (int) (getHeight() / 2 + panY - y2 * scale));
	}

	@Override protected void paintComponent(Graphics gr) {
		Graphics2D g = (Graphics2D) gr.create();
		Theme.aa(g);
		g.setColor(Theme.DARK);
		g.fillRoundRect(0, 0, getWidth(), getHeight(), Theme.ARC, Theme.ARC);
		g.clip(new java.awt.geom.RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), Theme.ARC, Theme.ARC));

		if (codeMode) { g.dispose(); return; }   // Model mode: the text field covers the whole panel (title and status line are hidden)

		// logo + title (top left)
		g.setColor(Theme.LITE);
		Icons.drawLogo(g, 15.1, 14.8, 0.966, 1.6f); // design: box 15 x 17 at (15, 14)
		g.setFont(Theme.fontSemi);
		FontMetrics fm0 = g.getFontMetrics();
		g.drawString("Euclid", 45, 13 + fm0.getAscent());

		// path / error (bottom left)
		if (!message.isEmpty()) {
			g.setFont(Theme.fontLight);
			FontMetrics fm = g.getFontMetrics();
			g.setColor(isError ? Theme.ERROR : Theme.LITE);
			g.drawString(Theme.fit(message, fm, getWidth() - 60 - Theme.GAP, true), 45, getHeight() - 32 + fm.getAscent());
			drawState(g, 15, getHeight() - 29);
		}

		double scale = scaleNow();

		// reference unit cube
		g.setColor(Theme.LITE);
		g.setStroke(new BasicStroke(1.5f));
		for (int a = 0; a < 8; a++)
			for (int bit : new int[]{1, 2, 4})
				if ((a & bit) == 0) {
					int b = a | bit;
					Point p = project((a & 4) != 0 ? .5 : -.5, (a & 2) != 0 ? .5 : -.5, (a & 1) != 0 ? .5 : -.5, scale);
					Point q = project((b & 4) != 0 ? .5 : -.5, (b & 2) != 0 ? .5 : -.5, (b & 1) != 0 ? .5 : -.5, scale);
					g.drawLine(p.x, p.y, q.x, q.y);
				}

		if (boxMode) {
			// the model outline as a ghost (like the reference cube), then the outline of the shape (all boxes combined)
			g.setColor(Theme.LITE);
			g.setStroke(new BasicStroke(1.5f));
			for (double[] l : lines) {
				Point p = project(l[0] / 16 - .5, l[1] / 16 - .5, l[2] / 16 - .5, scale);
				Point q = project(l[3] / 16 - .5, l[4] / 16 - .5, l[5] / 16 - .5, scale);
				g.drawLine(p.x, p.y, q.x, q.y);
			}
			g.setColor(Theme.HERO);
			g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			for (double[] l : boxLines) {
				Point p = project(l[0] / 16 - .5, l[1] / 16 - .5, l[2] / 16 - .5, scale);
				Point q = project(l[3] / 16 - .5, l[4] / 16 - .5, l[5] / 16 - .5, scale);
				g.drawLine(p.x, p.y, q.x, q.y);
			}
		} else {
		// outline lines (model units -> block space centered on the block)
		g.setColor(Theme.HERO);
		g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		for (double[] l : lines) {
			Point p = project(l[0] / 16 - .5, l[1] / 16 - .5, l[2] / 16 - .5, scale);
			Point q = project(l[3] / 16 - .5, l[4] / 16 - .5, l[5] / 16 - .5, scale);
			g.drawLine(p.x, p.y, q.x, q.y);
		}
		if (hover >= 0 && hover < lines.size()) {
			double[] l = lines.get(hover);
			g.setColor(Theme.ERROR);
			g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			Point p = project(l[0] / 16 - .5, l[1] / 16 - .5, l[2] / 16 - .5, scale);
			Point q = project(l[3] / 16 - .5, l[4] / 16 - .5, l[5] / 16 - .5, scale);
			g.drawLine(p.x, p.y, q.x, q.y);
		}

		}

		// north marker (-Z) so facing/rotation can be checked
		g.setFont(Theme.fontSemi.deriveFont(13f));
		g.setColor(Theme.LITE);
		Point n = project(0, -.5, -.8, scale);
		g.drawString("N (-Z)", n.x - 16, n.y);

		g.dispose();
	}
}
