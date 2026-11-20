package euclid.gui;

import euclid.io.Prefs;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.swing.KeyStroke;

/** The configurable hotkeys: one key stroke per action, remembered between runs, conflicts are detected. */
final class Hotkeys {
	static final String[] IDS = {"undo", "redo", "settings", "import", "reload", "export"};
	static final String[] NAMES = {"Undo", "Redo", "Settings", "Import", "Reload", "Export"};
	static final int[] DEFAULT_KEYS = {KeyEvent.VK_Z, KeyEvent.VK_Y, KeyEvent.VK_S, KeyEvent.VK_I, KeyEvent.VK_R, KeyEvent.VK_E};
	static final int MASKS = InputEvent.SHIFT_DOWN_MASK | InputEvent.CTRL_DOWN_MASK | InputEvent.ALT_DOWN_MASK | InputEvent.META_DOWN_MASK;
	static final boolean MAC = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac");
	final KeyStroke[] keys = new KeyStroke[IDS.length];

	Hotkeys() {
		for (int i = 0; i < IDS.length; i++) {
			KeyStroke ks = null;
			try {
				String s = Prefs.PREFS.get("hk." + IDS[i], null);
				if (s != null) { String[] p = s.split(":"); ks = KeyStroke.getKeyStroke(Integer.parseInt(p[0]), Integer.parseInt(p[1])); }
			} catch (Exception ignored) { }
			keys[i] = ks != null ? ks : def(i);
		}
	}

	/** Ctrl on Windows / Linux, Cmd on macOS. */
	static KeyStroke def(int i) { return KeyStroke.getKeyStroke(DEFAULT_KEYS[i], MAC ? InputEvent.META_DOWN_MASK : InputEvent.CTRL_DOWN_MASK); }

	boolean isDefault(int i) { return keys[i].equals(def(i)); }

	void set(int i, KeyStroke ks) {
		keys[i] = ks;
		try {
			if (isDefault(i)) Prefs.PREFS.remove("hk." + IDS[i]); else Prefs.PREFS.put("hk." + IDS[i], ks.getKeyCode() + ":" + ks.getModifiers());
			Prefs.PREFS.flush();
		} catch (Exception ignored) { }
	}

	void reset(int i) { set(i, def(i)); }

	/** Another action uses the same key stroke: neither of them works until this is resolved. */
	boolean conflict(int i) {
		for (int j = 0; j < keys.length; j++) if (j != i && keys[j].equals(keys[i])) return true;
		return false;
	}

	static boolean isModifier(int code) {
		return code == KeyEvent.VK_CONTROL || code == KeyEvent.VK_SHIFT || code == KeyEvent.VK_ALT
				|| code == KeyEvent.VK_META || code == KeyEvent.VK_ALT_GRAPH;
	}

	/** "Ctrl + Shift + Y", "F5", or just "Ctrl" for a modifier key on its own. */
	static String format(KeyStroke ks) {
		int m = ks.getModifiers() & MASKS;
		List<String> parts = new ArrayList<>();
		if ((m & InputEvent.CTRL_DOWN_MASK) != 0) parts.add("Ctrl");
		if ((m & InputEvent.META_DOWN_MASK) != 0) parts.add(MAC ? "Cmd" : "Meta");
		if ((m & InputEvent.ALT_DOWN_MASK) != 0) parts.add(MAC ? "Option" : "Alt");
		if ((m & InputEvent.SHIFT_DOWN_MASK) != 0) parts.add("Shift");
		if (!isModifier(ks.getKeyCode())) parts.add(KeyEvent.getKeyText(ks.getKeyCode()));
		return String.join(" + ", parts);
	}
}
