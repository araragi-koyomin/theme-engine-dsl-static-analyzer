package com.huawei.theme.analysis.lsp;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.huawei.theme.analysis.core.expression.ExpressionNode;
import com.huawei.theme.analysis.core.rulelibrary.RuleRepository;
import com.huawei.theme.analysis.core.shared.ast.DslAttributeNode;
import com.huawei.theme.analysis.core.shared.ast.DslAttributeValueNode;
import com.huawei.theme.analysis.core.shared.ast.DslElementNode;
import com.huawei.theme.analysis.core.shared.ast.DslFileNode;
import com.huawei.theme.analysis.core.shared.ast.ExpressionAstNode;
import com.huawei.theme.analysis.core.shared.ast.ExpressionKind;
import com.huawei.theme.analysis.core.syntaxanalysis.AstBuilder;

/**
 * Builds LSP {@code SemanticTokens} data covering the whole document — XML
 * structure (tag names, attribute names, comments, the XML declaration) and
 * embedded DSL expressions inside attribute values (variable refs, function
 * calls, literals).
 *
 * <p>The legend uses only standard LSP token types, so any conformant client
 * (VS Code, Neovim, Helix) maps them to theme colors automatically — no
 * per-editor {@code semanticTokenScopes} config required. Structural types
 * (tag/attribute/comment/keyword) are appended after the expression types so
 * the IntelliJ client (which only maps the first four expression indices)
 * ignores them and lets its native XML highlighter handle structure, avoiding
 * double-highlighting.</p>
 *
 * <p>Tokens are sorted by document position (line, column) and delta-encoded
 * into the LSP relative format
 * {@code [deltaLine, deltaStart, length, tokenType, tokenModifier]}.
 * Sorting is required because AST traversal order does not guarantee
 * document-position order — e.g. a FUNCTION_CALL starts at the function name
 * but its child VARIABLE_REF starts at an earlier column (the {@code #}
 * prefix). Without sorting the delta encoding would produce negative
 * offsets.</p>
 */
final class SemanticTokensProvider {

    /**
     * Legend indices must match the emission sites below. Expression types
     * (0–3) come first; tag types (4–7) distinguish categories; structural
     * types (8–10) are appended so the IntelliJ client can ignore them.
     */
    static final List<String> TOKEN_TYPES = List.of(
            "variable", "function", "number", "string",
            "tag", "tagRoot", "tagVariable", "tagCommand",
            "property", "comment", "keyword");
    static final List<String> TOKEN_MODIFIERS = List.of();

    private static final int TYPE_VARIABLE = 0;
    private static final int TYPE_FUNCTION = 1;
    private static final int TYPE_NUMBER = 2;
    private static final int TYPE_STRING = 3;
    private static final int TYPE_TAG = 4;
    private static final int TYPE_TAG_ROOT = 5;
    private static final int TYPE_TAG_VARIABLE = 6;
    private static final int TYPE_TAG_COMMAND = 7;
    private static final int TYPE_ATTRIBUTE = 8;
    private static final int TYPE_COMMENT = 9;
    private static final int TYPE_KEYWORD = 10;

    private static final Pattern COMMENT_PATTERN = Pattern.compile("<!--[\\s\\S]*?-->");
    private static final Pattern NUMERIC_PATTERN = Pattern.compile("-?\\d+(?:\\.\\d+)?");

    private final RuleRepository ruleRepository;

    SemanticTokensProvider(RuleRepository ruleRepository) {
        this.ruleRepository = ruleRepository;
    }

