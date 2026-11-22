package euclid.gui;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;

import javax.swing.BorderFactory;
import javax.swing.BoundedRangeModel;
import javax.swing.JComponent;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JViewport;
import javax.swing.ScrollPaneConstants;

/** The Model mode's text field: the datagen code of the selected model, shown inside the viewer's panel. */
final class ModelPane extends JComponent {
	private final JTextArea area = new JTextArea();
	private final JScrollPane scroll = new JScrollPane(area);
	private final JScrollBar bar = new JScrollBar(JScrollBar.VERTICAL);
	private static final int INSET = 15;

	ModelPane() {
		setLayout(null);
		setOpaque(false);
		area.setEditable(false);
		area.setDropTarget(null);   // dropped files fall through to this pane's handler (the window's file drop) instead of the text area's own
		area.setLineWrap(false);   // code lines stay whole; long ones scroll sideways
		area.setOpaque(false);
		area.setBorder(BorderFactory.createEmptyBorder(INSET, INSET, INSET, INSET));   // 15 px around the text; scrolled text runs to the edge of the panel
		area.setAutoscrolls(true);   // selecting past the edge scrolls sideways (there is no horizontal bar)
		area.setTabSize(4);
		area.setFont(Theme.fontMono);
		scroll.setBorder(null);
		scroll.setOpaque(false);
		scroll.getViewport().setOpaque(false);
		scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);   // no horizontal bar: long lines run under the edge
		scroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);   // the bar below lies on top of the text instead of beside it
		JScrollBar inner = scroll.getVerticalScrollBar();
		inner.setUnitIncrement(24);
		bar.setUI(new ThinBar());
		bar.setModel(inner.getModel());   // follows the (hidden) bar of the scroll pane
		bar.setOpaque(false);
		bar.setBorder(BorderFactory.createEmptyBorder(15, 0, 15, 15));
		bar.getModel().addChangeListener(e -> {
			BoundedRangeModel m = bar.getModel();
			bar.setVisible(m.getMaximum() - m.getMinimum() > m.getExtent());
		});
		bar.setVisible(false);
		scroll.addMouseWheelListener(e -> inner.setValue(inner.getValue() + e.getUnitsToScroll() * inner.getUnitIncrement()));   // a hidden bar gets no wheel events from the scroll pane
		// scrolling back to the left ends at the text's 15 px inset (the caret would otherwise stop right at the edge)
		int[] lastX = {0};
		scroll.getViewport().addChangeListener(e -> {
			JViewport vp = scroll.getViewport();
			int x = vp.getViewPosition().x;
			if (x > 0 && x <= INSET && x < lastX[0]) { lastX[0] = 0; vp.setViewPosition(new Point(0, vp.getViewPosition().y)); return; }
			lastX[0] = x;
		});
		add(bar);   // before the scroll pane = above it
		add(scroll);
	}

	void setText(String s) {
		if (s.equals(area.getText())) return;
		area.setText(s);
		area.setCaretPosition(0);
	}

	@Override public void doLayout() {
		scroll.setBounds(0, 0, getWidth(), getHeight());
		bar.setBounds(getWidth() - 20, 0, 20, getHeight());
	}

	/** The text is cut at the rounded corners of the panel. */
	@Override protected void paintChildren(Graphics gr) {
		Graphics2D g = (Graphics2D) gr.create();
		g.clip(new java.awt.geom.RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), Theme.ARC, Theme.ARC));
		super.paintChildren(g);
		g.dispose();
	}

	@Override protected void paintComponent(Graphics gr) {
		area.setForeground(Theme.WHITE);
		area.setSelectionColor(Theme.HERO);
		area.setSelectedTextColor(Color.WHITE);
	}
}
