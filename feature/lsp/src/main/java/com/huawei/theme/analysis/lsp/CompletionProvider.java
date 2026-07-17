package com.huawei.theme.analysis.lsp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.InsertTextFormat;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.MarkupKind;

import com.huawei.theme.analysis.core.rulelibrary.RuleRepository;
import com.huawei.theme.analysis.core.rulelibrary.model.AttrTypeSpec;
import com.huawei.theme.analysis.core.rulelibrary.model.DslElementRule;
import com.huawei.theme.analysis.core.rulelibrary.model.DslGlobalVar;
import com.huawei.theme.analysis.core.shared.ast.DslAttributeNode;
import com.huawei.theme.analysis.core.shared.ast.DslElementNode;

/**
 * Provides completion items for element names, attribute names, attribute
 * values, and expression-internal tokens (variables and functions).
 *
 * <p>Sorting: items already present in the document (element names already
 * used, attributes already on the enclosing element) are sorted last via a
 * {@code 2_} sortText prefix, after required ({@code 0_}) and optional
 * ({@code 1_}).</p>
 */
final class CompletionProvider {

    private final RuleRepository ruleRepository;
    private final HoverProvider hoverProvider;

    CompletionProvider(RuleRepository ruleRepository) {
        this.ruleRepository = ruleRepository;
        this.hoverProvider = new HoverProvider(ruleRepository);
    }

    List<CompletionItem> complete(ContextResolver.Context ctx,
                                   Set<String> presentElementNames,
                                   Set<String> declaredVars) {
        List<CompletionItem> items;
        if (ctx.type == ContextResolver.PositionType.ELEMENT_NAME) {
            items = elementNameItems(ctx.word,
                    presentElementNames != null ? presentElementNames : Collections.emptySet());
        } else if (ctx.type == ContextResolver.PositionType.ATTRIBUTE_NAME && ctx.tagName != null) {
            items = attributeNameItems(ctx.tagName, ctx.word, ctx.elementNode);
        } else if (ctx.type == ContextResolver.PositionType.ATTRIBUTE_VALUE
                && ctx.tagName != null && ctx.attrName != null) {
            items = attributeValueItems(ctx.tagName, ctx.attrName, ctx.word,
                    ctx.elementNode, declaredVars);
        } else {
            items = new ArrayList<>();
        }
        // Sort by sortText (type-priority first, then alphabetical within type).
        items.sort(Comparator.comparing(
                i -> i.getSortText() != null ? i.getSortText() : "",
                Comparator.naturalOrder()));
        return items;
    }

    // ---- Element name completion ----

    private List<CompletionItem> elementNameItems(String prefix, Set<String> presentNames) {
        List<CompletionItem> items = new ArrayList<>();
        for (String name : ruleRepository.getAllElementNames()) {
            if (!matches(prefix, name)) {
                continue;
            }
            boolean present = presentNames.contains(name);
            CompletionItem item = new CompletionItem(name);
            item.setKind(CompletionItemKind.Class);
            Optional<DslElementRule> ruleOpt = ruleRepository.getElementRule(name);
            String category = ruleOpt.map(DslElementRule::getCategory).orElse(null);
            item.setDetail((category != null && !category.isEmpty()) ? category : "ThemeDSL tag");
            item.setSortText((present ? "2_" : "0_") + name.toLowerCase());
            String markup = hoverProvider.tagMarkup(name);
            if (markup != null) {
                item.setDocumentation(new MarkupContent(MarkupKind.MARKDOWN, markup));
            }
            items.add(item);
        }
        return items;
    }

    // ---- Attribute name completion ----

    private List<CompletionItem> attributeNameItems(String tagName, String prefix,
                                                     DslElementNode elementNode) {
        Optional<DslElementRule> ruleOpt = ruleRepository.getElementRule(tagName);
        if (ruleOpt.isEmpty()) {
            return List.of();
        }
        DslElementRule rule = ruleOpt.get();
        Set<String> canonical = ruleRepository.getCanonicalAttrNames(tagName);
        List<String> required = rule.getRequiredAttrs();
        Set<String> presentAttrs = collectPresentAttrs(elementNode);
        List<CompletionItem> items = new ArrayList<>();
        for (String attr : canonical) {
            if (!matches(prefix, attr)) {
                continue;
            }
            boolean isRequired = required != null && required.contains(attr);
            boolean isPresent = presentAttrs.contains(attr);
            CompletionItem item = new CompletionItem(attr);
            item.setKind(isRequired ? CompletionItemKind.Field : CompletionItemKind.Property);
            item.setDetail(isRequired ? "required" : (isPresent ? "already set" : "optional"));
            item.setSortText((isPresent ? "2_" : (isRequired ? "0_" : "1_")) + attr.toLowerCase());
            item.setInsertText(attr + "=\"$0\"");
            item.setInsertTextFormat(InsertTextFormat.Snippet);
            String markup = hoverProvider.attributeMarkup(tagName, attr);
            if (markup != null) {
                item.setDocumentation(new MarkupContent(MarkupKind.MARKDOWN, markup));
            }
            items.add(item);
        }
        return items;
    }

