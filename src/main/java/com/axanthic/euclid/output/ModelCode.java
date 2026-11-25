package com.axanthic.euclid.output;

import com.axanthic.euclid.io.Json;
import com.axanthic.euclid.io.Prefs;
import com.axanthic.euclid.load.Models;
import com.axanthic.euclid.load.Result;
import com.axanthic.euclid.load.Row;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Writes the datagen code of the Model mode (ExtendedModelTemplateBuilder).
 * 
 * The model is converted with its parents resolved (flatModel). The parent expression and the location of
 * the texture slots are settings (remembered values, defaults below).
 */
public final class ModelCode {
	static final String[] FACE_ORDER = {"north", "east", "south", "west", "up", "down"};
	public static final String PARENT_BASE = "Identifier.withDefaultNamespace(\"block/block\")";   // base value of the parent identifier

	/**
	 * Base value of the texture slot location, the vanilla class with the texture slot constants.
	 * Every texture slot of a model is written as a constant of this class unless the setting is changed.
	 */
	public static final String TEXTURE_SLOT_BASE = "TextureSlot";

	/** The code settings (Output settings): {parent identifier expression, location in front of the texture slot names}. A saved value replaces
	 *  the base value; the settings never save an empty value. */
	public static String[] codeCfg() {
		String[] c = {PARENT_BASE, TEXTURE_SLOT_BASE};
		try {
			c[0] = Prefs.PREFS.get("codeParent", PARENT_BASE).trim();
			c[1] = Prefs.PREFS.get("codeTexClass", TEXTURE_SLOT_BASE).trim();
		} catch (Exception ignored) { }
		return c;
	}

	/** The exported Java file of the Model view: a class with one method per model (every model once, however often the blockstate uses it;
	 *  identical methods of the same name are written once). */
	public static String modelFile(Result r, String[] cfg, String cls) {
		StringBuilder b = new StringBuilder("public class " + cls + " {\n");
		Set<String> done = new HashSet<>();
		for (Map.Entry<String, String> e : methodNames(r).entrySet()) {
			String code = codeOf(r, e.getKey(), e.getValue(), cfg);
			if (!done.add(code)) continue;
			if (done.size() > 1) b.append('\n');
			b.append('\t').append(code.replace("\n", "\n\t")).append('\n');
		}
		return b.append("}\n").toString();
	}

	/** The datagen code of one model of the blockstate (parents resolved), as a method named 'method'. */
	public static String codeOf(Result r, String ref, String method, String[] cfg) {
		return modelCode(Models.flatModel(r.rawModels.get(ref), r.roots), method, cfg);
	}

	/** Method names of the models of a blockstate (model reference -> name), in order of first use. The name comes from the model file
	 *  (lower camel case, no numbering); models without a readable file are left out. */
	public static Map<String, String> methodNames(Result r) {
		Map<String, String> names = new LinkedHashMap<>();
		if (r == null || r.rawModels == null) return names;
		for (Row row : r.rows.values()) {
			if (names.containsKey(row.model) || r.rawModels.get(row.model) == null) continue;
			String c = Names.camel(row.label);
			names.put(row.model, Character.toLowerCase(c.charAt(0)) + c.substring(1));
		}
		return names;
	}

