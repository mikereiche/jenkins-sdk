package com.couchbase.fit.perf.database

import groovy.sql.Sql
import groovy.transform.CompileStatic
import com.couchbase.context.StageContext
import com.couchbase.fit.perf.config.Run

import java.util.stream.Collectors

class RunFromDb {
    Run run
    List<String> dbRunIds
}

@CompileStatic
class PerfDatabase {
    static Sql getConnection() {
        def dbUrl = "jdbc:postgresql://localhost/perf"
        def dbUser = "postgres"
        def dbPassword = "postgres"
        def dbDriver = "org.postgresql.Driver"

        def sql = Sql.newInstance(dbUrl, dbUser, dbPassword, dbDriver)
        return sql
    }

    static void getRuns() {
        def sql = getConnection()
        sql.eachRow('select * from runs') { row ->
            println "${row}"
        }
    }

    static List<RunFromDb> compareRunsAgainstDb(StageContext ctx, List <Run> runs) {
        def sql = getConnection()
        return runs.stream()
                .map(run -> {
                    def json = run.toJson()
                    def statement = "SELECT id FROM runs WHERE params @> '$json'::jsonb"
                    def dbRunIds = new ArrayList<String>()
                    sql.eachRow(statement) {
                        dbRunIds.add(it.getString("id"))
                    }
                    ctx.env.log("Found ${dbRunIds.size()} entries for run $statement")
                    def r = new RunFromDb()
                    r.run = run
                    r.dbRunIds = dbRunIds
                    return r
                })
                .collect(Collectors.toList())
    }
}