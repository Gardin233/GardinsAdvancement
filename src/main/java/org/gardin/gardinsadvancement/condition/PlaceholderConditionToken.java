package org.gardin.gardinsadvancement.condition;

public record PlaceholderConditionToken(Type type, String lexeme, int position) {
    public enum Type {
        LEFT_PAREN,
        RIGHT_PAREN,
        AND,
        OR,
        NOT,
        EQUALS,
        NOT_EQUALS,
        GREATER,
        GREATER_EQUALS,
        LESS,
        LESS_EQUALS,
        PLACEHOLDER,
        STRING,
        NUMBER,
        IDENTIFIER,
        BOOLEAN,
        NULL,
        EOF
    }
}
