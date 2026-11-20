package euclid.load;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The computed outlines of a blockstate, one entry per model row.
 * Also keeps the raw models and element corners for the other modes.
 */
public final class Result {
	public final int models;                              // number of distinct models used
	public final Map<String, List<double[]>> variants;    // row key (property key + "\0" + index of the model) -> lines in model units (0..16)
	public final Map<String, Row> rows;
	public List<Path> roots;                              // assets folders the models were read from (parents are looked up there)
	public Map<String, Map<String, Object>> rawModels;    // model reference -> the model file as read (for the code output)
	public Map<String, List<double[][]>> solids;          // row key -> the 8 corners of every element (model units)

	Result(int models, Map<String, List<double[]>> variants, Map<String, Row> rows) {
		this.models = models;
		this.variants = variants;
		this.rows = rows;
	}

	public String label(String rowKey) {
		Row r = rows.get(rowKey);
		return r == null ? rowKey : r.label;
	}

	/** Rotation badges of a row: "X = 90", "Y = 180" (none if the model is not rotated). */
	public List<String> badges(String rowKey) {
		Row r = rows.get(rowKey);
		List<String> b = new ArrayList<>();
		if (r != null && r.x != 0) b.add("X = " + r.x);
		if (r != null && r.y != 0) b.add("Y = " + r.y);
		return b;
	}

	/** Property key of a row (the group it belongs to). */
	public String group(String rowKey) {
		Row r = rows.get(rowKey);
		return r == null ? "" : r.key;
	}
}
