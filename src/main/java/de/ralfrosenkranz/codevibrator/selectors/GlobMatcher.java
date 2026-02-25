package de.ralfrosenkranz.codevibrator.selectors;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;

/**
 * Simple glob matcher for single file names. Supports '*' '?' and character classes as provided by NIO glob.
 * Patterns are matched against the file name only (no paths).
 */
public final class GlobMatcher {
    private GlobMatcher() {}

    public static boolean matches(String pattern, String fileName) {
        if (pattern == null || pattern.isBlank()) return false;
        String p = pattern.trim();
        PathMatcher m = FileSystems.getDefault().getPathMatcher("glob:" + p);
        return m.matches(Path.of(fileName));
    }
}
