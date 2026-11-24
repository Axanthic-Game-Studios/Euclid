package com.axanthic.euclid.gui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.util.ArrayList;
import java.util.List;

/**
 * Vector icons of the design, painted with Java2D.
 * Each one draws into a fixed box in the current or a given colour.
 */
final class Icons {
	/** The logo (hexagon cube) in the current colour; (x, y) = top left of its 16 x 16 box, s = scale. */
	static void drawLogo(Graphics2D g, double x, double y, double s, float stroke) {
		g.setStroke(new BasicStroke((float) (stroke * s), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		double[][] p = {{7.6, 0}, {14.5, 4}, {14.5, 12}, {7.6, 16}, {0.7, 12}, {0.7, 4}};
		java.awt.geom.Path2D hex = new java.awt.geom.Path2D.Double();
		for (int i = 0; i < p.length; i++) {
			double px = x + p[i][0] * s, py = y + p[i][1] * s;
			if (i == 0) hex.moveTo(px, py); else hex.lineTo(px, py);
		}
		hex.closePath();
		g.draw(hex);
		g.draw(new java.awt.geom.Line2D.Double(x + 0.7 * s, y + 4 * s, x + 7.6 * s, y + 8 * s));
		g.draw(new java.awt.geom.Line2D.Double(x + 14.5 * s, y + 4 * s, x + 7.6 * s, y + 8 * s));
		g.draw(new java.awt.geom.Line2D.Double(x + 7.6 * s, y + 8 * s, x + 7.6 * s, y + 16 * s));
	}

	/** Window / taskbar icon: the logo in Hero, rendered at several sizes. */
	static List<Image> appIcons() {
		List<Image> out = new ArrayList<>();
		for (int size : new int[]{16, 24, 32, 48, 64, 128, 256}) {
			java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(size, size, java.awt.image.BufferedImage.TYPE_INT_ARGB);
			Graphics2D g = img.createGraphics();
			Theme.aa(g);
			g.setColor(Theme.HERO);
			double s = size * 0.86 / 16.0; // leave a little margin
			drawLogo(g, (size - 14.5 * s) / 2 - 0.7 * s * 0 - 0.35 * s, size * 0.07, s, size <= 24 ? 2.2f : 1.8f);
			g.dispose();
			out.add(img);
		}
		return out;
	}

	/** Close cross (the design's 15 px icon, centred in its w x h box). */
	static void drawClose(Graphics2D g, int w, int h) {
		Graphics2D gg = (Graphics2D) g.create();
		gg.translate((w - 15) / 2.0, (h - 15) / 2.0);
		gg.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		gg.draw(new java.awt.geom.Line2D.Double(1.4, 1.4, 13.6, 13.6));
		gg.draw(new java.awt.geom.Line2D.Double(13.6, 1.4, 1.4, 13.6));
		gg.dispose();
	}

	/** Red disc with a cut-out "!" (15 x 15 at x, y): load errors and hotkey conflicts. */
	static void drawErrorIcon(Graphics2D g, int x, int y) {
		g.setColor(Theme.ERROR);
		g.fill(new java.awt.geom.Ellipse2D.Double(x, y, 15, 15));
		g.setColor(Theme.DARK);   // cut out like the check mark
		g.setStroke(new BasicStroke(1.7f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND));
		g.draw(new java.awt.geom.Line2D.Double(x + 7.5, y + 3.6, x + 7.5, y + 8.4));
		g.fill(new java.awt.geom.Ellipse2D.Double(x + 6.6, y + 10.0, 1.8, 1.8));
	}

	/** Reset-to-default arrow (15 x 15 at x, y), traced from the design: a 283 degree arc that ends in a filled arrow head at the top left. */
	static void drawReset(Graphics2D g, int x, int y, Color c) {
		Graphics2D gg = (Graphics2D) g.create();
		gg.translate(x, y);
		gg.setColor(c);
		gg.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		gg.draw(new java.awt.geom.Arc2D.Double(1.2, 1.2, 12.2, 12.2, 211.5, 283.5, java.awt.geom.Arc2D.OPEN));
		java.awt.geom.Path2D head = new java.awt.geom.Path2D.Double();
		head.moveTo(0.4, 0.4);
		head.lineTo(0.4, 5.5);
		head.lineTo(5.6, 5.5);
		head.closePath();
		gg.fill(head);
		gg.dispose();
	}

	/** Undo arrow (the design's 15 px icon, centred in its w x h box); redo is the mirror image. */
	static void drawUndo(Graphics2D g, int w, int h, boolean mirror) {
		g.translate(w / 2.0, h / 2.0);
		if (mirror) g.scale(-1, 1);
		g.translate(-7.5, -7.5);
		g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		java.awt.geom.Path2D arrow = new java.awt.geom.Path2D.Double();
		arrow.moveTo(4.5, 0.5); arrow.lineTo(0.5, 4.2); arrow.lineTo(4.5, 7.5);
		g.draw(arrow);
		java.awt.geom.Path2D body = new java.awt.geom.Path2D.Double();
		body.moveTo(0.8, 4.2); body.lineTo(9.5, 4.2);
		body.curveTo(13, 4.2, 14.2, 6.2, 14.2, 8.7);
		body.curveTo(14.2, 11.3, 12.8, 13.5, 9.8, 13.5);
		body.lineTo(3.5, 13.5);
		g.draw(body);
	}

	/** Settings gear: filled six-tooth cog with a hole. */
	static void drawGear(Graphics2D g, int w, int h) {
		java.awt.geom.Area gear = new java.awt.geom.Area(new java.awt.geom.Ellipse2D.Double(-5, -5, 10, 10));
		for (int i = 0; i < 6; i++) {
			java.awt.geom.Area tooth = new java.awt.geom.Area(new java.awt.geom.RoundRectangle2D.Double(-1.7, -7.5, 3.4, 5, 1.2, 1.2));
			tooth.transform(java.awt.geom.AffineTransform.getRotateInstance(Math.PI / 3 * i));
			gear.add(tooth);
		}
		gear.subtract(new java.awt.geom.Area(new java.awt.geom.Ellipse2D.Double(-2.1, -2.1, 4.2, 4.2)));
		g.translate(w / 2.0, h / 2.0);
		g.fill(gear);
	}

	/** 16 x 12 open eye, left edge at x, vertically centred on cy. */
	static void eyeOpen(Graphics2D g, int x, int cy, Color c) {
		java.awt.geom.Path2D p = new java.awt.geom.Path2D.Double();
		p.moveTo(x, cy);
		p.curveTo(x + 3.5, cy - 6.5, x + 12.5, cy - 6.5, x + 16, cy);
		p.curveTo(x + 12.5, cy + 6.5, x + 3.5, cy + 6.5, x, cy);
		p.closePath();
		g.setColor(c);
		g.fill(p);
		g.setColor(Theme.DARK);
		g.fill(new java.awt.geom.Ellipse2D.Double(x + 5.5, cy - 2.5, 5, 5));
	}

	/** 16 x 8 closed eye (lid curve with lashes). */
	static void eyeClosed(Graphics2D g, int x, int cy, Color c) {
		g.setColor(c);
		g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		java.awt.geom.Path2D lid = new java.awt.geom.Path2D.Double();
		lid.moveTo(x, cy - 1.5);
		lid.curveTo(x + 4, cy + 3.5, x + 12, cy + 3.5, x + 16, cy - 1.5);
		g.draw(lid);
		g.draw(new java.awt.geom.Line2D.Double(x + 2.2, cy + 1.2, x + 0.4, cy + 3.4));
		g.draw(new java.awt.geom.Line2D.Double(x + 5.6, cy + 2.4, x + 4.6, cy + 4.6));
		g.draw(new java.awt.geom.Line2D.Double(x + 10.4, cy + 2.4, x + 11.4, cy + 4.6));
		g.draw(new java.awt.geom.Line2D.Double(x + 13.8, cy + 1.2, x + 15.6, cy + 3.4));
	}

	/** Icon of a work mode in its 15 px design box at (x, y): 0 = cube outline with inner Y, 1 = cube with filled top, 2 = code brackets. */
	static void drawModeIcon(Graphics2D g, int mode, double x, double y, Color c) {
		g.setColor(c);
		g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		if (mode == 2) {
			java.awt.geom.Path2D l = new java.awt.geom.Path2D.Double(), r = new java.awt.geom.Path2D.Double();
			l.moveTo(x + 3.9, y + 3.2); l.lineTo(x + 0.8, y + 6.3); l.lineTo(x + 3.9, y + 9.4);
			r.moveTo(x + 11.1, y + 3.2); r.lineTo(x + 14.2, y + 6.3); r.lineTo(x + 11.1, y + 9.4);
			g.draw(l);
			g.draw(r);
			g.draw(new java.awt.geom.Line2D.Double(x + 9.4, y + 0.8, x + 5.6, y + 12.2));
			return;
		}
		double[][] p = {{7.5, 0.75}, {14.25, 4.4}, {14.25, 12.6}, {7.5, 16.25}, {0.75, 12.6}, {0.75, 4.4}};   // top, upper right, lower right, bottom, lower left, upper left
		double cx = 7.5, cy = 8.5;
		java.awt.geom.Path2D hex = new java.awt.geom.Path2D.Double();
		for (int i = 0; i < p.length; i++) { if (i == 0) hex.moveTo(x + p[i][0], y + p[i][1]); else hex.lineTo(x + p[i][0], y + p[i][1]); }
		hex.closePath();
		if (mode == 1) {
			java.awt.geom.Path2D top = new java.awt.geom.Path2D.Double();
			top.moveTo(x + p[0][0], y + p[0][1]); top.lineTo(x + p[1][0], y + p[1][1]); top.lineTo(x + cx, y + cy); top.lineTo(x + p[5][0], y + p[5][1]);
			top.closePath();
			g.fill(top);
		}
		g.draw(hex);
		g.draw(new java.awt.geom.Line2D.Double(x + cx, y + cy, x + p[3][0], y + p[3][1]));
		if (mode == 0) {
			g.draw(new java.awt.geom.Line2D.Double(x + cx, y + cy, x + p[1][0], y + p[1][1]));
			g.draw(new java.awt.geom.Line2D.Double(x + cx, y + cy, x + p[5][0], y + p[5][1]));
		}
	}
}
