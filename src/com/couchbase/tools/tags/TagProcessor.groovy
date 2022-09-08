package com.couchbase.tools.tags


import com.couchbase.versions.ImplementationVersion
import groovy.cli.picocli.CliBuilder
import groovy.transform.CompileStatic

import java.util.logging.ConsoleHandler
import java.util.logging.LogRecord
import java.util.logging.Logger
import java.util.logging.SimpleFormatter

/**
 * We need to build the performers against a wide range of SDKs.  It's possible to handle some of this with reflection,
 * but not in all languages, and it gets messy.
 *
 * So, we now support a tagging system, allowing blocks of code to be commented or not, based on the SDK version being
 * compiled.
 *
 * An example from the Java performer, where an expiry() overload was added in version 3.0.7:
 *
 * // [start:3.0.7]
 * out.expiry(Instant.ofEpochSecond(opts.getExpiry().getAbsoluteEpochSecs()));
 * // [end:3.0.7]
 * // [start:<3.0.7]
 * throw new UnsupportedOperationException("This SDK version does not support this form of expiry");
 * // [end:<3.0.7]
 *
 * So "3.0.7" should be read as ">=3.0.7"
 *
 * Other supported syntax:
 * // [start:3.0.7&&<3.1.0]
 * // [end:3.0.7&&<3.1.0]
 *
 * // [skip:<3.1.0]
 *
 * It's important to understand that the parser is _very_ dumb.  It doesn't track state, it doesn't know it's inside a
 * [start] block, it can't handle nesting.  It just inserts comment markers on or immediately after the tag.
 *
 * This tool is available in CLI form so it can also be used by the FIT CI jobs.
 */
class TagProcessor {
    private static Logger logger = Logger.getLogger("")

    @CompileStatic
    static boolean match(String s, ImplementationVersion sdkVersion) {
        boolean isLessThan = s.contains("<")
        String versionRaw
        if (isLessThan) {
            versionRaw = s.split("<")[1].split("]")[0]
        } else {
            versionRaw = s.split(":")[1].split("]")[0]
        }
        def version = ImplementationVersion.from(versionRaw)
        return (isLessThan ? (sdkVersion.isBelow(version)) : (sdkVersion.isAbove(version) || sdkVersion == version))
    }

    @CompileStatic
    static boolean isMatch(String line, ImplementationVersion sdkVersion) {
        if (line.contains("&&")) {
            var split = line.split("&&")
            return match(split[0], sdkVersion) && match(split[1], sdkVersion)
        }

        return match(line, sdkVersion)
    }

    @CompileStatic
    public static void processTags(File curPath, ImplementationVersion sdkVersion, boolean restoreMode) {
        curPath.listFiles().each {  File file ->

            if (file.isFile()) {
                // logger.info("Inspecting file ${file.getAbsolutePath()}")

                var out = new ArrayList<>()
                var lines = file.readLines()
                boolean needsOverwriting = false
                boolean skipMode = false

                for (int i = 0; i < lines.size(); i++) {
                    var line = lines.get(i)
                    boolean isStart = line.contains("// [start:")
                    boolean isEnd = line.contains("// [end:")
                    boolean isSkip = line.contains("// [skip:")
                    boolean isSkipped = line.startsWith("// [skipped] ")

                    if (skipMode) {
                        needsOverwriting = true
                        if (!isSkipped) {
                            out.add("// [skipped] " + line)
                        }
                        else {
                            // Line is already skipped, can just add it
                            out.add(line)
                        }
                    } else {
                        if (isSkipped) {
                            // Remove "// [skipped]
                            line = line.substring(13)
                            needsOverwriting = true
                        }

                        if (isStart || isEnd || isSkip) {
                            boolean isLastLine = i == lines.size() - 1
                            boolean match = isMatch(line, sdkVersion)

                            if (isStart || isEnd) {
                                boolean include = restoreMode || match
                                String commentMarker
                                if (isStart) {
                                    commentMarker = "/*"
                                } else {
                                    commentMarker = "*/"
                                }

                                out.add(line)
                                boolean includedAlready = isLastLine || !lines.get(i + 1).trim().startsWith(commentMarker)
                                // logger.info("May need to modify ${file.getAbsolutePath()} ${versionRaw} include=${include} includedAlready=${includedAlready}")
                                if (include && !includedAlready) {
                                    // Skip over the /*, e.g. don't include it in the output
                                    needsOverwriting = true
                                    i += 1
                                }
                                if (!include && includedAlready) {
                                    needsOverwriting = true
                                    out.add(commentMarker)
                                }
                            } else { // isSkip
                                skipMode = !restoreMode && match
                                out.add(line)
                            }
                        } else {
                            out.add(line)
                        }
                    }
                }

                if (needsOverwriting) {
                    boolean fileEndedWithNewLine = lines.get(lines.size() - 1).isBlank()
                    // logger.info("Modifying file ${file.getAbsolutePath()}")
                    def w = file.newWriter()
                    w << out.join(System.lineSeparator()) + (fileEndedWithNewLine ? System.getProperty("line.separator") : "")
                    w.close()

                }
            }
            else {
                processTags(file, sdkVersion, restoreMode)
            }
        }
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
            r longOpt: 'restore', argName: 'd', 'Restore mode'
            d longOpt: 'directory', args: 1, argName: 'd', required: true, 'Directory to scan'
            v longOpt: 'version', args: 1, argName: 'v', required: true, 'Version'
        }
        def options = cli.parse(args)
        if (!options || !options.d || !options.v) {
            logger.severe("Not enough arguments provided")
            System.exit(-1)
        }

        processTags(new File(options.d), ImplementationVersion.from(options.v), options.r)
    }
}