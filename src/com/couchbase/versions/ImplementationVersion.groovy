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
        int first = s.indexOf('.', 0)
        int second = s.indexOf('.', first + 1)
        String majorRaw = s.substring(0, first)
        String minorRaw = s.substring(first + 1, second)
        String restRaw = s.substring(second + 1)
        int major = Integer.parseInt(majorRaw)
        int minor = Integer.parseInt(minorRaw)

        if (restRaw.contains("-")) {
            // "3.3.3-20220715.074746-6"
            int index = restRaw.indexOf("-")
            return new ImplementationVersion(
                    major,
                    minor,
                    Integer.parseInt(restRaw.substring(0, index)),
                    restRaw.substring(index))
        }
        else {
            return new ImplementationVersion(major, minor, Integer.parseInt(restRaw), null)
        }
    }

    @Override
    String toString() {
        return "${major}.${minor}.${patch}${if (snapshot != null) snapshot else ""}"
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
