package com.huawei.theme.analysis.core.syntaxanalysis;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.huawei.theme.analysis.core.rulelibrary.JsonRuleLoader;
import com.huawei.theme.analysis.core.rulelibrary.RuleRepository;
import com.huawei.theme.analysis.core.shared.ast.DslFileNode;
import com.huawei.theme.analysis.core.shared.diagnostic.Diagnostic;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExpressionSyntaxCheckerNoSemTest {

    private final RuleRepository ruleRepository = loadRules();
    private final DslAstProvider astProvider = new AstBuilder(ruleRepository);
    private final ExpressionSyntaxChecker checker = new ExpressionSyntaxChecker(ruleRepository);

    private static RuleRepository loadRules() {
        String dir = System.getProperty("user.dir") + "/src/main/resources/rules";
        return new JsonRuleLoader().loadFromDirectory(dir);
    }

    private List<Diagnostic> check(String xml) {
        DslFileNode ast = astProvider.getDslAst("test.xml", xml);
        return checker.check("test.xml", ast);
    }

    private static boolean hasSemRuleId(List<Diagnostic> diags) {
        return diags.stream().anyMatch(d -> d.getRuleId() != null && d.getRuleId().startsWith("SEM-"));
    }

    // Regression: ExpressionSyntaxChecker must not produce any SEM-* diagnostics.
    // Previously a number-typed attribute with a string literal ('hello') produced
    // SEM-TYPE-003; that branch is removed so the case falls through to SYN-EXPR-ANTLR.
    @Test
    void noSemType003ForStringLiteralInNumberContext() {
        List<Diagnostic> diags = check("<Image x=\"'hello'\"/>");
        assertTrue(diags.stream().noneMatch(d -> "SEM-TYPE-003".equals(d.getRuleId())),
                "must not produce SEM-TYPE-003: " + diags);
        assertFalse(hasSemRuleId(diags),
                "must not produce any SEM-* ruleId: " + diags);
    }
}
