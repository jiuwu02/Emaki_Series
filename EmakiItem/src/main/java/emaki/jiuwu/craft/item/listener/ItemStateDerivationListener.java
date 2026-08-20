package emaki.jiuwu.craft.item.listener;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.gui.SoundParser;
import emaki.jiuwu.craft.item.EmakiItemPlugin;
import emaki.jiuwu.craft.item.api.ItemStateKey;
import emaki.jiuwu.craft.item.api.ItemStateMutation;
import emaki.jiuwu.craft.item.api.event.ItemStateChangeEvent;
import emaki.jiuwu.craft.item.api.event.ItemStateThresholdEvent;
import emaki.jiuwu.craft.item.model.EmakiItemDefinition;
import emaki.jiuwu.craft.item.model.ItemStateConfig;
import emaki.jiuwu.craft.item.service.ItemStateDerivationGuard;

public final class ItemStateDerivationListener implements Listener {

    private final EmakiItemPlugin plugin;

    public ItemStateDerivationListener(EmakiItemPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onStateChanged(ItemStateChangeEvent event) {
        ItemStateConfig.Derivation derivation = plugin.stateService().config().derivation();
        if (!derivation.enabled()) {
            return;
        }
        ItemStateMutation<?> mutation = event.getMutation();
        if (mutation == null || !mutation.committed() || !mutation.changed()) {
            return;
        }
        Player holder = event.getHolder() == null
                ? scanHolder(derivation, event.getItem())
                : event.getHolder();
        if (holder == null) {
            return;
        }
        refreshDerived(holder, derivation, "item_state_change");
    }

    private Player scanHolder(ItemStateConfig.Derivation derivation, ItemStack item) {
        if (!derivation.scanHolder() || item == null || item.getType().isAir()) {
            return null;
        }
        String instanceId = plugin.stateService().snapshot(item).metadata().instanceId();
        if (Texts.isBlank(instanceId)) {
            return null;
        }
        for (Player candidate : Bukkit.getOnlinePlayers()) {
            if (holdsInstance(candidate, instanceId)) {
                return candidate;
            }
        }
        return null;
    }

    private boolean holdsInstance(Player candidate, String instanceId) {
        PlayerInventory inventory = candidate.getInventory();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack stored = inventory.getItem(slot);
            if (stored == null || stored.getType().isAir()) {
                continue;
            }
            if (instanceId.equals(plugin.stateService().snapshot(stored).metadata().instanceId())) {
                return true;
            }
        }
        return false;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onThresholdCrossed(ItemStateThresholdEvent event) {
        ItemStateConfig config = plugin.stateService().config();
        ItemStateConfig.Field field = config.field(event.getKey().key());
        if (field == null) {
            return;
        }
        ItemStateConfig.Threshold threshold = threshold(field, event.getThresholdId());
        if (threshold == null || !shouldReward(event, threshold)) {
            return;
        }
        Player holder = event.getHolder() == null
                ? scanHolder(config.derivation(), event.getItem())
                : event.getHolder();
        Map<String, Object> placeholders = placeholders(event, threshold);
        if (holder != null) {
            sendThresholdMessage(holder, threshold, placeholders);
            playThresholdSound(holder, threshold);
            runThresholdActions(holder, event.getItem(), threshold, placeholders);
        }
        if (threshold.refreshDerived() && holder != null) {
            refreshDerived(holder, config.derivation(), "item_state_threshold");
        }
    }

    private boolean shouldReward(ItemStateThresholdEvent event, ItemStateConfig.Threshold threshold) {
        return event.getDirection() == ItemStateThresholdEvent.Direction.UP || threshold.rewardOnFall();
    }

    private ItemStateConfig.Threshold threshold(ItemStateConfig.Field field, String id) {
        String normalized = Texts.toStringSafe(id).trim().toLowerCase(Locale.ROOT);
        for (ItemStateConfig.Threshold candidate : field.thresholds()) {
            if (candidate.id().equals(normalized)) {
                return candidate;
            }
        }
        return null;
    }

    private Map<String, Object> placeholders(ItemStateThresholdEvent event, ItemStateConfig.Threshold threshold) {
        ItemStateKey<?> key = event.getKey();
        Map<String, Object> placeholders = new LinkedHashMap<>();
        placeholders.put("state_key", key.key());
        placeholders.put("state_type", key.type().name().toLowerCase(Locale.ROOT));
        placeholders.put("threshold_id", threshold.id());
        placeholders.put("threshold", threshold.value().toPlainString());
        placeholders.put("old_value", event.getOldValue() == null ? "" : String.valueOf(event.getOldValue()));
        placeholders.put("new_value", event.getNewValue() == null ? "" : String.valueOf(event.getNewValue()));
        placeholders.put("direction", event.getDirection().name().toLowerCase(Locale.ROOT));
        placeholders.put("rearmed", event.isRearmed());
        return placeholders;
    }

    private void sendThresholdMessage(Player holder,
            ItemStateConfig.Threshold threshold,
            Map<String, Object> placeholders) {
        if (Texts.isBlank(threshold.messageKey())) {
            return;
        }
        plugin.messageService().send(holder, threshold.messageKey(), placeholders);
    }

    private void playThresholdSound(Player holder, ItemStateConfig.Threshold threshold) {
        if (Texts.isBlank(threshold.sound())) {
            return;
        }
        Sound sound = SoundParser.resolve(threshold.sound());
        if (sound == null) {
            plugin.messageService().warning("console.item_state_unknown_sound",
                    Map.of("sound", threshold.sound()));
            return;
        }
        holder.playSound(holder.getLocation(), sound, threshold.soundVolume(), threshold.soundPitch());
    }

    private void runThresholdActions(Player holder,
            ItemStack item,
            ItemStateConfig.Threshold threshold,
            Map<String, Object> placeholders) {
        List<String> actions = threshold.actions();
        if (actions.isEmpty()) {
            return;
        }
        EmakiItemDefinition definition = definition(item);
        if (definition == null) {
            return;
        }
        int maxDepth = plugin.stateService().config().derivation().maxDepth();
        if (!ItemStateDerivationGuard.enter(maxDepth)) {
            plugin.debugLogger().log("item_state", holder, "item_state.recursion_blocked", Map.of(
                    "stage", "threshold_actions",
                    "threshold_id", threshold.id(),
                    "depth", ItemStateDerivationGuard.depth()));
            return;
        }
        try {
            plugin.actionService().executeLines(holder, definition, "item_state_threshold",
                    actions, placeholders, item);
        } finally {
            ItemStateDerivationGuard.leave();
        }
    }

    private EmakiItemDefinition definition(ItemStack item) {
        String id = plugin.identifier().identify(item);
        return Texts.isBlank(id) ? null : plugin.itemLoader().get(id);
    }

    private void refreshDerived(Player holder, ItemStateConfig.Derivation derivation, String reason) {
        if (!ItemStateDerivationGuard.enter(derivation.maxDepth())) {
            plugin.debugLogger().log("item_state", holder, "item_state.recursion_blocked", Map.of(
                    "stage", reason,
                    "threshold_id", "",
                    "depth", ItemStateDerivationGuard.depth()));
            return;
        }
        try {
            if (derivation.refreshLore()) {
                plugin.setService().refreshEquippedSets(holder, reason);
            }
            if (derivation.refreshAttributes()) {
                plugin.scheduleAttributeEquipmentSync(holder);
            }
        } finally {
            ItemStateDerivationGuard.leave();
        }
    }
}
