package euclid.geometry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Union outline of cuboids: only the edges where the model touches air.
 * 
 * 1. every cuboid becomes a convex solid with 6 face polygons
 * 2. every face polygon is clipped against all OTHER solids; parts inside another solid
 *    or lying flush against it (opposite normals) are removed, duplicated coplanar
 *    faces are kept only once -> what remains is the visible skin of the union
 * 3. an edge of the skin is drawn only if the faces next to it lie in different planes
 *    (a crease / intersection line). Seams between coplanar faces are dropped.
 * 4. collinear pieces are merged into long lines
 */
public final class Outlines {
	static final double EPS = 1e-6;
	static final double AREA_EPS = 1e-8;
	static final int[][] FACE_IDX = {{0, 1, 3, 2}, {4, 5, 7, 6}, {0, 1, 5, 4}, {2, 3, 7, 6}, {0, 2, 6, 4}, {1, 3, 7, 5}};

	/** Builds the union outline of cuboids given by their 8 corners. */
	public static List<double[]> outlineOf(List<double[][]> cuboids) {
		List<Solid> solids = new ArrayList<>();
		for (double[][] c : cuboids) {
			Solid sol = makeSolid(c);
			if (!sol.flat) solids.add(sol);   // like vanilla: an element without thickness (a plane) has no outline and no shape
		}
		return dedupe(unionOutline(solids));
	}

	public static List<double[]> dedupe(List<double[]> in) {
		Set<String> seen = new HashSet<>();
		List<double[]> out = new ArrayList<>();
		for (double[] l : in) {
			String a = pk(l, 0), b = pk(l, 3);
			if (a.equals(b)) continue;
			String key = a.compareTo(b) < 0 ? a + "|" + b : b + "|" + a;
			if (seen.add(key)) out.add(l);
		}
		return out;
	}

	static String pk(double[] l, int o) {
		return String.format(Locale.ROOT, "%.4f,%.4f,%.4f", Vectors.r4(l[o]), Vectors.r4(l[o + 1]), Vectors.r4(l[o + 2]));
	}

	static Solid makeSolid(double[][] c) {
		Solid s = new Solid();
		double[] ctr = new double[3];
		for (double[] p : c) for (int k = 0; k < 3; k++) ctr[k] += p[k] / 8.0;
		for (int[] idx : FACE_IDX) {
			Face f = new Face();
			f.v = new double[4][];
			double[] fc = new double[3];
			for (int k = 0; k < 4; k++) {
				f.v[k] = c[idx[k]].clone();
				for (int d = 0; d < 3; d++) fc[d] += f.v[k][d] / 4.0;
			}
			double[] n = Vectors.cross(Vectors.minus(f.v[1], f.v[0]), Vectors.minus(f.v[3], f.v[0]));
			double l = Vectors.len(n);
			if (l < 1e-9) { s.flat = true; continue; }
			for (int d = 0; d < 3; d++) n[d] /= l;
			if (Vectors.dot(n, Vectors.minus(fc, ctr)) < 0) for (int d = 0; d < 3; d++) n[d] = -n[d];
			f.n = n;
			f.d = Vectors.dot(n, f.v[0]);
			s.faces.add(f);
		}
		return s;
	}

	static double area(List<double[]> p) {
		if (p.size() < 3) return 0;
		double[] sum = new double[3];
		double[] o = p.get(0);
		for (int i = 1; i + 1 < p.size(); i++) {
			double[] cr = Vectors.cross(Vectors.minus(p.get(i), o), Vectors.minus(p.get(i + 1), o));
			for (int k = 0; k < 3; k++) sum[k] += cr[k];
		}
		return Vectors.len(sum) / 2.0;
	}

