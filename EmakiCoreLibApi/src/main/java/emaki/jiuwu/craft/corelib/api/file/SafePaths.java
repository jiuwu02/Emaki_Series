package emaki.jiuwu.craft.corelib.api.file;

import java.nio.file.Path;

public final class SafePaths {

    private SafePaths() {
    }

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
