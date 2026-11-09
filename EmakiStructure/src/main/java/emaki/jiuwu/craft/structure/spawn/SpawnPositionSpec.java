package emaki.jiuwu.craft.structure.spawn;

import java.util.List;
import java.util.Map;

public record SpawnPositionSpec(
        Map<?, ?> raw,
        int index,
        String type,
        int sizeX,
        int sizeY,
        int sizeZ,
        int count,
        double chance,
        String facing,
        Integer level,
        List<SpawnSidecarParser.Issue> issues) {
}
