package emaki.jiuwu.craft.corelib.api.yaml;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import dev.dejvokep.boostedyaml.YamlDocument;
import dev.dejvokep.boostedyaml.libs.org.snakeyaml.engine.v2.common.FlowStyle;
import dev.dejvokep.boostedyaml.settings.dumper.DumperSettings;
import dev.dejvokep.boostedyaml.settings.general.GeneralSettings;
import dev.dejvokep.boostedyaml.settings.loader.LoaderSettings;
import dev.dejvokep.boostedyaml.settings.updater.UpdaterSettings;

import emaki.jiuwu.craft.corelib.api.yaml.BoostedYamlSection;
import emaki.jiuwu.craft.corelib.api.yaml.MapYamlSection;
import emaki.jiuwu.craft.corelib.api.yaml.YamlLoadException;

public final class BoostedYamlSupport {

    private static final GeneralSettings GENERAL_SETTINGS = GeneralSettings.builder()
            .setUseDefaults(false)
            .build();
    private static final LoaderSettings LOADER_SETTINGS = LoaderSettings.builder()
            .setCreateFileIfAbsent(true)
            .setAutoUpdate(false)
            .build();
    private static final DumperSettings DUMPER_SETTINGS = DumperSettings.builder()
            .setIndentation(2)
            .setFlowStyle(FlowStyle.BLOCK)
            .build();
    private static final UpdaterSettings UPDATER_SETTINGS = UpdaterSettings.builder()
            .setAutoSave(false)
            .setKeepAll(true)
            .setOptionSorting(UpdaterSettings.OptionSorting.SORT_BY_DEFAULTS)
            .build();
    private static final byte[] EMPTY_DOCUMENT = "{}\n".getBytes(StandardCharsets.UTF_8);

    private BoostedYamlSupport() {
    }

    public static MapYamlSection load(InputStream inputStream) {
        if (inputStream == null) {
            return new MapYamlSection();
        }
        try {
            YamlDocument document = YamlDocument.create(
                    inputStream,
                    GENERAL_SETTINGS,
                    LOADER_SETTINGS,
                    DUMPER_SETTINGS,
                    UPDATER_SETTINGS
            );
            return new MapYamlSection(new BoostedYamlSection(document).asMap());
        } catch (Exception exception) {
            throw new YamlLoadException("Failed to parse YAML input: " + safeMessage(exception), exception);
        }
    }

    public static MapYamlSection load(String payload) {
        if (payload == null) {
            return new MapYamlSection();
        }
        try (InputStream inputStream = new ByteArrayInputStream(payload.getBytes(StandardCharsets.UTF_8))) {
            return load(inputStream);
        } catch (YamlLoadException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new YamlLoadException("Failed to read YAML payload: " + safeMessage(exception), exception);
        }
    }

    public static MapYamlSection load(Reader reader) {
        if (reader == null) {
            return new MapYamlSection();
        }
        try {
            StringWriter writer = new StringWriter();
            reader.transferTo(writer);
            return load(writer.toString());
        } catch (YamlLoadException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new YamlLoadException("Failed to read YAML stream: " + safeMessage(exception), exception);
        }
    }

    public static String dump(Map<String, ?> values) {
        Map<String, Object> normalized = values == null ? new LinkedHashMap<>() : MapYamlSection.normalizeMap(values);
        try (InputStream inputStream = new ByteArrayInputStream(EMPTY_DOCUMENT)) {
            YamlDocument document = YamlDocument.create(
                    inputStream,
                    GENERAL_SETTINGS,
                    LOADER_SETTINGS,
                    DUMPER_SETTINGS,
                    UPDATER_SETTINGS
            );
            document.clear();
            for (Map.Entry<String, Object> entry : normalized.entrySet()) {
                document.set(entry.getKey(), entry.getValue());
            }
            return document.dump(DUMPER_SETTINGS);
        } catch (Exception exception) {
            return "";
        }
    }

    private static String safeMessage(Throwable throwable) {
        if (throwable == null || throwable.getMessage() == null || throwable.getMessage().isBlank()) {
            return throwable == null ? "unknown error" : throwable.getClass().getSimpleName();
        }
        return throwable.getMessage();
    }
}