    List<Integer> collect(String uri, String text) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }
        int[] lineStarts = buildLineStarts(text);
        List<int[]> tokens = new ArrayList<>();
        DslFileNode ast;
        try {
            ast = new AstBuilder(ruleRepository).getDslAst(uri, text);
        } catch (RuntimeException e) {
            ast = null;
        }
        if (ast != null && ast.getRootElement() != null) {
            walk(ast.getRootElement(), tokens);
            emitXmlDeclaration(ast.getXmlDeclaration(), text, lineStarts, tokens);
        }
        // Comments are scanned from the raw text: AstBuilder does not capture
        // XML COMMENT events into the AST, so structural comment ranges are
        // recovered here. This also covers documents that fail to parse.
        scanComments(text, lineStarts, tokens);
        if (tokens.isEmpty()) {
            return List.of();
        }
        tokens.sort((a, b) -> a[0] != b[0] ? a[0] - b[0] : a[1] - b[1]);
        return deltaEncode(tokens);
    }

    private void walk(DslElementNode element, List<int[]> tokens) {
        if (element == null) {
            return;
        }
        String tagName = element.getTagName();
        if (tagName != null && !tagName.isEmpty()) {
            // Tag name immediately follows '<' (element.column points at '<').
            int line = element.getLine() - 1;
            int col = element.getColumn() + 1;
            if (line >= 0) {
                tokens.add(new int[]{line, col, tagName.length(), tagTypeOf(tagName)});
            }
        }
        List<DslAttributeNode> attrs = element.getAttributes();
        if (attrs != null) {
            for (DslAttributeNode attr : attrs) {
                String name = attr.getName();
                if (name != null && !name.isEmpty() && attr.getLine() > 0) {
                    int line = attr.getLine() - 1;
                    tokens.add(new int[]{line, attr.getColumn(), name.length(), TYPE_ATTRIBUTE});
                }
                DslAttributeValueNode value = attr.getValue();
                if (value != null) {
                    if (value.getExpression().isPresent()) {
                        ExpressionAstNode expr = value.getExpression().get();
                        if (expr instanceof ExpressionNode) {
                            emit((ExpressionNode) expr, tokens);
                        }
                    } else if (value.getLine() > 0 && value.getRawValue() != null) {
                        // Literal attribute value — emit a value token so it
                        // gets colored (numeric=number, boolean=keyword, else=string).
                        int vLine = value.getLine() - 1;
                        int vCol = value.getColumn();
                        int vLen = value.getRawValue().length();
                        if (vLen > 0) {
                            tokens.add(new int[]{vLine, vCol, vLen, attrValueTypeOf(value.getRawValue())});
                        }
                    }
                }
            }
        }
        List<DslElementNode> children = element.getChildElements();
        if (children != null) {
            for (DslElementNode child : children) {
                walk(child, tokens);
            }
        }
    }

    private void emit(ExpressionNode node, List<int[]> tokens) {
        int type = tokenTypeOf(node);
        if (type >= 0) {
            int line = node.getLine() - 1; // 1-based -> 0-based
            int col = node.getColumn();
            int length = lengthOf(node);
            if (line >= 0 && length > 0) {
                tokens.add(new int[]{line, col, length, type});
            }
        }
        if (node.getChildren() != null) {
            for (ExpressionNode c : node.getChildren()) {
                emit(c, tokens);
            }
        }
        if (node.getIndexExpression() != null) {
            emit(node.getIndexExpression(), tokens);
        }
    }

    private static int tokenTypeOf(ExpressionAstNode node) {
        ExpressionKind kind = node.getKind();
        switch (kind) {
            case VARIABLE_REF:
            case ARRAY_ACCESS:
                return TYPE_VARIABLE;
            case FUNCTION_CALL:
                return TYPE_FUNCTION;
            case LITERAL:
                String t = node.getText();
                return (t != null && !t.isEmpty() && t.charAt(0) == '\'') ? TYPE_STRING : TYPE_NUMBER;
            default:
                return -1; // skip BINARY/UNARY/CONDITIONAL/UNKNOWN
        }
    }

    /**
     * Maps a tag name to a category-specific token type by looking up its
     * {@link DslElementRule} category in the rule library:
     * <ul>
     *   <li>root → {@code tagRoot} (Lockscreen/Widget/Wallpaper/ChargingSkin)</li>
     *   <li>variable → {@code tagVariable} (Var/VarArray/Vars/Array/Items/…)</li>
     *   <li>command → {@code tagCommand} (Command/SoundCommand/…)</li>
     *   <li>everything else → {@code tag} (view/layout/animation/effect/…)</li>
     * </ul>
     * This gives different tag categories distinct colors in any LSP client.
     */
    private int tagTypeOf(String tagName) {
        var rule = ruleRepository.getElementRule(tagName);
        if (rule.isEmpty()) {
            return TYPE_TAG;
        }
        String category = rule.get().getCategory();
        if (category == null || category.isEmpty()) {
            return TYPE_TAG;
        }
        switch (category) {
            case "root":
                return TYPE_TAG_ROOT;
            case "variable":
                return TYPE_TAG_VARIABLE;
            case "command":
                return TYPE_TAG_COMMAND;
            default:
                return TYPE_TAG;
        }
    }

    /**
     * Classifies a literal attribute value for highlighting: numeric values
     * → {@code number}, boolean literals ({@code true}/{@code false}) →
     * {@code keyword}, everything else (strings, enum values) → {@code string}.
     */
    private static int attrValueTypeOf(String rawValue) {
        if (rawValue == null || rawValue.isEmpty()) {
            return TYPE_STRING;
        }
        if ("true".equalsIgnoreCase(rawValue) || "false".equalsIgnoreCase(rawValue)) {
            return TYPE_KEYWORD;
        }
        if (NUMERIC_PATTERN.matcher(rawValue).matches()) {
            return TYPE_NUMBER;
        }
        return TYPE_STRING;
    }

    private static int lengthOf(ExpressionAstNode node) {
        if (node.getKind() == ExpressionKind.FUNCTION_CALL && node instanceof ExpressionNode) {
            String fn = ((ExpressionNode) node).getFunctionName();
            return fn == null ? 0 : fn.length();
        }
        String t = node.getText();
        return t == null ? 0 : t.length();
    }

    private static void emitXmlDeclaration(String declaration, String text, int[] lineStarts,
                                           List<int[]> tokens) {
        if (declaration == null || declaration.isEmpty()) {
            return;
        }
        int offset = text.indexOf(declaration);
        if (offset < 0) {
            return;
        }
        int[] lc = offsetToLineCol(lineStarts, offset);
        tokens.add(new int[]{lc[0], lc[1], declaration.length(), TYPE_KEYWORD});
    }

    private static void scanComments(String text, int[] lineStarts, List<int[]> tokens) {
        Matcher m = COMMENT_PATTERN.matcher(text);
        while (m.find()) {
            int start = m.start();
            int[] lc = offsetToLineCol(lineStarts, start);
            tokens.add(new int[]{lc[0], lc[1], m.end() - start, TYPE_COMMENT});
        }
    }

    private static int[] offsetToLineCol(int[] lineStarts, int offset) {
        int line = 0;
        while (line + 1 < lineStarts.length && lineStarts[line + 1] <= offset) {
            line++;
        }
        return new int[]{line, offset - lineStarts[line]};
    }

    private static int[] buildLineStarts(String text) {
        int count = 1;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                count++;
            }
        }
        int[] starts = new int[count];
        int idx = 1;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                starts[idx++] = i + 1;
            }
        }
        return starts;
    }

    private static List<Integer> deltaEncode(List<int[]> tokens) {
        List<Integer> data = new ArrayList<>(tokens.size() * 5);
        int prevLine = 0;
        int prevCol = 0;
        for (int[] t : tokens) {
            int deltaLine = t[0] - prevLine;
            int deltaStart = (deltaLine == 0) ? t[1] - prevCol : t[1];
            data.add(deltaLine);
            data.add(deltaStart);
            data.add(t[2]); // length
            data.add(t[3]); // type
            data.add(0);    // no modifiers
            prevLine = t[0];
            prevCol = t[1];
        }
        return data;
    }
}
