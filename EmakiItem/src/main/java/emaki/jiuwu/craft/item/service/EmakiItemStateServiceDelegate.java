package emaki.jiuwu.craft.item.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import emaki.jiuwu.craft.corelib.debug.DebugLogger;
import emaki.jiuwu.craft.item.api.ItemState;
import emaki.jiuwu.craft.item.api.ItemStateKey;
import emaki.jiuwu.craft.item.api.ItemStateMetadata;
import emaki.jiuwu.craft.item.api.ItemStateMutation;
import emaki.jiuwu.craft.item.api.ItemStateSchema;
import emaki.jiuwu.craft.item.api.ItemStateSnapshot;
import emaki.jiuwu.craft.item.api.ItemStateType;
import emaki.jiuwu.craft.item.api.event.ItemStateChangeEvent;
import emaki.jiuwu.craft.item.api.event.ItemStateThresholdEvent;
import emaki.jiuwu.craft.item.model.ItemStateConfig;
import emaki.jiuwu.craft.corelib.debug.FailureRecorder;

final class EmakiItemStateServiceDelegate {

    private final EmakiItemStateService ownerRef;

    EmakiItemStateServiceDelegate(EmakiItemStateService ownerRef) {
        this.ownerRef = ownerRef;
    }

    <T> void dispatchThresholdEvents(ItemStack item, Player holder, ItemStateKey<T> key, T old, T current, ItemStateThresholdEvaluator.Outcome outcome) {
        if (outcome == null || outcome.empty()) {
            return;
        }
        Number before = old instanceof Number number ? number : null;
        Number after = current instanceof Number number ? number : null;
        for (ItemStateThresholdEvaluator.Crossing crossing : outcome.crossings()) {
            Bukkit.getPluginManager().callEvent(new ItemStateThresholdEvent(
                    item,
                    holder,
                    key,
                    before,
                    after,
                    crossing.threshold().value(),
                    crossing.threshold().id(),
                    crossing.direction(),
                    crossing.threshold().once(),
                    crossing.rearmed()));
        }
    }

}
