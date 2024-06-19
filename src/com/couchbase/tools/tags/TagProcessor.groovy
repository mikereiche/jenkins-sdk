package com.couchbase.tools.tags

import com.couchbase.tools.performer.*
import com.couchbase.versions.ImplementationVersion
import groovy.cli.picocli.CliBuilder
import groovy.io.FileType
import groovy.transform.CompileStatic

import java.nio.file.Files
import java.util.logging.ConsoleHandler
import java.util.logging.LogRecord
import java.util.logging.Logger
import java.util.logging.SimpleFormatter
import java.util.regex.Pattern

/**
 * A pre-processor that comments-out bits of code depending on a target SDK version.
 * <p>
 * See TAG_SYNTAX.md for more info.
 * <p>
 * See README.md for an example of how to run this from the command line using Gradle.
 */
class TagProcessor {
    private static Logger logger = Logger.getLogger("")

    static boolean hasIfTags(File file) {
        String commentPrefix = file.name.endsWithAny(".rb", ".py") ? "#" : "//";
        String content = Files.readString(file.toPath())
        return content =~ /(?m).*${Pattern.quote(commentPrefix)}\s*\[if:.+]/
    }

    @CompileStatic
    public static void processTags(File rootDirectory, ImplementationVersion version) {
        def warnings = new ArrayList()

        rootDirectory.traverse(type: FileType.FILES) { file ->
            // Let developers opt in to the single-line comment implementation
            if (hasIfTags(file)) {
                logger.info("Processing any tags in file ${file.getAbsolutePath()} using V1 ('if', 'else', nested tags)")
                warnings.addAll(TagProcessorV1.process(file, version))
            } else {
                logger.info("Processing any tags in file ${file.getAbsolutePath()} using V0 (no 'if' or nested tags)")
                TagProcessorV0.processTags(file, new BuildVersion(version.toString()))
            }
        }

        warnings.forEach(it -> logger.warning(it.toString()))
    }

    static void restoreAll(File rootDirectory, String version) {
        rootDirectory.traverse(type: FileType.FILES) { file ->
            if (hasIfTags(file)) {
                TagProcessorV1.restoreFile(file)
            } else {
                TagProcessorV0.processTags(file, new BuildVersion(version), true)
            }
        }
    }

    /**
     * @param rootDirectory a directory to recursively run the tags processing on all files below
     * @param build what to build
     */
    @CompileStatic
    public static void processTags(File rootDirectory, VersionToBuild build) {
        // See comments on these two classes for why tags processing is skipped in these modes.
        if (build instanceof BuildGerrit || build instanceof BuildSha) {
            return
        }

        // If building main there's nothing to do, since we don't know what version we're building.
        // Note that if the source is currently set into e.g. 3.4.3 mode, then what we really want to do is to reset
        // the code to 'main'.  So there is a bug here, as that's not done anywhere.  But it's unlikely to be a bug
        // that impacts anyone: the functional tests only build master, and the perf tests only build specific versions or shas.
        if (build instanceof BuildMain) {
            return
        }

        def version = ImplementationVersion.from(((HasVersion) build).version())
        processTags(rootDirectory, version)
    }

    static void configureLogging(Logger logger) {
        var handler = new ConsoleHandler();
        handler.setFormatter(new SimpleFormatter() {
            private static final String format = '[%1$tF %1$tT] [%2$-7s] %3$s %n'

            @Override
            public String formatMessage(LogRecord record) {
                return String.format(format,
                        new Date(record.getMillis()),
                        record.getLevel().getLocalizedName(),
                        record.getMessage()
                );
            }
        });
        logger.setUseParentHandlers(false);
        logger.addHandler(handler);
    }

    static void main(String[] args) {
        configureLogging(logger)

        def cli = new CliBuilder()
        cli.with {
            r longOpt: 'restore', argName: 'r', 'Restore mode'
            d longOpt: 'directory', args: 1, argName: 'd', required: true, 'Directory to scan'
            v longOpt: 'version', args: 1, argName: 'v', required: true, 'Version'
        }
        def options = cli.parse(args)
        if (!options || !options.d || !options.v) {
            logger.severe("Not enough arguments provided")
            System.exit(-1)
        }

        if (options.r) {
            restoreAll(new File(options.d), options.v)
            return
        }

        if (options.v) {
            processTags(new File(options.d), new BuildVersion(options.v))
        } else {
            processTags(new File(options.d), new BuildMain())
        }
    }
}
