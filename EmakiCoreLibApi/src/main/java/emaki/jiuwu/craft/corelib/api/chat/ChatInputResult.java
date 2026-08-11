package emaki.jiuwu.craft.corelib.api.chat;

/**
 * 一次聊天输入等待的最终结果。每次 {@link ChatInputRequest} 的回调只会收到一个结果。
 *
 * @param status 结束方式
 * @param text   玩家提交的纯文本；非 {@link Status#SUBMITTED} 时为空字符串
 */
public record ChatInputResult(Status status, String text) {

    public enum Status {

        /** 玩家在聊天框输入了内容。 */
        SUBMITTED,

        /** 输入被主动撤销：命中取消词、调用方撤销，或服务关闭。 */
        CANCELLED,

        /** 超时未输入。 */
        TIMEOUT,

        /** 玩家退出或被踢出。 */
        QUIT
    }

    public ChatInputResult(Status status, String text) {
        this.status = status == null ? Status.CANCELLED : status;
        this.text = text == null ? "" : text;
    }

    public static ChatInputResult submitted(String text) {
        return new ChatInputResult(Status.SUBMITTED, text);
    }

    public static ChatInputResult cancelled() {
        return new ChatInputResult(Status.CANCELLED, "");
    }

    public static ChatInputResult timeout() {
        return new ChatInputResult(Status.TIMEOUT, "");
    }

    public static ChatInputResult quit() {
        return new ChatInputResult(Status.QUIT, "");
    }

    public boolean submitted() {
        return status == Status.SUBMITTED;
    }
}
