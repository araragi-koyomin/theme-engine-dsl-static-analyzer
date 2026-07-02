package com.huawei.theme.analysis.core.cli;

import java.io.File;

public class CliMain {

    static final String USAGE_HINT =
            "Usage: java -jar dsl-analyzer.jar [options] <file-or-directory>\n" +
            "Options:\n" +
            "  --rule-dir <path>   Custom rule library directory (default: built-in)\n" +
            "  --no-type-check     Disable type inference checking (default: enabled)\n" +
            "  --verbose           Enable verbose output\n" +
            "  --config <path>     Inspection config file (JSON: ruleId enable/disable, severity override, root element override)\n" +
            "  --help, -h          Show this help message";

    public static void main(String[] args) {
        System.exit(run(args));
    }

    static int run(String[] args) {
        try {
            CliConfig config = CliConfig.fromArgs(args);
            if (config.isHelpRequested()) {
                System.out.println(USAGE_HINT);
                return 0;
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
            InspectionConfig inspectionConfig = null;
            if (config.getConfigPath() != null) {
                File configFile = new File(config.getConfigPath());
                if (!configFile.exists()) {
                    System.err.println(CliOutputFormatter.formatError("Config file not found: " + config.getConfigPath()));
                    return 2;
                }
                if (!configFile.isFile()) {
                    System.err.println(CliOutputFormatter.formatError("Config path is not a file: " + config.getConfigPath()));
                    return 2;
                }
                try {
                    InspectionConfigLoader loader = new InspectionConfigLoader();
                    inspectionConfig = loader.load(config.getConfigPath());
                } catch (InspectionConfigLoader.ConfigLoadException e) {
                    System.err.println(CliOutputFormatter.formatError("Config load error: " + e.getMessage()));
                    return 2;
                } catch (InspectionConfigLoader.ConfigValidationException e) {
                    System.err.println(CliOutputFormatter.formatError("Config validation error: " + e.getMessage()));
                    return 2;
                }
            }
            System.out.println(CliOutputFormatter.formatConfig(config, inspectionConfig));
            return 0;
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
}
