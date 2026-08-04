package emaki.jiuwu.craft.skills.bridge;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.text.LogMessages;
import emaki.jiuwu.craft.corelib.api.text.MiniMessages;
import emaki.jiuwu.craft.skills.model.ResourceCostType;

public final class ExternalManaBridge {

    private static final String AURASKILLS_PLUGIN_NAME = "AuraSkills";
    private static final String MYTHICLIB_PLUGIN_NAME = "MythicLib";
    private static final String MMOCORE_PLUGIN_NAME = "MMOCore";

    private final JavaPlugin plugin;
    private final LogMessages messages;
    private AuraSkillsAccess auraSkills;
    private MythicManaAccess mythicMana;

    public ExternalManaBridge(JavaPlugin plugin, LogMessages messages) {
        this.plugin = plugin;
        this.messages = messages;
    }

    public void init() {
        auraSkills = loadAuraSkills();
        mythicMana = loadMythicMana();
    }

    public void shutdown() {
        auraSkills = null;
        mythicMana = null;
    }

    public boolean isAvailable(ResourceCostType type) {
        return switch (type) {
            case AURASKILLS_MANA -> auraSkills != null && auraSkills.available();
            case MYTHICLIB_MANA -> mythicMana != null && mythicMana.available();
            default -> false;
        };
    }

    public double readCurrent(Player player, ResourceCostType type) {
        if (player == null) {
            return -1D;
        }
        try {
            return switch (type) {
                case AURASKILLS_MANA -> auraSkills == null ? -1D : auraSkills.read(player);
                case MYTHICLIB_MANA -> mythicMana == null ? -1D : mythicMana.read(player);
                default -> -1D;
            };
        } catch (Exception exception) {
            warning(readFailureKey(type), Map.of("error", errorMessage(exception)), exception);
            return -1D;
        }
    }

    public boolean consume(Player player, ResourceCostType type, double amount) {
        if (player == null || amount <= 0D) {
            return true;
        }
        try {
            return switch (type) {
                case AURASKILLS_MANA -> auraSkills != null && auraSkills.consume(player, amount);
                case MYTHICLIB_MANA -> mythicMana != null && mythicMana.consume(player, amount);
                default -> false;
            };
        } catch (Exception exception) {
            warning(consumeFailureKey(type), Map.of("error", errorMessage(exception)), exception);
            return false;
        }
    }

    private AuraSkillsAccess loadAuraSkills() {
        if (!plugin.getServer().getPluginManager().isPluginEnabled(AURASKILLS_PLUGIN_NAME)) {
            info("console.auraskills_mana_unavailable");
            return null;
        }
        try {
            Class<?> apiClass = Class.forName("dev.aurelium.auraskills.api.AuraSkillsApi");
            Method getMethod = apiClass.getMethod("get");
            Object api = getMethod.invoke(null);
            if (api == null) {
                info("console.auraskills_mana_unavailable");
                return null;
            }
            Method userMethod = firstMethod(apiClass, List.of("getUser"), UUID.class, Player.class);
            if (userMethod == null) {
                info("console.auraskills_mana_unavailable");
                return null;
            }
            Class<?> userClass = userMethod.getReturnType();
            Method readMethod = firstNoArgNumberMethod(userClass, List.of("getMana", "mana"));
            Method setMethod = firstOneNumberArgMethod(userClass, List.of("setMana"));
            if (readMethod == null || setMethod == null) {
                info("console.auraskills_mana_unavailable");
                return null;
            }
            readMethod.setAccessible(true);
            setMethod.setAccessible(true);
            info("console.auraskills_mana_ready", Map.of("mode", "AuraSkillsApi"));
            return new AuraSkillsAccess(api, userMethod, readMethod, setMethod);
        } catch (Exception | LinkageError exception) {
            warning("console.auraskills_mana_init_failed", Map.of("error", errorMessage(exception)), exception);
            return null;
        }
    }

