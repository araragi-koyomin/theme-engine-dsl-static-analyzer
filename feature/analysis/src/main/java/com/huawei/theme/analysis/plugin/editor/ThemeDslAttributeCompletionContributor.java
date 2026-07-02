package com.huawei.theme.analysis.plugin.editor;

import java.util.Optional;

import com.huawei.theme.analysis.core.rulelibrary.model.AttrTypeSpec;
import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlTokenType;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import com.huawei.theme.analysis.core.rulelibrary.RuleRepository;
import com.huawei.theme.analysis.core.rulelibrary.model.DslElementRule;
import com.huawei.theme.analysis.plugin.rule.RuleRepositoryService;

import javax.swing.*;

/**
 * 属性名补全：在ThemeDSL标签的属性名位置（{@link XmlTokenType#XML_NAME}且parent为{@link XmlAttribute}）
 * 贡献当前所属标签规则条目（{@link RuleRepository#getElementRule(String)}）定义的全部规范属性名。
 *
 * <p>标准XmlLexer将标签名与属性名均词法为XML_NAME，故通过parent是否为{@link XmlAttribute}区分属性名位置，
 * 再通过最近{@link XmlTag}祖先取标签名查询规则。仅补全规范属性名（attrTypes的key），不含别名/枚举值。</p>
 */
public class ThemeDslAttributeCompletionContributor extends CompletionContributor {

    private static final Logger LOG = Logger.getInstance(ThemeDslAttributeCompletionContributor.class);

    @Override
    public void fillCompletionVariants(@NotNull CompletionParameters parameters, @NotNull CompletionResultSet result) {
        PsiElement position = parameters.getPosition();
        ASTNode node = position.getNode();
        if (node == null || node.getElementType() != XmlTokenType.XML_NAME) {
            return;
        }
        if (!(position.getParent() instanceof XmlAttribute)) {
            return;
        }
        XmlTag tag = PsiTreeUtil.getParentOfType(position, XmlTag.class);
        if (tag == null) {
            return;
        }
        RuleRepository repo = RuleRepositoryService.getInstance().getRuleRepository();
        Optional<DslElementRule> ruleOpt = repo.getElementRule(tag.getName());
        if (ruleOpt.isEmpty()) {
            return;
        }
        LOG.info("ThemeDSL attribute completion for tag <" + tag.getName() + ">");
        for(var entry : ruleOpt.get().getAttrTypes().entrySet()){

            var type = getTypeHint(entry.getValue());

            LookupElementBuilder element = LookupElementBuilder.create(entry.getKey()).withTypeText(type).withIcon(getIcon(type));
            result.addElement(element);
        }
    }

    private Icon getIcon(String type){
        if(type.contains("Expression")){
            return AllIcons.Nodes.ClassInitializer;
        }else{
            return AllIcons.Nodes.Parameter;
        }
    }
    private String getTypeHint(AttrTypeSpec typeSpec){
        if(typeSpec.isSupportsExpression()){
            return StringUtils.capitalize(typeSpec.getExpressionKind()+" Expression");
        }
        return StringUtils.capitalize(typeSpec.getType());
    }
}
