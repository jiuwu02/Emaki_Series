package emaki.jiuwu.craft.strengthen.enhancement;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.pdc.SignatureUtil;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.variable.VariableContext;
import emaki.jiuwu.craft.strengthen.api.target.EnhancementTargetProvider;

public final class EnhancementTargetVariables {

    private static final String FORGE_NAMESPACE = "emakiforge";

    public static final String FORGE_PATH_QUALITY_ID = "forge.quality_id";
    public static final String FORGE_PATH_QUALITY_DISPLAY = "forge.quality_display";
    public static final String FORGE_PATH_QUALITY_MULTIPLIER = "forge.quality_multiplier";
    public static final String FORGE_PATH_RECIPE_ID = "forge.forge_recipe_id";

    public static final String VARIABLE_MULTIPLIER_VALID = "forge_quality_multiplier_valid";
    public static final String VARIABLE_PDC_READ_ERRORS = "target_pdc_read_error_count";

    public static final double DEFAULT_QUALITY_MULTIPLIER = 1D;

    private static final Map<String, List<String>> FORGE_ALIASES = Map.of(
            FORGE_PATH_QUALITY_ID, List.of("forge_quality_id", "forge.quality_id", "quality_id"),
            FORGE_PATH_QUALITY_DISPLAY,
            List.of("forge_quality_display", "forge.quality_display", "quality_display"),
            FORGE_PATH_QUALITY_MULTIPLIER,
            List.of("forge_quality_multiplier", "forge.quality_multiplier", "quality_multiplier"),
            FORGE_PATH_RECIPE_ID, List.of("forge_recipe_id", "forge.forge_recipe_id"));

    private EnhancementTargetVariables() {
    }

    public static @NotNull Map<String, List<String>> forgeAliasContract() {
        return FORGE_ALIASES;
    }

    public static @NotNull List<String> forgeAliases(@Nullable String path) {
        return FORGE_ALIASES.getOrDefault(Texts.toStringSafe(path).toLowerCase(Locale.ROOT), List.of());
    }

    public static @NotNull String forgeNamespace() {
        return FORGE_NAMESPACE;
    }

    public static double coerceQualityMultiplier(@Nullable Object raw) {
        return finiteDouble(raw, DEFAULT_QUALITY_MULTIPLIER).value();
    }

    public static boolean validQualityMultiplier(@Nullable Object raw) {
        return finiteDouble(raw, DEFAULT_QUALITY_MULTIPLIER).valid();
    }

    public static @NotNull Snapshot capture(@Nullable Player player,
            @Nullable ItemStack target,
            @Nullable EnhancementTargetProvider provider) {
        String providerId = provider == null ? "" : safeProviderId(provider);
        int level = provider == null ? 0 : safeNonNegative(() -> provider.readLevel(player, target));
        int temper = provider == null ? 0 : safeNonNegative(() -> provider.readTemper(player, target));
        String recipeId = provider == null ? "" : safeString(() -> provider.readRecipeId(player, target));
        Map<String, Object> pdc = new LinkedHashMap<>();
        Map<String, Object> effects = new LinkedHashMap<>();
        Map<String, Object> layers = new LinkedHashMap<>();
        Map<String, Object> audit = new LinkedHashMap<>();
        Map<String, Object> meta = readMeta(target);
        List<String> unreadableKeys = new ArrayList<>();
        readPdc(target, pdc, effects, layers, audit, meta, unreadableKeys);
        int unreadable = unreadableKeys.size();
        String instanceId = provider == null ? "" : safeString(() -> provider.readInstanceId(player, target));
        if (Texts.isBlank(instanceId)) {
            instanceId = findIdentityValue(pdc, "instance_id", "owner_id", "uuid");
        }
        String explicitVersion = provider == null ? "" : safeString(() -> provider.readVersion(player, target));
        if (Texts.isBlank(explicitVersion)) {
            explicitVersion = findIdentityValue(pdc, "data_version", "state_version", "update_version", "revision", "version");
        }
        Map<String, Object> versionData = new LinkedHashMap<>();
        versionData.put("provider", providerId);
        versionData.put("instance", instanceId);
        versionData.put("explicit_version", explicitVersion);
        versionData.put("level", level);
        versionData.put("temper", temper);
        versionData.put("recipe", recipeId);
        versionData.put("pdc", pdc);
        versionData.put("effects", effects);
        versionData.put("layers", layers);
        versionData.put("audit", audit);
        versionData.put("meta", meta);
        String version = SignatureUtil.stableSignature(versionData);
        Map<String, Object> variables = buildVariables(target, providerId, instanceId, version, level, temper,
                recipeId, pdc, effects, layers, audit, meta, unreadable);
        return new Snapshot(providerId, instanceId, version, level, temper, recipeId,
                pdc, effects, layers, audit, meta, variables, unreadable, unreadableKeys);
    }