    private MythicManaAccess loadMythicMana() {
        if (!plugin.getServer().getPluginManager().isPluginEnabled(MYTHICLIB_PLUGIN_NAME)) {
            info("console.mythic_mana_unavailable");
            return null;
        }
        try {
            Class<?> mythicLibClass = Class.forName("io.lumine.mythic.lib.MythicLib");
            Object mythicLib = invokeStaticNoArg(mythicLibClass, "inst");
            if (mythicLib == null) {
                Field pluginField = mythicLibClass.getField("plugin");
                mythicLib = pluginField.get(null);
            }
            if (mythicLib == null) {
                info("console.mythic_mana_unavailable");
                return null;
            }
            Method getManaModule = mythicLibClass.getMethod("getManaModule");
            Object manaModule = getManaModule.invoke(mythicLib);
            if (manaModule == null) {
                manaModule = manaModuleFromString();
            }
            if (manaModule == null) {
                info("console.mythic_mana_unavailable");
                return null;
            }
            Class<?> playerDataClass = Class.forName("io.lumine.mythic.lib.api.player.MMOPlayerData");
            Method playerDataMethod = firstStaticMethod(playerDataClass, List.of("online", "get", "setup"), Player.class, UUID.class);
            Method readMethod = manaModule.getClass().getMethod("getMana", playerDataClass);
            Class<?> reasonClass = Class.forName("io.lumine.mythic.lib.player.resource.ResourceUpdateReason");
            Method setMethod = manaModule.getClass().getMethod("setMana", playerDataClass, double.class, reasonClass);
            Object reason = enumConstant(reasonClass, List.of("SKILL", "MECHANIC", "COMMAND", "OTHER"));
            if (playerDataMethod == null || reason == null) {
                info("console.mythic_mana_unavailable");
                return null;
            }
            readMethod.setAccessible(true);
            setMethod.setAccessible(true);
            String mode = plugin.getServer().getPluginManager().isPluginEnabled(MMOCORE_PLUGIN_NAME)
                    ? "MMOCore via MythicLib ManaModule"
                    : "MythicLib ManaModule";
            info("console.mythic_mana_ready", Map.of("mode", mode));
            return new MythicManaAccess(playerDataMethod, manaModule, readMethod, setMethod, reason);
        } catch (Exception | LinkageError exception) {
            warning("console.mythic_mana_init_failed", Map.of("error", errorMessage(exception)), exception);
            return null;
        }
    }

    private Object manaModuleFromString() {
        try {
            Class<?> manaModuleClass = Class.forName("io.lumine.mythic.lib.rpg.ManaModule");
            Method from = manaModuleClass.getMethod("from", String.class);
            for (String id : List.of("MMOCore", "mmocore", "MythicLib", "native")) {
                Object module = from.invoke(null, id);
                if (module != null) {
                    return module;
                }
            }
        } catch (Exception | LinkageError ignored) {
        }
        return null;
    }

    private Method firstMethod(Class<?> owner, List<String> names, Class<?>... parameterCandidates) {
        for (String name : names) {
            for (Class<?> parameter : parameterCandidates) {
                try {
                    Method method = owner.getMethod(name, parameter);
                    method.setAccessible(true);
                    return method;
                } catch (NoSuchMethodException ignored) {
                }
            }
        }
        return null;
    }

    private Method firstStaticMethod(Class<?> owner, List<String> names, Class<?>... parameterCandidates) {
        for (String name : names) {
            for (Class<?> parameter : parameterCandidates) {
                try {
                    Method method = owner.getMethod(name, parameter);
                    if (Modifier.isStatic(method.getModifiers())) {
                        method.setAccessible(true);
                        return method;
                    }
                } catch (NoSuchMethodException ignored) {
                }
            }
        }
        return null;
    }

    private Method firstNoArgNumberMethod(Class<?> owner, List<String> names) {
        for (String name : names) {
            try {
                Method method = owner.getMethod(name);
                if (Number.class.isAssignableFrom(wrap(method.getReturnType())) || method.getReturnType().isPrimitive()) {
                    return method;
                }
            } catch (NoSuchMethodException ignored) {
            }
        }
        return null;
    }

    private Method firstOneNumberArgMethod(Class<?> owner, List<String> names) {
        for (String name : names) {
            for (Class<?> type : List.of(double.class, Double.class, float.class, Float.class, int.class, Integer.class)) {
                try {
                    return owner.getMethod(name, type);
                } catch (NoSuchMethodException ignored) {
                }
            }
        }
        return null;
    }

    private Object invokeStaticNoArg(Class<?> owner, String methodName) {
        try {
            Method method = owner.getMethod(methodName);
            if (!Modifier.isStatic(method.getModifiers())) {
                return null;
            }
            return method.invoke(null);
        } catch (Exception ignored) {
            return null;
        }
    }

    private Object enumConstant(Class<?> enumClass, List<String> names) {
        if (!enumClass.isEnum()) {
            return null;
        }
        Object[] constants = enumClass.getEnumConstants();
        if (constants == null) {
            return null;
        }
        for (String name : names) {
            for (Object constant : constants) {
                if (((Enum<?>) constant).name().equalsIgnoreCase(name)) {
                    return constant;
                }
            }
        }
        return constants.length == 0 ? null : constants[0];
    }

