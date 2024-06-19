package com.couchbase.tools.tags

import com.couchbase.versions.ImplementationVersion
import org.junit.jupiter.api.Test

import java.nio.file.Files

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertThrows

class TagProcessorV1Test {

    @Test
    void doesNotAddFinalNewlineIfNotPresentInOriginal() {
        def input = new Input("""
            #[if:2.0.0]
            test
            #[end]""")

        input.check("1.0.0", """
            # [DEACTIVATED]#[if:2.0.0]
            # [DEACTIVATED]test
            # [DEACTIVATED]#[end]""")
    }

    @Test
    void simpleIfPreservesDescriptionAndWhitespace() {
        // include some different leading whitespace to ensure it's preserved
        def input = new Input("""
            test
              #[if:2.0.0]  description
             test
            #  [end]description
            test
            """)

        input.check("2.0.0", """
            test
              #[if:2.0.0]  description
             test
            #  [end]description
            test
            """)

        input.check("1.0.0", """
            test
            # [DEACTIVATED]  #[if:2.0.0]  description
            # [DEACTIVATED] test
            # [DEACTIVATED]#  [end]description
            test
            """)
    }

    @Test
    void supportsLessThan() {
        def input = new Input("""
            # [if:<2.0.0]
            test
            # [end]
            """)

        input.check("1.0.0", """
            # [if:<2.0.0]
            test
            # [end]
            """)

        input.check("2.0.0", """
            # [DEACTIVATED]# [if:<2.0.0]
            # [DEACTIVATED]test
            # [DEACTIVATED]# [end]
            """)
    }

    @Test
    void recognizesStartAsAnAliasForIf() {
        def input = new Input("""
            # [start:2.0.0]
            # [end:2.0.0]
            """)

        input.check("1.0.0", """
            # [DEACTIVATED]# [start:2.0.0]
            # [DEACTIVATED]# [end:2.0.0]
            """)
    }

    @Test
    void ignoresUnrecognizedTag() {
        new Input("# [bogus]").check("1.0.0", "# [bogus]")
        new Input("# [bogus:2.0.0]").check("1.0.0", "# [bogus:2.0.0]")
    }

    @Test
    void notAConditionalCommentIfPrecededByWhitespace() {
        new Input("# ?").check("1.0.0", "# ?")
    }

    @Test
    void notATagIfPrecededByGarbage() {
        new Input("# bogus [if:2.0.0]").check("1.0.0", "# bogus [if:2.0.0]")
    }

    @Test
    void supportsNestedTags() {
        def input = new Input("""
            # [if:1.0.0]
            test
            # [if:2.0.0]
            test
            # [end]
            test
            # [end]
            """)

        input.check("1.0.0", """
            # [if:1.0.0]
            test
            # [DEACTIVATED]# [if:2.0.0]
            # [DEACTIVATED]test
            # [DEACTIVATED]# [end]
            test
            # [end]
            """)

        input.check("2.0.0", """
            # [if:1.0.0]
            test
            # [if:2.0.0]
            test
            # [end]
            test
            # [end]
            """)
    }

    @Test
    void innerIfIsAlwaysDeactivatedIfOuterIsDeactivated() {
        def input = new Input("""
            # [if:2.0.0]
            test
            # [if:1.0.0] should be deactivated because enclosing tag is deactivated
            test
            # [end]
            test
            # [end]
            """)

        input.check("1.0.0", """
            # [DEACTIVATED]# [if:2.0.0]
            # [DEACTIVATED]test
            # [DEACTIVATED]# [if:1.0.0] should be deactivated because enclosing tag is deactivated
            # [DEACTIVATED]test
            # [DEACTIVATED]# [end]
            # [DEACTIVATED]test
            # [DEACTIVATED]# [end]
            """)
    }

    @Test
    void elseIsAlwaysDeactivatedIfOuterIsDeactivated() {
        def input = new Input("""
            # [if:2.0.0]
            # [if:3.0.0]
            # [else]
            should be deactivated because outer "if:2.0.0" tag is deactivated
            # [end]
            # [end]
            """)

        input.check("1.0.0", """
            # [DEACTIVATED]# [if:2.0.0]
            # [DEACTIVATED]# [if:3.0.0]
            # [DEACTIVATED]# [else]
            # [DEACTIVATED]should be deactivated because outer "if:2.0.0" tag is deactivated
            # [DEACTIVATED]# [end]
            # [DEACTIVATED]# [end]
            """)
    }

    @Test
    void skipBypassesSubsequentTags() {
        def input = new Input("""
            test
            # [skip:1.0.0]
            # [if:invalidVersion] would be a syntax error if not skipped
            test
            """)

        input.check("1.0.0", """
            test
            # [DEACTIVATED]# [skip:1.0.0]
            # [DEACTIVATED]# [if:invalidVersion] would be a syntax error if not skipped
            # [DEACTIVATED]test
            """)
    }

