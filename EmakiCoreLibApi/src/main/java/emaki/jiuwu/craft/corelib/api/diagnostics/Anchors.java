package emaki.jiuwu.craft.corelib.api.diagnostics;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 诊断锚点的统一字段名与格式化。
 *
 * <p>W6-2a 要求线程与持久化两类锚点使用**统一字段**，以便实机日志可按同一套 key 检索与关联。
 * 此前各模块自行拼字符串（如 {@code operationId=...}），字段名与顺序不一致，跨模块比对困难。
 *
 * <h2>为什么只做格式化，不做输出</h2>
 *
 * 本类**不写日志、不持状态、不判断开关**。是否输出由调用方的 {@code DebugLogger} 决定
 * （模块级 + 玩家级开关，**默认关闭**）。这样锚点字段可以进 api 契约供各模块共享，
 * 而运行期的输出控制仍留在 CoreLib 实现侧 —— 与 M2-2 的边界一致。
 *
 * <h2>字段约定</h2>
 *
 * 输出形如 {@code op=a1b2 gen=3 phase=charged thread=owner elapsed=12ms}，
 * 键固定用本类常量，顺序按插入序稳定输出，便于 grep 与 diff。
 * 值为 {@code null} 或空白的字段**整项省略**，避免出现 {@code cause=null} 这种噪声。
 */
public final class Anchors {

    /** 操作关联 id，与业务事件的 operationId 同值，用于串联一次操作的全部锚点。 */
    public static final String OP = "op";

    /** 持久化世代号，用于识别迟到回写：世代不匹配即应拒绝。 */
    public static final String GENERATION = "gen";

    /** 状态迁移的目标阶段，如 {@code begin} / {@code charged} / {@code committed}。 */
    public static final String PHASE = "phase";

    /** 线程归属判定结果，取 {@link #THREAD_OWNER} 等常量。 */
    public static final String THREAD = "thread";

    /** 调度类别，如 {@code global} / {@code entity} / {@code location} / {@code async}。 */
    public static final String LANE = "lane";

    /** 耗时，毫秒。 */
    public static final String ELAPSED_MS = "elapsed";

    /** 失败原因；成功时应省略而非写空串。 */
    public static final String CAUSE = "cause";

    /** 受影响条目数，如 drain 汇总的任务数。 */
    public static final String COUNT = "count";

    /** {@link #THREAD} 的取值：当前线程持有该实体/区域的所有权。 */
    public static final String THREAD_OWNER = "owner";

    /** {@link #THREAD} 的取值：当前线程不持有所有权，操作应改为调度提交。 */
    public static final String THREAD_FOREIGN = "foreign";

    /** {@link #PHASE} 的取值：调度已提交但尚未执行。 */
    public static final String PHASE_SUBMITTED = "submitted";

    /** {@link #PHASE} 的取值：future 已完成。 */
    public static final String PHASE_COMPLETED = "completed";

    /** {@link #PHASE} 的取值：迟到任务被拒绝，未产生副作用。 */
    public static final String PHASE_REJECTED_LATE = "rejected_late";

    private Anchors() {
    }

    /**
     * {@return a builder collecting anchor fields in insertion order}
     */
    public static Builder of() {
        return new Builder();
    }

    /** 按插入序累积锚点字段，{@link #render()} 产出稳定字符串。 */
    public static final class Builder {

        private final Map<String, String> fields = new LinkedHashMap<>();

        private Builder() {
        }

        /**
         * 记录一个锚点字段；值为 {@code null} 或空白时**整项跳过**。
         *
         * @param key   字段名，应取本类常量
         * @param value 字段值
         * @return this
         */
        public Builder put(String key, Object value) {
            if (key == null || key.isBlank() || value == null) {
                return this;
            }
            String text = String.valueOf(value);
            if (text.isBlank()) {
                return this;
            }
            fields.put(key, text);
            return this;
        }

        /**
         * {@return this builder with the op field set}
         *
         * @param operationId operation identity
         */
        public Builder op(Object operationId) {
            return put(OP, operationId);
        }

        /**
         * {@return this builder with the generation field set}
         *
         * @param generation generation stamp
         */
        public Builder generation(Object generation) {
            return put(GENERATION, generation);
        }

        /**
         * {@return this builder with the phase field set}
         *
         * @param phase state-transition phase
         */
        public Builder phase(String phase) {
            return put(PHASE, phase);
        }

        /**
         * {@return this builder with the thread-ownership field set}
         *
         * @param owned whether the owner thread holds it
         */
        public Builder thread(boolean owned) {
            return put(THREAD, owned ? THREAD_OWNER : THREAD_FOREIGN);
        }

        /**
         * {@return this builder with the scheduling lane field set}
         *
         * @param lane scheduling lane name
         */
        public Builder lane(String lane) {
            return put(LANE, lane);
        }

        /**
         * {@return this builder with the elapsed-millis field set}
         *
         * @param millis elapsed milliseconds
         */
        public Builder elapsedMs(long millis) {
            return put(ELAPSED_MS, millis + "ms");
        }

        /**
         * {@return this builder with the failure cause field set}
         *
         * @param cause failure cause
         */
        public Builder cause(Object cause) {
            return put(CAUSE, cause);
        }

        /**
         * {@return this builder with the affected-count field set}
         *
         * @param count affected entry count
         */
        public Builder count(int count) {
            return put(COUNT, count);
        }

        /**
         * {@return the anchor fields as {@code key=value} pairs joined by single spaces}
         *
         * 空 builder 返回空串，调用方可据此跳过输出。
         */
        public String render() {
            if (fields.isEmpty()) {
                return "";
            }
            StringBuilder sb = new StringBuilder(fields.size() * 16);
            for (Map.Entry<String, String> entry : fields.entrySet()) {
                if (sb.length() > 0) {
                    sb.append(' ');
                }
                sb.append(entry.getKey()).append('=').append(entry.getValue());
            }
            return sb.toString();
        }

        @Override
        public String toString() {
            return render();
        }
    }
}
