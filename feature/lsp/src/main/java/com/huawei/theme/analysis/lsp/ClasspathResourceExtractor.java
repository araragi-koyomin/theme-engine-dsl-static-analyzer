package com.huawei.theme.analysis.lsp;

import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

/**
 * Extracts the built-in {@code rules/} and {@code functions/} resources from
 * the classpath into a temporary directory, so that {@link
 * com.huawei.theme.analysis.core.rulelibrary.JsonRuleLoader} (which uses
 * {@code Files.walk}) can load them uniformly whether the server runs from a
 * fat jar or from exploded class directories.
 *
 * <p>Two roots are extracted: {@code <tmp>/rules} and {@code <tmp>/functions}.
 * The method returns the tmp root; callers append the sub-directory name.</p>
 */
final class ClasspathResourceExtractor {

    private static final String[] ROOTS = {"rules", "functions"};

    static Path extractBuiltinResources() throws IOException {
        Path tmp = Files.createTempDirectory("dsl-lsp-resources");
        for (String root : ROOTS) {
            extractRoot(root, tmp.resolve(root));
        }
        return tmp;
    }

    private static void extractRoot(String root, Path target) throws IOException {
        URL anchor = ClasspathResourceExtractor.class.getClassLoader().getResource(root);
        if (anchor == null) {
            return;
        }
        if ("jar".equals(anchor.getProtocol())) {
            extractFromJar(anchor, root, target);
        } else if ("file".equals(anchor.getProtocol())) {
            extractFromDir(anchor, target);
        }
    }

    private static void extractFromJar(URL anchor, String root, Path target) throws IOException {
        JarURLConnection conn = (JarURLConnection) anchor.openConnection();
        try (JarFile jar = conn.getJarFile()) {
            String prefix = root + "/";
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                if (!name.startsWith(prefix) || entry.isDirectory()) {
                    continue;
                }
                Path dest = target.resolve(name.substring(prefix.length()));
                Files.createDirectories(dest.getParent());
                try (InputStream in = jar.getInputStream(entry)) {
                    Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private static void extractFromDir(URL anchor, Path target) throws IOException {
        Path source = toPath(anchor);
        if (!Files.isDirectory(source)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(source)) {
            walk.forEach(s -> {
                try {
                    Path rel = source.relativize(s);
                    Path dest = target.resolve(rel.toString());
                    if (Files.isDirectory(s)) {
                        Files.createDirectories(dest);
                    } else {
                        Files.createDirectories(dest.getParent());
                        Files.copy(s, dest, StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (IOException ignored) {
                    // skip individual file copy errors
                }
            });
        }
    }

    private static Path toPath(URL anchor) {
        try {
            return Paths.get(anchor.toURI());
        } catch (URISyntaxException e) {
            return Paths.get(anchor.getPath());
        }
    }

    private ClasspathResourceExtractor() {
    }
}
