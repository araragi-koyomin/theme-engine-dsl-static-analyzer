package com.huawei.theme.analysis.plugin.editor.themedsl;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.huawei.theme.analysis.core.rulelibrary.model.AttrTypeSpec;
import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.PrioritizedLookupElement;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.psi.XmlElementFactory;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlTokenType;
import org.apache.commons.lang3.StringUtils;

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
 *
 * <p>补全增强：</p>
 * <ul>
 *   <li>必填属性（{@link DslElementRule#getRequiredAttrs()}）加粗并提高优先级，排在选填属性之前；</li>
 *   <li>标签中已存在的属性（含通过别名写入的）不再推荐，避免重复添加。</li>
 * </ul>
 */
public class ThemeDslAttributeCompletionContributor extends CompletionContributor {

    private static final Logger LOG = Logger.getInstance(ThemeDslAttributeCompletionContributor.class);

    private static final double REQUIRED_PRIORITY = 1.0;
    private static final double OPTIONAL_PRIORITY = 0.1;

    /**
     * Cache of "tagName:attrName" → dummy XmlAttribute for documentation.
     * The dummy attribute is created inside a dummy XmlTag with the correct
     * tag name, so the doc provider can look up the AttrTypeSpec.
     */
    private static final Map<String, XmlAttribute> DOC_ATTR_CACHE = new HashMap<>();

    @Override
    public void fillCompletionVariants(@NotNull CompletionParameters parameters, @NotNull CompletionResultSet result) {
        PsiElement position = parameters.getPosition();
        ASTNode node = position.getNode();

        // Case 1: Attribute name position → offer attribute names
        if (node != null && node.getElementType() == XmlTokenType.XML_NAME
                && position.getParent() instanceof XmlAttribute currentAttr) {
            fillAttributeNameCompletion(position, currentAttr, result);
            // Suppress the default XML completion contributor (junk from no schema/DTD).
            result.stopHere();
            return;
        }

        // Case 2: Attribute value position → offer enum values
        if (position.getParent() instanceof XmlAttributeValue valueElement) {
            // Skip expression-supporting attributes — handled by DslExpressionCompletionContributor
            XmlAttribute attr = PsiTreeUtil.getParentOfType(valueElement, XmlAttribute.class);
            XmlTag tag = PsiTreeUtil.getParentOfType(attr, XmlTag.class);
            if (attr != null && tag != null) {
                RuleRepository repo = RuleRepositoryService.getInstance().getRuleRepository();
                Optional<DslElementRule> ruleOpt = repo.getElementRule(tag.getName());
                if (ruleOpt.isPresent()) {
                    AttrTypeSpec spec = findAttrSpec(ruleOpt.get(), attr.getName());
                    if (spec != null && spec.isSupportsExpression()) {
                        return;
                    }
                }
            }
            fillAttributeValueCompletion(valueElement, result);
            // Suppress the default XML completion contributor.
            result.stopHere();
        }
    }

    private void fillAttributeNameCompletion(@NotNull PsiElement position, @NotNull XmlAttribute currentAttr,
                                             @NotNull CompletionResultSet result) {
        XmlTag tag = PsiTreeUtil.getParentOfType(position, XmlTag.class);
        if (tag == null) {
            return;
        }
        RuleRepository repo = RuleRepositoryService.getInstance().getRuleRepository();
        Optional<DslElementRule> ruleOpt = repo.getElementRule(tag.getName());
        if (ruleOpt.isEmpty()) {
            return;
        }
        DslElementRule rule = ruleOpt.get();
        Set<String> presentAttrNames = collectPresentAttrNames(tag, currentAttr);

        LOG.info("ThemeDSL attribute completion for tag <" + tag.getName() + ">");
        for (var entry : rule.getAttrTypes().entrySet()) {
            String attrName = entry.getKey();
            AttrTypeSpec spec = entry.getValue();
            if (isAlreadyPresent(attrName, spec, presentAttrNames)) {
                continue;
            }
            boolean required = rule.getRequiredAttrs() != null && rule.getRequiredAttrs().contains(attrName);
            String type = getTypeHint(spec);

            // Create a dummy XmlAttribute inside a dummy XmlTag with the correct
            // tag name, so the doc provider can look up the AttrTypeSpec.
            XmlAttribute dummyAttr = getDocAttribute(position.getProject(), tag.getName(), attrName);

            LookupElementBuilder builder = LookupElementBuilder.create(dummyAttr, attrName)
                    .withTypeText(type)
                    .withIcon(getIcon(type))
                    .withBoldness(required);
            LookupElement element = PrioritizedLookupElement.withPriority(
                    builder, required ? REQUIRED_PRIORITY : OPTIONAL_PRIORITY);
            result.addElement(element);
        }
    }

    /**
     * Returns a cached dummy {@link XmlAttribute} for documentation purposes.
     * The attribute is created inside a dummy {@code <tagName attrName=""/>}
     * tag so the doc provider can find both the tag name and attr name.
     */
    @Nullable
    private static XmlAttribute getDocAttribute(@NotNull com.intellij.openapi.project.Project project,
                                                @NotNull String tagName,
                                                @NotNull String attrName) {
        String key = tagName + ":" + attrName;
        XmlAttribute cached = DOC_ATTR_CACHE.get(key);
        if (cached != null && cached.isValid()) {
            return cached;
        }
        try {
            XmlElementFactory factory = XmlElementFactory.getInstance(project);
            XmlTag dummyTag = factory.createTagFromText("<" + tagName + " " + attrName + "=\"\"/>");
            XmlAttribute attr = dummyTag.getAttribute(attrName);
            if (attr != null) {
                DOC_ATTR_CACHE.put(key, attr);
                return attr;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /**
     * Offers enum values for attributes whose {@link AttrTypeSpec#getType()} is "enum".
     * Only fires for non-injected attribute values (bare values like {@code visibility="gone"});
     * expression values are handled by the DE completion contributor.
     */
    private void fillAttributeValueCompletion(@NotNull XmlAttributeValue valueElement,
                                             @NotNull CompletionResultSet result) {
        XmlAttribute attr = PsiTreeUtil.getParentOfType(valueElement, XmlAttribute.class);
        if (attr == null) {
            return;
        }
        XmlTag tag = PsiTreeUtil.getParentOfType(attr, XmlTag.class);
        if (tag == null) {
            return;
        }
        RuleRepository repo = RuleRepositoryService.getInstance().getRuleRepository();
        Optional<AttrTypeSpec> specOpt = repo.getAttrTypeSpec(tag.getName(), attr.getName());
        if (specOpt.isEmpty()) {
            return;
        }
        AttrTypeSpec spec = specOpt.get();
        List<String> enumValues = spec.getEnumValues();
        if (enumValues == null || enumValues.isEmpty()) {
            return;
        }
        for (String value : enumValues) {
            result.addElement(LookupElementBuilder.create(value)
                    .withIcon(AllIcons.Nodes.Enum)
                    .withTypeText(attr.getName()));
        }
    }

    /**
     * 收集标签中已存在的属性名，排除当前正在补全的占位属性，避免误把正在输入的属性判为已存在。
     */
    private static Set<String> collectPresentAttrNames(XmlTag tag, XmlAttribute currentAttr) {
        Set<String> names = new HashSet<>();
        for (XmlAttribute attr : tag.getAttributes()) {
            if (attr == currentAttr) {
                continue;
            }
            String name = attr.getName();
            if (name != null && !name.isEmpty()) {
                names.add(name);
            }
        }
        return names;
    }

    /**
     * 规范属性名或其任一别名已出现在标签中时，视为已存在。
     */
    private static boolean isAlreadyPresent(String canonicalName, AttrTypeSpec spec, Set<String> present) {
        if (present.contains(canonicalName)) {
            return true;
        }
        List<String> aliases = spec.getAliases();
        if (aliases != null) {
            for (String alias : aliases) {
                if (present.contains(alias)) {
                    return true;
                }
            }
        }
        return false;
    }

    private Icon getIcon(String type){
        if(type.contains("Expression")){
            return AllIcons.Nodes.ClassInitializer;
        }else if(type.contains("Enum")) {
            return AllIcons.Nodes.Enum;
        }else{
            return AllIcons.Nodes.Parameter;
        }
    }

    private String getTypeHint(AttrTypeSpec typeSpec){
        if(typeSpec.isSupportsExpression()){
            return StringUtils.capitalize(typeSpec.getExpressionKind()+" Expression");
        }else if(!typeSpec.getEnumValues().isEmpty()){
            return "Enum";
        }

        return StringUtils.capitalize(typeSpec.getType());
    }

    private static AttrTypeSpec findAttrSpec(DslElementRule rule, String attrName) {
        AttrTypeSpec spec = rule.getAttrTypes().get(attrName);
        if (spec != null) {
            return spec;
        }
        for (AttrTypeSpec candidate : rule.getAttrTypes().values()) {
            if (candidate.getAliases() != null && candidate.getAliases().contains(attrName)) {
                return candidate;
            }
        }
        return null;
    }
}
