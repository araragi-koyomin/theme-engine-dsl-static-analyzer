package com.huawei.theme.analysis.plugin.editor.themedsl;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.psi.XmlElementFactory;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlTokenType;
import com.huawei.theme.analysis.plugin.rule.RuleRepositoryService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 元素标签名补全：在ThemeDSL文件的XML标签名位置贡献内置规则库定义的全部元素标签名。
 *
 * <p>每个补全项关联一个通过{@link XmlElementFactory#createTagFromText}创建的临时{@link XmlTag}，
 * 使文档提供者能在补全弹窗中显示标签描述。创建结果缓存以避免重复解析。</p>
 */
public class ThemeDslElementNameCompletionContributor extends CompletionContributor {

    private static final Logger LOG = Logger.getInstance(ThemeDslElementNameCompletionContributor.class);

    /** Cache of tagName → dummy XmlTag for documentation. */
    private static final Map<String, XmlTag> DOC_TAG_CACHE = new ConcurrentHashMap<>();

    @Override
    public void fillCompletionVariants(@NotNull CompletionParameters parameters, @NotNull CompletionResultSet result) {
        PsiElement position = parameters.getPosition();
        ASTNode node = position.getNode();
        if (node == null || node.getElementType() != XmlTokenType.XML_NAME) {
            return;
        }
        if (!(position.getParent() instanceof XmlTag)) {
            return;
        }
        LOG.info("ThemeDSL element-name completion invoked at offset " + parameters.getOffset());
        for (String name : RuleRepositoryService.getInstance().getRuleRepository().getAllElementNames()) {
            XmlTag dummyTag = getDocTag(position.getProject(), name);
            result.addElement(LookupElementBuilder.create(dummyTag, name)
                    .withTypeText("ThemeDSL Tag")
                    .withIcon(AllIcons.Nodes.Tag));
        }
    }

    /**
     * Returns a cached dummy {@link XmlTag} for documentation purposes.
     */
    @Nullable
    private static XmlTag getDocTag(@NotNull com.intellij.openapi.project.Project project, @NotNull String name) {
        XmlTag cached = DOC_TAG_CACHE.get(name);
        if (cached != null && cached.isValid()) {
            return cached;
        }
        try {
            XmlTag tag = XmlElementFactory.getInstance(project).createTagFromText("<" + name + "/>");
            DOC_TAG_CACHE.put(name, tag);
            return tag;
        } catch (Exception ignored) {
        }
        return null;
    }
}
