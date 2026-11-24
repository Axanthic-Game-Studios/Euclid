package com.axanthic.euclid.io;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal JSON parser, enough for block model and blockstate files.
 * Also holds the typed accessors used on the parsed maps and lists.
 */
public final class Json {
	private final String s;
	private int i;

	private Json(String s) { this.s = s; }

	public static Object parse(String s) { return new Json(s).value(); }

	private void ws() { while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++; }

	private Object value() {
		ws();
		char c = s.charAt(i);
		switch (c) {
			case '{': return obj();
			case '[': return arr();
			case '"': return str();
			case 't': i += 4; return Boolean.TRUE;
			case 'f': i += 5; return Boolean.FALSE;
			case 'n': i += 4; return null;
			default: return num();
		}
	}

	private Map<String, Object> obj() {
		Map<String, Object> m = new LinkedHashMap<>();
		i++; ws();
		if (s.charAt(i) == '}') { i++; return m; }
		while (true) {
			ws();
			String k = str();
			ws(); i++; // ':'
			m.put(k, value());
			ws();
			if (s.charAt(i++) == '}') return m;
		}
	}

	private List<Object> arr() {
		List<Object> l = new ArrayList<>();
		i++; ws();
		if (s.charAt(i) == ']') { i++; return l; }
		while (true) {
			l.add(value());
			ws();
			if (s.charAt(i++) == ']') return l;
		}
	}

	private String str() {
		i++;
		StringBuilder b = new StringBuilder();
		while (true) {
			char c = s.charAt(i++);
			if (c == '"') break;
			if (c == '\\') {
				char e = s.charAt(i++);
				switch (e) {
					case 'n': b.append('\n'); break;
					case 't': b.append('\t'); break;
					case 'r': b.append('\r'); break;
					case 'u': b.append((char) Integer.parseInt(s.substring(i, i + 4), 16)); i += 4; break;
					default: b.append(e);
				}
			} else b.append(c);
		}
		return b.toString();
	}

	private Double num() {
		int st = i;
		while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
		return Double.parseDouble(s.substring(st, i));
	}

	@SuppressWarnings("unchecked")
	public static Map<String, Object> asMap(Object o) { return (Map<String, Object>) o; }

	@SuppressWarnings("unchecked")
	public static List<Object> asList(Object o) { return (List<Object>) o; }

	public static double num(Object o) { return ((Number) o).doubleValue(); }

	public static double[] vec(Object o) {
		List<Object> l = asList(o);
		return new double[]{num(l.get(0)), num(l.get(1)), num(l.get(2))};
	}

	public static double[] numList(Object o, int n) {
		if (!(o instanceof List) || asList(o).size() < n) return null;
		double[] r = new double[n];
		for (int i = 0; i < n; i++) {
			Object e = asList(o).get(i);
			if (!(e instanceof Number)) return null;
			r[i] = num(e);
		}
		return r;
	}
}
