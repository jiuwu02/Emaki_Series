package emaki.jiuwu.craft.corelib.yaml;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import dev.dejvokep.boostedyaml.YamlDocument;
import dev.dejvokep.boostedyaml.settings.dumper.DumperSettings;
import dev.dejvokep.boostedyaml.settings.general.GeneralSettings;
import dev.dejvokep.boostedyaml.settings.loader.LoaderSettings;
import dev.dejvokep.boostedyaml.settings.updater.UpdaterSettings;

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
            return new MapYamlSection();
        }
    }

    public static MapYamlSection load(String payload) {
        if (payload == null) {
            return new MapYamlSection();
        }
        try (InputStream inputStream = new ByteArrayInputStream(payload.getBytes(StandardCharsets.UTF_8))) {
            return load(inputStream);
        } catch (Exception exception) {
            return new MapYamlSection();
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
        } catch (Exception exception) {
            return new MapYamlSection();
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
}
