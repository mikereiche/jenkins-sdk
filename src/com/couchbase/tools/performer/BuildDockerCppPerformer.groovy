package com.couchbase.tools.performer

import com.couchbase.context.environments.Environment
import com.couchbase.tools.tags.TagProcessor
import com.couchbase.versions.ImplementationVersion
import groovy.transform.CompileStatic

import java.util.logging.Logger

@CompileStatic
class BuildDockerCppPerformer {
    private static Logger logger = Logger.getLogger("")

    /**
     * @param imp        the build environment
     * @param path       absolute path to above 'transactions-fit-performer'
     * @param build      what to build
     * @param imageName  the name of the docker image
     * @param onlySource whether to skip the docker build
     */
    static void build(Environment imp, String path, VersionToBuild build, String imageName, boolean onlySource = false) {
        if (build instanceof BuildGerrit) {
            throw new RuntimeException("Building Gerrit not currently supported for C++")
        }

        imp.dirAbsolute(path) {
            imp.dir('transactions-fit-performer') {
                imp.dir('performers/cpp') {
                    TagProcessor.processTags(new File(imp.currentDir()), build)
                }
                if (!onlySource) {
                    String branch = getSdkBranch(build)
                    String cmd = "docker build -f ./performers/cpp/Dockerfile -t $imageName --build-arg SDK_BRANCH=$branch  ."
                    imp.execute(cmd, false, true, true)
                }
            }
        }
    }

    private static String getSdkBranch(VersionToBuild build) {
        String res = "main"
        if (build instanceof HasSha) {
            res = build.sha()
        }
        else if (build instanceof HasVersion) {
            res = "tags/${build.version()}"
        }
        return res
    }
}
