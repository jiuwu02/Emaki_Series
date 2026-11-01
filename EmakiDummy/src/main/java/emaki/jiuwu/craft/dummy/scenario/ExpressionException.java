package emaki.jiuwu.craft.dummy.scenario;

public final class ExpressionException extends Exception {

    private final int position;

    public ExpressionException(String message, int position) {
        super(message + " (position " + position + ")");
        this.position = position;
    }

    public int position() {
        return position;
    }
}
