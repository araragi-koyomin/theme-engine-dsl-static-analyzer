package com.huawei.theme.analysis.core.syntaxanalysis;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.huawei.theme.analysis.core.rulelibrary.JsonRuleLoader;
import com.huawei.theme.analysis.core.rulelibrary.RuleRepository;
import com.huawei.theme.analysis.core.shared.ast.DslFileNode;
import com.huawei.theme.analysis.core.shared.diagnostic.Diagnostic;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExpressionSyntaxCheckerTest {

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

    private static boolean hasRuleId(List<Diagnostic> diags, String ruleId) {
        return diags.stream().anyMatch(d -> ruleId.equals(d.getRuleId()));
    }

    // SYN-EXPR-001 -#var
    @Test
    void expr001_unaryMinusOnHashVar() {
        assertTrue(hasRuleId(check("<Image x=\"-#w\"/>"), "SYN-EXPR-001"));
    }

    @Test
    void expr001_unaryMinusOnNumberNoDiagnostic() {
        assertFalse(hasRuleId(check("<Image x=\"-1*#w\"/>"), "SYN-EXPR-001"));
    }

    // SYN-EXPR-002 precision > 7 digits
    @Test
    void expr002_numberLiteralOverSevenDigits() {
        assertTrue(hasRuleId(check("<Image x=\"12345678*#w\"/>"), "SYN-EXPR-002"));
    }

    @Test
    void expr002_numberLiteralSevenDigitsNoDiagnostic() {
        assertFalse(hasRuleId(check("<Image x=\"1234567*#w\"/>"), "SYN-EXPR-002"));
    }

    // SYN-EXPR-003 string starts with #
    @Test
    void expr003_stringStartsWithHashAndOperator() {
        assertTrue(hasRuleId(check("<Text color=\"#num*10\"/>"), "SYN-EXPR-003"));
    }

    @Test
    void expr003_stringNotStartsWithHashNoDiagnostic() {
        assertFalse(hasRuleId(check("<Text color=\"10*#num\"/>"), "SYN-EXPR-003"));
    }

    // SYN-EXPR-004 missing single quotes
    @Test
    void expr004_bareWordInConcat() {
        assertTrue(hasRuleId(check("<Text textExp=\"hello+@var\"/>"), "SYN-EXPR-004"));
    }

    @Test
    void expr004_quotedStringNoDiagnostic() {
        assertFalse(hasRuleId(check("<Text textExp=\"'hello'+@var\"/>"), "SYN-EXPR-004"));
    }

    // SYN-EXPR-005 missing braces
    @Test
    void expr005_numericInConcatWithoutBraces() {
        assertTrue(hasRuleId(check("<Text textExp=\"'x'+10*#num\"/>"), "SYN-EXPR-005"));
    }

    @Test
    void expr005_numericInConcatWithBracesNoDiagnostic() {
        assertFalse(hasRuleId(check("<Text textExp=\"'x'+{10*#num}\"/>"), "SYN-EXPR-005"));
    }

    // SYN-EXPR-006 preciseeval suffix
    @Test
    void expr006_preciseevalFollowedByOperator() {
        assertTrue(hasRuleId(check("<Image x=\"preciseeval(#x)+1\"/>"), "SYN-EXPR-006"));
    }

    @Test
    void expr006_preciseevalAloneNoDiagnostic() {
        assertFalse(hasRuleId(check("<Image x=\"preciseeval(#x)\"/>"), "SYN-EXPR-006"));
    }

    // SYN-EXPR-ANTLR generic parse error
    @Test
    void exprAntlr_incompleteExpression() {
        assertTrue(hasRuleId(check("<Image x=\"#x+\"/>"), "SYN-EXPR-ANTLR"));
    }

    @Test
    void exprAntlr_validExpressionNoDiagnostic() {
        assertFalse(hasRuleId(check("<Image x=\"#x+1\"/>"), "SYN-EXPR-ANTLR"));
    }
}
