package net.sr89.jlox;

import java.util.List;

class LoxFunction implements LoxCallable {
  private final Stmt.Function declaration;

  /**
   * This is the environment that is active when the function is <i>declared</i>, not called.
   */
  private final Environment closure;

  LoxFunction(Stmt.Function declaration, Environment closure) {
    this.declaration = declaration;
    this.closure = closure;
  }

  /**
   * Creates a new instance of this function, bound to a specific object.
   * So that 'this' will refer to that object.
   *
   * See {@link Resolver#visitClassStmt(Stmt.Class)}.
   * The 'this' lives in a scope together with the methods.
   * bind() (this method) is only called for lox methods, not functions.
   */
  LoxFunction bind(LoxInstance instance) {
    Environment environment = new Environment(closure);
    environment.define("this", instance);
    return new LoxFunction(declaration, environment);
  }

  @Override
  public int arity() {
    return declaration.params.size();
  }

  @Override
  public Object call(Interpreter interpreter, List<Object> arguments) {
    Environment environment = new Environment(closure);
    for (int i = 0; i < declaration.params.size(); i++) {
      environment.define(declaration.params.get(i).lexeme, arguments.get(i));
    }

    try {
      interpreter.executeBlock(declaration.body, environment);
    } catch (Return returnValue) {
      return returnValue.value;
    }

    return null;
  }

  @Override
  public String toString() {
    return "<fn " + declaration.name.lexeme + ">";
  }
}