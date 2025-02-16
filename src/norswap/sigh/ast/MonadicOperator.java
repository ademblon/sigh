package norswap.sigh.ast;

import norswap.autumn.Grammar.rule;

public enum MonadicOperator
{
    NOT("!"),
    GRAB_LAST("{:"),
    SUM_SLASH("+/"),
    MULT_SLASH("./"),
    DIV_SLASH(":/"),
    HASHTAG("#"),
    SELF_ADD("+:"),
    SELF_MULT("*:"),
    MIN_SLASH("-/"),
    AND_SLASH("&/"),
    OR_SLASH("|/");

    public final String string;

    MonadicOperator (String string) {
        this.string = string;
    }
}