    private static Set<String> collectPresentAttrs(DslElementNode elementNode) {
        Set<String> attrs = new HashSet<>();
        if (elementNode != null && elementNode.getAttributes() != null) {
            for (DslAttributeNode a : elementNode.getAttributes()) {
                if (a.getName() != null) {
                    attrs.add(a.getName());
                }
            }
        }
        return attrs;
    }

    // ---- Attribute value completion (enum + variables + functions) ----

    private List<CompletionItem> attributeValueItems(String tagName, String attrName,
                                                      String word, DslElementNode elementNode,
                                                      Set<String> declaredVars) {
        Optional<AttrTypeSpec> specOpt = ruleRepository.getAttrTypeSpec(tagName, attrName);
        if (specOpt.isEmpty()) {
            return List.of();
        }
        AttrTypeSpec spec = specOpt.get();
        List<CompletionItem> items = new ArrayList<>();

        // 1. Enum values (if any)
        List<String> enumValues = spec.getEnumValues();
        if (enumValues != null && !enumValues.isEmpty()) {
            String defaultValue = spec.getDefaultValue();
            for (String value : enumValues) {
                if (!matches(word, value)) {
                    continue;
                }
                boolean isDefault = value.equals(defaultValue);
                CompletionItem item = new CompletionItem(value);
                item.setKind(CompletionItemKind.EnumMember);
                item.setDetail(spec.getType());
                item.setSortText((isDefault ? "0_" : "1_") + value.toLowerCase());
                item.setInsertText(value);
                items.add(item);
            }
        }

        // 2. Variables and functions (if the attribute supports expressions)
        if (spec.isSupportsExpression()) {
            items.addAll(variableItems(word, declaredVars));
            items.addAll(functionItems(word));
        }

        return items;
    }

    // ---- Variable completion ----

    private List<CompletionItem> variableItems(String word, Set<String> declaredVars) {
        List<CompletionItem> items = new ArrayList<>();
        if (declaredVars == null) {
            declaredVars = Collections.emptySet();
        }
        // Determine prefix from the word: if it starts with # or @, use that;
        // otherwise offer both # and @ variants.
        char prefix = 0;
        String filter = "";
        if (word != null && !word.isEmpty()) {
            char first = word.charAt(0);
            if (first == '#' || first == '@') {
                prefix = first;
                filter = word.substring(1);
            } else {
                filter = word;
            }
        }
        char[] prefixes = prefix != 0 ? new char[]{prefix} : new char[]{'#', '@'};
        // Collect all variable names: declared (from AST) + global (from rules)
        Set<String> allVarNames = new HashSet<>(declaredVars);
        for (DslGlobalVar gv : ruleRepository.getAllGlobalVars()) {
            if (gv.getName() != null) {
                allVarNames.add(gv.getName());
            }
        }
        for (char pfx : prefixes) {
            for (String varName : allVarNames) {
                String label = pfx + varName;
                if (!matches(filter, varName)) {
                    continue;
                }
                CompletionItem item = new CompletionItem(label);
                item.setKind(CompletionItemKind.Variable);
                boolean isGlobal = declaredVars.contains(varName) == false
                        && ruleRepository.getGlobalVar(varName).isPresent();
                item.setDetail(isGlobal ? "global variable" : "user variable");
                item.setSortText((isGlobal ? "3_" : "2_") + label.toLowerCase());
                item.setInsertText(label);
                items.add(item);
            }
        }
        return items;
    }

    // ---- Function completion ----

    private List<CompletionItem> functionItems(String word) {
        List<CompletionItem> items = new ArrayList<>();
        var fnLib = ruleRepository.getFunctionSignatureLibrary();
        if (fnLib == null) {
            return items;
        }
        // Get all function names by checking known function signatures.
        // The function library doesn't have a "list all" method, so we scan
        // the DSL functions resource for known function names.
        for (String fnName : KNOWN_FUNCTIONS) {
            if (!fnLib.hasFunction(fnName)) {
                continue;
            }
            if (!matches(word, fnName)) {
                continue;
            }
            CompletionItem item = new CompletionItem(fnName + "()");
            item.setKind(CompletionItemKind.Function);
            item.setDetail("function");
            item.setSortText("4_" + fnName.toLowerCase());
            item.setInsertText(fnName + "($0)");
            item.setInsertTextFormat(InsertTextFormat.Snippet);
            items.add(item);
        }
        return items;
    }

    private static boolean matches(String prefix, String candidate) {
        return prefix == null || prefix.isEmpty()
                || candidate.toLowerCase().startsWith(prefix.toLowerCase());
    }

    // Common DSL function names — the function library doesn't expose a
    // "list all" method, so we enumerate the known set. This covers all
    // functions in the built-in dsl_functions.json.
    private static final List<String> KNOWN_FUNCTIONS = List.of(
            "abs", "ceil", "floor", "round", "max", "min",
            "sin", "cos", "tan", "asin", "acos", "atan",
            "sqrt", "pow", "log", "exp", "random",
            "if", "clamp", "lerp", "map", "range",
            "isHour12", "isHour24",
            "intToString", "stringToInt",
            "sinh", "cosh", "tanh",
            "contains", "length", "toUpperCase", "toLowerCase",
            "now", "today", "formatDate", "formatTime",
            "getVariable", "setVariable"
    );
}