	/** Removes from every piece the part that lies inside solid b; returns what stays visible. */
	static List<List<double[]>> subtract(List<List<double[]>> pieces, Face src, int srcIdx, Solid b, int bIdx) {
		List<List<double[]>> result = new ArrayList<>();
		for (List<double[]> piece : pieces) {
			List<double[]> cur = piece;
			for (Face g : b.faces) {
				if (cur == null) break;
				int m = cur.size(), pos = 0, neg = 0;
				double[] sd = new double[m];
				for (int i = 0; i < m; i++) {
					double v = Vectors.dot(g.n, cur.get(i)) - g.d;
					sd[i] = Math.abs(v) <= EPS ? 0 : v;
					if (sd[i] > 0) pos++; else if (sd[i] < 0) neg++;
				}
				if (pos == 0 && neg == 0) {
					// piece lies exactly in this plane of b.
					// opposite normals = flush contact -> counts as inside (removed).
					// same normal = duplicate surface -> only the lower index keeps it.
					boolean keep = Vectors.dot(src.n, g.n) > 0 && bIdx > srcIdx;
					if (keep) { result.add(cur); cur = null; }
					continue;
				}
				if (neg == 0) { result.add(cur); cur = null; continue; } // fully outside this plane
				if (pos == 0) continue;                                    // fully inside, test next plane
				List<double[]> out = new ArrayList<>(), in = new ArrayList<>();
				for (int i = 0; i < m; i++) {
					double[] a = cur.get(i), c = cur.get((i + 1) % m);
					double sa = sd[i], sc = sd[(i + 1) % m];
					if (sa >= 0) out.add(a);
					if (sa <= 0) in.add(a);
					if ((sa > 0 && sc < 0) || (sa < 0 && sc > 0)) {
						double t = sa / (sa - sc);
						double[] p = {a[0] + (c[0] - a[0]) * t, a[1] + (c[1] - a[1]) * t, a[2] + (c[2] - a[2]) * t};
						out.add(p);
						in.add(p);
					}
				}
				if (area(out) > AREA_EPS) result.add(out);
				cur = area(in) > AREA_EPS ? in : null;
			}
			// whatever is left in 'cur' is inside b and dropped
		}
		return result;
	}

	static int planeId(List<double[]> planes, Face f) {
		for (int i = 0; i < planes.size(); i++) {
			double[] p = planes.get(i);
			if (Math.abs(p[0] - f.n[0]) < 1e-5 && Math.abs(p[1] - f.n[1]) < 1e-5 && Math.abs(p[2] - f.n[2]) < 1e-5
					&& Math.abs(p[3] - f.d) < 1e-4) return i;
		}
		planes.add(new double[]{f.n[0], f.n[1], f.n[2], f.d});
		return planes.size() - 1;
	}

	static long segKey(int a, int b) { return ((long) Math.min(a, b) << 32) | (long) Math.max(a, b); }

	static int other(int[] s, int v) { return s[0] == v ? s[1] : s[0]; }

	static List<int[]> mergeCollinear(List<int[]> segs, List<double[]> pts) {
		List<int[]> cur = new ArrayList<>(segs);
		boolean changed = true;
		while (changed) {
			changed = false;
			Map<Integer, List<Integer>> inc = new HashMap<>();
			for (int i = 0; i < cur.size(); i++)
				for (int k = 0; k < 2; k++) {
					List<Integer> l = inc.get(cur.get(i)[k]);
					if (l == null) { l = new ArrayList<>(); inc.put(cur.get(i)[k], l); }
					l.add(i);
				}
			boolean[] dead = new boolean[cur.size()];
			List<int[]> added = new ArrayList<>();
			for (Map.Entry<Integer, List<Integer>> e : inc.entrySet()) {
				int v = e.getKey();
				List<Integer> l = e.getValue();
				outer:
				for (int x = 0; x < l.size(); x++) {
					int i = l.get(x);
					if (dead[i]) continue;
					for (int y = x + 1; y < l.size(); y++) {
						int j = l.get(y);
						if (dead[j]) continue;
						int oi = other(cur.get(i), v), oj = other(cur.get(j), v);
						double[] di = Vectors.minus(pts.get(oi), pts.get(v)), dj = Vectors.minus(pts.get(oj), pts.get(v));
						double li = Vectors.len(di), lj = Vectors.len(dj);
						if (li < 1e-9 || lj < 1e-9) continue;
						if (Vectors.dot(di, dj) / (li * lj) < -1 + 1e-9) {
							dead[i] = true;
							dead[j] = true;
							added.add(new int[]{oi, oj});
							changed = true;
							continue outer;
						}
					}
				}
			}
			if (changed) {
				List<int[]> next = new ArrayList<>();
				for (int i = 0; i < cur.size(); i++) if (!dead[i]) next.add(cur.get(i));
				next.addAll(added);
				cur = next;
			}
		}
		return cur;
	}

