package emaki.jiuwu.craft.corelib.yaml;

import java.io.File;
import java.io.IOException;

import dev.dejvokep.boostedyaml.YamlDocument;

public final class VersionedYamlFile {

    private final File file;
    private final String resourcePath;
    private final YamlDocument document;
    private final BoostedYamlSection root;
    private final BoostedYamlSection defaults;
    private final boolean versionUpdated;
    private final String previousVersion;
    private final String updatedVersion;

    public VersionedYamlFile(File file, String resourcePath, YamlDocument document) {
        this(file, resourcePath, document, false, "", "");
    }

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

    public File file() {
        return file;
    }

    public String resourcePath() {
        return resourcePath;
    }

    public YamlDocument document() {
        return document;
    }

    public YamlSection root() {
        return root;
    }

    public YamlSection defaults() {
        return defaults;
    }

    public String version(String key) {
        return root == null ? "" : root.getString(key, "");
    }

    public String bundledVersion(String key) {
        return defaults == null ? "" : defaults.getString(key, "");
    }

    public boolean versionUpdated() {
        return versionUpdated;
    }

    public String previousVersion() {
        return previousVersion;
    }

    public String updatedVersion() {
        return updatedVersion;
    }

    public void save() throws IOException {
        if (document != null) {
            document.save();
        }
    }
}
