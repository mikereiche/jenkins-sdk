package com.couchbase.versions

import com.couchbase.context.environments.Environment
import com.couchbase.perf.shared.config.PerfConfig

class Versions {
    static List<PerfConfig.Implementation> versions(Environment env, Object implementation, String client, Set<ImplementationVersion> versions) {
        String[] split = implementation.version.split("\\.")
        Integer lookingForMajor = null
        Integer lookingForMinor = null
        Integer lookingForPatch = null

        if (split[0] != 'X') lookingForMajor = Integer.parseInt(split[0])
        if (split[1] != 'X') lookingForMinor = Integer.parseInt(split[1])
        if (split[2] != 'X') lookingForPatch = Integer.parseInt(split[2])

        List<ImplementationVersion> lookingFor = versions.stream()
                .filter(v -> {
                    boolean out = true

                    // Bit of hardcoded logic to filter out Kotlin developer previews, since they don't compile with
                    // the current performer
                    if (implementation.language == "Kotlin"
                            && v.snapshot != null
                            && v.snapshot.startsWith("-dp")) {
                        env.log("Filtering out kotlin ${v.toString()}")
                        out = false
                    }

                    if (out && lookingForMajor != null && lookingForMajor != v.major) out = false
                    if (out && lookingForMinor != null && lookingForMinor != v.minor) out = false
                    if (out && lookingForPatch != null && lookingForPatch != v.patch) out = false
                    return out
                })
                .toList()

        return lookingFor.stream()
                .map(v -> new PerfConfig.Implementation(implementation.language, v.toString(), null))
                .toList()
    }

    static List<PerfConfig.Implementation> jvmVersions(Environment env, Object implementation, String client) {
        def allVersions = JVMVersions.getAllJVMReleases(client)
        return versions(env, implementation, client, allVersions)
    }
}
