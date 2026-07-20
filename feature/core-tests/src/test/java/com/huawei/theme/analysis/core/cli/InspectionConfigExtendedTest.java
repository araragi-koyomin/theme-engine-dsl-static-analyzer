package com.huawei.theme.analysis.core.cli;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InspectionConfigExtendedTest {

    @Test
    void builderWithPipelineMode() {
        InspectionConfig config = InspectionConfig.builder()
                .pipelineMode(PipelineMode.SYNTAX_ONLY)
                .typeCheck(true)
                .noColor(false)
                .verbose(false)
                .quiet(false)
                .build();
        assertEquals(PipelineMode.SYNTAX_ONLY, config.getPipelineMode());
        assertTrue(config.isTypeCheck());
        assertFalse(config.isNoColor());
    }

    @Test
    void builderDefaults() {
        InspectionConfig config = InspectionConfig.builder().build();
        assertNull(config.getPipelineMode());
        assertNull(config.getRootElementNames());
    }

    @Test
    void pipelineModeSemanticOnly() {
        InspectionConfig config = InspectionConfig.builder()
                .pipelineMode(PipelineMode.SEMANTIC_ONLY)
                .typeCheck(false)
                .build();
        assertEquals(PipelineMode.SEMANTIC_ONLY, config.getPipelineMode());
        assertFalse(config.isTypeCheck());
    }
}
