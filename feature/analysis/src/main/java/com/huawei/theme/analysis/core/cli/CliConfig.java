package com.huawei.theme.analysis.core.cli;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CliConfig {
    String ruleDir;
    @Builder.Default
    boolean typeCheck = true;
    boolean verbose;
    boolean helpRequested;
    String targetPath;
    String configPath;
    @Builder.Default
    String format = "terminal";
    String outputPath;
    @Builder.Default
    boolean noColor = false;
    boolean quiet;
    boolean syntaxOnly;
    boolean semanticOnly;
    boolean versionRequested;

    public static CliConfig fromArgs(String[] args) {
        CliConfigBuilder builder = CliConfig.builder();
        String targetPath = null;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--rule-dir":
                    if (i + 1 >= args.length) {
                        throw new IllegalArgumentException("--rule-dir requires a path value");
                    }
                    builder.ruleDir(args[++i]);
                    break;
                case "--no-type-check":
                    builder.typeCheck(false);
                    break;
                case "--verbose":
                    builder.verbose(true);
                    break;
                case "--quiet":
                    builder.quiet(true);
                    break;
                case "--format":
                    if (i + 1 >= args.length) {
                        throw new IllegalArgumentException("--format requires a value (json/markdown/terminal)");
                    }
                    builder.format(args[++i]);
                    break;
                case "--output":
                    if (i + 1 >= args.length) {
                        throw new IllegalArgumentException("--output requires a path value");
                    }
                    builder.outputPath(args[++i]);
                    break;
                case "--no-color":
                    builder.noColor(true);
                    break;
                case "--syntax-only":
                    builder.syntaxOnly(true);
                    break;
                case "--semantic-only":
                    builder.semanticOnly(true);
                    break;
                case "--version":
                    builder.versionRequested(true);
                    break;
                case "--help":
                case "-h":
                    builder.helpRequested(true);
                    break;
                case "--config":
                    if (i + 1 >= args.length) {
                        throw new IllegalArgumentException("--config requires a path value");
                    }
                    builder.configPath(args[++i]);
                    break;
                default:
                    if (args[i].startsWith("--")) {
                        throw new IllegalArgumentException("Unknown option: " + args[i]);
                    }
                    if (targetPath != null) {
                        throw new IllegalArgumentException(
                                "Multiple target paths provided. Only one <file-or-directory> argument is allowed.");
                    }
                    targetPath = args[i];
                    break;
            }
        }

        if (builder.build().isHelpRequested() || builder.build().isVersionRequested()) {
            builder.targetPath(targetPath);
            return builder.build();
        }

        if (targetPath == null) {
            throw new IllegalArgumentException("No target path provided");
        }

        builder.targetPath(targetPath);
        return builder.build();
    }
}
