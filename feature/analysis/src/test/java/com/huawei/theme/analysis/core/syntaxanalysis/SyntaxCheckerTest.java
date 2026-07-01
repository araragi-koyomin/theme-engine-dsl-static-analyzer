package com.huawei.theme.analysis.core.syntaxanalysis;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.huawei.theme.analysis.core.rulelibrary.JsonRuleLoader;
import com.huawei.theme.analysis.core.rulelibrary.RuleRepository;
import com.huawei.theme.analysis.core.shared.ast.DslFileNode;
import com.huawei.theme.analysis.core.shared.diagnostic.Diagnostic;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SyntaxCheckerTest {

    private final RuleRepository ruleRepository = loadRules();
    private final DslAstProvider astProvider = new AstBuilder(ruleRepository);
    private final SyntaxChecker checker = new SyntaxChecker(ruleRepository);

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

    // SYN-001 root element wrong
    @Test
    void syn001_rootNotRootElement() {
        assertTrue(hasRuleId(check("<Var name=\"x\"/>"), "SYN-001"));
    }

    @Test
    void syn001_validRootNoDiagnostic() {
        assertFalse(hasRuleId(check("<Lockscreen/>"), "SYN-001"));
    }

    // SYN-002 nesting violation
    @Test
    void syn002_childNotAllowedUnderParent() {
        assertTrue(hasRuleId(check("<Lockscreen><Var name=\"x\"><Image/></Var></Lockscreen>"), "SYN-002"));
    }

    @Test
    void syn002_validNestingNoDiagnostic() {
        assertFalse(hasRuleId(check("<Lockscreen><Var name=\"x\"/></Lockscreen>"), "SYN-002"));
    }

    // SYN-003 unknown element
    @Test
    void syn003_unknownElementTag() {
        assertTrue(hasRuleId(check("<Lockscreen><UnknownTag/></Lockscreen>"), "SYN-003"));
    }

    @Test
    void syn003_knownElementNoDiagnostic() {
        assertFalse(hasRuleId(check("<Lockscreen><Var name=\"x\"/></Lockscreen>"), "SYN-003"));
    }

    // SYN-004 unknown attribute
    @Test
    void syn004_unknownAttribute() {
        assertTrue(hasRuleId(check("<Lockscreen><Image name=\"x\" bogusAttr=\"1\"/></Lockscreen>"), "SYN-004"));
    }

    @Test
    void syn004_knownAttributeNoDiagnostic() {
        assertFalse(hasRuleId(check("<Lockscreen><Image name=\"x\" src=\"a.png\"/></Lockscreen>"), "SYN-004"));
    }

    // SYN-005 missing required attribute
    @Test
    void syn005_missingRequiredAttr() {
        assertTrue(hasRuleId(check("<Lockscreen><Var/></Lockscreen>"), "SYN-005"));
    }

    @Test
    void syn005_requiredAttrPresentNoDiagnostic() {
        assertFalse(hasRuleId(check("<Lockscreen><Var name=\"x\"/></Lockscreen>"), "SYN-005"));
    }

    // SYN-006 literal type error
    @Test
    void syn006_numberAttrNonNumericValue() {
        assertTrue(hasRuleId(check("<Lockscreen frameRate=\"abc\"/>"), "SYN-006"));
    }

    @Test
    void syn006_numberAttrNumericValueNoDiagnostic() {
        assertFalse(hasRuleId(check("<Lockscreen frameRate=\"60\"/>"), "SYN-006"));
    }

    // SYN-007 enum value error
    @Test
    void syn007_enumAttrInvalidValue() {
        assertTrue(hasRuleId(check("<Lockscreen><Image scaleType=\"invalid\"/></Lockscreen>"), "SYN-007"));
    }

    @Test
    void syn007_enumAttrValidValueNoDiagnostic() {
        assertFalse(hasRuleId(check("<Lockscreen><Image scaleType=\"fill\"/></Lockscreen>"), "SYN-007"));
    }

    // XML format error returns empty
    @Test
    void xmlFormatErrorReturnsEmpty() {
        assertTrue(check("<Lockscreen><Image></Lockscreen>").isEmpty());
    }
}
