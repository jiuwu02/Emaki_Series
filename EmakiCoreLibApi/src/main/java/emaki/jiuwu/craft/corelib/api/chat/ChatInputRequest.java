package emaki.jiuwu.craft.corelib.api.chat;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import emaki.jiuwu.craft.corelib.api.text.Texts;

/**
 * 一次聊天输入等待的注册请求。
 *
 * <p>提示语由调用方自行发送，本请求不携带任何语言键：CoreLib 只提供机制，文案归业务模块。</p>
 *
 * @param owner          调度归属插件，用于超时任务与回调调度
 * @param player         等待输入的玩家
 * @param timeoutSeconds 超时秒数；{@code 0} 表示不超时，负值按 {@code 0} 处理
 * @param cancelKeywords 取消词，玩家输入命中任一取消词时以 {@link ChatInputResult.Status#CANCELLED} 结束
 * @param callback       结果回调，恰好执行一次。正常路径调度回玩家的 entity owner 线程；
 *                       若该线程已不可调度（玩家退出/被踢、插件停用），则就地执行，
 *                       此时回调不应访问 Bukkit 状态
 */
public record ChatInputRequest(
        Plugin owner,
        Player player,
        long timeoutSeconds,
        List<String> cancelKeywords,
        Consumer<ChatInputResult> callback
) {

    public ChatInputRequest(Plugin owner,
            Player player,
            long timeoutSeconds,
            List<String> cancelKeywords,
            Consumer<ChatInputResult> callback) {
        this.owner = Objects.requireNonNull(owner, "owner");
        this.player = Objects.requireNonNull(player, "player");
        this.timeoutSeconds = Math.max(0L, timeoutSeconds);
        this.cancelKeywords = normalizeKeywords(cancelKeywords);
        this.callback = Objects.requireNonNull(callback, "callback");
    }

    /** 无取消词、不超时的最小请求。 */
    public static ChatInputRequest of(Plugin owner, Player player, Consumer<ChatInputResult> callback) {
        return new ChatInputRequest(owner, player, 0L, List.of(), callback);
    }

    /** 判断输入是否命中取消词，比较使用 {@link java.util.Locale#ROOT} 小写化。 */
    public boolean isCancelKeyword(String input) {
        if (cancelKeywords.isEmpty()) {
            return false;
        }
        return cancelKeywords.contains(Texts.lower(Texts.trim(input)));
    }

    private static List<String> normalizeKeywords(List<String> keywords) {
        if (keywords == null || keywords.isEmpty()) {
            return List.of();
        }
        return keywords.stream()
                .filter(Texts::isNotBlank)
                .map(keyword -> Texts.lower(Texts.trim(keyword)))
                .distinct()
                .toList();
    }
}
