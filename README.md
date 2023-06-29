A Jenkins shared library, for use by SDK team.

(Well, that was the intent, but Jenkins won't allow untrusted libraries to use third-party libs.)

# SDK Performance
See https://github.com/couchbaselabs/transactions-fit-performer/blob/master/perf-driver/README.md for instructions.

# CLI tools

## Tags
See the comments on `TagProcessor` for more.
```
./gradlew tags --args="-d <some_path> -v 3.0.0"
```

To restore the code use the -r flag:
```
./gradlew tags --args="-d <some_path> -v 3.0.0 -r"
```

# Building performer Docker images
See the comments on `BuilderPerformer` for more.

```
./gradlew buildPerformer --args="-d <some_path> -s kotlin -v 1.0.0 -i performer"
```

# Setting up performance database
Creates all required tables.

```
./gradlew setupPerfDatabase
```
