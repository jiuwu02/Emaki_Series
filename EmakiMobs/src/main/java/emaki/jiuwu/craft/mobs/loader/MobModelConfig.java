package emaki.jiuwu.craft.mobs.loader;

import emaki.jiuwu.craft.corelib.api.animation.AnimationKeyframe;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

public record MobModelConfig(
        String blueprint,
        @Nullable String api,
        double scale,
        Map<String, String> animations,
        Map<String, KeyframeTimeline> keyframes,
        @Nullable LodBounds lod
) {

    public record KeyframeTimeline(
            int durationTicks,
            boolean loop,
            String priority,
            String conflict,
            List<AnimationKeyframe> frames
    ) {
        public KeyframeTimeline {
            frames = frames == null ? List.of() : List.copyOf(frames);
        }
    }

    public record LodBounds(double near, double mid, double far) {
    }
}
