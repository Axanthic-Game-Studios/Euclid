package euclid.geometry;

import java.util.ArrayList;
import java.util.List;

/**
 * A cuboid as a convex solid made of face polygons.
 * Flat when the cuboid has no thickness.
 */
final class Solid {
	final List<Face> faces = new ArrayList<>();
	boolean flat;
}
