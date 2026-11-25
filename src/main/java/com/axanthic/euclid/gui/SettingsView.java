package com.axanthic.euclid.gui;

import com.axanthic.euclid.io.Prefs;
import com.axanthic.euclid.load.Multiblock;
import com.axanthic.euclid.output.ModelCode;

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
 * The settings pages (hotkeys, output, viewer, pair and span multiblocks), shown instead of the whole window content.
 * Changes are remembered between runs.
 */
final class SettingsView extends JComponent {
	/**
	 * Rows of the page list from top to bottom, section headlines included.
	 * The page of each row is in NAV_PAGES.
	 */
	private static final String[] NAV = {"General Settings", "Hotkey Settings", "Output Settings", "Viewer Settings",
			"Pair Settings", "Depth Settings", "Height Settings", "Width Settings",
			"Span Settings", "Depth Settings", "Height Settings", "Width Settings"};

	/**
	 * Page that each row of the page list opens, -1 marks a section headline.
	 * Pages 3 to 5 are the pair settings and 6 to 8 the span settings of depth, height and width.
	 */
	private static final int[] NAV_PAGES = {-1, 0, 2, 1, -1, 3, 4, 5, -1, 6, 7, 8};

	private static final String[] OUT_NAMES = {"Parent Identifier", "Texture Slot Location"};
	private static final String[] OUT_KEYS = {"codeParent", "codeTexClass"};

	/**
	 * Values of the output settings the user never changed, shown in their fields.
	 * The reset buttons bring them back.
	 */
	private static final String[] OUT_DEFAULTS = {ModelCode.PARENT_BASE, ModelCode.TEXTURE_SLOT_BASE};

	/**
	 * Axis of the multiblock settings that the depth, height and width pages edit.
	 * The indices are those of Multiblock.AXES.
	 */
	private static final int[] SECTION_AXES = {2, 1, 0};

	/**
	 * Labels of the fields of the pair pages, the property followed by the two values.
	 * The rows are depth, height and width.
	 */
	private static final String[][] PAIR_NAMES = {
			{"Depth Property", "Front Value", "Back Value"},
			{"Height Property", "Bottom Value", "Top Value"},
			{"Width Property", "Left Value", "Right Value"}};

	/**
	 * Labels of the fields of the span pages, the property followed by the three values.
	 * The rows are depth, height and width.
	 */
	private static final String[][] SPAN_NAMES = {
			{"Depth Property", "Front Value", "Center Value", "Back Value"},
			{"Height Property", "Bottom Value", "Center Value", "Top Value"},
			{"Width Property", "Left Value", "Center Value", "Right Value"}};

	/**
	 * Hint shown at the end of a field whose value clashes with another one.
	 * Conflicting multiblock settings are not used.
	 */
	private static final String CONFLICT = "Conflict detected";

	/**
	 * Client property of a text field that holds its value from before it was emptied on entering.
	 * It is null while the field is not focused.
	 */
	private static final String BEFORE = "euclid.before";

	private final JTextField[] outF = new JTextField[OUT_NAMES.length];

	/**
	 * Text fields of the six multiblock pages in the order of the labels.
	 * Only the fields of the shown page are visible.
	 */
	private final JTextField[][] mbF = new JTextField[6][];

	/**
	 * The multiblock settings as currently saved, used for the conflict hints.
	 * It is read again after every change of a multiblock field.
	 */
	private Multiblock multiblock = Multiblock.load();

