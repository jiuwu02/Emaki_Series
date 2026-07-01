package emaki.jiuwu.craft.cooking.model;

/**
 * 工位运行态只读快照，统一承载占位符可读字段。
 *
 * <p>不适用的字段统一留空串或 0：例如无热源工位的 {@code heatBlockId} 为空、
 * {@code burning} 为 {@code false}；无流体工位的 {@code fluidName} 为空。
 * 所有字符串字段经规范化构造器保证非 {@code null}，便于占位符直接输出。</p>
 *
 * @param stationType            工位类型
 * @param worldName              所在世界名
 * @param x                      工作方块 X
 * @param y                      工作方块 Y
 * @param z                      工作方块 Z
 * @param stationBlockId         工作方块 id（自定义方块 id 或原版 Material 名）
 * @param heatBlockId            热源方块 id（工作方块下方；无热源则为空串）
 * @param burning                热源是否正在燃烧
 * @param burningRemainingSeconds 热源剩余燃烧秒数
 * @param heat                   热度（烤炉）/火力等级（炒锅）
 * @param moisture               水分（蒸锅）
 * @param steam                  蒸汽（蒸锅）
 * @param inputItemName          主输入物品纯文本名
 * @param inputItemSource        主输入物品 shorthand
 * @param inputAmount            主输入物品数量
 * @param ingredientCount        输入物品种类数
 * @param recipeId               当前匹配/进行中的配方 id
 * @param recipeName             配方显示名
 * @param progressCurrent        进度当前值
 * @param progressTarget         进度目标值
 * @param progressPercent        进度百分比（0~100）
 * @param completed              是否已完成（可收取）
 * @param fluidName              流体名（榨汁机）
 * @param fluidAmountMl          流体毫升量（榨汁机）
 * @param playerName             操作/查看玩家名
 */
public record StationSnapshot(
        StationType stationType,
        String worldName,
        int x,
        int y,
        int z,
        String stationBlockId,
        String heatBlockId,
        boolean burning,
        long burningRemainingSeconds,
        int heat,
        int moisture,
        int steam,
        String inputItemName,
        String inputItemSource,
        int inputAmount,
        int ingredientCount,
        String recipeId,
        String recipeName,
        int progressCurrent,
        int progressTarget,
        double progressPercent,
        boolean completed,
        String fluidName,
        int fluidAmountMl,
        String playerName) {

    public StationSnapshot {
        worldName = safe(worldName);
        stationBlockId = safe(stationBlockId);
        heatBlockId = safe(heatBlockId);
        inputItemName = safe(inputItemName);
        inputItemSource = safe(inputItemSource);
        recipeId = safe(recipeId);
        recipeName = safe(recipeName);
        fluidName = safe(fluidName);
        playerName = safe(playerName);
        burningRemainingSeconds = Math.max(0L, burningRemainingSeconds);
        if (Double.isNaN(progressPercent) || Double.isInfinite(progressPercent)) {
            progressPercent = 0.0D;
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