    public static void enrich(@NotNull VariableContext.Builder builder, @Nullable ItemStack target) {
        enrich(builder, capture(null, target, null));
    }

    public static void enrich(@NotNull VariableContext.Builder builder, @NotNull Snapshot snapshot) {
        builder.withAll(snapshot.variables());
    }

    private static Map<String, Object> buildVariables(ItemStack target,
            String providerId,
            String instanceId,
            String version,
            int level,
            int temper,
            String recipeId,
            Map<String, Object> pdc,
            Map<String, Object> effects,
            Map<String, Object> layers,
            Map<String, Object> audit,
            Map<String, Object> meta,
            int unreadable) {
        Map<String, Object> variables = new LinkedHashMap<>();
        String material = target == null || target.getType().isAir() ? "" : target.getType().name();
        variables.put("target_item_type", material);
        variables.put("target.item_type", material);
        variables.put("item_type", material);
        variables.put("target_material", material);
        variables.put("target.material", material);
        variables.put("target_provider", providerId);
        variables.put("target.provider", providerId);
        variables.put("provider", providerId);
        variables.put("target_instance", instanceId);
        variables.put("target.instance", instanceId);
        variables.put("target_instance_id", instanceId);
        variables.put("target.instance_id", instanceId);
        variables.put("target_version", version);
        variables.put("target.version", version);
        variables.put("target_level", level);
        variables.put("target.level", level);
        variables.put("target_temper", temper);
        variables.put("target.temper", temper);
        variables.put("target_recipe", recipeId);
        variables.put("target.recipe", recipeId);
        variables.put("target_recipe_id", recipeId);
        variables.put("target.recipe_id", recipeId);
        variables.put("forge_quality_id", "");
        variables.put("forge_quality_display", "");
        variables.put("forge_quality_multiplier", 1D);
        variables.put("forge_recipe_id", "");
        variables.put("forge.quality_id", "");
        variables.put("forge.quality_display", "");
        variables.put("forge.quality_multiplier", 1D);
        variables.put("forge.forge_recipe_id", "");
        variables.put("quality_id", "");
        variables.put("quality_display", "");
        variables.put("quality_multiplier", 1D);
        variables.put("forge_quality_multiplier_valid", 1);
        variables.put("target_pdc_read_error_count", unreadable);
        pdc.forEach((key, value) -> variables.put("item_pdc_" + key.replace(':', '_'), value));
        addForgeAliases(variables, pdc);
        effects.forEach((key, value) -> variables.put("target_effect_" + normalizeVariableKey(key), value));
        layers.forEach((key, value) -> variables.put("target_layer_" + normalizeVariableKey(key), value));
        audit.forEach((key, value) -> variables.put("target_audit_" + normalizeVariableKey(key), value));
        meta.forEach((key, value) -> variables.put("target_meta_" + normalizeVariableKey(key), value));
        return Map.copyOf(variables);
    }

    private static void readPdc(ItemStack target,
            Map<String, Object> pdc,
            Map<String, Object> effects,
            Map<String, Object> layers,
            Map<String, Object> audit,
            Map<String, Object> meta,
            List<String> unreadableKeys) {
        if (target == null || target.getType().isAir()) {
            return;
        }
        ItemMeta itemMeta = target.getItemMeta();
        if (itemMeta == null) {
            return;
        }
        PersistentDataContainer container = itemMeta.getPersistentDataContainer();
        for (NamespacedKey key : container.getKeys()) {
            Object value = read(container, key);
            if (value == null) {
                unreadableKeys.add(key.getNamespace() + ":" + key.getKey());
                continue;
            }
            String canonical = key.getNamespace() + ":" + key.getKey();
            pdc.put(canonical, value);
            String path = key.getKey().toLowerCase(Locale.ROOT);
            if (path.contains("effect")) {
                effects.put(canonical, value);
            }
            if (path.contains("layer")) {
                layers.put(canonical, value);
            }
            if (path.contains("audit")) {
                audit.put(canonical, value);
            }
            if (path.contains("meta")) {
                meta.put(canonical, value);
            }
        }
    }

