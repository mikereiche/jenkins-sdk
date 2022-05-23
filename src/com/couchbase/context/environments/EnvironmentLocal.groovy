package com.couchbase.context.environments

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic

import java.time.LocalDateTime

// An Environment for running on a local development machine.  It can abstract over Windows, Linux & Mac, though
// appropriate executable overrides may need to be set in the config.
@CompileStatic
class EnvironmentLocal extends Environment {
    Stack<File> workingDirectory = new Stack<>()
    String initialDir
    List<String> envvarConverted = new ArrayList<>()

    @CompileDynamic
    EnvironmentLocal(config) {
        super(config)

        envvar.forEach((k,v) -> {
            envvarConverted.add(k + "=" + v)
        })

        initialDir = System.getProperty("user.dir")
        log("Working directory: $initialDir")
    }

    @Override
    String currentDir() {
        if (workingDirectory.isEmpty()) {
            return initialDir
        }
        return workingDirectory.peek()
    }

    @Override
    void dir(String directory, Closure closure) {
        String fullDirectory
        if (workingDirectory.isEmpty()) {
            fullDirectory = initialDir + File.separator + directory
        }
        else {
            fullDirectory = workingDirectory.peek().getAbsolutePath() + File.separator + directory
        }
        dirAbsolute(fullDirectory, closure)
    }


    @Override
    void dirAbsolute(String fullDirectory, Closure closure) {
        workingDirectory.add(new File(fullDirectory))
        log("Moving to new working directory, stack now $workingDirectory")
        try {
            closure.run()
        }
        finally {
            workingDirectory.pop()
            log("Popping directory stack, now $workingDirectory")
        }
    }

    @Override
    void execute(String command) {
        def exeOrig = command.split(" ")[0]
        def exe = exeOrig

        if (executableOverrides.containsKey(exe) && executableOverrides.get(exe) != null) {
            def replaceWith = executableOverrides.get(exe)
            // log("Overriding command $exe to $replaceWith")
            command = command.replace(exe, replaceWith)
            exe = replaceWith
        }

        // This hangs sometimes...
//        def which = "which $exe".execute().text.trim()

        File wd = null
        if (!workingDirectory.empty()) {
            wd = workingDirectory.peek()
        }
        File fullWd = wd != null ? wd : new File(initialDir)

        String output = ((LocalDateTime.now().toString().padRight(30, '0')) + "-" + exeOrig)
                .replaceAll(':', '-')
                .replaceAll('\\.', "-")
                .replaceAll(' ', "-")
        String stdout = output + ".stdout.log"
        String stderr = output + ".stderr.log"


        log("Executing '$command' logging to ${stdout} in directory ${fullWd.getAbsolutePath()} with envvar ${envvarConverted} ")
        def stdoutFile = new FileWriter(stdout)
        def stderrFile = new FileWriter(stderr)
        def env = new ArrayList<String>()
        env.add(null)
        def proc = command.execute(env, fullWd)
        proc.waitForProcessOutput(stdoutFile, stderrFile)
//        proc.waitForOrKill(600000)

        stdoutFile.close()
        stderrFile.close()

        if (proc.exitValue() != 0) {
            log("Process '$command' failed with error ${proc.exitValue()}")
            throw new RuntimeException("Process '$command' failed with error ${proc.exitValue()}")
        }
    }

    @Override
    String executeSimple(String command) {
        def exe = command.split(" ")[0]

        if (executableOverrides.containsKey(exe) && executableOverrides.get(exe) != null) {
            def replaceWith = executableOverrides.get(exe)
            // log("Overriding command $exe to $replaceWith")
            command = command.replace(exe, replaceWith)
            exe = replaceWith
        }

        // This hangs sometimes...
//        def which = "which $exe".execute().text.trim()

        File wd = null
        if (!workingDirectory.empty()) {
            wd = workingDirectory.peek()
        }
        File fullWd = wd != null ? wd : new File(initialDir)
        String output = "logs/" + UUID.randomUUID().toString()

        log("Executing '$command' logging to ${output} in directory ${fullWd.getAbsolutePath()} with envvar ${envvarConverted} ")

        def sout = new StringBuilder(), serr = new StringBuilder()

        def proc = ['bash', '-c', command].execute(envvarConverted, fullWd)
        proc.consumeProcessOutput(sout, serr)
        proc.waitForOrKill(120000)

        if (proc.exitValue() != 0) {
            log("Process '$command' failed with error ${proc.exitValue()}")
            throw new RuntimeException("Process '$command' failed with error ${proc.exitValue()}")
        }
        return sout.toString().trim()
    }

    @Override
    void log(String toLog) {
        String bonusIndent = ""
        if (!(toLog.startsWith("<") || toLog.startsWith(">"))) {
            bonusIndent = "  "
        }
        println("${LocalDateTime.now().toString().padRight(30, '0')} ${" " * logIndent}${bonusIndent}$toLog")
    }
}