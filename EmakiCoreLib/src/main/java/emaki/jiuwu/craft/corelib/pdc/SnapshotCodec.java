package emaki.jiuwu.craft.corelib.pdc;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import emaki.jiuwu.craft.corelib.api.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.math.Numbers;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.corelib.api.yaml.YamlFiles;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;

public interface SnapshotCodec<T> {

    String SCHEMA_VERSION_FIELD = "schema_version";
    String SNAPSHOT_SIGNATURE_FIELD = "_snapshot_signature";

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
                return YamlFiles.dump(normalize(data));
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

    static <T> SnapshotCodec<T> versionedYaml(int currentSchemaVersion,
            Function<T, Map<String, Object>> encoder,
            Function<Map<String, Object>, T> decoder) {
        return versionedYaml(currentSchemaVersion, encoder, decoder, VersionedYamlOptions.defaults());
    }

    static <T> SnapshotCodec<T> versionedYaml(int currentSchemaVersion,
            Function<T, Map<String, Object>> encoder,
            Function<Map<String, Object>, T> decoder,
            VersionedYamlOptions options) {
        int schemaVersion = Math.max(1, currentSchemaVersion);
        VersionedYamlOptions effectiveOptions = options == null ? VersionedYamlOptions.defaults() : options;
        return new SnapshotCodec<>() {
            @Override
            public String encode(T value) {
                if (value == null) {
                    return "";
                }
                Map<String, Object> encoded = normalize(encoder == null ? Map.of() : encoder.apply(value));
                LinkedHashMap<String, Object> data = new LinkedHashMap<>(encoded);
                data.putIfAbsent(SCHEMA_VERSION_FIELD, schemaVersion);
                if (effectiveOptions.signPayload()) {
                    data.remove(SNAPSHOT_SIGNATURE_FIELD);
                    data.put(SNAPSHOT_SIGNATURE_FIELD, SignatureUtil.stableSignature(data));
                }
                String payload = YamlFiles.dump(data);
                if (effectiveOptions.maxPayloadLength() > 0 && payload.length() > effectiveOptions.maxPayloadLength()) {
                    return "";
                }
                return payload;
            }

            @Override
            public T decode(String payload) {
                if (Texts.isBlank(payload)) {
                    return null;
                }
                if (effectiveOptions.maxPayloadLength() > 0 && payload.length() > effectiveOptions.maxPayloadLength()) {
                    return null;
                }
                YamlSection configuration = YamlFiles.load(payload);
                Map<String, Object> loaded = normalize(configuration.asMap());
                if (loaded.isEmpty()) {
                    return null;
                }
                if (!signatureValid(loaded)) {
                    return null;
                }
                LinkedHashMap<String, Object> data = withoutSignature(loaded);
                int payloadVersion = Numbers.tryParseInt(data.get(SCHEMA_VERSION_FIELD), schemaVersion);
                data.putIfAbsent(SCHEMA_VERSION_FIELD, payloadVersion);
                Map<String, Object> migrated = migrate(data, payloadVersion, schemaVersion, effectiveOptions);
                return decoder == null ? null : decoder.apply(migrated);
            }

            private boolean signatureValid(Map<String, Object> data) {
                Object signature = data.get(SNAPSHOT_SIGNATURE_FIELD);
                if (Texts.isBlank(signature)) {
                    return true;
                }
                LinkedHashMap<String, Object> unsigned = withoutSignature(data);
                return SignatureUtil.stableSignature(unsigned).equals(Texts.toStringSafe(signature));
            }
        };
    }

    private static Map<String, Object> migrate(Map<String, Object> data,
            int fromVersion,
            int currentVersion,
            VersionedYamlOptions options) {
        LinkedHashMap<String, Object> migrated = new LinkedHashMap<>(data);
        int version = Math.max(1, fromVersion);
        while (version < currentVersion) {
            UnaryOperator<Map<String, Object>> migration = options.migrations().get(version);
            if (migration != null) {
                Map<String, Object> next = migration.apply(new LinkedHashMap<>(migrated));
                migrated = new LinkedHashMap<>(normalize(next));
            }
            version++;
            migrated.put(SCHEMA_VERSION_FIELD, version);
        }
        migrated.putIfAbsent(SCHEMA_VERSION_FIELD, currentVersion);
        return new LinkedHashMap<>(migrated);
    }

    private static LinkedHashMap<String, Object> withoutSignature(Map<String, Object> data) {
        LinkedHashMap<String, Object> unsigned = new LinkedHashMap<>(data);
        unsigned.remove(SNAPSHOT_SIGNATURE_FIELD);
        return unsigned;
    }

    private static Map<String, Object> normalize(Map<String, Object> data) {
        if (data == null || data.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            normalized.put(entry.getKey(), ConfigNodes.toPlainData(entry.getValue()));
        }
        return normalized;
    }

    record VersionedYamlOptions(int maxPayloadLength,
            boolean signPayload,
            Map<Integer, UnaryOperator<Map<String, Object>>> migrations) {

        public VersionedYamlOptions {
            maxPayloadLength = Math.max(0, maxPayloadLength);
            migrations = migrations == null || migrations.isEmpty() ? Map.of() : Map.copyOf(migrations);
        }

        public static VersionedYamlOptions defaults() {
            return new VersionedYamlOptions(16_384, true, Map.of());
        }

        public static VersionedYamlOptions unsigned(int maxPayloadLength) {
            return new VersionedYamlOptions(maxPayloadLength, false, Map.of());
        }

        public VersionedYamlOptions withMigration(int fromSchemaVersion, UnaryOperator<Map<String, Object>> migration) {
            if (fromSchemaVersion <= 0 || migration == null) {
                return this;
            }
            LinkedHashMap<Integer, UnaryOperator<Map<String, Object>>> copy = new LinkedHashMap<>(migrations);
            copy.put(fromSchemaVersion, migration);
            return new VersionedYamlOptions(maxPayloadLength, signPayload, copy);
        }
    }
}
