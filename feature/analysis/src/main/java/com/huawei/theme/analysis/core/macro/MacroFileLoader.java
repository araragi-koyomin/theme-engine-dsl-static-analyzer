package com.huawei.theme.analysis.core.macro;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Loads a file's text content by path. Used by {@link IncludeHandler} to load included
 * {@code function_*.xml} sub-files. The default {@link #DISK} implementation reads from the
 * real filesystem via {@link Files#readString}; the editor (Phase 3) can supply a VFS-backed
 * loader so included files inside JARs / the IDE's virtual FS resolve too.
 */
@FunctionalInterface
public interface MacroFileLoader {

    @Nullable String loadFile(@NotNull String path);

    MacroFileLoader DISK = path -> {
        try {
            return Files.readString(Path.of(path), StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    };
}
