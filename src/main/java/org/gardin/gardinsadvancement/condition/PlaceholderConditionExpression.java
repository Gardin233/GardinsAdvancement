package org.gardin.gardinsadvancement.condition;

import org.bukkit.entity.Player;
import org.gardin.gardinsadvancement.hook.PlaceholderHook;

import java.util.List;
import java.util.Locale;

import static org.gardin.gardinsadvancement.condition.PlaceholderConditionToken.Type;

public class PlaceholderConditionExpression {
    private final String source;
    private final ExpressionNode root;

    private PlaceholderConditionExpression(String source, ExpressionNode root) {
        this.source = source;
        this.root = root;
    }

    public static PlaceholderConditionExpression compile(String source) {
        List<PlaceholderConditionToken> tokens = new PlaceholderConditionLexer(source).scanTokens();
        ExpressionNode root = new Parser(source, tokens).parse();
        return new PlaceholderConditionExpression(source, root);
    }

    public boolean test(Player player, PlaceholderHook hook) {
        return root.evaluate(player, hook).asBoolean();
    }

    public String getSource() {
        return source;
    }

    private interface ExpressionNode {
        ConditionValue evaluate(Player player, PlaceholderHook hook);
    }

    private record LiteralNode(ConditionValue value) implements ExpressionNode {
        @Override
        public ConditionValue evaluate(Player player, PlaceholderHook hook) {
            return value;
        }
    }

    private record PlaceholderNode(String placeholder) implements ExpressionNode {
        @Override
        public ConditionValue evaluate(Player player, PlaceholderHook hook) {
            return ConditionValue.of(hook.resolve(player, placeholder));
        }
    }

    private record UnaryNode(Type operator, ExpressionNode right) implements ExpressionNode {
        @Override
        public ConditionValue evaluate(Player player, PlaceholderHook hook) {
            ConditionValue value = right.evaluate(player, hook);
            if (operator == Type.NOT) {
                return ConditionValue.of(!value.asBoolean());
            }
            throw new IllegalStateException("不支持的单目运算符: " + operator);
        }
    }

    private record BinaryNode(Type operator, ExpressionNode left, ExpressionNode right) implements ExpressionNode {
        @Override
        public ConditionValue evaluate(Player player, PlaceholderHook hook) {
            return switch (operator) {
                case AND -> ConditionValue.of(
                        left.evaluate(player, hook).asBoolean() && right.evaluate(player, hook).asBoolean()
                );
                case OR -> ConditionValue.of(
                        left.evaluate(player, hook).asBoolean() || right.evaluate(player, hook).asBoolean()
                );
                case EQUALS -> ConditionValue.of(compare(left, right, player, hook) == 0);
                case NOT_EQUALS -> ConditionValue.of(compare(left, right, player, hook) != 0);
                case GREATER -> ConditionValue.of(compare(left, right, player, hook) > 0);
                case GREATER_EQUALS -> ConditionValue.of(compare(left, right, player, hook) >= 0);
                case LESS -> ConditionValue.of(compare(left, right, player, hook) < 0);
                case LESS_EQUALS -> ConditionValue.of(compare(left, right, player, hook) <= 0);
                default -> throw new IllegalStateException("不支持的双目运算符: " + operator);
            };
        }

        private int compare(ExpressionNode left, ExpressionNode right, Player player, PlaceholderHook hook) {
            ConditionValue leftValue = left.evaluate(player, hook);
            ConditionValue rightValue = right.evaluate(player, hook);

            Double leftNumber = leftValue.asNumber();
            Double rightNumber = rightValue.asNumber();
            if (leftNumber != null && rightNumber != null) {
                return Double.compare(leftNumber, rightNumber);
            }

            Boolean leftBoolean = leftValue.asStrictBoolean();
            Boolean rightBoolean = rightValue.asStrictBoolean();
            if (leftBoolean != null && rightBoolean != null) {
                return Boolean.compare(leftBoolean, rightBoolean);
            }

            return leftValue.asString().compareToIgnoreCase(rightValue.asString());
        }
    }

    private record ConditionValue(Object value) {
        static ConditionValue of(Object value) {
            return new ConditionValue(value);
        }

        boolean asBoolean() {
            if (value == null) {
                return false;
            }
            if (value instanceof Boolean booleanValue) {
                return booleanValue;
            }
            if (value instanceof Number number) {
                return number.doubleValue() != 0.0D;
            }
            String text = asString();
            if (text.isEmpty()) {
                return false;
            }
            String normalized = text.toLowerCase(Locale.ROOT);
            return !normalized.equals("false")
                    && !normalized.equals("0")
                    && !normalized.equals("null");
        }

