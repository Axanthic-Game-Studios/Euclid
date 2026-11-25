package com.axanthic.euclid.gui;

import com.axanthic.euclid.io.AssetPaths;
import com.axanthic.euclid.io.Prefs;
import com.axanthic.euclid.io.TextFiles;
import com.axanthic.euclid.load.Blockstates;
import com.axanthic.euclid.load.Result;
import com.axanthic.euclid.load.Row;
import com.axanthic.euclid.output.ModelCode;
import com.axanthic.euclid.output.Names;
import com.axanthic.euclid.output.OutlineJson;
import com.axanthic.euclid.output.ShapeCode;

import java.awt.AWTEvent;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FileDialog;
import java.awt.Frame;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.KeyboardFocusManager;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.AbstractMap;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.ToolTipManager;
import javax.swing.TransferHandler;
import javax.swing.UIManager;

/**
 * Builds the main window and wires the components together.
 * Holds the load, export, undo and hotkey behaviour of the GUI.
 */
public final class App {
	public static void run() {
		try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception ignored) { }   // standard dialogs (the unsaved-changes question) get the look of the operating system
		JFrame frame = new JFrame("Euclid");
		frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);   // closing asks first when there are unexported changes
		frame.setIconImages(Icons.appIcons());
		ToolTipManager.sharedInstance().setInitialDelay(300);
		Theme.applyTooltipColors();
		try { Prefs.PREFS.remove("theme"); Prefs.PREFS.remove("codeMethod"); } catch (Exception ignored) { }   // left over from the removed light mode and the removed Template setting
		UIManager.put("ToolTip.font", Theme.fontLight);

		Root root = new Root();
		Preview preview = root.preview;
		VariantList list = root.list;

		boolean[] busy = {false};   // a file is being loaded
		Path[] file = {null};        // the loaded blockstate
		Result[] current = {null};   // its computed outlines
		Path[] outDir = {Prefs.prefDir("exportDir")};      // folder the export dialog starts in
		Path[] inDir = {Prefs.prefDir("importDir")};      // folder the import dialog starts in

		root.reloadB.setEnabled(false);
		root.exportB.setEnabled(false);

		// clicking a line in the preview removes it from the selected variant; undo / redo step through those removals
		Deque<Object[]> undo = new ArrayDeque<>();
		Deque<Object[]> redo = new ArrayDeque<>();
		Runnable history = () -> {
			root.undoB.setEnabled(!busy[0] && root.mode == 0 && !undo.isEmpty());   // only the outline view has something to undo
			root.redoB.setEnabled(!busy[0] && root.mode == 0 && !redo.isEmpty());
		};
		// Export is possible when data is loaded (in the Model mode: when a model is selected)
		BoxCache boxes = new BoxCache();
		// Model mode: the datagen code of the selected model (null when there is none)
		java.util.function.Supplier<String> modelText = () -> {
			Result r = current[0];
			String k = list.selectedKey();
			Row row = r == null || k == null ? null : r.rows.get(k);
			String name = row == null ? null : ModelCode.methodNames(r).get(row.model);
			return name == null ? null : ModelCode.codeOf(r, row.model, name, ModelCode.codeCfg());
		};
		Runnable updateExport = () -> root.exportB.setEnabled(!busy[0] && current[0] != null
				&& (root.mode != 2 || !ModelCode.methodNames(current[0]).isEmpty()));
		// Boxes view: the shape of the selected model (only computed while the view is shown)
		Runnable refreshBoxes = () -> {
			boxes.setResult(current[0]);
			preview.setBoxes(root.boxMode ? boxes.outlineFor(list.selectedKey()) : null);
			if (root.mode == 2) {
				String code = modelText.get();
				root.code.setText(code != null ? code : "");
			}
			updateExport.run();
		};
		root.onModeChanged = () -> { refreshBoxes.run(); history.run(); };
		// the removals that are part of the last export (or of the loaded file): when the undo stack differs from it, there are unexported changes
		List<Object[]> saved = new ArrayList<>();
		java.util.function.BooleanSupplier dirty = () -> {
			if (undo.size() != saved.size()) return true;
			Iterator<Object[]> it = undo.iterator();
			for (Object[] s : saved) if (it.next() != s) return true;
			return false;
		};
		preview.onRemove = i -> {
			if (busy[0]) return;
			String key = list.selectedKey();
			List<double[]> l = key == null || current[0] == null ? null : current[0].variants.get(key);
			if (l == null || i < 0 || i >= l.size()) return;
			undo.push(new Object[]{key, i, l.remove(i)});
			redo.clear();
			history.run();
		};
		Runnable doUndo = () -> {
			if (busy[0]) return;
			if (undo.isEmpty() || current[0] == null) return;
			Object[] u = undo.pop();
			List<double[]> l = current[0].variants.get((String) u[0]);
			if (l == null) return;
			l.add(Math.min((Integer) u[1], l.size()), (double[]) u[2]);
			redo.push(u);
			if (!u[0].equals(list.selectedKey())) list.setKeys(current[0].variants.keySet(), (String) u[0]);
			preview.repaint();
			history.run();
		};
		Runnable doRedo = () -> {
			if (busy[0]) return;
			if (redo.isEmpty() || current[0] == null) return;
			Object[] u = redo.pop();
			List<double[]> l = current[0].variants.get((String) u[0]);
			if (l == null) return;
			int i = Math.min((Integer) u[1], l.size() - 1);
			if (i >= 0) l.remove(i);
			undo.push(u);
			if (!u[0].equals(list.selectedKey())) list.setKeys(current[0].variants.keySet(), (String) u[0]);
			preview.repaint();
			history.run();
		};
		root.undoB.onClick(doUndo);
		root.redoB.onClick(doRedo);
		root.undoB.setEnabled(false);
		root.redoB.setEnabled(false);

		// ---- settings + hotkeys
		boolean[] settingsOpen = {false};
		Runnable toggleSettings = () -> { settingsOpen[0] = !settingsOpen[0]; root.showSettings(settingsOpen[0]); };
		root.settingsB.onClick(toggleSettings);
		root.closeB.onClick(() -> { if (settingsOpen[0]) toggleSettings.run(); });
		// Tab (fixed, not configurable) cycles the mode: Lines -> Voxel Shape -> Model -> Lines, Shift + Tab the other way (Tab is a focus key, so it is caught globally)
		KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(e -> {
			if (e.getID() != KeyEvent.KEY_PRESSED || e.getKeyCode() != KeyEvent.VK_TAB) return false;
			if (e.getModifiersEx() != 0 && e.getModifiersEx() != KeyEvent.SHIFT_DOWN_MASK) return false;
			if (!frame.isActive() || settingsOpen[0]) return false;
			root.setMode((root.mode + (e.isShiftDown() ? 2 : 1)) % 3);
			return true;
		});
		root.settings.onOutputChanged = () -> refreshBoxes.run();   // the Output settings change the Model code
		// the hotkeys press the same buttons (so they respect the disabled state); while the settings are open only "Settings" works
		Runnable[] hotkeyActions = {root.undoB::click, root.redoB::click, toggleSettings, root.importB::click, root.reloadB::click, root.exportB::click};
		Runnable rebind = () -> {
			InputMap im = root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
			ActionMap am = root.getActionMap();
			im.clear();
			am.clear();
			for (int i = 0; i < Hotkeys.IDS.length; i++) {
				final int idx = i;
				am.put(Hotkeys.IDS[i], new AbstractAction() {
					@Override public void actionPerformed(ActionEvent e) {
						if (settingsOpen[0] && idx != 2) return;
						hotkeyActions[idx].run();
					}
				});
				KeyStroke ks = root.hotkeys.keys[i];
				if (!root.hotkeys.conflict(i) && !Hotkeys.isModifier(ks.getKeyCode())) im.put(ks, Hotkeys.IDS[i]);   // a conflict disables both hotkeys
			}
		};
		root.settings.onHotkeysChanged = rebind;
		rebind.run();

		// a hotkey that is only a modifier (Ctrl, Shift, ...) fires when the key is released without anything else pressed meanwhile,
		// so that Ctrl + Z does not trigger a "Ctrl" hotkey
		KeyStroke[] heldMod = {null};
		Toolkit.getDefaultToolkit().addAWTEventListener(ev -> {
			if (ev instanceof MouseEvent) { if (ev.getID() == MouseEvent.MOUSE_PRESSED) heldMod[0] = null; return; }
			if (!(ev instanceof KeyEvent)) return;
			KeyEvent k = (KeyEvent) ev;
			int code = k.getKeyCode();
			if (k.getID() == KeyEvent.KEY_PRESSED) {
				heldMod[0] = Hotkeys.isModifier(code) ? KeyStroke.getKeyStroke(code, k.getModifiersEx() & Hotkeys.MASKS) : null;
			} else if (k.getID() == KeyEvent.KEY_RELEASED && Hotkeys.isModifier(code) && heldMod[0] != null) {
				KeyStroke ks = heldMod[0];
				heldMod[0] = null;
				if (!frame.isActive() || root.settings.isRecording()) return;
				for (int i = 0; i < Hotkeys.IDS.length; i++) {
					if (!root.hotkeys.keys[i].equals(ks) || root.hotkeys.conflict(i)) continue;
					if (settingsOpen[0] && i != 2) return;
					hotkeyActions[i].run();
					return;
				}
			}
		}, AWTEvent.KEY_EVENT_MASK | AWTEvent.MOUSE_EVENT_MASK);

		// ---- behaviour
		// clearView = a new file (the old data goes away right away); false = reload of the same file (the old data stays until the new is there)
		java.util.function.Consumer<Boolean> load = clearView -> {
			if (file[0] == null || busy[0]) return;
			String prev = list.selectedKey();
			Path f = file[0];
			busy[0] = true;
			preview.setLoading(f.toString());
			preview.setInteractive(false);   // no hover feedback or clicks on the lines while the data is replaced
			history.run();
			if (clearView) {
				current[0] = null;
				undo.clear();
				redo.clear();
				saved.clear();
				history.run();
				list.clear();
				preview.setLines(null);
			}
			updateExport.run();   // the old data stays visible while loading, so nothing may be exported or reloaded now
			root.reloadB.setEnabled(false);
			root.importB.setEnabled(false);
			Thread th = new Thread(() -> {
				Result r = null;
				Throwable err = null;
				try {
					Path assets = AssetPaths.autoPaths(f)[0]; // the "assets" folder above the blockstate
					List<Path> roots = new ArrayList<>();
					if (assets != null) roots.add(assets);
					r = Blockstates.compute(f, roots, p -> SwingUtilities.invokeLater(() -> { if (busy[0]) preview.setProgress(p); }));
				} catch (Throwable ex) { err = ex; }
				final Result res = r;
				final Throwable fe = err;
				SwingUtilities.invokeLater(() -> {
					busy[0] = false;
					preview.setInteractive(true);
					history.run();
					root.reloadB.setEnabled(true);
					root.importB.setEnabled(true);
					if (fe == null) {
						for (Map.Entry<String, List<double[]>> en : res.variants.entrySet())
							en.setValue(new ArrayList<>(en.getValue())); // own, editable copy per variant
						undo.clear();
						redo.clear();
						saved.clear();
						history.run();
						current[0] = res;
						list.labeler = res::label;
						list.grouper = res::group;
						list.badger = res::badges;
						list.setKeys(res.variants.keySet(), prev);
						updateExport.run();
						preview.setMessage(f.toString(), false);
					} else {
						current[0] = null;
						list.clear();
						preview.setLines(null);
						updateExport.run();
						preview.setMessage(msg(fe instanceof Exception ? (Exception) fe : new Exception(fe)), true);
					}
				});
			}, "perigraph-load");
			th.setDaemon(true);
			th.start();
		};

		list.onSelect(() -> {
			preview.setLines(current[0] == null ? null : pick(current[0], list.selectedKey()));
			refreshBoxes.run();
		});

		// export; "after" (may be null) runs once the file has been written, not when the dialog is cancelled or the export fails
		java.util.function.Consumer<Runnable> exportThen = after -> {
			if (current[0] == null || file[0] == null) return;
			// start in the nearest existing folder (outlines may not exist yet)
			Path start = outDir[0];
			while (start != null && !Files.isDirectory(start)) start = start.getParent();
			Result result = current[0];

			pickFile(frame, true, "Export", start, file[0].getFileName().toString(), chosen -> {
				try {
					Path out = chosen;
					if (!out.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json"))
						out = out.resolveSibling(out.getFileName() + ".json");
					TextFiles.write(out, OutlineJson.toJson(result)); // the system dialog already asked before overwriting
					outDir[0] = out.toAbsolutePath().getParent();
					Prefs.putPref("exportDir", outDir[0]);
					saved.clear();
					saved.addAll(undo);
					preview.setMessage(file[0].toString(), false);
				} catch (Exception ex) {
					preview.setMessage(msg(ex), true);
					return;
				}
				if (after != null) after.run();
			});
		};

		// runs "proceed" right away, or after the user decided what to do with unexported changes
		java.util.function.Consumer<Runnable> confirmLoss = proceed -> {
			if (!dirty.getAsBoolean()) { proceed.run(); return; }
			String[] options = {"Export", "Discard", "Cancel"};
			int choice = JOptionPane.showOptionDialog(frame, "Export or discard changes?", "Unsaved changes",
					JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE, null, options, options[0]);
			if (choice == 0) exportThen.accept(proceed);
			else if (choice == 1) proceed.run();
		};

		// opens a file without asking (the question has already been asked); setFile = the same for a dropped file, which asks first
		java.util.function.Consumer<Path> openFile = p -> {
			if (busy[0]) return;   // still loading the previous file
			file[0] = p.toAbsolutePath();
			root.reloadB.setEnabled(true);
			Path[] auto = AssetPaths.autoPaths(file[0]);
			if (outDir[0] == null) outDir[0] = auto[0] != null ? auto[1].toAbsolutePath().getParent() : file[0].getParent();
			inDir[0] = file[0].getParent();
			Prefs.putPref("importDir", inDir[0]);
			load.accept(true);
		};
		java.util.function.Consumer<Path> setFile = p -> { if (!busy[0]) confirmLoss.accept(() -> openFile.accept(p)); };

		root.importB.onClick(() -> {
			if (busy[0]) return;
			Path dir = inDir[0] != null ? inDir[0] : (file[0] != null ? file[0].getParent() : null);
			if (dir != null && !Files.isDirectory(dir)) dir = null;
			Path start = dir;
			confirmLoss.accept(() -> pickFile(frame, false, "Import", start, null, openFile));   // ask about unexported changes before the file dialog opens
		});
		root.reloadB.onClick(() -> confirmLoss.accept(() -> load.accept(false)));

		// Boxes and Model view: all models as a Java file; the suggested file and class name is the blockstate's name (camel case) plus 'suffix'; a name changed in the dialog is used as it is
		Path[] shapeDir = {Prefs.prefDir("shapeDir")};
		java.util.function.BiConsumer<String, java.util.function.Function<String, String>> exportJava = (suffix, content) -> {
			if (current[0] == null || file[0] == null) return;
			String base = file[0].getFileName().toString();
			if (base.toLowerCase(Locale.ROOT).endsWith(".json")) base = base.substring(0, base.length() - 5);
			Path start = shapeDir[0] != null ? shapeDir[0] : file[0].getParent();
			pickFile(frame, true, "Export", start, Names.camel(base) + suffix + ".java", chosen -> {
				try {
					String n = chosen.getFileName().toString();
					if (n.toLowerCase(Locale.ROOT).endsWith(".java")) n = n.substring(0, n.length() - 5);
					n = n.replaceAll("[^A-Za-z0-9_$]", "_");
					if (Character.isDigit(n.charAt(0))) n = "_" + n;
					Path out = chosen.resolveSibling(n + ".java");
					TextFiles.write(out, content.apply(n));   // the class is named like the file
					shapeDir[0] = out.toAbsolutePath().getParent();
					Prefs.putPref("shapeDir", shapeDir[0]);
				} catch (Exception ex) {
					preview.setMessage(msg(ex), true);
				}
			});
		};
		Runnable exportShape = () -> {
			if (current[0] == null || file[0] == null) return;
			List<Map.Entry<String, List<double[]>>> shapes = new ArrayList<>();
			for (String key : current[0].variants.keySet())
				shapes.add(new AbstractMap.SimpleEntry<>(ShapeCode.shapeName(current[0].group(key)), boxes.boxesFor(key)));
			exportJava.accept("VoxelShape", cls -> ShapeCode.shapeFile(shapes, cls));
		};
		root.exportB.onClick(() -> {
			if (root.mode == 2) exportJava.accept("Model", cls -> ModelCode.modelFile(current[0], ModelCode.codeCfg(), cls));   // every model once, not every variant
			else if (root.boxMode) exportShape.run();
			else exportThen.accept(null);
		});

		frame.setContentPane(root);

		// drag & drop a blockstate anywhere onto the window
		TransferHandler dnd = new TransferHandler() {
			@Override public boolean canImport(TransferSupport s) { return !settingsOpen[0] && s.isDataFlavorSupported(DataFlavor.javaFileListFlavor); }
			@Override @SuppressWarnings("unchecked")
			public boolean importData(TransferSupport s) {
				try {
					List<File> files = (List<File>) s.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
					if (files.isEmpty()) return false;
					setFile.accept(files.get(0).toPath());
					return true;
				} catch (Exception ex) { return false; }
			}
		};
		root.setTransferHandler(dnd);
		for (Component c : root.getComponents()) ((JComponent) c).setTransferHandler(dnd);   // incl. the settings view (it refuses while open)

		root.setPreferredSize(new Dimension(1100, 700));
		frame.pack();
		Insets in = frame.getInsets();   // the window border counts into the minimum size, otherwise the content gets narrower than intended
		frame.setMinimumSize(new Dimension(75 + 420 + Theme.GAP + 420 + Theme.GAP + in.left + in.right, 645 + in.top + in.bottom));   // viewer and sidebar / settings at their minimum widths; the hotkey page ends at 630
		restoreWindow(frame);

		// remember window position/size (the normal, non-maximized bounds) and the maximized state
		Rectangle[] normal = {frame.getBounds()};
		frame.addComponentListener(new ComponentAdapter() {
			void track() { if ((frame.getExtendedState() & JFrame.MAXIMIZED_BOTH) == 0) normal[0] = frame.getBounds(); }
			@Override public void componentMoved(ComponentEvent e) { track(); }
			@Override public void componentResized(ComponentEvent e) { track(); }
		});
		frame.addWindowListener(new WindowAdapter() {
			@Override public void windowClosing(WindowEvent e) {
				try {
					Rectangle r = normal[0];
					Prefs.PREFS.putInt("x", r.x); Prefs.PREFS.putInt("y", r.y); Prefs.PREFS.putInt("w", r.width); Prefs.PREFS.putInt("h", r.height);
					Prefs.PREFS.putBoolean("max", (frame.getExtendedState() & JFrame.MAXIMIZED_BOTH) != 0);
					Prefs.PREFS.flush();
				} catch (Exception ignored) { }
				confirmLoss.accept(() -> System.exit(0));
			}
		});
		frame.setVisible(true);
	}

	static void restoreWindow(JFrame frame) {
		try {
			int w = Prefs.PREFS.getInt("w", -1), h = Prefs.PREFS.getInt("h", -1), x = Prefs.PREFS.getInt("x", 0), y = Prefs.PREFS.getInt("y", 0);
			if (w > 0 && h > 0) {
				Rectangle r = new Rectangle(x, y, w, h);
				boolean visible = false;
				for (GraphicsDevice d : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices())
					if (d.getDefaultConfiguration().getBounds().intersects(r)) visible = true;
				if (visible) {
					frame.setBounds(r);
					if (Prefs.PREFS.getBoolean("max", false)) frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
					return;
				}
			}
		} catch (Exception ignored) { }
		frame.setLocationRelativeTo(null);
		frame.setExtendedState(JFrame.MAXIMIZED_BOTH); // first start
	}

	/** Shows an open/save dialog and passes the chosen file to 'done' (not called when cancelled). */
	static void pickFile(Frame frame, boolean save, String title, Path dir, String file, java.util.function.Consumer<Path> done) {
		FileDialog fd = new FileDialog(frame, title, save ? FileDialog.SAVE : FileDialog.LOAD);
		if (dir != null) fd.setDirectory(dir.toAbsolutePath().toString());
		fd.setFile(file != null ? file : "*.json");
		fd.setVisible(true); // blocks until closed; the system dialog asks itself before overwriting a file
		if (fd.getFile() != null) done.accept(Paths.get(fd.getDirectory(), fd.getFile()));
	}

	static List<double[]> pick(Result r, String sel) {
		return r == null || sel == null ? null : r.variants.get(sel);
	}

	static String msg(Throwable t) { return t.getMessage() != null ? t.getMessage() : t.toString(); }
}
