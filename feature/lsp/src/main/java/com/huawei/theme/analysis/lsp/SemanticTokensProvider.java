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
 * call / literal is emitted as a token. Token positions use the document
 * absolute coordinates that {@code AstBuilder} already offset onto the
 * expression nodes (1-based line / 0-based column, open-ended end). Binary /
 * unary operator nodes are skipped because the operator position is not
 * recorded separately on the AST.</p>
 *
 * <p>Output is the LSP relative-encoding integer list
 * {@code [deltaLine, deltaStart, length, tokenType, tokenModifier]}.</p>
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
        List<Integer> data = new ArrayList<>();
        if (ast == null || ast.getRootElement() == null) {
            return data;
        }
        int[] prev = {0, 0}; // prevLine(0-based), prevChar(0-based)
        walk(ast.getRootElement(), data, prev);
        return data;
    }

    private void walk(DslElementNode element, List<Integer> data, int[] prev) {
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
                        emit((ExpressionNode) expr, data, prev);
                    }
                }
            }
        }
        List<DslElementNode> children = element.getChildElements();
        if (children != null) {
            for (DslElementNode child : children) {
                walk(child, data, prev);
            }
        }
    }

    private void emit(ExpressionNode node, List<Integer> data, int[] prev) {
        int type = tokenTypeOf(node);
        if (type >= 0) {
            int line = node.getLine() - 1; // 1-based -> 0-based
            int col = node.getColumn();
            int length = lengthOf(node);
            if (line >= 0 && length > 0) {
                int deltaLine = line - prev[0];
                int deltaStart = (deltaLine == 0) ? col - prev[1] : col;
                data.add(deltaLine);
                data.add(deltaStart);
                data.add(length);
                data.add(type);
                data.add(0); // no modifiers
                prev[0] = line;
                prev[1] = col;
            }
        }
        if (node.getChildren() != null) {
            for (ExpressionNode c : node.getChildren()) {
                emit(c, data, prev);
            }
        }
        if (node.getIndexExpression() != null) {
            emit(node.getIndexExpression(), data, prev);
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
