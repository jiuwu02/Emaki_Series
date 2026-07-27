package emaki.jiuwu.craft.corelib.packet;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 虚拟实体 id 的进程级分配器。
 *
 * <p>展示实体的发包后端可能存在多个实例（CoreLib 默认实例、各模块自建实例），
 * 若每个实例各自计数，不同实例会分配到相同的 entity id，导致客户端上一个实体的
 * 元数据覆盖另一个。因此计数器必须是进程唯一的静态状态。
 *
 * <p>起始值取一个远离真实实体 id 的高位区间，避免与服务端实体冲突。
 */
public final class VirtualEntityIds {

    private static final AtomicInteger NEXT_ID = new AtomicInteger(1_000_000_000);

    private VirtualEntityIds() {
    }

    /** {@return 下一个可用的虚拟实体 id} */
    public static int next() {
        return NEXT_ID.getAndIncrement();
    }
}
