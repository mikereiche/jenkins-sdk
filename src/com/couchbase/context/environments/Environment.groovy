package com.couchbase.context.environments

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import com.couchbase.stages.Stage

// The abstraction that allows commands to be executed correctly on Jenkins, or localhost, or wherever required.
@CompileStatic
abstract class Environment {
    Map<String, String> executableOverrides
    Map<String, String> envvar
    int logIndent = 0

    @CompileDynamic
    Environment(config) {
        this.executableOverrides = config.executables;
        this.envvar = config.envvar
    }

    abstract void execute(String command)
    abstract String executeSimple(String command)
    // Runs `closure` inside a relative directory from current stack. This dir is pushed onto the stack.
    abstract void dir(String directory, Closure closure)
    // Runs `closure` inside an absolute directory. This dir is pushed onto the stack.
    abstract void dirAbsolute(String directory, Closure closure)
//    abstract Boolean supportsCbdeps()
    abstract void log(String toLog)

    default startStage(Stage stage) {
        logIndent ++
        log("> ${stage.name()}")
    }

    default stopStage(Stage stage) {
        log("< ${stage.name()}")
        logIndent --
        if (logIndent < 0) {
            // bug!
            logIndent = 0
        }
    }

    void tempDir(Closure voidClosure) {
        def tempDir = "temp-${UUID.randomUUID().toString().substring(0, 4)}"
        execute("mkdir -p $tempDir")
        dir(tempDir, voidClosure)
    }

    void checkout(String c) {
        execute("git clone $c")
    }

    abstract String currentDir()
}

