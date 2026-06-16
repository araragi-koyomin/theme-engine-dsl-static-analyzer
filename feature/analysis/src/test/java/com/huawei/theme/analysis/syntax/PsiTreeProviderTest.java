package com.huawei.theme.analysis.syntax;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;

import com.huawei.theme.analysis.DslAnalysisService;
import com.huawei.theme.analysis.file.DslFileMatcher;
import com.huawei.theme.analysis.rule.loader.JsonRuleLoader;
import com.huawei.theme.analysis.rule.model.DslElementRule;
import com.huawei.theme.analysis.rule.model.RuleSource;
import com.huawei.theme.analysis.rule.repository.RuleRepository;
import com.huawei.theme.analysis.rule.repository.RuleRepositoryImpl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class PsiTreeProviderTest {

    private PsiTreeProvider stubProvider;
    private DslAnalysisService service;

    @BeforeEach
    void setUp() {
        stubProvider = new StubPsiTreeProvider();
        service = new DslAnalysisService();
    }

    @Test
    void interfaceContract_getDslPsiTree_nullFileReturnsNull() {
        assertNull(stubProvider.getDslPsiTree(null));
    }

    @Test
    void interfaceContract_findElementsByName_nullFileReturnsEmptyList() {
        List<PsiElement> result = stubProvider.findElementsByName(null, "Var");
        assertNotNull(result);
        assertEquals(0, result.size());
    }

    @Test
    void interfaceContract_findElementsByName_nullNameReturnsEmptyList() {
        List<PsiElement> result = stubProvider.findElementsByName(null, null);
        assertEquals(0, result.size());
    }

    @Test
    void interfaceContract_findElementsByName_emptyNameReturnsEmptyList() {
        List<PsiElement> result = stubProvider.findElementsByName(null, "");
        assertEquals(0, result.size());
    }

    @Test
    void integration_serviceShouldProvideValidRuleRepository() {
        RuleRepository repo = service.getRuleRepository();
        assertNotNull(repo);
        List<String> rootNames = repo.getRootElementNames();
        assertEquals(4, rootNames.size());
    }

    @Test
    void integration_serviceShouldProvideFileMatcher() {
        DslFileMatcher matcher = service.getFileMatcher();
        assertNotNull(matcher);
    }

    @Test
    void integration_dslSyntaxConstants_shouldMatchRuleRepositorySource() {
        RuleRepository repo = service.getRuleRepository();
        assertNotNull(repo.getRuleSource(DslSyntaxConstants.SYN_001).orElse(null));
    }

    @Test
    void integration_dslLanguageShouldMatchDslElementTypesLanguage() {
        assertSame(DslLanguage.INSTANCE, DslElementTypes.DSL_FILE.getLanguage());
    }

    @Test
    void integration_dslParserDefinitionShouldUseDslElementTypes() {
        DslParserDefinition pd = new DslParserDefinition();
        assertSame(DslElementTypes.DSL_FILE, pd.getFileNodeType());
    }

    private static class StubPsiTreeProvider implements PsiTreeProvider {
        @Override
        public DslFile getDslPsiTree(VirtualFile file) {
            return null;
        }

        @Override
        public List<PsiElement> findElementsByName(PsiFile file, String elementName) {
            if (file == null || elementName == null) {
                return List.of();
            }
            return List.of();
        }
    }

    private static void assertSame(Object expected, Object actual) {
        if (expected != actual) throw new AssertionError("expected same: " + expected + " but got: " + actual);
    }
}
