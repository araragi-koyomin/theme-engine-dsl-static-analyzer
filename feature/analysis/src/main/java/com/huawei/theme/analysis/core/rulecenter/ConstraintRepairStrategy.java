package com.huawei.theme.analysis.core.rulecenter;

@FunctionalInterface
public interface ConstraintRepairStrategy {
    ConstraintRepairProposal repair(ConstraintRepairContext context);
}
