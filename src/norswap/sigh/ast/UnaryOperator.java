package norswap.sigh.ast;

public enum UnaryOperator
{
    NOT("!"),
    GRAB_LAST("{:"),
    SUM_SLASH("+/");

    public final String string;

    UnaryOperator (String string) {
        this.string = string;
    }
}
