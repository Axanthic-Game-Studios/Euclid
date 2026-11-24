# Euclid

Euclid is a small desktop tool for NeoForge mod development. It reads Minecraft block JSON (a blockstate or a single block model) and turns it into code or data for your mod:

- **Lines**: outline JSON for a custom block outline renderer.
- **Voxel Shape**: Java code for a `VoxelShape` built from `Block.box(...)` calls.
- **Model**: datagen code using NeoForge's `ExtendedModelTemplateBuilder`.

It is written in plain Java Swing and has no dependencies.

## Requirements

- Java 25 or newer.

## Running

Build the jar (see [Building](#building)) or download it from the artifacts of a [GitHub Actions](https://github.com/Axanthic-Game-Studios/Euclid/actions) run and start it:

```
java -jar Euclid-<VERSION>.jar
```

Euclid is GUI only. Command line arguments are ignored.

Open a file with the import hotkey or drag and drop it onto the window. Accepted inputs:

- a blockstate JSON, `assets/<modid>/blockstates/<block>.json`
- a block model JSON, `assets/<modid>/models/block/<block>.json`

Anything else, including Blockbench `.bbmodel` files, is rejected as an invalid file.

When the file lives inside an `assets` folder, Euclid uses that folder to resolve parent models and referenced models from other namespaces, and proposes `assets/<modid>/outlines/<block>.json` as the export target for Lines mode. Outside an `assets` folder the target is `<block>_outline.json` next to the input.

### Hotkeys

All hotkeys can be changed in the settings and are remembered between runs. The defaults use Ctrl (Cmd on macOS):

| Action   | Default |
|----------|---------|
| Undo     | Ctrl+Z  |
| Redo     | Ctrl+Y  |
| Settings | Ctrl+S  |
| Import   | Ctrl+I  |
| Reload   | Ctrl+R  |
| Export   | Ctrl+E  |

## Output modes

### Lines

Produces the outline of every variant as line segments. The format:

```json
{
  "variants": {
    "facing=north,half=bottom": [
      { "weight": 1, "outlines": [[0, 0, 0, 1, 0, 0], [1, 0, 0, 1, 1, 0]] }
    ],
    "": [
      { "weight": 1, "outlines": [[0, 0, 0, 0, 1, 0]] }
    ]
  }
}
```

- Each key is a comma separated list of `property=value`. The empty key is the catch-all.
- Each entry is a weighted model. `weight` is always written and is at least 1.
- Each outline is one line segment `[x1, y1, z1, x2, y2, z2]`.

The JSON is meant to be read by a reload listener in the mod and drawn on `ExtractBlockOutlineRenderStateEvent`. That client code is not part of this repository.

### Voxel Shape

Produces one line of Java per shape, combining the boxes of the model:

```java
Stream.of(Block.box(0, 0, 0, 16, 8, 16), Block.box(0, 8, 0, 8, 16, 16)).reduce((a, b) -> Shapes.join(a, b, BooleanOp.OR)).get();
```

### Model

Produces datagen code for the model elements using `ExtendedModelTemplateBuilder`. Every model is written once, not once per variant. The parent defaults to `Identifier.withDefaultNamespace("block/block")` and can be changed in the settings, together with the texture slot location. Face rotations are written with `Quadrant` constants (`R90`, `R180`, `R270`).

Generated code contains no imports. Add them in your mod.

## Building

Euclid is built with Gradle. The wrapper is included, a JDK 25 is needed:

```
./gradlew jar
```

This compiles `src/main/java`, adds the fonts from `src/main/resources/fonts` and `LICENSE.md` and writes `build/libs/Euclid-<VERSION>.jar`. The version is set in `gradle.properties`.

GitHub Actions (`.github/workflows/build.yml`) runs `./gradlew build` on every push and pull request and uploads the jar as a build artifact.

For development, open the project in IntelliJ as a Gradle project and run `com.axanthic.euclid.Euclid`.

## Project layout

```
src/main/java/com/axanthic/euclid/
  Euclid.java   entry point and version
  io/           JSON parser, file and path helpers, preferences
  load/         blockstate and model loading, variant resolution
  geometry/     vectors, element corners, outline union, voxel boxes
  output/       Lines JSON, Voxel Shape code, Model code
  gui/          window, preview, variant list, settings, theme
src/main/resources/fonts/  Noto Sans fonts and their licence
```

## Fonts

The interface uses Noto Sans Display and Noto Sans Mono, bundled under the SIL Open Font License (`src/main/resources/fonts/OFL.txt`). If the fonts are missing, Euclid falls back to the Java default fonts.

## License

Euclid is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version. See [LICENSE](LICENSE.md). The bundled fonts keep their own license (SIL Open Font License 1.1).
