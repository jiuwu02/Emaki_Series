package emaki.jiuwu.craft.corelib.pdc;

import java.io.StringReader;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

import org.bukkit.configuration.file.YamlConfiguration;

import emaki.jiuwu.craft.corelib.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.text.Texts;

public interface SnapshotCodec<T> {

    String encode(T value);

    T decode(String payload);

    static <T> SnapshotCodec<T> yaml(Function<T, Map<String, Object>> encoder,
            Function<Map<String, Object>, T> decoder) {
        return new SnapshotCodec<>() {
            @Override
            public String encode(T value) {
                if (value == null) {
                    return "";
                }
                Map<String, Object> data = encoder == null ? Map.of() : encoder.apply(value);
                YamlConfiguration configuration = new YamlConfiguration();
                for (Map.Entry<String, Object> entry : data.entrySet()) {
                    configuration.set(entry.getKey(), ConfigNodes.toPlainData(entry.getValue()));
                }
                return configuration.saveToString();
            }

            @Override
            public T decode(String payload) {
                if (Texts.isBlank(payload)) {
                    return null;
                }
                YamlConfiguration configuration = new YamlConfiguration();
                try {
                    configuration.load(new StringReader(payload));
                } catch (Exception exception) {
                    return null;
                }
                Map<String, Object> data = new LinkedHashMap<>(ConfigNodes.entries(configuration));
                return decoder == null ? null : decoder.apply(data);
            }
        };
    }
}
