package emaki.jiuwu.craft.corelib.api.yaml;

import java.io.File;
import java.io.IOException;

import dev.dejvokep.boostedyaml.YamlDocument;

/**
 * A versioned YAML resource: the runtime document, its bundled defaults and the outcome of the
 * last version comparison.
 *
 * <p>Instances are created by {@link YamlFiles}. {@link #root()} reads the runtime document and
 * {@link #defaults()} the bundled defaults the updater merged from; either may be {@code null}
 * when the backing document (or its defaults) is absent, and the version accessors then answer
 * an empty string. {@link #save()} writes the current document state back to {@link #file()}.
 */
public final class VersionedYamlFile {

    private final File file;
    private final String resourcePath;
    private final YamlDocument document;
    private final BoostedYamlSection root;
    private final BoostedYamlSection defaults;
    private final boolean versionUpdated;
    private final String previousVersion;
    private final String updatedVersion;

    /**
     * Wraps a document without version bookkeeping.
     *
     * @param file runtime file; may be {@code null} for resource-only views
     * @param resourcePath bundled resource path this document was opened from; may be {@code null}
     * @param document opened document; {@code null} yields a view whose {@link #root()} and
     *                  {@link #defaults()} are {@code null}
     */
    public VersionedYamlFile(File file, String resourcePath, YamlDocument document) {
        this(file, resourcePath, document, false, "", "");
    }

    /**
     * Wraps a document together with the outcome of a version sync.
     *
     * @param file runtime file; may be {@code null} for resource-only views
     * @param resourcePath bundled resource path; may be {@code null}
     * @param document opened document; may be {@code null}
     * @param versionUpdated whether this pass replaced the stored version with the bundled one
     * @param previousVersion stored version before the sync; {@code null} becomes an empty string
     * @param updatedVersion bundled version written by the sync; {@code null} becomes an empty
     *                        string
     */
    public VersionedYamlFile(File file,
            String resourcePath,
            YamlDocument document,
            boolean versionUpdated,
            String previousVersion,
            String updatedVersion) {
        this.file = file;
        this.resourcePath = resourcePath;
        this.document = document;
        this.root = document == null ? null : new BoostedYamlSection(document);
        this.defaults = document == null || document.getDefaults() == null
                ? null
                : new BoostedYamlSection(document.getDefaults());
        this.versionUpdated = versionUpdated;
        this.previousVersion = previousVersion == null ? "" : previousVersion;
        this.updatedVersion = updatedVersion == null ? "" : updatedVersion;
    }

    /**
     * {@return the runtime file, or {@code null} for resource-only views}
     */
    public File file() {
        return file;
    }

    /**
     * {@return the bundled resource path this document was opened from, or {@code null} when
     * unknown}
     */
    public String resourcePath() {
        return resourcePath;
    }

    /**
     * {@return the live BoostedYAML document backing this view, or {@code null} when none was
     * supplied}
     */
    public YamlDocument document() {
        return document;
    }

    /**
     * {@return a section view over the runtime document, or {@code null} when there is no
     * document}
     */
    public YamlSection root() {
        return root;
    }

    /**
     * {@return a section view over the bundled defaults, or {@code null} when the document
     * carries no defaults}
     */
    public YamlSection defaults() {
        return defaults;
    }

    /**
     * Reads the stored version from the runtime root.
     *
     * @param key version key such as {@code version}
     * @return the value at {@code key}, or an empty string when there is no root or the key is
     *         missing
     */
    public String version(String key) {
        return root == null ? "" : root.getString(key, "");
    }

    /**
     * Reads the bundled version from the defaults.
     *
     * @param key version key such as {@code version}
     * @return the value at {@code key}, or an empty string when there are no defaults or the key
     *         is missing
     */
    public String bundledVersion(String key) {
        return defaults == null ? "" : defaults.getString(key, "");
    }

    /**
     * {@return whether the last sync replaced the stored version with the bundled one}
     */
    public boolean versionUpdated() {
        return versionUpdated;
    }

    /**
     * {@return the stored version read before the sync; never {@code null}}
     */
    public String previousVersion() {
        return previousVersion;
    }

    /**
     * {@return the bundled version written by the sync; never {@code null}}
     */
    public String updatedVersion() {
        return updatedVersion;
    }

    /**
     * Writes the current document back to {@link #file()}; a view without a document is a
     * no-op.
     *
     * @throws IOException if the text cannot be written to disk
     * @throws NullPointerException if a document is present but this view has no file
     *         (resource-only views), because the writer requires a target path
     */
    public void save() throws IOException {
        if (document != null) {
            YamlFiles.saveText(file, document.dump());
        }
    }
}
