package com.couchbase.tools.performer

import com.couchbase.context.environments.Environment
import com.couchbase.tools.tags.TagProcessor
import groovy.cli.picocli.CliBuilder

import java.util.logging.Logger

/**
 * Building the performer Docker images can be non-trivial, often requiring manipulating the project build files.
 *
 * This tool encapsulates that logic so it can be easily called from the both the performance and integration CI jobs,
 * as well as on localhost.
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
            s longOpt: 'sdk', args: 1, argName: 'sdk', required: true, 'SDK to build (java-sdk, scala, kotlin, go)'
            v longOpt: 'version', args: 1, argName: 'v', 'Version'
            i longOpt: 'image', args: 1, argName: 'i', required: true, 'Docker image name'
            o longOpt: 'only-source', argName: 'o', 'Only modify source, no Docker build'
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

        if (sdkRaw == "java-sdk" || sdkRaw == "scala" || sdkRaw == "kotlin") {
            BuildDockerJVMPerformer.build(env, dir, sdkRaw.replace("-sdk", ""), version, imageName, onlySource)
        }
        else if (sdkRaw == "go") {
            BuildDockerGoPerformer.build(env, dir, version, imageName, onlySource)
        }
        else {
            logger.severe("Do not yet know how to build " + sdkRaw)
        }
    }
}