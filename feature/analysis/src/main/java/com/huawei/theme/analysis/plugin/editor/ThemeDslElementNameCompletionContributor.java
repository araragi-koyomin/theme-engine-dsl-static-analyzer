package com.huawei.theme.analysis.plugin.editor;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlTokenType;
import com.huawei.theme.analysis.plugin.rule.RuleRepositoryService;
import org.jetbrains.annotations.NotNull;

/**
 * 元素标签名补全：在ThemeDSL文件的XML标签名位置贡献内置规则库定义的全部元素标签名
 * （来自{@link com.huawei.theme.analysis.core.rulelibrary.RuleRepository#getAllElementNames()}）。
 *
 * <p>标准XmlLexer将标签名与属性名都词法为{@link XmlTokenType#XML_NAME}，
 * 故需通过parent是否为{@link XmlTag}区分标签名位置，避免在属性名处误补全。
 * 参考IntelliJ内置{@code XmlCompletionContributor#completeTagName}的同款判定。</p>
 */
public class ThemeDslElementNameCompletionContributor extends CompletionContributor {

    private static final Logger LOG = Logger.getInstance(ThemeDslElementNameCompletionContributor.class);

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
            result.addElement(LookupElementBuilder.create(name).withTypeText("ThemeDSL Tag"));
        }
    }
}
