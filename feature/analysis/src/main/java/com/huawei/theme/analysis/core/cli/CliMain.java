package com.huawei.theme.analysis.core.cli;

import java.io.File;

public class CliMain {

    static final String USAGE_HINT =
            "Usage: java -jar dsl-analyzer.jar [options] <file-or-directory>\n" +
            "Options:\n" +
            "  --rule-dir <path>   Custom rule library directory (default: built-in)\n" +
            "  --no-type-check     Disable type inference checking (default: enabled)\n" +
            "  --verbose           Enable verbose output\n" +
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
            System.out.println(CliOutputFormatter.formatConfig(config));
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
