package com.axanthic.euclid.load;

import com.axanthic.euclid.io.Prefs;

import java.util.Set;

/**
 * The multiblock settings, a position property with its values per kind and axis.
 * A pair has two values, a span three, and only complete settings without conflicts are used.
 */
public final class Multiblock {
	/**
	 * Names of the kinds, used in the preference keys.
	 * A pair spans two blocks per axis, a span three.
	 */
	public static final String[] KINDS = {"pair", "span"};

	/**
	 * Names of the axes in the order of the part cells, used in the preference keys.
	 * Width runs from right to left, height from bottom to top and depth from front to back.
	 */
	public static final String[] AXES = {"width", "height", "depth"};

	/**
	 * Values of the settings the user never changed, per kind, axis and slot, from the vanilla bed and the two block tall blocks.
	 * Depth pairs use part with head in front and foot behind, height pairs use half with lower below and upper above.
	 */
	private static final String[][][] DEFAULTS = {
			{{"", "", ""}, {"half", "lower", "upper"}, {"part", "head", "foot"}},
			{{"", "", "", ""}, {"", "", "", ""}, {"", "", "", ""}}};

	/**
	 * The trimmed settings per kind, axis and slot, slot 0 is the property and the others are the cell values.
	 * Empty strings stand for settings that are not defined.
	 */
	private final String[][][] slots = new String[KINDS.length][AXES.length][];

	/**
	 * Marks the settings whose values do not all appear in the loaded blockstate.
	 * Such settings are not used for that blockstate.
	 */
	private final boolean[][] unused = new boolean[KINDS.length][AXES.length];

	/**
	 * Reads the current multiblock settings from the preferences.
	 * Settings that were never saved take their default, saved empty ones stay empty.
	 */
	public static Multiblock load() {
		var multiblock = new Multiblock();
		for (var kind = 0; kind < KINDS.length; kind++) {
			for (var axis = 0; axis < AXES.length; axis++) {
				var row = new String[Multiblock.slots(kind)];
				for (var slot = 0; slot < row.length; slot++) {
					row[slot] = Multiblock.saved(kind, axis, slot);
				}
				multiblock.slots[kind][axis] = row;
			}
		}
		return multiblock;
	}

	/**
	 * Gives a copy that leaves out the settings whose values do not all appear among the property=value pairs of a blockstate.
	 * Conflicts are still checked against all settings.
	 */
	Multiblock usedBy(Set<String> pPairs) {
		var copy = new Multiblock();
		for (var kind = 0; kind < KINDS.length; kind++) {
			for (var axis = 0; axis < AXES.length; axis++) {
				var row = slots[kind][axis];
				copy.slots[kind][axis] = row;
				for (var slot = 1; slot < row.length; slot++) {
					copy.unused[kind][axis] |= !pPairs.contains(row[0] + "=" + row[slot]);
				}
			}
		}
		return copy;
	}

	/**
	 * Gives the default of one setting, empty if it has none.
	 * Only the depth and height pairs have defaults.
	 */
	public static String defaultValue(int pKind, int pAxis, int pSlot) {
		return DEFAULTS[pKind][pAxis][pSlot];
	}

	/**
	 * Reads one trimmed setting from the preferences.
	 * A setting that was never saved gives its default.
	 */
	public static String saved(int pKind, int pAxis, int pSlot) {
		return Prefs.PREFS.get(Multiblock.key(pKind, pAxis, pSlot), Multiblock.defaultValue(pKind, pAxis, pSlot)).trim();
	}

	/**
	 * Gives the number of settings of a kind, the property followed by one slot per cell.
	 * A pair has three settings, a span four.
	 */
	public static int slots(int pKind) {
		return pKind == 0 ? 3 : 4;
	}

	/**
	 * Builds the preference key of one setting.
	 * The property uses the slot name property, the values use the cell index.
	 */
	public static String key(int pKind, int pAxis, int pSlot) {
		return "multiblock." + KINDS[pKind] + "." + AXES[pAxis] + "." + (pSlot == 0 ? "property" : String.valueOf(pSlot - 1));
	}

	/**
	 * Tells whether a defined setting clashes with another one.
	 * Properties must differ between all kinds and axes, values must differ within their axis.
	 */
	public boolean conflict(int pKind, int pAxis, int pSlot) {
		var value = slots[pKind][pAxis][pSlot];
		if (value.isEmpty()) {
			return false;
		}
		if (pSlot == 0) {
			for (var kind = 0; kind < KINDS.length; kind++) {
				for (var axis = 0; axis < AXES.length; axis++) {
					if ((kind != pKind || axis != pAxis) && slots[kind][axis][0].equals(value)) {
						return true;
					}
				}
			}
			return false;
		}
		var row = slots[pKind][pAxis];
		for (var slot = 1; slot < row.length; slot++) {
			if (slot != pSlot && row[slot].equals(value)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Tells whether an axis of a kind has its property and all its values without any conflict and is not left out.
	 * Only such settings place the models of a blockstate as parts of a multiblock.
	 */
	public boolean active(int pKind, int pAxis) {
		if (unused[pKind][pAxis]) {
			return false;
		}
		var row = slots[pKind][pAxis];
		for (var slot = 0; slot < row.length; slot++) {
			if (row[slot].isEmpty() || conflict(pKind, pAxis, slot)) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Finds the axis whose active pair or span settings use the given property.
	 * Gives -1 if no active settings use that property.
	 */
	int axis(String pProperty) {
		var row = row(pProperty);
		return row < 0 ? -1 : row % AXES.length;
	}

	/**
	 * Finds the cell of a value of the given property.
	 * Gives -1 if the property is not active or the value is none of its values.
	 */
	int cell(String pProperty, String pValue) {
		var row = row(pProperty);
		if (row < 0) {
			return -1;
		}
		var values = slots[row / AXES.length][row % AXES.length];
		for (var slot = 1; slot < values.length; slot++) {
			if (!values[slot].isEmpty() && values[slot].equals(pValue)) {
				return slot - 1;
			}
		}
		return -1;
	}

	/**
	 * Finds the active settings that use the given property, as kind times the axis count plus axis.
	 * Gives -1 if no active settings use that property.
	 */
	private int row(String pProperty) {
		for (var kind = 0; kind < KINDS.length; kind++) {
			for (var axis = 0; axis < AXES.length; axis++) {
				if (active(kind, axis) && slots[kind][axis][0].equals(pProperty)) {
					return kind * AXES.length + axis;
				}
			}
		}
		return -1;
	}
}
