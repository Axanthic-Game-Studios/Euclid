package euclid.gui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/** Hover fade: the level of every key follows "is the target" with a smooth FADE_MS ramp (one target at a time, e.g. the hovered row). */
final class HoverFade {
	static final int FADE_MS = 100;

	private final Map<Integer, Float> lv = new HashMap<>();
	private int target = -1;
	private long last;
	private final javax.swing.Timer t;
	private final Runnable repaint;

	HoverFade(Runnable repaint) {
		this.repaint = repaint;
		t = new javax.swing.Timer(12, e -> tick());
	}

	private void tick() {
		{
			long n = System.nanoTime();
			float d = (n - last) / 1e6f / FADE_MS;
			last = n;
			boolean busy = false;
			for (Iterator<Map.Entry<Integer, Float>> it = lv.entrySet().iterator(); it.hasNext(); ) {
				Map.Entry<Integer, Float> en = it.next();
				float v = en.getValue();
				if (en.getKey() == target) { v = Math.min(1f, v + d); if (v < 1f) busy = true; }
				else { v -= d; if (v <= 0f) { it.remove(); continue; } busy = true; }
				en.setValue(v);
			}
			repaint.run();
			if (!busy) t.stop();
		}
	}

	/** Makes {@code key} the fading-in one (-1 = none, everything fades out). */
	void set(int key) {
		if (key == target) return;
		target = key;
		if (key >= 0 && !lv.containsKey(key)) lv.put(key, 0f);
		last = System.nanoTime();
		if (!t.isRunning()) t.start();
	}

	/** Drops everything at once (the content was replaced). */
	void reset() { target = -1; lv.clear(); t.stop(); }

	float get(int key) { Float v = lv.get(key); return v == null ? 0f : v * v * (3 - 2 * v); }
	List<Integer> keys() { return new ArrayList<>(lv.keySet()); }
}
