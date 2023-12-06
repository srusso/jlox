package net.sr89.jlox;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static net.sr89.jlox.TokenType.*;

/**
 * Transforms a list of token into an expression (aka, the AST).
 * <p></p>
 * Implements the following grammar, from <a href="http://craftinginterpreters.com/parsing-expressions.html#ambiguity-and-the-parsing-game">this chapter</a>:
 * <p></p>
 * program        → declaration* EOF ;
 * declaration    → classDecl
 *                | funDecl
 *                | varDecl
 *                | statement ;
 * classDecl      → "class" IDENTIFIER "{" function* "}" ;
 * funDecl        → "fun" function ;
 * function       → IDENTIFIER "(" parameters? ")" block ;
 * parameters     → IDENTIFIER ( "," IDENTIFIER )* ;
 * varDecl        → "var" IDENTIFIER ( "=" expression )? ";" ;
 * statement      → exprStmt
 *                | forStmt
 *                | ifStmt
 *                | printStmt
 *                | returnStmt
 *                | whileStmt
 *                | block ;
 * returnStmt     → "return" expression? ";" ;
 * forStmt        → "for" "(" ( varDecl | exprStmt | ";" )
 *                  expression? ";"
 *                  expression? ")" statement ;
 * whileStmt      → "while" "(" expression ")" statement ;
 * ifStmt         → "if" "(" expression ")" statement
 *                ( "else" statement )? ;
 * block          → "{" declaration* "}" ;
 * exprStmt       → expression ";" ;
 * printStmt      → "print" expression ";" ;
 * expression     → assignment ;
 * assignment     → IDENTIFIER "=" assignment
 *                | logic_or ;
 * logic_or       → logic_and ( "or" logic_and )* ;
 * logic_and      → equality ( "and" equality )* ;
 * equality       → comparison ( ( "!=" | "==" ) comparison )* ;
 * comparison     → term ( ( ">" | ">=" | "<" | "<=" ) term )* ;
 * term           → factor ( ( "-" | "+" ) factor )* ;
 * factor         → unary ( ( "/" | "*" ) unary )* ;
 * unary          → ( "!" | "-" ) unary | call ;
 * call           → primary ( "(" arguments? ")" )* ;
 * arguments      → expression ( "," expression )* ;
 * primary        → NUMBER | STRING | "true" | "false" | "nil"
 *                | "(" expression ")"
 *                | IDENTIFIER;
 */
public class Parser {
    private static class ParseError extends RuntimeException {
    }

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
        while (!isAtEnd()) {
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
        Expr expr = or();

        if (match(EQUAL)) {
            Token equals = previous();

            // One slight difference from binary operators is that we don’t loop
            // to build up a sequence of the same operator. Since assignment is right-associative,
            // we instead recursively call assignment() to parse the right-hand side.
            Expr value = assignment();

            if (expr instanceof Expr.Variable) {
                Token name = ((Expr.Variable) expr).name;
                return new Expr.Assign(name, value);
            }

            // We report an error if the left-hand side isn’t a valid assignment target,
            // but we don’t throw it because the parser isn’t in a confused state where we need
            // to go into panic mode and synchronize.
            error(equals, "Invalid assignment target.");
        }

        return expr;
    }

    private Expr or() {
        Expr expr = and();

        while (match(OR)) {
            Token operator = previous();
            Expr right = and();
            expr = new Expr.Logical(expr, operator, right);
        }

        return expr;
    }

    private Expr and() {
        Expr expr = equality();

        while (match(AND)) {
            Token operator = previous();
            Expr right = equality();
            expr = new Expr.Logical(expr, operator, right);
        }

        return expr;
    }

    private Stmt declaration() {
        try {
            if (match(CLASS)) return classDeclaration();

            if (match(FUN)) return function(FunctionKind.FUNCTION);

            if (match(VAR))
                return varDeclaration();

            return statement();
        } catch (ParseError e) {
            synchronize();
            return null;
        }
    }

    private Stmt classDeclaration() {
        Token name = consume(IDENTIFIER, "Expect class name.");
        consume(LEFT_BRACE, "Expect '{' before class body.");

        List<Stmt.Function> methods = new ArrayList<>();
        while (!check(RIGHT_BRACE) && !isAtEnd()) {
            methods.add(function(FunctionKind.METHOD));
        }

        consume(RIGHT_BRACE, "Expect '}' after class body.");

        return new Stmt.Class(name, methods);
    }

    private Stmt.Function function(FunctionKind kind) {
        Token name = consume(IDENTIFIER, "Expect " + kind + " name.");

        consume(LEFT_PAREN, "Expect '(' after " + kind + " name.");
        List<Token> parameters = new ArrayList<>();
        if (!check(RIGHT_PAREN)) {
            do {
                if (parameters.size() >= 255) {
                    error(peek(), "Can't have more than 255 parameters.");
                }

                parameters.add(
                    consume(IDENTIFIER, "Expect parameter name."));
            } while (match(COMMA));
        }
        consume(RIGHT_PAREN, "Expect ')' after parameters.");

        // Note that we consume the { at the beginning of the body here before calling block().
        // That’s because block() assumes the brace token has already been matched.
        // Consuming it here lets us report a more precise error message if the { isn’t found,
        // since we know it’s in the context of a function declaration.
        consume(LEFT_BRACE, "Expect '{' before " + kind + " body.");
        List<Stmt> body = block();
        return new Stmt.Function(name, parameters, body);
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
        if (match(FOR)) {
            return forStatement();
        }
        if (match(IF)) {
            return ifStatement();
        }
        if (match(PRINT)) {
            return printStatement();
        }
        if (match(RETURN)) {
            return returnStatement();
        }
        if (match(WHILE)) {
            return whileStatement();
        }
        if (match(LEFT_BRACE)) {
            return new Stmt.Block(block());
        }
        {
            return expressionStatement();
        }
    }

