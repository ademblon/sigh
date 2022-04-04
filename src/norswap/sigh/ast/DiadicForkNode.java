package norswap.sigh.ast;

import norswap.autumn.positions.Span;
import norswap.utils.Util;

public final class DiadicForkNode extends ExpressionNode {
    public final ExpressionNode operandL, operandR;
    public final DiadicOperator operatorL, operatorR, operatorM;

    public DiadicForkNode (Span span, Object operandL, Object operatorL, Object operatorM, Object operatorR, Object operandR) {
        super(span);
        this.operandL = Util.cast(operandL, ExpressionNode.class);
        this.operatorL = Util.cast(operatorL, DiadicOperator.class);
        this.operatorM = Util.cast(operatorM, DiadicOperator.class);
        this.operatorR = Util.cast(operatorR, DiadicOperator.class);
        this.operandR = Util.cast(operandR, ExpressionNode.class);
    }

    @Override public String contents ()
    {
        String candidate = String.format("%s (%s %s %s) %s",
            operandL.contents(), operatorL.string, operatorM.string, operatorR.string, operandR.contents());

        return candidate.length() <= contentsBudget()
            ? candidate
            : String.format("(?) (%s %s %s) (?)", operatorL.string, operatorM.string, operatorR.string);
    }
}
