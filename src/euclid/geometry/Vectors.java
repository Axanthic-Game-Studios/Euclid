package euclid.geometry;

/**
 * Small vector helpers on double[3] arrays.
 * Also holds the rotations of blockstate variants about the block centre.
 */
public final class Vectors {
	static double dot(double[] a, double[] b) { return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]; }
	static double[] minus(double[] a, double[] b) { return new double[]{a[0] - b[0], a[1] - b[1], a[2] - b[2]}; }
	static double[] cross(double[] a, double[] b) {
		return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
	}
	static double len(double[] a) { return Math.sqrt(dot(a, a)); }

	public static double[] rotate(double[] v, int axis, double degrees) {
		double a = Math.toRadians(degrees), c = Math.cos(a), s = Math.sin(a);
		double x = v[0], y = v[1], z = v[2];
		switch (axis) {
			case 0: return new double[]{x, y * c - z * s, y * s + z * c};
			case 1: return new double[]{x * c + z * s, y, -x * s + z * c};
			default: return new double[]{x * c - y * s, x * s + y * c, z};
		}
	}

	public static double r4(double v) { return Math.round(v * 10000.0) / 10000.0 + 0.0; }

	/**
	 * Blockstate variant rotation about the block centre: x first, then y (like vanilla).
	 * Both use the negative angle in vanilla's convention (y verified in game, x not yet).
	 */
	public static double[] varRot(double[] l, int x, int y) {
		double dx = -x, dy = -y;
		double[] a = {l[0] - 8, l[1] - 8, l[2] - 8}, b = {l[3] - 8, l[4] - 8, l[5] - 8};
		if (x != 0) { a = rotate(a, 0, dx); b = rotate(b, 0, dx); }
		if (y != 0) { a = rotate(a, 1, dy); b = rotate(b, 1, dy); }
		return new double[]{a[0] + 8, a[1] + 8, a[2] + 8, b[0] + 8, b[1] + 8, b[2] + 8};
	}

	public static double[] rotPt(double[] p, int x, int y) {
		double[] a = {p[0] - 8, p[1] - 8, p[2] - 8};
		if (x != 0) a = rotate(a, 0, -x);
		if (y != 0) a = rotate(a, 1, -y);
		return new double[]{a[0] + 8, a[1] + 8, a[2] + 8};
	}
}
