package net.sr89.jlox;

import java.util.List;

import static net.sr89.jlox.TokenType.*;

/**
 * Transforms a list of token into an expression (aka, the AST).
 * <p></p>
 * Implements the following grammar, from <a href="http://craftinginterpreters.com/parsing-expressions.html#ambiguity-and-the-parsing-game">this chapter</a>:
 * <p></p>
 * expression     → equality ;
 * equality       → comparison ( ( "!=" | "==" ) comparison )* ;
 * comparison     → term ( ( ">" | ">=" | "<" | "<=" ) term )* ;
 * term           → factor ( ( "-" | "+" ) factor )* ;
 * factor         → unary ( ( "/" | "*" ) unary )* ;
 * unary          → ( "!" | "-" ) unary
 *                | primary ;
 * primary        → NUMBER | STRING | "true" | "false" | "nil"
 *                | "(" expression ")" ;
 */
public class Parser {
    private static class ParseError extends RuntimeException {}

    private final List<Token> tokens;

    // index of the next token to parse
    private int current = 0;

    Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    Expr parse() {
        try {
            return expression();
        } catch (ParseError e) {
            return null;
        }
    }

    // implements grammar rule:
    // expression → equality ;
    private Expr expression() {
        return equality();
    }

    // implements:
    // equality → comparison ( ( "!=" | "==" ) comparison )* ;
    private Expr equality() {
        Expr expr = comparison();

        // ( ... )* translates to a while loop
        while (match(BANG_EQUAL, EQUAL_EQUAL)) {
            Token operator = previous(); // this will be the matched token, either BANG_EQUAL or EQUAL_EQUAL
            Expr right = comparison();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    // implements:
    // comparison → term ( ( ">" | ">=" | "<" | "<=" ) term )* ;
    private Expr comparison() {
        Expr expr = term();

        while (match(GREATER, GREATER_EQUAL, LESS, LESS_EQUAL)) {
            Token operator = previous(); // this will be one of the tokens matched above
            Expr right = term();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    // implements:
    // term → factor ( ( "-" | "+" ) factor )* ;
    private Expr term() {
        Expr expr = factor();

        while (match(MINUS, PLUS)) {
            Token operator = previous();
            Expr right = factor();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    // implements:
    // factor → unary ( ( "/" | "*" ) unary )* ;
    private Expr factor() {
        Expr expr = unary();

        while (match(SLASH, STAR)) {
            Token operator = previous();
            Expr right = unary();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    // implements:
    // unary  → ( "!" | "-" ) unary
    //        | primary ;
    private Expr unary(){
        if (match(BANG, MINUS)) {
            Token operator = previous();
            Expr right = unary();
            return new Expr.Unary(operator, right);
        } else {
            return primary();
        }
    }

    // implements:
    // primary  → NUMBER | STRING | "true" | "false" | "nil"
    //          | "(" expression ")" ;
    private Expr primary() {
        if (match(FALSE)) return new Expr.Literal(false);
        if (match(TRUE)) return new Expr.Literal(true);
        if (match(NIL)) return new Expr.Literal(null);

        if(match(NUMBER, STRING)) {
            return new Expr.Literal(previous().literal);
        }

        if (match(LEFT_PAREN)) {
            Expr expr = expression();
            consume(RIGHT_PAREN, "Expect ')' after an expression.");
            return new Expr.Grouping(expr);
        }

        throw error(peek(), "Expect expression.");
    }

    // Checks whether the current token is of any of the given types.
    // If so, consumes it and returns true.
    // Otherwise, leaves it alone and returns false.
    private boolean match(TokenType... types) {
        for(TokenType type : types) {
            if (check(type)) {
                advance();
                return true;
            }
        }
        return false;
    }

    private Token consume(TokenType tokenToConsume, String errorMessage) {
        if (check(tokenToConsume)) {
            return advance();
        }

        throw error(peek(), errorMessage);
    }

    // Returns true if the current token is of the wanted type.
    // Does not consume the token.
    private boolean check(TokenType type) {
        if (isAtEnd()) {
            return false;
        } else {
            return peek().type == type;
        }
    }

    // Consumes and returns the current token.
    private Token advance() {
        if (!isAtEnd()) {
            current++;
        }

        return previous();
    }

    private boolean isAtEnd() {
        return peek().type == EOF;
    }

    private Token peek() {
        return tokens.get(current);
    }

    private Token previous() {
        return tokens.get(current - 1);
    }

    private ParseError error(Token token, String message) {
        Lox.error(token, message);
        return new ParseError();
    }

    /*
    When we encounter a parsing error, we want to try to find the end of the current statement,
    consume and disregard any tokens in that statement.
    Then we want to simply keep going.
     */
    private void synchronize() {
        advance();

        while(!isAtEnd()) {
            if (previous().type == SEMICOLON) {
                // We found the end of the statement (aka a semicolon).
                // Let's keep going.
                return;
            }

            switch (peek().type) {
                // We found the start of a new statement.
                // Let's keep going. Note that in this case, the token wasn't consumed,
                // because it's part of that new statement.
                case CLASS, FUN, VAR, FOR, IF, WHILE, PRINT, RETURN:
                    return;
            }

            advance();
        }
    }
}
