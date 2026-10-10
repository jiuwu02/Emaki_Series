package emaki.jiuwu.craft.item.editor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.api.yaml.YamlFiles;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;
import emaki.jiuwu.craft.corelib.file.FileRevisions;
import emaki.jiuwu.craft.item.model.EmakiItemDefinition;
import emaki.jiuwu.craft.item.model.EmakiItemDefinitionParser;

public final class ItemDefinitionDocument {

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

    @FunctionalInterface
    public interface DraftValidator {

        String validate(YamlTextDocument candidate, String itemId, Path source);
    }

    private final Logger logger;
    private final Path file;
    private final String itemId;
    private final DraftValidator validator;
    private YamlTextDocument document;
    private long expectedRevision;
    private boolean dirty;

    private ItemDefinitionDocument(Logger logger,
            Path file,
            String itemId,
            DraftValidator validator,
            YamlTextDocument document,
            long revision) {
        this.logger = logger;
        this.file = file;
        this.itemId = itemId;
        this.validator = validator;
        this.document = document;
        this.expectedRevision = revision;
    }

    public static ItemDefinitionDocument open(Logger logger, Path file, DraftValidator validator) throws IOException {
        String text = Files.readString(file, StandardCharsets.UTF_8);
        YamlTextDocument parsed = YamlTextDocument.parse(text);
        Object rawId = parsed.value("id");
        String itemId = Texts.normalizeId(rawId == null ? "" : Texts.toStringSafe(rawId));
        return new ItemDefinitionDocument(logger, file, itemId, validator, parsed, FileRevisions.revision(file));
    }

    public static DraftValidator definitionParserValidator(Logger logger) {
        return (candidate, itemId, source) -> {
            try {
                YamlSection section = YamlFiles.load(candidate.text());
                if (section == null) {
                    return "unparseable_yaml";
                }
                EmakiItemDefinition definition = new EmakiItemDefinitionParser(logger).parse(section, source.toString());
                if (definition == null) {
                    return "definition_parse_failed";
                }
                if (!itemId.equals(definition.id())) {
                    return "id_changed";
                }
            } catch (RuntimeException failure) {
                return "unparseable_yaml";
            }
            return null;
        };
    }

    public String itemId() {
        return itemId;
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
        YamlTextDocument candidate = YamlTextDocument.parse(document.text());
        mutation.accept(candidate);
        String problem = validate(candidate);
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
            writeAtomically(document.text());
            if (YamlFiles.load(document.text()) == null) {
                return new SaveResult(SaveStatus.IO_ERROR, expectedRevision, "post_write_unreadable");
            }
            expectedRevision = FileRevisions.advance(file, current);
            dirty = false;
            return new SaveResult(SaveStatus.SAVED, expectedRevision, "");
        } catch (FileRevisions.RevisionConflictException conflict) {
            return new SaveResult(SaveStatus.CONFLICT, conflict.currentRevision(), "revision_conflict");
        } catch (IOException | RuntimeException failure) {
            logger.log(Level.WARNING, "[editor] 无法持久化 EmakiItem 定义 " + file + ": " + failure, failure);
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
            logger.log(Level.WARNING, "[editor] 无法重新加载 EmakiItem 定义 " + file + ": " + failure, failure);
        }
    }

    private String validate(YamlTextDocument candidate) {
        return validator.validate(candidate, itemId, file);
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
        Path target = backupRoot.resolve(Long.toString(System.currentTimeMillis())).resolve(itemId + ".yml");
        Files.createDirectories(target.getParent());
        Files.writeString(target, text, StandardCharsets.UTF_8);
    }

    private void writeAtomically(String text) throws IOException {
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(temporary, text, StandardCharsets.UTF_8);
        try {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException failure) {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
