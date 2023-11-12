package net.sr89.jlox;

import java.util.ArrayList;
import java.util.List;

import static net.sr89.jlox.TokenType.*;

/**
 * Transforms a list of token into an expression (aka, the AST).
 * <p></p>
 * Implements the following grammar, from <a href="http://craftinginterpreters.com/parsing-expressions.html#ambiguity-and-the-parsing-game">this chapter</a>:
 * <p></p>
 * program        → declaration* EOF ;
 * declaration    → varDecl
 *                | statement ;
 * varDecl        → "var" IDENTIFIER ( "=" expression )? ";" ;
 * statement      → exprStmt
 *                | printStmt ;
 * exprStmt       → expression ";" ;
 * printStmt      → "print" expression ";" ;
 * expression     → assignment ;
 * assignment     → IDENTIFIER "=" assignment
 *                | equality;
 * equality       → comparison ( ( "!=" | "==" ) comparison )* ;
 * comparison     → term ( ( ">" | ">=" | "<" | "<=" ) term )* ;
 * term           → factor ( ( "-" | "+" ) factor )* ;
 * factor         → unary ( ( "/" | "*" ) unary )* ;
 * unary          → ( "!" | "-" ) unary
 *                | primary ;
 * primary        → NUMBER | STRING | "true" | "false" | "nil"
 *                | "(" expression ")"
 *                | IDENTIFIER;
 */
public class Parser {
    private static class ParseError extends RuntimeException {}

    private final List<Token> tokens;

    // index of the next token to parse
    private int current = 0;

    Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    List<Stmt> parse() {
        List<Stmt> statements = new ArrayList<>();

        // the program is just a list of statements! see grammar rule:
        // program        → statement* EOF ;
        while(!isAtEnd()) {
            statements.add(declaration());
        }

        return statements;
    }

    // implements grammar rule for expression
    private Expr expression() {
        return assignment();
    }

    // see http://craftinginterpreters.com/statements-and-state.html#assignment-syntax
    private Expr assignment() {
        Expr expr = equality();

        if (match(EQUAL)) {
            Token equals = previous();

            // One slight difference from binary operators is that we don’t loop
            // to build up a sequence of the same operator. Since assignment is right-associative,
            // we instead recursively call assignment() to parse the right-hand side.
            Expr value = assignment();

            if (expr instanceof Expr.Variable) {
                Token name = ((Expr.Variable)expr).name;
                return new Expr.Assign(name, value);
            }

            error(equals, "Invalid assignment target.");
        }

        return expr;
    }

    private Stmt declaration() {
        try {
            if (match(VAR)) {
                return varDeclaration();
            } else {
                return statement();
            }
        } catch (ParseError e) {
            synchronize();
            return null;
        }
    }

    private Stmt varDeclaration() {
        Token name = consume(IDENTIFIER, "Expect variable name.");

        final Expr initializer;
        if (match(EQUAL)) {
            initializer = expression();
        } else {
            initializer = null;
        }

        consume(SEMICOLON, "Expect ';' after variable declaration.");
        return new Stmt.Var(name, initializer);
    }

    private Stmt statement() {
        if(match(PRINT)) {
            return printStatement();
        } else {
            return expressionStatement();
        }
    }

    private Stmt expressionStatement() {
        Expr expr = expression();
        consume(SEMICOLON, "Expect ';' after expression.");
        return new Stmt.Expression(expr);
    }

    private Stmt printStatement() {
        Expr value = expression();
        consume(SEMICOLON, "Expect ';' after value.");
        return new Stmt.Print(value);
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
    //          | IDENTIFIER
    private Expr primary() {
        if (match(FALSE)) return new Expr.Literal(false);
        if (match(TRUE)) return new Expr.Literal(true);
        if (match(NIL)) return new Expr.Literal(null);

        if(match(NUMBER, STRING)) {
            return new Expr.Literal(previous().literal);
        }

        if (match(IDENTIFIER)) {
            return new Expr.Variable(previous());
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
