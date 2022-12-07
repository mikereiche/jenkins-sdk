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
     * @param sdkVersion (e.g. '3.2.3'). If not present (and sha is not present), it indicates to just build master.
     * @param sha        (e.g. '20e862d'). If present, it indicates to build from specific commit
     * @param imageName  the name of the docker image
     * @param onlySource whether to skip the docker build
     */
    static void build(Environment imp, String path, Optional<String> sdkVersion, Optional<String> sha, String imageName, boolean onlySource = false) {
        imp.dirAbsolute(path) {
            imp.dir('transactions-fit-performer') {
                imp.dir('performers/python') {
                    writePythonRequirementsFile(imp, sdkVersion, sha)
                    sdkVersion.ifPresent(v -> {
                        TagProcessor.processTags(new File(imp.currentDir()), ImplementationVersion.from(v), false)
                    })
                }
                if (!onlySource) {
                    imp.execute("docker build -f ./performers/python/Dockerfile -t $imageName .", false, true, true)
                }
            }
        }
    }

    private static void writePythonRequirementsFile(Environment imp, Optional<String> sdkVersion, Optional<String> sha) {
        def requirements = new File("${imp.currentDir()}/cb_requirement.txt")
        def lines = requirements.readLines()
        requirements.write("")

        for (String line : lines) {
            if ((line.contains("couchbase") && !line.contains("/")) || line.contains("github.com/couchbase/couchbase-python-client")) {
                sha.ifPresentOrElse(
                    (shaValue) -> {
                        requirements.append("git+https://github.com/couchbase/couchbase-python-client.git@${shaValue}#egg=couchbase\n")
                    },
                    () -> {
                        sdkVersion.ifPresentOrElse(
                            (versionValue) -> {
                                requirements.append("couchbase==${versionValue}\n")
                            },
                            () -> {
                                requirements.append("git+https://github.com/couchbase/couchbase-python-client.git@master#egg=couchbase\n")
                            }
                        )
                    }
                )
            } else {
                requirements.append(line + "\n")
            }
        }
    }
}
