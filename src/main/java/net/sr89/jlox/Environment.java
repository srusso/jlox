package net.sr89.jlox;

import java.util.HashMap;
import java.util.Map;

public class Environment {
    private final Map<String, Object> values = new HashMap<>();

    void define(String variableName, Object value) {
        // Note that we don't check whether the variable already exists.
        // Basically this program is allowed:
        // var a = "something";
        // print a;
        // var a = "something else";
        // print a;
        values.put(variableName, value);
    }

    Object get(Token variableName) {
        if (values.containsKey(variableName.lexeme)) {
            return values.get(variableName.lexeme);
        } else {
            throw new RuntimeError(variableName, "Undefined variable '" + variableName.lexeme + "'.");
        }
    }
}
