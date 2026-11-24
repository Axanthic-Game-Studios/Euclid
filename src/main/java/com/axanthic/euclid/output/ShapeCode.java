package com.axanthic.euclid.output;

import com.axanthic.euclid.geometry.Vectors;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Writes the VoxelShape code of the Voxel Shape mode.
 * One Block.box per box, joined with Shapes.join and BooleanOp.OR.
 */
public final class ShapeCode {
	/** The exported Java file of the Boxes view: a class with one shape per model (constant name -> boxes). Entries with the same name and the same
	 *  boxes are written once; different shapes of the same name are all written (unnumbered). */
	public static String shapeFile(List<Map.Entry<String, List<double[]>>> shapes, String cls) {
		StringBuilder b = new StringBuilder("public class " + cls + " {\n");
		List<String> done = new ArrayList<>();
		for (Map.Entry<String, List<double[]>> e : shapes) {
			String code = shapeCode(e.getValue());
			if (done.contains(e.getKey() + "\0" + code)) continue;
			if (!done.isEmpty()) b.append('\n');
			done.add(e.getKey() + "\0" + code);
			b.append("\tpublic static final VoxelShape ").append(e.getKey()).append(" = ").append(code).append('\n');
		}
		return b.append("}\n").toString();
	}

	/** The shape as code (a statement ending in ";") in one line: the Block.box entries joined with OR; "Shapes.empty();" without boxes. */
	static String shapeCode(List<double[]> boxes) {
		if (boxes.isEmpty()) return "Shapes.empty();";
		StringBuilder b = new StringBuilder("Stream.of(");
		for (int i = 0; i < boxes.size(); i++) {
			double[] x = boxes.get(i);
			b.append(i > 0 ? ", " : "").append("Block.box(");
			for (int k = 0; k < 6; k++) b.append(k > 0 ? ", " : "").append(shapeNum(x[k]));
			b.append(')');
		}
		return b.append(").reduce((a, b) -> Shapes.join(a, b, BooleanOp.OR)).get();").toString();
	}

	/** Java number as in the shape code: 1 -> "1.0", 0.25 -> "0.25". */
	static String shapeNum(double v) {
		String s = new java.math.BigDecimal(Vectors.r4(v)).setScale(4, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
		return (s.contains(".") ? s : s + ".0") + "D";
	}

	/** Constant name of a model: its property key in capitals ("facing=north" -> FACING_NORTH), VOXEL_SHAPE without properties; models of the same key share the name (no numbering). */
	public static String shapeName(String group) {
		String n = group.replaceAll("[^A-Za-z0-9]+", "_").replaceAll("^_+|_+$", "").toUpperCase(Locale.ROOT);
		if (n.isEmpty()) n = "VOXEL_SHAPE";
		else if (Character.isDigit(n.charAt(0))) n = "_" + n;
		return n;
	}
}
