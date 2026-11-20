package euclid.gui;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;

import javax.swing.BorderFactory;
import javax.swing.UIManager;

/**
 * Design tokens of the GUI (Figma "Euclid"): colours, sizes and fonts.
 * Also holds small painting helpers shared by all components.
 */
public final class Theme {
	// Role colours (variables of the design): SEMI = window, scroll bar thumb and rotation pills, DARK = panels / buttons / fields,
	// LITE = hover and secondary text, WHITE = text and icons, HERO = active elements. There is only one (dark) theme.
	static final Color SEMI = new Color(0x212326);
	static final Color DARK = new Color(0x17181a);
	static final Color LITE = new Color(0x292e33);
	static final Color WHITE = new Color(0xd9ecff);
	static final Color HERO = new Color(0x0080ff);
	static final Color ERROR = new Color(0xFF0000);
	static final int GAP = 15, SIDEBAR_W = 420, BTN_H = 45, ARC = 20; // ARC = 2 x corner radius 10 (pills, thumb: radius 5)

	static Font fontLight = new Font(Font.SANS_SERIF, Font.PLAIN, 15);
	static Font fontMono = new Font(Font.MONOSPACED, Font.PLAIN, 15);
	static Font fontSemi = new Font(Font.SANS_SERIF, Font.BOLD, 15);

	/** Noto Sans Display (SIL Open Font License) is bundled in the jar so the look is identical on every system. */
	public static void loadFonts() {
		try {
			fontLight = loadFont("/fonts/NotoSansDisplay-Regular.ttf", fontLight);
			fontSemi = loadFont("/fonts/NotoSansDisplay-SemiBold.ttf", fontSemi);
			fontMono = loadFont("/fonts/NotoSansMono-Regular.ttf", fontMono);
		} catch (Throwable ignored) { }
	}

	private static Font loadFont(String res, Font fallback) {
		try (java.io.InputStream in = Theme.class.getResourceAsStream(res)) {
			if (in == null) return fallback;
			Font f = Font.createFont(Font.TRUETYPE_FONT, in).deriveFont(15f);
			GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(f);
			return f;
		} catch (Exception e) { return fallback; }
	}

	static void applyTooltipColors() {
		UIManager.put("ToolTip.background", DARK);
		UIManager.put("ToolTip.foreground", WHITE);
		UIManager.put("ToolTip.border", BorderFactory.createLineBorder(LITE));
	}

	static void aa(Graphics2D g) {
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
	}

	static Color mix(Color a, Color b, double t) {
		t = Math.max(0, Math.min(1, t));
		return new Color((int) Math.round(a.getRed() + (b.getRed() - a.getRed()) * t), (int) Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
				(int) Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t));
	}

	/** Cuts a string at the end ("abc …") or at the start ("… abc") so that it fits into maxW. */
	static String fit(String s, FontMetrics fm, int maxW, boolean cutStart) {
		if (fm.stringWidth(s) <= maxW) return s;
		int lo = 0, hi = s.length();
		while (lo < hi) {
			int mid = (lo + hi + 1) >>> 1;
			String t = cutStart ? "… " + s.substring(s.length() - mid) : s.substring(0, mid) + " …";
			if (fm.stringWidth(t) <= maxW) lo = mid; else hi = mid - 1;
		}
		return cutStart ? "… " + s.substring(s.length() - lo) : s.substring(0, lo) + " …";
	}

	static void drawCentered(Graphics2D g, String text, int w, int h) {
		FontMetrics fm = g.getFontMetrics();
		g.drawString(text, (w - fm.stringWidth(text)) / 2f, (h - fm.getHeight()) / 2f + fm.getAscent());
	}
}
