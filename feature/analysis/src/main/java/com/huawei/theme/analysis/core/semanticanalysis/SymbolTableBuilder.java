package com.huawei.theme.analysis.core.semanticanalysis;

import com.huawei.theme.analysis.core.rulelibrary.RuleRepository;
import com.huawei.theme.analysis.core.semanticanalysis.model.SymbolTable;
import com.huawei.theme.analysis.core.shared.ast.DslElementNode;
import com.huawei.theme.analysis.core.shared.ast.DslFileNode;

/**
 * 符号表构建器，M4语义分析的对外契约。
 *
 * <p>负责将AST与规则库转化为符号表（{@link SymbolTable}），供变量引用分析、
 * 类型推断等消费方使用。构建分两层：</p>
 * <ul>
 *   <li>{@link #buildGlobal} 构建全局符号表：注入规则库预置全局变量，并收集文件中所有 &lt;Var&gt; 元素</li>
 *   <li>{@link #build} 为元素的子节点构建局部作用域符号表：处理 &lt;Array&gt;/&lt;CycleCommand&gt; 的 indexFlag 局部变量</li>
 * </ul>
 *
 * <p>消费方：M4 {@code DiagnosticProviderImpl} 在遍历AST时调用本接口，
 * 先建立全局表，再随递归下钻逐层构建子作用域表。</p>
 *
 * <p>遵循依赖倒置：消费方依赖此接口而非实现，便于测试注入与多数据源替换。</p>
 */
public interface SymbolTableBuilder {

    /**
     * 构建全局符号表。
     *
     * <p>全局表包含两类符号：</p>
     * <ol>
     *   <li>规则库预置全局变量：来自 {@link RuleRepository#getAllGlobalVars()}，
     *   如 battery_level、ishour12。标记 isGlobal=true，无定义位置（astNode=null）</li>
     *   <li>文件中所有 &lt;Var&gt; 元素：深度遍历整棵元素树收集，
     *   无论 Var 出现在树中哪个位置均视为全局变量。标记 isGlobal=false，
     *   astNode 指向 Var 元素节点；type 取自 type 属性（缺省为 number）</li>
     </ol>
     *
     * <p>同名时文件中的 &lt;Var&gt; 覆盖规则库预置变量，以文件声明为最终生效符号。</p>
     *
     * @param fileNode DSL文件AST根节点，为null时仅返回预置全局变量
     * @param ruleRepository 规则库查询接口，提供预置全局变量
     * @return 全局符号表，parent=null
     */
    SymbolTable buildGlobal(DslFileNode fileNode, RuleRepository ruleRepository);

    /**
     * 为给定元素的子节点构建局部作用域符号表。
     *
     * <p>调用方（如 {@code DiagnosticProviderImpl}）在进入元素的子节点前调用本方法，
     * 返回的表作为子节点分析期间的符号表。本方法仅处理局部变量声明，
     * 不改变父表内容；子表通过 parent 链向上查找父作用域符号。</p>
     *
     * <p>当前仅两类元素引入局部变量：</p>
     * <ul>
     *   <li>&lt;Array&gt;：indexFlag 属性声明循环索引变量名，类型为 number，
     *   取值范围 [0, frequency-1]</li>
     *   <li>&lt;CycleCommand&gt;：indexFlag 属性声明循环索引变量名，类型为 number，
     *   取值范围由 frequency 或 [begin, end] 决定</li>
     * </ul>
     *
     * <p>indexFlag 变量仅在元素子节点作用域内可见（如 #__i），
     * 同级兄弟节点不可见。非上述元素或不带 indexFlag 时直接返回 parent，
     * 即子节点共享父作用域。</p>
     *
     * TODO: 现在是硬编码，以后通过外部配置文件修改什么会新建变量域。
     *
     * @param elementNode 当前元素节点，为null时直接返回parent
     * @param parent 父作用域符号表
     * @param ruleRepository 规则库查询接口（局部变量构建当前未使用，保留以备扩展）
     * @return 子节点作用域符号表；无新增局部变量时返回parent
     */
    SymbolTable build(DslElementNode elementNode, SymbolTable parent, RuleRepository ruleRepository);
}
