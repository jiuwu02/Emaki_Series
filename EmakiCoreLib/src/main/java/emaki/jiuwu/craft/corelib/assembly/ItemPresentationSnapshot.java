package emaki.jiuwu.craft.corelib.assembly;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import emaki.jiuwu.craft.corelib.api.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.pdc.SnapshotCodec;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.api.yaml.YamlFiles;

record ItemPresentationSnapshot(String customName,
        List<String> lore,
        boolean assemblyNameOverlay,
        boolean assemblyNameOverlayKnown) {

    private static final String ASSEMBLY_NAME_OVERLAY = "assembly_name_overlay";

    static final SnapshotCodec<ItemPresentationSnapshot> CODEC = SnapshotCodec.versionedYaml(
            2,
            ItemPresentationSnapshot::toMap,
            ItemPresentationSnapshot::fromMap
    );

    ItemPresentationSnapshot(String customName, List<String> lore) {
        this(customName, lore, false, true);
    }

    ItemPresentationSnapshot(String customName, List<String> lore, boolean assemblyNameOverlay) {
        this(customName, lore, assemblyNameOverlay, true);
    }

    ItemPresentationSnapshot {
        customName = Texts.toStringSafe(customName);
        lore = lore == null || lore.isEmpty()
                ? List.of()
                : lore.stream().map(Texts::toStringSafe).toList();
    }

    static ItemPresentationSnapshot decodeStrict(String payload) {
        if (Texts.isBlank(payload)) {
            return null;
        }
        try {
            Map<String, Object> data = YamlFiles.load(payload).asMap();
            if (Texts.isBlank(data.get(SnapshotCodec.SNAPSHOT_SIGNATURE_FIELD))) {
                return null;
            }
            return CODEC.decode(payload);
        } catch (RuntimeException _) {
            return null;
        }
    }

    Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("custom_name", customName);
        map.put("lore", new ArrayList<>(lore));
        map.put(ASSEMBLY_NAME_OVERLAY, assemblyNameOverlay);
        return map;
    }

    static ItemPresentationSnapshot fromMap(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return null;
        }
        List<String> lore = new ArrayList<>();
        for (Object line : ConfigNodes.asObjectList(map.get("lore"))) {
            lore.add(Texts.toStringSafe(line));
        }
        boolean assemblyNameOverlayKnown = map.containsKey(ASSEMBLY_NAME_OVERLAY);
        return new ItemPresentationSnapshot(
                Texts.toStringSafe(map.get("custom_name")),
                lore,
                ConfigNodes.bool(map, ASSEMBLY_NAME_OVERLAY, false),
                assemblyNameOverlayKnown
        );
    }
}
