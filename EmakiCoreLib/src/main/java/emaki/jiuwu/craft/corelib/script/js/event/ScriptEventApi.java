package emaki.jiuwu.craft.corelib.script.js.event;

import java.util.LinkedHashMap;
import java.util.Map;

import org.graalvm.polyglot.HostAccess;

import emaki.jiuwu.craft.corelib.script.ScriptEntitySnapshot.EntityView;
import emaki.jiuwu.craft.corelib.script.ScriptSnapshots;
import emaki.jiuwu.craft.corelib.text.Texts;


public final class ScriptEventApi {

    private final String type;
    private final boolean async;
    private final boolean cancellable;
    private final boolean allowMutation;
    private final EntityView entity;
    private final EntityView player;
    private final EntityView damager;
    private final CauseView cause;
    private final boolean hasDamage;
    private final boolean hasMessage;
    private final boolean movable;
    private final String command;
    private final Map<String, Object> from;
    private boolean cancelled;
    private double damage;
    private String message;
    private Map<String, Object> to;
    private boolean cancellationDirty;
    private boolean damageDirty;
    private boolean messageDirty;
    private boolean toDirty;

    public ScriptEventApi(
            String type,
            boolean async,
            boolean cancellable,
            boolean cancelled,
            boolean allowMutation,
            EntityView entity,
            EntityView player,
            EntityView damager,
            String cause,
            boolean hasDamage,
            double damage,
            boolean hasMessage,
            String message,
            String command,
            Map<String, ?> from,
            Map<String, ?> to,
            boolean movable) {
        this.type = type == null ? "" : type;
        this.async = async;
        this.cancellable = cancellable;
        this.cancelled = cancellable && cancelled;
        this.allowMutation = allowMutation;
        this.entity = entity == null ? EntityView.empty() : entity;
        this.player = player == null ? EntityView.empty() : player;
        this.damager = damager == null ? EntityView.empty() : damager;
        this.cause = new CauseView(cause);
        this.hasDamage = hasDamage;
        this.damage = Math.max(0D, damage);
        this.hasMessage = hasMessage;
        this.message = message == null ? "" : message;
        this.command = command == null ? "" : command;
        this.from = from == null ? Map.of() : ScriptSnapshots.immutableMap(from);
        this.to = to == null ? Map.of() : ScriptSnapshots.immutableMap(to);
        this.movable = movable;
    }

    @HostAccess.Export
    public String type() {
        return type;
    }

    @HostAccess.Export
    public boolean async() {
        return async;
    }

    @HostAccess.Export
    public boolean cancellable() {
        return cancellable;
    }

    @HostAccess.Export
    public boolean cancelled() {
        return cancellable && cancelled;
    }

    @HostAccess.Export
    public void setCancelled(boolean cancelled) {
        if (allowMutation && cancellable) {
            this.cancelled = cancelled;
            this.cancellationDirty = true;
        }
    }

    @HostAccess.Export
    public EntityView entity() {
        return entity;
    }

    @HostAccess.Export
    public EntityView player() {
        return player;
    }

    @HostAccess.Export
    public EntityView damager() {
        return damager;
    }

    @HostAccess.Export
    public double damage() {
        return hasDamage ? damage : 0D;
    }

    @HostAccess.Export
    public void setDamage(double damage) {
        if (allowMutation && hasDamage) {
            this.damage = Math.max(0D, damage);
            this.damageDirty = true;
        }
    }

    @HostAccess.Export
    public CauseView cause() {
        return cause;
    }

    @HostAccess.Export
    public String message() {
        return hasMessage ? message : "";
    }

    @HostAccess.Export
    public void setMessage(String message) {
        if (allowMutation && hasMessage) {
            this.message = Texts.toStringSafe(message);
            this.messageDirty = true;
        }
    }

    @HostAccess.Export
    public String command() {
        return command;
    }

    @HostAccess.Export
    public Map<String, Object> from() {
        return from;
    }

    @HostAccess.Export
    public Map<String, Object> to() {
        return to;
    }

    @HostAccess.Export
    public void setTo(double x, double y, double z) {
        if (!allowMutation || !movable || to.isEmpty()) {
            return;
        }
        Map<String, Object> moved = new LinkedHashMap<>(to);
        moved.put("x", x);
        moved.put("y", y);
        moved.put("z", z);
        to = ScriptSnapshots.immutableMap(moved);
        toDirty = true;
    }

    public boolean cancellationDirty() {
        return cancellationDirty;
    }

    public boolean damageDirty() {
        return damageDirty;
    }

    public boolean messageDirty() {
        return messageDirty;
    }

    public boolean toDirty() {
        return toDirty;
    }

    public boolean hasDamage() {
        return hasDamage;
    }

    public boolean hasMessage() {
        return hasMessage;
    }

    public boolean movable() {
        return movable;
    }

    public record CauseView(String name) {

        public CauseView {
            name = name == null ? "" : name;
        }

        @Override
        @HostAccess.Export
        public String name() {
            return name;
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
