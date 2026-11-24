package com.axanthic.euclid.geometry;

import java.util.HashSet;
import java.util.Set;

/**
 * An edge piece of the visible skin of a union outline.
 * Records which planes touch it and how often it occurs.
 */
final class SegInfo {
	int a, b, count;
	final Set<Integer> planes = new HashSet<>();
}
