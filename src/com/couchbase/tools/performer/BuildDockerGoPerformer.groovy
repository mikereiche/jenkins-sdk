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
                    TagProcessor.processTags(new File(imp.currentDir()), build)
                }
                var version = "master"
                if (build instanceof HasSha) {
                    version = build.sha()
                }
                else if (build instanceof HasVersion) {
                    version = "v${build.version()}"
                }
                else if (build instanceof BuildMain) {
                    version = "master"
                }
                if (!onlySource) {
                    imp.execute("docker build -f performers/go/Dockerfile --platform=linux/amd64 --build-arg SDK_VERSION=${version} -t $imageName .", false, true, true)
                }
            }
        }
    }
}
