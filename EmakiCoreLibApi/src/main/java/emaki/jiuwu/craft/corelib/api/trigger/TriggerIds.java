package emaki.jiuwu.craft.corelib.api.trigger;

/**
 * 标准触发器 ID 的跨模块常量面。
 *
 * <p>这些字符串是配置契约：管理员在技能与物品的 YAML 中按同一套 ID 声明触发条件，
 * 因此它们属于对外发布契约而非实现细节，放在 api 模块供各模块共享。
 *
 * <h2>为什么常量与注册表分离</h2>
 *
 * 触发器的**分发机制**（注册表、派发器、冲突仲裁）留在 CoreLib runtime，
 * 只有 Skills 消费；而**ID 常量**被 Item 与 Skills 共同引用。
 * 把常量提到 api 后，只引用 ID 的模块不必依赖分发机制的实现类型。
 *
 * <h2>不做什么</h2>
 *
 * 本类只声明 ID 字面量，**不承载启用状态、显示名或分类**。
 * 那些是 runtime 注册表按配置解析出的运行期数据，不是编译期常量。
 */
public final class TriggerIds {

    /** 左键点击。 */
    public static final String LEFT_CLICK = "left_click";

    /** 右键点击。 */
    public static final String RIGHT_CLICK = "right_click";

    /** Shift + 左键点击。 */
    public static final String SHIFT_LEFT_CLICK = "shift_left_click";

    /** Shift + 右键点击。 */
    public static final String SHIFT_RIGHT_CLICK = "shift_right_click";

    /** 按 Q 丢弃。 */
    public static final String DROP_Q = "drop_q";

    /** 发起攻击。 */
    public static final String ATTACK = "attack";

    /** 受到伤害。 */
    public static final String DAMAGED = "damaged";

    /** 受到实体伤害。 */
    public static final String DAMAGED_BY_ENTITY = "damaged_by_entity";

    /** 死亡。 */
    public static final String DEATH = "death";

    /** 击杀实体。 */
    public static final String KILL_ENTITY = "kill_entity";

    /** 击杀玩家。 */
    public static final String KILL_PLAYER = "kill_player";

    /** 射出弓箭。 */
    public static final String SHOOT_BOW = "shoot_bow";

    /** 箭命中目标。 */
    public static final String ARROW_HIT = "arrow_hit";

    /** 箭落地。 */
    public static final String ARROW_LAND = "arrow_land";

    /** 投出三叉戟。 */
    public static final String SHOOT_TRIDENT = "shoot_trident";

    /** 三叉戟命中目标。 */
    public static final String TRIDENT_HIT = "trident_hit";

    /** 三叉戟落地。 */
    public static final String TRIDENT_LAND = "trident_land";

    /** 破坏方块。 */
    public static final String BREAK_BLOCK = "break_block";

    /** 放置方块。 */
    public static final String PLACE_BLOCK = "place_block";

    /** 丢弃物品。 */
    public static final String DROP_ITEM = "drop_item";

    /** Shift + 丢弃物品。 */
    public static final String SHIFT_DROP_ITEM = "shift_drop_item";

    /** 主副手换手。 */
    public static final String SWAP_ITEMS = "swap_items";

    /** Shift + 主副手换手。 */
    public static final String SHIFT_SWAP_ITEMS = "shift_swap_items";

    /** 登录。 */
    public static final String LOGIN = "login";

    /** 潜行。 */
    public static final String SNEAK = "sneak";

    /** 传送。 */
    public static final String TELEPORT = "teleport";

    /** 周期计时。 */
    public static final String TIMER = "timer";

    /** 连击。 */
    public static final String COMBO_ATTACK = "combo_attack";

    private TriggerIds() {
    }
}
