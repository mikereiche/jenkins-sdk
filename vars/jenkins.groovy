import com.couchbase.perf.shared.main.Execute

def info(message) {
    echo "INFO: ${message}"
}

void execute() {
    Execute.execute()
}