package emaki.jiuwu.craft.corelib.pdc;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

import emaki.jiuwu.craft.corelib.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.corelib.yaml.YamlFiles;
import emaki.jiuwu.craft.corelib.yaml.YamlSection;

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
                Map<String, Object> normalized = new LinkedHashMap<>();
                for (Map.Entry<String, Object> entry : data.entrySet()) {
                    normalized.put(entry.getKey(), ConfigNodes.toPlainData(entry.getValue()));
                }
                return YamlFiles.dump(normalized);
            }

            @Override
            public T decode(String payload) {
                if (Texts.isBlank(payload)) {
                    return null;
                }
                YamlSection configuration = YamlFiles.load(payload);
                Map<String, Object> data = configuration.asMap();
                return decoder == null ? null : decoder.apply(data);
            }
        };
    }
}
