package emaki.jiuwu.craft.corelib.variable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.text.Texts;
import me.clip.placeholderapi.PlaceholderAPI;

/**
 * 统一的变量上下文，用于公式引擎、Matcher 等需要读取运行时变量的场景。
 * <p>
 * 支持的变量来源（按优先级）：
 * <ol>
 *   <li>显式注入的自定义变量</li>
 *   <li>玩家 PDC 数据（通过 {@link #withPdc} 声明的键）</li>
 *   <li>玩家内置属性（level、health、food、exp 等）</li>
 *   <li>PlaceholderAPI（通过 {@link #withPapi} 声明的键）</li>
 * </ol>
 * <p>
 * 使用 Builder 模式构建，不可变实例。
 */
public final class VariableContext {

    private final Player player;
    private final Map<String, Object> variables;
    private final Map<NamespacedKey, PersistentDataType<?, ?>> pdcKeys;
    private final Map<String, String> papiKeys;
    private final Map<String, Object> resolvedCache;

    private VariableContext(Player player,
                            Map<String, Object> variables,
                            Map<NamespacedKey, PersistentDataType<?, ?>> pdcKeys,
                            Map<String, String> papiKeys) {
        this.player = player;
        this.variables = Map.copyOf(variables);
        this.pdcKeys = Map.copyOf(pdcKeys);
        this.papiKeys = Map.copyOf(papiKeys);
        this.resolvedCache = new LinkedHashMap<>();
    }

    /**
     * 创建 Builder。
     *
     * @param player 玩家，null 时仅支持显式变量
     * @return Builder
     */
    public static Builder builder(@Nullable Player player) {
        return new Builder(player);
    }

