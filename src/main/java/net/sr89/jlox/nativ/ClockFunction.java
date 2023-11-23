package net.sr89.jlox.nativ;

import net.sr89.jlox.Interpreter;
import net.sr89.jlox.LoxCallable;

import java.util.List;

public class ClockFunction implements LoxCallable {
    @Override
    public int arity() {
        return 0;
    }

    @Override
    public Object call(Interpreter interpreter, List<Object> arguments) {
        return (double) System.currentTimeMillis() / 1000.0;
    }

    @Override
    public String toString() {
        return "<native fn>";
    }
}
