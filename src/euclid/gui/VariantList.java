package euclid.gui;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import javax.swing.ToolTipManager;

/** Variant list with eye icons, a thin scroll thumb and tooltips for shortened entries. */
final class VariantList extends JComponent {
	static final int ROW = 35, HDR = 35, PAD_TOP = 15, LEFT = 15, TEXT_X = 45, GAP_PILL = 15, RIGHT = 35, BAR_W = 5;   // design: rows every 35 px (20 px text + 15 px gap), 15 px inset
	/** One line of the list: a group header (properties of a blockstate key) or a selectable model. */
	private static final class Item {
		final String key;      // row key of the model, null for a header
		final String header;   // properties text of a header
		int top, h;
		Item(String key, String header) { this.key = key; this.header = header; this.h = key == null ? HDR : ROW; }
		boolean isHeader() { return key == null; }
	}
	private final List<Item> items = new ArrayList<>();
	private int selected = -1;
	private int scrollY = 0;
	private int dragOff = -1;
	private int hoverItem = -1;    // entry under the mouse
	private final HoverFade fade = new HoverFade(this::repaint);
	private int mouseY = -1;
	private final List<Runnable> selListeners = new ArrayList<>();

	VariantList() {
		setOpaque(false);
		setFocusable(true);
		MouseAdapter m = new MouseAdapter() {
			@Override public void mousePressed(MouseEvent e) {
				requestFocusInWindow();
				Rectangle t = thumb();
				if (t != null && e.getX() >= getWidth() - 25 && e.getX() <= getWidth() - 10 && e.getY() >= t.y && e.getY() <= t.y + t.height) {
					dragOff = e.getY() - t.y;
					return;
				}
				int i = itemAt(e.getY());
				if (i >= 0) select(i);
			}
			@Override public void mouseDragged(MouseEvent e) {
				Rectangle t = thumb();
				if (dragOff < 0 || t == null) return;
				int track = getHeight() - 30 - t.height;
				int max = maxScroll();
				if (track > 0) setScroll((int) ((long) (e.getY() - dragOff - 15) * max / track));
			}
			@Override public void mouseReleased(MouseEvent e) { dragOff = -1; }
			@Override public void mouseMoved(MouseEvent e) { mouseY = e.getY(); updateHover(); }
			@Override public void mouseExited(MouseEvent e) { mouseY = -1; updateHover(); }
			@Override public void mouseWheelMoved(MouseWheelEvent e) { setScroll(scrollY + e.getWheelRotation() * ROW); }
		};
		addMouseListener(m);
		addMouseMotionListener(m);
		addMouseWheelListener(m);
		addComponentListener(new ComponentAdapter() {
			@Override public void componentResized(ComponentEvent e) { setScroll(scrollY); }
		});
		getInputMap().put(KeyStroke.getKeyStroke("DOWN"), "down");
		getInputMap().put(KeyStroke.getKeyStroke("UP"), "up");
		getActionMap().put("down", new AbstractAction() { public void actionPerformed(ActionEvent e) { step(1); } });
		getActionMap().put("up", new AbstractAction() { public void actionPerformed(ActionEvent e) { step(-1); } });
		ToolTipManager.sharedInstance().registerComponent(this);
	}

	java.util.function.Function<String, String> labeler = k -> k;    // text of a model row
	java.util.function.Function<String, List<String>> badger = k -> Collections.<String>emptyList();   // rotation badges of a model
	java.util.function.Function<String, String> grouper = k -> "";   // properties of the key a model belongs to ("" = no header)
	String display(String key) { return labeler.apply(key); }

	void onSelect(Runnable r) { selListeners.add(r); }
	String selectedKey() { return selected >= 0 && selected < items.size() ? items.get(selected).key : null; }

	void setKeys(Collection<String> ks, String keep) {
		items.clear();
		String lastGroup = null;
		for (String k : ks) {
			String g = grouper.apply(k);
			if (!g.isEmpty() && !g.equals(lastGroup)) items.add(new Item(null, g));
			lastGroup = g;
			items.add(new Item(k, null));
		}
		layout2();
		selected = -1;
		for (int i = 0; i < items.size(); i++) if (!items.get(i).isHeader() && (selected < 0 || items.get(i).key.equals(keep))) {
			if (selected < 0) selected = i;
			if (items.get(i).key.equals(keep)) { selected = i; break; }
		}
		scrollY = 0;
		if (selected >= 0) reveal(selected);
		repaint();
		fire();
	}

	void clear() { items.clear(); selected = -1; scrollY = 0; hoverItem = -1; fade.reset(); repaint(); fire(); }

	private void layout2() {
		int y = PAD_TOP;
		for (Item it : items) { it.top = y; y += it.h; }
	}

	private int itemAt(int py) {
		int y = py + scrollY;
		for (int i = 0; i < items.size(); i++) {
			Item it = items.get(i);
			if (y >= it.top && y < it.top + it.h) return it.isHeader() ? -1 : i;
		}
		return -1;
	}

	private void fire() { for (Runnable r : selListeners) r.run(); }

	private void step(int d) {
		for (int i = selected + d; i >= 0 && i < items.size(); i += d)
			if (!items.get(i).isHeader()) { select(i); return; }
	}

