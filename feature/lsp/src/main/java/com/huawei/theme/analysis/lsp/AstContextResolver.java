package com.huawei.theme.analysis.lsp;

import com.huawei.theme.analysis.core.expression.ExpressionNode;
import com.huawei.theme.analysis.core.shared.ast.DslAttributeNode;
import com.huawei.theme.analysis.core.shared.ast.DslAttributeValueNode;
import com.huawei.theme.analysis.core.shared.ast.DslElementNode;
import com.huawei.theme.analysis.core.shared.ast.DslFileNode;
import com.huawei.theme.analysis.core.shared.ast.ExpressionAstNode;
import com.huawei.theme.analysis.core.shared.ast.ExpressionKind;

/**
 * AST-based cursor context resolver. Determines the cursor's structural
 * context (element-name position vs attribute-name position vs other) by
 * walking the parsed AST and matching node ranges, replacing the text-scan
 * heuristic of {@link ContextResolver} for the precise case.
 *
 * <p>AST node ranges come from {@link com.huawei.theme.analysis.core.syntaxanalysis.AstBuilder}:
 * <ul>
 *   <li>{@link DslElementNode} covers the entire start tag
 *       ({@code <Tag attr="v">}), {@code line/column} pointing at {@code <},
 *       {@code endLine/endColumn} just past {@code >};</li>
 *   <li>{@link DslAttributeNode} covers {@code name="v"} (name start to past
 *       the closing quote);</li>
 *   <li>{@link com.huawei.theme.analysis.core.shared.ast.DslAttributeValueNode}
 *       covers the value content.</li>
 * </ul>
 * Because an element's range spans only its start tag, parent and child start
 * tags are disjoint in document order, so the deepest element whose range
 * contains the cursor is well-defined.</p>
 *
 * <p>Returns {@code null} when the AST is unavailable, malformed (error node
 * with a zero-width range), or the cursor falls outside any start tag (e.g.
 * XML declaration, closing tags, text content) so the caller can fall back to
 * {@link ContextResolver}.</p>
 */
final class AstContextResolver {

    private final String text;
    private final PositionMapper mapper;

    AstContextResolver(String text) {
        this.text = text;
        this.mapper = new PositionMapper(text);
    }

    /**
     * Resolves the cursor context from the AST. Returns {@code null} to
     * signal the caller should fall back to the text heuristic.
     */
    ContextResolver.Context resolve(int offset, DslFileNode ast) {
        if (ast == null || ast.getRootElement() == null) {
            return null;
        }
        int n = Math.min(Math.max(offset, 0), text.length());
        Result r = new Result();
        walk(ast.getRootElement(), n, r);
        if (r.element == null) {
            return null;
        }
        return buildContext(r.element, r.attribute, r.attributeValue, n);
    }

    private ContextResolver.Context buildContext(DslElementNode element,
                                                DslAttributeNode attribute,
                                                DslAttributeValueNode attributeValue, int cursor) {
        String tagName = element.getTagName() != null ? element.getTagName() : "";
        int elemStart = mapper.coreOffset(element.getLine(), element.getColumn());
        int tagNameStart = elemStart + 1;
        int tagNameEnd = tagNameStart + tagName.length();

        if (cursor >= elemStart && cursor < tagNameEnd) {
            String word = safeSubstring(tagNameStart, cursor);
            return new ContextResolver.Context(
                    ContextResolver.PositionType.ELEMENT_NAME,
                    tagName.isEmpty() ? null : tagName,
                    word);
        }

        if (attribute != null) {
            String attrName = attribute.getName() != null ? attribute.getName() : "";
            int nameStart = mapper.coreOffset(attribute.getLine(), attribute.getColumn());
            int nameEnd = nameStart + attrName.length();
            if (cursor >= nameStart && cursor < nameEnd) {
                String word = safeSubstring(nameStart, cursor);
                return new ContextResolver.Context(
                        ContextResolver.PositionType.ATTRIBUTE_NAME,
                        tagName.isEmpty() ? null : tagName,
                        word,
                        attrName);
            }
            // Inside the attribute but past its name: distinguish the value
            // content (between quotes) from the '=' / quote characters, so the
            // completion provider can offer enum values for the attribute.
            if (attributeValue != null) {
                int valueStart = mapper.coreOffset(attributeValue.getLine(), attributeValue.getColumn());
                int valueEnd = mapper.coreOffset(attributeValue.getEndLine(), attributeValue.getEndColumn());
                if (cursor >= valueStart && cursor <= valueEnd) {
                    String word = safeSubstring(valueStart, cursor);
                    // Resolve the individual expression token at cursor for
                    // per-token hover (variable ref / function / literal).
                    // Uses text scanning (not AST traversal) for robustness —
                    // expression node ranges from the parser can be point ranges
                    // that don't reliably contain the cursor.
                    ExpressionAstNode exprNode = null;
                    if (attributeValue.getExpression().isPresent()) {
                        String exprText = attributeValue.getRawValue();
                        int offsetInExpr = cursor - valueStart;
                        exprNode = findExprTokenByText(exprText, offsetInExpr);
                    }
                    return new ContextResolver.Context(
                            ContextResolver.PositionType.ATTRIBUTE_VALUE,
                            tagName.isEmpty() ? null : tagName,
                            word,
                            attrName,
                            exprNode,
                            element);
                }
            }
            return new ContextResolver.Context(
                    ContextResolver.PositionType.OTHER,
                    tagName.isEmpty() ? null : tagName,
                    null,
                    attrName);
        }

        // cursor in the element's start tag but past the tag name and not in
        // any attribute: the user is on whitespace between attributes or
        // right after the tag name, about to type an attribute. Default to
        // ATTRIBUTE_NAME (empty word) so completion offers the canonical
        // attribute set. Skip on tag-boundary chars ('>', '/', '<') where
        // attribute completion would be wrong.
        int len = text.length();
        char atCursor = (cursor < len) ? text.charAt(cursor) : ' ';
        if (atCursor == '>' || atCursor == '/' || atCursor == '<') {
            return new ContextResolver.Context(
                    ContextResolver.PositionType.OTHER,
                    tagName.isEmpty() ? null : tagName,
                    null);
        }
        char prev = (cursor > 0) ? text.charAt(cursor - 1) : ' ';
        if (prev == '=') {
            return new ContextResolver.Context(
                    ContextResolver.PositionType.OTHER,
                    tagName.isEmpty() ? null : tagName,
                    null);
        }
        return new ContextResolver.Context(
                ContextResolver.PositionType.ATTRIBUTE_NAME,
                tagName.isEmpty() ? null : tagName,
                "");
    }

