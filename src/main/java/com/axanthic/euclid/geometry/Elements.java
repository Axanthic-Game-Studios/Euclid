package com.axanthic.euclid.geometry;

import com.axanthic.euclid.io.Json;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Geometry of a single model element.
 * Turns an element into its 8 corners with the element rotation applied.
 */
public final class Elements {
	/** Returns the 8 corners of an element with its rotation applied. Index = x*4 + y*2 + z (0 = from, 1 = to). */
	public static double[][] corners(Map<String, Object> el) {
		double[] f = Json.vec(el.get("from")), t = Json.vec(el.get("to"));
		double[][] c = new double[8][];
		for (int i = 0; i < 8; i++)
			c[i] = new double[]{(i & 4) != 0 ? t[0] : f[0], (i & 2) != 0 ? t[1] : f[1], (i & 1) != 0 ? t[2] : f[2]};

		if (!(el.get("rotation") instanceof Map)) return c;
		Map<String, Object> r = Json.asMap(el.get("rotation"));
		double[] o = r.containsKey("origin") ? Json.vec(r.get("origin")) : new double[]{8, 8, 8};

		// Axis list: legacy {"axis","angle"} or the multi-axis {"x","y","z"} form.
		int[] axes;
		double[] angles;
		if (r.containsKey("axis")) {
			axes = new int[]{"xyz".indexOf((String) r.get("axis"))};
			angles = new double[]{Json.num(r.get("angle"))};
		} else {
			List<Integer> ax = new ArrayList<>();
			List<Double> an = new ArrayList<>();
			for (int k = 0; k < 3; k++) {
				String key = String.valueOf("xyz".charAt(k));
				if (r.containsKey(key) && Json.num(r.get(key)) != 0.0) { ax.add(k); an.add(Json.num(r.get(key))); }
			}
			axes = ax.stream().mapToInt(Integer::intValue).toArray();
			angles = an.stream().mapToDouble(Double::doubleValue).toArray();
		}

		boolean rescale = Boolean.TRUE.equals(r.get("rescale"));
		for (int i = 0; i < 8; i++) {
			double[] v = {c[i][0] - o[0], c[i][1] - o[1], c[i][2] - o[2]};
			for (int k = 0; k < axes.length; k++) {
				v = Vectors.rotate(v, axes[k], angles[k]);
				if (rescale && Math.abs(angles[k]) < 90) {
					double scale = 1.0 / Math.cos(Math.toRadians(angles[k]));
					for (int d = 0; d < 3; d++) if (d != axes[k]) v[d] *= scale; // vanilla: scale the two non-rotation axes
				}
			}
			c[i] = new double[]{v[0] + o[0], v[1] + o[1], v[2] + o[2]};
		}
		return c;
	}
}
