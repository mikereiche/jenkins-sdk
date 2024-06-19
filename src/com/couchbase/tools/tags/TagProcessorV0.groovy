package com.couchbase.tools.tags

import com.couchbase.tools.performer.*
import com.couchbase.versions.ImplementationVersion
import groovy.transform.CompileStatic

class TagProcessorV0 {

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
    static boolean isMatch(String line, VersionToBuild build) {
        if (build instanceof BuildMain) return false

        ImplementationVersion sdkVersion = (build as HasVersion).implementationVersion() as ImplementationVersion

        if (line.contains("&&")) {
            var split = line.split("&&")
            return match(split[0], sdkVersion) && match(split[1], sdkVersion)
        }

        return match(line, sdkVersion)
    }


    @CompileStatic
    public static void processTags(File file, VersionToBuild build, boolean restoreMode = false) {
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


        boolean isPython = file.toString().endsWith(".py")
        boolean isRuby = file.toString().endsWith(".rb")

        var commentMarker = "//"
        if (isPython || isRuby) {
            commentMarker = "#"
        }

        var out = new ArrayList<>()
        var lines = file.readLines()
        boolean needsOverwriting = false
        boolean skipMode = false

        for (int i = 0; i < lines.size(); i++) {
            var line = lines.get(i)
            boolean isStart = line.contains(commentMarker + " [start:")
            boolean isEnd = line.contains(commentMarker + " [end:")
            boolean isSkip = line.contains(commentMarker + " [skip:")
            boolean isSkipped = line.startsWith(commentMarker + " [skipped]")

            if (skipMode) {
                needsOverwriting = true
                if (!isSkipped) {
                    out.add(commentMarker + " [skipped] " + line)
                } else {
                    // Line is already skipped, can just add it
                    out.add(line)
                }
            } else {
                if (isSkipped) {
                    // Remove "// [skipped]
                    line = line.substring(commentMarker.length() + 10)
                    needsOverwriting = true
                }

                if (isStart || isEnd || isSkip) {
                    boolean isLastLine = i == lines.size() - 1
                    boolean match = isMatch(line, build)

                    if (isStart || isEnd) {
                        boolean include = restoreMode || match
                        String marker
                        if (isPython) {
                            var leadingWhitespaceLength = line.length() - line.stripLeading().length()
                            marker = " ".repeat(leadingWhitespaceLength) + "'''"
                        } else if (isRuby) {
                            if (isStart) {
                                marker = "=begin"
                            } else {
                                marker = "=end"
                            }
                        } else {
                            if (isStart) {
                                marker = "/*"
                            } else {
                                marker = "*/"
                            }
                        }

                        out.add(line)
                        // Trimming the marker for the startsWith() check because in Python it needs to have leading whitespace
                        boolean includedAlready = isLastLine || !lines.get(i + 1).trim().startsWith(marker.trim())
                        // logger.info("May need to modify ${file.getAbsolutePath()} ${versionRaw} include=${include} includedAlready=${includedAlready}")
                        if (include && !includedAlready) {
                            // Skip over the /*, e.g. don't include it in the output
                            needsOverwriting = true
                            i += 1
                        }
                        if (!include && includedAlready) {
                            needsOverwriting = true
                            out.add(marker)
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
}
