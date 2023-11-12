package net.sr89.jlox;

import java.util.HashMap;
import java.util.Map;

public class Environment {
    // reference to the parent environment/scope
    private final Environment enclosing;
    private final Map<String, Object> values = new HashMap<>();

    Environment() {
        enclosing = null;
    }

    Environment(Environment enclosing) {
        this.enclosing = enclosing;
    }

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
        } else if (enclosing != null) {
            return enclosing.get(variableName);
        } else {
            throw new RuntimeError(variableName, "Undefined variable '" + variableName.lexeme + "'.");
        }
    }

    public void assign(Token name, Object value) {
        if (values.containsKey(name.lexeme)) {
            values.put(name.lexeme, value);
        } else if (enclosing != null) {
            enclosing.assign(name, value);
        } else {
            throw new RuntimeError(name, "Undefined variable '" + name.lexeme + "'");
        }
    }
}
