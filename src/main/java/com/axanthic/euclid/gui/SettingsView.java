package com.axanthic.euclid.gui;

import com.axanthic.euclid.io.Prefs;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.ToolTipManager;

/**
 * The settings pages (hotkeys, viewer, output), shown instead of the whole window content.
 * Changes are remembered between runs.
 */
final class SettingsView extends JComponent {
	private static final String[] NAV = {"Hotkey Settings", "Viewer Settings", "Output Settings"};
	private static final String[] OUT_NAMES = {"Parent Identifier", "Texture Slot Location"};
	private static final String[] OUT_KEYS = {"codeParent", "codeTexClass"};
	private final JTextField[] outF = new JTextField[OUT_NAMES.length];
	private boolean loading;
	private final Hotkeys hk;
	private final MouseButtons mouse;
	private int page = 0;            // 0 = Hotkey, 1 = Viewer, 2 = Output Settings
	private int recording = -1;      // field that waits for a key (hotkeys) or a mouse button (viewer), -1 = none
	private KeyStroke lastMod;       // last modifier pressed while recording (becomes the hotkey if released without a key)
	private int swallow = -1;        // key that was just recorded: its repeats and release must not trigger anything
	private int hoverReset = -1;     // reset button under the mouse
	private int hoverNav = -1;       // entry of the page list under the mouse
	private final HoverFade fadeNav = new HoverFade(this::repaint), fadeReset = new HoverFade(this::repaint);
	Runnable onHotkeysChanged = () -> { };
	Runnable onOutputChanged = () -> { };

