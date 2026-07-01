package com.huawei.theme.analysis.core.cli;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import com.huawei.theme.analysis.core.rulelibrary.RuleRepository;
import com.huawei.theme.analysis.core.shared.ast.DslElementNode;
import com.huawei.theme.analysis.core.shared.ast.DslFileNode;
import com.huawei.theme.analysis.core.syntaxanalysis.AstBuilder;
import com.huawei.theme.analysis.core.syntaxanalysis.DslAstProvider;

public class CliDslAstProvider implements DslAstProvider {

    private final AstBuilder delegate;

    public CliDslAstProvider(RuleRepository ruleRepository) {
        this.delegate = new AstBuilder(ruleRepository);
    }

    public DslFileNode getDslAst(File file) {
        try {
            String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            return delegate.getDslAst(file.getPath(), content);
        } catch (IOException e) {
            DslFileNode fileNode = new DslFileNode();
            fileNode.setFilePath(file.getPath());
            DslElementNode errorNode = new DslElementNode();
            errorNode.setHasError(true);
            errorNode.setErrorMessage(e.getMessage());
            errorNode.setLine(0);
            errorNode.setColumn(0);
            errorNode.setAttributes(new java.util.ArrayList<>());
            errorNode.setChildElements(new java.util.ArrayList<>());
            fileNode.setRootElement(errorNode);
            errorNode.setParent(fileNode);
            return fileNode;
        }
    }

    @Override
    public DslFileNode getDslAst(String filePath, String content) {
        return delegate.getDslAst(filePath, content);
    }
}
