package com.huawei.theme.analysis.core.macro;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Loads an included file's text by path. The default {@link #DISK} reads the real
 * filesystem; the editor supplies a VFS-backed loader so unsaved PSI is analyzed.
 */
public interface MacroFileLoader {

    @Nullable String loadFile(@NotNull String path);

    MacroFileLoader DISK = new MacroFileLoader() {
        @Override
        public @Nullable String loadFile(@NotNull String path) {
            try {
                return Files.readString(Path.of(path), StandardCharsets.UTF_8);
            } catch (IOException | RuntimeException e) {
                return null;
            }
        }
    };
}
