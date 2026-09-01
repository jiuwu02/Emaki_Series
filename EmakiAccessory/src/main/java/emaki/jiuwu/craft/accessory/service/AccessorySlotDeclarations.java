package emaki.jiuwu.craft.accessory.service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import emaki.jiuwu.craft.accessory.config.AccessorySlotSourceConfig;
import emaki.jiuwu.craft.attribute.api.EmakiAttributeApi;
import emaki.jiuwu.craft.attribute.api.model.PdcAttributePayload;
import emaki.jiuwu.craft.corelib.api.item.EquipmentSlotMatcher;
import emaki.jiuwu.craft.corelib.api.item.ItemTextBridge;
import emaki.jiuwu.craft.corelib.api.text.MiniMessages;
import emaki.jiuwu.craft.corelib.api.text.Texts;

public final class AccessorySlotDeclarations {

    private AccessorySlotDeclarations() {
    }

    public static Set<String> read(ItemStack item, AccessorySlotSourceConfig config) {
        Set<String> declared = new LinkedHashSet<>();
        if (item == null || item.getType().isAir()) {
            return Set.of();
        }
        collectAttributePayloads(item, declared);
        collectSkillPayload(item, declared);
        if (config != null) {
            collectPdc(item, config, declared);
            collectLore(item, config, declared);
        }
        return Set.copyOf(declared);
    }

    public static boolean matchesAny(String slotInstanceId, Set<String> declared) {
        if (declared == null || declared.isEmpty()) {
            return true;
        }
        for (String candidate : declared) {
            if (AccessoryPartRegistry.matchesAccessorySlot(slotInstanceId, candidate)) {
                return true;
            }
        }
        return false;
    }

    public static String describe(Set<String> declared) {
        if (declared == null || declared.isEmpty()) {
            return EquipmentSlotMatcher.SLOT_ALL;
        }
        return String.join(", ", declared);
    }

    private static void collectAttributePayloads(ItemStack item, Set<String> declared) {
        if (!EmakiAttributeApi.status().usable()) {
            return;
        }
        Map<String, PdcAttributePayload> payloads = EmakiAttributeApi.extensions().pdc().readAll(item);
        for (PdcAttributePayload payload : payloads.values()) {
            if (payload == null) {
                continue;
            }
            addNormalized(payload.meta().get(EquipmentSlotMatcher.ACTIVE_SLOT_META_KEY), declared);
        }
    }

    private static void collectSkillPayload(ItemStack item, Set<String> declared) {
        AccessorySkillPayloadCodec.Payload payload = AccessorySkillPayloadCodec.read(item);
        if (payload.present()) {
            addNormalized(payload.activeSlot(), declared);
        }
    }

    private static void collectPdc(ItemStack item, AccessorySlotSourceConfig config, Set<String> declared) {
        if (!config.pdcUsable()) {
            return;
        }
        NamespacedKey key = NamespacedKey.fromString(config.pdcKey());
        if (key == null) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        PersistentDataContainer container = meta.getPersistentDataContainer();
        if (container.has(key, PersistentDataType.LIST.strings())) {
            List<String> listed = container.get(key, PersistentDataType.LIST.strings());
            if (listed != null) {
                for (String value : listed) {
                    addAliased(value, config, declared);
                }
                return;
            }
        }
        if (!container.has(key, PersistentDataType.STRING)) {
            return;
        }
        String joined = container.get(key, PersistentDataType.STRING);
        if (Texts.isBlank(joined)) {
            return;
        }
        for (String value : joined.split(Pattern.quote(config.separator()))) {
            addAliased(value, config, declared);
        }
    }

    private static void collectLore(ItemStack item, AccessorySlotSourceConfig config, Set<String> declared) {
        if (!config.loreUsable()) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        List<Pattern> patterns = config.lorePatterns();
        for (Object entry : ItemTextBridge.loreLines(meta)) {
            String line = MiniMessages.toMiniMessage(entry);
            line = Texts.stripMiniTags(line);
            line = Texts.normalizeWhitespace(line);
            if (line.isBlank()) {
                continue;
            }
            for (Pattern pattern : patterns) {
                Matcher matcher = pattern.matcher(line);
                if (!matcher.find() || matcher.groupCount() < 1) {
                    continue;
                }
                String captured = matcher.group(1);
                if (Texts.isBlank(captured)) {
                    continue;
                }
                for (String value : captured.split(Pattern.quote(config.separator()))) {
                    addAliased(value, config, declared);
                }
            }
        }
    }

    private static void addAliased(String raw, AccessorySlotSourceConfig config, Set<String> declared) {
        String resolved = config.resolveAlias(raw);
        if (Texts.isNotBlank(resolved)) {
            declared.add(resolved);
        }
    }

    private static void addNormalized(String raw, Set<String> declared) {
        String normalized = Texts.normalizeId(raw);
        if (Texts.isNotBlank(normalized)) {
            declared.add(normalized);
        }
    }
}
