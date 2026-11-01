package emaki.jiuwu.craft.dummy.log;

public enum LogLevel {
    ACTION("ACTION"),
    RECV("RECV"),
    EVENT("EVENT"),
    SCENARIO("SCENARIO"),
    ASSERT("ASSERT"),
    ERROR("ERROR");

    private final String token;

    LogLevel(String token) {
        this.token = token;
    }

    public String token() {
        return token;
    }
}
