package emaki.jiuwu.craft.attribute.bridge;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
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
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import emaki.jiuwu.craft.attribute.EmakiAttributePlugin;
import emaki.jiuwu.craft.attribute.api.AttributeContribution;
import emaki.jiuwu.craft.attribute.api.AttributeContributionProvider;
import emaki.jiuwu.craft.attribute.model.AttributeDefinition;
import emaki.jiuwu.craft.attribute.model.AttributeSnapshot;
import emaki.jiuwu.craft.attribute.model.DamageContext;
import emaki.jiuwu.craft.attribute.model.DamageContextVariables;
import emaki.jiuwu.craft.attribute.model.ProjectileDamageSnapshot;
import emaki.jiuwu.craft.attribute.model.ResolvedDamage;
import emaki.jiuwu.craft.attribute.service.AttributeService;
import emaki.jiuwu.craft.corelib.math.Numbers;
import emaki.jiuwu.craft.corelib.text.Texts;
import net.Indyuce.mmoitems.api.event.item.SpecialWeaponAttackEvent;

public final class MmoItemsBridge implements Listener {

    private final EmakiAttributePlugin plugin;
    private final AttributeService attributeService;
    private final ReflectiveMmoItemsAccessor accessor;
    private final AttributeContributionProvider contributionProvider;
    private final Set<UUID> trackedProjectiles = ConcurrentHashMap.newKeySet();

