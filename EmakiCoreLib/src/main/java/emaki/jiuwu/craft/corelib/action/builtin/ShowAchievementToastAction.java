package emaki.jiuwu.craft.corelib.action.builtin;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import emaki.jiuwu.craft.corelib.action.ActionContext;
import emaki.jiuwu.craft.corelib.action.ActionErrorType;
import emaki.jiuwu.craft.corelib.action.ActionParameter;
import emaki.jiuwu.craft.corelib.action.ActionParameterType;
import emaki.jiuwu.craft.corelib.action.ActionParsers;
import emaki.jiuwu.craft.corelib.action.ActionResult;
import emaki.jiuwu.craft.corelib.async.FoliaSchedulerAdapter;
import emaki.jiuwu.craft.corelib.item.ItemSource;
import emaki.jiuwu.craft.corelib.item.ItemSourceService;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.text.Texts;

public final class ShowAchievementToastAction extends BaseAction {

    private static final String PACKET_BRIDGE_CLASS = "emaki.jiuwu.craft.corelib.action.builtin.AchievementToastPacketBridge";
    private static final String DEFAULT_ICON = "minecraft-book";
    private static final String DEFAULT_FRAME = "task";
    private static final String DEFAULT_REMOVE_DELAY = "20t";

    private final ItemSourceService itemSourceService;

    public ShowAchievementToastAction(ItemSourceService itemSourceService) {
        super(
                "showachievementtoast",
                "feedback",
                "Show a client-side advancement toast without registering or granting an advancement.",
                ActionParameter.required("title", ActionParameterType.STRING, "Toast title"),
                ActionParameter.optional("description", ActionParameterType.STRING, "", "Toast description"),
                ActionParameter.optional("icon", ActionParameterType.STRING, DEFAULT_ICON, "Toast icon item source"),
                ActionParameter.optional("frame", ActionParameterType.STRING, DEFAULT_FRAME, "Toast frame: task, goal, or challenge"),
                ActionParameter.optional("id", ActionParameterType.STRING, "", "Optional client-side toast id"),
                ActionParameter.optional("remove_delay", ActionParameterType.TIME, DEFAULT_REMOVE_DELAY, "Delay before removing the fake advancement packet")
        );
        this.itemSourceService = itemSourceService;
    }

    @Override
    public ActionResult execute(ActionContext context, Map<String, String> arguments) {
        ActionResult playerCheck = requirePlayerResult(context);
        if (!playerCheck.success()) {
            return playerCheck;
        }
        if (!isPacketEventsPresent()) {
            return ActionResult.failure(ActionErrorType.PROVIDER_UNAVAILABLE, "PacketEvents is required to show achievement toasts.");
        }
        String frame = frameArgument(arguments);
        if (frame == null) {
            return ActionResult.failure(ActionErrorType.INVALID_ARGUMENT, "Unknown achievement toast frame: " + stringWithDefault(arguments, "frame", DEFAULT_FRAME));
        }
        Player player = context.player();
        String key = "emakicorelib:toast/" + toastId(arguments);
        ItemStack icon = resolveIcon(stringWithDefault(arguments, "icon", DEFAULT_ICON));
        try {
            boolean sent = invokeBridge("send", player, key,
                    stringArg(arguments, "title"),
                    stringWithDefault(arguments, "description", ""),
                    icon,
                    frame);
            if (!sent) {
                return ActionResult.failure(ActionErrorType.INVALID_STATE, "Could not send achievement toast packet to player.");
            }
            scheduleRemoval(context, player, key, removeDelay(arguments));
            return ActionResult.ok(Map.of("advancement", key, "frame", frame));
        } catch (Throwable throwable) {
            return ActionResult.failure(ActionErrorType.EXECUTION_EXCEPTION, "Could not show achievement toast: " + rootMessage(throwable));
        }
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
                // Fall through to a vanilla material fallback.
            }
        }
        String normalized = Texts.toStringSafe(rawIcon).replace("minecraft:", "").replace('-', '_').toUpperCase(Locale.ROOT);
        Material material = Material.matchMaterial(normalized);
        return new ItemStack(material == null ? Material.BOOK : material);
    }

    private String frameArgument(Map<String, String> arguments) {
        String frame = Texts.lower(stringWithDefault(arguments, "frame", DEFAULT_FRAME));
        return switch (frame) {
            case "task", "goal", "challenge" -> frame;
            default -> null;
        };
    }

    private long removeDelay(Map<String, String> arguments) {
        long parsed = ActionParsers.parseTicks(stringWithDefault(arguments, "remove_delay", DEFAULT_REMOVE_DELAY));
        return parsed < 0L ? ActionParsers.parseTicks(DEFAULT_REMOVE_DELAY) : Math.max(1L, parsed);
    }

    private String toastId(Map<String, String> arguments) {
        String raw = stringWithDefault(arguments, "id", "");
        String normalized = Texts.isBlank(raw) ? UUID.randomUUID().toString() : Texts.normalizeId(raw);
        StringBuilder result = new StringBuilder(normalized.length());
        for (int index = 0; index < normalized.length(); index++) {
            char ch = Character.toLowerCase(normalized.charAt(index));
            if ((ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9') || ch == '_' || ch == '-' || ch == '/') {
                result.append(ch);
            } else {
                result.append('_');
            }
        }
        return result.isEmpty() ? UUID.randomUUID().toString() : result.toString();
    }

    private boolean invokeBridge(String methodName, Player player, String key, String title, String description, ItemStack icon, String frame) throws Throwable {
        Class<?> bridge = Class.forName(PACKET_BRIDGE_CLASS);
        Method method = bridge.getMethod(methodName, Player.class, String.class, String.class, String.class, ItemStack.class, String.class);
        try {
            Object result = method.invoke(null, player, key, title, description, icon, frame);
            return Boolean.TRUE.equals(result);
        } catch (InvocationTargetException exception) {
            throw exception.getCause() == null ? exception : exception.getCause();
        }
    }

    private void scheduleRemoval(ActionContext context, Player player, String key, long removeDelayTicks) {
        Plugin schedulerPlugin = context == null ? null : context.sourcePlugin();
        if (schedulerPlugin == null) {
            schedulerPlugin = Bukkit.getPluginManager().getPlugin("EmakiCoreLib");
        }
        if (schedulerPlugin == null || !schedulerPlugin.isEnabled()) {
            removeToast(player, key);
            return;
        }
        FoliaSchedulerAdapter.runEntityTaskLater(schedulerPlugin, player, () -> removeToast(player, key), removeDelayTicks);
    }

    private void removeToast(Player player, String key) {
        try {
            invokeBridge("remove", player, key, "", "", new ItemStack(Material.BOOK), DEFAULT_FRAME);
        } catch (Throwable ignored) {
            // Best-effort client cleanup; the toast has already been sent.
        }
    }

    private boolean isPacketEventsPresent() {
        Plugin upper = Bukkit.getPluginManager().getPlugin("PacketEvents");
        Plugin lower = Bukkit.getPluginManager().getPlugin("packetevents");
        return (upper != null && upper.isEnabled()) || (lower != null && lower.isEnabled());
    }

    private String stringWithDefault(Map<String, String> arguments, String key, String fallback) {
        String value = arguments == null ? null : arguments.get(key);
        return Texts.isBlank(value) ? fallback : value;
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current instanceof InvocationTargetException invocation && invocation.getCause() != null) {
            current = invocation.getCause();
        }
        return Texts.toStringSafe(current == null ? null : current.getMessage());
    }
}
