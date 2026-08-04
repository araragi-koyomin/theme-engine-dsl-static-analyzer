package com.huawei.theme.analysis.core.macro;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.huawei.theme.analysis.core.shared.ast.DslAttributeNode;
import com.huawei.theme.analysis.core.shared.ast.DslElementNode;
import com.huawei.theme.analysis.core.shared.ast.DslFileNode;

/**
 * Resolves the "context root" for a {@code function_*.xml} sub-file: the
 * {@code script.xml} or {@code script_*.xml} in the same directory that
 * {@code <Include>}s this sub-file.
 *
 * <p>Per the Include design, a {@code function_*.xml} is never analyzed standalone —
 * its analysis context is the main script file that includes it. This class
 * finds that main (or reports 0 / multiple) and, for the single-main case, extracts
 * the param key/value pairs the main's {@code <Include>} passes to this sub-file
 * (so the editor can demacro the sub-file standalone with those params as the
 * compile-time scope).</p>
 */
public final class ContextRootResolver {

    public static final String RULE_NO_CONTEXT_ROOT = "MACRO-008";
    public static final String RULE_MULTIPLE_CONTEXT_ROOT = "MACRO-009";

    private static final String NAME_ATTR = "name";
    private static final String INCLUDE_TAG = "Include";

    private final MacroExpander expander;

    public ContextRootResolver(@NotNull MacroExpander expander) {
        this.expander = expander;
    }

    @NotNull
    public List<String> findContextRoots(@NotNull String functionFilePath) {
        String dir = parentDir(functionFilePath);
        String funcName = fileName(functionFilePath);
        if (dir == null || funcName == null) {
            return Collections.emptyList();
        }
        List<String> scriptFiles = expander.loadFileNames(dir);
        if (scriptFiles == null) {
            return Collections.emptyList();
        }
        List<String> roots = new ArrayList<>();
        for (String scriptName : scriptFiles) {
            if (!isScriptFile(scriptName)) {
                continue;
            }
            String scriptPath = joinPath(dir, scriptName);
            if (includesFunction(scriptPath, funcName, new HashSet<>())) {
                roots.add(scriptPath);
            }
        }
        return roots;
    }

    private static boolean isScriptFile(@NotNull String fileName) {
        return "script.xml".equals(fileName)
                || fileName.startsWith("script_") && fileName.endsWith(".xml");
    }

    /**
     * The param key/value pairs the main's {@code <Include name="<funcName>">} passes.
     * Raw (pre-interpolation) values — the editor demacros the sub-file with these as
     * the compile-time scope. Empty if the main has no such include or it passes no params.
     */
    @NotNull
    public Map<String, String> extractIncludeParams(@NotNull String mainFilePath, @NotNull String funcName) {
        String content = expander.loadFile(mainFilePath);
        if (content == null) {
            return Collections.emptyMap();
        }
        DslFileNode mainAst = expander.buildNormalAst(mainFilePath, content);
        DslElementNode root = mainAst.getRootElement();
        if (root == null) {
            return Collections.emptyMap();
        }
        Optional<DslElementNode> includeNode = findInclude(root, funcName);
        if (includeNode.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, String> params = new HashMap<>();
        if (includeNode.get().getAttributes() != null) {
            for (DslAttributeNode a : includeNode.get().getAttributes()) {
                if (!NAME_ATTR.equals(a.getName()) && a.getName() != null && a.getValue() != null) {
                    params.put(a.getName(), a.getValue().getRawValue());
                }
            }
        }
        return params;
    }

    private static Optional<DslElementNode> findInclude(@NotNull DslElementNode node, @NotNull String funcName) {
        if (INCLUDE_TAG.equals(node.getTagName()) && funcName.equals(attrValue(node, NAME_ATTR))) {
            return Optional.of(node);
        }
        if (node.getChildElements() != null) {
            for (DslElementNode child : node.getChildElements()) {
                Optional<DslElementNode> found = findInclude(child, funcName);
                if (found.isPresent()) {
                    return found;
                }
            }
        }
        return Optional.empty();
    }

    private boolean includesFunction(@NotNull String filePath,
                                     @NotNull String targetName,
                                     @NotNull Set<String> visited) {
        if (!visited.add(filePath)) {
            return false;
        }
        String content = expander.loadFile(filePath);
        if (content == null) {
            return false;
        }
        DslElementNode root = expander.buildNormalAst(filePath, content).getRootElement();
        if (root == null) {
            return false;
        }
        List<String> includes = new ArrayList<>();
        collectIncludes(root, includes);
        String dir = parentDir(filePath);
        for (String include : includes) {
            if (targetName.equals(include)) {
                return true;
            }
            if (dir != null && include.startsWith("function_") && include.endsWith(".xml")
                    && includesFunction(joinPath(dir, include), targetName, visited)) {
                return true;
            }
        }
        return false;
    }

    private static void collectIncludes(@NotNull DslElementNode node, @NotNull List<String> includes) {
        if (INCLUDE_TAG.equals(node.getTagName())) {
            String name = attrValue(node, NAME_ATTR);
            if (name != null) {
                includes.add(name);
            }
        }
        if (node.getChildElements() != null) {
            for (DslElementNode child : node.getChildElements()) {
                collectIncludes(child, includes);
            }
        }
    }

    @Nullable
    private static String attrValue(@NotNull DslElementNode node, @NotNull String attrName) {
        if (node.getAttributes() == null) {
            return null;
        }
        for (DslAttributeNode a : node.getAttributes()) {
            if (attrName.equals(a.getName()) && a.getValue() != null) {
                return a.getValue().getRawValue();
            }
        }
        return null;
    }

    @Nullable
    private static String parentDir(@NotNull String path) {
        int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return slash < 0 ? null : path.substring(0, slash);
    }

    @Nullable
    private static String fileName(@NotNull String path) {
        int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return slash < 0 ? path : path.substring(slash + 1);
    }

    @NotNull
    private static String joinPath(@NotNull String dir, @NotNull String name) {
        return dir.endsWith("/") || dir.endsWith("\\") ? dir + name : dir + "/" + name;
    }
}
