package com.huawei.theme.analysis.file;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.intellij.openapi.vfs.VirtualFile;

import com.intellij.psi.PsiFile;

import com.huawei.theme.analysis.rule.loader.JsonRuleLoader;
import com.huawei.theme.analysis.rule.model.DslElementRule;
import com.huawei.theme.analysis.rule.model.RuleSource;
import com.huawei.theme.analysis.rule.repository.RuleRepository;
import com.huawei.theme.analysis.rule.repository.RuleRepositoryImpl;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DslFileMatcherTest {

    private DslFileMatcher matcher;

    @BeforeEach
    void setUp() {
        JsonRuleLoader loader = new JsonRuleLoader();
        Map<String, DslElementRule> elementMap = loader.buildElementRuleMap("rules/test_rules.json");
        Map<String, RuleSource> sourceMap = loader.buildRuleSourceMap("rules/test_rules.json");
        RuleRepository repository = new RuleRepositoryImpl(elementMap, sourceMap);
        matcher = new DslFileIdentifier(repository);
    }

    @Test
    void matcher_shouldImplementDslFileMatcher() {
        assertNotNull(matcher);
        assertTrue(matcher instanceof DslFileMatcher);
    }

    @Test
    void isDslFile_nullVirtualFile_shouldReturnFalse() {
        assertFalse(matcher.isDslFile((VirtualFile) null));
    }

    @Test
    void isDslFile_nullPsiFile_shouldReturnFalse() {
        assertFalse(matcher.isDslFile((PsiFile) null));
    }
}
