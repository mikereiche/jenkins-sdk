package com.couchbase.tools.performer

import com.couchbase.context.environments.Environment
import com.couchbase.perf.shared.config.PerfConfig
import com.couchbase.tools.tags.TagProcessor
import com.couchbase.versions.CppVersions
import com.couchbase.versions.DotNetVersions
import com.couchbase.versions.GoVersions
import com.couchbase.versions.NodeVersions
import com.couchbase.versions.PythonVersions
import com.couchbase.versions.Versions
import groovy.cli.picocli.CliBuilder

import java.util.logging.Logger

/**
 * Building the performer Docker images can be non-trivial, often requiring manipulating the project build files.
 *
 * This tool encapsulates that logic so it can be easily called from the both the performance and integration CI jobs,
 * as well as on localhost.
 *
 * It now also supports validation modes, that checks that the performer can be built against multiple older versions of
 * the SDK, to ensure the code tagging is correct.
 *
 * Currently it supports the new JVM performers, but ultimately we want this to support all of them.
 */
class BuildPerformer {
    private static Logger logger = Logger.getLogger("")

    static void main(String[] args) {
        TagProcessor.configureLogging(logger)

        def cli = new CliBuilder()
        // We only use "java-sdk" here to distinguish the SDK integrated version from the OG Java performer.
        cli.with {
            d longOpt: 'directory', args: 1, argName: 'd', required: true, 'Directory containing transactions-fit-performer'
            s longOpt: 'sdk', args: 1, argName: 'sdk', required: true, 'SDK to build (java-sdk, scala, kotlin, go, python, c++)'
            v longOpt: 'version', args: 1, argName: 'v', 'Version'
            i longOpt: 'image', args: 1, argName: 'i', required: true, 'Docker image name'
            o longOpt: 'only-source', argName: 'o', 'Only modify source, no Docker build'
            va longOpt: 'validationMode', args: 1, argName: 'va', 'Validation mode.  "auto" = build various versions of the SDK'
        }
        def options = cli.parse(args)
        if (!options) {
            logger.severe("Not enough arguments provided")
            System.exit(-1)
        }

        String sdkRaw = options.s.toLowerCase().trim()
        def env = new Environment()
        Optional<String> version = options.v ? Optional.of(options.v) : Optional.empty()
        boolean onlySource = options.o
        String imageName = options.i
        String dir = options.d
        String validationMode = options.va

        ArrayList<Optional<String>> versionsToBuild = []
        if (validationMode == null) {
            versionsToBuild.add(version)
        } else {
            List<PerfConfig.Implementation> versions

            if (sdkRaw == "java-sdk") {
                def implementation = new PerfConfig.Implementation("Java", "3.X.0", null)
                versions = Versions.jvmVersions(env, implementation, "java-client")
            } else if (sdkRaw == "scala") {
                def implementation = new PerfConfig.Implementation("Scala", "1.X.0", null)
                versions = Versions.jvmVersions(env, implementation, "scala-client_2.12")
            } else if (sdkRaw == "kotlin") {
                def implementation = new PerfConfig.Implementation("Kotlin", "1.X.0", null)
                versions = Versions.jvmVersions(env, implementation, "kotlin-client")
            } else if (sdkRaw == "go") {
                // 2.3.0 is earliest supported
                def implementation = new PerfConfig.Implementation("Go", "2.3.0", null)
                versions = Versions.versions(env, implementation, "Go", GoVersions.allReleases)
            } else if (sdkRaw == "python") {
                // 4.1.0 is earliest supported
                def implementation = new PerfConfig.Implementation("Python", "4.1.0", null)
                versions = Versions.versions(env, implementation, "Python", PythonVersions.allReleases)
            } else if (sdkRaw == "c++") {
                def implementation = new PerfConfig.Implementation("C++", "1.0.0", null)
                versions = Versions.versions(env, implementation, "C++", CppVersions.allReleases)
            } else if (sdkRaw == "node") {
                // 4.2.0 is earliest supported
                def implementation = new PerfConfig.Implementation("Node", "4.2.0", null)
                versions = Versions.versions(env, implementation, "Node", NodeVersions.allReleases)
            } else if (sdkRaw == ".net") {
                // 3.3.0 is earliest supported
                def implementation = new PerfConfig.Implementation(".NET", "3.3.0", null)
                versions = Versions.versions(env, implementation, ".NET", DotNetVersions.allReleases)
            } else {
                logger.severe("Do not yet know how to validate " + sdkRaw)
            }

            env.log("Got ${versions.size()} versions")

            versions.forEach(vers -> {
                versionsToBuild.add(Optional.of(vers.version))
            })
        }

        versionsToBuild.forEach(vers -> env.log("Will build ${vers}"))

        ArrayList<Optional<String>> successfullyBuilt = []

        versionsToBuild.forEach(vers -> {
            env.log("Building ${vers}")

            try {
                if (sdkRaw == "java-sdk" || sdkRaw == "scala" || sdkRaw == "kotlin") {
                    BuildDockerJVMPerformer.build(env, dir, sdkRaw.replace("-sdk", ""), vers, imageName, onlySource)
                } else if (sdkRaw == "go") {
                    BuildDockerGoPerformer.build(env, dir, vers, imageName, onlySource)
                } else if (sdkRaw == "python") {
                    BuildDockerPythonPerformer.build(env, dir, vers, Optional.empty(), imageName, onlySource)
                } else if (sdkRaw == "c++") {
                    BuildDockerCppPerformer.build(env, dir, vers, Optional.empty(), imageName, onlySource)
                } else {
                    logger.severe("Do not yet know how to build " + sdkRaw)
                }
            }
            catch (err) {
                env.log("Failed to build ${vers}")
                // We bail out as the source is in a state that's showing the build problem and hence is easy to fix.
                throw err
            }

            successfullyBuilt.add(vers)
        })

        successfullyBuilt.forEach(vers -> env.log("Successfully built ${vers}"))
    }
}