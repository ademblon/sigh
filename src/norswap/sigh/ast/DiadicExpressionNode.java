package norswap.sigh.ast;

import norswap.autumn.positions.Span;
import norswap.utils.Util;

public final class DiadicExpressionNode extends ExpressionNode
{
    public final ExpressionNode left, right;
    public final DiadicOperator operator;

    public DiadicExpressionNode (Span span, Object left, Object operator, Object right) {
        super(span);
        this.left = Util.cast(left, ExpressionNode.class);
        this.right = Util.cast(right, ExpressionNode.class);
        this.operator = Util.cast(operator, DiadicOperator.class);
    }

    @Override public String contents ()
    {
        String candidate = String.format("%s %s %s",
            left.contents(), operator.string, right.contents());

        return candidate.length() <= contentsBudget()
            ? candidate
            : String.format("(?) %s (?)", operator.string);
    }
}
