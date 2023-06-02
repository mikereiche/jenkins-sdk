package com.couchbase.tools.performer

import com.couchbase.context.environments.Environment
import com.couchbase.tools.tags.TagProcessor
import com.couchbase.versions.ImplementationVersion
import groovy.transform.CompileStatic

import java.security.cert.CertificateEncodingException
import java.util.logging.Logger

@CompileStatic
class BuildDockerPythonPerformer {
    private static Logger logger = Logger.getLogger("")

    /**
     * @param imp        the build environment
     * @param path       absolute path to above 'transactions-fit-performer'
     * @param build what to build
     * @param imageName  the name of the docker image
     * @param onlySource whether to skip the docker build
     */
    static void build(Environment imp, String path, VersionToBuild build, String imageName, boolean onlySource = false) {
        imp.log("Building Python ${build}")

        if (build instanceof BuildGerrit) {
            throw new RuntimeException("Building Gerrit not currently supported for Python")
        }

        imp.dirAbsolute(path) {
            imp.dir('transactions-fit-performer') {
                imp.dir('performers/python') {
                    writePythonRequirementsFile(imp, build)
                    TagProcessor.processTags(new File(imp.currentDir()), build)
                }
                if (!onlySource) {
                    imp.execute("docker build -f ./performers/python/Dockerfile -t $imageName .", false, true, true)
                }
            }
        }
    }

    private static void writePythonRequirementsFile(Environment imp, VersionToBuild build) {
        def requirements = new File("${imp.currentDir()}/cb_requirement.txt")
        def lines = requirements.readLines()
        requirements.write("")

        for (String line : lines) {
            if ((line.contains("couchbase") && !line.contains("/")) || line.contains("github.com/couchbase/couchbase-python-client")) {
                if (build instanceof HasSha) {
                    requirements.append("git+https://github.com/couchbase/couchbase-python-client.git@${build.sha()}#egg=couchbase\n")
                }
                else if (build instanceof HasVersion) {
                    requirements.append("couchbase==${build.version()}\n")
                }
                else if (build instanceof BuildMain) {
                    requirements.append("git+https://github.com/couchbase/couchbase-python-client.git@master#egg=couchbase\n")
                }
            } else {
                requirements.append(line + "\n")
            }
        }
    }
}
