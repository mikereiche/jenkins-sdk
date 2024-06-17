package com.couchbase.tools.tags

import com.couchbase.versions.ImplementationVersion
import groovy.transform.ImmutableOptions

import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.function.BiFunction
import java.util.function.Predicate
import java.util.regex.Pattern

import static java.util.Objects.requireNonNull

/**
 * Supports if/else and nested tags.
 */
class TagProcessorV1 {
    private final File file
    private final String commentPrefix
    private final String conditionalCommentPrefix
    private final String original

    // Gets appended to a line to indicate it was originally a conditional comment ("//?" or "#?")
    private final String activationMarker

    private TagProcessorV1(File file) {
        this.file = file
        this.original = Files.readString(file.toPath())
        this.commentPrefix = file.name.endsWithAny(".rb", ".py") ? "#" : "//"
        this.conditionalCommentPrefix = commentPrefix + "?"
        this.activationMarker = " " + commentPrefix + " [ACTIVATED]"
    }

    static List<String> process(File f, ImplementationVersion version) {
        return new TagProcessorV1(f).processTags(version)
    }

    String addDeactivatedPrefix(String s) {
        return commentPrefix + " [DEACTIVATED]" + s
    }

    static void restoreFile(File file) {
        TagProcessorV1 strategy = new TagProcessorV1(file)
        String restored = strategy.restore()

        if (strategy.original != restored) {
            atomicWrite(file, restored)
        }
    }

    String restore() {
        String undeactivated = original.replaceAll("(?m)^" + Pattern.quote(commentPrefix + " [DEACTIVATED]"), "")

        def list = new ArrayList()
        undeactivated.eachLine { line -> list.add(restoreActivatedCode(line)) }
        return join(list)
    }

    String join(List<String> lines) {
        def result = lines.join(System.lineSeparator())
        if (original.endsWith(System.lineSeparator())) {
            result += System.lineSeparator()
        }
        return result
    }

