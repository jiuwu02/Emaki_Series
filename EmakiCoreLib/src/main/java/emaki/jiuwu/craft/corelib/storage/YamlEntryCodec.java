package emaki.jiuwu.craft.corelib.storage;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import emaki.jiuwu.craft.corelib.api.yaml.YamlFiles;

final class YamlEntryCodec {

    private YamlEntryCodec() {
    }

    static Map<String, byte[]> encode(Map<String, Object> document) {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        if (document == null) {
            return entries;
        }
        for (Map.Entry<String, Object> entry : document.entrySet()) {
            Map<String, Object> single = new LinkedHashMap<>();
            single.put(entry.getKey(), entry.getValue());
            entries.put(entry.getKey(), YamlFiles.dump(single).getBytes(StandardCharsets.UTF_8));
        }
        return entries;
    }

    static Map<String, Object> decode(Map<String, byte[]> entries) {
        Map<String, Object> document = new LinkedHashMap<>();
        if (entries == null) {
            return document;
        }
        for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
            String payload = new String(entry.getValue(), StandardCharsets.UTF_8);
            Map<String, Object> parsed = YamlFiles.load(payload).asMap();
            document.put(entry.getKey(), parsed.get(entry.getKey()));
        }
        return document;
    }
}
