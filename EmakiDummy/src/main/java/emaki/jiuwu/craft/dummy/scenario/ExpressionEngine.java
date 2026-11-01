package emaki.jiuwu.craft.dummy.scenario;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ExpressionEngine {

    public enum Kind {
        NUMBER,
        STRING,
        BOOLEAN
    }

    public static final class Value {
        private final Kind kind;
        private final double number;
        private final String text;
        private final boolean flag;

        private Value(Kind kind, double number, String text, boolean flag) {
            this.kind = kind;
            this.number = number;
            this.text = text;
            this.flag = flag;
        }

        public static Value ofNumber(double number) {
            return new Value(Kind.NUMBER, number, null, false);
        }

        public static Value ofString(String text) {
            return new Value(Kind.STRING, 0.0D, text, false);
        }

        public static Value ofBoolean(boolean flag) {
            return new Value(Kind.BOOLEAN, flag ? 1.0D : 0.0D, null, flag);
        }

        public Kind kind() {
            return kind;
        }

        public double number() {
            return number;
        }

        public String text() {
            return text;
        }

        public boolean flag() {
            return flag;
        }

        public boolean isTruthy() {
            return switch (kind) {
                case BOOLEAN -> flag;
                case NUMBER -> number != 0.0D;
                case STRING -> "true".equalsIgnoreCase(text);
            };
        }

        public String describe() {
            return switch (kind) {
                case BOOLEAN -> String.valueOf(flag);
                case NUMBER -> (number == Math.floor(number)
                        ? String.valueOf((long) number)
                        : String.valueOf(number));
                case STRING -> text;
            };
        }
    }

    private sealed interface Expr permits NumberExpr, StringExpr, BoolExpr, VarExpr, CallExpr,
            UnaryExpr, BinaryExpr {
    }

    private record NumberExpr(double value) implements Expr {
    }

    private record StringExpr(String value) implements Expr {
    }

    private record BoolExpr(boolean value) implements Expr {
    }

    private record VarExpr(String name) implements Expr {
    }

    private record CallExpr(String name, List<Expr> args) implements Expr {
    }

    private record UnaryExpr(String op, Expr operand) implements Expr {
    }

    private record BinaryExpr(String op, Expr left, Expr right) implements Expr {
    }

    private enum TokenType {
        NUMBER,
        STRING,
        IDENT,
        OPERATOR,
        END
    }

    private record Token(TokenType type, String text, int position) {
    }

    private ExpressionEngine() {
    }

    public static Value evaluate(String source, ExpressionContext context) throws ExpressionException {
        if (source == null || source.isBlank()) {
            throw new ExpressionException("empty expression", 0);
        }
        Parser parser = new Parser(source);
        Expr expression = parser.parseExpression();
        parser.expectEnd();
        return eval(expression, context);
    }

    private static Value eval(Expr expression, ExpressionContext context) throws ExpressionException {
        if (expression instanceof NumberExpr numberExpr) {
            return Value.ofNumber(numberExpr.value());
        }
        if (expression instanceof StringExpr stringExpr) {
            return Value.ofString(stringExpr.value());
        }
        if (expression instanceof BoolExpr boolExpr) {
            return Value.ofBoolean(boolExpr.value());
        }
        if (expression instanceof VarExpr varExpr) {
            return evalVariable(varExpr, context);
        }
        if (expression instanceof CallExpr callExpr) {
            return evalCall(callExpr, context);
        }
        if (expression instanceof UnaryExpr unaryExpr) {
            Value operand = eval(unaryExpr.operand(), context);
            if ("!".equals(unaryExpr.op())) {
                return Value.ofBoolean(!operand.isTruthy());
            }
            throw new ExpressionException("unsupported unary operator " + unaryExpr.op(), 0);
        }
        BinaryExpr binary = (BinaryExpr) expression;
        if ("&&".equals(binary.op())) {
            Value left = eval(binary.left(), context);
            if (!left.isTruthy()) {
                return Value.ofBoolean(false);
            }
            return Value.ofBoolean(eval(binary.right(), context).isTruthy());
        }
        if ("||".equals(binary.op())) {
            Value left = eval(binary.left(), context);
            if (left.isTruthy()) {
                return Value.ofBoolean(true);
            }
            return Value.ofBoolean(eval(binary.right(), context).isTruthy());
        }
        Value left = eval(binary.left(), context);
        Value right = eval(binary.right(), context);
        return evalBinary(binary.op(), left, right, 0);
    }

    private static Value evalVariable(VarExpr variable, ExpressionContext context)
            throws ExpressionException {
        return switch (variable.name()) {
            case "online_count" -> Value.ofNumber(context.onlineCount());
            case "true" -> Value.ofBoolean(true);
            case "false" -> Value.ofBoolean(false);
            default -> throw new ExpressionException("unknown variable: " + variable.name(), 0);
        };
    }

    private static Value evalCall(CallExpr call, ExpressionContext context) throws ExpressionException {
        switch (call.name()) {
            case "online_count" -> {
                requireArgs(call, 0);
                return Value.ofNumber(context.onlineCount());
            }
            case "dummy_online" -> {
                requireArgs(call, 1);
                return Value.ofNumber(context.dummyOnline(dummyArg(call, context)));
            }
            case "event_count" -> {
                requireArgs(call, 1);
                return Value.ofNumber(context.eventCount(textArg(call, context, 0)));
            }
            case "audit_contains" -> {
                requireArgs(call, 1);
                return Value.ofBoolean(context.auditContains(textArg(call, context, 0)));
            }
            case "recv_contains" -> {
                requireArgs(call, 2);
                return Value.ofBoolean(context.recvContains(
                        textArg(call, context, 0), textArg(call, context, 1)));
            }
            case "inventory_count" -> {
                requireArgs(call, 1);
                return Value.ofNumber(context.inventoryCount(textArg(call, context, 0)));
            }
            default -> throw new ExpressionException("unknown function: " + call.name(), 0);
        }
    }

    private static void requireArgs(CallExpr call, int expected) throws ExpressionException {
        if (call.args().size() != expected) {
            throw new ExpressionException("function " + call.name() + " expects " + expected
                    + " arguments", 0);
        }
    }

    private static String textArg(CallExpr call, ExpressionContext context, int index)
            throws ExpressionException {
        Value value = eval(call.args().get(index), context);
        if (value.kind() != Kind.STRING) {
            throw new ExpressionException("function " + call.name() + " argument " + (index + 1)
                    + " must be a string", 0);
        }
        return value.text();
    }

    private static String dummyArg(CallExpr call, ExpressionContext context) throws ExpressionException {
        return textArg(call, context, 0);
    }

    private static Value evalBinary(String op, Value left, Value right, int position)
            throws ExpressionException {
        if ("+".equals(op) || "-".equals(op) || "*".equals(op) || "/".equals(op)) {
            if (left.kind() != Kind.NUMBER || right.kind() != Kind.NUMBER) {
                throw new ExpressionException("operator " + op + " requires numbers", position);
            }
            double result = switch (op) {
                case "+" -> left.number() + right.number();
                case "-" -> left.number() - right.number();
                case "*" -> left.number() * right.number();
                default -> left.number() / right.number();
            };
            return Value.ofNumber(result);
        }
        return switch (op) {
            case "==" -> Value.ofBoolean(looseEquals(left, right));
            case "!=" -> Value.ofBoolean(!looseEquals(left, right));
            case "<", "<=", ">", ">=" -> Value.ofBoolean(compare(op, left, right, position));
            default -> throw new ExpressionException("unsupported operator " + op, position);
        };
    }

    private static boolean looseEquals(Value left, Value right) {
        if (left.kind() == Kind.NUMBER && right.kind() == Kind.NUMBER) {
            return Double.compare(left.number(), right.number()) == 0;
        }
        if (left.kind() == Kind.BOOLEAN || right.kind() == Kind.BOOLEAN) {
            return left.isTruthy() == right.isTruthy();
        }
        String leftText = left.kind() == Kind.NUMBER ? numberText(left.number()) : left.text();
        String rightText = right.kind() == Kind.NUMBER ? numberText(right.number()) : right.text();
        return leftText != null && leftText.equals(rightText);
    }

    private static boolean compare(String op, Value left, Value right, int position)
            throws ExpressionException {
        if (left.kind() != Kind.NUMBER || right.kind() != Kind.NUMBER) {
            throw new ExpressionException("operator " + op + " requires numbers", position);
        }
        double first = left.number();
        double second = right.number();
        return switch (op) {
            case "<" -> first < second;
            case "<=" -> first <= second;
            case ">" -> first > second;
            default -> first >= second;
        };
    }

    private static String numberText(double number) {
        return number == Math.floor(number) ? String.valueOf((long) number) : String.valueOf(number);
    }

    private static final class Parser {
        private final String source;
        private int index;
        private Token current;

        Parser(String source) throws ExpressionException {
            this.source = source;
            advance();
        }

        Expr parseExpression() throws ExpressionException {
            return parseOr();
        }

        void expectEnd() throws ExpressionException {
            if (current.type() != TokenType.END) {
                throw new ExpressionException("unexpected token " + current.text(), current.position());
            }
        }

        private Expr parseOr() throws ExpressionException {
            Expr left = parseAnd();
            while (isOperator("||")) {
                advance();
                left = new BinaryExpr("||", left, parseAnd());
            }
            return left;
        }

        private Expr parseAnd() throws ExpressionException {
            Expr left = parseComparison();
            while (isOperator("&&")) {
                advance();
                left = new BinaryExpr("&&", left, parseComparison());
            }
            return left;
        }

        private Expr parseComparison() throws ExpressionException {
            Expr left = parseAdditive();
            if (isOperator("==") || isOperator("!=") || isOperator("<") || isOperator("<=")
                    || isOperator(">") || isOperator(">=")) {
                String op = current.text();
                advance();
                left = new BinaryExpr(op, left, parseAdditive());
            }
            return left;
        }

        private Expr parseAdditive() throws ExpressionException {
            Expr left = parseMultiplicative();
            while (isOperator("+") || isOperator("-")) {
                String op = current.text();
                advance();
                left = new BinaryExpr(op, left, parseMultiplicative());
            }
            return left;
        }

        private Expr parseMultiplicative() throws ExpressionException {
            Expr left = parseUnary();
            while (isOperator("*") || isOperator("/")) {
                String op = current.text();
                advance();
                left = new BinaryExpr(op, left, parseUnary());
            }
            return left;
        }

        private Expr parseUnary() throws ExpressionException {
            if (isOperator("!")) {
                advance();
                return new UnaryExpr("!", parseUnary());
            }
            return parsePrimary();
        }

        private Expr parsePrimary() throws ExpressionException {
            Token token = current;
            if (token.type() == TokenType.NUMBER) {
                advance();
                return new NumberExpr(Double.parseDouble(token.text()));
            }
            if (token.type() == TokenType.STRING) {
                advance();
                return new StringExpr(token.text());
            }
            if (token.type() == TokenType.IDENT) {
                advance();
                if (current.type() == TokenType.OPERATOR && "(".equals(current.text())) {
                    advance();
                    List<Expr> args = new ArrayList<>();
                    if (!(current.type() == TokenType.OPERATOR && ")".equals(current.text()))) {
                        args.add(parseExpression());
                        while (current.type() == TokenType.OPERATOR && ",".equals(current.text())) {
                            advance();
                            args.add(parseExpression());
                        }
                    }
                    if (!(current.type() == TokenType.OPERATOR && ")".equals(current.text()))) {
                        throw new ExpressionException("expected )", current.position());
                    }
                    advance();
                    return new CallExpr(token.text(), List.copyOf(args));
                }
                return new VarExpr(token.text());
            }
            if (token.type() == TokenType.OPERATOR && "(".equals(token.text())) {
                advance();
                Expr inner = parseExpression();
                if (!(current.type() == TokenType.OPERATOR && ")".equals(current.text()))) {
                    throw new ExpressionException("expected )", current.position());
                }
                advance();
                return inner;
            }
            throw new ExpressionException("unexpected token " + token.text(), token.position());
        }

        private boolean isOperator(String op) {
            return current.type() == TokenType.OPERATOR && current.text().equals(op);
        }

        private void advance() throws ExpressionException {
            current = nextToken();
        }

        private Token nextToken() throws ExpressionException {
            while (index < source.length() && Character.isWhitespace(source.charAt(index))) {
                index++;
            }
            if (index >= source.length()) {
                return new Token(TokenType.END, "<end>", index);
            }
            int start = index;
            char ch = source.charAt(index);
            if (Character.isDigit(ch) || (ch == '.' && index + 1 < source.length()
                    && Character.isDigit(source.charAt(index + 1)))) {
                boolean seenDot = false;
                while (index < source.length()) {
                    char digit = source.charAt(index);
                    if (digit == '.' && !seenDot) {
                        seenDot = true;
                        index++;
                        continue;
                    }
                    if (!Character.isDigit(digit)) {
                        break;
                    }
                    index++;
                }
                return new Token(TokenType.NUMBER, source.substring(start, index), start);
            }
            if (ch == '\'' || ch == '"') {
                char quote = ch;
                index++;
                int valueStart = index;
                while (index < source.length() && source.charAt(index) != quote) {
                    index++;
                }
                if (index >= source.length()) {
                    throw new ExpressionException("unterminated string", start);
                }
                String value = source.substring(valueStart, index);
                index++;
                return new Token(TokenType.STRING, value, start);
            }
            if (Character.isLetter(ch) || ch == '_') {
                while (index < source.length()) {
                    char letter = source.charAt(index);
                    if (!Character.isLetterOrDigit(letter) && letter != '_') {
                        break;
                    }
                    index++;
                }
                return new Token(TokenType.IDENT, source.substring(start, index), start);
            }
            String twoChar = index + 1 < source.length()
                    ? source.substring(index, index + 2)
                    : String.valueOf(ch);
            String matched = switch (twoChar) {
                case "==", "!=", "<=", ">=", "&&", "||" -> twoChar;
                default -> null;
            };
            if (matched != null) {
                index += 2;
                return new Token(TokenType.OPERATOR, matched, start);
            }
            if ("<>!+-*/(),:".indexOf(ch) >= 0) {
                index++;
                return new Token(TokenType.OPERATOR, String.valueOf(ch), start);
            }
            throw new ExpressionException("invalid character " + ch, start);
        }
    }
}
