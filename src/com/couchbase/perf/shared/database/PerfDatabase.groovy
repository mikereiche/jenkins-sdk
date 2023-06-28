package com.couchbase.perf.shared.database

import com.couchbase.context.environments.Environment
import groovy.sql.Sql
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import com.couchbase.context.StageContext
import com.couchbase.perf.shared.config.Run

import java.util.stream.Collectors

class RunFromDb {
    Run run
    List<String> dbRunIds
}

@CompileStatic
class PerfDatabase {
    @CompileDynamic
    private static Sql getConnection(String jdbc, String username, String password) {
        def dbDriver = "org.postgresql.Driver"
        return Sql.newInstance(jdbc, username, password, dbDriver)
    }

    private static void execute(Sql sql, Environment env, String st) {
        env.log(st)
        sql.execute(st)
    }

    /**
     * Sets up the database.
     */
    static void migrate(String jdbc, String username, String password, Environment env) {
        def sql = getConnection(jdbc, username, password)

        execute(sql, env, "CREATE TABLE IF NOT EXISTS runs (id uuid PRIMARY KEY, datetime timestamp, params jsonb)")
        execute(sql, env, "CREATE TABLE IF NOT EXISTS buckets (time TIMESTAMPTZ NOT NULL, run_id uuid, operations_total int, operations_success int, operations_failed int, duration_min_us int, duration_max_us int, duration_average_us int, duration_p50_us int, duration_p95_us int, duration_p99_us int, errors jsonb)")
        execute(sql, env, "CREATE TABLE IF NOT EXISTS metrics (initiated TIMESTAMPTZ NOT NULL, run_id uuid, metrics jsonb)")
        execute(sql, env, "CREATE TABLE IF NOT EXISTS run_events (run_id uuid, datetime TIMESTAMPTZ, params jsonb)")
        execute(sql, env, "ALTER TABLE buckets ADD COLUMN IF NOT EXISTS errors jsonb")
        execute(sql, env, "ALTER TABLE buckets DROP COLUMN IF EXISTS operations_incomplete")
        execute(sql, env, "ALTER TABLE buckets ADD COLUMN IF NOT EXISTS time_offset_secs bigint")
        execute(sql, env, "ALTER TABLE metrics ADD COLUMN IF NOT EXISTS time_offset_secs bigint")
        // This turns a regular postgres database into a timescaledb one
        execute(sql, env, "SELECT create_hypertable('buckets', 'time', migrate_data => true, if_not_exists => true)")

        // Cleanup buckets and metrics for which the run has been removed
        execute(sql, env, "delete from buckets where run_id not in (select id from runs);")
        execute(sql, env, "delete from metrics where run_id not in (select id from runs);")
        execute(sql, env, "delete from run_events where run_id not in (select id from runs);")

        // Situational testing
        execute(sql, env, "CREATE TABLE IF NOT EXISTS situational_runs (id uuid, datetime TIMESTAMPTZ)")
        execute(sql, env, "CREATE TABLE IF NOT EXISTS situational_run_join (situational_run_id uuid, run_id uuid, params jsonb)")
        execute(sql, env, "ALTER TABLE situational_run_join ADD COLUMN IF NOT EXISTS params jsonb")
        execute(sql, env, "ALTER TABLE run_events ALTER COLUMN datetime TYPE TIMESTAMPTZ")
        execute(sql, env, "ALTER TABLE situational_runs ALTER COLUMN datetime TYPE TIMESTAMPTZ")
    }

    /**
     * We have a bunch of runs specified in job-config.yaml.  See which ones already exist in the database (we can
     * skip those).
     */
    static List<RunFromDb> compareRunsAgainstDb(String jdbc, String username, String password, Environment env, List <Run> runs) {
        env.log("Connecting to database ${jdbc} to check existing runs")
        def sql = getConnection(jdbc, username, password)

        return runs.stream()
                .map(run -> {
                    def json = run.toJson()
                    def statement = 'SELECT id FROM runs WHERE params @> ?.jsonparam::jsonb'
                    def dbRunIds = new ArrayList<String>()
                    sql.eachRow(statement, [jsonparam: json]) {
                        dbRunIds.add(it.getString("id"))
                    }
                    env.log("Found ${dbRunIds.size()} entries for run ${json}")
                    def r = new RunFromDb()
                    r.run = run
                    r.dbRunIds = dbRunIds
                    return r
                })
                .collect(Collectors.toList())
    }
}