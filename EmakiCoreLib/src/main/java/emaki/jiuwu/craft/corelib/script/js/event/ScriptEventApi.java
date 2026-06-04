package emaki.jiuwu.craft.corelib.script.js.event;

import java.util.LinkedHashMap;
import java.util.Map;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.graalvm.polyglot.HostAccess;

import emaki.jiuwu.craft.corelib.api.script.ScriptServerApi.ScriptEntityApi;
import emaki.jiuwu.craft.corelib.text.Texts;

public final class ScriptEventApi {

    private final String type;
    private final Event event;
    private final boolean allowMutation;
    private final Map<String, Object> meta = new LinkedHashMap<>();

    public ScriptEventApi(String type, Event event, boolean allowMutation) {
        this.type = Texts.normalizeId(type);
        this.event = event;
        this.allowMutation = allowMutation;
    }

    @HostAccess.Export
    public String type() {
        return type;
    }

    @HostAccess.Export
    public String eventClass() {
        return event == null ? "" : event.getClass().getName();
    }

    @HostAccess.Export
    public boolean cancellable() {
        return event instanceof Cancellable;
    }

    @HostAccess.Export
    public boolean cancelled() {
        return event instanceof Cancellable cancellable && cancellable.isCancelled();
    }

    @HostAccess.Export
    public void cancel() {
        setCancelled(true);
    }

    @HostAccess.Export
    public void setCancelled(boolean cancelled) {
        if (allowMutation && event instanceof Cancellable cancellable) {
            cancellable.setCancelled(cancelled);
        }
    }

    @HostAccess.Export
    public boolean mutable() {
        return allowMutation;
    }

    @HostAccess.Export
    public ScriptEntityApi player() {
        if (event instanceof PlayerInteractEvent interactEvent) {
            return new ScriptEntityApi(interactEvent.getPlayer());
        }
        if (event instanceof PlayerJoinEvent joinEvent) {
            return new ScriptEntityApi(joinEvent.getPlayer());
        }
        return new ScriptEntityApi(null);
    }

    @HostAccess.Export
    public ScriptEntityApi damager() {
        return new ScriptEntityApi(event instanceof EntityDamageByEntityEvent damageEvent ? damageEvent.getDamager() : null);
    }

    @HostAccess.Export
    public ScriptEntityApi entity() {
        return new ScriptEntityApi(event instanceof EntityDamageByEntityEvent damageEvent ? damageEvent.getEntity() : null);
    }

    @HostAccess.Export
    public ScriptEntityApi victim() {
        return entity();
    }

    @HostAccess.Export
    public String action() {
        return event instanceof PlayerInteractEvent interactEvent && interactEvent.getAction() != null
                ? interactEvent.getAction().name()
                : "";
    }

    @HostAccess.Export
    public boolean rightClick() {
        if (!(event instanceof PlayerInteractEvent interactEvent)) {
            return false;
        }
        Action action = interactEvent.getAction();
        return action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK;
    }

    @HostAccess.Export
    public boolean leftClick() {
        if (!(event instanceof PlayerInteractEvent interactEvent)) {
            return false;
        }
        Action action = interactEvent.getAction();
        return action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK;
    }

    @HostAccess.Export
    public String hand() {
        return event instanceof PlayerInteractEvent interactEvent && interactEvent.getHand() != null
                ? interactEvent.getHand().name()
                : "";
    }

    @HostAccess.Export
    public String clickedBlockType() {
        Block block = event instanceof PlayerInteractEvent interactEvent ? interactEvent.getClickedBlock() : null;
        return block == null ? "" : block.getType().name().toLowerCase();
    }

    @HostAccess.Export
    public Map<String, Object> clickedBlockLocation() {
        Block block = event instanceof PlayerInteractEvent interactEvent ? interactEvent.getClickedBlock() : null;
        return block == null ? Map.of() : locationMap(block.getLocation());
    }

    @HostAccess.Export
    public String itemType() {
        if (!(event instanceof PlayerInteractEvent interactEvent) || interactEvent.getItem() == null) {
            return "";
        }
        Material material = interactEvent.getItem().getType();
        return material == null ? "" : material.name().toLowerCase();
    }

    @HostAccess.Export
    public String joinMessage() {
        return event instanceof PlayerJoinEvent joinEvent ? Texts.toStringSafe(joinEvent.getJoinMessage()) : "";
    }

    @HostAccess.Export
    public void setJoinMessage(String message) {
        if (allowMutation && event instanceof PlayerJoinEvent joinEvent) {
            joinEvent.setJoinMessage(message);
        }
    }

    @HostAccess.Export
    public double damage() {
        return event instanceof EntityDamageByEntityEvent damageEvent ? damageEvent.getDamage() : 0D;
    }

    @HostAccess.Export
    public double finalDamage() {
        return event instanceof EntityDamageByEntityEvent damageEvent ? damageEvent.getFinalDamage() : 0D;
    }

    @HostAccess.Export
    public void setDamage(double damage) {
        if (allowMutation && event instanceof EntityDamageByEntityEvent damageEvent) {
            damageEvent.setDamage(Math.max(0D, damage));
        }
    }

    @HostAccess.Export
    public String cause() {
        return event instanceof EntityDamageByEntityEvent damageEvent && damageEvent.getCause() != null
                ? damageEvent.getCause().name()
                : "";
    }

    @HostAccess.Export
    public Map<String, Object> location() {
        Entity entity = null;
        if (event instanceof PlayerInteractEvent interactEvent) {
            entity = interactEvent.getPlayer();
        } else if (event instanceof PlayerJoinEvent joinEvent) {
            entity = joinEvent.getPlayer();
        } else if (event instanceof EntityDamageByEntityEvent damageEvent) {
            entity = damageEvent.getEntity();
        }
        return entity == null ? Map.of() : locationMap(entity.getLocation());
    }

    @HostAccess.Export
    public Object meta(String key) {
        return meta.get(key);
    }

    @HostAccess.Export
    public void setMeta(String key, Object value) {
        if (Texts.isBlank(key)) {
            return;
        }
        if (value == null) {
            meta.remove(key);
        } else {
            meta.put(key, value);
        }
    }

    @HostAccess.Export
    public Map<String, Object> meta() {
        return Map.copyOf(meta);
    }

    private Map<String, Object> locationMap(Location location) {
        if (location == null) {
            return Map.of();
        }
        return Map.of(
                "world", location.getWorld() == null ? "" : location.getWorld().getName(),
                "x", location.getX(),
                "y", location.getY(),
                "z", location.getZ(),
                "yaw", location.getYaw(),
                "pitch", location.getPitch()
        );
    }

    @SuppressWarnings("unused")
    private LivingEntity living(Entity entity) {
        return entity instanceof LivingEntity livingEntity ? livingEntity : null;
    }
}