    private static Map<String, Object> readMeta(ItemStack target) {
        if (target == null || target.getType().isAir()) {
            return Map.of();
        }
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("material", target.getType().name());
        ItemMeta meta = target.getItemMeta();
        if (meta == null) {
            return Map.copyOf(values);
        }
        if (meta.hasDisplayName()) {
            values.put("display_name", meta.getDisplayName());
        }
        if (meta.hasLore() && meta.getLore() != null) {
            values.put("lore", List.copyOf(meta.getLore()));
        }
        if (meta.hasCustomModelData()) {
            values.put("custom_model_data", meta.getCustomModelData());
        }
        values.put("unbreakable", meta.isUnbreakable());
        if (meta instanceof Damageable damageable && damageable.hasDamage()) {
            values.put("damage", damageable.getDamage());
        }
        Map<String, Integer> enchantments = new LinkedHashMap<>();
        meta.getEnchants().forEach((enchantment, level) -> enchantments.put(enchantment.getKey().toString(), level));
        if (!enchantments.isEmpty()) {
            values.put("enchantments", enchantments);
        }
        if (!meta.getItemFlags().isEmpty()) {
            List<String> flags = new ArrayList<>();
            meta.getItemFlags().forEach(flag -> flags.add(flag.name()));
            flags.sort(String::compareTo);
            values.put("item_flags", List.copyOf(flags));
        }
        return Map.copyOf(values);
    }

    private static Object read(PersistentDataContainer container, NamespacedKey key) {
        try {
            if (container.has(key, PersistentDataType.STRING)) {
                return container.get(key, PersistentDataType.STRING);
            }
            if (container.has(key, PersistentDataType.DOUBLE)) {
                return container.get(key, PersistentDataType.DOUBLE);
            }
            if (container.has(key, PersistentDataType.FLOAT)) {
                return container.get(key, PersistentDataType.FLOAT);
            }
            if (container.has(key, PersistentDataType.LONG)) {
                return container.get(key, PersistentDataType.LONG);
            }
            if (container.has(key, PersistentDataType.INTEGER)) {
                return container.get(key, PersistentDataType.INTEGER);
            }
            if (container.has(key, PersistentDataType.SHORT)) {
                return container.get(key, PersistentDataType.SHORT);
            }
            if (container.has(key, PersistentDataType.BYTE)) {
                return container.get(key, PersistentDataType.BYTE);
            }
            if (container.has(key, PersistentDataType.BYTE_ARRAY)) {
                byte[] bytes = container.get(key, PersistentDataType.BYTE_ARRAY);
                return bytes == null ? null : SignatureUtil.stableSignature(bytes);
            }
            if (container.has(key, PersistentDataType.INTEGER_ARRAY)) {
                int[] values = container.get(key, PersistentDataType.INTEGER_ARRAY);
                return values == null ? null : SignatureUtil.stableSignature(values);
            }
            if (container.has(key, PersistentDataType.LONG_ARRAY)) {
                long[] values = container.get(key, PersistentDataType.LONG_ARRAY);
                return values == null ? null : SignatureUtil.stableSignature(values);
            }
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        }
        return null;
    }

    private static void addForgeAliases(Map<String, Object> variables, Map<String, Object> pdc) {
        pdc.forEach((canonical, value) -> {
            int separator = canonical.indexOf(':');
            if (separator <= 0 || !FORGE_NAMESPACE.equalsIgnoreCase(canonical.substring(0, separator))) {
                return;
            }
            String path = canonical.substring(separator + 1).toLowerCase(Locale.ROOT);
            List<String> names = FORGE_ALIASES.get(path);
            if (names == null) {
                return;
            }
            if (FORGE_PATH_QUALITY_MULTIPLIER.equals(path)) {
                NumberResult result = finiteDouble(value, DEFAULT_QUALITY_MULTIPLIER);
                aliases(variables, result.value(), names);
                variables.put(VARIABLE_MULTIPLIER_VALID, result.valid() ? 1 : 0);
                return;
            }
            aliases(variables, value, names);
        });
    }

    private static void aliases(Map<String, Object> variables, Object value, List<String> names) {
        for (String name : names) {
            variables.put(name, value);
        }
    }

