package com.axanthic.euclid.geometry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Vertex registry that merges points closer than ~2e-5 (spatial hash, robust at cell borders). */
final class Verts {
	final List<double[]> pts = new ArrayList<>();
	final Map<String, List<Integer>> cells = new HashMap<>();
	static final double CELL = 1e-3;

	int id(double[] p) {
		long cx = (long) Math.floor(p[0] / CELL), cy = (long) Math.floor(p[1] / CELL), cz = (long) Math.floor(p[2] / CELL);
		for (long x = cx - 1; x <= cx + 1; x++)
			for (long y = cy - 1; y <= cy + 1; y++)
				for (long z = cz - 1; z <= cz + 1; z++) {
					List<Integer> l = cells.get(x + "," + y + "," + z);
					if (l == null) continue;
					for (int i : l) if (Vectors.len(Vectors.minus(pts.get(i), p)) < 2e-5) return i;
				}
		pts.add(p.clone());
		int id = pts.size() - 1;
		String k = cx + "," + cy + "," + cz;
		List<Integer> l = cells.get(k);
		if (l == null) { l = new ArrayList<>(); cells.put(k, l); }
		l.add(id);
		return id;
	}
}
