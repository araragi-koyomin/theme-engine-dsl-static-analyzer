package com.huawei.theme.analysis.core.macro;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.huawei.theme.analysis.core.shared.ast.DslAttributeNode;
import com.huawei.theme.analysis.core.shared.ast.DslElementNode;
import com.huawei.theme.analysis.core.shared.ast.DslFileNode;

/**
 * Resolves the "context root" for a {@code function_*.xml} sub-file: the
 * {@code script_*.xml} in the same directory that {@code <Include>}s this sub-file.
 *
 * <p>Per the Include design, a {@code function_*.xml} is never analyzed standalone —
 * its analysis context is the main {@code script_*.xml} that includes it. This class
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

    /**
     * All {@code script_*.xml} files in the sub-file's directory that contain an
     * {@code <Include name="<funcName>">}. Empty if none, >1 if multiple context roots.
     */
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
            if (!scriptName.startsWith("script_") || !scriptName.endsWith(".xml")) {
                continue;
            }
            String scriptPath = joinPath(dir, scriptName);
            String content = expander.loadFile(scriptPath);
            if (content != null && content.contains("<" + INCLUDE_TAG) && includesFunction(content, funcName)) {
                roots.add(scriptPath);
            }
        }
        return roots;
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

    private static boolean includesFunction(@NotNull String content, @NotNull String funcName) {
        int idx = content.indexOf("<" + INCLUDE_TAG);
        while (idx >= 0) {
            int end = content.indexOf('>', idx);
            if (end < 0) {
                break;
            }
            String tagText = content.substring(idx, end + 1);
            if (matchesName(tagText, funcName)) {
                return true;
            }
            idx = content.indexOf("<" + INCLUDE_TAG, end);
        }
        return false;
    }

    private static boolean matchesName(@NotNull String tagText, @NotNull String funcName) {
        int nameIdx = tagText.indexOf(NAME_ATTR);
        while (nameIdx >= 0) {
            int eq = tagText.indexOf('=', nameIdx);
            if (eq < 0) {
                break;
            }
            int q1 = eq + 1;
            while (q1 < tagText.length() && Character.isWhitespace(tagText.charAt(q1))) {
                q1++;
            }
            if (q1 >= tagText.length()) {
                break;
            }
            char quote = tagText.charAt(q1);
            if (quote != '"' && quote != '\'') {
                nameIdx = tagText.indexOf(NAME_ATTR, q1);
                continue;
            }
            int q2 = tagText.indexOf(quote, q1 + 1);
            if (q2 < 0) {
                break;
            }
            String value = tagText.substring(q1 + 1, q2);
            if (funcName.equals(value)) {
                return true;
            }
            nameIdx = tagText.indexOf(NAME_ATTR, q2);
        }
        return false;
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
