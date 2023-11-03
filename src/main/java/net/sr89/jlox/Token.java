package net.sr89.jlox;

public class Token {
    final TokenType type;

    // this is the piece of code associated to this Token,
    // for strings this would be the string with the tokens (like it would appear in the code),
    // for numbers it would be a string like '123.42' etc. etc. (again, like it would appear in the code)
    final String lexeme;

    // the parsed value, for strings it's a string without the double quotes,
    // for numbers it's a Double object, etc.
    final Object literal;
    final int line;

    public Token(TokenType type, String lexeme, Object literal, int line) {
        this.type = type;
        this.lexeme = lexeme;
        this.literal = literal;
        this.line = line;
    }

    @Override
    public String toString() {
        return type + " " + lexeme + " " + literal;
    }
}
