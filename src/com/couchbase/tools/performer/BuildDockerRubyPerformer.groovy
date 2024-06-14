package com.couchbase.tools.performer

import com.couchbase.context.environments.Environment
import com.couchbase.tools.tags.TagProcessor
import com.couchbase.versions.ImplementationVersion
import groovy.transform.CompileStatic

import java.util.logging.Logger

@CompileStatic
class BuildDockerRubyPerformer {
    /**
     * @param imp        the build environment
     * @param path       absolute path to above 'transactions-fit-performer'
     * @param build      what to build
     * @param imageName  the name of the docker image
     * @param onlySource whether to skip the docker build
     */
    static void build(Environment imp, String path, VersionToBuild build, String imageName, boolean onlySource = false, Map<String, String> dockerBuildArgs = [:]) {
        imp.log("Building Ruby ${build}")

        if (build instanceof BuildGerrit) {
            throw new RuntimeException("Building Gerrit not currently supported for Ruby")
        }

        imp.dirAbsolute(path) {
            imp.dir('transactions-fit-performer') {
                imp.dir('performers/ruby') {
                    TagProcessor.processTags(new File(imp.currentDir()), build)
                }

                def dockerfile = "snapshot.Dockerfile"

                if (build instanceof HasSha) {
                    dockerBuildArgs.put("SDK_REF", build.sha().toString())
                }
                else if (build instanceof HasVersion) {
                    dockerBuildArgs.put("SDK_VERSION", build.version().toString())
                    if (build.implementationVersion().isBelow(ImplementationVersion.from("3.5.0"))) {
                        dockerBuildArgs.put("SDK_GEM_SOURCE", "https://packages.couchbase.com/clients/ruby/sdk-${build.version()}/couchbase-${build.version()}-x86_64-linux-3.2.0.gem".toString())
                    } else {
                        dockerBuildArgs.put("SDK_GEM_SOURCE", "https://packages.couchbase.com/clients/ruby/sdk-${build.version()}/couchbase-${build.version()}-x86_64-linux.gem".toString())
                    }
                    dockerfile = "release.Dockerfile"
                }
                else if (build instanceof BuildMain) {
                    dockerBuildArgs.put("SDK_BRANCH", "main")
                }

                def serializedBuildArgs = dockerBuildArgs.collect((k, v) -> "--build-arg $k=$v").join(" ")

                if (!onlySource) {
                    imp.execute("docker build -f performers/ruby/dockerfiles/$dockerfile $serializedBuildArgs -t $imageName .", false, true, true)
                }
            }
        }
    }
}
