package com.axanthic.euclid.load;

import com.axanthic.euclid.geometry.Elements;
import com.axanthic.euclid.geometry.Outlines;
import com.axanthic.euclid.geometry.Vectors;
import com.axanthic.euclid.io.Json;
import com.axanthic.euclid.io.TextFiles;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Entry point of the computation: reads a blockstate or a single model file.
 * Produces the Result with the outline lines of every model row.
 */
public final class Blockstates {
	static final String INVALID = "Invalid file";

	public static Result compute(Path file, List<Path> roots, java.util.function.DoubleConsumer progress) throws IOException {
		Map<String, Object> m;
		try {
			Object root = Json.parse(TextFiles.readString(file));
			if (!(root instanceof Map)) throw new IOException(INVALID);
			m = Json.asMap(root);
		} catch (IOException | RuntimeException ex) {
			throw new IOException(INVALID);   // not readable, not JSON, or not an object
		}
		if (m.containsKey("multipart")) throw new IOException("Multipart blockstates are not supported yet");
		if (m.containsKey("variants")) {
			if (!(m.get("variants") instanceof Map)) throw new IOException(INVALID);
			requireVariants(Json.asMap(m.get("variants")));
			Result res = computeBlockstate(m, roots, progress);
			res.roots = roots;
			return res;
		}
		if (Models.isModel(m)) return computeModel(file, roots, progress);   // a single block model
		throw new IOException(INVALID);   // neither a blockstate nor a block model
	}

	/** A single block model: handled like a blockstate with one variant without properties and without rotation. The model is copied into a
	 *  temporary assets folder under its own file name (so the list shows that name) and the parents are found through the normal roots. */
	static Result computeModel(Path file, List<Path> roots, java.util.function.DoubleConsumer progress) throws IOException {
		String name = file.getFileName().toString().replaceAll("(?i)\\.json$", "");
		Path tmp = Files.createTempDirectory("euclid");
		try {
			Path dir = tmp.resolve("euclid").resolve("models");
			Files.createDirectories(dir);
			Files.copy(file, dir.resolve(name + ".json"));
			List<Path> all = new ArrayList<>();
			all.add(tmp);
			all.addAll(roots);
			Map<String, Object> variant = new LinkedHashMap<>();
			variant.put("model", "euclid:" + name);
			Map<String, Object> variants = new LinkedHashMap<>();
			variants.put("", variant);
			Map<String, Object> root = new LinkedHashMap<>();
			root.put("variants", variants);
			Result res = computeBlockstate(root, all, progress);
			res.roots = roots;   // not the temporary folder: it is gone afterwards
			return res;
		} finally {
			try (java.util.stream.Stream<Path> w = Files.walk(tmp)) {
				w.sorted(Comparator.reverseOrder()).forEach(p -> { try { Files.delete(p); } catch (IOException ignored) { } });
			} catch (IOException ignored) { }
		}
	}

	/** "facing=north,amount=2" -> "amount=2,facing=north" (sorted; "" / "normal" -> ""). */
	static String normKey(String key) {
		List<String> parts = new ArrayList<>();
		for (String p : key.split(",")) {
			p = p.trim();
			if (!p.isEmpty() && !p.equals("normal")) parts.add(p);
		}
		Collections.sort(parts);
		return String.join(",", parts);
	}

	/** All variant keys must name the same properties (the values may differ); that also rules out mixing "" (no properties) with variants that have properties. */
	static void requireProperties(Collection<String> keys) throws IOException {
		Set<String> first = null;
		for (String key : new LinkedHashSet<>(keys)) {
			Set<String> names = new TreeSet<>();
			if (!key.trim().isEmpty())
				for (String kv : key.split(",")) {
					int eq = kv.indexOf('=');
					names.add((eq < 0 ? kv : kv.substring(0, eq)).trim());
				}
			if (first == null) first = names;
			else if (!first.equals(names)) throw new IOException(INVALID);
		}
	}

	/** "variants" of a blockstate: at least one variant, each one an object with a model or a non-empty list of such objects. */
	static void requireVariants(Map<String, Object> variants) throws IOException {
		if (variants.isEmpty()) throw new IOException(INVALID);
		for (Object v : variants.values()) {
			List<Object> entries = v instanceof List ? Json.asList(v) : Collections.singletonList(v);
			if (entries.isEmpty()) throw new IOException(INVALID);
			for (Object ev : entries)
				if (!(ev instanceof Map) || !(Json.asMap(ev).get("model") instanceof String)) throw new IOException(INVALID);
		}
		requireProperties(variants.keySet());
	}