	private void select(int i) {
		if (i < 0 || i >= items.size() || i == selected || items.get(i).isHeader()) return;
		selected = i;
		reveal(i);
		repaint();
		fire();
	}

	private void reveal(int i) {
		int top = items.get(i).top, bottom = top + items.get(i).h;
		// show the header of the group together with its first model
		if (i > 0 && items.get(i - 1).isHeader()) top = items.get(i - 1).top;
		if (top < scrollY) setScroll(top - PAD_TOP);
		else if (bottom > scrollY + getHeight()) setScroll(bottom - getHeight());
	}

	private int contentH() { return items.isEmpty() ? 0 : items.get(items.size() - 1).top + items.get(items.size() - 1).h; }
	private int maxScroll() { return Math.max(0, contentH() - getHeight()); }
	private void setScroll(int v) { scrollY = Math.max(0, Math.min(maxScroll(), v)); updateHover(); repaint(); }

	private void updateHover() {
		int h = mouseY < 0 ? -1 : itemAt(mouseY);
		if (h != hoverItem) { hoverItem = h; fade.set(h); repaint(); }
	}

	private Rectangle thumb() {
		int ch = contentH(), h = getHeight();
		if (ch <= h || h <= 30) return null;
		int track = h - 30;   // design: 15 px above and below, 15 px from the right edge
		int th = Math.max(30, (int) ((long) track * h / ch));
		int y = 15 + (int) ((long) (track - th) * scrollY / maxScroll());
		return new Rectangle(getWidth() - 15 - BAR_W, y, BAR_W, th);
	}

	private static final Font[] pillFont = new Font[1];
	private Font pillFont() {
		if (pillFont[0] == null || pillFont[0].getFamily() != Theme.fontSemi.getFamily()) pillFont[0] = Theme.fontSemi.deriveFont(10f);
		return pillFont[0];
	}

	/** Width of the badges of a model incl. the gap in front of them (0 = none). */
	private int badgesW(Item it) {
		List<String> b = badger.apply(it.key);
		if (b.isEmpty()) return 0;
		FontMetrics pf = getFontMetrics(pillFont());
		int w = GAP_PILL;
		for (String s : b) w += pf.stringWidth(s) + 10 + 5;
		return w - 5;
	}

	@Override public String getToolTipText(MouseEvent e) {
		int y = e.getY() + scrollY;
		for (Item it : items) {
			if (y < it.top || y >= it.top + it.h) continue;
			String text = it.isHeader() ? it.header : display(it.key);
			FontMetrics fm = getFontMetrics(Theme.fontLight);
			int x0 = it.isHeader() ? LEFT : TEXT_X;
			int avail = getWidth() - x0 - RIGHT - (it.isHeader() ? 0 : badgesW(it));
			return fm.stringWidth(text) > avail ? text : null;
		}
		return null;
	}

	@Override protected void paintComponent(Graphics gr) {
		Graphics2D g = (Graphics2D) gr.create();
		Theme.aa(g);
		g.setColor(Theme.DARK);
		g.fillRoundRect(0, 0, getWidth(), getHeight(), Theme.ARC, Theme.ARC);
		g.clipRect(0, 0, getWidth(), getHeight());
		g.setFont(Theme.fontLight);
		FontMetrics fm = g.getFontMetrics();
		for (int i = 0; i < items.size(); i++) {
			Item it = items.get(i);
			int top = it.top - scrollY;
			if (top > getHeight()) break;
			if (top + it.h < 0) continue;
			int cy = top + 10;   // the text is 20 px high at the top of the 40 px slot
			float ty = cy - fm.getHeight() / 2f + fm.getAscent();
			if (it.isHeader()) {   // group: properties of the key in Lite, no eye
				g.setColor(Theme.LITE);
				g.drawString(Theme.fit(it.header, fm, getWidth() - LEFT - RIGHT, false), LEFT, ty);
				continue;
			}
			boolean sel = i == selected;
			Color c = sel ? Theme.HERO : Theme.mix(Theme.WHITE, Theme.HERO, fade.get(i));
			g.setColor(c);
			if (sel) Icons.eyeOpen(g, LEFT, cy, Theme.HERO); else Icons.eyeClosed(g, LEFT, cy, c);
			int bw = badgesW(it);
			g.setColor(c);
			String name = Theme.fit(display(it.key), fm, getWidth() - TEXT_X - RIGHT - bw, false);
			g.drawString(name, TEXT_X, ty);
			if (bw > 0) {   // rotation badges right behind the name
				int px = TEXT_X + fm.stringWidth(name) + GAP_PILL;
				Font old = g.getFont();
				g.setFont(pillFont());
				FontMetrics pf = g.getFontMetrics();
				for (String s : badger.apply(it.key)) {
					int w = pf.stringWidth(s) + 10;
					g.setColor(Theme.SEMI);
					g.fillRoundRect(px, top, w, 20, 10, 10);
					g.setColor(c);
					g.drawString(s, px + 5, cy - pf.getHeight() / 2f + pf.getAscent());
					px += w + 5;
				}
				g.setFont(old);
			}
		}
		Rectangle t = thumb();
		if (t != null) {
			g.setColor(Theme.SEMI);
			g.fillRoundRect(t.x, t.y, t.width, t.height, 10, 10);
		}
		g.dispose();
	}
}
