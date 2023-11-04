package net.sr89.jlox;

public class Interpreter implements Expr.Visitor<Object> {
    @Override
    public Object visitBinaryExpr(Expr.Binary expr) {
        Object left = evaluate(expr.left);
        Object right = evaluate(expr.right);

        switch (expr.operator.type) {
            case MINUS:
                // yes, we are casting directly to double here. it's a dynamically
                // typed language and the cast will fail at runtime.
                return (double) left - (double) right;
            case SLASH:
                return (double) left / (double) right;
            case STAR:
                return (double) left * (double) right;
            case PLUS:
                if (left instanceof Double && right instanceof Double) {
                    return (double) left + (double) right;
                }

                if (left instanceof String && right instanceof String) {
                    return left + (String) right;
                }

                break;
            case GREATER:
                return (double) left > (double) right;
            case GREATER_EQUAL:
                return (double) left >= (double) right;
            case LESS:
                return (double) left < (double) right;
            case LESS_EQUAL:
                return (double) left <= (double) right;
            case BANG_EQUAL:
                return !isEqual(left, right);
            case EQUAL_EQUAL:
                return isEqual(left, right);
        }

        return null;
    }

    @Override
    public Object visitGroupingExpr(Expr.Grouping expr) {
        return evaluate(expr.expression);
    }

    @Override
    public Object visitLiteralExpr(Expr.Literal expr) {
        return expr.value;
    }

    @Override
    public Object visitUnaryExpr(Expr.Unary expr) {
        final Object value = evaluate(expr.right);

        switch (expr.operator.type) {
            case MINUS:
                return -(double) value;
            case BANG:
                return !isTruthy(value);
        }

        // Unreachable.
        return null;
    }

    // In Lox, `false` and `nil` are falsey, everything else is truthy.
    private boolean isTruthy(Object object) {
        if (object == null) return false;
        if (object instanceof Boolean) return (boolean) object;
        return true;
    }

    private boolean isEqual(Object a, Object b) {
        if (a == null && b == null) return true;
        if (a == null) return false;

        return a.equals(b);
    }

    private Object evaluate(Expr expr) {
        return expr.accept(this);
    }
}
