package emaki.jiuwu.craft.station.dismantle;

import java.util.List;
import java.util.Set;

import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import emaki.jiuwu.craft.corelib.condition.ConditionBlock;
import emaki.jiuwu.craft.corelib.matcher.ItemRequirement;
import emaki.jiuwu.craft.corelib.matcher.MatchContext;
import emaki.jiuwu.craft.corelib.matcher.Matcher;

public record DismantleRecipeDefinition(
        String id,
        String displayName,
        String stationId,
        Set<String> tags,
        ItemRequirement inputRequirement,
        RollsRange rolls,
        List<DismantlePoolEntry> pool,
        String permission,
        ConditionBlock condition) {

    public DismantleRecipeDefinition {
        if (id == null) {
            throw new NullPointerException("id");
        }
        if (inputRequirement == null) {
            throw new NullPointerException("inputRequirement");
        }
        if (rolls == null) {
            throw new NullPointerException("rolls");
        }
        if (pool == null) {
            throw new NullPointerException("pool");
        }
        displayName = displayName == null ? id : displayName;
        stationId = stationId == null ? "" : stationId;
        tags = tags == null ? Set.of() : Set.copyOf(tags);
        pool = List.copyOf(pool);
        permission = permission == null ? "" : permission;
        condition = condition == null ? ConditionBlock.empty() : condition;
    }

    public DismantleRecipeDefinition(String id,
            String displayName,
            String stationId,
            Set<String> tags,
            ItemSourceRef inputSource,
            RollsRange rolls,
            List<DismantlePoolEntry> pool,
            String permission,
            ConditionBlock condition) {
        this(id, displayName, stationId, tags, sourceRequirement(inputSource), rolls, pool, permission, condition);
    }

    public DismantleRecipeDefinition(String id,
            String displayName,
            String stationId,
            Set<String> tags,
            ItemSourceRef inputSource,
            RollsRange rolls,
            List<DismantlePoolEntry> pool,
            String permission,
            ConditionBlock condition,
            Matcher matcher) {
        this(id, displayName, stationId, tags, requirement(inputSource, matcher), rolls, pool, permission, condition);
    }

    public ItemSourceRef inputSource() {
        return inputRequirement.sources().isEmpty() ? null : inputRequirement.sources().getFirst();
    }

    public List<ItemSourceRef> inputSources() {
        return inputRequirement.sources();
    }

    public Matcher matcher() {
        return inputRequirement.matcher();
    }

    public boolean hasPermission() {
        return !permission.isBlank();
    }

    public boolean hasScopedStation() {
        return !stationId.isBlank();
    }

    public boolean hasMatcher() {
        return inputRequirement.declaresMatcher();
    }

    public boolean acceptsInput(MatchContext context) {
        return inputRequirement.test(context);
    }

    private static ItemRequirement sourceRequirement(ItemSourceRef inputSource) {
        return requirement(inputSource, null);
    }

    private static ItemRequirement requirement(ItemSourceRef inputSource, Matcher matcher) {
        if (inputSource == null) {
            throw new NullPointerException("inputSource");
        }
        List<ItemSourceRef> sources = List.of(inputSource);
        return new ItemRequirement(sources, matcher, ItemRequirement.sourceIdentity(sources));
    }
}
