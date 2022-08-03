package com.couchbase.tools.performer

import com.couchbase.context.environments.Environment
import com.couchbase.tools.tags.TagProcessor
import com.couchbase.versions.ImplementationVersion
import groovy.transform.CompileStatic

/**
 * Building the JVM performers requires some pom.xml manipulation to pull in the correct transitive core-io version.
 */
@CompileStatic
class BuildDockerJVMPerformer {
    /**
     * @param path absolute path to above 'couchbase-jvm-clients'
     * @param client 'java', 'kotlin'
     * @param sdkVersion '3.2.3'
     */
    static void build(Environment imp, String path, String client, String sdkVersion, String imageName, boolean onlySource = false) {
        imp.dirAbsolute(path) {
            imp.dir("couchbase-jvm-clients") {
                writeParentPomFile(imp)
                imp.dir("${client}-fit-performer") {
                    writePerformerPomFile(imp, client + "-client", sdkVersion)
                    TagProcessor.processTags(new File(imp.currentDir() + "/src"), ImplementationVersion.from(sdkVersion), false)
                }
            }
            if (!onlySource) {
                imp.execute("docker build -f couchbase-jvm-clients/${client}-fit-performer/Dockerfile -t ${imageName} .",
                    false, true)
            }
        }
    }

    /**
     * Updates couchbase-jvm-client/pom.xml.
     */
    private static List writeParentPomFile(Environment imp) {
        def pom = new File("${imp.currentDir()}/pom.xml")
        imp.log("Updating ${pom.getAbsolutePath()}")
        def lines = pom.readLines()
        def out = new ArrayList<String>()
        def commentFromLine = -1
        def commentUntilLine = -1

        for (int i = 0; i < lines.size(); i++) {
            def line = lines[i]
            boolean alreadyCommented = line.size() > 0 && line.startsWith("<!--")

            // Have to comment out the core-io dependency as it overrides the java-client's transitive dependency.
            // E.g. we end up with java-client 3.0.0 and core-io 2.3.3-SNAPSHOT.  And it's challenging to programmatically
            // select the correct core-io build for a java-client build, when working with SNAPSHOTs.
            // This assumes the core-io dependency is the first listed.
            if (line.contains("dependencies") && lines.get(i + 3).contains("core-io")) {
                commentFromLine = i + 1
                commentUntilLine = i + 5
            }

            // Have to comment the other modules out as they don't compile when the core-io dependency is removed
            def commentLine = (line.contains("<module>") && !line.contains("fit-performer"))
                    || (commentFromLine != -1 && (i >= commentFromLine && i <= commentUntilLine))

            if (commentLine && !alreadyCommented) {
                out.add("<!-- " + line + " !-->")
            }
            else {
                out.add(line)
            }
        }

        def w = pom.newWriter()
        w << out.join(System.lineSeparator())
        w.close()
    }

    /**
     * Updates the performer's pom.xml to build with the SDK under test.
     */
    private static List writePerformerPomFile(Environment imp, String lookingFor, String versionToInsert) {
        def pom = new File("${imp.currentDir()}/pom.xml")
        imp.log("Updating ${pom.getAbsolutePath()}")
        def lines = pom.readLines()
        def replaceVersion = false

        pom.write("")

        for (int i = 0; i < lines.size(); i++) {
            def line = lines[i]

            if (replaceVersion) {
                assert (line.contains("<version>"))
                pom.append("\t\t\t<version>${versionToInsert}</version>\n")
                replaceVersion = false
            } else if (line.contains("<artifactId>${lookingFor}</artifactId>")) {
                replaceVersion = true
                pom.append(line + "\n")
            } else {
                pom.append(line + "\n")
            }
        }
    }
}