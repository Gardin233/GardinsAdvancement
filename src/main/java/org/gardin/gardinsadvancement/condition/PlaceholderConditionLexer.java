package org.gardin.gardinsadvancement.condition;

import org.gardin.gardinsadvancement.util.Lang;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.gardin.gardinsadvancement.condition.PlaceholderConditionToken.Type;

public class PlaceholderConditionLexer {
    private final String source;
    private final List<PlaceholderConditionToken> tokens = new ArrayList<>();
    private int current;

    public PlaceholderConditionLexer(String source) {
        this.source = source == null ? "" : source;
    }

    public List<PlaceholderConditionToken> scanTokens() {
        while (!isAtEnd()) {
            int start = current;
            char c = advance();
            switch (c) {
                case ' ', '\r', '\t', '\n' -> {
                }
                case '(' -> addToken(Type.LEFT_PAREN, "(", start);
                case ')' -> addToken(Type.RIGHT_PAREN, ")", start);
                case '!' -> addToken(match('=') ? Type.NOT_EQUALS : Type.NOT, readMatched(start), start);
                case '=' -> {
                    if (!match('=')) {
                        throw error(Lang.text("expr.lexer.single_equals"));
                    }
                    addToken(Type.EQUALS, readMatched(start), start);
                }
                case '>' -> addToken(match('=') ? Type.GREATER_EQUALS : Type.GREATER, readMatched(start), start);
                case '<' -> addToken(match('=') ? Type.LESS_EQUALS : Type.LESS, readMatched(start), start);
                case '&' -> {
                    if (!match('&')) {
                        throw error(Lang.text("expr.lexer.single_ampersand"));
                    }
                    addToken(Type.AND, readMatched(start), start);
                }
                case '|' -> {
                    if (!match('|')) {
                        throw error(Lang.text("expr.lexer.single_pipe"));
                    }
                    addToken(Type.OR, readMatched(start), start);
                }
                case '"', '\'' -> scanString(c, start);
                case '%' -> scanPlaceholder(start);
                default -> {
                    if (isDigit(c)) {
                        scanNumber(start);
                    } else if (isIdentifierStart(c)) {
                        scanIdentifier(start);
                    } else {
                        throw error(Lang.text("expr.lexer.unknown_char", c));
                    }
                }
            }
        }
        tokens.add(new PlaceholderConditionToken(Type.EOF, "", source.length()));
        return List.copyOf(tokens);
    }

    private void scanString(char quote, int start) {
        while (!isAtEnd() && peek() != quote) {
            advance();
        }
        if (isAtEnd()) {
            throw error(Lang.text("expr.lexer.string_unterminated"));
        }
        advance();
        String content = source.substring(start + 1, current - 1);
        addToken(Type.STRING, content, start);
    }

    private void scanPlaceholder(int start) {
        while (!isAtEnd() && peek() != '%') {
            advance();
        }
        if (isAtEnd()) {
            throw error(Lang.text("expr.lexer.placeholder_unterminated"));
        }
        advance();
        String content = source.substring(start, current);
        addToken(Type.PLACEHOLDER, content, start);
    }

    private void scanNumber(int start) {
        while (isDigit(peek())) {
            advance();
        }
        if (peek() == '.' && isDigit(peekNext())) {
            advance();
            while (isDigit(peek())) {
                advance();
            }
        }
        addToken(Type.NUMBER, source.substring(start, current), start);
    }

    private void scanIdentifier(int start) {
        while (isIdentifierPart(peek())) {
            advance();
        }
        String lexeme = source.substring(start, current);
        String normalized = lexeme.toLowerCase(Locale.ROOT);
        Type type = switch (normalized) {
            case "true", "false" -> Type.BOOLEAN;
            case "null" -> Type.NULL;
            default -> Type.IDENTIFIER;
        };
        addToken(type, lexeme, start);
    }

    private void addToken(Type type, String lexeme, int position) {
        tokens.add(new PlaceholderConditionToken(type, lexeme, position));
    }

    private boolean match(char expected) {
        if (isAtEnd() || source.charAt(current) != expected) {
            return false;
        }
        current++;
        return true;
    }

    private char advance() {
        return source.charAt(current++);
    }

    private char peek() {
        return isAtEnd() ? '\0' : source.charAt(current);
    }

    private char peekNext() {
        return current + 1 >= source.length() ? '\0' : source.charAt(current + 1);
    }

    private boolean isAtEnd() {
        return current >= source.length();
    }

    private boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private boolean isIdentifierStart(char c) {
        return Character.isLetter(c) || c == '_' || c == '$';
    }

    private boolean isIdentifierPart(char c) {
        return isIdentifierStart(c) || isDigit(c) || c == ':' || c == '.' || c == '-';
    }

    private String readMatched(int start) {
        return source.substring(start, current);
    }

    private IllegalArgumentException error(String message) {
        return new IllegalArgumentException(Lang.text("expr.error", message, current, source));
    }
}
