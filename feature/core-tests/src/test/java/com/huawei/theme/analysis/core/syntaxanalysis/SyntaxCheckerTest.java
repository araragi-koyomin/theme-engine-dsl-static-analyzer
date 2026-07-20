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

    // XML format error must surface a diagnostic (not silently swallowed)
    @Test
    @org.junit.jupiter.api.Disabled("blocked by FIX006: SyntaxChecker.java:33-36 early-returns when "
            + "root.isHasError(), silently swallowing malformed-XML errors that AstBuilder stored in "
            + "root.getErrorMessage(). Correct behavior: emit a SYN-002 diagnostic with the XML parse "
            + "error message so the user knows the file failed to parse. Audit C14 — theater-masks-real-bug, "
            + "flagged 待确认设计意图 by audit; needs PHASE 1 to confirm SYN-002 rule + message format.")
    void xmlFormatErrorReturnsEmpty() {
        // FIX004 C14: original assertion assertTrue(isEmpty()) encoded the
        // silent-swallow behavior as correct. Correct behavior: malformed XML
        // (unclosed <Image>) must produce a diagnostic, not be silently dropped.
        List<Diagnostic> diags = check("<Lockscreen><Image></Lockscreen>");
        assertFalse(diags.isEmpty(),
                "malformed XML must produce a diagnostic; got empty list (silent swallow). "
                        + "AstBuilder stored the XMLStreamException message in root.getErrorMessage(); "
                        + "SyntaxChecker should surface it as SYN-002.");
        assertTrue(hasRuleId(diags, "SYN-002"),
                "malformed XML should produce SYN-002; got: " + diags);
    }
}
