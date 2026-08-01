package emaki.jiuwu.craft.codex.action.v2;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.codex.EmakiCodexPlugin;
import emaki.jiuwu.craft.corelib.api.action.CoreActionExecutionTarget;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreActionFailureKind;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreActionOutcome;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreActionStage;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreActionSubject;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreResolvedArguments;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreStageContext;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreStageParameter;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreStageParameterType;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreStagePlanningContext;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreTargetRequirement;
import emaki.jiuwu.craft.corelib.item.ItemSource;
import emaki.jiuwu.craft.corelib.item.ItemSourceService;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.text.Texts;

/**
 * Shows a client-side advancement toast without registering or granting an advancement.
 *
 * <p>The v2 counterpart of {@code ShowAchievementToastAction}. The toast is a fake advancement pushed over the
 * wire and withdrawn again a moment later, so the removal is scheduled rather than immediate.</p>
 *
 * <p>Domain {@code CONTEXT_ENTITY}: the packet goes to one player's connection. Despite touching the network
 * layer this is not global state, so it does not need the global domain.</p>
 */
public final class ShowAchievementToastStage implements CoreActionStage {

    private static final String PACKET_BRIDGE_CLASS =
            "emaki.jiuwu.craft.codex.advancement.packet.AchievementToastPacketBridge";
    private static final String DEFAULT_ICON = "minecraft-book";
    private static final String DEFAULT_FRAME = "task";
    private static final String DEFAULT_REMOVE_DELAY = "20t";
    private static final long DEFAULT_REMOVE_DELAY_TICKS = 20L;

    /**
     * Guards against a stale removal cancelling a newer toast.
     *
     * <p>Static because the tokens have to survive a reload: a removal scheduled before the reload still fires
     * afterwards, and it must recognise that its own toast has since been replaced.</p>
     */
    private static final ConcurrentMap<String, UUID> REMOVAL_TOKENS = new ConcurrentHashMap<>();

    private final EmakiCodexPlugin plugin;
    private final ItemSourceService itemSourceService;

    /**
     * Creates the stage.
     *
     * @param plugin owning plugin, used for scheduling the removal
     * @param itemSourceService resolves the toast icon, may be {@code null}
     */
    public ShowAchievementToastStage(@NotNull EmakiCodexPlugin plugin, ItemSourceService itemSourceService) {
        this.plugin = plugin;
        this.itemSourceService = itemSourceService;
    }

    @Override
    public @NotNull String id() {
        return "codex_show_toast";
    }

    @Override
    public @NotNull String description() {
        return "Shows a client-side advancement toast to the target.";
    }

    @Override
    public @NotNull String category() {
        return "codex";
    }

    @Override
    public @NotNull List<CoreStageParameter> parameters() {
        return List.of(
                CoreStageParameter.required("title", CoreStageParameterType.STRING, "Toast title"),
                CoreStageParameter.optional("description", CoreStageParameterType.STRING, "",
                        "Toast description"),
                CoreStageParameter.optional("icon", CoreStageParameterType.STRING, DEFAULT_ICON,
                        "Toast icon item source"),
                CoreStageParameter.optional("frame", CoreStageParameterType.STRING, DEFAULT_FRAME,
                        "Toast frame: task, goal, or challenge"),
                CoreStageParameter.optional("id", CoreStageParameterType.STRING, "",
                        "Client-side toast id"),
                CoreStageParameter.optional("remove_delay", CoreStageParameterType.DURATION,
                        DEFAULT_REMOVE_DELAY, "Delay before withdrawing the toast"));
    }

    @Override
    public @NotNull CoreTargetRequirement targetRequirement() {
        return CoreTargetRequirement.REQUIRED_ENTITY;
    }

    @Override
    public @NotNull CoreActionExecutionTarget executionTarget(@NotNull CoreStagePlanningContext context) {
        return CoreActionExecutionTarget.contextEntity();
    }

    @Override
    public @NotNull CoreActionOutcome execute(@NotNull CoreStageContext context,
            @NotNull CoreResolvedArguments arguments) {
        Player target = player(context.currentTarget());
        if (target == null) {
            return CoreActionOutcome.skipped("action.v2.stage.common.not_player");
        }
        if (!isPacketEventsPresent()) {
            return CoreActionOutcome.failure(CoreActionFailureKind.MISSING_CONTEXT,
                    "action.v2.stage.codex.toast_requires_packetevents");
        }
        String title = Texts.trim(arguments.getString("title"));
        if (title.isEmpty()) {
            return CoreActionOutcome.failure(CoreActionFailureKind.INVALID_CONFIG,
                    "action.v2.stage.codex.title_required");
        }
        String frame = frame(arguments);
        if (frame == null) {
            return CoreActionOutcome.failure(CoreActionFailureKind.INVALID_CONFIG,
                    "action.v2.stage.codex.unknown_frame",
                    Map.of("frame", arguments.getString("frame")));
        }
        String key = "emakicodex:toast/" + toastId(arguments);
        ItemStack icon = resolveIcon(arguments.getString("icon"));
        try {
            if (!invokeBridge("send", target, key, title, arguments.getString("description"), icon, frame)) {
                return CoreActionOutcome.failure(CoreActionFailureKind.INTERNAL_ERROR,
                        "action.v2.stage.codex.toast_send_failed");
            }
            scheduleRemoval(target, key, removeDelayTicks(arguments));
            return CoreActionOutcome.success(Map.of("advancement", key, "frame", frame));
        } catch (Throwable throwable) {
            return CoreActionOutcome.failure(CoreActionFailureKind.INTERNAL_ERROR,
                    "action.v2.stage.codex.toast_error",
                    Map.of("error", String.valueOf(rootMessage(throwable))));
        }
    }

