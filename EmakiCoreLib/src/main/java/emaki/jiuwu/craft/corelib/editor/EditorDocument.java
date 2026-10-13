package emaki.jiuwu.craft.corelib.editor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.api.yaml.YamlFiles;
import emaki.jiuwu.craft.corelib.file.FileRevisions;

public final class EditorDocument {

    public enum SaveStatus {
        SAVED,
        VALIDATION_FAILED,
        CONFLICT,
        IO_ERROR
    }

    public record SaveResult(SaveStatus status, long revision, String detail) {

        public boolean saved() {
            return status == SaveStatus.SAVED;
        }
    }

    private static final long MAX_TEXT_BYTES = 512L * 1024L;

    private final Logger logger;
    private final Path file;
    private final String docId;
    private final String docKey;
    private final EditorValidator validator;
    private YamlTextDocument document;
    private long expectedRevision;
    private boolean dirty;

    private EditorDocument(Logger logger,
            Path file,
            String docId,
            String docKey,
            EditorValidator validator,
            YamlTextDocument document,
            long revision) {
        this.logger = logger;
        this.file = file;
        this.docId = docId;
        this.docKey = docKey;
        this.validator = validator;
        this.document = document;
        this.expectedRevision = revision;
    }

    public static EditorDocument open(Logger logger, Path file, EditorValidator validator) throws IOException {
        String text = Files.readString(file, StandardCharsets.UTF_8);
        YamlTextDocument parsed = YamlTextDocument.parse(text);
        Object rawId = parsed.value("id");
        String docId = Texts.normalizeId(rawId == null ? "" : Texts.toStringSafe(rawId));
        String docKey = docId.isEmpty() ? fileStem(file) : docId;
        return new EditorDocument(logger, file, docId, docKey, validator, parsed, FileRevisions.revision(file));
    }

    private static String fileStem(Path file) {
        String name = file.getFileName() == null ? "" : file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    public String docId() {
        return docId;
    }

    public String docKey() {
        return docKey;
    }

    public Path path() {
        return file;
    }

    public YamlTextDocument view() {
        return document;
    }

    public long expectedRevision() {
        return expectedRevision;
    }

    public SaveResult mutate(Consumer<YamlTextDocument> mutation) {
        return mutate(mutation, null);
    }

    public SaveResult mutate(Consumer<YamlTextDocument> mutation,
            Function<YamlTextDocument, String> extraCheck) {
        YamlTextDocument candidate = YamlTextDocument.parse(document.text());
        mutation.accept(candidate);
        String problem = validate(candidate);
        if (problem == null && extraCheck != null) {
            problem = extraCheck.apply(candidate);
        }
        if (problem != null) {
            return new SaveResult(SaveStatus.VALIDATION_FAILED, expectedRevision, problem);
        }
        String text = candidate.text();
        if (text.getBytes(StandardCharsets.UTF_8).length > MAX_TEXT_BYTES) {
            return new SaveResult(SaveStatus.VALIDATION_FAILED, expectedRevision, "document_too_large");
        }
        document = candidate;
        dirty = true;
        return new SaveResult(SaveStatus.SAVED, expectedRevision, "");
    }

    public SaveResult flush(Path backupRoot) {
        if (!dirty) {
            return new SaveResult(SaveStatus.SAVED, expectedRevision, "");
        }
        try {
            requireWritable();
            long current = FileRevisions.requireExpected(file, expectedRevision);
            if (Files.exists(file)) {
                writeBackup(backupRoot, Files.readString(file, StandardCharsets.UTF_8));
            }
            writeDirect(document.text());
            if (YamlFiles.load(document.text()) == null) {
                return new SaveResult(SaveStatus.IO_ERROR, expectedRevision, "post_write_unreadable");
            }
            expectedRevision = FileRevisions.advance(file, current);
            dirty = false;
            return new SaveResult(SaveStatus.SAVED, expectedRevision, "");
        } catch (FileRevisions.RevisionConflictException conflict) {
            return new SaveResult(SaveStatus.CONFLICT, conflict.currentRevision(), "revision_conflict");
        } catch (IOException | RuntimeException failure) {
            logger.log(Level.WARNING, "[editor] 无法持久化定义文件 " + file + ": " + failure, failure);
            return new SaveResult(SaveStatus.IO_ERROR, expectedRevision, Texts.toStringSafe(failure.getMessage()));
        }
    }

    public void discard() {
        try {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            document = YamlTextDocument.parse(text);
            expectedRevision = FileRevisions.revision(file);
            dirty = false;
        } catch (IOException failure) {
            logger.log(Level.WARNING, "[editor] 无法重新加载定义文件 " + file + ": " + failure, failure);
        }
    }

    private String validate(YamlTextDocument candidate) {
        return validator.validate(candidate, docId, file);
    }

    private void requireWritable() throws IOException {
        if (Files.exists(file)) {
            if (!Files.isRegularFile(file) || Files.isSymbolicLink(file)) {
                throw new IOException("定义路径不是常规文件: " + file);
            }
        } else {
            Files.createDirectories(file.getParent());
        }
    }

    private void writeBackup(Path backupRoot, String text) throws IOException {
        if (backupRoot == null) {
            return;
        }
        Path target = backupRoot.resolve(Long.toString(System.currentTimeMillis())).resolve(docKey + ".yml");
        Files.createDirectories(target.getParent());
        Files.writeString(target, text, StandardCharsets.UTF_8);
    }

    private void writeDirect(String text) throws IOException {
        Files.writeString(file, text, StandardCharsets.UTF_8);
    }
}
