package euclid.geometry;

import java.util.ArrayList;
import java.util.List;

/**
 * Axis aligned boxes (collision / interaction shapes) that cover the model.
 * 
 * Elements that stay axis aligned (also turned by 90 degrees) give exact boxes. A tilted element is covered
 * with grid cells instead: every cell the element reaches into becomes part of the shape (no margin around it),
 * so thin, flat or steep diagonals can never vanish. The cells are merged into as few boxes as possible.
 */
public final class Boxes {
	static final double GRID = 1;      // cell size in model units (1 unit = 1/16 block): the boxes of tilted elements lie on the pixel grid

	static boolean axisAligned(double[] v) {
		int n = 0;
		for (double d : v) if (Math.abs(d) > 1e-4) n++;
		return n <= 1;
	}

	/** Boxes {x1,y1,z1,x2,y2,z2,kind} in model units; kind 0 = exact element box, 1 = box made of approximation cells. */
	public static List<double[]> boxesOf(List<double[][]> solids) {
		List<double[]> exact = new ArrayList<>();
		List<double[][]> tilted = new ArrayList<>();
		for (double[][] c : solids) {
			if (Vectors.len(Vectors.minus(c[4], c[0])) < 1e-4 || Vectors.len(Vectors.minus(c[2], c[0])) < 1e-4 || Vectors.len(Vectors.minus(c[1], c[0])) < 1e-4) continue;   // a plane has no volume: no box, like vanilla
			if (axisAligned(Vectors.minus(c[4], c[0])) && axisAligned(Vectors.minus(c[2], c[0])) && axisAligned(Vectors.minus(c[1], c[0]))) {
				double[] b = {1e9, 1e9, 1e9, -1e9, -1e9, -1e9, 0};
				for (double[] p : c) for (int k = 0; k < 3; k++) { b[k] = Math.min(b[k], p[k]); b[k + 3] = Math.max(b[k + 3], p[k]); }
				for (int k = 0; k < 6; k++) b[k] = Vectors.r4(b[k]);
				exact.add(b);
			} else {
				tilted.add(c);
			}
		}
		List<double[]> out = new ArrayList<>();
		for (int i = 0; i < exact.size(); i++) {
			boolean inside = false;
			for (int j = 0; j < exact.size() && !inside; j++)
				if (j != i && contains(exact.get(j), exact.get(i), 1e-6) && (!contains(exact.get(i), exact.get(j), 1e-6) || j < i)) inside = true;
			if (!inside) out.add(exact.get(i));
		}
		if (!tilted.isEmpty()) out.addAll(cellBoxes(tilted, out));
		return out;
	}

	static boolean contains(double[] big, double[] small, double eps) {
		for (int k = 0; k < 3; k++) if (small[k] < big[k] - eps || small[k + 3] > big[k + 3] + eps) return false;
		return true;
	}

	private static void addAxis(List<double[]> axes, double[] a) {
		double l = Vectors.len(a);
		if (l > 1e-9) axes.add(new double[]{a[0] / l, a[1] / l, a[2] / l});
	}

