package emaki.jiuwu.craft.cooking.service.display;

import java.util.Objects;

import emaki.jiuwu.craft.cooking.model.StationCoordinates;
import emaki.jiuwu.craft.cooking.model.StationType;
import emaki.jiuwu.craft.cooking.service.CookingSettingsService;
import emaki.jiuwu.craft.corelib.text.MiniMessages;
import emaki.jiuwu.craft.corelib.text.Texts;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.bukkit.util.Transformation;

/**
 * 一个文本展示实体的渲染描述。文本以 MiniMessage 字符串携带，由各后端按需解析为
 * {@link net.kyori.adventure.text.Component}（PacketEvents）或 legacy 字符串（Bukkit）。
 */
public record CookingTextDisplaySpec(StationType stationType,
        StationCoordinates stationCoordinates,
        String displayKey,
        String text,
        Location baseLocation,
        CookingSettingsService.TextDisplayProfile profile) {

    public CookingTextDisplaySpec {
        Objects.requireNonNull(stationType, "stationType");
        Objects.requireNonNull(stationCoordinates, "stationCoordinates");
        Objects.requireNonNull(displayKey, "displayKey");
        Objects.requireNonNull(baseLocation, "baseLocation");
        Objects.requireNonNull(profile, "profile");
        text = Texts.toStringSafe(text);
        baseLocation = baseLocation.clone();
    }

    public String stationRuntimeKey() {
        return stationType.folderName() + ":" + stationCoordinates.runtimeKey();
    }

    public String runtimeKey() {
        return stationRuntimeKey() + ":" + displayKey;
    }

    public boolean hasText() {
        return Texts.isNotBlank(text);
    }

    /**
     * 文本实体的世界坐标 = 基准坐标 + profile.offset。
     */
    public Location displayLocation() {
        return new Location(
                baseLocation.getWorld(),
                baseLocation.getX() + profile.offset().x(),
                baseLocation.getY() + profile.offset().y(),
                baseLocation.getZ() + profile.offset().z()
        );
    }

    /**
     * 解析后的 Adventure 文本组件。
     */
    public Component component() {
        return MiniMessages.parse(text);
    }

    /**
     * 缩放变换（文本居中显示，旋转交由 billboard 处理，故旋转保持单位四元数）。
     */
    public Transformation transformation() {
        CookingSettingsService.Vector3 scale = profile.scale();
        return new Transformation(
                new Vector3f(),
                new Quaternionf(),
                new Vector3f((float) scale.x(), (float) scale.y(), (float) scale.z()),
                new Quaternionf()
        );
    }
}
