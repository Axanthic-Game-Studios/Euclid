package com.axanthic.euclid.io;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Reads and writes text files as UTF-8.
 * Missing parent folders are created when writing.
 */
public final class TextFiles {
	public static String readString(Path p) throws IOException {
		return new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
	}

	public static void write(Path out, String json) throws IOException {
		if (out.getParent() != null) Files.createDirectories(out.getParent());
		Files.write(out, json.getBytes(StandardCharsets.UTF_8));
	}
}
