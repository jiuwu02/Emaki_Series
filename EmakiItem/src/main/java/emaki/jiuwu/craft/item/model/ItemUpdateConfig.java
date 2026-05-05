package emaki.jiuwu.craft.item.model;

public record ItemUpdateConfig(boolean enabled,
        boolean preserveAmount,
        boolean preserveDamage,
        boolean preserveUnknownAttributeSources,
        TriggerConfig triggers) {

    public ItemUpdateConfig {
        triggers = triggers == null ? TriggerConfig.defaults() : triggers;
    }

    public static ItemUpdateConfig defaults() {
        return new ItemUpdateConfig(true, true, true, true, TriggerConfig.defaults());
    }

    public boolean triggerEnabled(String trigger) {
        return enabled && triggers.enabled(trigger);
    }

    public record TriggerConfig(boolean join,
            boolean heldChange,
            boolean inventoryClick,
            boolean inventoryDrag,
            boolean pickup,
            boolean interact,
            boolean command) {

        public static TriggerConfig defaults() {
            return new TriggerConfig(true, true, true, true, true, true, true);
        }

        public boolean enabled(String trigger) {
            return switch (trigger == null ? "" : trigger) {
                case "join" -> join;
                case "held_change" -> heldChange;
                case "inventory_click" -> inventoryClick;
                case "inventory_drag" -> inventoryDrag;
                case "pickup" -> pickup;
                case "interact" -> interact;
                case "command" -> command;
                default -> false;
            };
        }
    }
}
