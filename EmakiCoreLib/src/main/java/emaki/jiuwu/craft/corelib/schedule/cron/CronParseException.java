package emaki.jiuwu.craft.corelib.schedule.cron;

/**
 * 解析 Cron 表达式失败时抛出的运行时异常。
 */
public class CronParseException extends RuntimeException {

    public CronParseException(String message) {
        super(message);
    }

    public CronParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
