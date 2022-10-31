package com.couchbase.tools.performer

import com.couchbase.context.environments.Environment
import com.couchbase.tools.tags.TagProcessor
import com.couchbase.versions.ImplementationVersion
import groovy.transform.CompileStatic

@CompileStatic
class BuildDockerGoPerformer {
    /**
     * @param path absolute path to above 'transactions-fit-performer'
     * @param sdkVersion '3.2.3'.  If not present, it indicates to just build master.
     */
    static void build(Environment imp, String path, Optional<String> sdkVersion, String imageName, boolean onlySource = false) {
        // Build context needs to be perf-sdk as we need the .proto files
        imp.dirAbsolute(path) {
            imp.dir('transactions-fit-performer') {
                imp.dir('performers/go') {
                    writeGoModFile(imp, sdkVersion)
                    sdkVersion.ifPresent(v -> {
                        TagProcessor.processTags(new File(imp.currentDir()), ImplementationVersion.from(v), false)
                    })
                }
                if (!onlySource) {
                    imp.execute("docker build -f performers/go/Dockerfile -t $imageName .", false, true, true)
                }
            }
        }
    }

    static private List writeGoModFile(Environment imp, Optional<String> sdkVersion) {
        def goMod = new File("${imp.currentDir()}/go.mod")
        def lines = goMod.readLines()

        goMod.write("")

        for (int i = 0; i < lines.size(); i++) {
            def line = lines[i]

            if (line.contains("couchbase/gocb/v2") && sdkVersion.isPresent()) {
                goMod.append("\tgithub.com/couchbase/gocb/v2 v${sdkVersion}\n")
            } else {
                goMod.append(line + "\n")
            }
        }

    }
}