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
        version = Math.max(1, version);
        triggers = triggers == null ? TriggerPolicy.empty() : triggers;
    }

    public static ItemUpdatePolicy defaults() {
        return new ItemUpdatePolicy(1, null, null, null, null, TriggerPolicy.empty());
    }

    public ItemUpdateConfig resolve(ItemUpdateConfig global) {
        ItemUpdateConfig base = global == null ? ItemUpdateConfig.defaults() : global;
        return new ItemUpdateConfig(
                enabled == null ? base.enabled() : enabled,
                preserveAmount == null ? base.preserveAmount() : preserveAmount,
                preserveDamage == null ? base.preserveDamage() : preserveDamage,
                preserveUnknownAttributeSources == null ? base.preserveUnknownAttributeSources() : preserveUnknownAttributeSources,
                triggers.resolve(base.triggers())
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

        public ItemUpdateConfig.TriggerConfig resolve(ItemUpdateConfig.TriggerConfig global) {
            ItemUpdateConfig.TriggerConfig base = global == null ? ItemUpdateConfig.TriggerConfig.defaults() : global;
            return new ItemUpdateConfig.TriggerConfig(
                    join == null ? base.join() : join,
                    heldChange == null ? base.heldChange() : heldChange,
                    inventoryClick == null ? base.inventoryClick() : inventoryClick,
                    inventoryDrag == null ? base.inventoryDrag() : inventoryDrag,
                    pickup == null ? base.pickup() : pickup,
                    interact == null ? base.interact() : interact,
                    command == null ? base.command() : command
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
