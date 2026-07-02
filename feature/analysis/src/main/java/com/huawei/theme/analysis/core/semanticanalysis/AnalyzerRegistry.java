package com.huawei.theme.analysis.core.semanticanalysis;

import com.huawei.theme.analysis.core.semanticanalysis.analyzers.*;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

public class AnalyzerRegistry {
    private AnalyzerRegistry(){}

    @Getter
    private static List<DslAnalyzer> analyzers = new ArrayList<>();

    private static boolean initialized = false;

    public static void register(DslAnalyzer analyzer){
        analyzers.add(analyzer);
    }

    static{
        register(new ConstraintAnalyzer());
        register(new ParentChildAnalyzer());
        register(new ScopeAnalyzer());
        register(new RequiredAttrAnalyzer());
        register(new LiteralTypeAnalyzer());
        register(new EnumValueAnalyzer());
    }

    /**
     * init已经在static快中执行了。
     */
    @Deprecated()
    public static void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        register(new ConstraintAnalyzer());
        register(new ParentChildAnalyzer());
        register(new ScopeAnalyzer());
        register(new RequiredAttrAnalyzer());
        register(new LiteralTypeAnalyzer());
        register(new EnumValueAnalyzer());
        register(new VarRefAnalyzer());
        register(new TypeAnalyzer());
    }

}
