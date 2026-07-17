package com.huawei.theme.analysis.lsp;

import org.junit.jupiter.api.Test;

import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.ServerCapabilities;

import com.huawei.theme.analysis.core.cli.InspectionConfig;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DslLanguageServerCapabilitiesTest {

    @Test
    void declaresDefinitionProvider() throws Exception {
        DslLanguageServer server = new DslLanguageServer(null, InspectionConfig.builder().build());
        InitializeResult result = server.initialize(new InitializeParams()).get();
        ServerCapabilities caps = result.getCapabilities();
        assertTrue(caps.getDefinitionProvider().getLeft(),
                "server must declare definitionProvider capability");
    }
}
