package com.couchbase.tools.performer

import com.couchbase.context.environments.Environment
import com.couchbase.tools.tags.TagProcessor
import com.couchbase.versions.ImplementationVersion
import groovy.transform.CompileStatic

import java.util.logging.Logger

@CompileStatic
class BuildDockerRubyPerformer {
    private static Logger logger = Logger.getLogger("")

    /**
     * @param imp        the build environment
     * @param path       absolute path to above 'transactions-fit-performer'
     * @param sdkVersion (e.g. '3.2.3'). If not present (and sha is not present), it indicates to just build master.
     * @param sha        (e.g. '20e862d'). If present, it indicates to build from specific commit
     * @param imageName  the name of the docker image
     * @param onlySource whether to skip the docker build
     */
    static void build(Environment imp, String path, Optional<String> sdkVersion, Optional<String> sha, String imageName, boolean onlySource = false) {
        imp.dirAbsolute(path) {
            imp.dir('transactions-fit-performer') {
                imp.dir('performers/ruby') {
                    sdkVersion.ifPresent(v -> {
                        TagProcessor.processTags(new File(imp.currentDir()), ImplementationVersion.from(v), false)
                    })
                }

                def buildArgs = "SDK_BRANCH=main"
                if (sha.isPresent()) {
                    buildArgs = "SDK_REF=${sha.get()}"
                } else if (sdkVersion.isPresent()) {
                    buildArgs = "SDK_VERSION=${sdkVersion.get()}"
                }

                if (!onlySource) {
                    imp.execute("docker build -f performers/ruby/Dockerfile --build-arg $buildArgs -t $imageName .", false, true, true)
                }
            }
        }
    }
}
