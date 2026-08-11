package emaki.jiuwu.craft.cooking.service.display;

import java.util.Objects;

import org.bukkit.Location;

import emaki.jiuwu.craft.cooking.model.StationCoordinates;
import emaki.jiuwu.craft.cooking.model.StationType;
import emaki.jiuwu.craft.cooking.service.CookingSettingsService;
import emaki.jiuwu.craft.corelib.display.DisplayGeometry;
import emaki.jiuwu.craft.corelib.display.TextDisplaySpec;
import emaki.jiuwu.craft.corelib.api.text.Texts;

/**
 * 工位文本展示的入参。
 *
 * <p>保留工位语义供调用方使用，通过 {@link #toCoreSpec()} 转成 CoreLib 的通用 spec。
 * 工位文本是常驻显示，因此存活时长恒为 0，可见性也不做定向。
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

    /** {@return 转换为 CoreLib 通用 spec} */
    public TextDisplaySpec toCoreSpec() {
        return new TextDisplaySpec(
                CookingDisplayKeys.of(stationType, stationCoordinates, displayKey),
                text,
                baseLocation,
                toCoreProfile(profile)
        );
    }

    static DisplayGeometry.TextProfile toCoreProfile(CookingSettingsService.TextDisplayProfile profile) {
        if (profile == null) {
            return DisplayGeometry.TextProfile.defaults();
        }
        return new DisplayGeometry.TextProfile(
                toCoreVector(profile.offset()),
                toCoreVector(profile.scale()),
                profile.billboard(),
                profile.lineWidth(),
                profile.backgroundArgb(),
                profile.shadow(),
                profile.seeThrough()
        );
    }

    static DisplayGeometry.Vector3 toCoreVector(CookingSettingsService.Vector3 vector) {
        return vector == null
                ? DisplayGeometry.Vector3.ZERO
                : new DisplayGeometry.Vector3(vector.x(), vector.y(), vector.z());
    }

    public boolean hasText() {
        return Texts.isNotBlank(text);
    }
}
