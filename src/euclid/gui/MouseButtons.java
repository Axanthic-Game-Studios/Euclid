package euclid.gui;

import euclid.io.Prefs;

import java.awt.event.MouseEvent;

/** Mouse buttons of the viewer: which one rotates and which one moves the view (remembered like the hotkeys). */
final class MouseButtons {
	static final String[] IDS = {"rotate", "move"};
	static final String[] NAMES = {"Rotate", "Move"};
	static final int[] DEFAULTS = {MouseEvent.BUTTON1, MouseEvent.BUTTON3};
	final int[] buttons = new int[IDS.length];

	MouseButtons() {
		for (int i = 0; i < IDS.length; i++) {
			int b = Prefs.PREFS.getInt("mouse." + IDS[i], DEFAULTS[i]);
			buttons[i] = b >= 1 ? b : DEFAULTS[i];
		}
	}

	boolean isDefault(int i) { return buttons[i] == DEFAULTS[i]; }

	void set(int i, int button) {
		buttons[i] = button;
		try {
			if (isDefault(i)) Prefs.PREFS.remove("mouse." + IDS[i]); else Prefs.PREFS.putInt("mouse." + IDS[i], button);
			Prefs.PREFS.flush();
		} catch (Exception ignored) { }
	}

	void reset(int i) { set(i, DEFAULTS[i]); }

	/** Both actions use the same button: neither of them works until this is resolved. */
	boolean conflict(int i) { return buttons[0] == buttons[1]; }

	/** Does this button trigger the action (0 = rotate, 1 = move)? */
	boolean triggers(int i, int button) { return button == buttons[i] && !conflict(i); }

	static String format(int button) {
		switch (button) {
			case MouseEvent.BUTTON1: return "Left Mouse Button";
			case MouseEvent.BUTTON2: return "Middle Mouse Button";
			case MouseEvent.BUTTON3: return "Right Mouse Button";
			default: return "Mouse Button " + button;
		}
	}
}