	static Result computeBlockstate(Map<String, Object> root, List<Path> roots, java.util.function.DoubleConsumer progress) throws IOException {
		Map<String, Object> variants = Json.asMap(root.get("variants"));
		Map<String, List<double[]>> baseCache = new HashMap<>();
		Map<String, List<double[][]>> cornerCache = new HashMap<>();
		Map<String, List<double[]>> unionCache = new HashMap<>();
		Map<String, List<double[]>> out = new LinkedHashMap<>();
		Map<String, List<double[][]>> solidCache = new HashMap<>();   // for the boxes: corners per model (kept apart from cornerCache, which counts the models)
		Map<String, List<double[][]>> solids = new LinkedHashMap<>();

		List<Part> parts = new ArrayList<>();
		Map<String, Row> rows = new LinkedHashMap<>();
		for (Map.Entry<String, Object> e : variants.entrySet()) {
			Object v = e.getValue();
			List<Object> entries = v instanceof List ? Json.asList(v) : Collections.singletonList(v);
			List<Part> mine = new ArrayList<>();
			for (Object ev : entries) {
				Map<String, Object> var = Json.asMap(ev);
				Part p = new Part();
				p.key = normKey(e.getKey());
				p.ref = (String) var.get("model");
				p.x = var.get("x") instanceof Number ? (int) Json.num(var.get("x")) : 0;
				p.y = var.get("y") instanceof Number ? (int) Json.num(var.get("y")) : 0;
				p.weight = var.get("weight") instanceof Number ? Math.max(1, (int) Json.num(var.get("weight"))) : 0;   // 0 = not given
				Part.classify(p);
				mine.add(p);
			}
			for (int i = 0; i < mine.size(); i++) {
				Part p = mine.get(i);
				p.idx = i;
				p.rowKey = p.key + "\0" + i;
				rows.put(p.rowKey, new Row(p.key, p.weight, p.ref, p.x, p.y));
				parts.add(p);
			}
		}

		// a property without any "center" value is a pair: its second value is cell 1, not 2
		Set<String> withCenter = new HashSet<>();
		for (Part p : parts)
			for (int a = 0; a < 3; a++) if (p.prop[a] != null && p.cell[a] == 1) withCenter.add(p.prop[a]);
		for (Part p : parts)
			for (int a = 0; a < 3; a++) if (p.prop[a] != null && p.cell[a] == 2 && !withCenter.contains(p.prop[a])) p.cell[a] = 1;

		Map<String, List<Part>> groups = new HashMap<>();
		for (Part p : parts) if (p.multi) groups.computeIfAbsent(p.group + "|" + p.x + "|" + p.y, k -> new ArrayList<>()).add(p);

		Map<String, Map<String, Object>> models = Models.checkModels(parts.stream().map(q -> q.ref).collect(java.util.stream.Collectors.toList()), roots);

		int done = 0;
		for (Part p : parts) {
			if (progress != null) progress.accept((double) done++ / parts.size());
			Map<String, Object> model;
			if (!p.multi) {
				List<double[]> base = baseCache.get(p.ref);
				if (base == null) {
					model = models.get(p.ref);
					try {
						base = Models.modelLines(model, roots);
					} catch (IOException | RuntimeException ex) {
						throw new IOException("Invalid model " + p.ref);   // safety net: checkModels has already vetted the models and their parents
					}
					baseCache.put(p.ref, base);
				}
				List<double[]> lines = new ArrayList<>();
				for (double[] l : base) lines.add(p.x == 0 && p.y == 0 ? l : Vectors.varRot(l, p.x, p.y));
				out.put(p.rowKey, Outlines.dedupe(lines));
				List<double[][]> els = solidCache.get(p.ref);
				if (els == null) {
					els = new ArrayList<>();
					for (Object o : Models.findElements(models.get(p.ref), roots)) els.add(Elements.corners(Json.asMap(o)));
					solidCache.put(p.ref, els);
				}
				List<double[][]> turned = new ArrayList<>();
				for (double[][] c : els) {
					double[][] n = new double[c.length][];
					for (int i = 0; i < c.length; i++) n[i] = p.x == 0 && p.y == 0 ? c[i] : Vectors.rotPt(c[i], p.x, p.y);
					turned.add(n);
				}
				solids.put(p.rowKey, turned);
				continue;
			}

			// multiblock part: union of ALL parts of its group, shifted into this part's cell
			List<double[][]> all = new ArrayList<>();
			StringBuilder sig = new StringBuilder();   // identifies the union: parts with the same signature give the same outline
			Set<String> seen = new HashSet<>();
			for (Part q0 : groups.get(p.group + "|" + p.x + "|" + p.y)) {
				if (!seen.add(q0.key)) continue;   // one model per key; of the other keys the first one
				Part q = q0.key.equals(p.key) ? p : q0;
				List<double[][]> els = cornerCache.get(q.ref);
				if (els == null) {
					try {
						List<Object> raw = Models.findElements(models.get(q.ref), roots);
						if (raw.isEmpty()) throw new IOException();
						els = new ArrayList<>();
						for (Object o : raw) els.add(Elements.corners(Json.asMap(o)));
					} catch (IOException | RuntimeException ex) {
						throw new IOException("Invalid model " + q.ref);
					}
					cornerCache.put(q.ref, els);
				}
				double[] d = {16.0 * (q.cell[0] - p.cell[0]), 16.0 * (q.cell[1] - p.cell[1]), 16.0 * (q.cell[2] - p.cell[2])};
				sig.append(q.ref).append(',').append(q.x).append(',').append(q.y).append(',').append(d[0]).append(',').append(d[1]).append(',').append(d[2]).append(';');
				if (p.x != 0) d = Vectors.rotate(d, 0, -p.x);
				if (p.y != 0) d = Vectors.rotate(d, 1, -p.y);
				for (double[][] c : els) {
					double[][] n = new double[c.length][];
					for (int i = 0; i < c.length; i++) {
						double[] r = Vectors.rotPt(c[i], q.x, q.y);
						n[i] = new double[]{r[0] + d[0], r[1] + d[1], r[2] + d[2]};
					}
					all.add(n);
				}
			}
			String sigKey = p.x + "," + p.y + "|" + sig;
			List<double[]> lines = unionCache.get(sigKey);
			if (lines == null) {
				lines = new ArrayList<>();
				for (double[] l : Outlines.outlineOf(all)) {
					double[] r = new double[6];
					for (int i = 0; i < 6; i++) r[i] = Vectors.r4(l[i]);
					lines.add(r);
				}
				unionCache.put(sigKey, lines);
			}
			out.put(p.rowKey, new ArrayList<>(lines));
			solids.put(p.rowKey, all);
		}
		Result result = new Result(baseCache.size() + cornerCache.size(), out, rows);
		result.solids = solids;
		result.rawModels = models;
		return result;
	}
}
