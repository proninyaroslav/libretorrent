package com.ernieyu.feedparser.mediarss;

/**
 * This is the hash of the binary media file.
 * It can appear multiple times as long as each instance is a different algo.
 * It has one optional attribute.
 *
 * <media:hash algo="md5">dfdec888b72151965a34b4b59031290a</media:hash>
 *
 * algo indicates the algorithm used to create the hash.
 * Possible values are "md5" and "sha-1". Default value is "md5".
 * It is an optional attribute.
 */

public class Hash {
    private String value;
    private String algorithm = "md5";

    public Hash(String value) {
        this.value = value;
    }

    /**
     * Returns a hash.
     * <p>
     *
     * @return a hash, <b>null</b> if none.
     */

    public String getValue() {
        return value;
    }

    /**
     * Returns the hash algorithm.
     * <p>
     *
     * @return the hash algorithm.
     * Possible values are "md5" and "sha-1". Default value is "md5".
     */

    public String getAlgorithm() {
        return algorithm;
    }

    public void setAlgorithm(String algorithm) {
        if (algorithm == null)
            throw new IllegalArgumentException("Algorithm cannot be null");
        this.algorithm = algorithm;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Hash that = (Hash) o;

        if (value != null ? !value.equals(that.value) : that.value != null)
            return false;
        return algorithm != null ? algorithm.equals(that.algorithm) : that.algorithm == null;
    }

    @Override
    public int hashCode() {
        int result = value != null ? value.hashCode() : 0;
        result = 31 * result + (algorithm != null ? algorithm.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "MediaRssHash{" +
            "value='" + value + '\'' +
            ", algorithm='" + algorithm + '\'' +
            '}';
    }
}
