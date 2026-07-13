package com.huawei.theme.analysis.lsp;

import java.util.ArrayList;
import java.util.List;

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
 * Builds LSP {@code SemanticTokens} data for embedded DSL expressions inside
 * attribute values.
 *
 * <p>Walks the AST produced by {@link AstBuilder}: for every attribute whose
 * value was parsed as an expression ({@link DslAttributeValueNode#getExpression()}),
 * the expression node tree is traversed and each variable reference / function
 * call / literal is collected. Tokens are then sorted by document position
 * (line, column) and delta-encoded into the LSP relative format
 * {@code [deltaLine, deltaStart, length, tokenType, tokenModifier]}.</p>
 *
 * <p>Sorting is required because AST traversal order (depth-first, parent
 * before children) does not guarantee document-position order — e.g. a
 * FUNCTION_CALL node starts at the function name but its child VARIABLE_REF
 * starts at an earlier column (the {@code #} prefix). Without sorting, the
 * delta encoding would produce negative offsets, corrupting token ranges on
 * the client.</p>
 */
final class SemanticTokensProvider {

    /** Legend indices must match {@link #tokenTypeOf}. */
    static final List<String> TOKEN_TYPES = List.of("variable", "function", "number", "string");
    static final List<String> TOKEN_MODIFIERS = List.of();

    private final RuleRepository ruleRepository;

    SemanticTokensProvider(RuleRepository ruleRepository) {
        this.ruleRepository = ruleRepository;
    }

    List<Integer> collect(String uri, String text) {
        DslFileNode ast;
        try {
            ast = new AstBuilder(ruleRepository).getDslAst(uri, text);
        } catch (RuntimeException e) {
            return List.of();
        }
        return collect(ast);
    }

    List<Integer> collect(DslFileNode ast) {
        if (ast == null || ast.getRootElement() == null) {
            return List.of();
        }
        // 1. Collect all tokens as {line(0-based), col(0-based), length, type}.
        List<int[]> tokens = new ArrayList<>();
        walk(ast.getRootElement(), tokens);
        if (tokens.isEmpty()) {
            return List.of();
        }
        // 2. Sort by document position (line asc, then col asc) so delta
        //    encoding produces non-negative offsets.
        tokens.sort((a, b) -> a[0] != b[0] ? a[0] - b[0] : a[1] - b[1]);
        // 3. Delta-encode into the LSP flat list.
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

    private void walk(DslElementNode element, List<int[]> tokens) {
        if (element == null) {
            return;
        }
        List<DslAttributeNode> attrs = element.getAttributes();
        if (attrs != null) {
            for (DslAttributeNode attr : attrs) {
                DslAttributeValueNode value = attr.getValue();
                if (value != null && value.getExpression().isPresent()) {
                    ExpressionAstNode expr = value.getExpression().get();
                    if (expr instanceof ExpressionNode) {
                        emit((ExpressionNode) expr, tokens);
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
                return 0; // variable
            case FUNCTION_CALL:
                return 1; // function
            case LITERAL:
                String t = node.getText();
                return (t != null && !t.isEmpty() && t.charAt(0) == '\'') ? 3 : 2; // string : number
            default:
                return -1; // skip BINARY/UNARY/CONDITIONAL/UNKNOWN
        }
    }

    private static int lengthOf(ExpressionAstNode node) {
        if (node.getKind() == ExpressionKind.FUNCTION_CALL && node instanceof ExpressionNode) {
            String fn = ((ExpressionNode) node).getFunctionName();
            return fn == null ? 0 : fn.length();
        }
        String t = node.getText();
        return t == null ? 0 : t.length();
    }
}