	static List<double[]> unionOutline(List<Solid> solids) {
		// --- 1. visible skin ---------------------------------------------------------------
		List<List<double[]>> polys = new ArrayList<>();
		List<Integer> polyPlane = new ArrayList<>();
		List<double[]> planes = new ArrayList<>();
		for (int i = 0; i < solids.size(); i++) {
			for (Face f : solids.get(i).faces) {
				int pid = planeId(planes, f);
				List<List<double[]>> pieces = new ArrayList<>();
				List<double[]> start = new ArrayList<>();
				for (double[] p : f.v) start.add(p);
				pieces.add(start);
				for (int j = 0; j < solids.size() && !pieces.isEmpty(); j++) {
					if (j != i) pieces = subtract(pieces, f, i, solids.get(j), j);
				}
				for (List<double[]> pc : pieces) { polys.add(pc); polyPlane.add(pid); }
			}
		}

		// --- 2. snap vertices, collect polygon edges ---------------------------------------
		Verts verts = new Verts();
		List<int[]> segs = new ArrayList<>(); // {a, b, plane}
		Set<Integer> used = new LinkedHashSet<>();
		for (int k = 0; k < polys.size(); k++) {
			List<double[]> poly = polys.get(k);
			int[] ring = new int[poly.size()];
			int m = 0;
			for (double[] p : poly) {
				int id = verts.id(p);
				if (m == 0 || ring[m - 1] != id) ring[m++] = id;
			}
			if (m > 1 && ring[m - 1] == ring[0]) m--;
			if (m < 3) continue;
			for (int i = 0; i < m; i++) {
				int a = ring[i], b = ring[(i + 1) % m];
				if (a == b) continue;
				segs.add(new int[]{a, b, polyPlane.get(k)});
				used.add(a);
				used.add(b);
			}
		}
		int[] usedArr = new int[used.size()];
		int ui = 0;
		for (int v : used) usedArr[ui++] = v;

		// --- 3. split edges at T-junctions, record which planes touch each piece ------------
		Map<Long, SegInfo> info = new LinkedHashMap<>();
		for (int[] s : segs) {
			double[] pa = verts.pts.get(s[0]), pb = verts.pts.get(s[1]);
			double[] d = Vectors.minus(pb, pa);
			double l2 = Vectors.dot(d, d);
			double[] lo = {Math.min(pa[0], pb[0]) - 1e-5, Math.min(pa[1], pb[1]) - 1e-5, Math.min(pa[2], pb[2]) - 1e-5};
			double[] hi = {Math.max(pa[0], pb[0]) + 1e-5, Math.max(pa[1], pb[1]) + 1e-5, Math.max(pa[2], pb[2]) + 1e-5};
			List<double[]> mids = new ArrayList<>();
			for (int v : usedArr) {
				if (v == s[0] || v == s[1]) continue;
				double[] p = verts.pts.get(v);
				if (p[0] < lo[0] || p[0] > hi[0] || p[1] < lo[1] || p[1] > hi[1] || p[2] < lo[2] || p[2] > hi[2]) continue;
				double t = Vectors.dot(Vectors.minus(p, pa), d) / l2;
				if (t <= 1e-9 || t >= 1 - 1e-9) continue;
				double[] q = {pa[0] + d[0] * t, pa[1] + d[1] * t, pa[2] + d[2] * t};
				if (Vectors.len(Vectors.minus(p, q)) > 1e-5) continue;
				mids.add(new double[]{t, v});
			}
			Collections.sort(mids, new Comparator<double[]>() {
				@Override public int compare(double[] x, double[] y) { return Double.compare(x[0], y[0]); }
			});
			int prev = s[0];
			for (double[] mid : mids) {
				int nxt = (int) mid[1];
				record(info, prev, nxt, s[2]);
				prev = nxt;
			}
			record(info, prev, s[1], s[2]);
		}

		// --- 4. keep creases / boundaries, drop seams between coplanar faces ----------------
		List<int[]> drawn = new ArrayList<>();
		for (SegInfo si : info.values()) {
			boolean seam = si.planes.size() == 1 && si.count >= 2;
			if (!seam) drawn.add(new int[]{si.a, si.b});
		}

		// dedupe + merge collinear pieces
		Map<Long, int[]> uniq = new LinkedHashMap<>();
		for (int[] s : drawn) uniq.put(segKey(s[0], s[1]), s);
		List<int[]> merged = mergeCollinear(new ArrayList<>(uniq.values()), verts.pts);

		List<double[]> out = new ArrayList<>();
		for (int[] s : merged) {
			double[] a = verts.pts.get(s[0]), b = verts.pts.get(s[1]);
			out.add(new double[]{a[0], a[1], a[2], b[0], b[1], b[2]});
		}
		return out;
	}

	private static void record(Map<Long, SegInfo> info, int a, int b, int plane) {
		if (a == b) return;
		long k = segKey(a, b);
		SegInfo si = info.get(k);
		if (si == null) { si = new SegInfo(); si.a = a; si.b = b; info.put(k, si); }
		si.planes.add(plane);
		si.count++;
	}
}
