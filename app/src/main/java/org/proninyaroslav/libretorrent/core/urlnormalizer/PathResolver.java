package org.proninyaroslav.libretorrent.core.urlnormalizer;

import java.util.ArrayList;
import java.util.List;

/**
 * PathResolver is a utility class that resolves a reference path against a base path.
 */
final class PathResolver {

    /**
     * Disallow instantiation of class.
     */
    private PathResolver() {
    }

    /**
     * Returns a resolved path.
     * <p>
     * For example:
     * <p>
     * resolve("/some/path", "..") == "/some"
     * resolve("/some/path", ".") == "/some/"
     * resolve("/some/path", "./here") == "/some/here"
     * resolve("/some/path", "../here") == "/here"
     */
    public static String resolve(String base, String ref) {
        String merged = merge(base, ref);
        if (merged == null || merged.isEmpty()) {
            return "";
        }
        String[] parts = merged.split("/", -1);
        return resolve(parts);
    }

    /**
     * Returns the two path strings merged into one.
     * <p>
     * For example:
     * <qp>
     * merge("/some/path", "./../hello") == "/some/./../hello"
     * merge("/some/path/", "./../hello") == "/some/path/./../hello"
     * merge("/some/path/", "") == "/some/path/"
     * merge("", "/some/other/path") == "/some/other/path"
     */
    private static String merge(String base, String ref) {
        String merged;

        if (ref == null || ref.isEmpty()) {
            merged = base;
        } else if (ref.charAt(0) != '/' && base != null && !base.isEmpty()) {
            int i = base.lastIndexOf("/");
            merged = base.substring(0, i + 1) + ref;
        } else {
            merged = ref;
        }

        if (merged == null || merged.isEmpty()) {
            return "";
        }

        return merged;
    }

    /**
     * Returns the resolved path parts.
     * <p>
     * Example:
     * <p>
     * resolve(String[]{"some", "path", "..", "hello"}) == "/some/hello"
     */
    private static String resolve(String[] parts) {
        if (parts.length == 0) {
            return "";
        }

        List<String> result = new ArrayList<>();

        for (String part : parts) {
            switch (part) {
                case "":
                case ".":
                    // Ignore
                    break;
                case "..":
                    if (result.size() > 0) {
                        result.remove(result.size() - 1);
                    }
                    break;
                default:
                    result.add(part);
                    break;
            }
        }

        // Get last element, if it was '.' or '..' we need
        // to end in a slash.
        switch (parts[parts.length - 1]) {
            case ".":
            case "..":
                // Add an empty last string, it will be turned into
                // a slash when joined together.
                result.add("");
                break;
        }

        return "/" + join("/", result);
    }

    private static String join(CharSequence delimiter, List<String> tokens)
    {
        int length = tokens.size();
        if (length == 0)
            return "";

        StringBuilder sb = new StringBuilder();
        sb.append(tokens.get(0));
        for (int i = 1; i < length; i++) {
            sb.append(delimiter);
            sb.append(tokens.get(i));
        }

        return sb.toString();
    }
}
