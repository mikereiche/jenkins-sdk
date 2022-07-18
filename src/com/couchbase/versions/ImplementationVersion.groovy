package com.couchbase.versions

class ImplementationVersion {
    public final int major;
    public final int minor;
    public final int patch;
    public final String snapshot;

    ImplementationVersion(int major, int minor, int patch, String snapshot) {
        this.major = major
        this.minor = minor
        this.patch = patch
        this.snapshot = snapshot
    }

    static ImplementationVersion from(String s) {
        String[] split = s.split("\\.")
        return new ImplementationVersion(
                Integer.parseInt(split[0]),
                Integer.parseInt(split[1]),
                Integer.parseInt(split[2]),
                null
        )
    }

    @Override
    String toString() {
        return "${major}.${minor}.${patch}${if (snapshot != null) "." + snapshot else ""}"
    }

    boolean equals(o) {
        if (this.is(o)) return true
        if (getClass() != o.class) return false

        ImplementationVersion that = (ImplementationVersion) o

        if (major != that.major) return false
        if (minor != that.minor) return false
        if (patch != that.patch) return false
        if (snapshot != that.snapshot) return false

        return true
    }

    int hashCode() {
        int result
        result = major
        result = 31 * result + minor
        result = 31 * result + patch
        result = 31 * result + (snapshot != null ? snapshot.hashCode() : 0)
        return result
    }
}