	/**
	 * Covers tilted elements with grid cells, one element at a time. Across a slope the boxes lie on the grid (the
	 * cells). Along an axis without any slope (e.g. the depth of a leaning plank, the height of a head turned about
	 * the vertical axis) the boxes are clipped to the element, so they end exactly where the element ends.
	 */
	static List<double[]> cellBoxes(List<double[][]> solids, List<double[]> exact) {
		List<double[]> out = new ArrayList<>();
		for (double[][] c : solids) {
			double[][] e = {Vectors.minus(c[4], c[0]), Vectors.minus(c[2], c[0]), Vectors.minus(c[1], c[0])};
			double[] smin = {1e9, 1e9, 1e9}, smax = {-1e9, -1e9, -1e9};
			for (double[] p : c) for (int k = 0; k < 3; k++) { smin[k] = Math.min(smin[k], p[k]); smax[k] = Math.max(smax[k], p[k]); }

			double g = GRID;
			int[] n = new int[3];
			double[] o = new double[3];
			while (true) {
				long total = 1;
				for (int k = 0; k < 3; k++) {
					o[k] = Math.floor((smin[k] - 1e-6) / g) * g;
					n[k] = Math.max(1, (int) Math.floor((smax[k] - o[k] + 1e-6) / g) + 1);
					total *= n[k];
				}
				if (total <= 6_000_000L) break;
				g *= 2;   // a huge multiblock: coarser cells instead of running out of memory
			}
			int nx = n[0], ny = n[1], nz = n[2];
			boolean[] cells = new boolean[nx * ny * nz];   // index (y * nz + z) * nx + x

			double[][] unit = {{1, 0, 0}, {0, 1, 0}, {0, 0, 1}};
			List<double[]> axes = new ArrayList<>();
			for (double[] u : unit) axes.add(u);
			for (int i = 0; i < 3; i++) {
				addAxis(axes, Vectors.cross(e[i], e[(i + 1) % 3]));
				for (double[] u : unit) addAxis(axes, Vectors.cross(e[i], u));
			}
			int m = axes.size();
			double[] mid = new double[m], half = new double[m], rad = new double[m];
			for (int a = 0; a < m; a++) {
				double[] ax = axes.get(a);
				double mn = 1e18, mx = -1e18;
				for (double[] p : c) { double d = Vectors.dot(p, ax); mn = Math.min(mn, d); mx = Math.max(mx, d); }
				mid[a] = (mn + mx) / 2;
				half[a] = (mx - mn) / 2;
				rad[a] = g / 2 * (Math.abs(ax[0]) + Math.abs(ax[1]) + Math.abs(ax[2]));
			}
			boolean[] straight = new boolean[3];   // axis k without slope: at most one edge reaches along it, and that one runs purely along it
			for (int k = 0; k < 3; k++) {
				int reach = 0;
				boolean pure = true;
				for (double[] ed : e)
					if (Math.abs(ed[k]) > 1e-4) {
						reach++;
						for (int j = 0; j < 3; j++) if (j != k && Math.abs(ed[j]) > 1e-4) pure = false;
					}
				straight[k] = reach <= 1 && pure;
			}

			for (int y = 0; y < ny; y++)
				for (int z = 0; z < nz; z++)
					for (int x = 0; x < nx; x++) {
						double[] cell = {o[0] + x * g, o[1] + y * g, o[2] + z * g, o[0] + (x + 1) * g, o[1] + (y + 1) * g, o[2] + (z + 1) * g};
						boolean covered = false;
						for (double[] b : exact) if (contains(b, cell, 1e-6)) { covered = true; break; }
						if (covered) continue;
						double cx = (cell[0] + cell[3]) / 2, cy = (cell[1] + cell[4]) / 2, cz = (cell[2] + cell[5]) / 2;
						boolean hit = true;
						for (int a = 0; a < m && hit; a++) {
							double[] ax = axes.get(a);
							if (Math.abs(cx * ax[0] + cy * ax[1] + cz * ax[2] - mid[a]) > rad[a] + half[a] - 1e-6) hit = false;   // a cell only counts when the element reaches INTO it, touching is not enough
						}
						if (hit) cells[(y * nz + z) * nx + x] = true;
					}

			// merge: runs along x, then rows along z, then slabs along y
			for (int y = 0; y < ny; y++)
				for (int z = 0; z < nz; z++)
					for (int x = 0; x < nx; x++) {
						if (!cells[(y * nz + z) * nx + x]) continue;
						int x2 = x;
						while (x2 + 1 < nx && cells[(y * nz + z) * nx + x2 + 1]) x2++;
						int z2 = z;
						grow:
						while (z2 + 1 < nz) {
							for (int xx = x; xx <= x2; xx++) if (!cells[(y * nz + z2 + 1) * nx + xx]) break grow;
							z2++;
						}
						int y2 = y;
						grow2:
						while (y2 + 1 < ny) {
							for (int zz = z; zz <= z2; zz++)
								for (int xx = x; xx <= x2; xx++) if (!cells[((y2 + 1) * nz + zz) * nx + xx]) break grow2;
							y2++;
						}
						for (int yy = y; yy <= y2; yy++)
							for (int zz = z; zz <= z2; zz++)
								for (int xx = x; xx <= x2; xx++) cells[(yy * nz + zz) * nx + xx] = false;
						double[] b = {o[0] + x * g, o[1] + y * g, o[2] + z * g, o[0] + (x2 + 1) * g, o[1] + (y2 + 1) * g, o[2] + (z2 + 1) * g, 1};
						for (int k = 0; k < 3; k++) {
							if (!straight[k]) continue;        // across a slope: stay on the grid
							b[k] = Vectors.r4(Math.max(b[k], smin[k]));
							b[k + 3] = Vectors.r4(Math.min(b[k + 3], smax[k]));
						}
						out.add(b);
					}
		}
		return out;
	}

	/** The combined outline of a box list (edges only where the shape meets air), in model units: what the Boxes view shows. */
	public static List<double[]> boxOutline(List<double[]> boxes) {
		List<double[][]> cuboids = new ArrayList<>();
		for (double[] b : boxes) {
			double[][] c = new double[8][];
			for (int i = 0; i < 8; i++) c[i] = new double[]{(i & 4) != 0 ? b[3] : b[0], (i & 2) != 0 ? b[4] : b[1], (i & 1) != 0 ? b[5] : b[2]};
			cuboids.add(c);
		}
		return Outlines.outlineOf(cuboids);
	}
}