    private Stmt forStatement() {
        consume(LEFT_PAREN, "Expect '(' after 'for'.");

        final Stmt initializer;
        if (match(SEMICOLON)) {
            initializer = null;
        } else if (match(VAR)) {
            initializer = varDeclaration();
        } else {
            initializer = expressionStatement();
        }

        final Expr condition;
        if (!check(SEMICOLON)) {
            condition = expression();
        } else {
            condition = new Expr.Literal(true);
        }
        consume(SEMICOLON, "Expect ';' after loop condition.");

        final Expr increment;
        if (!check(RIGHT_PAREN)) {
            increment = expression();
        } else {
            increment = null;
        }
        consume(RIGHT_PAREN, "Expect ')' after for clauses.");

        Stmt body = statement();

        return toWhileLoop(initializer, condition, increment, body);
    }

    private Stmt toWhileLoop(Stmt initializer, Expr condition, Expr increment, final Stmt forLoopBody) {
        Stmt whileLoopBody = forLoopBody;

        if (increment != null) {
            whileLoopBody = new Stmt.Block(
                Arrays.asList(
                    whileLoopBody,
                    new Stmt.Expression(increment)));
        }

        whileLoopBody = new Stmt.While(condition, whileLoopBody);

        if (initializer != null) {
            whileLoopBody = new Stmt.Block(Arrays.asList(initializer, whileLoopBody));
        }

        return whileLoopBody;
    }

    private Stmt whileStatement() {
        consume(LEFT_PAREN, "Expect '(' after 'while'.");
        Expr condition = expression();
        consume(RIGHT_PAREN, "Expect ')' after condition.");
        Stmt body = statement();

        return new Stmt.While(condition, body);
    }

    private Stmt ifStatement() {
        consume(LEFT_PAREN, "Expect '(' after 'if'.");
        Expr condition = expression();
        consume(RIGHT_PAREN, "Expect ')' after if condition.");

        Stmt thenBranch = statement();
        Stmt elseBranch = null;
        if (match(ELSE)) {
            // note: if there are multiple nested ifs, the else always applies to the closest one (innermost)
            elseBranch = statement();
        }

        return new Stmt.If(condition, thenBranch, elseBranch);
    }

    private Stmt expressionStatement() {
        Expr expr = expression();
        consume(SEMICOLON, "Expect ';' after expression.");
        return new Stmt.Expression(expr);
    }

    private List<Stmt> block() {
        List<Stmt> statements = new ArrayList<>();

        while (!check(RIGHT_BRACE) && !isAtEnd()) {
            statements.add(declaration());
        }

        consume(RIGHT_BRACE, "Expect '}' after block.");
        return statements;
    }

    private Stmt printStatement() {
        Expr value = expression();
        consume(SEMICOLON, "Expect ';' after value.");
        return new Stmt.Print(value);
    }

    private Stmt returnStatement() {
        Token keyword = previous(); // consume the matched RETURN token

        final Expr value;
        if (!check(SEMICOLON)) {
            value = expression();
        } else {
            value = null;
        }

        consume(SEMICOLON, "Expect ';' after return value.");
        return new Stmt.Return(keyword, value);
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
    private Expr unary() {
        if (match(BANG, MINUS)) {
            Token operator = previous();
            Expr right = unary();
            return new Expr.Unary(operator, right);
        } else {
            return call();
        }
    }

    private Expr call() {
        Expr expr = primary();

        while (true) {
            if (match(LEFT_PAREN)) {
                expr = finishCall(expr);
            } else {
                break;
            }
        }

        return expr;
    }

    private Expr finishCall(Expr callee) {
        List<Expr> arguments = new ArrayList<>();
        if (!check(RIGHT_PAREN)) {
            do {
                if (arguments.size() >= 255) {
                    error(peek(), "Can't have more than 255 arguments.");
                }
                arguments.add(expression());
            } while (match(COMMA));
        }

        Token paren = consume(RIGHT_PAREN,
            "Expect ')' after arguments.");

        return new Expr.Call(callee, paren, arguments);
    }

    // implements:
    // primary  → NUMBER | STRING | "true" | "false" | "nil"
    //          | "(" expression ")" ;
    //          | IDENTIFIER
    private Expr primary() {
        if (match(FALSE)) return new Expr.Literal(false);
        if (match(TRUE)) return new Expr.Literal(true);
        if (match(NIL)) return new Expr.Literal(null);

        if (match(NUMBER, STRING)) {
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
        for (TokenType type : types) {
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

        while (!isAtEnd()) {
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
