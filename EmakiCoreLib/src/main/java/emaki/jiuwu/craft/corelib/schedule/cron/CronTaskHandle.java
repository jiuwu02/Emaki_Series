package emaki.jiuwu.craft.corelib.schedule.cron;

public interface CronTaskHandle {

    void cancel();

    boolean isCancelled();
}
