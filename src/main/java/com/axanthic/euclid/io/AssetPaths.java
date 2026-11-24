package com.axanthic.euclid.io;

import java.nio.file.Path;

/**
 * Finds the assets folder above a blockstate or model file.
 * Also suggests the default output path of the outline JSON.
 */
public final class AssetPaths {
	/** If the model sits in .../assets/<ns>/models/..., returns {assetsRoot, defaultOutput}. */
	public static Path[] autoPaths(Path model) {
		Path abs = model.toAbsolutePath();
		for (Path p = abs.getParent(); p != null; p = p.getParent()) {
			if (p.getFileName() != null && p.getFileName().toString().equals("assets")) {
				Path rel = p.relativize(abs); // <ns>/models/block/x.json
				if (rel.getNameCount() >= 3 && (rel.getName(1).toString().equals("models") || rel.getName(1).toString().equals("blockstates"))) {
					String name = abs.getFileName().toString().replaceAll("\\.json$", "");
					return new Path[]{p, p.resolve(rel.getName(0)).resolve("outlines").resolve(name + ".json")};
				}
			}
		}
		String name = abs.getFileName().toString().replaceAll("\\.json$", "");
		return new Path[]{null, abs.resolveSibling(name + "_outline.json")};
	}
}
