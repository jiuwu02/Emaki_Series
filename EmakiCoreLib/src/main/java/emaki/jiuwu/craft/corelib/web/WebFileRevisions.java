package emaki.jiuwu.craft.corelib.web;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.LinkedHashMap;
import java.util.Map;

/** Shared file revision helpers for Web Console save and conflict flows. */
public final class WebFileRevisions {

    public static final String SAVE_CONFLICT_MESSAGE = "文件已被其他管理员修改，请重载后再保存。";

    private WebFileRevisions() {
    }

    public static long revision(File file) {
        return file == null ? 0L : revision(file.toPath());
    }

    public static long revision(Path path) {
        if (path == null || !Files.exists(path)) return 0L;
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException ignored) {
            return 0L;
        }
    }

    public static long requireExpected(File file, Long expectedRevision) throws WebConsoleRegistry.RevisionConflictException {
        return requireExpected(file, expectedRevision, SAVE_CONFLICT_MESSAGE);
    }

    public static long requireExpected(Path path, Long expectedRevision) throws WebConsoleRegistry.RevisionConflictException {
        return requireExpected(path, expectedRevision, SAVE_CONFLICT_MESSAGE);
    }

    public static long requireExpected(File file, Long expectedRevision, String message) throws WebConsoleRegistry.RevisionConflictException {
        return requireExpected(file == null ? null : file.toPath(), expectedRevision, message);
    }

    public static long requireExpected(Path path, Long expectedRevision, String message) throws WebConsoleRegistry.RevisionConflictException {
        long current = revision(path);
        if (expectedRevision != null && current != 0L && current != expectedRevision) {
            throw new WebConsoleRegistry.RevisionConflictException(message, current);
        }
        return current;
    }

    public static long advance(File file, long previousRevision) throws IOException {
        return advance(file.toPath(), previousRevision);
    }

    public static long advance(Path path, long previousRevision) throws IOException {
        long nextRevision = revision(path);
        if (previousRevision > 0L && nextRevision <= previousRevision) {
            nextRevision = previousRevision + 1L;
            Files.setLastModifiedTime(path, FileTime.fromMillis(nextRevision));
        }
        return nextRevision;
    }

    static Map<String, Object> conflictPayload(String message, long currentRevision) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("success", false);
        payload.put("error", message == null || message.isBlank() ? SAVE_CONFLICT_MESSAGE : message);
        payload.put("errorType", "revision_conflict");
        payload.put("revision", currentRevision);
        payload.put("currentRevision", currentRevision);
        return payload;
    }
}
