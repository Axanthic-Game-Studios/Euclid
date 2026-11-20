package euclid.load;

import euclid.geometry.Elements;
import euclid.geometry.Outlines;
import euclid.io.Json;
import euclid.io.TextFiles;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Resolves block models and their parents through the asset roots.
 * Also validates models and turns a model into its outline lines.
 */
public final class Models {
	/** "minecraft:block/oak_planks" -> "oak_planks" */
	static String shortModel(String ref) {
		String s = ref.substring(ref.indexOf(':') + 1);
		return s.substring(s.lastIndexOf('/') + 1);   // file name only, no namespace and no folders
	}

	static Map<String, Object> resolveModel(String ref, List<Path> roots) throws IOException {
		String ns = "minecraft", path = ref;
		int c = ref.indexOf(':');
		if (c >= 0) { ns = ref.substring(0, c); path = ref.substring(c + 1); }
		for (Path root : roots) {
			Path p = root.resolve(ns).resolve("models").resolve(path + ".json");
			if (Files.exists(p)) return Json.asMap(Json.parse(TextFiles.readString(p)));
		}
		return null;
	}

	static List<Object> findElements(Map<String, Object> model, List<Path> roots) throws IOException {
		for (int depth = 0; model != null && depth < 32; depth++) {
			if (model.containsKey("elements")) return Json.asList(model.get("elements"));
			Object parent = model.get("parent");
			model = parent instanceof String ? resolveModel((String) parent, roots) : null;
		}
		return Collections.<Object>emptyList();
	}

	static boolean elementsOk(Object els) {
		if (!(els instanceof List) || Json.asList(els).isEmpty()) return false;
		try {
			for (Object o : Json.asList(els)) {
				if (Json.asMap(o).get("rotation") != null && !(Json.asMap(o).get("rotation") instanceof Map)) return false;   // a list is not a rotation
				Elements.corners(Json.asMap(o));
			}
			return true;
		} catch (RuntimeException ex) {
			return false;
		}
	}

	/**
	 * Loads the models of a blockstate and checks them together with their parents. The model itself is judged before its parents,
	 * a missing file before an invalid one: Missing model, Invalid model, Missing parent, Invalid parent.
	 * A model is invalid when it cannot be read, has broken or no elements (and no parent) or when its chain of parents is circular.
	 */
	static Map<String, Map<String, Object>> checkModels(Collection<String> refs, List<Path> roots) throws IOException {
		Map<String, Map<String, Object>> models = new LinkedHashMap<>();
		String[] bad = new String[3];   // first invalid model, first missing parent, first invalid parent
		for (String ref : refs) {
			if (models.containsKey(ref)) continue;
			Map<String, Object> mo;
			try {
				mo = resolveModel(ref, roots);
			} catch (IOException | RuntimeException ex) {
				if (bad[0] == null) bad[0] = ref;   // not readable
				models.put(ref, null);
				continue;
			}
			if (mo == null) throw new IOException("Missing model " + ref);
			models.put(ref, mo);
		}
		for (Map.Entry<String, Map<String, Object>> en : models.entrySet()) {
			Map<String, Object> m = en.getValue();
			if (m == null) continue;
			Set<String> chain = new HashSet<>();
			chain.add(en.getKey());
			String ref = en.getKey();
			for (int depth = 0; ; depth++) {
				int who = depth == 0 ? 0 : 2;   // 0: the model itself, 2: a parent
				if (depth >= 32) { if (bad[0] == null) bad[0] = en.getKey(); break; }
				if (m.containsKey("elements")) {
					if (!elementsOk(m.get("elements")) && bad[who] == null) bad[who] = ref;
					break;
				}
				if (!(m.get("parent") instanceof String)) { if (bad[who] == null) bad[who] = ref; break; }   // neither elements nor parent
				String parent = (String) m.get("parent");
				if (!chain.add(parent)) { if (bad[0] == null) bad[0] = en.getKey(); break; }   // circular
				Map<String, Object> pm;
				try {
					pm = resolveModel(parent, roots);
				} catch (IOException | RuntimeException ex) {
					if (bad[2] == null) bad[2] = parent;
					break;
				}
				if (pm == null) { if (bad[1] == null) bad[1] = parent; break; }
				m = pm;
				ref = parent;
			}
		}
		if (bad[0] != null) throw new IOException("Invalid model " + bad[0]);
		if (bad[1] != null) throw new IOException("Missing parent " + bad[1]);
		if (bad[2] != null) throw new IOException("Invalid parent " + bad[2]);
		return models;
	}

	/** A single block model: it has elements or a parent, keeps its textures in an object and the rotation of each element in an object.
	 *  Files of other tools look similar but are not accepted: Blockbench projects, for example, keep both as lists. */
	static boolean isModel(Map<String, Object> m) {
		if (!m.containsKey("elements") && !m.containsKey("parent")) return false;
		if (m.containsKey("textures") && !(m.get("textures") instanceof Map)) return false;
		if (m.containsKey("elements")) {
			if (!(m.get("elements") instanceof List)) return false;
			for (Object el : Json.asList(m.get("elements")))
				if (!(el instanceof Map) || (Json.asMap(el).get("rotation") != null && !(Json.asMap(el).get("rotation") instanceof Map))) return false;
		}
		return true;
	}

	/** Outline lines (model units) of one block model. */
	static List<double[]> modelLines(Map<String, Object> model, List<Path> roots) throws IOException {
		List<Object> els = findElements(model, roots);
		if (els.isEmpty()) throw new IOException("No elements");

		List<double[][]> cuboids = new ArrayList<>();
		for (Object o : els) cuboids.add(Elements.corners(Json.asMap(o)));
		return Outlines.outlineOf(cuboids);
	}

	/** Resolves the parents of a model: what the child does not set comes from its parent (elements are replaced as a whole, "display" is merged per
	 *  context). The chain ends at the first parent that is not in the assets folders (usually minecraft:block/block). The datagen code
	 *  does not use the parent that is left: it always gets the parent of the settings. */
	public static Map<String, Object> flatModel(Map<String, Object> m, List<Path> roots) {
		Map<String, Object> out = new LinkedHashMap<>(m);
		Set<String> seen = new HashSet<>();
		for (int depth = 0; depth < 32 && roots != null; depth++) {
			if (!(out.get("parent") instanceof String)) break;
			String ref = (String) out.get("parent");
			Map<String, Object> par;
			try { par = resolveModel(ref, roots); } catch (IOException | RuntimeException ex) { break; }
			if (par == null || !seen.add(ref)) break;
			for (Map.Entry<String, Object> e : par.entrySet()) {
				if (e.getKey().equals("parent")) continue;
				if (e.getKey().equals("display") && out.get("display") instanceof Map && e.getValue() instanceof Map) {
					Map<String, Object> d = new LinkedHashMap<>(Json.asMap(e.getValue()));
					d.putAll(Json.asMap(out.get("display")));   // the child's entries win
					out.put("display", d);
				} else if (!out.containsKey(e.getKey())) out.put(e.getKey(), e.getValue());
			}
			if (par.get("parent") instanceof String) out.put("parent", par.get("parent")); else out.remove("parent");
		}
		return out;
	}
}