    /**
     * 获取 double 值。缺失或无法解析时返回 0.0。
     *
     * @param key 变量键
     * @return double 值
     */
    public double getDouble(@NotNull String key) {
        Object value = get(key);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String str) {
            try {
                return Double.parseDouble(str);
            } catch (NumberFormatException _) {
            }
        }
        return 0.0;
    }

    /**
     * 获取 String 值。缺失时返回空字符串。
     *
     * @param key 变量键
     * @return String 值
     */
    public @NotNull String getString(@NotNull String key) {
        Object value = get(key);
        return value == null ? "" : String.valueOf(value);
    }

    /**
     * 获取 int 值。缺失或无法解析时返回 0。
     *
     * @param key 变量键
     * @return int 值
     */
    public int getInt(@NotNull String key) {
        return (int) getDouble(key);
    }

    /**
     * 获取 long 值。缺失或无法解析时返回 0L。
     *
     * @param key 变量键
     * @return long 值
     */
    public long getLong(@NotNull String key) {
        return (long) getDouble(key);
    }

    /**
     * 检查变量是否存在。
     *
     * @param key 变量键
     * @return true 如果变量存在
     */
    public boolean has(@NotNull String key) {
        return get(key) != null;
    }

    /**
     * 导出所有已解析的变量为 Map（用于 ExpressionEngine）。
     *
     * @return 变量 Map
     */
    public @NotNull Map<String, Object> toMap() {
        Map<String, Object> result = new LinkedHashMap<>(variables);
        if (player != null) {
            putPlayerBuiltins(result, player);
        }
        for (NamespacedKey pdcKey : pdcKeys.keySet()) {
            String varKey = "pdc_" + pdcKey.getNamespace() + "_" + pdcKey.getKey();
            if (!resolvedCache.containsKey(varKey)) {
                Object value = resolvePdc(pdcKey);
                if (value != null) {
                    resolvedCache.put(varKey, value);
                }
            }
            if (resolvedCache.containsKey(varKey)) {
                result.put(varKey, resolvedCache.get(varKey));
            }
        }
        for (String papiKey : papiKeys.keySet()) {
            String varKey = "papi_" + papiKey;
            if (!resolvedCache.containsKey(varKey)) {
                String value = resolvePapi(papiKey);
                if (value != null) {
                    resolvedCache.put(varKey, value);
                }
            }
            if (resolvedCache.containsKey(varKey)) {
                result.put(varKey, resolvedCache.get(varKey));
            }
        }
        return result;
    }

    private @Nullable Object get(@NotNull String key) {
        if (variables.containsKey(key)) {
            return variables.get(key);
        }
        if (key.startsWith("pdc_") && player != null) {
            return resolvedCache.computeIfAbsent(key, k -> {
                for (NamespacedKey pdcKey : pdcKeys.keySet()) {
                    String pdcVarKey = "pdc_" + pdcKey.getNamespace() + "_" + pdcKey.getKey();
                    if (pdcVarKey.equals(key)) {
                        return resolvePdc(pdcKey);
                    }
                }
                return null;
            });
        }
        if (key.startsWith("papi_") && player != null) {
            return resolvedCache.computeIfAbsent(key, k -> {
                String papiPlaceholder = papiKeys.get(key.substring(5));
                return papiPlaceholder == null ? null : resolvePapi(papiPlaceholder);
            });
        }
        if (player != null) {
            return getPlayerBuiltin(key, player);
        }
        return null;
    }

    private @Nullable Object resolvePdc(@NotNull NamespacedKey key) {
        if (player == null) {
            return null;
        }
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        PersistentDataType<?, ?> type = pdcKeys.get(key);
        if (type == null) {
            return null;
        }
        try {
            if (type == PersistentDataType.DOUBLE) {
                return pdc.get(key, PersistentDataType.DOUBLE);
            } else if (type == PersistentDataType.INTEGER) {
                return pdc.get(key, PersistentDataType.INTEGER);
            } else if (type == PersistentDataType.LONG) {
                return pdc.get(key, PersistentDataType.LONG);
            } else if (type == PersistentDataType.STRING) {
                return pdc.get(key, PersistentDataType.STRING);
            }
        } catch (Exception _) {
        }
        return null;
    }

    private @Nullable String resolvePapi(@NotNull String placeholder) {
        if (player == null || !Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            return null;
        }
        try {
            return PlaceholderAPI.setPlaceholders(player, "%" + placeholder + "%");
        } catch (Exception _) {
            return null;
        }
    }

    private static void putPlayerBuiltins(Map<String, Object> target, Player player) {
        target.putIfAbsent("player_level", player.getLevel());
        target.putIfAbsent("player_exp", player.getExp());
        target.putIfAbsent("player_food", player.getFoodLevel());
        target.putIfAbsent("player_health", player.getHealth());
        target.putIfAbsent("player_max_health", player.getMaxHealth());
        target.putIfAbsent("player_world", player.getWorld() == null ? "" : player.getWorld().getName());
    }

    private static @Nullable Object getPlayerBuiltin(String key, Player player) {
        return switch (key) {
            case "player_level" -> player.getLevel();
            case "player_exp" -> player.getExp();
            case "player_food" -> player.getFoodLevel();
            case "player_health" -> player.getHealth();
            case "player_max_health" -> player.getMaxHealth();
            case "player_world" -> player.getWorld() == null ? "" : player.getWorld().getName();
            case "player_name" -> player.getName();
            case "player_uuid" -> player.getUniqueId().toString();
            default -> null;
        };
    }

    public static final class Builder {
        private final Player player;
        private final Map<String, Object> variables = new LinkedHashMap<>();
        private final Map<NamespacedKey, PersistentDataType<?, ?>> pdcKeys = new LinkedHashMap<>();
        private final Map<String, String> papiKeys = new LinkedHashMap<>();

        private Builder(@Nullable Player player) {
            this.player = player;
        }

        /**
         * 添加自定义变量。
         *
         * @param key   变量键
         * @param value 变量值
         * @return this
         */
        public Builder with(@NotNull String key, @Nullable Object value) {
            Objects.requireNonNull(key, "key");
            variables.put(key, value);
            return this;
        }

        /**
         * 批量添加自定义变量。
         *
         * @param vars 变量 Map
         * @return this
         */
        public Builder withAll(@Nullable Map<String, ?> vars) {
            if (vars != null && !vars.isEmpty()) {
                variables.putAll(vars);
            }
            return this;
        }

        /**
         * 声明从玩家 PDC 读取的键（double 类型）。
         *
         * @param key PDC 键
         * @return this
         */
        public Builder withPdcDouble(@NotNull NamespacedKey key) {
            Objects.requireNonNull(key, "key");
            pdcKeys.put(key, PersistentDataType.DOUBLE);
            return this;
        }

        /**
         * 声明从玩家 PDC 读取的键（int 类型）。
         *
         * @param key PDC 键
         * @return this
         */
        public Builder withPdcInt(@NotNull NamespacedKey key) {
            Objects.requireNonNull(key, "key");
            pdcKeys.put(key, PersistentDataType.INTEGER);
            return this;
        }

        /**
         * 声明从玩家 PDC 读取的键（long 类型）。
         *
         * @param key PDC 键
         * @return this
         */
        public Builder withPdcLong(@NotNull NamespacedKey key) {
            Objects.requireNonNull(key, "key");
            pdcKeys.put(key, PersistentDataType.LONG);
            return this;
        }

        /**
         * 声明从玩家 PDC 读取的键（string 类型）。
         *
         * @param key PDC 键
         * @return this
         */
        public Builder withPdcString(@NotNull NamespacedKey key) {
            Objects.requireNonNull(key, "key");
            pdcKeys.put(key, PersistentDataType.STRING);
            return this;
        }

        /**
         * 声明从 PlaceholderAPI 读取的占位符。最终变量键为 "papi_{key}"。
         *
         * @param key         变量键
         * @param placeholder PAPI 占位符（不含 % 符号）
         * @return this
         */
        public Builder withPapi(@NotNull String key, @NotNull String placeholder) {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(placeholder, "placeholder");
            papiKeys.put(key, placeholder);
            return this;
        }

        /**
         * 构建不可变的 VariableContext。
         *
         * @return VariableContext
         */
        public VariableContext build() {
            return new VariableContext(player, variables, pdcKeys, papiKeys);
        }
    }
}
