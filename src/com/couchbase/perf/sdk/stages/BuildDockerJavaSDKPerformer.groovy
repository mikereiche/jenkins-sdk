package com.couchbase.perf.sdk.stages

import groovy.transform.CompileStatic
import com.couchbase.context.StageContext
import com.couchbase.context.environments.Environment
import com.couchbase.stages.Stage

@CompileStatic
class BuildDockerJavaSDKPerformer extends Stage {

    private final String sdkVersion
    final String imageName

    static String genImageName(String sdkVersion) {
        return "performer-java" + sdkVersion
    }

    BuildDockerJavaSDKPerformer(String sdkVersion) {
        this(BuildDockerJavaSDKPerformer.genImageName(sdkVersion), sdkVersion)
    }

    BuildDockerJavaSDKPerformer(String imageName, String sdkVersion) {
        this.sdkVersion = sdkVersion
        this.imageName = imageName
    }

    @Override
    String name() {
        return "Building image ${imageName}"
    }

    @Override
    void executeImpl(StageContext ctx) {
        def imp = ctx.env
        // Build context needs to be perf-sdk as we need the .proto files
        ctx.inSourceDirAbsolute {
            imp.dir('performers/java/sdk-performer-java') {
                writePomFile(imp)
            }
            imp.execute("docker build -f performers/java/Dockerfile -t $imageName .")
        }
    }

    /**
     * Updates pom.xml to build with the transaction library under test.
     */
    private List writePomFile(Environment imp) {
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
                pom.append("\t\t\t<version>${sdkVersion}</version>\n")
                replaceVersion = false
            } else if (line.contains("<artifactId>java-client</artifactId>")) {
                replaceVersion = true
                pom.append(line + "\n")
            } else {
                pom.append(line + "\n")
            }
        }
    }

    String getImageName(){
        return imageName
    }
}