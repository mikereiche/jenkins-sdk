package com.couchbase.tools.performer

import com.couchbase.context.environments.Environment
import com.couchbase.tools.tags.TagProcessor
import groovy.transform.CompileStatic

@CompileStatic
class BuildDockerGoPerformer {
    /**
     * @param path absolute path to above 'transactions-fit-performer'
     * @param build what to build
     */
    static void build(Environment imp, String path, VersionToBuild build, String imageName, boolean onlySource = false) {
        imp.log("Building Go ${build}")

        if (build instanceof BuildSha || build instanceof BuildShaVersion) {
            throw new RuntimeException("Building SHA not currently supported for Go")
        }
        if (build instanceof BuildGerrit) {
            throw new RuntimeException("Building Gerrit not currently supported for Go")
        }

        // Build context needs to be perf-sdk as we need the .proto files
        imp.dirAbsolute(path) {
            imp.dir('transactions-fit-performer') {
                imp.dir('performers/go') {
                    writeGoModFile(imp, build)
                    TagProcessor.processTags(new File(imp.currentDir()), build)
                }
                if (!onlySource) {
                    imp.execute("docker build -f performers/go/Dockerfile --platform=linux/amd64 -t $imageName .", false, true, true)
                }
            }
        }
    }

    static private List writeGoModFile(Environment imp, VersionToBuild build) {
        def goMod = new File("${imp.currentDir()}/go.mod")
        def lines = goMod.readLines()

        goMod.write("")

        for (int i = 0; i < lines.size(); i++) {
            def line = lines[i]

            if (line.contains("couchbase/gocb/v2") && build instanceof HasVersion) {
                goMod.append("\tgithub.com/couchbase/gocb/v2 v${build.version()}\n")
            } else {
                goMod.append(line + "\n")
            }
        }

    }
}
