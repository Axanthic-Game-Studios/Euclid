package euclid;

import euclid.gui.App;
import euclid.gui.Theme;

import java.io.PrintWriter;
import java.io.StringWriter;

import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

/**
 * Euclid - builds block outlines for every variant of a blockstate JSON (assets/<modid>/blockstates/*.json).
 *
 * Start:  java -jar Euclid.jar
 *
 * Build (no dependencies):
 *   javac --release 25 -d out $(find src -name "*.java")   (then copy the fonts/ folder next to the classes into the jar)
 *   jar --create --file Euclid.jar --main-class euclid.Euclid -C out .
 */
public final class Euclid {
	static final String VERSION = "0.24.0";

	static void showError(Throwable t) {
		StringWriter sw = new StringWriter();
		t.printStackTrace(new PrintWriter(sw));
		t.printStackTrace();
		try {
			JTextArea area = new JTextArea(sw.toString(), 16, 70);
			area.setEditable(false);
			JOptionPane.showMessageDialog(null, new JScrollPane(area), "Euclid - error", JOptionPane.ERROR_MESSAGE);
		} catch (Throwable ignored) { }
	}

	public static void main(String[] args) {
		Thread.setDefaultUncaughtExceptionHandler((th, e) -> showError(e));
		try {
			Theme.loadFonts();
			SwingUtilities.invokeLater(App::run);
		} catch (Throwable t) {
			showError(t);
		}
	}
}