    private Class<?> wrap(Class<?> type) {
        if (type == int.class) return Integer.class;
        if (type == long.class) return Long.class;
        if (type == float.class) return Float.class;
        if (type == double.class) return Double.class;
        if (type == short.class) return Short.class;
        if (type == byte.class) return Byte.class;
        return type;
    }

    private Object adaptNumber(Method method, double value) {
        Class<?> type = method.getParameterTypes()[0];
        if (type == float.class || type == Float.class) {
            return (float) value;
        }
        if (type == int.class || type == Integer.class) {
            return (int) Math.round(value);
        }
        return value;
    }

    private String readFailureKey(ResourceCostType type) {
        return type == ResourceCostType.AURASKILLS_MANA
                ? "console.auraskills_mana_read_failed"
                : "console.mythic_mana_read_failed";
    }

    private String consumeFailureKey(ResourceCostType type) {
        return type == ResourceCostType.AURASKILLS_MANA
                ? "console.auraskills_mana_consume_failed"
                : "console.mythic_mana_consume_failed";
    }

    private void info(String key) {
        info(key, Map.of());
    }

    private void info(String key, Map<String, ?> replacements) {
        if (messages != null) {
            messages.info(key, replacements == null ? Map.of() : replacements);
            return;
        }
        plugin.getLogger().info(key);
    }

    private void warning(String key, Map<String, ?> replacements, Throwable throwable) {
        String text = messages == null ? key : messages.message(key, replacements == null ? Map.of() : replacements);
        String plainText = messages == null ? text : MiniMessages.plainText(text);
        if (throwable == null) {
            plugin.getLogger().warning(plainText);
            return;
        }
        plugin.getLogger().log(Level.WARNING, plainText, throwable);
    }

    private String errorMessage(Throwable throwable) {
        if (throwable == null) {
            return "unknown";
        }
        String message = throwable.getMessage();
        return message == null || message.isBlank() ? throwable.getClass().getSimpleName() : message;
    }

    private final class AuraSkillsAccess {

        private final Object api;
        private final Method userMethod;
        private final Method readMethod;
        private final Method setMethod;

        private AuraSkillsAccess(Object api, Method userMethod, Method readMethod, Method setMethod) {
            this.api = api;
            this.userMethod = userMethod;
            this.readMethod = readMethod;
            this.setMethod = setMethod;
        }

        private boolean available() {
            return api != null && userMethod != null && readMethod != null && setMethod != null;
        }

        private double read(Player player) throws Exception {
            Object user = user(player);
            if (user == null) {
                return -1D;
            }
            Object value = readMethod.invoke(user);
            return value instanceof Number number ? number.doubleValue() : -1D;
        }

        private boolean consume(Player player, double amount) throws Exception {
            Object user = user(player);
            if (user == null) {
                return false;
            }
            double current = read(player);
            if (current < 0D) {
                return false;
            }
            setMethod.invoke(user, adaptNumber(setMethod, Math.max(0D, current - amount)));
            return true;
        }

        private Object user(Player player) throws Exception {
            Class<?> parameterType = userMethod.getParameterTypes()[0];
            Object argument = parameterType == UUID.class ? player.getUniqueId() : player;
            return userMethod.invoke(api, argument);
        }
    }

    private final class MythicManaAccess {

        private final Method playerDataMethod;
        private final Object manaModule;
        private final Method readMethod;
        private final Method setMethod;
        private final Object reason;

        private MythicManaAccess(Method playerDataMethod, Object manaModule, Method readMethod, Method setMethod, Object reason) {
            this.playerDataMethod = playerDataMethod;
            this.manaModule = manaModule;
            this.readMethod = readMethod;
            this.setMethod = setMethod;
            this.reason = reason;
        }

        private boolean available() {
            return playerDataMethod != null && manaModule != null && readMethod != null && setMethod != null && reason != null;
        }

        private double read(Player player) throws Exception {
            Object data = playerData(player);
            if (data == null) {
                return -1D;
            }
            Object value = readMethod.invoke(manaModule, data);
            return value instanceof Number number ? number.doubleValue() : -1D;
        }

        private boolean consume(Player player, double amount) throws Exception {
            Object data = playerData(player);
            if (data == null) {
                return false;
            }
            double current = read(player);
            if (current < 0D) {
                return false;
            }
            Object result = setMethod.invoke(manaModule, data, Math.max(0D, current - amount), reason);
            return !(result instanceof Boolean bool) || bool;
        }

        private Object playerData(Player player) throws Exception {
            Class<?> parameterType = playerDataMethod.getParameterTypes()[0];
            Object argument = parameterType == UUID.class ? player.getUniqueId() : player;
            return playerDataMethod.invoke(null, argument);
        }
    }
}
