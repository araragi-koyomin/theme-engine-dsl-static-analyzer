package com.huawei.theme.analysis.core.cli;

import java.io.File;

import com.huawei.theme.analysis.core.batchinspection.BatchInspectionRunner;
import com.huawei.theme.analysis.core.batchinspection.BatchInspectionRunnerImpl;
import com.huawei.theme.analysis.core.batchinspection.ExitCodeCalculator;
import com.huawei.theme.analysis.core.batchinspection.ReportExporter;
import com.huawei.theme.analysis.core.batchinspection.ReportExporterImpl;
import com.huawei.theme.analysis.core.batchinspection.TerminalFormatter;
import com.huawei.theme.analysis.core.batchinspection.model.BatchInspectionResult;
import com.huawei.theme.analysis.core.rulelibrary.JsonRuleLoader;
import com.huawei.theme.analysis.core.rulelibrary.RuleRepository;
import com.huawei.theme.analysis.core.semanticanalysis.DiagnosticProviderImpl;
import com.huawei.theme.analysis.core.semanticanalysis.SymbolTableBuilderImpl;
import com.huawei.theme.analysis.core.semanticanalysis.AnalyzerRegistry;
import com.huawei.theme.analysis.core.quickfix.QuickFixProviderImpl;

public class CliMain {

    public static final String VERSION = "0.1.0";

    static final String USAGE_HINT =
            "Usage: java -jar dsl-analyzer.jar [options] <file-or-directory>\n" +
            "Options:\n" +
            "  --rule-dir <path>     Custom rule library directory (default: built-in)\n" +
            "  --no-type-check       Disable type inference checking (default: enabled)\n" +
            "  --syntax-only         Only run syntax analysis (skip M4/M5)\n" +
            "  --semantic-only       Only run semantic analysis (skip syntax errors)\n" +
            "  --format <format>     Output format: terminal (default), json, markdown\n" +
            "  --output <path>       Write report to file (json/markdown only)\n" +
            "  --no-color            Disable ANSI color output\n" +
            "  --verbose             Enable verbose output\n" +
            "  --quiet               Only output error-level diagnostics\n" +
            "  --config <path>       Inspection config file (JSON)\n" +
            "  --version             Show version\n" +
            "  --help, -h            Show this help message";

    private static final String BUILT_IN_RULES_PATH = "feature/analysis/src/main/resources/rules";

    public static void main(String[] args) {
        System.exit(run(args));
    }

    static int run(String[] args) {
        try {
            AnalyzerRegistry.init();
            CliConfig config = CliConfig.fromArgs(args);

            if (config.isVersionRequested()) {
                System.out.println(CliOutputFormatter.formatVersion());
                return 0;
            }

            if (config.isHelpRequested()) {
                System.out.println(USAGE_HINT);
                return 0;
            }

            String mutualExclusionError = checkMutualExclusion(config);
            if (mutualExclusionError != null) {
                System.err.println(CliOutputFormatter.formatError(mutualExclusionError));
                System.err.println(USAGE_HINT);
                return 2;
            }

            File targetFile = new File(config.getTargetPath());
            if (!targetFile.exists()) {
                System.err.println(CliOutputFormatter.formatError("Path not found: " + config.getTargetPath()));
                System.err.println(USAGE_HINT);
                return 2;
            }
            if (!targetFile.isFile() && !targetFile.isDirectory()) {
                System.err.println(CliOutputFormatter.formatError("Path is not a file or directory: " + config.getTargetPath()));
                System.err.println(USAGE_HINT);
                return 2;
            }

            InspectionConfig inspectionConfig = loadInspectionConfig(config);
            if (inspectionConfig == null) {
                return 2;
            }

            PipelineMode mode = resolvePipelineMode(config);
            InspectionConfig effectiveConfig = InspectionConfig.builder()
                    .rootElementNames(inspectionConfig.getRootElementNames())
                    .enabledRuleIds(inspectionConfig.getEnabledRuleIds())
                    .disabledRuleIds(inspectionConfig.getDisabledRuleIds())
                    .severityOverrides(inspectionConfig.getSeverityOverrides())
                    .pipelineMode(mode)
                    .typeCheck(config.isTypeCheck())
                    .noColor(config.isNoColor())
                    .verbose(config.isVerbose())
                    .quiet(config.isQuiet())
                    .build();

            RuleRepository ruleRepo = loadRuleRepository(config);
            if (ruleRepo == null) {
                return 2;
            }

            RuleRepository effectiveRepo = new ConfigAwareRuleRepository(ruleRepo, effectiveConfig);

            CliDslFileMatcher matcher = new CliDslFileMatcher(effectiveRepo);
            CliDslAstProvider astProvider = new CliDslAstProvider(effectiveRepo);

            BatchInspectionRunner runner = new BatchInspectionRunnerImpl(
                    matcher, astProvider,
                    new DiagnosticProviderImpl(),
                    new QuickFixProviderImpl(),
                    new SymbolTableBuilderImpl(),
                    effectiveRepo,
                    effectiveConfig
            );

            BatchInspectionResult result;
            if (targetFile.isFile()) {
                result = runner.runOnFile(targetFile.getAbsolutePath());
            } else {
                result = runner.runOnDirectory(targetFile.getAbsolutePath());
            }

            exportReport(result, config);

            return ExitCodeCalculator.compute(result);
        } catch (IllegalArgumentException e) {
            System.err.println(CliOutputFormatter.formatError(e.getMessage()));
            System.err.println(USAGE_HINT);
            return 2;
        } catch (Exception e) {
            System.err.println(CliOutputFormatter.formatError("Unexpected error: " + e.getMessage()));
            System.err.println(USAGE_HINT);
            return 2;
        }
    }