    private static String findIdentityValue(Map<String, Object> values, String... suffixes) {
        for (String suffix : suffixes) {
            for (Map.Entry<String, Object> entry : values.entrySet()) {
                String key = entry.getKey().toLowerCase(Locale.ROOT);
                if ((key.endsWith(":" + suffix) || key.endsWith("." + suffix) || key.endsWith("_" + suffix))
                        && Texts.isNotBlank(Texts.toStringSafe(entry.getValue()))) {
                    return Texts.toStringSafe(entry.getValue());
                }
            }
        }
        return "";
    }

    private static String normalizeVariableKey(String value) {
        return Texts.toStringSafe(value).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]+", "_");
    }

    private static NumberResult finiteDouble(Object value, double fallback) {
        try {
            double parsed = value instanceof Number number
                    ? number.doubleValue()
                    : Double.parseDouble(String.valueOf(value));
            boolean valid = Double.isFinite(parsed) && parsed >= 0D;
            return new NumberResult(valid ? parsed : fallback, valid);
        } catch (NumberFormatException exception) {
            return new NumberResult(fallback, false);
        }
    }

    private static String safeProviderId(EnhancementTargetProvider provider) {
        try {
            return Texts.toStringSafe(provider.id());
        } catch (RuntimeException | LinkageError exception) {
            return "";
        }
    }

    private static int safeNonNegative(IntReader reader) {
        try {
            return Math.max(0, reader.read());
        } catch (RuntimeException | LinkageError exception) {
            return 0;
        }
    }

    private static String safeString(StringReader reader) {
        try {
            return Texts.toStringSafe(reader.read());
        } catch (RuntimeException | LinkageError exception) {
            return "";
        }
    }

    @FunctionalInterface
    private interface IntReader {
        int read();
    }

    @FunctionalInterface
    private interface StringReader {
        String read();
    }

    private record NumberResult(double value, boolean valid) {
    }

    public record Snapshot(@NotNull String providerId,
            @NotNull String instanceId,
            @NotNull String version,
            int level,
            int temper,
            @NotNull String recipeId,
            @NotNull Map<String, Object> pdc,
            @NotNull Map<String, Object> effects,
            @NotNull Map<String, Object> layers,
            @NotNull Map<String, Object> audit,
            @NotNull Map<String, Object> meta,
            @NotNull Map<String, Object> variables,
            int unreadablePdcCount,
            @NotNull List<String> unreadablePdcKeys) {

        public Snapshot {
            providerId = Texts.toStringSafe(providerId);
            instanceId = Texts.toStringSafe(instanceId);
            version = Texts.toStringSafe(version);
            level = Math.max(0, level);
            temper = Math.max(0, temper);
            recipeId = Texts.toStringSafe(recipeId);
            pdc = pdc == null ? Map.of() : Map.copyOf(pdc);
            effects = effects == null ? Map.of() : Map.copyOf(effects);
            layers = layers == null ? Map.of() : Map.copyOf(layers);
            audit = audit == null ? Map.of() : Map.copyOf(audit);
            meta = meta == null ? Map.of() : Map.copyOf(meta);
            variables = variables == null ? Map.of() : Map.copyOf(variables);
            unreadablePdcKeys = unreadablePdcKeys == null ? List.of() : List.copyOf(unreadablePdcKeys);
            unreadablePdcCount = Math.max(0, unreadablePdcCount);
        }

        public Snapshot(@NotNull String providerId,
                @NotNull String instanceId,
                @NotNull String version,
                int level,
                int temper,
                @NotNull String recipeId,
                @NotNull Map<String, Object> pdc,
                @NotNull Map<String, Object> effects,
                @NotNull Map<String, Object> layers,
                @NotNull Map<String, Object> audit,
                @NotNull Map<String, Object> meta,
                @NotNull Map<String, Object> variables,
                int unreadablePdcCount) {
            this(providerId, instanceId, version, level, temper, recipeId, pdc, effects, layers,
                    audit, meta, variables, unreadablePdcCount, List.of());
        }

        public boolean sameIdentityAndVersion(@Nullable Snapshot other) {
            if (other == null || !Objects.equals(providerId, other.providerId)
                    || !Objects.equals(version, other.version)) {
                return false;
            }
            if (Texts.isNotBlank(instanceId) || Texts.isNotBlank(other.instanceId)) {
                return Objects.equals(instanceId, other.instanceId);
            }
            return level == other.level && temper == other.temper && Objects.equals(recipeId, other.recipeId);
        }

        public void enrichVariables(@NotNull VariableContext.Builder builder) {
            builder.withAll(variables);
        }
    }
}
