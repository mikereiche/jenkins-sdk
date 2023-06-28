package com.couchbase.tools.perf.database

import com.couchbase.context.environments.Environment
import com.couchbase.perf.shared.config.PerfConfig
import com.couchbase.perf.shared.database.PerfDatabase
import com.couchbase.tools.performer.*
import com.couchbase.tools.tags.TagProcessor
import com.couchbase.versions.*
import groovy.cli.picocli.CliBuilder

import java.util.logging.Logger

/**
 * Sets up the performance database.
 */
class SetupPerfDatabase {
    private static Logger logger = Logger.getLogger("")

    static void main(String[] args) {
        TagProcessor.configureLogging(logger)

        def env = new Environment()

        PerfDatabase.migrate("jdbc:postgresql://localhost:5432/perf", "postgres", "password", env)
    }
}