package norswap.sigh.ast;

import norswap.autumn.positions.Span;
import norswap.utils.Util;

public final class MonadicForkNode extends ExpressionNode {
    public final ExpressionNode operand;
    public final MonadicOperator operatorL, operatorR;
    public final DiadicOperator operatorM;

    public MonadicForkNode (Span span, Object operatorL, Object operatorM, Object operatorR, Object operand) {
        super(span);
        this.operand = Util.cast(operand, ExpressionNode.class);
        this.operatorL = Util.cast(operatorL, MonadicOperator.class);
        this.operatorM = Util.cast(operatorM, DiadicOperator.class);
        this.operatorR = Util.cast(operatorR, MonadicOperator.class);
    }

    @Override public String contents ()
    {
        String candidate = String.format("(%s %s %s) %s",
            operatorL.string, operatorM.string, operatorR.string, operand.contents());

        return candidate.length() <= contentsBudget()
            ? candidate
            : String.format("(%s %s %s) (?)", operatorL.string, operatorM.string, operatorR.string);
    }
}