    private static String checkMutualExclusion(CliConfig config) {
        if (config.isVerbose() && config.isQuiet()) {
            return "--verbose and --quiet are mutually exclusive";
        }
        if (config.isSyntaxOnly() && config.isSemanticOnly()) {
            return "--syntax-only and --semantic-only are mutually exclusive";
        }
        return null;
    }

    private static PipelineMode resolvePipelineMode(CliConfig config) {
        if (config.isSyntaxOnly()) {
            return PipelineMode.SYNTAX_ONLY;
        }
        if (config.isSemanticOnly()) {
            return PipelineMode.SEMANTIC_ONLY;
        }
        return PipelineMode.FULL;
    }

    private static InspectionConfig loadInspectionConfig(CliConfig config) {
        if (config.getConfigPath() == null) {
            return InspectionConfig.builder().build();
        }
        File configFile = new File(config.getConfigPath());
        if (!configFile.exists()) {
            System.err.println(CliOutputFormatter.formatError("Config file not found: " + config.getConfigPath()));
            return null;
        }
        if (!configFile.isFile()) {
            System.err.println(CliOutputFormatter.formatError("Config path is not a file: " + config.getConfigPath()));
            return null;
        }
        try {
            InspectionConfigLoader loader = new InspectionConfigLoader();
            return loader.load(config.getConfigPath());
        } catch (InspectionConfigLoader.ConfigLoadException e) {
            System.err.println(CliOutputFormatter.formatError("Config load error: " + e.getMessage()));
            return null;
        } catch (InspectionConfigLoader.ConfigValidationException e) {
            System.err.println(CliOutputFormatter.formatError("Config validation error: " + e.getMessage()));
            return null;
        }
    }

    private static RuleRepository loadRuleRepository(CliConfig config) {
        JsonRuleLoader loader = new JsonRuleLoader();
        if (config.getRuleDir() != null) {
            File ruleDirFile = new File(config.getRuleDir());
            if (!ruleDirFile.exists()) {
                System.err.println(CliOutputFormatter.formatError("Rule directory not found: " + config.getRuleDir()));
                return null;
            }
            try {
                RuleRepository customRepo = loader.loadFromDirectory(config.getRuleDir());
                if (customRepo.getAllElementNames().isEmpty()) {
                    System.err.println(CliOutputFormatter.formatWarning(
                            "Custom rule directory has no rule files, falling back to built-in rules"));
                    return loadBuiltInRules(loader);
                }
                return customRepo;
            } catch (JsonRuleLoader.RuleLoadException e) {
                System.err.println(CliOutputFormatter.formatError("Rule load error: " + e.getMessage()));
                return null;
            }
        }
        return loadBuiltInRules(loader);
    }

    private static RuleRepository loadBuiltInRules(JsonRuleLoader loader) {
        try {
            return loader.loadFromDirectory(BUILT_IN_RULES_PATH);
        } catch (JsonRuleLoader.RuleLoadException e) {
            System.err.println(CliOutputFormatter.formatError("Built-in rules load error: " + e.getMessage()));
            return null;
        }
    }

    private static void exportReport(BatchInspectionResult result, CliConfig config) {
        TerminalFormatter formatter = new TerminalFormatter(config.isNoColor());
        ReportExporter exporter = new ReportExporterImpl(formatter);

        if (config.getOutputPath() != null) {
            exporter.exportToFile(result, config.getFormat(), config.getOutputPath());
            if (config.isVerbose()) {
                System.out.println("Report written to: " + config.getOutputPath());
            }
        } else {
            String output;
            switch (config.getFormat().toLowerCase()) {
                case "json":
                    output = exporter.exportJson(result);
                    break;
                case "markdown":
                case "md":
                    output = exporter.exportMarkdown(result);
                    break;
                default:
                    output = exporter.exportTerminal(result);
                    break;
            }
            System.out.println(output);
        }
    }
}
