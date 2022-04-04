package norswap.sigh.ast;

import norswap.autumn.positions.Span;
import norswap.utils.Util;

public final class MonadicExpressionNode extends ExpressionNode
{
    public final ExpressionNode operand;
    public final MonadicOperator operator;

    public MonadicExpressionNode (Span span, Object operator, Object operand) {
        super(span);
        this.operand = Util.cast(operand, ExpressionNode.class);
        this.operator = Util.cast(operator, MonadicOperator.class);
    }

    @Override public String contents ()
    {
        String candidate = operator.string + operand.contents();
        return candidate.length() <= contentsBudget()
            ? candidate
            : operator.string + "(?)";
    }
}