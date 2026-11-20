package euclid.output;

import euclid.load.Result;
import euclid.load.Row;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Writes the outline JSON of the Lines mode.
 * One entry per condition with the weight and the outlines of each model.
 */
public final class OutlineJson {
	/**
	 * Output: { "variants": { "facing=north": [ {"weight": n, "outlines": [[x1,y1,z1,x2,y2,z2], ...]}, ... ], ... } }
	 * The blockstate condition is the key, its models are the value.
	 */
	public static String toJson(Result r) {
		Map<String, List<String>> byKey = new LinkedHashMap<>();
		for (String rk : r.variants.keySet()) byKey.computeIfAbsent(r.rows.get(rk).key, k -> new ArrayList<>()).add(rk);
		StringBuilder b = new StringBuilder("{").append(nl());
		b.append(ind(1)).append("\"variants\"").append(": {").append(nl());
		int n = 0;
		for (Map.Entry<String, List<String>> e : byKey.entrySet()) {
			List<String> rks = e.getValue();
			b.append(ind(2)).append('"').append(esc(e.getKey())).append('"').append(": ");
			// one entry per model of the key, in the order of the blockstate
			b.append('[').append(nl());
			for (int j = 0; j < rks.size(); j++) {
				b.append(ind(3)).append('{').append(nl());
				Row row = r.rows.get(rks.get(j));
				int w = row.weight;
				b.append(ind(4)).append("\"weight\"").append(": ").append(Math.max(1, w)).append(',').append(nl());   // 1 unless the blockstate gives one
				b.append(ind(4)).append("\"outlines\"").append(": ");
				appendLines(b, r.variants.get(rks.get(j)), 4);
				b.append(nl()).append(ind(3)).append('}');
				if (j < rks.size() - 1) b.append(',');
				b.append(nl());
			}
			b.append(ind(2)).append(']');
			if (++n < byKey.size()) b.append(',');
			b.append(nl());
		}
		b.append(ind(1)).append('}').append(nl());
		b.append('}');
		b.append('\n');
		return b.toString();
	}

	/** Appends [[x1,y1,z1,x2,y2,z2], ...]; each segment on its own row. 'level' = indent of the closing bracket. */
	static void appendLines(StringBuilder b, List<double[]> lines, int level) {
		if (lines.isEmpty()) { b.append("[]"); return; }
		b.append('[').append(nl());
		for (int i = 0; i < lines.size(); i++) {
			double[] l = lines.get(i);
			b.append(ind(level + 1)).append('[');
			for (int k = 0; k < 6; k++) {
				if (k > 0) b.append(", ");
				b.append(fmt(l[k] / 16.0));
			}
			b.append(']');
			if (i < lines.size() - 1) b.append(',');
			b.append(nl());
		}
		b.append(ind(level)).append(']');
	}

	static String fmt(double v) {
		String s = String.format(Locale.ROOT, "%.5f", v);
		s = s.replaceAll("0+$", "").replaceAll("\\.$", "");
		return s.equals("-0") || s.isEmpty() ? "0" : s;
	}

	static String esc(String s) { return s.replace("\\", "\\\\").replace("\"", "\\\""); }

	private static String nl() { return "\n"; }
	private static String ind(int level) {
		StringBuilder b = new StringBuilder();
		for (int i = 0; i < level; i++) b.append("  ");   // JSON: two spaces like vanilla and datagen
		return b.toString();
	}
}
