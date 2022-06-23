package com.couchbase.perf.transactions.stages

import groovy.transform.CompileStatic
import com.couchbase.context.StageContext
import com.couchbase.context.environments.Environment
import com.couchbase.stages.Stage

@CompileStatic
class BuildDockerJavaFITPerformer extends Stage {

    private final String transactionVersion
    final String imageName

    static String genImageName(String transactionVersion) {
        return "transactions-fit-performer-java-" + transactionVersion
    }

    BuildDockerJavaFITPerformer(String transactionVersion) {
        this(BuildDockerJavaFITPerformer.genImageName(transactionVersion),
                transactionVersion)
    }

    BuildDockerJavaFITPerformer(String imageName, String transactionVersion) {
        this.imageName = imageName
        this.transactionVersion = transactionVersion
    }

    @Override
    String name() {
        return "Building image ${imageName}"
    }

    @Override
    void executeImpl(StageContext ctx) {
        def imp = ctx.env
        imp.tempDir() {
            // Build context needs to be transactions-fit-performer as we need the .proto files
            ctx.inSourceDir {
                imp.dir('performers/java') {
                    writePomFile(imp)
                }
                imp.execute("docker build -f performers/java/Dockerfile -t $imageName .")
            }
        }
    }

    /**
     * Updates pom.xml to build with the transaction library under test.
     */
    private List writePomFile(Environment imp) {
        imp.dir("txn-performer-java") {
            /*
                <dependency>
                  <groupId>com.couchbase.client</groupId>
                  <artifactId>couchbase-transactions</artifactId>
                  <version>1.1.6</version>
                </dependency>
             */
            def pom = new File("${imp.currentDir()}/pom.xml")
            def lines = pom.readLines()
            def replaceVersion = false

            pom.write("")

            for (int i = 0; i < lines.size(); i++) {
                def line = lines[i]

                if (replaceVersion) {
                    assert (line.contains("<version>"))
                    pom.append("<version>${transactionVersion}</version>\n")
                    replaceVersion = false
                } else if (line.contains("<artifactId>couchbase-transactions</artifactId>")) {
                    replaceVersion = true
                    pom.append(line + "\n")
                } else {
                    pom.append(line + "\n")
                }
            }
        }
    }
}