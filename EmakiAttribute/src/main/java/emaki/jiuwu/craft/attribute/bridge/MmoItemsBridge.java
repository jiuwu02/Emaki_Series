package emaki.jiuwu.craft.attribute.bridge;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.attribute.EmakiAttributePlugin;
import emaki.jiuwu.craft.attribute.api.AttributeContribution;
import emaki.jiuwu.craft.attribute.api.AttributeContributionProvider;
import emaki.jiuwu.craft.attribute.model.AttributeDefinition;
import emaki.jiuwu.craft.attribute.model.AttributeSnapshot;
import emaki.jiuwu.craft.attribute.model.DamageContext;
import emaki.jiuwu.craft.attribute.model.ProjectileDamageSnapshot;
import emaki.jiuwu.craft.attribute.model.ResolvedDamage;
import emaki.jiuwu.craft.attribute.service.AttributeService;
import emaki.jiuwu.craft.attribute.service.CombatSupport;
import emaki.jiuwu.craft.corelib.text.Texts;
import net.Indyuce.mmoitems.MMOItems;
import net.Indyuce.mmoitems.api.Type;
import net.Indyuce.mmoitems.api.event.item.SpecialWeaponAttackEvent;
import net.Indyuce.mmoitems.api.item.mmoitem.LiveMMOItem;
import net.Indyuce.mmoitems.api.item.mmoitem.MMOItem;
import net.Indyuce.mmoitems.stat.data.DoubleData;
import net.Indyuce.mmoitems.stat.type.ItemStat;

public final class MmoItemsBridge implements Listener {

    private final EmakiAttributePlugin plugin;
    private final AttributeService attributeService;
    private final DirectMmoItemsAccessor accessor;
    private final AttributeContributionProvider contributionProvider;
    private final Set<UUID> trackedProjectiles = ConcurrentHashMap.newKeySet();

