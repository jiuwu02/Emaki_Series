package emaki.jiuwu.craft.mobs.listener;

import emaki.jiuwu.craft.corelib.random.WeightedPool;
import emaki.jiuwu.craft.item.api.EmakiItemApi;
import emaki.jiuwu.craft.mobs.loader.MobSpec;
import emaki.jiuwu.craft.mobs.loot.LootEntryDefinition;
import emaki.jiuwu.craft.mobs.loot.LootFunctionDefinition;
import emaki.jiuwu.craft.mobs.loot.LootPoolDefinition;
import emaki.jiuwu.craft.mobs.loot.LootTableDefinition;
import emaki.jiuwu.craft.mobs.service.MobIdentifier;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import java.util.logging.Logger;

public final class MobDropHandler implements Listener {

    private final MobIdentifier mobIdentifier;
    private final Supplier<Map<String, MobSpec>> mobRegistry;
    private final Supplier<Map<String, LootTableDefinition>> lootRegistry;
    private final Logger logger;
    private volatile boolean emakiItemAbsent = false;

    public MobDropHandler(MobIdentifier mobIdentifier,
                          Supplier<Map<String, MobSpec>> mobRegistry,
                          Supplier<Map<String, LootTableDefinition>> lootRegistry,
                          Logger logger) {
        this.mobIdentifier = mobIdentifier;
        this.mobRegistry = mobRegistry;
        this.lootRegistry = lootRegistry;
        this.logger = logger;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof LivingEntity entity)) {
            return;
        }
        String mobId = mobIdentifier.readId(entity);
        if (mobId == null) {
            return;
        }
        MobSpec spec = mobRegistry.get().get(mobId);
        if (spec == null) {
            return;
        }
        if (spec.experience() > 0) {
            event.setDroppedExp(spec.experience());
        }
        if (!spec.components().containsKey("loot_table")) {
            return;
        }
        LootTableDefinition lootDef = lootRegistry.get().get(mobId);
        if (lootDef == null) {
            return;
        }
        event.getDrops().clear();
        int lootingLevel = getLootingLevel(event.getEntity().getKiller());
        for (LootPoolDefinition pool : lootDef.pools()) {
            applyPool(event, pool, lootingLevel);
        }
    }

    private void applyPool(EntityDeathEvent event, LootPoolDefinition pool, int lootingLevel) {
        int rolls = resolveRolls(pool.rolls());
        for (int i = 0; i < rolls; i++) {
            WeightedPool<LootEntryDefinition> weightedPool = new WeightedPool<>();
            for (LootEntryDefinition entry : pool.entries()) {
                weightedPool.add(entry, entry.weight());
            }
            weightedPool.roll().ifPresent(entry -> tryDropEntry(event, entry, lootingLevel));
        }
    }

    private void tryDropEntry(EntityDeathEvent event, LootEntryDefinition entry, int lootingLevel) {
        if (entry.chance() < 1.0 && ThreadLocalRandom.current().nextDouble() >= entry.chance()) {
            return;
        }
        int count = resolveCount(entry, lootingLevel);
        if (count <= 0) {
            return;
        }
        ItemStack item = resolveItem(entry);
        if (item == null || item.getType() == Material.AIR) {
            return;
        }
        item.setAmount(Math.min(count, item.getMaxStackSize()));
        event.getDrops().add(item);
    }

    private ItemStack resolveItem(LootEntryDefinition entry) {
        if (entry.emakiItem() != null) {
            ItemStack result = resolveEmakiItem(entry.emakiItem());
            if (result != null) {
                return result;
            }
        }
        if (entry.item() != null) {
            Material material = Material.matchMaterial(entry.item());
            if (material != null && material != Material.AIR) {
                return new ItemStack(material);
            }
        }
        return null;
    }

    private ItemStack resolveEmakiItem(String itemId) {
        if (emakiItemAbsent) {
            return null;
        }
        try {
            var status = EmakiItemApi.status();
            if (!status.ready()) {
                return null;
            }
            return EmakiItemApi.operations().create(itemId, 1).orElse(null);
        } catch (NoClassDefFoundError e) {
            emakiItemAbsent = true;
            logger.fine("EmakiItem API not available, emaki_item drops disabled");
            return null;
        }
    }

    private int resolveCount(LootEntryDefinition entry, int lootingLevel) {
        int base = 1;
        int lootingBonus = 0;
        for (LootFunctionDefinition func : entry.functions()) {
            if ("set_count".equals(func.type()) && func.count() != null) {
                int min = func.count().min();
                int max = func.count().max();
                base = max > min ? ThreadLocalRandom.current().nextInt(min, max + 1) : min;
            } else if ("looting_enchant".equals(func.type()) && func.count() != null && lootingLevel > 0) {
                int maxBonus = func.count().max() * lootingLevel;
                lootingBonus = maxBonus > 0 ? ThreadLocalRandom.current().nextInt(maxBonus + 1) : 0;
            }
        }
        return base + lootingBonus;
    }

    private int resolveRolls(Object rolls) {
        if (rolls instanceof Number num) {
            return Math.max(1, num.intValue());
        }
        if (rolls instanceof String s) {
            try {
                return Math.max(1, Integer.parseInt(s));
            } catch (NumberFormatException e) {
                logger.finest("Invalid rolls value: " + s);
            }
        }
        return 1;
    }

    private int getLootingLevel(Player killer) {
        if (killer == null) {
            return 0;
        }
        ItemStack weapon = killer.getInventory().getItemInMainHand();
        return weapon.getEnchantmentLevel(Enchantment.LOOTING);
    }
}