    static void atomicWrite(File file, String content) {
        File temp = new File(file.parentFile, file.name + ".tag_processor_temp")
        try {
            Files.writeString(temp.toPath(), content)
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally {
            temp.delete()
        }
    }

    String activateCode(String line) {
        def prefixStart = line.indexOf(conditionalCommentPrefix)
        def prefixEnd = prefixStart + conditionalCommentPrefix.length()
        return line.substring(0, prefixStart) + line.substring(prefixEnd).stripLeading() + activationMarker
    }

    String restoreActivatedCode(String line) {
        if (!line.endsWith(activationMarker)) return line

        def indexOfNonWhitespace = line.findIndexOf { !Character.isWhitespace(it.charAt(0)) }

        // put back the conditional comment prefix at the start of the line
        line = line.substring(0, indexOfNonWhitespace) + conditionalCommentPrefix + " " + line.stripLeading()

        // remove the activation marker from the end of the line
        line = line.substring(0, line.length() - activationMarker.length())

        return line
    }

    enum Operator {
        GTE(">=", (left, right) -> left >= right),
        LT("<", (left, right) -> left < right),

        private final BiFunction<Comparable, Comparable, Boolean> function
        private final String literal

        Operator(String literal, BiFunction<Comparable, Comparable, Boolean> function) {
            this.literal = literal
            this.function = function
        }

        boolean apply(Comparable left, Comparable right) { function.apply(left, right) }

        String toString() { literal }

        static Operator parse(String s) {
            requireNonNull(
                    values().find(it -> it.literal == s),
                    /Syntax error: unrecognized operator: "$s" ; expected one of ${values()}/
            )
        }
    }

    @ImmutableOptions(knownImmutableClasses = ImplementationVersion.class)
    static record Condition(Operator op, ImplementationVersion version) implements Predicate<ImplementationVersion> {
        boolean test(ImplementationVersion actualVersion) { op.apply(actualVersion, version) }

        String toString() { "$op$version" }
    }

    static Set<Condition> parseConditions(String conditions) {
        if (conditions.count("&&") > 1) {
            throw new IllegalArgumentException("Syntax error; can have at most 2 sub-conditions, but got: $conditions")
        }

        return conditions.split("&&").collect { condition ->
            def matcher = condition =~ /(?<operator>[<=>]*)(?<version>\d\S*)/
            if (!matcher.matches()) {
                throw new IllegalArgumentException(/Syntax error; invalid condition(s): "$conditions" ; expected something like: "1.2.0" or "<2.0.3" or "2.4.5&&<3.0.0"/)
            }

            def op = matcher.group("operator").with {
                it == "" ? Operator.GTE : Operator.parse(it) // default to ">=" if absent
            }
            def version = ImplementationVersion.from(matcher.group("version"))
            return new Condition(op, version)
        }
    }

    @ImmutableOptions(knownImmutableClasses = Condition.class)
    static record Tag(Type type, Set<Condition> conditions) implements Predicate<ImplementationVersion> {
        enum Type {
            IF, ELSE, END, SKIP
        }

        @Override
        boolean test(ImplementationVersion version) {
            return conditions().stream().allMatch(it -> it.test(version))
        }

        String toString() {
            "[" + type.name().toLowerCase(Locale.ROOT) + (conditions().isEmpty() ? "" : (":" + conditions.join("&&"))) + "]"
        }

        static Tag parse(String line) {
            def matcher = line =~ /\s*\[(?<tag>start|if|else|end|skip)(:(?<condition>\S*))?].*/
            if (!matcher.matches()) return null

            def tagName = matcher.group('tag').toUpperCase(Locale.ROOT)
            if (tagName == "START") tagName = "IF"
            def type = Type.valueOf(tagName)
            def conditions = matcher.group('condition')
            def tag = new Tag(type, conditions == null ? new LinkedHashSet<Condition>() : parseConditions(conditions))

            if (tag.conditions.isEmpty() && !Set.of(Type.END, Type.ELSE).contains(type)) {
                throw new IllegalArgumentException("Syntax error: missing condition for tag $tag")
            }
            if (!tag.conditions().isEmpty() && (type == Type.ELSE)) {
                throw new IllegalArgumentException("Syntax error: $type tag must not have condition.")
            }
            return tag
        }
    }

    enum Mode {
        SKIPPING, INCLUDING
    }

    @ImmutableOptions(knownImmutableClasses = Tag.class)
    static record TagStackFrame(Tag tag, Mode mode, String file, int lineNumber) {
        String location() { file + ":" + lineNumber }
    }

    List<String> processTags(ImplementationVersion version) {
        def warnings = new ArrayList()
        var outputLines = new ArrayList<String>()

        var stack = new ArrayDeque<TagStackFrame>()
        stack.push(new TagStackFrame(new Tag(Tag.Type.IF, parseConditions("0.0.0")), Mode.INCLUDING, file.absolutePath, 0))

        // First "un-skip" everything that might have been commented out by a previous run.
        def restored = restore()

        restored.eachLine { line, lineNumberZeroBased ->
            def lineNumber = lineNumberZeroBased + 1
            String location = "${file.absolutePath}:${lineNumber}"

            try {
                // Once we've seen a [skip] tag, never stop skipping!
                if (stack.peek().tag().type() == Tag.Type.SKIP) {
                    outputLines.add(addDeactivatedPrefix(line))
                    return
                }

                def isConditionalComment = line =~ /\s*${Pattern.quote(conditionalCommentPrefix)}.*/
                if (isConditionalComment) {
                    if (stack.size() == 1) {
                        throw new IllegalArgumentException("Encountered conditional comment outside of `if` or `else` block.")
                    }
                    if (stack.peek().mode() == Mode.INCLUDING) {
                        line = activateCode(line)
                    }
                } else if (line =~ /\s*${Pattern.quote(commentPrefix)}\s*\[.+]/) { // looks like might be tag
                    def tag = Tag.parse(line.substring(line.indexOf(commentPrefix) + commentPrefix.length()))
                    if (tag != null) {
                        if (tag.type() == Tag.Type.SKIP && tag.test(version)) {
                            outputLines.add(addDeactivatedPrefix(line))
                            stack.push(new TagStackFrame(tag, Mode.SKIPPING, file.absolutePath, lineNumber))
                            return
                        }

                        if (tag.type() == Tag.Type.IF) {
                            Mode newMode
                            if (stack.peek().mode() == Mode.SKIPPING) {
                                // can't un-skip -- the tag itself is being skipped too!
                                newMode = Mode.SKIPPING
                            } else {
                                newMode = tag.test(version) ? Mode.INCLUDING : Mode.SKIPPING
                            }
                            stack.push(new TagStackFrame(tag, newMode, file.absolutePath, lineNumber))
                        }

                        if (tag.type() == Tag.Type.ELSE) {
                            Mode newMode

                            def startFrame = stack.pop()
                            if (startFrame.tag().type() != Tag.Type.IF || stack.isEmpty()) {
                                throw new IllegalArgumentException("Syntax error: encountered 'else' tag without 'if' tag.")
                            }

                            if (stack.peek().mode() == Mode.SKIPPING) {
                                // we were skipping even before the "if" clause, so continue skipping
                                newMode = Mode.SKIPPING
                            } else {
                                // "else" mode is the oppose of the "if" clause mode
                                newMode = startFrame.mode() == Mode.SKIPPING ? Mode.INCLUDING : Mode.SKIPPING
                            }
                            stack.push(new TagStackFrame(tag, newMode, file.absolutePath, lineNumber))
                        }

                        if (tag.type() == Tag.Type.END) {
                            Tag startTag = stack.peek().tag()
                            if (startTag.type() != Tag.Type.IF && startTag.type() != Tag.Type.ELSE) {
                                throw new IllegalArgumentException(
                                        "Syntax error: Unbalanced tags." +
                                                " Encountered end tag, but top of tag stack was not an start/if/else tag;" +
                                                " instead was: $startTag from line ${stack.peek().lineNumber()}"
                                )
                            }
                            if (!tag.conditions().isEmpty() && tag.conditions() != startTag.conditions()) {
                                throw new IllegalArgumentException(
                                        "Unbalanced tags." +
                                                " End tag condition was specified (this is optional!), but does not match start tag condition." +
                                                " Start tag from line ${stack.peek().lineNumber()}: ${startTag} End tag on line ${lineNumber}: ${tag}"
                                )
                            }
                            // Emit it here, so it gets commented out even if it's ending a skipped section
                            outputLines.add(stack.peek().mode() == Mode.SKIPPING ? addDeactivatedPrefix(line) : line)
                            stack.pop()
                            if (stack.isEmpty()) {
                                throw new IllegalArgumentException("Unbalanced tags. End tag had no matching start tag.")
                            }
                            return
                        }
                    }
                }

                outputLines.add(stack.peek().mode() == Mode.SKIPPING ? addDeactivatedPrefix(line) : line)
                return

            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid tag at ${location} ; ${e} ; line was: \"${line}\"", e)
            } catch (Exception e) {
                throw new RuntimeException("Internal error in tag processor at ${location} ; ${e} ; line was: \"${line}\"", e)
            }
        }

        if (stack.peek().tag().type() != Tag.Type.SKIP) {
            while (stack.size() > 1) warnings.add("Unclosed tag at " + stack.pop().location())
        }

        def result = join(outputLines)

        if (original != result) {
            atomicWrite(file, result)
        }

        return warnings
    }
}