    private String frame(CoreResolvedArguments arguments) {
        String frame = Texts.lower(arguments.getString("frame", DEFAULT_FRAME));
        return switch (frame) {
            case "task", "goal", "challenge" -> frame;
            default -> null;
        };
    }

    private long removeDelayTicks(CoreResolvedArguments arguments) {
        long parsed = arguments.getDurationTicks("remove_delay", DEFAULT_REMOVE_DELAY_TICKS);
        return parsed <= 0L ? DEFAULT_REMOVE_DELAY_TICKS : parsed;
    }

    /**
     * Builds a namespace-safe toast id.
     *
     * <p>A blank id becomes a random one so two simultaneous toasts cannot collide, and every other character
     * is folded to an underscore because the key becomes a {@code NamespacedKey} path.</p>
     */
    private String toastId(CoreResolvedArguments arguments) {
        String raw = arguments.getString("id", "");
        String normalized = Texts.isBlank(raw) ? UUID.randomUUID().toString() : Texts.normalizeId(raw);
        StringBuilder result = new StringBuilder(normalized.length());
        for (int index = 0; index < normalized.length(); index++) {
            char ch = Character.toLowerCase(normalized.charAt(index));
            boolean safe = (ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9')
                    || ch == '_' || ch == '-' || ch == '/';
            result.append(safe ? ch : '_');
        }
        return result.isEmpty() ? UUID.randomUUID().toString() : result.toString();
    }

    private ItemStack resolveIcon(String rawIcon) {
        if (itemSourceService != null) {
            try {
                ItemSource source = ItemSourceUtil.parse(rawIcon);
                if (source != null) {
                    ItemStack itemStack = itemSourceService.createItem(source, 1);
                    if (itemStack != null && !itemStack.getType().isAir()) {
                        return itemStack;
                    }
                }
            } catch (RuntimeException ignored) {
                // Fall through to the material lookup: a bad icon must not stop the toast.
            }
        }
        String normalized = Texts.toStringSafe(rawIcon)
                .replace("minecraft:", "")
                .replace('-', '_')
                .toUpperCase(Locale.ROOT);
        Material material = Material.matchMaterial(normalized);
        return new ItemStack(material == null ? Material.BOOK : material);
    }

    private boolean isPacketEventsPresent() {
        try {
            Class.forName("com.github.retrooper.packetevents.PacketEvents");
            return true;
        } catch (ClassNotFoundException exception) {
            return false;
        }
    }

    private boolean invokeBridge(String methodName,
            Player target,
            String key,
            String title,
            String description,
            ItemStack icon,
            String frame) throws Throwable {
        Class<?> bridge = Class.forName(PACKET_BRIDGE_CLASS);
        Method method = bridge.getMethod(methodName, Player.class, String.class, String.class,
                String.class, ItemStack.class, String.class);
        try {
            return Boolean.TRUE.equals(method.invoke(null, target, key, title, description, icon, frame));
        } catch (InvocationTargetException exception) {
            throw exception.getCause() == null ? exception : exception.getCause();
        }
    }

    private void scheduleRemoval(Player target, String key, long removeDelayTicks) {
        String tokenKey = target.getUniqueId() + "|" + key;
        UUID token = UUID.randomUUID();
        REMOVAL_TOKENS.put(tokenKey, token);
        if (!plugin.isEnabled()) {
            // Withdraw immediately rather than leaving a permanent fake advancement on the client.
            removeToast(target, key, tokenKey, token);
            return;
        }
        try {
            Object scheduled = plugin.executionDispatcher().runEntityLater(plugin, target,
                    () -> removeToast(target, key, tokenKey, token),
                    () -> { },
                    removeDelayTicks);
            if (scheduled == null) {
                REMOVAL_TOKENS.remove(tokenKey, token);
            }
        } catch (Throwable ignored) {
            REMOVAL_TOKENS.remove(tokenKey, token);
        }
    }

    private void removeToast(Player target, String key, String tokenKey, UUID token) {
        if (!REMOVAL_TOKENS.remove(tokenKey, token)) {
            return;
        }
        try {
            invokeBridge("remove", target, key, "", "", new ItemStack(Material.BOOK), DEFAULT_FRAME);
        } catch (Throwable ignored) {
            // The client drops the fake advancement on its own when it leaves; nothing else to do here.
        }
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private static Player player(CoreActionSubject subject) {
        return subject != null && subject.entityOrNull() instanceof Player resolved ? resolved : null;
    }
}