    /**
     * Walks the tree to find the element whose start-tag range contains the
     * cursor. An element's range spans only its own start tag, so a child's
     * start tag lies entirely outside its parent's range — pruning by parent
     * range would skip valid children. Therefore the walk recurses into
     * children whenever the cursor is NOT in the current element's range,
     * and stops recursing once a matching element is found (sibling start tags
     * are disjoint, so at most one branch can match).
     */
    private void walk(DslElementNode element, int cursor, Result r) {
        if (element == null) {
            return;
        }
        int start = mapper.coreOffset(element.getLine(), element.getColumn());
        int end = mapper.coreOffset(element.getEndLine(), element.getEndColumn());
        if (cursor >= start && cursor < end) {
            r.element = element;
            r.attribute = null;
            r.attributeValue = null;
            if (element.getAttributes() != null) {
                for (DslAttributeNode attr : element.getAttributes()) {
                    int as = mapper.coreOffset(attr.getLine(), attr.getColumn());
                    int ae = mapper.coreOffset(attr.getEndLine(), attr.getEndColumn());
                    if (cursor >= as && cursor < ae) {
                        r.attribute = attr;
                        DslAttributeValueNode value = attr.getValue();
                        if (value != null) {
                            int vs = mapper.coreOffset(value.getLine(), value.getColumn());
                            int ve = mapper.coreOffset(value.getEndLine(), value.getEndColumn());
                            // value range is half-open [vs, ve); also accept the
                            // empty-value cursor-at-start case (attr="").
                            if (cursor >= vs && cursor <= ve) {
                                r.attributeValue = value;
                            }
                        }
                        break;
                    }
                }
            }
            // Child start tags are outside this element's start tag, so they
            // cannot also contain the cursor — no need to recurse further.
            return;
        }
        if (element.getChildElements() != null) {
            for (DslElementNode child : element.getChildElements()) {
                walk(child, cursor, r);
            }
        }
    }

    private String safeSubstring(int start, int end) {
        if (start < 0) {
            start = 0;
        }
        if (end < start) {
            end = start;
        }
        if (end > text.length()) {
            end = text.length();
        }
        return text.substring(start, end);
    }

    private static final class Result {
        DslElementNode element;
        DslAttributeNode attribute;
        DslAttributeValueNode attributeValue;
    }

    // ---- Expression token resolution for per-token hover ----

    private static final java.util.regex.Pattern EXPR_TOKEN_PATTERN =
            java.util.regex.Pattern.compile("[#@][A-Za-z_][\\w.]*|[A-Za-z_]\\w*(?=\\()|'[^']*'|-?\\d+\\.?\\d*");

    /**
     * Scans the expression text for the token at the given offset and returns
     * a minimal {@link ExpressionNode} with the right kind / variableName /
     * functionName / text for hover rendering. Uses text patterns instead of
     * AST node ranges (which can be point ranges from the parser that don't
     * reliably contain the cursor).
     */
    private static ExpressionAstNode findExprTokenByText(String exprText, int offsetInExpr) {
        if (exprText == null || offsetInExpr < 0 || offsetInExpr >= exprText.length()) {
            return null;
        }
        java.util.regex.Matcher m = EXPR_TOKEN_PATTERN.matcher(exprText);
        while (m.find()) {
            if (offsetInExpr >= m.start() && offsetInExpr < m.end()) {
                String token = m.group();
                if (token.charAt(0) == '#' || token.charAt(0) == '@') {
                    // Variable reference: #var or @var
                    String prefix = String.valueOf(token.charAt(0));
                    String varName = token.substring(1);
                    return ExpressionNode.variableRef(prefix, varName, token, 1, m.start());
                } else if (token.startsWith("'")) {
                    // String literal
                    return ExpressionNode.literal(token, token, 1, m.start());
                } else if (Character.isDigit(token.charAt(0))
                        || (token.length() > 1 && token.charAt(0) == '-'
                                && Character.isDigit(token.charAt(1)))) {
                    // Number literal (e.g. 123, -0.5)
                    return ExpressionNode.literal(token, token, 1, m.start());
                } else {
                    // Function call: funcName( — token is just the name (lookahead matched '(')
                    return ExpressionNode.functionCall(token, java.util.Collections.emptyList(), token, 1, m.start());
                }
            }
        }
        // Also check for bare numbers (no minus sign in the pattern above for positive numbers)
        java.util.regex.Pattern numPattern = java.util.regex.Pattern.compile("\\d+\\.?\\d*");
        java.util.regex.Matcher nm = numPattern.matcher(exprText);
        while (nm.find()) {
            if (offsetInExpr >= nm.start() && offsetInExpr < nm.end()) {
                return ExpressionNode.literal(nm.group(), nm.group(), 1, nm.start());
            }
        }
        return null;
    }
}
