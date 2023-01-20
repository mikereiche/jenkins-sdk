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
     * @param sdkVersion (e.g. '1.0.0'). If not present (and sha is not present), it indicates to just build main.
     * @param sha        (e.g. '20e862d'). If present, it indicates to build from specific commit
     * @param imageName  the name of the docker image
     * @param onlySource whether to skip the docker build
     */
    static void build(Environment imp, String path, Optional<String> sdkVersion, Optional<String> sha, String imageName, boolean onlySource = false) {
        imp.dirAbsolute(path) {
            imp.dir('transactions-fit-performer') {
                imp.dir('performers/cpp') {
                    sdkVersion.ifPresent(v -> {
                        TagProcessor.processTags(new File(imp.currentDir()), ImplementationVersion.from(v), false)
                    })
                }
                if (!onlySource) {
                    String branch = getSdkBranch(sdkVersion, sha);
                    String cmd = "docker build -f ./performers/cpp/Dockerfile -t $imageName --build-arg SDK_BRANCH=$branch  ."
                    imp.execute(cmd, false, true, true)
                }
            }
        }
    }

    private static String getSdkBranch(Optional<String> sdkVersion, Optional<String> sha) {
        String res = "main"
        sha.ifPresentOrElse(
            (shaValue) -> {
                res = shaValue
            },
            () -> {
                sdkVersion.ifPresent(
                    (versionValue) -> {
                        res = "tags/${versionValue}"
                    }
                )
            }
        )
        return res
    }
}