	/** Converts a block model (as in its JSON file) to a datagen template method named 'method'; cfg = {@link #codeCfg()}. */
	static String modelCode(Map<String, Object> m, String method, String[] cfg) {
		final String T4 = "\t\t", T5 = "\t\t\t";

		StringBuilder b = new StringBuilder();
		StringBuilder body = new StringBuilder();
		body.append("\treturn ExtendedModelTemplateBuilder.builder()");
		body.append(".parent(").append(cfg[0]).append(")\n");   // always: the parent of the settings, whatever the model's own parent is
		if (m.get("ambientocclusion") instanceof Boolean) body.append(T4).append(".ambientOcclusion(").append(m.get("ambientocclusion")).append(")\n");
		if (m.get("gui_light") instanceof String) body.append(T4).append(".guiLight(UnbakedModel.GuiLight.").append(Names.constName((String) m.get("gui_light"))).append(")\n");
		if (m.get("render_type") instanceof String) body.append(T4).append(".renderType(\"").append(m.get("render_type")).append("\")\n");
		if (m.get("display") instanceof Map) {
			for (Map.Entry<String, Object> d : Json.asMap(m.get("display")).entrySet()) {
				if (!(d.getValue() instanceof Map)) continue;
				Map<String, Object> tv = Json.asMap(d.getValue());
				StringBuilder s = new StringBuilder(T4 + ".transform(ItemDisplayContext." + displayContext(d.getKey()) + ", transformBuilder -> transformBuilder");
				for (String part : new String[]{"rotation", "translation", "scale"}) {
					double[] v = Json.numList(tv.get(part), 3);
					if (v != null) s.append('.').append(part).append('(').append(codeNums(v)).append(')');
				}
				body.append(s).append(")\n");
			}
		}
		if (m.get("elements") instanceof List) {
			for (Object eo : Json.asList(m.get("elements"))) {
				if (!(eo instanceof Map)) continue;
				Map<String, Object> el = Json.asMap(eo);
				double[] f = Json.numList(el.get("from"), 3), t = Json.numList(el.get("to"), 3);
				if (f == null || t == null) continue;
				List<String> lines = new ArrayList<>();
				lines.add(T4 + ".element(elementBuilder -> elementBuilder.from(" + codeNums(f) + ").to(" + codeNums(t) + ")");
				if (el.get("rotation") instanceof Map) {
					Map<String, Object> r = Json.asMap(el.get("rotation"));
					double[] o = Json.numList(r.get("origin"), 3);
					if (o == null) o = new double[]{8, 8, 8};
					double[] e = new double[3];
					if (r.get("angle") instanceof Number && r.get("axis") instanceof String) {   // vanilla notation: one axis and an angle
						int ax = "xyz".indexOf(((String) r.get("axis")).toLowerCase(Locale.ROOT));
						if (ax >= 0) e[ax] = Json.num(r.get("angle"));
					} else {
						for (int i = 0; i < 3; i++) if (r.get("xyz".substring(i, i + 1)) instanceof Number) e[i] = Json.num(r.get("xyz".substring(i, i + 1)));
					}
					lines.add(T5 + ".rotation(rotationBuilder -> rotationBuilder.eulerXYZ(" + codeNums(e) + ").origin(" + codeNums(o) + ")"
							+ (Boolean.TRUE.equals(r.get("rescale")) ? ".rescale(true)" : "") + ")");
				}
				if (Boolean.FALSE.equals(el.get("shade"))) lines.add(T5 + ".shade(false)");
				if (el.get("light_emission") instanceof Number && Json.num(el.get("light_emission")) > 0) lines.add(T5 + ".lightEmission(" + (int) Json.num(el.get("light_emission")) + ")");
				if (el.get("faces") instanceof Map) {
					Map<String, Object> faces = Json.asMap(el.get("faces"));
					for (String dir : FACE_ORDER) {
						if (!(faces.get(dir) instanceof Map)) continue;
						Map<String, Object> fc = Json.asMap(faces.get(dir));
						double[] uv = Json.numList(fc.get("uv"), 4);
						if (uv == null) uv = defaultUv(dir, f, t);
						StringBuilder s = new StringBuilder(T5 + ".face(Direction." + dir.toUpperCase(Locale.ROOT) + ", faceBuilder -> faceBuilder.uvs(" + codeNums(uv) + ")");
						if (fc.get("texture") instanceof String) {
							String tx = (String) fc.get("texture");
							s.append(".texture(").append(tx.startsWith("#") ? slotName(cfg[1], tx.substring(1)) : "\"" + tx + "\"").append(')');
						}
						if (fc.get("cullface") instanceof String && !((String) fc.get("cullface")).isEmpty()) s.append(".cullface(Direction.").append(((String) fc.get("cullface")).toUpperCase(Locale.ROOT)).append(')');
						if (fc.get("tintindex") instanceof Number) s.append(".tintindex(").append((int) Json.num(fc.get("tintindex"))).append(')');
						String quadrant = fc.get("rotation") instanceof Number ? quadrant(Json.num(fc.get("rotation"))) : null;
						if (quadrant != null) s.append(".rotation(").append(quadrant).append(')');
						lines.add(s + ")");
					}
				}
				lines.set(lines.size() - 1, lines.get(lines.size() - 1) + ")");   // closes the element lambda
				for (String l : lines) body.append(l).append('\n');
			}
		}
		body.append(T4).append(".build();\n}");
		b.append("public static ExtendedModelTemplate ").append(method).append("() {\n").append(body);
		return b.toString();
	}

	/** The rotation of a face as the constant of the enum Quadrant (R90, R180, R270). Like vanilla, multiples of 90 are accepted and negative
	 *  angles count the other way round (-90 is R270). Returns null for no rotation (0, 360, ...) and for angles that are no multiple of 90. */
	static String quadrant(double degrees) {
		long d = Math.round(degrees);
		if (d != degrees || d % 90 != 0) return null;
		long q = Math.floorMod(d, 360L);
		return q == 0 ? null : "Quadrant.R" + q;
	}

	/** Texture slot constant; a custom location is put in front of it with a "." between. */
	static String slotName(String location, String slot) {
		String loc = location.endsWith(".") ? location.substring(0, location.length() - 1) : location;
		return (loc.isEmpty() ? "" : loc + ".") + Names.constName(slot);
	}

	/** Float literal as short as possible: the value as it is (no rounding), a whole number gets one zero ("2.0F", "0.125F"). */
	static String codeF(double v) {
		if (v == 0) return "0.0F";
		String s = java.math.BigDecimal.valueOf(v).stripTrailingZeros().toPlainString();
		return (s.contains(".") ? s : s + ".0") + "F";
	}

	static String codeNums(double... v) {
		StringBuilder b = new StringBuilder();
		for (int i = 0; i < v.length; i++) b.append(i > 0 ? ", " : "").append(codeF(v[i]));
		return b.toString();
	}

	/** The UV the game uses when a face has none (from the element's box). */
	static double[] defaultUv(String dir, double[] f, double[] t) {
		switch (dir) {
			case "down":  return new double[]{f[0], 16 - t[2], t[0], 16 - f[2]};
			case "up":    return new double[]{f[0], f[2], t[0], t[2]};
			case "north": return new double[]{16 - t[0], 16 - t[1], 16 - f[0], 16 - f[1]};
			case "south": return new double[]{f[0], 16 - t[1], t[0], 16 - f[1]};
			case "west":  return new double[]{f[2], 16 - t[1], t[2], 16 - f[1]};
			default:      return new double[]{16 - t[2], 16 - t[1], 16 - f[2], 16 - f[1]};   // east
		}
	}

	static String displayContext(String key) {
		switch (key) {
			case "thirdperson_righthand": return "THIRD_PERSON_RIGHT_HAND";
			case "thirdperson_lefthand": return "THIRD_PERSON_LEFT_HAND";
			case "firstperson_righthand": return "FIRST_PERSON_RIGHT_HAND";
			case "firstperson_lefthand": return "FIRST_PERSON_LEFT_HAND";
			default: return Names.constName(key);   // gui, ground, fixed, head, ...
		}
	}
}
