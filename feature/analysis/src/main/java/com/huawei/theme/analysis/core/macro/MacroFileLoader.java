package com.huawei.theme.analysis.core.macro;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Loads a file's text content + lists a directory's files by path. Used by
 * {@link IncludeHandler} (load included {@code function_*.xml} sub-files) and by
 * {@link ContextRootResolver} (scan the main file's directory for {@code script_*.xml}
 * context roots). The default {@link #DISK} reads the real filesystem; the editor
 * (Phase 3) can swap a VFS-backed loader.
 */
public interface MacroFileLoader {

    @Nullable String loadFile(@NotNull String path);

    @Nullable List<String> listFiles(@NotNull String dirPath);

    MacroFileLoader DISK = new MacroFileLoader() {
        @Override
        public @Nullable String loadFile(@NotNull String path) {
            try {
                return Files.readString(Path.of(path), StandardCharsets.UTF_8);
            } catch (IOException | RuntimeException e) {
                return null;
            }
        }

        @Override
        public @Nullable List<String> listFiles(@NotNull String dirPath) {
            try (Stream<Path> paths = Files.list(Path.of(dirPath))) {
                return paths.filter(Files::isRegularFile)
                        .map(p -> p.getFileName().toString())
                        .collect(Collectors.toList());
            } catch (IOException | RuntimeException e) {
                return null;
            }
        }
    };
}
