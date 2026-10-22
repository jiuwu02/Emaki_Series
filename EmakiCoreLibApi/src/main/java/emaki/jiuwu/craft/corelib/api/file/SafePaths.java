package emaki.jiuwu.craft.corelib.api.file;

import java.nio.file.Path;

/**
 * Resolves user-supplied relative paths against a trusted root without letting them escape it.
 *
 * <p>Every check happens on the strings before any file is touched: backslashes are treated as
 * separators, a colon (Windows drive letters or stream separators) is refused outright, and the
 * normalized result must still start with the normalized absolute root. The helper only computes
 * paths; it never touches the file system, so a non-{@code null} result does not imply the file
 * exists.
 */
public final class SafePaths {

    private SafePaths() {
    }

    /**
     * Resolves {@code relativePath} inside {@code root}.
     *
     * @param root directory the result must stay inside; may be {@code null}
     * @param relativePath candidate relative path; may be {@code null}
     * @return the normalized absolute path, guaranteed to stay under the normalized absolute
     *         {@code root}, or {@code null} when either argument is {@code null}, the input
     *         contains a parent-directory segment ({@code ..}) or a {@code :} character, or the
     *         resolved path would leave {@code root}
     */
    public static Path resolveInside(Path root, String relativePath) {
        if (root == null || relativePath == null) {
            return null;
        }
        String normalizedInput = relativePath.replace('\\', '/');
        if (normalizedInput.contains("../") || normalizedInput.equals("..") || normalizedInput.contains(":")) {
            return null;
        }
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path resolved = normalizedRoot.resolve(normalizedInput).normalize();
        if (!resolved.startsWith(normalizedRoot)) {
            return null;
        }
        return resolved;
    }
}
