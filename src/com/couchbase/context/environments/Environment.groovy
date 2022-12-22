package com.couchbase.context.environments

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import com.couchbase.stages.Stage

import java.nio.file.Files
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
    private final Object config

    @CompileDynamic
    Environment() {
        initialDir = System.getProperty("user.dir")
    }


    @CompileDynamic
    Environment(config) {
        this()
        this.executableOverrides = config.environment.executables != null ? config.environment.executables : new HashMap<>();
        this.envvar = config.environment.envvar != null ? config.environment.envvar : new HashMap<>()
        this.config = config

        envvarConverted.add("PATH=" + System.getenv("PATH"))

        envvar.forEach((k,v) -> {
            envvarConverted.add(k + "=" + v)
        })
        if (envvarConverted.size() == 0){
            envvarConverted = null
        }

        workspaceAbs = new File(config.environment.workspace + File.separatorChar + UUID.randomUUID().toString().substring(0, 6)).getAbsolutePath()
        logFile = new File(workspaceAbs + File.separatorChar + "log.txt")

        mkdirs(workspaceAbs)

        log("Working directory: $initialDir, workspace: $workspaceAbs, log: ${logFile.absolutePath}")
    }

    void mkdirs(String path) {
        boolean created = new File(path).mkdirs()
        log("Created directory ${path}")
    }

    void rmdir(String path) {
        new File(path).deleteDir()
        log("Deleted directory ${path}")
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

    String overrideIfNeeded(String exe) {
        if (executableOverrides != null && executableOverrides.containsKey(exe) && executableOverrides.get(exe) != null) {
            return executableOverrides.get(exe)
        }
        return exe
    }

    String execute(String command,
                   boolean saveOutputToFile = true,
                   boolean logFailure = true,
                   boolean writeOutputDirectly = false,
                   boolean background = false) {
        def exeOrig = command.split(" ")[0]
        def exe = exeOrig

        boolean isWindows = System.getProperty("os.name").toLowerCase().startsWith("windows")

        if (executableOverrides != null && executableOverrides.containsKey(exe) && executableOverrides.get(exe) != null) {
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

        log("Executing '$command' ")

        Process proc = null
        String[] nullArray = null
        if (isWindows) {
            proc = [overrideIfNeeded('cmd'), '/c', command].execute(nullArray, fullWd)
        }
        else {
            proc = command.execute(nullArray, fullWd)
        }

        String ret = null

        if (saveOutputToFile) {
            if (background) {
                throw new IllegalArgumentException("background not supported in this mode")
            }

            String stdout = workspaceAbs + File.separatorChar + output + ".stdout.log"
            String stderr = workspaceAbs + File.separatorChar + output + ".stderr.log"

            def stdoutFile = new FileWriter(stdout)
            def stderrFile = new FileWriter(stderr)

            if (saveOutputToFile) {
                proc.consumeProcessOutput(stdoutFile, stderrFile)
            }
            proc.waitForProcessOutput()

            stdoutFile.close()
            stderrFile.close()

            def stdoutFileAfter = new File(stdout)
            def stderrFileAfter = new File(stderr)

            // Keep tidy by removing useless empty files
            if (stdoutFileAfter.size() == 0) {
                stdoutFileAfter.delete()
            }
            // Useful to print the output directly to logs if it's not too large
            else if (proc.exitValue() != 0 && logFailure) {
                // Due to difficulties with getting logs from dynamic CI nodes, just logging everything regardless
//                if (stdoutFileAfter.size() < 5000) {
                    log(stdoutFileAfter.readLines().join("\n"))
//                } else {
//                    log("Output of ${stdout} is too large to log")
//                }
            }

            if (stderrFileAfter.size() == 0) {
                stderrFileAfter.delete()
            }
            else if (proc.exitValue() != 0 && logFailure) {
//                if (stderrFileAfter.size() < 5000) {
                    log(stderrFileAfter.readLines().join("\n"))
//                } else {
//                    log("Output of ${stderr} is too large to log")
//                }
            }
        }
        else if (writeOutputDirectly) {
            if (background) {
                proc.consumeProcessOutput(System.out, System.err)
            }
            else {
                proc.waitForProcessOutput(System.out, System.err)
            }
        }
        else {
            if (background) {
                throw new IllegalArgumentException("background not supported in this mode")
            }

            def sout = new StringBuilder(), serr = new StringBuilder()

            proc.waitForProcessOutput(sout, serr)

            ret = sout.toString().trim() + serr.toString().trim()
        }

        if (!background && proc.exitValue() != 0) {
            if (logFailure) {
                log("Process '$command' failed with error ${proc.exitValue()}")
            }
            throw new RuntimeException("Process '$command' failed with error ${proc.exitValue()}")
        }

        return ret
    }

    String executeSimple(String command) {
        return execute(command, false)
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
        // logFile.println(str)
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

    /**
     * Creates a temp dir intentionally outside of the workspace, so we can safely run git commands without getting
     * "Your local changes to the following files would be overwritten by checkout" errors.
     */
    void tempDir(Closure voidClosure) {
        var file = Files.createTempDirectory("temp-${UUID.randomUUID().toString().substring(0, 4)}")
        def tempDir = file.toAbsolutePath().toString()
        log("Created temp dir ${tempDir}")
        dirAbsolute(tempDir, voidClosure)
    }

    void checkout(String c) {
        execute("git clone $c")
    }

    /**
     * The source dir is the directory above jenkins-sdk and transactions-fit-performer
     */
    @CompileDynamic
    String sourceDirAbsolute() {
        return config.servers.driver.source
    }

    // Runs the closure inside the source directory
    def inSourceDirAbsolute(Closure closure) {
        def sd = sourceDirAbsolute()
        dirAbsolute(sd, closure)
    }
}

