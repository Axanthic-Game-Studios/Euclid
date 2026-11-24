package com.axanthic.euclid.output;

import java.util.Locale;

/**
 * Turns names from the files into Java identifiers.
 * Class names in camel case and constant names in capitals.
 */
public final class Names {
	/** "oak_table" -> "OakTable" (camel case for a class name; characters that cannot be part of a name separate the words). */
	public static String camel(String s) {
		StringBuilder b = new StringBuilder();
		for (String w : s.split("[^A-Za-z0-9]+")) if (!w.isEmpty()) b.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
		if (b.length() == 0 || Character.isDigit(b.charAt(0))) b.insert(0, '_');
		return b.toString();
	}

	static String constName(String s) {
		String n = s.replaceAll("[^A-Za-z0-9]+", "_").toUpperCase(Locale.ROOT);
		return n.isEmpty() || Character.isDigit(n.charAt(0)) ? "_" + n : n;
	}
}
