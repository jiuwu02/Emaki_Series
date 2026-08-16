package emaki.jiuwu.craft.corelib.schedule.cron;

/**
 * Cron 任务句柄，用于取消已注册的 Cron 调度任务。
 */
public interface CronTaskHandle {

    /**
     * 取消该 Cron 任务。已执行完毕或已取消的任务再次调用此方法无副作用。
     */
    void cancel();

    /**
     * 返回该任务是否已被取消或自然结束（maxExecutions 耗尽）。
     */
    boolean isCancelled();
}
