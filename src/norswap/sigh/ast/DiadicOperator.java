package norswap.sigh.ast;

public enum DiadicOperator
{
    MULTIPLY("*"),
    DIVIDE("/"),
    REMAINDER("%"),
    ADD("+"),
    SUBTRACT("-"),
    EQUALITY("=="),
    NOT_EQUALS("!="),
    GREATER(">"),
    LOWER("<"),
    GREATER_EQUAL(">="),
    LOWER_EQUAL("<="),
    AND("&&"),
    OR("||");

    public final String string;

    DiadicOperator (String string) {
        this.string = string;
    }
}