        Double asNumber() {
            if (value instanceof Number number) {
                return number.doubleValue();
            }
            if (value == null) {
                return null;
            }
            try {
                return Double.parseDouble(asString());
            } catch (NumberFormatException exception) {
                return null;
            }
        }

        Boolean asStrictBoolean() {
            if (value instanceof Boolean booleanValue) {
                return booleanValue;
            }
            if (value == null) {
                return null;
            }
            String normalized = asString().toLowerCase(Locale.ROOT);
            return switch (normalized) {
                case "true" -> true;
                case "false" -> false;
                default -> null;
            };
        }

        String asString() {
            return value == null ? "" : String.valueOf(value).trim();
        }
    }

    private static class Parser {
        private final String source;
        private final List<PlaceholderConditionToken> tokens;
        private int current;

        private Parser(String source, List<PlaceholderConditionToken> tokens) {
            this.source = source;
            this.tokens = tokens;
        }

        private ExpressionNode parse() {
            ExpressionNode expression = parseOr();
            consume(Type.EOF, "表达式末尾存在无法解析的内容");
            return expression;
        }

        private ExpressionNode parseOr() {
            ExpressionNode expression = parseAnd();
            while (match(Type.OR)) {
                Type operator = previous().type();
                ExpressionNode right = parseAnd();
                expression = new BinaryNode(operator, expression, right);
            }
            return expression;
        }

        private ExpressionNode parseAnd() {
            ExpressionNode expression = parseUnary();
            while (match(Type.AND)) {
                Type operator = previous().type();
                ExpressionNode right = parseUnary();
                expression = new BinaryNode(operator, expression, right);
            }
            return expression;
        }

        private ExpressionNode parseUnary() {
            if (match(Type.NOT)) {
                return new UnaryNode(previous().type(), parseUnary());
            }
            return parseComparison();
        }

        private ExpressionNode parseComparison() {
            ExpressionNode expression = parsePrimary();
            if (match(
                    Type.EQUALS,
                    Type.NOT_EQUALS,
                    Type.GREATER,
                    Type.GREATER_EQUALS,
                    Type.LESS,
                    Type.LESS_EQUALS
            )) {
                Type operator = previous().type();
                ExpressionNode right = parsePrimary();
                expression = new BinaryNode(operator, expression, right);
            }
            return expression;
        }

        private ExpressionNode parsePrimary() {
            if (match(Type.LEFT_PAREN)) {
                ExpressionNode expression = parseOr();
                consume(Type.RIGHT_PAREN, "括号缺少 ')'");
                return expression;
            }
            if (match(Type.PLACEHOLDER)) {
                return new PlaceholderNode(previous().lexeme());
            }
            if (match(Type.STRING)) {
                return new LiteralNode(ConditionValue.of(previous().lexeme()));
            }
            if (match(Type.NUMBER)) {
                return new LiteralNode(ConditionValue.of(Double.parseDouble(previous().lexeme())));
            }
            if (match(Type.BOOLEAN)) {
                return new LiteralNode(ConditionValue.of(Boolean.parseBoolean(previous().lexeme())));
            }
            if (match(Type.NULL)) {
                return new LiteralNode(ConditionValue.of(null));
            }
            if (match(Type.IDENTIFIER)) {
                return new LiteralNode(ConditionValue.of(previous().lexeme()));
            }
            throw error(peek(), "无法解析的表达式片段");
        }

        private boolean match(Type... types) {
            for (Type type : types) {
                if (check(type)) {
                    advance();
                    return true;
                }
            }
            return false;
        }

        private PlaceholderConditionToken consume(Type type, String message) {
            if (check(type)) {
                return advance();
            }
            throw error(peek(), message);
        }

        private boolean check(Type type) {
            return peek().type() == type;
        }

        private PlaceholderConditionToken advance() {
            if (!isAtEnd()) {
                current++;
            }
            return previous();
        }

        private boolean isAtEnd() {
            return peek().type() == Type.EOF;
        }

        private PlaceholderConditionToken peek() {
            return tokens.get(current);
        }

        private PlaceholderConditionToken previous() {
            return tokens.get(current - 1);
        }

        private IllegalArgumentException error(PlaceholderConditionToken token, String message) {
            return new IllegalArgumentException(
                    message + "，位置 " + token.position() + "，表达式: " + source
            );
        }
    }
}
