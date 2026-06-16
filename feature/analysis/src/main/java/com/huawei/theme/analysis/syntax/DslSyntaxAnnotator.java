package com.huawei.theme.analysis.syntax;

import java.util.Locale;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.psi.PsiErrorElement;

public class DslSyntaxAnnotator implements Annotator {
    @Override
    public void annotate(com.intellij.psi.PsiElement element, AnnotationHolder holder) {
        // 只处理语法错误元素，其他元素不进行标注
        if (!(element instanceof PsiErrorElement)) {
            return;
        }
        PsiErrorElement errorElement = (PsiErrorElement) element;
        String errorDescription = errorElement.getErrorDescription();
        if (errorDescription == null) {
            return;
        }
        String lowerDesc = errorDescription.toLowerCase(Locale.ENGLISH);
        String ruleId = mapErrorToRuleId(lowerDesc);
        if (ruleId != null) {
            holder.newAnnotation(HighlightSeverity.ERROR, ruleId + ": " + errorDescription) // 步骤1：创建标注+设定严重级别
                    .range(errorElement) // 步骤2：设定标注范围（哪个PsiElement）
                    .create(); // 步骤3：注册到编辑器
        }
    }

    String mapErrorToRuleId(String lowerDesc) {
        if (lowerDesc.contains("not closed") || lowerDesc.contains("unclosed")
                || lowerDesc.contains("end tag") || lowerDesc.contains("closed")) {
            return DslSyntaxConstants.SYN_001;
        }
        if (lowerDesc.contains("quote") || lowerDesc.contains("quotation")
                || lowerDesc.contains("attribute value")) {
            return DslSyntaxConstants.SYN_003;
        }
        if (lowerDesc.contains("nesting") || lowerDesc.contains("nested")) {
            return DslSyntaxConstants.SYN_002;
        }
        return null;
    }
}
