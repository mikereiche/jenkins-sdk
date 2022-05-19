package com.couchbase.fit.perf.database

import groovy.sql.Sql
import groovy.transform.CompileDynamic
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
    @CompileDynamic
    static Sql getConnection(StageContext ctx, String[] args) {
        def dbUrl = "jdbc:postgresql://${ctx.jc.database.hostname}:${ctx.jc.database.port}/${ctx.jc.database.database}"
        def dbUser = ctx.jc.database.username
        def dbPassword = ctx.jc.database.password
        if (args.length > 0) {
            // TODO we are parsing the args twice, it will only ever be one item so not too big a deal.
            dbPassword = args[0]
        }
        def dbDriver = "org.postgresql.Driver"
        def sql = Sql.newInstance(dbUrl, dbUser, dbPassword, dbDriver)
        return sql
    }

    static List<RunFromDb> compareRunsAgainstDb(StageContext ctx, List <Run> runs, String[] args) {
        def sql = getConnection(ctx, args)

        if (sql.rows('SELECT * FROM pg_catalog.pg_tables WHERE tablename = "runs";').size() == 0) {
            ctx.env.log("`runs` table does not exist yet")
            return runs.stream()
                    .map(run -> {
                        def r = new RunFromDb()
                        r.run = run
                        r.dbRunIds = new ArrayList<String>()
                        return r
                    })
                    .collect(Collectors.toList())
        }

        return runs.stream()
                .map(run -> {
                    def json = run.toJson()
                    def statement = 'SELECT id FROM runs WHERE params @> "$json"::jsonb'
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