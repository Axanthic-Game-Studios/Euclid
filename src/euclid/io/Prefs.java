package euclid.io;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.prefs.Preferences;

/**
 * Values remembered between runs (folders, window bounds, hotkeys, output settings).
 * Stored by Java itself in the node of the former default package, so existing settings are kept.
 */
public final class Prefs {
	public static final Preferences PREFS = Preferences.userRoot().node("/<unnamed>");

	public static Path prefDir(String key) {
		try {
			String s = PREFS.get(key, null);
			if (s != null) { Path p = Paths.get(s); if (Files.isDirectory(p)) return p; }
		} catch (Exception ignored) { }
		return null;
	}

	public static void putPref(String key, Path dir) {
		try { if (dir != null) PREFS.put(key, dir.toAbsolutePath().toString()); } catch (Exception ignored) { }
	}
}
