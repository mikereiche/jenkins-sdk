package com.couchbase.context.environments

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import com.couchbase.stages.Stage

import java.time.LocalDateTime

// The abstraction that allows commands to be executed correctly on Jenkins, or localhost, or wherever required.
@CompileStatic
class Environment {
    private Map<String, String> executableOverrides
    private Map<String, String> envvar
    private int logIndent = 0
    private Stack<File> workingDirectory = new Stack<>()
    private String initialDir
    private List<String> envvarConverted = new ArrayList<>()
    // Absolute location of the workspace (temporary working space)
    public final String workspaceAbs
    private final File logFile

    @CompileDynamic
    Environment(config) {
        this.executableOverrides = config.environment.executables != null ? config.environment.executables : new HashMap<>();
        this.envvar = config.environment.envvar != null ? config.environment.envvar : new HashMap<>()

        envvar.forEach((k,v) -> {
            envvarConverted.add(k + "=" + v)
        })
        if (envvarConverted.size() == 0){
            envvarConverted = null
        }

        initialDir = System.getProperty("user.dir")
        workspaceAbs = new File(config.environment.workspace + File.separatorChar + UUID.randomUUID().toString().substring(0, 6)).getAbsolutePath()
        logFile = workspaceAbs + File.separatorChar + "log.txt"

        mkdirs(workspaceAbs)

        log("Working directory: $initialDir, workspace: $workspaceAbs, log: ${logFile.absolutePath}")
    }

    void mkdirs(String path) {
        boolean created = new File(path).mkdirs()
        log("Created directory ${path}")
    }

    String currentDir() {
        if (workingDirectory.isEmpty()) {
            return initialDir
        }
        return workingDirectory.peek()
    }

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

        def proc = ['bash', '-c', command].execute(envvarConverted, fullWd)
        proc.consumeProcessOutput(stdoutFile, stderrFile)
        proc.waitForProcessOutput()

        stdoutFile.close()
        stderrFile.close()

        if (proc.exitValue() != 0) {
            log("Process '$command' failed with error ${proc.exitValue()}")
            throw new RuntimeException("Process '$command' failed with error ${proc.exitValue()}")
        }
    }

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
        proc.waitForProcessOutput(sout, serr)


        if (proc.exitValue() != 0) {
            log("Process '$command' failed with error ${proc.exitValue()}")
            throw new RuntimeException("Process '$command' failed with error ${proc.exitValue()}, ${serr.toString().trim()}")
        }
        return sout.toString().trim()
    }

//    @Override
//    void noBlockExecute(){
//
//    }

    void log(String toLog) {
        String bonusIndent = ""
        if (!(toLog.startsWith("<") || toLog.startsWith(">"))) {
            bonusIndent = "  "
        }
        def str = "${LocalDateTime.now().toString().padRight(30, '0')} ${" " * logIndent}${bonusIndent}$toLog"
        println(str)
        logFile.println(str)
    }

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
        def tempDir = "$workspaceAbs/temp-${UUID.randomUUID().toString().substring(0, 4)}"
        mkdirs(tempDir)
        dir(tempDir, voidClosure)
    }

    void checkout(String c) {
        execute("git clone $c")
    }
}