	private boolean loading;
	private final Hotkeys hk;
	private final MouseButtons mouse;
	private int page = 0;            // 0 = Hotkey, 1 = Viewer, 2 = Output, 3 to 8 = Pair and Span Settings
	private int recording = -1;      // field that waits for a key (hotkeys) or a mouse button (viewer), -1 = none
	private KeyStroke lastMod;       // last modifier pressed while recording (becomes the hotkey if released without a key)
	private int swallow = -1;        // key that was just recorded: its repeats and release must not trigger anything
	private int hoverReset = -1;     // reset button under the mouse
	private int hoverNav = -1;       // row of the page list under the mouse
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
				for (int i = 0; i < NAV.length; i++) if (NAV_PAGES[i] >= 0 && navRect(i).contains(p)) { setPage(NAV_PAGES[i]); return; }
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
				} else if (page == 2) {
					for (int i = 0; i < OUT_NAMES.length; i++) if (resetRect(i).contains(p)) { if (custom(i)) outF[i].setText(OUT_DEFAULTS[i]); repaint(); return; }
				} else {
					var slot = multiblockResetAt(p);
					if (slot >= 0) { mbF[page - 3][slot].setText(SettingsView.multiblockDefault(page - 3, slot)); repaint(); return; }
				}
				recording = -1;   // a click anywhere else cancels the recording
				repaint();
			}
			@Override public void mouseMoved(MouseEvent e) {
				Point p = e.getPoint();
				boolean hand = false;
				for (int i = 0; i < NAV.length; i++) hand |= NAV_PAGES[i] >= 0 && navRect(i).contains(p);
				if (page == 0) for (int i = 0; i < Hotkeys.IDS.length; i++) hand |= fieldRect(i).contains(p) || (resetRect(i).contains(p) && !hk.isDefault(i));
				else if (page == 1) for (int i = 0; i < MouseButtons.IDS.length; i++) hand |= fieldRect(i).contains(p) || (resetRect(i).contains(p) && !mouse.isDefault(i));
				else if (page == 2) for (int i = 0; i < OUT_NAMES.length; i++) hand |= resetRect(i).contains(p) && custom(i);
				else hand |= multiblockResetAt(p) >= 0;
				setCursor(hand ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
				int hn = -1;
				for (int i = 0; i < NAV.length; i++) if (NAV_PAGES[i] >= 0 && navRect(i).contains(p)) hn = i;
				if (hn != hoverNav) { hoverNav = hn; fadeNav.set(hn); repaint(); }
				int hr = -1;
				if (page == 0) for (int i = 0; i < Hotkeys.IDS.length; i++) if (resetRect(i).contains(p) && !hk.isDefault(i)) hr = i;
				if (page == 1) for (int i = 0; i < MouseButtons.IDS.length; i++) if (resetRect(i).contains(p) && !mouse.isDefault(i)) hr = i;
				if (page == 2) for (int i = 0; i < OUT_NAMES.length; i++) if (resetRect(i).contains(p) && custom(i)) hr = i;
				if (page >= 3) hr = multiblockResetAt(p);
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
				if (recording < 0) {
					if (code == swallow) e.consume();
					else if (code == KeyEvent.VK_TAB) { e.consume(); focusField(null, e.isShiftDown()); }   // into the text fields of the page
					return;
				}
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
			outF[i] = newField(OUT_DEFAULTS[i], () -> {
				savePref(OUT_KEYS[idx], outF[idx].getText(), OUT_DEFAULTS[idx]);
				onOutputChanged.run();
			});
		}
		for (var mbPage = 0; mbPage < mbF.length; mbPage++) {
			mbF[mbPage] = new JTextField[Multiblock.slots(mbPage / 3)];
			for (var slot = 0; slot < mbF[mbPage].length; slot++) {
				var key = SettingsView.multiblockKey(mbPage, slot);
				var fallback = SettingsView.multiblockDefault(mbPage, slot);
				var field = mbF[mbPage][slot] = newField(fallback, null);
				field.getDocument().addDocumentListener(new FieldListener(() -> {
					savePref(key, field.getText(), fallback);
					multiblock = Multiblock.load();
					doLayout();
				}));
			}
		}
	}

	/**
	 * Creates a hidden settings text field in the style of the design and adds it to this view, a field showing its default empties when entered.
	 * A field left empty gets its previous value back, the action runs after every edit that is not part of loading the saved values.
	 */
	private JTextField newField(String pDefault, Runnable pOnChange) {
		var f = new JTextField();
		f.setOpaque(false);
		f.setBorder(BorderFactory.createEmptyBorder());
		f.setFont(Theme.fontLight);
		f.setForeground(Theme.WHITE);
		f.setCaretColor(Theme.WHITE);
		f.setSelectionColor(Theme.HERO);
		f.setSelectedTextColor(Color.WHITE);
		f.setFocusTraversalKeysEnabled(false);
		if (pOnChange != null) {
			f.getDocument().addDocumentListener(new FieldListener(pOnChange));
		}
		f.addFocusListener(new FocusAdapter() {
			@Override public void focusGained(FocusEvent e) {
				if (pDefault.isEmpty() || !f.getText().trim().equals(pDefault)) {
					return;
				}
				f.putClientProperty(BEFORE, f.getText());
				loading = true;
				f.setText("");
				loading = false;
				repaint();
			}
			@Override public void focusLost(FocusEvent e) {
				var before = (String) f.getClientProperty(BEFORE);
				f.putClientProperty(BEFORE, null);
				if (f.getText().isBlank()) {
					f.setText(before == null ? pDefault : before);
				}
				showStart(f);   // a long value shows its beginning again as soon as the field is left
				repaint();
			}
		});
		f.addKeyListener(new KeyAdapter() {
			@Override public void keyPressed(KeyEvent e) {
				if (e.getKeyCode() == KeyEvent.VK_ESCAPE || e.getKeyCode() == KeyEvent.VK_ENTER) { e.consume(); requestFocusInWindow(); }
				if (e.getKeyCode() == KeyEvent.VK_TAB) { e.consume(); focusField(f, e.isShiftDown()); }
			}
		});
		add(f);
		f.setVisible(false);
		return f;
	}

	/**
	 * Gives the text fields of the shown page in their order from top to bottom.
	 * Pages without text fields give none.
	 */
	private JTextField[] pageFields() {
		return page == 2 ? outF : page >= 3 ? mbF[page - 3] : new JTextField[0];
	}

	/**
	 * Tells whether any setting of a page clashes with another one.
	 * The page list marks such pages while another page is shown.
	 */
	private boolean pageConflict(int pPage) {
		if (pPage == 0) {
			return java.util.stream.IntStream.range(0, Hotkeys.IDS.length).anyMatch(hk::conflict);
		}
		if (pPage == 1) {
			return java.util.stream.IntStream.range(0, MouseButtons.IDS.length).anyMatch(mouse::conflict);
		}
		if (pPage == 2) {
			return false;
		}
		var mbPage = pPage - 3;
		return java.util.stream.IntStream.range(0, mbF[mbPage].length).anyMatch(slot -> multiblockConflict(mbPage, slot));
	}

	/**
	 * Moves the focus from a field to the next field of the shown page, or to the previous one, wrapping around at the ends.
	 * Without a field, the first or the last field gets the focus.
	 */
	private void focusField(JTextField pFrom, boolean pBackwards) {
		var fields = pageFields();
		if (fields.length == 0) {
			return;
		}
		var index = java.util.Arrays.asList(fields).indexOf(pFrom);
		var next = index < 0 ? (pBackwards ? fields.length - 1 : 0) : Math.floorMod(index + (pBackwards ? -1 : 1), fields.length);
		fields[next].requestFocusInWindow();
	}

	/**
	 * Paints the value a focused, emptied field had before, as ghost text like the hotkey fields.
	 * It comes back when the field is left empty.
	 */
	private static void paintGhost(Graphics2D pGraphics, JTextField pField, Rectangle pRect) {
		var before = (String) pField.getClientProperty(BEFORE);
		if (before == null || before.isEmpty() || !pField.getText().isEmpty()) {
			return;
		}
		pGraphics.setFont(Theme.fontLight);
		var shown = Theme.fit(before, pGraphics.getFontMetrics(), pRect.width - 30, false);
		text(pGraphics, shown, pRect.x + 15, pRect.y + 13, Theme.fontLight, Theme.LITE);
	}

	/**
	 * Saves a trimmed setting, or removes it from the preferences when it is empty or equals its default.
	 * Failures of the preferences store are ignored.
	 */
	private static void savePref(String pKey, String pValue, String pDefault) {
		try {
			var v = pValue.trim();
			if (v.isEmpty() || v.equals(pDefault)) {
				Prefs.PREFS.remove(pKey);
			} else {
				Prefs.PREFS.put(pKey, v);
			}
			Prefs.PREFS.flush();
		} catch (Exception ignored) {
		}
	}

	/**
	 * Runs an action and repaints the view after every edit of a settings field.
	 * Edits while the saved values are loaded are skipped.
	 */
	private final class FieldListener implements javax.swing.event.DocumentListener {
		private final Runnable action;

		FieldListener(Runnable pAction) {
			action = pAction;
		}

		private void changed() {
			if (loading) {
				return;
			}
			action.run();
			repaint();
		}

		@Override public void insertUpdate(javax.swing.event.DocumentEvent e) { changed(); }
		@Override public void removeUpdate(javax.swing.event.DocumentEvent e) { changed(); }
		@Override public void changedUpdate(javax.swing.event.DocumentEvent e) { changed(); }
	}

	@Override public void doLayout() {
		for (int i = 0; i < OUT_NAMES.length; i++) { Rectangle r = fieldRect(i); outF[i].setBounds(r.x + 15, r.y + 12, r.width - 30, 21); }
		for (var mbPage = 0; mbPage < mbF.length; mbPage++) {
			for (var slot = 0; slot < mbF[mbPage].length; slot++) {
				var r = fieldRect(slot);
				var hint = multiblockConflict(mbPage, slot) ? getFontMetrics(Theme.fontLight).stringWidth(CONFLICT) + 45 : 0;
				mbF[mbPage][slot].setBounds(r.x + 15, r.y + 12, r.width - 30 - hint, 21);
			}
		}
	}

	boolean isRecording() { return recording >= 0; }

	@Override public String getToolTipText(MouseEvent e) {
		Point p = e.getPoint();
		int n = page == 0 ? Hotkeys.IDS.length : page == 1 ? MouseButtons.IDS.length : page == 2 ? OUT_NAMES.length : mbF[page - 3].length;
		for (int i = 0; i < n; i++) if (resetRect(i).contains(p)) return "Reset";
		return null;
	}

	private boolean custom(int i) { return !SettingsView.value(outF[i], OUT_DEFAULTS[i]).equals(OUT_DEFAULTS[i]); }

	/**
	 * Gives the value a field stands for, the value from before it was emptied while it is entered and empty.
	 * A field without such a value stands for its default when empty.
	 */
	private static String value(JTextField pField, String pDefault) {
		var text = pField.getText().trim();
		if (!text.isEmpty()) {
			return text;
		}
		var before = (String) pField.getClientProperty(BEFORE);
		return before == null ? pDefault : before.trim();
	}

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
		for (int i = 0; i < OUT_NAMES.length; i++) { outF[i].setText(Prefs.PREFS.get(OUT_KEYS[i], OUT_DEFAULTS[i])); showStart(outF[i]); }
		for (var mbPage = 0; mbPage < mbF.length; mbPage++) {
			for (var slot = 0; slot < mbF[mbPage].length; slot++) {
				mbF[mbPage][slot].setText(Prefs.PREFS.get(SettingsView.multiblockKey(mbPage, slot), SettingsView.multiblockDefault(mbPage, slot)));
				showStart(mbF[mbPage][slot]);
			}
		}
		loading = false;
		multiblock = Multiblock.load();
		doLayout();
		syncFields();
		repaint();
	}

	private void syncFields() {
		for (JTextField f : outF) f.setVisible(page == 2);
		for (var mbPage = 0; mbPage < mbF.length; mbPage++) {
			for (var field : mbF[mbPage]) {
				field.setVisible(page == mbPage + 3);
			}
		}
	}

	private void setPage(int p) { page = p; recording = -1; syncFields(); repaint(); }

	/**
	 * Gives the axis of the multiblock settings that a multiblock page edits.
	 * The pages run through depth, height and width, first for pairs and then for spans.
	 */
	private static int multiblockAxis(int pMbPage) {
		return SECTION_AXES[pMbPage % 3];
	}

	/**
	 * Gives the slot of the multiblock settings that a field of a multiblock page edits.
	 * Width lists left first, but left is the last cell, so its values run backwards.
	 */
	private static int multiblockSlot(int pMbPage, int pField) {
		if (pField == 0 || SettingsView.multiblockAxis(pMbPage) != 0) {
			return pField;
		}
		return Multiblock.slots(pMbPage / 3) - pField;
	}

	/**
	 * Builds the preference key of a field of a multiblock page.
	 * Pair pages come first, span pages second.
	 */
	private static String multiblockKey(int pMbPage, int pField) {
		return Multiblock.key(pMbPage / 3, SettingsView.multiblockAxis(pMbPage), SettingsView.multiblockSlot(pMbPage, pField));
	}

	/**
	 * Gives the default of a field of a multiblock page, empty if it has none.
	 * The fields show it until the user changes them.
	 */
	private static String multiblockDefault(int pMbPage, int pField) {
		return Multiblock.defaultValue(pMbPage / 3, SettingsView.multiblockAxis(pMbPage), SettingsView.multiblockSlot(pMbPage, pField));
	}

	/**
	 * Tells whether a field of a multiblock page holds something other than its default.
	 * Only such fields have something to reset.
	 */
	private static boolean multiblockCustom(JTextField pField, int pMbPage, int pSlot) {
		var fallback = SettingsView.multiblockDefault(pMbPage, pSlot);
		return !SettingsView.value(pField, fallback).equals(fallback);
	}

	/**
	 * Tells whether the setting of a field of a multiblock page clashes with another one.
	 * Such fields show the conflict hint.
	 */
	private boolean multiblockConflict(int pMbPage, int pField) {
		return multiblock.conflict(pMbPage / 3, SettingsView.multiblockAxis(pMbPage), SettingsView.multiblockSlot(pMbPage, pField));
	}

	/**
	 * Finds the reset button of the shown multiblock page under the point whose field differs from its default.
	 * Gives -1 on other pages, on unchanged fields and off the reset buttons.
	 */
	private int multiblockResetAt(Point pPoint) {
		if (page < 3) {
			return -1;
		}
		var fields = mbF[page - 3];
		for (var slot = 0; slot < fields.length; slot++) {
			if (resetRect(slot).contains(pPoint) && SettingsView.multiblockCustom(fields[slot], page - 3, slot)) {
				return slot;
			}
		}
		return -1;
	}

	// ---- geometry (design coordinates, 1920 wide; the content column moves left in narrow windows)
	private int cx() { return Math.max(510, (getWidth() - 420) / 2); }   // design: x = 750 at 1920 px, centred; never closer than 15 px to the nav panel
	private Rectangle navRect(int i) { return new Rectangle(75, 83 + 35 * i, 420, 35); }
	private Rectangle fieldRect(int i) { return new Rectangle(cx(), 110 + 95 * i, 360, 45); }
	private Rectangle resetRect(int i) { return new Rectangle(cx() + 375, 110 + 95 * i, 45, 45); }

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
		g.setColor(Theme.DARK);
		g.fillRoundRect(75, 75, 420, 30 + 35 * NAV.length - 15, Theme.ARC, Theme.ARC);   // 15 px padding, rows every 35 px
		for (int i = 0; i < NAV.length; i++) {
			if (NAV_PAGES[i] < 0) {
				text(g, NAV[i], 90, 90 + 35 * i, Theme.fontLight, Theme.LITE);
				continue;
			}
			Color c = page == NAV_PAGES[i] ? Theme.HERO : Theme.mix(Theme.WHITE, Theme.HERO, fadeNav.get(i));
			Graphics2D gg = (Graphics2D) g.create();
			gg.translate(90, 92 + 35 * i);
			gg.setColor(c);
			Icons.drawGear(gg, 15, 16);
			gg.dispose();
			text(g, NAV[i], 120, 90 + 35 * i, Theme.fontLight, c);
			if (NAV_PAGES[i] != page && pageConflict(NAV_PAGES[i])) {
				g.setFont(Theme.fontLight);
				var sw = g.getFontMetrics().stringWidth(CONFLICT);
				text(g, CONFLICT, 75 + 420 - 45 - sw, 90 + 35 * i, Theme.fontLight, Theme.ERROR);
				Icons.drawErrorIcon(g, 75 + 420 - 30, 92 + 35 * i);
			}
		}

		Graphics2D c = (Graphics2D) g.create();
		if (page >= 3) {
			paintMultiblock(c);
		} else if (page == 2) {
			for (int i = 0; i < OUT_NAMES.length; i++) {
				text(c, OUT_NAMES[i], cx(), 75 + 95 * i, Theme.fontSemi, Theme.WHITE);
				Rectangle f = fieldRect(i), r = resetRect(i);
				c.setColor(Theme.DARK);
				c.fillRoundRect(f.x, f.y, f.width, f.height, Theme.ARC, Theme.ARC);
				c.setColor(Theme.mix(Theme.DARK, Theme.LITE, fadeReset.get(i)));
				c.fillRoundRect(r.x, r.y, r.width, r.height, Theme.ARC, Theme.ARC);
				SettingsView.paintGhost(c, outF[i], f);
				Icons.drawReset(c, r.x + 15, r.y + 15, custom(i) ? Theme.WHITE : Theme.LITE);
			}
		} else if (page == 1) {
			for (int i = 0; i < MouseButtons.IDS.length; i++) {
				text(c, MouseButtons.NAMES[i], cx(), 75 + 95 * i, Theme.fontSemi, Theme.WHITE);
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
				text(c, Hotkeys.NAMES[i], cx(), 75 + 95 * i, Theme.fontSemi, Theme.WHITE);
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

	/**
	 * Paints the labels, fields and reset buttons of the shown multiblock page.
	 * Conflicting settings get the same hint as conflicting hotkeys.
	 */
	private void paintMultiblock(Graphics2D pGraphics) {
		var mbPage = page - 3;
		var names = mbPage < 3 ? PAIR_NAMES[mbPage % 3] : SPAN_NAMES[mbPage % 3];
		for (var slot = 0; slot < names.length; slot++) {
			var f = fieldRect(slot);
			var r = resetRect(slot);
			text(pGraphics, names[slot], cx(), 75 + 95 * slot, Theme.fontSemi, Theme.WHITE);
			pGraphics.setColor(Theme.DARK);
			pGraphics.fillRoundRect(f.x, f.y, f.width, f.height, Theme.ARC, Theme.ARC);
			pGraphics.setColor(Theme.mix(Theme.DARK, Theme.LITE, fadeReset.get(slot)));
			pGraphics.fillRoundRect(r.x, r.y, r.width, r.height, Theme.ARC, Theme.ARC);
			if (multiblockConflict(mbPage, slot)) {
				pGraphics.setFont(Theme.fontLight);
				var sw = pGraphics.getFontMetrics().stringWidth(CONFLICT);
				text(pGraphics, CONFLICT, f.x + f.width - 45 - sw, f.y + 13, Theme.fontLight, Theme.ERROR);
				Icons.drawErrorIcon(pGraphics, f.x + f.width - 30, f.y + 15);
			}
			SettingsView.paintGhost(pGraphics, mbF[mbPage][slot], f);
			Icons.drawReset(pGraphics, r.x + 15, r.y + 15, SettingsView.multiblockCustom(mbF[mbPage][slot], mbPage, slot) ? Theme.WHITE : Theme.LITE);
		}
	}
}
