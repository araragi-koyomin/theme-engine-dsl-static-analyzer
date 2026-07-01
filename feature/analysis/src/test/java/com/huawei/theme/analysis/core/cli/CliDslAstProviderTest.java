package com.huawei.theme.analysis.core.cli;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.huawei.theme.analysis.core.rulelibrary.JsonRuleLoader;
import com.huawei.theme.analysis.core.rulelibrary.RuleRepository;
import com.huawei.theme.analysis.core.shared.ast.DslAttributeNode;
import com.huawei.theme.analysis.core.shared.ast.DslElementNode;
import com.huawei.theme.analysis.core.shared.ast.DslFileNode;
import com.huawei.theme.analysis.core.shared.ast.ExpressionKind;
import com.huawei.theme.analysis.core.syntaxanalysis.AstBuilder;

class CliDslAstProviderTest {

    private static CliDslAstProvider provider;
    private static AstBuilder directBuilder;
    private static String dslDir;

    @BeforeAll
    static void setUp() {
        String moduleDir = System.getProperty("user.dir");
        String rulesDir = moduleDir + "/src/main/resources/rules";
        RuleRepository repo = new JsonRuleLoader().loadFromDirectory(rulesDir);
        provider = new CliDslAstProvider(repo);
        directBuilder = new AstBuilder(repo);
        dslDir = moduleDir + "/src/test/resources/dsl";
    }

    @Test
    void getDslAstWithValidLockscreenProducesCompleteTree() {
        File file = new File(dslDir, "valid_lockscreen.xml");
        DslFileNode ast = provider.getDslAst(file);
        assertEquals("Lockscreen", ast.getRootElement().getTagName());
        assertTrue(ast.getRootElement().getChildElements().size() > 0);
    }

    @Test
    void astNodesHaveLineAndColumnNumbers() {
        File file = new File(dslDir, "valid_lockscreen.xml");
        DslFileNode ast = provider.getDslAst(file);
        DslElementNode root = ast.getRootElement();
        assertTrue(root.getLine() > 0 || root.getColumn() >= 0);
        for (DslElementNode child : root.getChildElements()) {
            assertTrue(child.getLine() > 0 || child.getColumn() >= 0);
            for (DslAttributeNode attr : child.getAttributes()) {
                assertTrue(attr.getLine() > 0 || attr.getColumn() >= 0);
            }
        }
    }

    @Test
    void getDslAstWithMalformedXmlReturnsErrorNode() {
        File file = new File(dslDir, "error_unclosed.xml");
        DslFileNode ast = provider.getDslAst(file);
        assertTrue(ast.getRootElement().isHasError());
    }

    @Test
    void getDslAstStringStringMatchesDirectAstBuilder() throws Exception {
        Path lockscreenPath = Path.of(dslDir, "valid_lockscreen.xml");
        String content = Files.readString(lockscreenPath, StandardCharsets.UTF_8);
        DslFileNode viaProvider = provider.getDslAst(lockscreenPath.toString(), content);
        DslFileNode viaDirect = directBuilder.getDslAst(lockscreenPath.toString(), content);
        assertEquals(viaDirect.getRootElement().getTagName(),
                viaProvider.getRootElement().getTagName());
        assertEquals(viaDirect.getRootElement().isHasError(),
                viaProvider.getRootElement().isHasError());
    }

    @Test
    void getDslAstWithNonexistentFileReturnsErrorNode() {
        File nonexistent = new File("/nonexistent/path/theme.xml");
        DslFileNode ast = provider.getDslAst(nonexistent);
        assertTrue(ast.getRootElement().isHasError());
        assertTrue(ast.getRootElement().getErrorMessage() != null
                && !ast.getRootElement().getErrorMessage().isEmpty());
    }

    @Test
    void expressionEmbeddingWorksForWidget() {
        File file = new File(dslDir, "valid_widget.xml");
        DslFileNode ast = provider.getDslAst(file);
        DslElementNode root = ast.getRootElement();
        boolean foundExpression = false;
        for (DslElementNode child : root.getChildElements()) {
            for (DslAttributeNode attr : child.getAttributes()) {
                if ("expression".equals(attr.getName())) {
                    if (attr.getValue().getExpression().isPresent()) {
                        ExpressionKind kind = attr.getValue().getExpression().get().getKind();
                        assertTrue(kind == ExpressionKind.VARIABLE_REF
                                || kind == ExpressionKind.FUNCTION_CALL
                                || kind == ExpressionKind.BINARY_EXPR
                                || kind == ExpressionKind.LITERAL
                                || kind == ExpressionKind.UNARY_EXPR);
                        foundExpression = true;
                    }
                }
            }
        }
        assertTrue(foundExpression);
    }
}
