package com.huawei.theme.analysis.core.semanticanalysis.model;

import lombok.Builder;
import lombok.Data;

import com.huawei.theme.analysis.core.shared.ast.DslElementNode;
import com.huawei.theme.analysis.core.shared.ast.ExpressionAstNode;
import com.huawei.theme.analysis.core.shared.type.DslType;

@Data
@Builder
public class VarDeclaration {
    String name;
    DslType type;
    /**
     * 对应的expression的位置，如果外部变量则为null
     */
    ExpressionAstNode expression;
    boolean isConstAttr;
    /**
     * 是否是外部引入的全局变量
     */
    boolean isGlobal;
    /**
     * 对应的定义位置，如果是外部变量则为null
     */
    DslElementNode astNode;
    /**
     * 宿主元素中承载可注入 VarNameElement 的属性名。
     *
     * <p>{@code <Var name="x">} → "name"；{@code <Array indexFlag="i">} /
     * {@code <CycleCommand indexFlag="i">} → "indexFlag"；规则库预置全局变量 → null
     * （无宿主声明，resolve 返回 null，无下划线）。</p>
     *
     * <p>消费方：editor 层 resolve 据此从宿主 XmlTag 取对应 XmlAttribute 的
     * 注入 VarNameElement。</p>
     */
    String hostAttrName;
}
