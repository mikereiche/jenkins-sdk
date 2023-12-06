package com.couchbase.tools.performer

import com.couchbase.context.environments.Environment
import com.couchbase.tools.tags.TagProcessor
import groovy.transform.CompileStatic

class BuildDockerNodePerformer {
    private static String master = "master"

    @CompileStatic
    static void build(Environment imp, String path, VersionToBuild build, String imageName, boolean onlySource = false) {
        imp.log("Building Node ${build}")

        if (build instanceof BuildGerrit) {
            throw new RuntimeException("Building Gerrit not currently supported for Node")
        }

        imp.dirAbsolute(path) {
            imp.dir('transactions-fit-performer') {
                imp.dir("performers/node") {
                    writePackageFile(imp, build)
                    TagProcessor.processTags(new File(imp.currentDir()), build)
                }
                if (!onlySource) {
                    imp.log("building docker container")
                    imp.execute("docker build -f performers/node/Dockerfile -t $imageName .", false, true, true)
                }
            }
        }
    }

    @CompileStatic
    private static void writePackageFile(Environment imp, VersionToBuild build) {
        def packageFile = new File("${imp.currentDir()}/package.json")
        def shaFile = new File("${imp.currentDir()}/sha.txt")
        def lines = packageFile.readLines()
        packageFile.write("")
        shaFile.write("")

        for (String line : lines) {
            if (line.contains("couchbase")) {
                if (build instanceof BuildMain) {
                    shaFile.append(master)
                } else if (build instanceof HasSha) {
                    shaFile.append(build.sha())
                } else if (build instanceof HasVersion) {
                    packageFile.append("\t\"couchbase\": \"^${build.version()}\",\n")
                } else {
                    packageFile.append(line + "\n")
                }
            } else {
                packageFile.append(line + "\n")
            }
        }
    }
}
