package org.gardin.gardinsadvancement.condition;

import org.bukkit.entity.Player;
import org.gardin.gardinsadvancement.hook.PlaceholderHook;
import org.gardin.gardinsadvancement.util.Lang;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.gardin.gardinsadvancement.condition.PlaceholderConditionToken.Type;

public class PlaceholderConditionExpression {
    private final String source;
    private final ExpressionNode root;
    private final Set<String> placeholders;

    private PlaceholderConditionExpression(String source, ExpressionNode root, Set<String> placeholders) {
        this.source = source;
        this.root = root;
        this.placeholders = Set.copyOf(placeholders);
    }

    public static PlaceholderConditionExpression compile(String source) {
        List<PlaceholderConditionToken> tokens = new PlaceholderConditionLexer(source).scanTokens();
        Set<String> placeholders = new LinkedHashSet<>();
        for (PlaceholderConditionToken token : tokens) {
            if (token.type() == Type.PLACEHOLDER) {
                placeholders.add(token.lexeme());
            }
        }
        ExpressionNode root = new Parser(source, tokens).parse();
        return new PlaceholderConditionExpression(source, root, placeholders);
    }

    public boolean test(Player player, PlaceholderHook hook) {
        return test(placeholder -> hook.resolve(player, placeholder));
    }

    public String getSource() {
        return source;
    }

    public Set<String> getPlaceholders() {
        return placeholders;
    }

    public boolean test(PlaceholderValueResolver resolver) {
        return root.evaluate(resolver).asBoolean();
    }

    @FunctionalInterface
    public interface PlaceholderValueResolver {
        String resolve(String placeholder);
    }

    private interface ExpressionNode {
        ConditionValue evaluate(PlaceholderValueResolver resolver);
    }

    private record LiteralNode(ConditionValue value) implements ExpressionNode {
        @Override
        public ConditionValue evaluate(PlaceholderValueResolver resolver) {
            return value;
        }
    }

    private record PlaceholderNode(String placeholder) implements ExpressionNode {
        @Override
        public ConditionValue evaluate(PlaceholderValueResolver resolver) {
            return ConditionValue.of(resolver.resolve(placeholder));
        }
    }

    private record UnaryNode(Type operator, ExpressionNode right) implements ExpressionNode {
        @Override
        public ConditionValue evaluate(PlaceholderValueResolver resolver) {
            ConditionValue value = right.evaluate(resolver);
            if (operator == Type.NOT) {
                return ConditionValue.of(!value.asBoolean());
            }
            throw new IllegalStateException(Lang.text("expr.parser.unsupported_unary", operator));
        }
    }

    private record BinaryNode(Type operator, ExpressionNode left, ExpressionNode right) implements ExpressionNode {
        @Override
        public ConditionValue evaluate(PlaceholderValueResolver resolver) {
            return switch (operator) {
                case AND -> ConditionValue.of(
                        left.evaluate(resolver).asBoolean() && right.evaluate(resolver).asBoolean()
                );
                case OR -> ConditionValue.of(
                        left.evaluate(resolver).asBoolean() || right.evaluate(resolver).asBoolean()
                );
                case EQUALS -> ConditionValue.of(compare(left, right, resolver) == 0);
                case NOT_EQUALS -> ConditionValue.of(compare(left, right, resolver) != 0);
                case GREATER -> ConditionValue.of(compare(left, right, resolver) > 0);
                case GREATER_EQUALS -> ConditionValue.of(compare(left, right, resolver) >= 0);
                case LESS -> ConditionValue.of(compare(left, right, resolver) < 0);
                case LESS_EQUALS -> ConditionValue.of(compare(left, right, resolver) <= 0);
                default -> throw new IllegalStateException(Lang.text("expr.parser.unsupported_binary", operator));
            };
        }

        private int compare(ExpressionNode left, ExpressionNode right, PlaceholderValueResolver resolver) {
            ConditionValue leftValue = left.evaluate(resolver);
            ConditionValue rightValue = right.evaluate(resolver);

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
            consume(Type.EOF, Lang.text("expr.parser.trailing_content"));
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
                consume(Type.RIGHT_PAREN, Lang.text("expr.parser.missing_right_paren"));
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
            throw error(peek(), Lang.text("expr.parser.invalid_fragment"));
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
            return new IllegalArgumentException(Lang.text("expr.error", message, token.position(), source));
        }
    }
}
