package com.couchbase.tools.performer

import com.couchbase.context.environments.Environment
import com.couchbase.tools.tags.TagProcessor
import com.couchbase.versions.ImplementationVersion
import groovy.transform.CompileStatic

import java.security.cert.CertificateEncodingException
import java.util.logging.Logger

@CompileStatic
class BuildDockerPythonPerformer {
    /**
     * @param path absolute path to above 'transactions-fit-performer'
     * @param sdkVersion (e.g. '3.2.3'). If not present, it indicates to just build master.
     */
    private static Logger logger = Logger.getLogger("")

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
