package emaki.jiuwu.craft.item.model;

public record SetBonusConfig(boolean enabled, ItemUpdateConfig.TriggerConfig refreshTriggers) {

    public SetBonusConfig {
        refreshTriggers = refreshTriggers == null ? ItemUpdateConfig.TriggerConfig.defaults() : refreshTriggers;
    }

    public static SetBonusConfig defaults() {
        return new SetBonusConfig(true, ItemUpdateConfig.TriggerConfig.defaults());
    }

    public boolean triggerEnabled(String trigger) {
        return enabled && refreshTriggers.enabled(trigger);
    }

    public boolean triggerEnabled(Iterable<String> triggers) {
        return enabled && refreshTriggers.enabled(triggers);
    }

    public String effectiveTrigger(Iterable<String> triggers) {
        return enabled ? refreshTriggers.effectiveTrigger(triggers) : null;
    }
}
