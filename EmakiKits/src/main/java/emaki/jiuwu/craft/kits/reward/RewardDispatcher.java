package emaki.jiuwu.craft.kits.reward;

import emaki.jiuwu.craft.kits.config.AppConfig;
import emaki.jiuwu.craft.corelib.api.text.MiniMessages;
import emaki.jiuwu.craft.kits.config.KitIds;
import emaki.jiuwu.craft.kits.model.ChoiceOption;
import emaki.jiuwu.craft.kits.model.KitDefinition;
import emaki.jiuwu.craft.kits.model.RewardEntry;
import emaki.jiuwu.craft.kits.model.RewardGroup;
import emaki.jiuwu.craft.kits.store.KitState;
import emaki.jiuwu.craft.kits.store.KitStore;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import java.util.function.Function;
import java.util.function.Supplier;

public final class RewardDispatcher {

    private final Plugin plugin;
    private final KitStore store;
    private final EconomyPort economy;
    private final Supplier<AppConfig> config;
    private final Function<String, KitDefinition> kits;

    public RewardDispatcher(Plugin plugin, KitStore store, EconomyPort economy,
                            Supplier<AppConfig> config,
                            Function<String, KitDefinition> kits) {
        this.plugin = plugin;
        this.store = store;
        this.economy = economy;
        this.config = config;
        this.kits = kits;
    }

    public List<String> deliver(UUID playerId, Player player, String kitId,
                                List<RewardEntry> rewards, String transactionId,
                                int depth) {
        List<String> auditIds = new ArrayList<>();
        int maxDepth = config.get().nested().maxDepth();
        if (depth > maxDepth) {
            audit(playerId, kitId, "nested_depth_exceeded",
                    "transaction=" + transactionId + ", depth=" + depth, auditIds);
            throw new RewardDeliveryException("nested depth exceeded: " + depth);
        }
        for (RewardEntry entry : rewards) {
            if (entry.requiresPermission() && player != null && !player.hasPermission(entry.permission())) {
                audit(playerId, kitId, "reward_permission_denied",
                        "transaction=" + transactionId + ", entry=" + entry.id(), auditIds);
                continue;
            }
            dispatchOne(playerId, player, kitId, entry, transactionId, depth, auditIds);
        }
        return List.copyOf(auditIds);
    }

    public List<String> deliverGroup(UUID playerId, Player player, String kitId,
                                     RewardGroup group, String transactionId, int depth) {
        if (!group.valid() || group.entries().isEmpty()) {
            return List.of();
        }
        if (!group.weighted()) {
            return deliver(playerId, player, kitId, group.entries(), transactionId, depth);
        }
        int roll = ThreadLocalRandom.current().nextInt(group.totalWeight());
        int cursor = 0;
        RewardEntry selected = group.entries().getLast();
        for (RewardEntry entry : group.entries()) {
            cursor += entry.weight();
            if (roll < cursor) {
                selected = entry;
                break;
            }
        }
        return deliver(playerId, player, kitId, List.of(selected), transactionId, depth);
    }

    private void dispatchOne(UUID playerId, Player player, String kitId, RewardEntry entry,
                             String transactionId, int depth, List<String> auditIds) {
        switch (entry.kind()) {
            case COMMAND -> {
                String line = entry.command().replace("%player%",
                        player == null ? playerId.toString() : player.getName())
                        .replace("%uuid%", playerId.toString())
                        .replace("%amount%", String.valueOf(entry.amount()));
                if (line.isBlank()) {
                    throw new RewardDeliveryException("empty command for " + entry.id());
                }
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), line);
                audit(playerId, kitId, "command", "transaction=" + transactionId
                        + ", entry=" + entry.id() + ", command=" + line, auditIds);
            }
            case ITEM -> {
                if (player == null) {
                    audit(playerId, kitId, "item_offline", "transaction=" + transactionId
                            + ", entry=" + entry.id(), auditIds);
                    throw new RewardDeliveryException("player offline for item " + entry.id());
                }
                Material material = Material.matchMaterial(entry.itemMaterial().trim().toUpperCase(Locale.ROOT));
                if (material == null || material.isAir()) {
                    throw new RewardDeliveryException("invalid material " + entry.itemMaterial());
                }
                ItemStack stack = new ItemStack(material, entry.amount());
                ItemMeta meta = stack.getItemMeta();
                if (meta != null) {
                    if (!entry.itemName().isBlank()) {
                        meta.displayName(MiniMessages.parse(entry.itemName()));
                    }
                    if (!entry.itemLore().isEmpty()) {
                        meta.lore(entry.itemLore().stream().map(MiniMessages::parse).toList());
                    }
                    stack.setItemMeta(meta);
                }
                Map<Integer, ItemStack> overflow = player.getInventory().addItem(stack);
                for (ItemStack leftover : overflow.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), leftover);
                }
                audit(playerId, kitId, "item", "transaction=" + transactionId
                        + ", entry=" + entry.id() + ", material=" + material + ", amount=" + entry.amount(), auditIds);
            }
            case MONEY -> {
                if (!economy.available()) {
                    throw new RewardDeliveryException("economy unavailable for " + entry.id());
                }
                AppConfig.EconomySettings settings = new AppConfig.EconomySettings(
                        config.get().economy().provider(), config.get().economy().currencyId());
                economy.refund(player, settings, entry.amount());
                audit(playerId, kitId, "money", "transaction=" + transactionId
                        + ", entry=" + entry.id() + ", amount=" + entry.amount(), auditIds);
            }
            case EXP -> {
                if (player == null) {
                    throw new RewardDeliveryException("player offline for exp " + entry.id());
                }
                player.giveExp(entry.amount());
                audit(playerId, kitId, "exp", "transaction=" + transactionId
                        + ", entry=" + entry.id() + ", amount=" + entry.amount(), auditIds);
            }
            case NESTED_KIT -> {
                String childId = KitIds.normalize(entry.nestedKitId());
                KitDefinition child = kits.apply(childId);
                if (child == null || !child.enabled()) {
                    audit(playerId, kitId, "nested_missing", "transaction=" + transactionId
                            + ", entry=" + entry.id() + ", child=" + childId, auditIds);
                    return;
                }
                List<RewardEntry> childRewards = new ArrayList<>(child.fixedRewards().entries());
                childRewards.addAll(child.randomRewards().entries());
                deliver(playerId, player, childId, childRewards, transactionId, depth + 1);
                audit(playerId, kitId, "nested", "transaction=" + transactionId
                        + ", entry=" + entry.id() + ", child=" + childId + ", depth=" + (depth + 1), auditIds);
            }
        }
    }

    public List<RewardEntry> optionRewards(ChoiceOption option) {
        List<RewardEntry> rewards = new ArrayList<>(option.rewards());
        if (!option.nestedKitId().isBlank()) {
            KitDefinition child = kits.apply(KitIds.normalize(option.nestedKitId()));
            if (child != null && child.enabled()) {
                rewards.addAll(child.fixedRewards().entries());
                rewards.addAll(child.randomRewards().entries());
            }
        }
        return List.copyOf(rewards);
    }

    private void audit(UUID playerId, String kitId, String action, String detail,
                       List<String> auditIds) {
        String id = store.read(s -> "A" + s.nextSequence());
        store.recordAudit(new KitState.AuditRecord(id, System.currentTimeMillis(), playerId,
                kitId, action, detail));
        auditIds.add(id);
    }
}
