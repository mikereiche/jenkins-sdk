A Jenkins shared library, for use by SDK team.

(Well, that was the intent, but Jenkins won't allow untrusted libraries to use third-party libs.)

# SDK Performance
See the https://github.com/couchbaselabs/perf-sdk project for instructions.

# CLI tools

## Tags
See the comments on `TagProcessor` for more.
```
./gradlew tags --args="-d <some_path> -v 3.0.0"
```