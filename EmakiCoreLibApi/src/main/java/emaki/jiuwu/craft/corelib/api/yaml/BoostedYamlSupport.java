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

/**
 * Load and dump helpers that adapt BoostedYAML to the {@link YamlSection} contract with one
 * fixed settings profile.
 *
 * <p>The profile loads without bundled defaults or auto-update and dumps block-style YAML with
 * two-space indentation, so repository files keep a uniform shape. Every {@code load} overload
 * answers an empty {@link MapYamlSection} for {@code null} input and wraps parse failures in
 * {@link YamlLoadException}; {@link #dump(Map)} never throws and falls back to an empty string.
 */
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

    /**
     * Parses YAML text from a stream.
     *
     * @param inputStream source to read; {@code null} yields an empty section
     * @return the parsed document as a plain map-backed section; never {@code null}
     * @throws YamlLoadException if the document cannot be parsed
     */
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
            throw new YamlLoadException("解析 YAML 输入失败: " + safeMessage(exception), exception);
        }
    }

    /**
     * Parses YAML text.
     *
     * @param payload document text; {@code null} yields an empty section
     * @return the parsed document as a plain map-backed section; never {@code null}
     * @throws YamlLoadException if the document cannot be parsed
     */
    public static MapYamlSection load(String payload) {
        if (payload == null) {
            return new MapYamlSection();
        }
        try (InputStream inputStream = new ByteArrayInputStream(payload.getBytes(StandardCharsets.UTF_8))) {
            return load(inputStream);
        } catch (YamlLoadException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new YamlLoadException("读取 YAML 内容失败: " + safeMessage(exception), exception);
        }
    }

    /**
     * Parses YAML text from a character reader, draining it fully into memory first.
     *
     * @param reader source to read; {@code null} yields an empty section
     * @return the parsed document as a plain map-backed section; never {@code null}
     * @throws YamlLoadException if the reader fails or the document cannot be parsed
     */
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
            throw new YamlLoadException("读取 YAML 流失败: " + safeMessage(exception), exception);
        }
    }

    /**
     * Renders a map as block-style YAML.
     *
     * <p>Keys are normalized to strings and nested structures to plain maps and lists first, so
     * the dump always uses block style with two-space indentation and never contains flow-style
     * mappings or sequences.
     *
     * @param values values to render; {@code null} renders an empty document
     * @return the YAML text, or an empty string when the document cannot be rendered (this
     *         method never throws)
     */
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
            return throwable == null ? "未知错误" : throwable.getClass().getSimpleName();
        }
        return throwable.getMessage();
    }
}
