package com.huawei.theme.analysis.core.expression;

import java.util.List;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.ErrorNode;

import com.huawei.theme.analysis.core.expression.generated.DslExpressionLexer;
import com.huawei.theme.analysis.core.expression.generated.DslExpressionParser;
import com.huawei.theme.analysis.core.expression.generated.DslExpressionParser.VarNameContext;

import org.antlr.intellij.adaptor.parser.ErrorStrategyAdaptor;
import org.antlr.intellij.adaptor.parser.SyntaxError;
import org.antlr.intellij.adaptor.parser.SyntaxErrorListener;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression test for the adapter bug where a lone {@code @} (at-sign followed
 * by EOF) produced an empty, valid {@code varName} PSI node instead of a
 * "missing {ID, VAR_ID}" error.
 *
 * <p>Root cause: ANTLR's {@code DefaultErrorStrategy.recoverInline} reports a
 * missing-token error and returns a conjured token without throwing, but the
 * generated rule code discards that token, leaving the {@code varName} context
 * with no child node. The PSI converter only surfaces errors that have a
 * terminal/error node, so the error was dropped. The fix in
 * {@link ErrorStrategyAdaptor#recoverInline} attaches an {@link ErrorNode} for
 * conjured tokens.</p>
 */
class DslExpressionAdapterErrorTest {

    @Test
    void loneAtSignReportsMissingVarName() {
        DslExpressionLexer lexer = new DslExpressionLexer(CharStreams.fromString("@"));
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        DslExpressionParser parser = new DslExpressionParser(tokens);
        parser.removeErrorListeners();
        SyntaxErrorListener errorListener = new SyntaxErrorListener();
        parser.addErrorListener(errorListener);
        parser.setErrorHandler(new ErrorStrategyAdaptor());

        ParseTree tree = parser.expression();

        List<SyntaxError> errors = errorListener.getSyntaxErrors();
        assertEquals(1, errors.size(), "expected exactly one syntax error");
        assertTrue(errors.get(0).getMessage().contains("missing {ID, VAR_ID}"),
                "unexpected message: " + errors.get(0).getMessage());

        VarNameContext varName = findFirst(tree, VarNameContext.class);
        assertNotNull(varName, "varName context should be present in the tree");
        assertEquals(1, varName.getChildCount(),
                "varName should carry an error node, not be empty; was: " + tree.toStringTree(parser));
        assertTrue(varName.getChild(0) instanceof ErrorNode,
                "varName child should be an ErrorNode; was: " + varName.getChild(0).getClass().getSimpleName());
    }

    private static <T extends ParseTree> T findFirst(ParseTree root, Class<T> type) {
        if (type.isInstance(root)) {
            return type.cast(root);
        }
        for (int i = 0; i < root.getChildCount(); i++) {
            T found = findFirst(root.getChild(i), type);
            if (found != null) {
                return found;
            }
        }
        return null;
    }
}
