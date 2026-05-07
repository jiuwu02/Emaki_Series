package emaki.jiuwu.craft.item.model;

import java.util.LinkedHashMap;
import java.util.Map;

public record ItemUpdatePolicy(int version,
        Boolean enabled,
        Boolean preserveAmount,
        Boolean preserveDamage,
        Boolean preserveUnknownAttributeSources,
        TriggerPolicy triggers) {

    public ItemUpdatePolicy {
        version = Math.max(0, version);
        triggers = triggers == null ? TriggerPolicy.empty() : triggers;
    }

    public static ItemUpdatePolicy defaults() {
        return new ItemUpdatePolicy(0, false, null, null, null, TriggerPolicy.empty());
    }

    public boolean updateEnabled() {
        return Boolean.TRUE.equals(enabled) && version > 0;
    }

    public ItemUpdateConfig resolve() {
        return new ItemUpdateConfig(
                updateEnabled(),
                preserveAmount == null ? true : preserveAmount,
                preserveDamage == null ? true : preserveDamage,
                preserveUnknownAttributeSources == null ? true : preserveUnknownAttributeSources,
                triggers.resolve()
        );
    }

    public Map<String, Object> signatureData() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("version", version);
        if (enabled != null) {
            data.put("enabled", enabled);
        }
        if (preserveAmount != null) {
            data.put("preserve_amount", preserveAmount);
        }
        if (preserveDamage != null) {
            data.put("preserve_damage", preserveDamage);
        }
        if (preserveUnknownAttributeSources != null) {
            data.put("preserve_unknown_attribute_sources", preserveUnknownAttributeSources);
        }
        data.put("triggers", triggers.signatureData());
        return Map.copyOf(data);
    }

    public record TriggerPolicy(Boolean join,
            Boolean heldChange,
            Boolean inventoryClick,
            Boolean inventoryDrag,
            Boolean pickup,
            Boolean interact,
            Boolean command) {

        public static TriggerPolicy empty() {
            return new TriggerPolicy(null, null, null, null, null, null, null);
        }

        public ItemUpdateConfig.TriggerConfig resolve() {
            return new ItemUpdateConfig.TriggerConfig(
                    join == null ? true : join,
                    heldChange == null ? true : heldChange,
                    inventoryClick == null ? true : inventoryClick,
                    inventoryDrag == null ? true : inventoryDrag,
                    pickup == null ? true : pickup,
                    interact == null ? true : interact,
                    command == null ? true : command
            );
        }

        public Map<String, Object> signatureData() {
            Map<String, Object> data = new LinkedHashMap<>();
            if (join != null) {
                data.put("join", join);
            }
            if (heldChange != null) {
                data.put("held_change", heldChange);
            }
            if (inventoryClick != null) {
                data.put("inventory_click", inventoryClick);
            }
            if (inventoryDrag != null) {
                data.put("inventory_drag", inventoryDrag);
            }
            if (pickup != null) {
                data.put("pickup", pickup);
            }
            if (interact != null) {
                data.put("interact", interact);
            }
            if (command != null) {
                data.put("command", command);
            }
            return Map.copyOf(data);
        }
    }
}
