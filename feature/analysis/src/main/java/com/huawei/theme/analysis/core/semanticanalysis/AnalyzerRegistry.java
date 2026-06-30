package com.huawei.theme.analysis.core.semanticanalysis;

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

    public static void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        register(new ConstraintAnalyzer());
    }

}
