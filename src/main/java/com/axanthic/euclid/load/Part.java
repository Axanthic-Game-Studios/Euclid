package com.axanthic.euclid.load;

import java.io.IOException;
import java.util.Arrays;

/**
 * One model of a blockstate key together with its multiblock position.
 * Parts are told apart by width_* / height_* / depth_* properties, the suffix (pair, span, ...) does not matter.
 * Layout in the frame of facing=north (front = north = -Z, left = east = +X, top = +Y):
 * X: right 0, center 1, left 2     Y: bottom 0, center 1, top 2     Z: front 0, center 1, back 2
 */
final class Part {
	static final java.util.regex.Pattern POS_PROP = java.util.regex.Pattern.compile("(width|height|depth)_[a-z0-9]+");
	static final String[][] POS_VALUES = {{"right", "center", "left"}, {"bottom", "center", "top"}, {"front", "center", "back"}};

	String key, ref, group = "";
	String rowKey;       // key + "\0" + index of the model within the key
	int idx, weight;
	int x, y;
	int[] cell = new int[3];
	String[] prop = new String[3]; // name of the position property per axis
	boolean multi;

	static void classify(Part p) throws IOException {
		StringBuilder rest = new StringBuilder();
		for (String kv : p.key.split(",")) {
			int eq = kv.indexOf('=');
			String name = eq < 0 ? kv : kv.substring(0, eq), val = eq < 0 ? "" : kv.substring(eq + 1);
			java.util.regex.Matcher mt = POS_PROP.matcher(name);
			if (eq >= 0 && mt.matches()) {
				int axis = mt.group(1).equals("width") ? 0 : mt.group(1).equals("height") ? 1 : 2;
				int idx = Arrays.asList(POS_VALUES[axis]).indexOf(val);
				if (idx < 0) throw new IOException("Unknown value " + val + " for property " + name);
				p.cell[axis] = idx;
				p.prop[axis] = name;
				p.multi = true;
			} else {
				if (rest.length() > 0) rest.append(',');
				rest.append(kv);
			}
		}
		p.group = rest.toString();
	}
}
