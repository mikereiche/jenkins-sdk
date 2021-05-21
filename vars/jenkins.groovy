import com.couchbase.fit.perf.main.Execute

def info(message) {
    echo "INFO: ${message}"
}

void execute() {
    Execute.execute()
}