package com.axanthic.euclid.load;

/**
 * One model of a blockstate key together with its multiblock position.
 * Parts are told apart by the position properties and values of the multiblock settings.
 * Layout in the frame of facing=north (front = north = -Z, left = east = +X, top = +Y):
 * X: right 0, center 1, left 2     Y: bottom 0, center 1, top 2     Z: front 0, center 1, back 2
 * A pair has no center, its second value is cell 1.
 */
final class Part {
	String key, ref, group = "";
	String rowKey;       // key + "\0" + index of the model within the key
	int idx, weight;
	int x, y;
	int[] cell = new int[3];
	String[] prop = new String[3]; // name of the position property per axis
	boolean multi;

	/**
	 * Takes the position properties of the multiblock settings out of the key and sets the cells.
	 * A value that is not defined for its axis keeps the property as a normal one.
	 */
	static void classify(Part pPart, Multiblock pMultiblock) {
		var rest = new StringBuilder();
		for (var kv : pPart.key.split(",")) {
			var eq = kv.indexOf('=');
			var name = eq < 0 ? kv : kv.substring(0, eq);
			var axis = eq < 0 ? -1 : pMultiblock.axis(name);
			var cell = axis < 0 ? -1 : pMultiblock.cell(name, kv.substring(eq + 1));
			if (cell >= 0) {
				pPart.cell[axis] = cell;
				pPart.prop[axis] = name;
				pPart.multi = true;
			} else {
				if (rest.length() > 0) {
					rest.append(',');
				}
				rest.append(kv);
			}
		}
		pPart.group = rest.toString();
	}
}