    @Test
    void unmatchedEndTagIsFatalError() {
        new Input("# [end]").assertThrows("1.0.0", IllegalArgumentException.class)
    }

    @Test
    void unmatchedElseTagIsFatalError() {
        new Input("# [else]").assertThrows("1.0.0", IllegalArgumentException.class)
        new Input("""
            # [if:1.0.0]
            # [else]
            # [else]
            # [end]
            """).assertThrows("1.0.0", IllegalArgumentException.class)
    }

    @Test
    void orphanConditionalCommentIsFatalError() {
        new Input("#? foo").assertThrows("1.0.0", IllegalArgumentException.class)
    }

    @Test
    void tooManyConditionsIsFatalError() {
        new Input("# [if:1.0.0&&<2.0.0&&3.0.0]")
                .assertThrows("1.0.0", IllegalArgumentException.class)
    }

    @Test
    void malformedVersionIsFatalError() {
        new Input("# [if:malformedVersion]")
                .assertThrows("1.0.0", IllegalArgumentException.class)
    }

    @Test
    void canOmitWhitespaceAfterConditionalCommentMarker() {
        def input = new Input("""
            # [if:1.0.0]
                  #?foo
            # [end]
            """)

        // Skip the restoration check, because the processor doesn't know
        // how much whitespace was after #?
        // TODO: encode the whitespace into the activation marker, like maybe
        // between the comment prefix and the square bracket.
        input.checkButSkipRestoration("1.0.0", """
            # [if:1.0.0]
                  foo # [ACTIVATED]
            # [end]
            """)
    }

    @Test
    void activatesConditionalComments() {
        def input = new Input("""
            # [if:2.0.0]
            #? foo
            test
            # [else]
            #? bar
            test
            # [end]
            """)

        input.check("1.0.0", """
            # [DEACTIVATED]# [if:2.0.0]
            # [DEACTIVATED]#? foo
            # [DEACTIVATED]test
            # [else]
            bar # [ACTIVATED]
            test
            # [end]
            """)

        input.check("2.0.0", """
            # [if:2.0.0]
            foo # [ACTIVATED]
            test
            # [DEACTIVATED]# [else]
            # [DEACTIVATED]#? bar
            # [DEACTIVATED]test
            # [DEACTIVATED]# [end]
            """)
    }

    @Test
    void supportsSimpleCompoundConditions() {
        def input = new Input("""
            # [if:2.0.0&&<3.0.0]
            foo
            # [else]
            bar
            # [end]
            """)

        input.check("2.0.0", """
            # [if:2.0.0&&<3.0.0]
            foo
            # [DEACTIVATED]# [else]
            # [DEACTIVATED]bar
            # [DEACTIVATED]# [end]
            """)

        input.check("3.0.0", """
            # [DEACTIVATED]# [if:2.0.0&&<3.0.0]
            # [DEACTIVATED]foo
            # [else]
            bar
            # [end]
            """)

        input.check("1.0.0", """
            # [DEACTIVATED]# [if:2.0.0&&<3.0.0]
            # [DEACTIVATED]foo
            # [else]
            bar
            # [end]
            """)
    }

    static record Input(String original) {

        <T extends Throwable> T assertThrows(String version, Class<T> exceptionClass) {
            // Try with both kinds of tags, to make sure we didn't
            // accidentally hard-code one type of tag in the processor.
            assertThrows(exceptionClass, () -> doCheck(version, null, "//", true))
            return assertThrows(exceptionClass, () -> doCheck(version, null, "#", true))
        }

        void checkButSkipRestoration(String version, String expected) {
            doCheck(version, expected, "#", false)
            doCheck(version, expected, "//", false)
        }

        void check(String version, String expected) {
            // Try with both kinds of tags, to make sure we didn't
            // accidentally hard-code one type of tag in the processor.
            doCheck(version, expected, "#", true)
            doCheck(version, expected, "//", true)
        }

        private void doCheck(String version, String expected, String commentMarker, boolean checkRestore) {
            String extension = commentMarker == "#" ? ".rb" : ".java" // cheating for sure
            String original = original.stripIndent().replace("#", commentMarker)
            if (expected != null) {
                expected = expected.replace("#", commentMarker)
            }
            File f = File.createTempFile("tag-processor-test-", extension);
            try {
                Files.writeString(f.toPath(), original);
                TagProcessorV1.process(f, ImplementationVersion.from(version));

                if (expected != null) {
                    expected = expected.stripIndent()

                    String processed = Files.readString(f.toPath());
                    assertEquals(expected, processed, "tag processing");

                    if (checkRestore) {
                        TagProcessorV1.restoreFile(f);
                        assertEquals(original, Files.readString(f.toPath()), "restoration");
                    }
                }

            } finally {
                f.delete();
            }
        }
    }
}
