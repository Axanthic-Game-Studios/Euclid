package euclid.gui;

import euclid.io.Prefs;

import java.awt.Component;
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/** Root panel: Semi background, preview on the left, sidebar on the right (width can be dragged at its left edge). */
final class Root extends JPanel {
	final MouseButtons mouse = new MouseButtons();
	final Preview preview = new Preview(mouse);
	final FlatButton importB = new FlatButton("Import");
	final FlatButton reloadB = new FlatButton("Reload");
	final FlatButton exportB = new FlatButton("Export");   // outline JSON, or the shape's Java file in the Boxes view
	final VariantList list = new VariantList();
	final FlatButton undoB = new FlatButton((g, w, h) -> Icons.drawUndo(g, w, h, false), "Undo");
	final FlatButton redoB = new FlatButton((g, w, h) -> Icons.drawUndo(g, w, h, true), "Redo");
	final FlatButton settingsB = new FlatButton(Icons::drawGear, "Settings");
	final FlatButton closeB = new FlatButton(Icons::drawClose, "Close");   // top right, only while the settings are open
	final Hotkeys hotkeys = new Hotkeys();
	final ModeColumn modes = new ModeColumn();      // work modes left of the viewer
	final ModelPane code = new ModelPane();         // Model mode: datagen code instead of the 3D view
	int mode;                                       // 0 Outline, 1 Boxes, 2 Model
	boolean boxMode;                                // mode == 1
	Runnable onModeChanged = () -> { };
	final SettingsView settings = new SettingsView(hotkeys, mouse);
	private int sideW = Math.max(Theme.SIDEBAR_W, Prefs.PREFS.getInt("sidebarW", Theme.SIDEBAR_W));   // width of the sidebar (drag the gap in front of it), remembered
	private static final int SIDE_MIN = Theme.SIDEBAR_W, PREVIEW_MIN = 420;
	private int grab = -1;

	/** Shows the settings instead of the viewer and the sidebar (or the other way round). */
	void showSettings(boolean on) {
		for (Component c : getComponents()) if (c != closeB && c != settings) c.setVisible(!on);   // the settings button is gone while the settings are open
		settings.setVisible(on);
		closeB.setVisible(on);
		syncMode();
		if (on) {
			settings.reset();
			SwingUtilities.invokeLater(settings::requestFocusInWindow);
		} else {
			setCursor(Cursor.getDefaultCursor());
		}
		revalidate();
		repaint();
	}

	/** The text field belongs to the Model mode (unless the settings are open). */
	private void syncMode() { code.setVisible(!settings.isVisible() && mode == 2); }

	/** Switches the work mode: 0 = Outline, 1 = Boxes (shape of the model), 2 = Model (datagen code). */
	void setMode(int m) {
		mode = m;
		boxMode = m == 1;
		modes.setSelected(m);
		preview.setBoxMode(m != 0);
		preview.setCodeMode(m == 2);
		syncMode();
		doLayout();
		repaint();
		onModeChanged.run();
	}

	/** Sidebar width that still leaves room for the preview. */
	private int sideWNow() { return Math.max(SIDE_MIN, Math.min(sideW, getWidth() - 2 * Theme.GAP - Theme.BTN_H - Theme.GAP - PREVIEW_MIN)); }
	private boolean onEdge(int x) { int sx = getWidth() - Theme.GAP - sideWNow(); return x >= sx - Theme.GAP && x < sx; }

	Root() {
		super(null);
		setBackground(Theme.SEMI);
		add(code);
		code.setVisible(false);
		add(preview);
		add(modes);
		modes.onChange = this::setMode;
		add(importB);
		add(reloadB);
		add(list);
		add(exportB);
		add(undoB);
		add(redoB);
		add(settingsB);
		add(closeB);
		add(settings);   // last = lowest in z order: the buttons stay usable on top of it
		settings.setVisible(false);
		closeB.setVisible(false);
		MouseAdapter m = new MouseAdapter() {
			@Override public void mousePressed(MouseEvent e) {
				if (settings.isVisible() || !SwingUtilities.isLeftMouseButton(e)) return;
				if (onEdge(e.getX())) grab = e.getX() - (getWidth() - Theme.GAP - sideWNow() - Theme.GAP);
			}
			@Override public void mouseDragged(MouseEvent e) {
				if (grab < 0) return;
				int edge = e.getX() - grab;                // left end of the gap in front of the sidebar
				sideW = getWidth() - Theme.GAP - (edge + Theme.GAP);
				sideW = Math.max(SIDE_MIN, sideWNow());
				doLayout();
				repaint();
			}
			@Override public void mouseReleased(MouseEvent e) {
				try { if (grab >= 0) Prefs.PREFS.putInt("sidebarW", sideWNow()); } catch (Exception ignored) { }
				grab = -1;
			}
			@Override public void mouseMoved(MouseEvent e) {
				if (settings.isVisible()) return;
				setCursor(onEdge(e.getX()) ? Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR) : Cursor.getDefaultCursor());
			}
			@Override public void mouseExited(MouseEvent e) { if (grab < 0) setCursor(Cursor.getDefaultCursor()); }
		};
		addMouseListener(m);
		addMouseMotionListener(m);
	}

	@Override public void doLayout() {
		int w = getWidth(), h = getHeight();
		int sw = sideWNow();
		int sx = w - Theme.GAP - sw;
		int px = Theme.GAP + Theme.BTN_H + Theme.GAP; // viewport right of the narrow button column
		int ph = h - 2 * Theme.GAP;
		preview.setBounds(px, Theme.GAP, sx - Theme.GAP - px, ph);
		code.setBounds(px, Theme.GAP, sx - Theme.GAP - px, ph);   // exactly the viewer panel (the text has its own 15 px inset)
		undoB.setBounds(Theme.GAP, Theme.GAP, Theme.BTN_H, Theme.BTN_H);
		redoB.setBounds(Theme.GAP, Theme.GAP + Theme.BTN_H + Theme.GAP, Theme.BTN_H, Theme.BTN_H);
		settingsB.setBounds(Theme.GAP, h - Theme.GAP - Theme.BTN_H, Theme.BTN_H, Theme.BTN_H);
		int my = 2 * (Theme.GAP + Theme.BTN_H) + Theme.GAP;   // the mode column fills the space between redo and settings
		modes.setBounds(Theme.GAP, my, Theme.BTN_H + Theme.GAP, Math.max(0, h - Theme.GAP - Theme.BTN_H - Theme.GAP - my));
		modes.mid = h / 2 - my;             // its active button sits at the middle of the viewer
		importB.setBounds(sx, Theme.GAP, sw, Theme.BTN_H);
		reloadB.setBounds(sx, Theme.GAP + Theme.BTN_H + Theme.GAP, sw, Theme.BTN_H);
		int listTop = 2 * (Theme.GAP + Theme.BTN_H) + Theme.GAP; // 135 in the design
		exportB.setBounds(sx, h - Theme.GAP - Theme.BTN_H, sw, Theme.BTN_H);
		list.setBounds(sx, listTop, sw, exportB.getY() - Theme.GAP - listTop);
		closeB.setBounds(w - Theme.GAP - Theme.BTN_H, Theme.GAP, Theme.BTN_H, Theme.BTN_H);
		settings.setBounds(0, 0, w, h);
	}

	@Override protected void paintComponent(Graphics g) {
		g.setColor(Theme.SEMI);
		g.fillRect(0, 0, getWidth(), getHeight());
	}
}
