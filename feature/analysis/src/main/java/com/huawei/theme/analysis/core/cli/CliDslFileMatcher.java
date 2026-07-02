package com.huawei.theme.analysis.core.cli;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import com.huawei.theme.analysis.core.fileidentification.DslFileIdentifier;
import com.huawei.theme.analysis.core.fileidentification.DslFileMatcher;
import com.huawei.theme.analysis.core.rulelibrary.RuleRepository;

public class CliDslFileMatcher implements DslFileMatcher {

    private static final String XML_EXTENSION = ".xml";

    private final DslFileIdentifier delegate;

    public CliDslFileMatcher(RuleRepository ruleRepository) {
        this.delegate = new DslFileIdentifier(ruleRepository);
    }

    public boolean isDslFile(File file) {
        if (!file.exists() || !file.isFile()) {
            return false;
        }
        if (!file.getName().toLowerCase().endsWith(XML_EXTENSION)) {
            return false;
        }
        try {
            String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            return delegate.isDslFile(file.getPath(), content);
        } catch (IOException e) {
            return false;
        }
    }

    public boolean isDslFile(String filePath) {
        return isDslFile(new File(filePath));
    }

    @Override
    public boolean isDslFile(String filePath, String content) {
        return delegate.isDslFile(filePath, content);
    }
}
