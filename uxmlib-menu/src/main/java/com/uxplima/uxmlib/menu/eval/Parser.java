package com.uxplima.uxmlib.menu.eval;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * A recursive-descent evaluator over a token list. It evaluates as it parses: there is no intermediate syntax
 * tree and therefore no node type that could carry behaviour, so the only values that ever flow through are
 * doubles, booleans, and strings. Precedence climbs from boolean {@code or} down to unary minus and primaries;
 * {@code ^} is right associative. A recursion-depth cap bounds the work a pathologically nested expression can
 * demand, keeping a hostile menu config from exhausting the stack.
 */
final class Parser {

    private static final int MAX_DEPTH = 48;

    private final List<Token> tokens;

    private int index;

    private int depth;

    Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    Object parse() throws ExpressionException {
        Object value = expression();
        require(Token.Kind.END);
        return value;
    }

    private Object expression() throws ExpressionException {
        if (++depth > MAX_DEPTH) {
            throw new ExpressionException("expression nested too deeply");
        }
        try {
            return or();
        } finally {
            depth--;
        }
    }

    private Object or() throws ExpressionException {
        Object value = and();
        while (matchOperator("||") || matchKeyword("or")) {
            boolean left = Values.bool(value);
            // The right operand is parsed unconditionally: the parser must consume its tokens even when
            // the result is already determined, so the combine happens on two already-evaluated locals.
            boolean right = Values.bool(and());
            value = left || right;
        }
        return value;
    }

    private Object and() throws ExpressionException {
        Object value = not();
        while (matchOperator("&&") || matchKeyword("and")) {
            boolean left = Values.bool(value);
            boolean right = Values.bool(not());
            value = left && right;
        }
        return value;
    }

    private Object not() throws ExpressionException {
        if (matchOperator("!") || matchKeyword("not")) {
            return !Values.bool(not());
        }
        return comparison();
    }

    private Object comparison() throws ExpressionException {
        Object left = additive();
        String op = relationalOp();
        if (op == null) {
            return left;
        }
        advance();
        return Values.compare(left, op, additive());
    }

    private Object additive() throws ExpressionException {
        Object value = multiplicative();
        String op;
        while ((op = arithmeticOp("+", "-")) != null) {
            advance();
            value = Values.arithmetic(Values.number(value), op, Values.number(multiplicative()));
        }
        return value;
    }

    private Object multiplicative() throws ExpressionException {
        Object value = unary();
        String op;
        while ((op = arithmeticOp("*", "/", "%")) != null) {
            advance();
            value = Values.arithmetic(Values.number(value), op, Values.number(unary()));
        }
        return value;
    }

    private Object unary() throws ExpressionException {
        if (arithmeticOp("-") != null) {
            advance();
            return Values.finite(-Values.number(unary()));
        }
        if (arithmeticOp("+") != null) {
            advance();
            return Values.number(unary());
        }
        return power();
    }

    private Object power() throws ExpressionException {
        Object base = primary();
        if (arithmeticOp("^") != null) {
            advance();
            return Values.arithmetic(Values.number(base), "^", Values.number(unary()));
        }
        return base;
    }

    private Object primary() throws ExpressionException {
        Token token = peek();
        return switch (token.kind()) {
            case NUMBER -> consumed(token.number());
            case STRING -> consumed(token.text());
            case LPAREN -> grouped();
            case IDENT -> identifier(token);
            default -> throw new ExpressionException("unexpected token: " + describe(token));
        };
    }

    private Object grouped() throws ExpressionException {
        expect(Token.Kind.LPAREN);
        Object value = expression();
        expect(Token.Kind.RPAREN);
        return value;
    }

    private Object identifier(Token token) throws ExpressionException {
        String name = token.text();
        if (name.equals("true") || name.equals("false")) {
            advance();
            return Boolean.parseBoolean(name);
        }
        if (Functions.isFunction(name)) {
            if (next().kind() != Token.Kind.LPAREN) {
                throw new ExpressionException(name + " is a function and needs brackets: " + name + "(...)");
            }
            return call(name);
        }
        throw new ExpressionException("unknown identifier: " + name);
    }

    private Object call(String name) throws ExpressionException {
        advance();
        expect(Token.Kind.LPAREN);
        List<Double> args = new ArrayList<>();
        if (peek().kind() != Token.Kind.RPAREN) {
            args.add(Values.number(expression()));
            while (matchType(Token.Kind.COMMA)) {
                args.add(Values.number(expression()));
            }
        }
        expect(Token.Kind.RPAREN);
        return Functions.apply(name, args);
    }

    private Object consumed(Object value) {
        advance();
        return value;
    }

    private Token peek() {
        return tokens.get(index);
    }

    /**
     * The token after the current one. It needs no bound check: it is only ever asked while the current token is an
     * identifier, and the token list always ends in an END token, so an identifier is never the last entry.
     */
    private Token next() {
        return tokens.get(index + 1);
    }

    /**
     * Step to the next token. It needs no stop at the end of the list. Every caller checks the current token's kind
     * first and END matches none of those checks, and the walk finishes on {@link #require} rather than
     * {@link #expect}, so no step is ever taken from the END token.
     */
    private void advance() {
        index++;
    }

    /** Check the current token's kind and consume it. */
    private void expect(Token.Kind kind) throws ExpressionException {
        require(kind);
        advance();
    }

    /** Check the current token's kind and leave it where it is: the one check that ends the walk. */
    private void require(Token.Kind kind) throws ExpressionException {
        if (peek().kind() != kind) {
            throw new ExpressionException("expected " + kind + " but found " + describe(peek()));
        }
    }

    private boolean matchType(Token.Kind kind) {
        if (peek().kind() == kind) {
            advance();
            return true;
        }
        return false;
    }

    private boolean matchOperator(String text) {
        if (peek().kind() == Token.Kind.OPERATOR && peek().text().equals(text)) {
            advance();
            return true;
        }
        return false;
    }

    private boolean matchKeyword(String text) {
        if (peek().kind() == Token.Kind.IDENT && peek().text().equals(text)) {
            advance();
            return true;
        }
        return false;
    }

    private @Nullable String arithmeticOp(String... ops) {
        if (peek().kind() != Token.Kind.OPERATOR) {
            return null;
        }
        String text = peek().text();
        for (String op : ops) {
            if (op.equals(text)) {
                return text;
            }
        }
        return null;
    }

    private @Nullable String relationalOp() {
        return arithmeticOp("==", "!=", "<", "<=", ">", ">=");
    }

    /**
     * Names a token in an operator-facing message. A number keeps its value in its own slot and leaves its text
     * empty, so reaching for the text alone would end the message on nothing; it is formatted the way the
     * evaluator formats every other number instead, so "found 2" reads the same as the expression that produced it.
     */
    private static String describe(Token token) {
        return switch (token.kind()) {
            case END -> "end of input";
            case NUMBER -> Values.format(token.number());
            default -> token.text();
        };
    }
}
