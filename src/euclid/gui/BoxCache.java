package euclid.gui;

import euclid.geometry.Boxes;
import euclid.load.Result;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** The boxes of the loaded blockstate's models (computed when asked for): the combined shape of a model and its outline. */
final class BoxCache {
	private Result result;
	private final Map<String, List<double[]>> cache = new HashMap<>();
	private final Map<String, List<double[]>> outCache = new HashMap<>();

	void setResult(Result r) {
		if (r == result) return;
		result = r;
		cache.clear();
		outCache.clear();
	}

	/** Boxes of one row (model) of the loaded blockstate; empty if there is no model data. */
	List<double[]> boxesFor(String rowKey) {
		if (result == null || rowKey == null) return Collections.emptyList();
		return cache.computeIfAbsent(rowKey, k -> {
			List<double[][]> s = result.solids.get(k);
			return s == null ? new ArrayList<double[]>() : Boxes.boxesOf(s);
		});
	}

	/** Outline of the boxes of one row combined (what the preview shows). */
	List<double[]> outlineFor(String rowKey) {
		if (result == null || rowKey == null) return Collections.emptyList();
		return outCache.computeIfAbsent(rowKey, k -> Boxes.boxOutline(boxesFor(k)));
	}
}
