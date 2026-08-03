package com.huawei.theme.analysis.core.semanticanalysis.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import lombok.Builder;
import lombok.Data;
import org.jetbrains.annotations.Nullable;

@Data
@Builder
public class SymbolTable {
    /**
     * 他的上一层变量scope。如果是全局变量层，则为null
     */
    @Nullable
    @Builder.Default
    SymbolTable parent = null;

    @Builder.Default
    Map<String, VarDeclaration> declarations = Collections.emptyMap();
//    @Builder.Default List<VarReference> references = Collections.emptyList();

    /**
     * 文件中所有元素的 name 属性值集合（文件级全局）。
     *
     * <p>M4 VarRefAnalyzer 消费：SEM-REF-002 元素 name 引用存在性检测时，
     * 对 #<elementName>.<prop> 表达式引用与 Command target="name.property" 字面量，
     * 提取 elementName 后在此集合比对，未找到则产出诊断。仅全局表填充。</p>
     */
    @Builder.Default
    Set<String> elementNames = Collections.emptySet();

    /**
     * 文件中重复定义的变量名集合（仅用户声明的 Var，不含预设全局变量）。
     *
     * <p>M4 VarRefAnalyzer 消费：SEM-REF-003 重复变量定义检测时，
     * 遍历到 Var 元素后检查此集合，命中则报告重复定义。</p>
     */
    @Builder.Default
    Set<String> duplicateVarNames = Collections.emptySet();

    /**
     * 沿 parent 链查找变量声明，局部作用域优先，全局兜底。
     *
     * <p>M4 VarRefAnalyzer 消费：对每个 #/@var 引用调用本方法判断存在性，
     * 未找到则产出 SEM-REF-001 诊断。</p>
     *
     * @param name 变量名，可为含点号的全局变量名（如 system.time.hour1）
     * @return 找到的声明，未找到返回 Optional.empty()
     */
    public Optional<VarDeclaration> lookup(String name) {
        if (name == null) {
            return Optional.empty();
        }
        VarDeclaration decl = declarations.get(name);
        if (decl != null) {
            return Optional.of(decl);
        }
        return parent != null ? parent.lookup(name) : Optional.empty();
    }

    /**
     * 沿 parent 链找到全局符号表（parent==null 的根表）。
     *
     * <p>M4 VarRefAnalyzer 消费：SEM-REF-003 重复变量定义检测时，需查全局表中
     * 最终生效的声明（buildGlobal 后覆盖语义下存的是最后声明的 Var），
     * 比对其 astNode 与当前 Var 元素判断是否被覆盖。</p>
     *
     * @return 全局符号表，若当前已是根表则返回自身
     */
    public SymbolTable getGlobalTable() {
        return parent != null ? parent.getGlobalTable() : this;
    }

    /**
     * 枚举沿 parent 链可见的全部声明，内层作用域优先（同名内层覆盖外层）。
     *
     * <p>消费方：editor 层 completion / getVariants 据此填充候选列表。
     * 预置全局变量与文件中 &lt;Var&gt; 同在全局表（同名以文件 &lt;Var&gt; 为准，
     * 见 buildGlobal）；indexFlag 局部变量在各自 Array/CycleCommand 子树作用域。</p>
     *
     * @return 内层优先去重后的可见声明列表（保留首次出现顺序）
     */
    public java.util.List<VarDeclaration> visibleDeclarations() {
        LinkedHashMap<String, VarDeclaration> seen = new LinkedHashMap<>();
        for (SymbolTable s = this; s != null; s = s.parent) {
            if (s.declarations != null) {
                for (Map.Entry<String, VarDeclaration> e : s.declarations.entrySet()) {
                    seen.putIfAbsent(e.getKey(), e.getValue());
                }
            }
        }
        return new ArrayList<>(seen.values());
    }
}