    public MmoItemsBridge(EmakiAttributePlugin plugin, AttributeService attributeService) {
        this.plugin = plugin;
        this.attributeService = attributeService;
        this.accessor = new ReflectiveMmoItemsAccessor(plugin);
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
                baseContext(event, target)
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
                baseContext(event, target)
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

    private DamageContextVariables baseContext(EntityDamageEvent event, LivingEntity target) {
        DamageContextVariables.Builder context = DamageContextVariables.builder();
        String cause = event.getCause().name();
        context.put("cause", cause);
        context.put("damage_cause", cause);
        context.put("damage_cause_id", cause);
        context.put("base_damage", event.getDamage());
        context.put("source_damage", event.getDamage());
        context.put("input_damage", event.getDamage());
        context.put("final_damage", event.getFinalDamage());
        context.put("target_uuid", target.getUniqueId().toString());
        context.put("target_type", target.getType().name());
        if (event instanceof EntityDamageByEntityEvent byEntityEvent) {
            context.put("damager_type", byEntityEvent.getDamager().getType().name());
            context.put("damager_uuid", byEntityEvent.getDamager().getUniqueId().toString());
        }
        return context.build();
    }

    private void resolveAndApplyDamage(CompletableFuture<ResolvedDamage> future, LivingEntity target, Entity visualSource) {
        if (future == null) {
            return;
        }
        future.whenComplete((resolvedDamage, throwable) -> plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (throwable != null || resolvedDamage == null || target == null || !target.isValid() || target.isDead()) {
                return;
            }
            applySyntheticKnockback(target, visualSource, resolvedDamage.finalDamage());
            attributeService.applyResolvedDamage(resolvedDamage, visualSource, 0D);
        }));
    }

    private void applySyntheticKnockback(LivingEntity target, Entity source, double finalDamage) {
        if (target == null || source == null || finalDamage <= 0D || !target.isValid() || target.isDead()
                || !attributeService.config().syntheticHitKnockback()) {
            return;
        }
        double strength = Math.max(0D, attributeService.config().syntheticHitKnockbackStrength());
        if (strength <= 0D) {
            return;
        }
        Vector direction = source.getLocation().toVector().subtract(target.getLocation().toVector());
        direction.setY(0D);
        if (direction.lengthSquared() < 1.0E-6D) {
            direction = source.getLocation().getDirection().multiply(-1D).setY(0D);
        }
        if (direction.lengthSquared() < 1.0E-6D) {
            return;
        }
        direction.normalize();
        target.knockback(strength, direction.getX(), direction.getZ());
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
                Object liveItem = accessor.createLiveItem(slot.item());
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
                            normalizeId(definition.mmoItemsStatId()),
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

    private static final class ReflectiveMmoItemsAccessor {

        private final EmakiAttributePlugin plugin;
        private final Set<String> warnings = new LinkedHashSet<>();
        private boolean initialized;
        private boolean available;
        private String availabilityError = "";
        private Method mmoItemsGetTypeMethod;
        private Method mmoItemsGetIdMethod;
        private Constructor<?> liveMmoItemConstructor;
        private Method mmoItemGetDataMethod;
        private Method itemStatGetIdMethod;
        private final Map<String, Object> statObjects = new LinkedHashMap<>();

        private ReflectiveMmoItemsAccessor(EmakiAttributePlugin plugin) {
            this.plugin = plugin;
        }

        private synchronized boolean ensureAvailable() {
            if (initialized) {
                return available;
            }
            initialized = true;
            try {
                Class<?> mmoItemsClass = Class.forName("net.Indyuce.mmoitems.MMOItems");
                Class<?> itemStatsClass = Class.forName("net.Indyuce.mmoitems.ItemStats");
                Class<?> itemStatClass = Class.forName("net.Indyuce.mmoitems.stat.type.ItemStat");
                Class<?> liveMmoItemClass = Class.forName("net.Indyuce.mmoitems.api.item.mmoitem.LiveMMOItem");
                Class<?> mmoItemClass = Class.forName("net.Indyuce.mmoitems.api.item.mmoitem.MMOItem");
                mmoItemsGetTypeMethod = mmoItemsClass.getMethod("getType", ItemStack.class);
                mmoItemsGetIdMethod = mmoItemsClass.getMethod("getID", ItemStack.class);
                liveMmoItemConstructor = liveMmoItemClass.getConstructor(ItemStack.class);
                mmoItemGetDataMethod = mmoItemClass.getMethod("getData", itemStatClass);
                itemStatGetIdMethod = itemStatClass.getMethod("getId");
                loadStatObjects(itemStatsClass, itemStatClass);
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
            Object rawType = invokeStatic(mmoItemsGetTypeMethod, itemStack);
            return readableId(rawType);
        }

        private String resolveItemId(ItemStack itemStack) {
            Object rawId = invokeStatic(mmoItemsGetIdMethod, itemStack);
            return Texts.toStringSafe(rawId).trim();
        }

        private Object createLiveItem(ItemStack itemStack) {
            if (!ensureAvailable() || liveMmoItemConstructor == null || itemStack == null || itemStack.getType().isAir()) {
                return null;
            }
            try {
                return liveMmoItemConstructor.newInstance(itemStack);
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

        private double readDoubleStat(Object liveItem, String configuredStatId, String attributeId) {
            if (!ensureAvailable() || liveItem == null || Texts.isBlank(configuredStatId)) {
                return 0D;
            }
            Object statObject = statObjects.get(normalizeId(configuredStatId));
            if (statObject == null) {
                warnOnce(
                        "console.mmoitems_stat_resolve_failed",
                        "missing_stat|" + normalizeId(configuredStatId),
                        Map.of(
                                "attribute", safeText(attributeId, "-"),
                                "stat", configuredStatId
                        )
                );
                return 0D;
            }
            try {
                Object data = mmoItemGetDataMethod.invoke(liveItem, statObject);
                Double numeric = toDouble(data);
                return numeric == null ? 0D : numeric;
            } catch (Throwable throwable) {
                warnOnce(
                        "console.mmoitems_stat_read_failed",
                        "read_stat|" + safeText(attributeId, "-") + "|" + normalizeId(configuredStatId),
                        Map.of(
                                "attribute", safeText(attributeId, "-"),
                                "stat", configuredStatId,
                                "error", Texts.toStringSafe(throwable.getMessage())
                        )
                );
                return 0D;
            }
        }

        private void loadStatObjects(Class<?> itemStatsClass, Class<?> itemStatClass) throws IllegalAccessException {
            statObjects.clear();
            for (Field field : itemStatsClass.getFields()) {
                if (!Modifier.isStatic(field.getModifiers()) || !itemStatClass.isAssignableFrom(field.getType())) {
                    continue;
                }
                Object statObject = field.get(null);
                if (statObject == null) {
                    continue;
                }
                statObjects.putIfAbsent(normalizeId(field.getName()), statObject);
                Object rawId = invoke(itemStatGetIdMethod, statObject);
                String statId = readableId(rawId);
                if (Texts.isNotBlank(statId)) {
                    statObjects.putIfAbsent(normalizeId(statId), statObject);
                }
            }
        }

        private Object invokeStatic(Method method, Object argument) {
            return invoke(method, null, argument);
        }

        private Object invoke(Method method, Object target, Object... arguments) {
            if (method == null) {
                return null;
            }
            try {
                return method.invoke(target, arguments);
            } catch (Throwable ignored) {
                return null;
            }
        }

        private Double toDouble(Object value) {
            if (value == null) {
                return null;
            }
            if (value instanceof Number number) {
                return number.doubleValue();
            }
            try {
                Method getValueMethod = value.getClass().getMethod("getValue");
                Object raw = getValueMethod.invoke(value);
                if (raw instanceof Number number) {
                    return number.doubleValue();
                }
            } catch (Throwable ignored) {
            }
            return Numbers.tryParseDouble(String.valueOf(value), null);
        }

        private String readableId(Object value) {
            if (value == null) {
                return "";
            }
            if (value instanceof String text) {
                return text.trim();
            }
            try {
                Method getIdMethod = value.getClass().getMethod("getId");
                Object raw = getIdMethod.invoke(value);
                if (raw != null) {
                    return String.valueOf(raw).trim();
                }
            } catch (Throwable ignored) {
            }
            return String.valueOf(value).trim();
        }

        private void warnOnce(String key, String uniqueId, Map<String, ?> replacements) {
            if (plugin == null || plugin.messageService() == null || !warnings.add(uniqueId)) {
                return;
            }
            plugin.messageService().warning(key, replacements);
        }

        private String normalizeId(String value) {
            return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        }

        private String safeText(String value, String fallback) {
            return Texts.isBlank(value) ? fallback : value;
        }
    }

    private static String normalizeId(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    }
}
