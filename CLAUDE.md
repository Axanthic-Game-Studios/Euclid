# Euclid

Euclid is a Java Swing tool (sources in `src/main/java/com/axanthic/euclid`, no dependencies) for NeoForge mods. It reads Minecraft block JSON and produces three kinds of output, selectable as modes in the GUI.

Answer in the language the user writes in (usually German or English). Code, identifiers and comments in the code are English.

## What it does

Input is either a NeoForge blockstate JSON (`assets/<modid>/blockstates/*.json`) or a single block model JSON. Anything that is neither must be rejected with the invalid file error, including Blockbench `.bbmodel` files. `isModel` and `elementsOk` enforce this, so keep them strict.

- Lines mode: outline JSON for a custom block outline renderer in the user's mod.
  Format: `{ "variants": { "<condition>": [ { "weight": n, "outlines": [[x1,y1,z1,x2,y2,z2], ...] } ] } }`.
  `weight` is always written and is at least 1. The condition is a comma separated list of `property=value`, the empty string is the catch-all.
  The output must stay compatible with the outline codec on the mod side and the vanilla codec.
- Voxel Shape mode: Java code for a `VoxelShape`, one `Block.box(...)` per box, joined on a single line with `Stream.of(...).reduce((a, b) -> Shapes.join(a, b, BooleanOp.OR)).get();` (`shapeCode`, `shapeFile`).
- Model mode: datagen code using NeoForge's `ExtendedModelTemplateBuilder` (`modelCode`, `modelFile`, `codeCfg`).

Usage: GUI only, started with `java -jar Euclid-<VERSION>.jar`. There is no command line mode, command line arguments are ignored.

## Code layout (src/main/java/com/axanthic/euclid)

Main class is `com.axanthic.euclid.Euclid`. Static helpers are called class qualified (`Vectors.rotate(...)`), no static imports.

- `Euclid`: `main`, error dialog.
- `io`: `Json` (minimal parser plus `asMap`, `asList`, `num`, `vec`, `numList`), `TextFiles`, `AssetPaths` (`autoPaths`), `Prefs` (Java preferences, node `/<unnamed>` so settings from the old default-package build are kept).
- `load`: `Blockstates` (`compute` routes to `computeModel` or `computeBlockstate`), `Models` (`resolveModel`, `findElements`, `elementsOk`, `checkModels`, `isModel`, `flatModel`, `modelLines`), `Part` (multiblock parts), `Result`, `Row`.
- `geometry`: `Vectors`, `Elements` (`corners`), `Outlines` (union outline, with `Face`, `Solid`, `SegInfo`, `Verts`), `Boxes` (voxel shape boxes).
- `output`: `OutlineJson` (Lines mode), `ShapeCode` (`shapeCode`, `shapeFile`), `ModelCode` (`modelCode`, `modelFile`, `codeCfg`, `quadrant`), `Names`.
- `gui`: `App` (window, load/export/undo/hotkey wiring), `Theme` (colours, sizes, fonts), `Icons`, and the components `Root`, `Preview`, `VariantList`, `ModeColumn`, `ModelPane`, `SettingsView`, `FlatButton`, `HoverFade`, `Hotkeys`, `MouseButtons`, `ThinBar`, `BoxCache`.

## Rules for the generated code

These were decided with the user, do not change them without asking.

- No imports are generated. The user adds them in the mod.
- Model mode always writes `.parent(<cfg[0]>)`, default `Identifier.withDefaultNamespace("block/block")`, independent of the model's own parent and of what `flatModel` resolved. `cfg[1]` is the texture slot location.
- If `minecraft:block/block` is in the asset roots, the output also contains inlined `.transform(...)` lines from its `display`. This is a known open point, ask before changing it.
- Face rotation is written with `Quadrant` constants (`com.mojang.math.Quadrant`): `.rotation(Quadrant.R90)`, `R180`, `R270`. `R0` and values that are not a multiple of 90 are omitted.
- Models are written once each, not once per variant.

## Resources and build

- Fonts are loaded from the classpath: `/fonts/NotoSansDisplay-Regular.ttf`, `/fonts/NotoSansDisplay-SemiBold.ttf`, `/fonts/NotoSansMono-Regular.ttf`, plus `OFL.txt`. Missing fonts silently fall back to the Java defaults, so the code never fails because of them. Keep the files and the licence in the jar.
- `LICENSE.md` (GNU GPL v3 or later) is copied to the jar root by the build.
- No other resources.
- Targets Java 25 (current LTS), compiled with `--release 25`. All Java 25 language features and APIs may be used.
- No dependencies, not even for tests.
- Build: Gradle (Groovy DSL, `build.gradle`, wrapper 9.9.2). `./gradlew jar` writes `build/libs/Euclid-<VERSION>.jar`. The version is set in `gradle.properties` (`version=...`), bump it there for every behaviour change. The app itself does not know its version. Standard Gradle layout (`src/main/java`, `src/main/resources`), package `com.axanthic.euclid`. The jar gets the fonts and `LICENSE.md`. A Java 25 toolchain is required. GitHub Actions (`.github/workflows/build.yml`) runs `./gradlew build` on every push and pull request and uploads the jar as an artifact. There is no release automation.
- Current version: 0.24.5.
- Test with a real blockstate that has many variants (a 64 variant blockstate was used as the regression case) and compare the output of the old and the new build: only the intended lines may differ. The GUI can be run headless under `xvfb` for checks.

## Companion mod code (not in this repo)

The exported outline JSON is consumed by the user's NeoForge 26.x client code (Mojang names, `Identifier`):

- A record with a codec for the `variants` map of `WeightedList<List<List<Float>>>`.
- A reload listener (`SimpleJsonResourceReloadListener` of that record) bakes all states into a `Map<BlockState, WeightedList<...>>` cache. It orders conditions by specificity (most properties first, catch-all last) and picks a weighted model seeded from the block position.
- A client event handler listens to `ExtractBlockOutlineRenderStateEvent` and draws the lines.

The user's own files are the source of truth. If Euclid's output format has to change, say what the mod side would need to change too.

## Code style

Applies to all Java the user will see, in Euclid and in anything written for the user's mods.

- Tabs for indentation, braces always, `var` in the mod code and in Euclid.
- Parameters are prefixed with `p`, for example `pBlockState`.
- Static calls are class qualified, for example `Vectors.rotate(...)`.
- Prefer method references over lambdas with explicit parameter types. If the type is needed for `.reversed()`, use a method reference on the key instead.
- Annotations come directly before the class, followed by a blank line, then the class.
- Javadocs are plain descriptions of exactly two lines per method and field. No `@param` or `@return`, no colons, no examples, no `""` notation for the catch-all, and do not state things that are not always true (for example that neighbouring blocks differ).
- No `//` comments in the mod code and no section divider comments.
- Keep methods small and extract helpers when the user asks, name them by what they do.

## Working with the user

- Work piece by piece. The user often rewrites the result in their own style, so keep changes minimal and focused on the request.
- Verify before claiming. API names for NeoForge or Minecraft 26.x that cannot be checked here must be flagged as unverified.
- Do not claim performance effects without a reason, and correct earlier claims when they were wrong.
- Never reproduce the user's mod code from memory, ask for the current file instead.
- When a task needs a decision the user has not made (for example removing the duplicate `.transform(...)` lines), ask one short question instead of guessing.
