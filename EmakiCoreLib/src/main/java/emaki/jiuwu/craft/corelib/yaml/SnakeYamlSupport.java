package emaki.jiuwu.craft.corelib.yaml;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.representer.Representer;

public final class SnakeYamlSupport {

    private SnakeYamlSupport() {
    }

    public static MapYamlSection load(InputStream inputStream) {
        if (inputStream == null) {
            return new MapYamlSection();
        }
        try (Reader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {
            return load(reader);
        } catch (Exception exception) {
            return new MapYamlSection();
        }
    }

    public static MapYamlSection load(String payload) {
        if (payload == null) {
            return new MapYamlSection();
        }
        try (StringReader reader = new StringReader(payload)) {
            return load(reader);
        } catch (Exception exception) {
            return new MapYamlSection();
        }
    }

    public static MapYamlSection load(Reader reader) {
        if (reader == null) {
            return new MapYamlSection();
        }
        Object loaded = yaml().load(reader);
        if (loaded instanceof Map<?, ?> map) {
            return new MapYamlSection(MapYamlSection.normalizeMap(map));
        }
        return new MapYamlSection();
    }

    public static String dump(Map<String, ?> values) {
        Map<String, Object> normalized = values == null ? new LinkedHashMap<>() : MapYamlSection.normalizeMap(values);
        return yaml().dump(normalized);
    }

    private static Yaml yaml() {
        LoaderOptions loaderOptions = new LoaderOptions();
        loaderOptions.setAllowDuplicateKeys(true);
        DumperOptions dumperOptions = new DumperOptions();
        dumperOptions.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        dumperOptions.setPrettyFlow(true);
        dumperOptions.setIndent(2);
        // SnakeYAML 2.x requires indicator indent to be smaller than the base indent.
        dumperOptions.setIndicatorIndent(1);
        dumperOptions.setProcessComments(true);
        dumperOptions.setWidth(160);
        Representer representer = new Representer(dumperOptions);
        representer.getPropertyUtils().setSkipMissingProperties(true);
        return new Yaml(new SafeConstructor(loaderOptions), representer, dumperOptions, loaderOptions);
    }
}
