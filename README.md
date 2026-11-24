# Euclid

3D inspector and converter, importing Minecraft Java Edition block models and states, exporting voxel shapes and data code. No dependencies required besides a minimum of Java 25.

### Import

Euclid supports both a single block model or block state. If the file is inside an `assets` folder, Euclid resolves every referenced parent or model within the imported file, as long as the structure follows Minecraft's asset structure. With this, a block state brings in every model of its variants, each one rotated by the variant's `x` and `y` rotation. Variants with several weighted models list each model on its own. Multipart block states are not supported yet.

### Mode

Switch between the three modes **Lines**, **Voxel Shape** and **Model**. The viewport, model panel and output follow the current selection.

<img width="2560" height="1440" alt="Mode" src="https://github.com/user-attachments/assets/0be8dfcd-47ad-46b4-baa7-57d9da7582ce"/>

In **Lines**, Euclid draws the outline of the selected model. All elements are merged into one body, dropping edges hidden inside it and seams between flush faces, while lines on the same edge are joined into one. Hovering over unwanted lines marks them red, clicking then removes them. Undo brings back removed lines and redo removes them again.

<img width="2560" height="1440" alt="Edit" src="https://github.com/user-attachments/assets/79306a8d-5ab0-4448-b5fa-463d65d0ea74"/>

The export contains the lines of every variant as JSON for a custom block outline renderer. Each key is a comma separated list of `property=value`, an empty key works as catch-all fallback. Each entry is a weighted model, where `weight` is at least `1`. Each outline is one line segment `[x1, y1, z1, x2, y2, z2]`. The output is meant to be read by a `SimpleJsonResourceReloadListener` and drawn on `ExtractBlockOutlineRenderStateEvent`.

``` JSON
{
  "variants": {
    "property=value": [
      {
        "weight": 1,
        "outlines": [
          [0, 0, 0, 0, 0, 1],
          [0, 0, 1, 0, 1, 1],
          [0, 1, 1, 0, 1, 0],
          [0, 1, 0, 0, 0, 0],
          [1, 0, 0, 1, 0, 1],
          [1, 0, 1, 1, 1, 1],
          [1, 1, 1, 1, 1, 0],
          [1, 1, 0, 1, 0, 0],
          [0, 0, 1, 1, 0, 1],
          [1, 0, 0, 0, 0, 0],
          [0, 1, 1, 1, 1, 1],
          [1, 1, 0, 0, 1, 0]
        ]
      }
    ]
  }
}
```

In **Voxel Shape**, Euclid covers the selected model with boxes. Straight elements become exact boxes, and boxes lying fully inside another one are dropped. Tilted elements are filled with one pixel cells, merged into as few boxes as possible. The export contains Java code with one `VoxelShape` per variant.

``` JAVA
public static final VoxelShape VOXEL_SHAPE = Stream.of(Block.box(0.0D, 0.0D, 0.0D, 16.0D, 16.0D, 16.0D)).reduce((a, b) -> Shapes.join(a, b, BooleanOp.OR)).get();
```

In **Model**, Euclid turns the model into data code using NeoForge's `ExtendedModelTemplateBuilder`, with every parent already resolved. Each model is only written once, no matter how many variants use it. The `parent` Identifier and the `texture` Texture Slot Location can be set in the settings.

``` JAVA
public static ExtendedModelTemplate model() {
	return ExtendedModelTemplateBuilder.builder().parent(IcariaModelProvider.blockFile(IcariaIds.MC, "block"))
		.element(elementBuilder -> elementBuilder.from(0.0F, 0.0F, 0.0F).to(16.0F, 16.0F, 16.0F)
			.face(Direction.NORTH, faceBuilder -> faceBuilder.uvs(0.0F, 0.0F, 16.0F, 16.0F).texture(IcariaTextureSlots.MISSING))
			.face(Direction.EAST, faceBuilder -> faceBuilder.uvs(0.0F, 0.0F, 16.0F, 16.0F).texture(IcariaTextureSlots.MISSING))
			.face(Direction.SOUTH, faceBuilder -> faceBuilder.uvs(0.0F, 0.0F, 16.0F, 16.0F).texture(IcariaTextureSlots.MISSING))
			.face(Direction.WEST, faceBuilder -> faceBuilder.uvs(0.0F, 0.0F, 16.0F, 16.0F).texture(IcariaTextureSlots.MISSING))
			.face(Direction.UP, faceBuilder -> faceBuilder.uvs(0.0F, 0.0F, 16.0F, 16.0F).texture(IcariaTextureSlots.MISSING))
			.face(Direction.DOWN, faceBuilder -> faceBuilder.uvs(0.0F, 0.0F, 16.0F, 16.0F).texture(IcariaTextureSlots.MISSING)))
		.build();
}
```

### Viewport

The viewport changes based on mode. **Lines** and **Voxel Shape** display the selected model as its 3D representation. Rotate the view with the left mouse button, move it with the right mouse button and zoom with the mouse wheel. Preferences can be set in the settings. **Model** displays the selected model as its data code representation.

<img width="2560" height="1440" alt="Viewport" src="https://github.com/user-attachments/assets/bf548fa6-de46-4bec-a357-10e817260428"/>

### Variants

The model panel right of the viewport shows every model of the file, grouped by block state condition. The selected model is shown in the viewport.

<img width="2560" height="1440" alt="Variants" src="https://github.com/user-attachments/assets/f1d13c85-6db0-4cbc-9ec1-919f373443cd"/>

### Settings

The settings tab is home for hotkey, viewer and output settings. Settings are saved across sessions.

<img width="2560" height="1440" alt="Settings" src="https://github.com/user-attachments/assets/22c53901-9b7e-436c-a759-92be0bde8b79"/>
