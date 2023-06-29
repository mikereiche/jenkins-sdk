package com.couchbase.tools.performer

import com.couchbase.context.environments.Environment
import com.couchbase.tools.tags.TagProcessor
import com.couchbase.versions.ImplementationVersion
import groovy.transform.CompileStatic

class BuildDockerNodePerformer {
    private static Boolean write_couchbase = false

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

        if (couchbaseInPackageFile(lines)) {
            for (int i = 0; i < lines.size(); i++) {
                def line = lines[i]

                if (line.contains("couchbase")) {
                    if (build instanceof HasVersion) {
                        if (build instanceof HasSha) { //True if using snapshot version
                            shaFile.append(build.sha())
                        } else {
                            packageFile.append("\t\"couchbase\": \"^${build.version()}\",\n")
                        }
                    }
                    else {
                        packageFile.append(line + "\n")
                    }
                } else {
                    packageFile.append(line + "\n")
                }
            }
        }
    }

    private static boolean couchbaseInPackageFile(List<String> lines) {
        for (int i = 0; i < lines.size(); i++) {
            def line = lines[i]
            if(line.contains("couchbase")) {
                return true
            }
        }
        return false
    }
}
