package emaki.jiuwu.craft.corelib.display;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import emaki.jiuwu.craft.corelib.api.text.MiniMessages;
import emaki.jiuwu.craft.corelib.api.text.Texts;

import net.kyori.adventure.text.Component;

public record TextDisplaySpec(DisplayKey key,
        String text,
        Location baseLocation,
        DisplayGeometry.TextProfile profile,
        int lifetimeTicks,
        Set<UUID> viewers,
        DisplayMotion motion) {

    public TextDisplaySpec {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(baseLocation, "baseLocation");
        text = Texts.toStringSafe(text);
        profile = profile == null ? DisplayGeometry.TextProfile.defaults() : profile;
        baseLocation = baseLocation.clone();
        lifetimeTicks = Math.max(0, lifetimeTicks);
        viewers = viewers == null || viewers.isEmpty() ? Set.of() : Set.copyOf(viewers);
        motion = motion == null ? DisplayMotion.NONE : motion;
    }

    public TextDisplaySpec(DisplayKey key,
            String text,
            Location baseLocation,
            DisplayGeometry.TextProfile profile) {
        this(key, text, baseLocation, profile, 0, Set.of(), DisplayMotion.NONE);
    }

    public TextDisplaySpec(DisplayKey key,
            String text,
            Location baseLocation,
            DisplayGeometry.TextProfile profile,
            int lifetimeTicks,
            Set<UUID> viewers) {
        this(key, text, baseLocation, profile, lifetimeTicks, viewers, DisplayMotion.NONE);
    }

    public String groupKey() {
        return key.groupKey();
    }

    public String runtimeKey() {
        return key.runtimeKey();
    }

    public boolean hasText() {
        return Texts.isNotBlank(text);
    }

    public boolean hasLifetime() {
        return lifetimeTicks > 0;
    }

    public boolean isTargeted() {
        return !viewers.isEmpty();
    }

    public Location displayLocation() {
        return new Location(
                baseLocation.getWorld(),
                baseLocation.getX() + profile.offset().x(),
                baseLocation.getY() + profile.offset().y(),
                baseLocation.getZ() + profile.offset().z()
        );
    }

    public Component component() {
        return MiniMessages.parse(text);
    }

    public Object componentObject() {
        return component();
    }

    public Transformation transformation() {
        return profile.transformation();
    }

    public boolean hasMotion() {
        return motion.isActive();
    }

    public Transformation transformation(DisplayGeometry.Vector3 translation, double scaleFactor) {
        DisplayGeometry.Vector3 offset = translation == null ? DisplayGeometry.Vector3.ZERO : translation;
        DisplayGeometry.Vector3 scale = profile.scale();
        return new Transformation(
                new Vector3f((float) offset.x(), (float) offset.y(), (float) offset.z()),
                new Quaternionf(),
                new Vector3f(
                        (float) (scale.x() * scaleFactor),
                        (float) (scale.y() * scaleFactor),
                        (float) (scale.z() * scaleFactor)
                ),
                new Quaternionf()
        );
    }
}