    public MmoItemsBridge(EmakiAttributePlugin plugin, AttributeService attributeService) {
        this.plugin = plugin;
        this.attributeService = attributeService;
        this.accessor = new DirectMmoItemsAccessor(plugin);
        this.contributionProvider = new MmoItemsAttributeContributionProvider();
        this.attributeService.registerContributionProvider(contributionProvider);
        if (!accessor.ensureAvailable()) {
            warnBridgeUnavailable(accessor.availabilityError());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof Projectile projectile)) {
            return;
        }
        LivingEntity shooter = projectile.getShooter() instanceof LivingEntity livingEntity ? livingEntity : null;
        if (shooter == null || sourceItem(shooter) == null) {
            return;
        }
        trackedProjectiles.add(projectile.getUniqueId());
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        LivingEntity target = event.getEntity() instanceof LivingEntity livingEntity ? livingEntity : null;
        if (target == null) {
            return;
        }
        if (attributeService.isSyntheticDamage(target)) {
            return;
        }
        if (event.getDamager() instanceof Projectile projectile && trackedProjectiles.contains(projectile.getUniqueId())) {
            handleProjectileDamage(event, projectile, target);
            return;
        }
        LivingEntity attacker = event.getDamager() instanceof LivingEntity livingEntity ? livingEntity : null;
        if (sourceItem(attacker) == null) {
            return;
        }
        event.setCancelled(true);
        if (attacker instanceof Player player && attributeService.isAttackCoolingDown(player)) {
            return;
        }
        DamageContext damageContext = attributeService.createDamageContext(
                attacker,
                target,
                null,
                event.getCause(),
                null,
                event.getDamage(),
                0D,
                CombatSupport.baseContext(event, target)
        );
        resolveAndApplyDamage(attributeService.resolveDamageApplicationAsync(damageContext), target, event.getDamager());
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onSpecialWeaponAttack(SpecialWeaponAttackEvent event) {
        if (event == null) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onProjectileHit(ProjectileHitEvent event) {
        if (event != null && event.getEntity() != null) {
            trackedProjectiles.remove(event.getEntity().getUniqueId());
        }
    }

    private void handleProjectileDamage(EntityDamageByEntityEvent event, Projectile projectile, LivingEntity target) {
        trackedProjectiles.remove(projectile.getUniqueId());
        event.setCancelled(true);
        LivingEntity shooter = projectile.getShooter() instanceof LivingEntity livingEntity ? livingEntity : null;
        if (shooter instanceof Player player && attributeService.isAttackCoolingDown(player)) {
            return;
        }
        ProjectileDamageSnapshot snapshot = attributeService.readProjectileSnapshot(projectile);
        if (snapshot == null) {
            return;
        }
        AttributeSnapshot targetSnapshot = attributeService.collectCombatSnapshot(target);
        DamageContext damageContext = attributeService.createDamageContext(
                shooter,
                target,
                projectile,
                event.getCause(),
                snapshot.damageTypeId(),
                event.getDamage(),
                0D,
                snapshot.attackSnapshot(),
                targetSnapshot,
                CombatSupport.baseContext(event, target)
        );
        resolveAndApplyDamage(attributeService.resolveDamageApplicationAsync(damageContext), target, projectile);
    }

    private ItemStack sourceItem(LivingEntity entity) {
        if (entity == null) {
            return null;
        }
        EntityEquipment equipment = entity.getEquipment();
        if (equipment == null) {
            return null;
        }
        ItemStack mainHand = equipment.getItemInMainHand();
        if (mainHand != null && !mainHand.getType().isAir() && accessor.isMmoItemsItem(mainHand)) {
            return mainHand;
        }
        ItemStack offHand = equipment.getItemInOffHand();
        if (offHand != null && !offHand.getType().isAir() && accessor.isMmoItemsItem(offHand)) {
            return offHand;
        }
        return null;
    }

    private void resolveAndApplyDamage(CompletableFuture<ResolvedDamage> future, LivingEntity target, Entity visualSource) {
        if (future == null) {
            return;
        }
        future.whenComplete((resolvedDamage, throwable) -> plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (throwable != null || resolvedDamage == null || target == null || !target.isValid() || target.isDead()) {
                return;
            }
            CombatSupport.applySyntheticKnockback(target, visualSource, resolvedDamage.finalDamage(), attributeService.config());
            attributeService.applyResolvedDamage(resolvedDamage, visualSource, 0D);
        }));
    }

    private void warnBridgeUnavailable(String error) {
        if (plugin == null || plugin.messageService() == null) {
            return;
        }
        plugin.messageService().warning("console.mmoitems_bridge_unavailable", Map.of(
                "error", Texts.toStringSafe(error)
        ));
    }

    private final class MmoItemsAttributeContributionProvider implements AttributeContributionProvider {

        @Override
        public String id() {
            return "mmoitems_attribute_mapping";
        }

        @Override
        public int priority() {
            return 220;
        }

        @Override
        public List<AttributeContribution> collect(LivingEntity entity) {
            if (entity == null) {
                return List.of();
            }
            List<AttributeDefinition> mappings = attributeService.mmoItemsMappedDefinitions();
            if (mappings.isEmpty() || !accessor.ensureAvailable()) {
                return List.of();
            }
            EntityEquipment equipment = entity.getEquipment();
            if (equipment == null) {
                return List.of();
            }
            List<AttributeContribution> contributions = new ArrayList<>();
            for (ItemSlot slot : equippedSlots(equipment)) {
                if (slot.item() == null || slot.item().getType().isAir() || !accessor.isMmoItemsItem(slot.item())) {
                    continue;
                }
                MMOItem liveItem = accessor.createLiveItem(slot.item());
                if (liveItem == null) {
                    continue;
                }
                String sourceId = buildSourceId(slot, slot.item());
                Map<String, Double> statCache = new LinkedHashMap<>();
                for (AttributeDefinition definition : mappings) {
                    if (definition == null || Texts.isBlank(definition.mmoItemsStatId())) {
                        continue;
                    }
                    double value = statCache.computeIfAbsent(
                            normalizeMmoId(definition.mmoItemsStatId()),
                            key -> accessor.readDoubleStat(liveItem, definition.mmoItemsStatId(), definition.id())
                    );
                    if (Double.compare(value, 0D) == 0) {
                        continue;
                    }
                    contributions.add(new AttributeContribution(definition.id(), value, sourceId));
                }
            }
            return contributions.isEmpty() ? List.of() : List.copyOf(contributions);
        }

        private List<ItemSlot> equippedSlots(EntityEquipment equipment) {
            return List.of(
                    new ItemSlot("main_hand", equipment.getItemInMainHand()),
                    new ItemSlot("off_hand", equipment.getItemInOffHand()),
                    new ItemSlot("helmet", equipment.getHelmet()),
                    new ItemSlot("chestplate", equipment.getChestplate()),
                    new ItemSlot("leggings", equipment.getLeggings()),
                    new ItemSlot("boots", equipment.getBoots())
            );
        }

        private String buildSourceId(ItemSlot slot, ItemStack itemStack) {
            String typeId = accessor.resolveTypeId(itemStack);
            String itemId = accessor.resolveItemId(itemStack);
            StringBuilder builder = new StringBuilder("mmoitems:");
            builder.append(slot == null ? "item" : slot.name());
            if (Texts.isNotBlank(typeId)) {
                builder.append(':').append(typeId);
            }
            if (Texts.isNotBlank(itemId)) {
                builder.append(':').append(itemId);
            }
            return builder.toString();
        }
    }

    private record ItemSlot(String name, ItemStack item) {

    }

    private static final class DirectMmoItemsAccessor {

        private final EmakiAttributePlugin plugin;
        private final Set<String> warnings = new LinkedHashSet<>();
        private boolean initialized;
        private boolean available;
        private String availabilityError = "";
        private final Map<String, ItemStat<?, ?>> statObjects = new LinkedHashMap<>();

        private DirectMmoItemsAccessor(EmakiAttributePlugin plugin) {
            this.plugin = plugin;
        }

        private synchronized boolean ensureAvailable() {
            if (initialized) {
                return available;
            }
            initialized = true;
            try {
                loadStatObjects();
                available = true;
            } catch (Throwable throwable) {
                available = false;
                availabilityError = throwable.getMessage() == null ? throwable.getClass().getSimpleName() : throwable.getMessage();
            }
            return available;
        }

        private String availabilityError() {
            return availabilityError;
        }

        private boolean isMmoItemsItem(ItemStack itemStack) {
            return Texts.isNotBlank(resolveTypeId(itemStack)) && Texts.isNotBlank(resolveItemId(itemStack));
        }

        private String resolveTypeId(ItemStack itemStack) {
            try {
                Type type = MMOItems.getType(itemStack);
                return type == null ? "" : type.getId().trim();
            } catch (Throwable throwable) {
                return "";
            }
        }

        private String resolveItemId(ItemStack itemStack) {
            try {
                String id = MMOItems.getID(itemStack);
                return id == null ? "" : id.trim();
            } catch (Throwable throwable) {
                return "";
            }
        }

        private MMOItem createLiveItem(ItemStack itemStack) {
            if (!ensureAvailable() || itemStack == null || itemStack.getType().isAir()) {
                return null;
            }
            try {
                return new LiveMMOItem(itemStack);
            } catch (Throwable throwable) {
                warnOnce(
                        "console.mmoitems_stat_read_failed",
                        "live_item|" + throwable.getClass().getName(),
                        Map.of(
                                "attribute", "-",
                                "stat", "-",
                                "error", Texts.toStringSafe(throwable.getMessage())
                        )
                );
                return null;
            }
        }

        private double readDoubleStat(MMOItem liveItem, String configuredStatId, String attributeId) {
            if (!ensureAvailable() || liveItem == null || Texts.isBlank(configuredStatId)) {
                return 0D;
            }
            ItemStat<?, ?> stat = statObjects.get(normalizeMmoId(configuredStatId));
            if (stat == null) {
                warnOnce(
                        "console.mmoitems_stat_resolve_failed",
                        "missing_stat|" + normalizeMmoId(configuredStatId),
                        Map.of(
                                "attribute", safeText(attributeId, "-"),
                                "stat", configuredStatId
                        )
                );
                return 0D;
            }
            try {
                Object data = liveItem.getData(stat);
                if (data instanceof DoubleData doubleData) {
                    return doubleData.getValue();
                }
                if (data instanceof Number number) {
                    return number.doubleValue();
                }
                return 0D;
            } catch (Throwable throwable) {
                warnOnce(
                        "console.mmoitems_stat_read_failed",
                        "read_stat|" + safeText(attributeId, "-") + "|" + normalizeMmoId(configuredStatId),
                        Map.of(
                                "attribute", safeText(attributeId, "-"),
                                "stat", configuredStatId,
                                "error", Texts.toStringSafe(throwable.getMessage())
                        )
                );
                return 0D;
            }
        }

        private void loadStatObjects() {
            statObjects.clear();
            if (MMOItems.plugin == null || MMOItems.plugin.getStats() == null) {
                return;
            }
            for (ItemStat<?, ?> stat : MMOItems.plugin.getStats().getAll()) {
                if (stat == null) {
                    continue;
                }
                statObjects.putIfAbsent(normalizeMmoId(stat.name()), stat);
                String statId = stat.getId();
                if (Texts.isNotBlank(statId)) {
                    statObjects.putIfAbsent(normalizeMmoId(statId), stat);
                }
            }
        }

        private void warnOnce(String key, String uniqueId, Map<String, ?> replacements) {
            if (plugin == null || plugin.messageService() == null || !warnings.add(uniqueId)) {
                return;
            }
            plugin.messageService().warning(key, replacements);
        }

        private String safeText(String value, String fallback) {
            return Texts.isBlank(value) ? fallback : value;
        }
    }

    private static String normalizeMmoId(String value) {
        return Texts.normalizeId(value).replace('-', '_');
    }
}