	SettingsView(Hotkeys hk, MouseButtons mouse) {
		this.hk = hk;
		this.mouse = mouse;
		setOpaque(false);
		setFocusable(true);
		setFocusTraversalKeysEnabled(false);   // Tab can be recorded as a hotkey
		MouseAdapter m = new MouseAdapter() {
			@Override public void mousePressed(MouseEvent e) {
				requestFocusInWindow();
				Point p = e.getPoint();
				if (page == 1 && recording >= 0 && fieldRect(recording).contains(p)) {   // any button on the waiting field is taken over
					mouse.set(recording, e.getButton());
					recording = -1;
					repaint();
					return;
				}
				if (!SwingUtilities.isLeftMouseButton(e)) return;
				for (int i = 0; i < NAV.length; i++) if (navRect(i).contains(p)) { setPage(i); return; }
				if (page == 1) {
					for (int i = 0; i < MouseButtons.IDS.length; i++) {
						if (resetRect(i).contains(p)) {
							if (!mouse.isDefault(i)) { recording = -1; mouse.reset(i); }
							repaint();
							return;
						}
						if (fieldRect(i).contains(p)) { recording = i; repaint(); return; }
					}
				} else if (page == 0) {
					for (int i = 0; i < Hotkeys.IDS.length; i++) {
						if (resetRect(i).contains(p)) {
							if (!hk.isDefault(i)) { recording = -1; hk.reset(i); onHotkeysChanged.run(); }
							repaint();
							return;
						}
						if (fieldRect(i).contains(p)) { recording = i; lastMod = null; repaint(); return; }
					}
				} else {
					for (int i = 0; i < OUT_NAMES.length; i++) if (resetRect(i).contains(p)) { if (custom(i)) outF[i].setText(""); repaint(); return; }
				}
				recording = -1;   // a click anywhere else cancels the recording
				repaint();
			}
			@Override public void mouseMoved(MouseEvent e) {
				Point p = e.getPoint();
				boolean hand = false;
				for (int i = 0; i < NAV.length; i++) hand |= navRect(i).contains(p);
				if (page == 0) for (int i = 0; i < Hotkeys.IDS.length; i++) hand |= fieldRect(i).contains(p) || (resetRect(i).contains(p) && !hk.isDefault(i));
				else if (page == 1) for (int i = 0; i < MouseButtons.IDS.length; i++) hand |= fieldRect(i).contains(p) || (resetRect(i).contains(p) && !mouse.isDefault(i));
				else for (int i = 0; i < OUT_NAMES.length; i++) hand |= resetRect(i).contains(p) && custom(i);
				setCursor(hand ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
				int hn = -1;
				for (int i = 0; i < NAV.length; i++) if (navRect(i).contains(p)) hn = i;
				if (hn != hoverNav) { hoverNav = hn; fadeNav.set(hn); repaint(); }
				int hr = -1;
				if (page == 0) for (int i = 0; i < Hotkeys.IDS.length; i++) if (resetRect(i).contains(p) && !hk.isDefault(i)) hr = i;
				if (page == 1) for (int i = 0; i < MouseButtons.IDS.length; i++) if (resetRect(i).contains(p) && !mouse.isDefault(i)) hr = i;
				if (page == 2) for (int i = 0; i < OUT_NAMES.length; i++) if (resetRect(i).contains(p) && custom(i)) hr = i;
				if (hr != hoverReset) { hoverReset = hr; fadeReset.set(hr); repaint(); }
			}
			@Override public void mouseExited(MouseEvent e) { if (hoverReset >= 0 || hoverNav >= 0) { hoverReset = -1; hoverNav = -1; fadeNav.set(-1); fadeReset.set(-1); repaint(); } }
		};
		addMouseListener(m);
		addMouseMotionListener(m);
		ToolTipManager.sharedInstance().registerComponent(this);
		addFocusListener(new FocusAdapter() { @Override public void focusLost(FocusEvent e) { recording = -1; repaint(); } });
		initOutputFields();
		addKeyListener(new KeyAdapter() {
			@Override public void keyPressed(KeyEvent e) {
				int code = e.getKeyCode();
				if (recording < 0) { if (code == swallow) e.consume(); return; }
				e.consume();   // while recording no other hotkey may fire
				if (code == KeyEvent.VK_ESCAPE) { recording = -1; repaint(); return; }
				if (page != 0 || code == KeyEvent.VK_UNDEFINED) return;   // the viewer page waits for a mouse button, keys do nothing
				int mods = e.getModifiersEx() & Hotkeys.MASKS;
				if (Hotkeys.isModifier(code)) { lastMod = KeyStroke.getKeyStroke(code, mods); return; }   // a key may still follow
				commit(KeyStroke.getKeyStroke(code, mods), code);
			}
			@Override public void keyReleased(KeyEvent e) {
				int code = e.getKeyCode();
				if (code == swallow) { swallow = -1; e.consume(); return; }
				if (recording < 0) return;
				e.consume();
				if (Hotkeys.isModifier(code) && lastMod != null) commit(lastMod, -1);   // modifier(s) released without a key: they are the hotkey
			}
		});
	}

	private static void showStart(JTextField f) {
		f.setCaretPosition(0);
		f.getHorizontalVisibility().setValue(0);
	}

	private void initOutputFields() {
		setLayout(null);
		for (int i = 0; i < OUT_NAMES.length; i++) {
			final int idx = i;
			JTextField f = new JTextField();
			f.setOpaque(false);
			f.setBorder(BorderFactory.createEmptyBorder());
			f.setFont(Theme.fontLight);
			f.setForeground(Theme.WHITE);
			f.setCaretColor(Theme.WHITE);
			f.setSelectionColor(Theme.HERO);
			f.setSelectedTextColor(Color.WHITE);
			f.setFocusTraversalKeysEnabled(false);
			f.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
				void changed() {
					if (loading) return;
					try {
						String v = f.getText().trim();
						if (v.isEmpty()) Prefs.PREFS.remove(OUT_KEYS[idx]); else Prefs.PREFS.put(OUT_KEYS[idx], v);
						Prefs.PREFS.flush();
					} catch (Exception ignored) { }
					onOutputChanged.run();
					repaint();
				}
				@Override public void insertUpdate(javax.swing.event.DocumentEvent e) { changed(); }
				@Override public void removeUpdate(javax.swing.event.DocumentEvent e) { changed(); }
				@Override public void changedUpdate(javax.swing.event.DocumentEvent e) { changed(); }
			});
			f.addFocusListener(new FocusAdapter() {   // a long value shows its beginning again as soon as the field is left
				@Override public void focusLost(FocusEvent e) { showStart(f); }
			});
			f.addKeyListener(new KeyAdapter() {
				@Override public void keyPressed(KeyEvent e) { if (e.getKeyCode() == KeyEvent.VK_ESCAPE || e.getKeyCode() == KeyEvent.VK_ENTER) { e.consume(); requestFocusInWindow(); } }
			});
			outF[i] = f;
			add(f);
			f.setVisible(false);
		}
	}

	@Override public void doLayout() {
		for (int i = 0; i < OUT_NAMES.length; i++) { Rectangle r = fieldRect(i); outF[i].setBounds(r.x + 15, r.y + 12, r.width - 30, 21); }
	}

	boolean isRecording() { return recording >= 0; }

	@Override public String getToolTipText(MouseEvent e) {
		Point p = e.getPoint();
		int n = page == 0 ? Hotkeys.IDS.length : page == 1 ? MouseButtons.IDS.length : OUT_NAMES.length;
		for (int i = 0; i < n; i++) if (resetRect(i).contains(p)) return "Reset";
		return null;
	}

	private boolean custom(int i) { return !outF[i].getText().trim().isEmpty(); }

	private void commit(KeyStroke ks, int swallowCode) {
		hk.set(recording, ks);
		recording = -1;
		lastMod = null;
		swallow = swallowCode;
		onHotkeysChanged.run();
		repaint();
	}

	/** Back to the first page without a running recording (called when the settings are opened). */
	void reset() {
		page = 0; recording = -1; lastMod = null; swallow = -1; hoverReset = -1; hoverNav = -1; fadeNav.reset(); fadeReset.reset();
		loading = true;
		for (int i = 0; i < OUT_NAMES.length; i++) { outF[i].setText(Prefs.PREFS.get(OUT_KEYS[i], "")); showStart(outF[i]); }
		loading = false;
		syncFields();
		repaint();
	}

	private void syncFields() { for (JTextField f : outF) f.setVisible(page == 2); }

	private void setPage(int p) { page = p; recording = -1; syncFields(); repaint(); }

	// ---- geometry (design coordinates, 1920 wide; the content column moves left in narrow windows)
	private int cx() { return Math.max(510, (getWidth() - 420) / 2 - 30); }   // design: x = 720 at 1920 px; never closer than 15 px to the nav panel
	private Rectangle navRect(int i) { return new Rectangle(75, 143 + 35 * i, 420, 35); }
	private Rectangle fieldRect(int i) { return new Rectangle(cx(), 135 + 95 * i, 360, 45); }
	private Rectangle resetRect(int i) { return new Rectangle(cx() + 375, 135 + 95 * i, 45, 45); }

	private static void text(Graphics2D g, String s, int x, int top, Font f, Color c) {
		g.setFont(f);
		g.setColor(c);
		FontMetrics fm = g.getFontMetrics();
		g.drawString(s, x, top + (20 - fm.getHeight()) / 2f + fm.getAscent());
	}

	@Override protected void paintComponent(Graphics gr) {
		Graphics2D g = (Graphics2D) gr.create();
		Theme.aa(g);
		g.clipRect(0, 0, getWidth(), getHeight());
		text(g, "Settings", 75, 100, Theme.fontSemi, Theme.WHITE);
		g.setColor(Theme.DARK);
		g.fillRoundRect(75, 135, 420, 30 + 35 * NAV.length - 15, Theme.ARC, Theme.ARC);   // 15 px padding, entries every 35 px
		for (int i = 0; i < NAV.length; i++) {
			Color c = page == i ? Theme.HERO : Theme.mix(Theme.WHITE, Theme.HERO, fadeNav.get(i));
			Graphics2D gg = (Graphics2D) g.create();
			gg.translate(90, 152 + 35 * i);
			gg.setColor(c);
			Icons.drawGear(gg, 15, 16);
			gg.dispose();
			text(g, NAV[i], 120, 150 + 35 * i, Theme.fontLight, c);
		}

		Graphics2D c = (Graphics2D) g.create();
		if (page == 2) {
			for (int i = 0; i < OUT_NAMES.length; i++) {
				text(c, OUT_NAMES[i], cx(), 100 + 95 * i, Theme.fontSemi, Theme.WHITE);
				Rectangle f = fieldRect(i), r = resetRect(i);
				c.setColor(Theme.DARK);
				c.fillRoundRect(f.x, f.y, f.width, f.height, Theme.ARC, Theme.ARC);
				c.setColor(Theme.mix(Theme.DARK, Theme.LITE, fadeReset.get(i)));
				c.fillRoundRect(r.x, r.y, r.width, r.height, Theme.ARC, Theme.ARC);
				Icons.drawReset(c, r.x + 15, r.y + 15, custom(i) ? Theme.WHITE : Theme.LITE);
			}
		} else if (page == 1) {
			for (int i = 0; i < MouseButtons.IDS.length; i++) {
				text(c, MouseButtons.NAMES[i], cx(), 100 + 95 * i, Theme.fontSemi, Theme.WHITE);
				Rectangle f = fieldRect(i), r = resetRect(i);
				c.setColor(Theme.DARK);
				c.fillRoundRect(f.x, f.y, f.width, f.height, Theme.ARC, Theme.ARC);
				c.setColor(Theme.mix(Theme.DARK, Theme.LITE, fadeReset.get(i)));
				c.fillRoundRect(r.x, r.y, r.width, r.height, Theme.ARC, Theme.ARC);
				if (recording == i) {
					text(c, "Record Button", f.x + 15, f.y + 13, Theme.fontLight, Theme.LITE);   // ghost text
				} else {
					text(c, MouseButtons.format(mouse.buttons[i]), f.x + 15, f.y + 13, Theme.fontLight, Theme.WHITE);
					if (mouse.conflict(i)) {
						c.setFont(Theme.fontLight);
						FontMetrics fm = c.getFontMetrics();
						String s = "Conflict detected";
						text(c, s, f.x + 315 - fm.stringWidth(s), f.y + 13, Theme.fontLight, Theme.ERROR);
						Icons.drawErrorIcon(c, f.x + 330, f.y + 15);
					}
				}
				Icons.drawReset(c, r.x + 15, r.y + 15, mouse.isDefault(i) ? Theme.LITE : Theme.WHITE);
			}
		} else {
			for (int i = 0; i < Hotkeys.IDS.length; i++) {
				text(c, Hotkeys.NAMES[i], cx(), 100 + 95 * i, Theme.fontSemi, Theme.WHITE);
				Rectangle f = fieldRect(i), r = resetRect(i);
				c.setColor(Theme.DARK);
				c.fillRoundRect(f.x, f.y, f.width, f.height, Theme.ARC, Theme.ARC);
				c.setColor(Theme.mix(Theme.DARK, Theme.LITE, fadeReset.get(i)));   // hover like the other buttons
				c.fillRoundRect(r.x, r.y, r.width, r.height, Theme.ARC, Theme.ARC);
				if (recording == i) {
					text(c, "Record Hotkey", f.x + 15, f.y + 13, Theme.fontLight, Theme.LITE);   // ghost text
				} else {
					text(c, Hotkeys.format(hk.keys[i]), f.x + 15, f.y + 13, Theme.fontLight, Theme.WHITE);
					if (hk.conflict(i)) {
						c.setFont(Theme.fontLight);
						FontMetrics fm = c.getFontMetrics();
						String s = "Conflict detected";
						text(c, s, f.x + 315 - fm.stringWidth(s), f.y + 13, Theme.fontLight, Theme.ERROR);
						Icons.drawErrorIcon(c, f.x + 330, f.y + 15);
					}
				}
				Icons.drawReset(c, r.x + 15, r.y + 15, hk.isDefault(i) ? Theme.LITE : Theme.WHITE);
			}
		}
		c.dispose();

		g.dispose();
	}
}
