package emaki.jiuwu.craft.dungeon.drop;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.dungeon.config.DungeonConfig;
import emaki.jiuwu.craft.dungeon.instance.DungeonInstance;
import emaki.jiuwu.craft.dungeon.orchestration.InstanceRegistry;
import emaki.jiuwu.craft.dungeon.party.Party;
import emaki.jiuwu.craft.dungeon.party.PartyService;

public final class DropRouter implements Listener {

    private final InstanceRegistry registry;
    private final PartyService parties;
    private final MessageService messages;
    private final RewardChestService rewardChests;
    private final emaki.jiuwu.craft.dungeon.rewardbox.RewardBoxService rewardBox;
    private final java.util.function.Function<String, DungeonConfig> configOf;
    private final org.bukkit.plugin.Plugin plugin;
    private final emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher dispatcher;
    private final Random random = new Random();

    public DropRouter(InstanceRegistry registry,
            PartyService parties,
            MessageService messages,
            RewardChestService rewardChests,
            emaki.jiuwu.craft.dungeon.rewardbox.RewardBoxService rewardBox,
            java.util.function.Function<String, DungeonConfig> configOf,
            org.bukkit.plugin.Plugin plugin,
            emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher dispatcher) {
        this.registry = registry;
        this.parties = parties;
        this.messages = messages;
        this.rewardChests = rewardChests;
        this.rewardBox = rewardBox;
        this.configOf = configOf;
        this.plugin = plugin;
        this.dispatcher = dispatcher;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity instanceof Player) {
            return;
        }
        DungeonInstance instance = registry.get(entity.getWorld().getName());
        if (instance == null) {
            return;
        }
        List<ItemStack> drops = event.getDrops();
        if (drops.isEmpty()) {
            return;
        }
        if (emaki.jiuwu.craft.dungeon.flow.WaveController.hasNoDrop(entity)) {
            drops.clear();
            return;
        }
        applyDropMultiplier(instance, drops);
        String mode = instance.dropMode();
        switch (mode) {
            case "killer" -> applyKiller(instance, event, drops);
            case "roll" -> {
                List<ItemStack> captured = cloneAll(drops);
                event.getDrops().clear();
                autoRoll(instance, captured);
            }
            case "score" -> {
                List<ItemStack> captured = cloneAll(drops);
                event.getDrops().clear();
                rewardChests.addPool(instance, captured);
            }
            case "share" -> {
                List<ItemStack> captured = cloneAll(drops);
                event.getDrops().clear();
                giveShare(instance, captured);
            }
            default -> {
            }
        }
    }

    private void applyDropMultiplier(DungeonInstance instance, List<ItemStack> drops) {
        DungeonConfig config = configOf.apply(instance.dungeonId());
        double multiplier = config == null ? 1.0D : config.dropMultiplier(instance.difficulty());
        if (multiplier == 1.0D) {
            return;
        }
        for (ItemStack stack : drops) {
            int adjusted = (int) Math.floor(stack.getAmount() * multiplier);
            if (adjusted < 1) {
                adjusted = multiplier > 0.0D ? 1 : 0;
            }
            stack.setAmount(adjusted);
        }
        drops.removeIf(stack -> stack.getAmount() <= 0);
    }

    private void applyKiller(DungeonInstance instance, EntityDeathEvent event, List<ItemStack> drops) {
        Player killer = event.getEntity().getKiller();
        List<ItemStack> captured = cloneAll(drops);
        event.getDrops().clear();
        if (killer == null) {
            dropAtSpawn(instance, captured);
            return;
        }
        rewardBox.deposit(killer.getUniqueId(), captured);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPickup(org.bukkit.event.entity.EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        DungeonInstance instance = registry.get(player.getWorld().getName());
        if (instance == null) {
            return;
        }
        ItemStack stack = event.getItem().getItemStack().clone();
        if (stack.getType() == Material.AIR || stack.getAmount() <= 0) {
            return;
        }
        event.setCancelled(true);
        event.getItem().remove();
        rewardBox.deposit(player.getUniqueId(), List.of(stack));
        rewardBox.notifyStored(player, itemLabel(stack), stack.getAmount());
    }

    private void dropAtSpawn(DungeonInstance instance, List<ItemStack> items) {
        if (items.isEmpty()) {
            return;
        }
        dispatcher.runGlobal(plugin, () -> {
            org.bukkit.World world = Bukkit.getWorld(instance.instanceId());
            if (world == null) {
                return;
            }
            var location = world.getSpawnLocation();
            for (ItemStack stack : items) {
                world.dropItemNaturally(location, stack);
            }
        });
    }

    private void autoRoll(DungeonInstance instance, List<ItemStack> items) {
        if (items.isEmpty()) {
            return;
        }
        List<UUID> members = new ArrayList<>();
        for (UUID memberId : membersOf(instance)) {
            if (Bukkit.getPlayer(memberId) != null) {
                members.add(memberId);
            }
        }
        if (members.isEmpty()) {
            dropAtSpawn(instance, items);
            return;
        }
        for (ItemStack stack : items) {
            UUID winner = members.get(random.nextInt(members.size()));
            rewardBox.deposit(winner, List.of(stack));
            Player winnerPlayer = Bukkit.getPlayer(winner);
            if (winnerPlayer != null) {
                messages.send(winnerPlayer, "drop.roll.won", Map.of(
                        "item", itemLabel(stack),
                        "amount", String.valueOf(stack.getAmount())
                ));
            }
        }
        for (UUID memberId : members) {
            Player member = Bukkit.getPlayer(memberId);
            if (member != null) {
                messages.send(member, "drop.roll.distributed", Map.of(
                        "count", String.valueOf(items.size())));
            }
        }
    }

    private static String itemLabel(ItemStack stack) {
        if (stack.hasItemMeta() && stack.getItemMeta().hasDisplayName()) {
            return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                    .plainText().serialize(stack.getItemMeta().displayName());
        }
        return stack.getType().name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
    }

    private void giveShare(DungeonInstance instance, List<ItemStack> items) {
        List<UUID> members = membersOf(instance);
        if (members.isEmpty()) {
            return;
        }
        Map<UUID, List<ItemStack>> allocation =
                DropAllocator.allocateShare(items, members);
        for (Map.Entry<UUID, List<ItemStack>> entry : allocation.entrySet()) {
            rewardBox.deposit(entry.getKey(), entry.getValue());
        }
    }

    private List<UUID> membersOf(DungeonInstance instance) {
        UUID partyId = instance.partyId();
        Party party = partyId == null ? null : parties.partyById(partyId);
        if (party != null) {
            return new ArrayList<>(party.members());
        }
        org.bukkit.World world = Bukkit.getWorld(instance.instanceId());
        if (world == null) {
            return List.of();
        }
        List<UUID> members = new ArrayList<>();
        for (Player player : world.getPlayers()) {
            members.add(player.getUniqueId());
        }
        return members;
    }

    private static List<ItemStack> cloneAll(List<ItemStack> items) {
        List<ItemStack> cloned = new ArrayList<>(items.size());
        for (ItemStack item : items) {
            if (item != null && item.getAmount() > 0) {
                cloned.add(item.clone());
            }
        }
        return cloned;
    }
}
