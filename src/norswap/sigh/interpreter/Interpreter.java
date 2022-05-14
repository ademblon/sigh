package norswap.sigh.interpreter;

import norswap.sigh.ast.*;
import norswap.sigh.scopes.DeclarationKind;
import norswap.sigh.scopes.RootScope;
import norswap.sigh.scopes.Scope;
import norswap.sigh.scopes.SyntheticDeclarationNode;
import norswap.sigh.types.ArrayType;
import norswap.sigh.types.FloatType;
import norswap.sigh.types.IntType;
import norswap.sigh.types.StringType;
import norswap.sigh.types.Type;
import norswap.uranium.Reactor;
import norswap.utils.Util;
import norswap.utils.exceptions.Exceptions;
import norswap.utils.exceptions.NoStackException;
import norswap.utils.visitors.ValuedVisitor;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static norswap.utils.Util.cast;
import static norswap.utils.Vanilla.coIterate;
import static norswap.utils.Vanilla.map;

/**
 * Implements a simple but inefficient interpreter for Sigh.
 *
 * <h2>Limitations</h2>
 * <ul>
 *     <li>The compiled code currently doesn't support closures (using variables in functions that
 *     are declared in some surroudning scopes outside the function). The top scope is supported.
 *     </li>
 * </ul>
 *
 * <p>Runtime value representation:
 * <ul>
 *     <li>{@code Int}, {@code Float}, {@code Bool}: {@link Long}, {@link Double}, {@link Boolean}</li>
 *     <li>{@code String}: {@link String}</li>
 *     <li>{@code null}: {@link Null#INSTANCE}</li>
 *     <li>Arrays: {@code Object[]}</li>
 *     <li>Structs: {@code HashMap<String, Object>}</li>
 *     <li>Functions: the corresponding {@link DeclarationNode} ({@link FunDeclarationNode} or
 *     {@link SyntheticDeclarationNode}), excepted structure constructors, which are
 *     represented by {@link Constructor}</li>
 *     <li>Types: the corresponding {@link StructDeclarationNode}</li>
 * </ul>
 */
public final class Interpreter {
    // ---------------------------------------------------------------------------------------------

    private final ValuedVisitor<SighNode, Object> visitor = new ValuedVisitor<>();
    private final Reactor reactor;
    private ScopeStorage storage = null;
    private RootScope rootScope;
    private ScopeStorage rootStorage;

    // ---------------------------------------------------------------------------------------------

    public Interpreter (Reactor reactor) {
        this.reactor = reactor;

        // expressions
        visitor.register(IntLiteralNode.class, this::intLiteral);
        visitor.register(FloatLiteralNode.class, this::floatLiteral);
        visitor.register(StringLiteralNode.class, this::stringLiteral);
        visitor.register(ReferenceNode.class, this::reference);
        visitor.register(ConstructorNode.class, this::constructor);
        visitor.register(ArrayLiteralNode.class, this::arrayLiteral);
        visitor.register(ParenthesizedNode.class, this::parenthesized);
        visitor.register(FieldAccessNode.class, this::fieldAccess);
        visitor.register(ArrayAccessNode.class, this::arrayAccess);
        visitor.register(FunCallNode.class, this::funCall);
        visitor.register(MonadicExpressionNode.class, this::monadicExpression);
        visitor.register(DiadicExpressionNode.class, this::diadicExpression);
        visitor.register(MonadicForkNode.class, this::monadicForkExpression);
        visitor.register(DiadicForkNode.class, this::diadicForkExpression);
        visitor.register(AssignmentNode.class, this::assignment);

        // statement groups & declarations
        visitor.register(RootNode.class, this::root);
        visitor.register(BlockNode.class, this::block);
        visitor.register(VarDeclarationNode.class, this::varDecl);
        // no need to visitor other declarations! (use fallback)

        // statements
        visitor.register(ExpressionStatementNode.class, this::expressionStmt);
        visitor.register(IfNode.class, this::ifStmt);
        visitor.register(WhileNode.class, this::whileStmt);
        visitor.register(ReturnNode.class, this::returnStmt);

        visitor.registerFallback(node -> null);
    }

    // ---------------------------------------------------------------------------------------------

    public Object interpret (SighNode root) {
        try {
            return run(root);
        } catch (PassthroughException e) {
            throw Exceptions.runtime(e.getCause());
        }
    }

    // ---------------------------------------------------------------------------------------------

    private Object run (SighNode node) {
        try {
            return visitor.apply(node);
        } catch (InterpreterException | Return | PassthroughException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new InterpreterException("exception while executing " + node, e);
        }
    }

    // ---------------------------------------------------------------------------------------------

    /**
     * Used to implement the control flow of the return statement.
     */
    private static class Return extends NoStackException {
        final Object value;

        private Return (Object value) {
            this.value = value;
        }
    }

    // ---------------------------------------------------------------------------------------------

    private <T> T get (SighNode node) {
        return cast(run(node));
    }

    // ---------------------------------------------------------------------------------------------

    private Long intLiteral (IntLiteralNode node) {
        return node.value;
    }

    private Double floatLiteral (FloatLiteralNode node) {
        return node.value;
    }

    private String stringLiteral (StringLiteralNode node) {
        return node.value;
    }

    // ---------------------------------------------------------------------------------------------

    private Object parenthesized (ParenthesizedNode node) {
        return get(node.expression);
    }

    // ---------------------------------------------------------------------------------------------

    private Object[] arrayLiteral (ArrayLiteralNode node) {
        return map(node.components, new Object[0], visitor);
    }

    // ---------------------------------------------------------------------------------------------

    private Object diadicExpression (DiadicExpressionNode node) {
        Type leftType = reactor.get(node.left, "type");
        Type rightType = reactor.get(node.right, "type");

        Object left = get(node.left);
        Object right = get(node.right);

        DiadicOperator operator = node.operator;

        return diadicExpressionCalculate(leftType,rightType,left,right,operator);
    }
    
    private Object diadicExpressionCalculate(Type leftType, Type rightType, Object left, Object right, DiadicOperator operator)
    {
        // Cases where both operands should not be evaluated.
        switch (operator) {
            case OR:
                return booleanOp(left,right, false);
            case AND:
                return booleanOp(left,right, true);
        }
        
        if (operator == DiadicOperator.ADD
            && (leftType instanceof StringType || rightType instanceof StringType))
            return convertToString(left) + convertToString(right);

        boolean floating = leftType instanceof FloatType || rightType instanceof FloatType;
        boolean numeric = floating || leftType instanceof IntType || rightType instanceof IntType;
        boolean array = leftType instanceof ArrayType || rightType instanceof ArrayType;

        if (array)
            return arrayOp(operator, left, right, leftType, rightType);
        else if (numeric)
            return numericOp(operator, floating, (Number) left, (Number) right);

        switch (operator) {
            case EQUALITY:
                return leftType.isPrimitive() ? left.equals(right) : left == right;
            case NOT_EQUALS:
                return leftType.isPrimitive() ? !left.equals(right) : left != right;
        }

        throw new Error("should not reach here");
    }

    // ---------------------------------------------------------------------------------------------

    private boolean booleanOp (Object left, Object right, boolean isAnd) {
        return isAnd
            ? (boolean) left && (boolean) right
            : (boolean) left || (boolean) right;
    }

    // ---------------------------------------------------------------------------------------------

    private Object numericOp
        (DiadicOperator operator, boolean floating, Number left, Number right) {
        long ileft, iright;
        double fleft, fright;

        if (floating) {
            fleft = left.doubleValue();
            fright = right.doubleValue();
            ileft = iright = 0;
        } else {
            ileft = left.longValue();
            iright = right.longValue();
            fleft = fright = 0;
        }

        Object result;
        if (floating)
            switch (operator) {
                case MULTIPLY:
                    return fleft * fright;
                case DIVIDE:
                    return fleft / fright;
                case REMAINDER:
                    return fleft % fright;
                case ADD:
                    return fleft + fright;
                case SUBTRACT:
                    return fleft - fright;
                case GREATER:
                    return fleft > fright ? 1.0 : 0.0;
                case LOWER:
                    return fleft < fright ? 1.0 : 0.0;
                case GREATER_EQUAL:
                    return fleft >= fright ? 1.0 : 0.0 ;
                case LOWER_EQUAL:
                    return fleft <= fright ? 1.0 : 0.0;
                case EQUALITY:
                    return fleft == fright ? 1.0 : 0.0;
                case NOT_EQUALS:
                    return fleft != fright ? 1.0 : 0.0;
                default:
                    throw new Error("should not reach here");
            }
        else
            switch (operator) {
                case MULTIPLY:
                    return ileft * iright;
                case DIVIDE:
                    return ileft / iright;
                case REMAINDER:
                    return ileft % iright;
                case ADD:
                    return ileft + iright;
                case SUBTRACT:
                    return ileft - iright;
                case GREATER:
                    return ileft > iright ? 1 : 0;
                case LOWER:
                    return ileft < iright ? 1 : 0;
                case GREATER_EQUAL:
                    return ileft >= iright ? 1 : 0;
                case LOWER_EQUAL:
                    return ileft <= iright ? 1 : 0;
                case EQUALITY:
                    return ileft == iright ? 1 : 0;
                case NOT_EQUALS:
                    return ileft != iright ? 1 : 0;
                default:
                    throw new Error("should not reach here");
            }
    }

    // ---------------------------------------------------------------------------------------------

    public Object assignment (AssignmentNode node) {
        if (node.left instanceof ReferenceNode) {
            Scope scope = reactor.get(node.left, "scope");
            String name = ((ReferenceNode) node.left).name;
            Object rvalue = get(node.right);
            assign(scope, name, rvalue, reactor.get(node, "type"));
            return rvalue;
        }

        if (node.left instanceof ArrayAccessNode) {
            ArrayAccessNode arrayAccess = (ArrayAccessNode) node.left;
            Object[] array = getNonNullArray(arrayAccess.array);
            int index = getIndex(arrayAccess.index);
            try {
                return array[index] = get(node.right);
            } catch (ArrayIndexOutOfBoundsException e) {
                throw new PassthroughException(e);
            }
        }

        if (node.left instanceof FieldAccessNode) {
            FieldAccessNode fieldAccess = (FieldAccessNode) node.left;
            Object object = get(fieldAccess.stem);
            if (object == Null.INSTANCE)
                throw new PassthroughException(
                    new NullPointerException("accessing field of null object"));
            Map<String, Object> struct = cast(object);
            Object right = get(node.right);
            struct.put(fieldAccess.fieldName, right);
            return right;
        }

        throw new Error("should not reach here");
    }

    // ---------------------------------------------------------------------------------------------

    private int getIndex (ExpressionNode node) {
        long index = get(node);
        if (index < 0)
            throw new ArrayIndexOutOfBoundsException("Negative index: " + index);
        if (index >= Integer.MAX_VALUE - 1)
            throw new ArrayIndexOutOfBoundsException("Index exceeds max array index (2ˆ31 - 2): " + index);
        return (int) index;
    }

    // ---------------------------------------------------------------------------------------------

    private Object[] getNonNullArray (ExpressionNode node) {
        Object object = get(node);
        if (object == Null.INSTANCE)
            throw new PassthroughException(new NullPointerException("indexing null array"));
        return (Object[]) object;
    }

    // ---------------------------------------------------------------------------------------------

    private Object monadicExpression (MonadicExpressionNode node) {
        Type opType = reactor.get(node.operand, "type");
        Object operand = get(node.operand);
        MonadicOperator operator = node.operator;
        return monadicExpressionCalculate(opType,operand,operator);
    }
    
    private Object monadicExpressionCalculate(Type opType, Object operand, MonadicOperator operator)
    {
        if(opType instanceof IntType || opType instanceof FloatType)
        {

            return monadicOp(operator,opType,operand);
        }
        else if(opType instanceof ArrayType)
        {
            return monadicOpArray(operator,opType,operand);
        }
        
        throw new Error("should not reach here");
    }

    private Object monadicOp(MonadicOperator operator, Type opType, Object operand)
    {
        boolean floating = opType instanceof FloatType;
        
        
        if(floating){
            double fvalue = (double) operand;
            switch (operator) {
                case NOT:
                    return la_gamma(fvalue);
                case GRAB_LAST:
                    return fvalue;
                case SUM_SLASH:
                    return fvalue;
                case MULT_SLASH:
                    return fvalue;
                case MIN_SLASH:
                    return fvalue;
                case DIV_SLASH:
                    return fvalue;
                case HASHTAG:
                    fvalue = 1;
                    return fvalue;
                default:
                    return fvalue;
            }
        }
        else {
            long lvalue = (long) operand;
            switch ( operator) {
                case NOT:
                    return factorial(lvalue);
                case GRAB_LAST:
                    return lvalue;
                case SUM_SLASH:
                    return lvalue;
                case MULT_SLASH:
                    return lvalue;
                case MIN_SLASH:
                    return lvalue;
                case DIV_SLASH:
                    return lvalue;
                case HASHTAG:
                    lvalue = 1;
                    return lvalue;
                default:
                    return lvalue;
            }
        }
    }
        
    private long factorial(long n)
    {
        if (n == 0) {
            return 1;
        }
        else {
            return (n * factorial(n - 1));
        }
    }

    // factorial of double
    // source : https://rosettacode.org/wiki/Gamma_function#Java
    private double la_gamma(double x) {
        double[] p = {0.99999999999980993, 676.5203681218851, -1259.1392167224028,
            771.32342877765313, -176.61502916214059, 12.507343278686905,
            -0.13857109526572012, 9.9843695780195716e-6, 1.5056327351493116e-7};
        int g = 7;
        if (x < 0.5) return Math.PI / (Math.sin(Math.PI * x) * la_gamma(1 - x));

        double a = p[0];
        double t = x + g + 0.5;
        for (int i = 1; i < p.length; i++) {
            a += p[i] / (x + i);
        }
        return Math.sqrt(2*Math.PI)*Math.pow(t, x+0.5)*Math.exp(-t)*a;
    }



        private Object monadicOpArray(MonadicOperator  operator, Type opType, Object operand)
    {
        boolean floating = ((ArrayType) opType).componentType instanceof FloatType;


        if(floating){
            double[] fvalue = castObjectArrayToDouble((Object[]) operand);
            int length = fvalue.length;
            
            if( operator.equals(MonadicOperator.NOT))
            {
                Object[] result = new Object[length];


                for(int i = 0; i < length; i++)
                {
                    result[i] = la_gamma(fvalue[i]);
                }

                return  result;
            }
            else if ( operator.equals(MonadicOperator.GRAB_LAST))
            {
                return (Object) fvalue[length-1];
            }
            else if (operator.equals(MonadicOperator.HASHTAG))
            {
                return length;
            }
            else {
                for(int i = length-2; i >= 0; i--)
                {
                    if(operator.equals(MonadicOperator.SUM_SLASH))
                        fvalue[i] = fvalue[i] + fvalue[i+1];
                    else  if(operator.equals(MonadicOperator.MULT_SLASH))
                        fvalue[i] = fvalue[i] * fvalue[i+1];
                    else  if(operator.equals(MonadicOperator.DIV_SLASH))
                        fvalue[i] = fvalue[i] / fvalue[i+1];
                    else  if(operator.equals(MonadicOperator.MIN_SLASH))
                        fvalue[i] = fvalue[i] - fvalue[i+1];
                    else
                        fvalue[i] = fvalue[i];

                }
                return (Object) fvalue[0];
            }
        }
        else {
            long[] lvalue = castObjectArrayToLong((Object[]) operand);
            int length = lvalue.length;

            if(operator.equals(MonadicOperator.NOT))
            {
                Object[] result = new Object[length];

                for(int i = 0; i < length; i++)
                {
                    result[i] = factorial(lvalue[i]);
                }
                return  result;
            }
            else if (operator.equals(MonadicOperator.GRAB_LAST))
            {
                return (Object) lvalue[length-1];
            }
            else if (operator.equals(MonadicOperator.HASHTAG))
            {
                return length;
            }
            else {
                for(int i = length-2; i >= 0; i--)
                {
                    if(operator.equals(MonadicOperator.SUM_SLASH))
                            lvalue[i] = lvalue[i] + lvalue[i+1];
                    else  if(operator.equals(MonadicOperator.MULT_SLASH))
                        lvalue[i] = lvalue[i] * lvalue[i+1];
                    else  if(operator.equals(MonadicOperator.DIV_SLASH))
                        lvalue[i] = lvalue[i] / lvalue[i+1];
                    else  if(operator.equals(MonadicOperator.MIN_SLASH))
                        lvalue[i] = lvalue[i] - lvalue[i+1];
                    else
                            lvalue[i] = lvalue[i];
                    
                }
                return (Object) lvalue[0];
            }
        }
    }


    // ---------------------------------------------------------------------------------------------

    private Object arrayAccess (ArrayAccessNode node) {
        Object[] array = getNonNullArray(node.array);
        try {
            return array[getIndex(node.index)];
        } catch (ArrayIndexOutOfBoundsException e) {
            throw new PassthroughException(e);
        }
    }

    // ---------------------------------------------------------------------------------------------

    private Object root (RootNode node) {
        assert storage == null;
        rootScope = reactor.get(node, "scope");
        storage = rootStorage = new ScopeStorage(rootScope, null);
        storage.initRoot(rootScope);

        try {
            node.statements.forEach(this::run);
        } catch (Return r) {
            return r.value;
            // allow returning from the main script
        } finally {
            storage = null;
        }
        return null;
    }

    // ---------------------------------------------------------------------------------------------

    private Void block (BlockNode node) {
        Scope scope = reactor.get(node, "scope");
        storage = new ScopeStorage(scope, storage);
        node.statements.forEach(this::run);
        storage = storage.parent;
        return null;
    }

    // ---------------------------------------------------------------------------------------------

    private Constructor constructor (ConstructorNode node) {
        // guaranteed safe by semantic analysis
        return new Constructor(get(node.ref));
    }

    // ---------------------------------------------------------------------------------------------

    private Object expressionStmt (ExpressionStatementNode node) {
        get(node.expression);
        return null;  // discard value
    }

    // ---------------------------------------------------------------------------------------------

    private Object fieldAccess (FieldAccessNode node) {
        Object stem = get(node.stem);
        if (stem == Null.INSTANCE)
            throw new PassthroughException(
                new NullPointerException("accessing field of null object"));
        return stem instanceof Map
            ? Util.<Map<String, Object>>cast(stem).get(node.fieldName)
            : (long) ((Object[]) stem).length; // only field on arrays
    }

    // ---------------------------------------------------------------------------------------------

    private Object funCall (FunCallNode node) {
        Object decl = get(node.function);
        node.arguments.forEach(this::run);
        Object[] args = map(node.arguments, new Object[0], visitor);

        if (decl == Null.INSTANCE)
            throw new PassthroughException(new NullPointerException("calling a null function"));

        if (decl instanceof SyntheticDeclarationNode)
            return builtin(((SyntheticDeclarationNode) decl).name(), args);

        if (decl instanceof Constructor)
            return buildStruct(((Constructor) decl).declaration, args);

        ScopeStorage oldStorage = storage;
        Scope scope = reactor.get(decl, "scope");
        storage = new ScopeStorage(scope, storage);

        FunDeclarationNode funDecl = (FunDeclarationNode) decl;
        coIterate(args, funDecl.parameters,
            (arg, param) -> storage.set(scope, param.name, arg));

        try {
            get(funDecl.block);
        } catch (Return r) {
            return r.value;
        } finally {
            storage = oldStorage;
        }
        return null;
    }

    // ---------------------------------------------------------------------------------------------

    private Object builtin (String name, Object[] args) {
        assert name.equals("print"); // only one at the moment
        String out = convertToString(args[0]);
        System.out.println(out);
        return out;
    }

    // ---------------------------------------------------------------------------------------------

    private String convertToString (Object arg) {
        if (arg == Null.INSTANCE)
            return "null";
        else if (arg instanceof Object[])
            return Arrays.deepToString((Object[]) arg);
        else if (arg instanceof FunDeclarationNode)
            return ((FunDeclarationNode) arg).name;
        else if (arg instanceof StructDeclarationNode)
            return ((StructDeclarationNode) arg).name;
        else if (arg instanceof Constructor)
            return "$" + ((Constructor) arg).declaration.name;
        else
            return arg.toString();
    }

    // ---------------------------------------------------------------------------------------------

    private HashMap<String, Object> buildStruct (StructDeclarationNode node, Object[] args) {
        HashMap<String, Object> struct = new HashMap<>();
        for (int i = 0; i < node.fields.size(); ++i)
            struct.put(node.fields.get(i).name, args[i]);
        return struct;
    }

    // ---------------------------------------------------------------------------------------------

    private Void ifStmt (IfNode node) {
        if (get(node.condition))
            get(node.trueStatement);
        else if (node.falseStatement != null)
            get(node.falseStatement);
        return null;
    }

    // ---------------------------------------------------------------------------------------------

    private Void whileStmt (WhileNode node) {
        while (get(node.condition))
            get(node.body);
        return null;
    }

    // ---------------------------------------------------------------------------------------------

    private Object reference (ReferenceNode node) {
        Scope scope = reactor.get(node, "scope");
        DeclarationNode decl = reactor.get(node, "decl");

        if (decl instanceof VarDeclarationNode
            || decl instanceof ParameterNode
            || decl instanceof SyntheticDeclarationNode
            && ((SyntheticDeclarationNode) decl).kind() == DeclarationKind.VARIABLE)
            return scope == rootScope
                ? rootStorage.get(scope, node.name)
                : storage.get(scope, node.name);

        return decl; // structure or function
    }

    // ---------------------------------------------------------------------------------------------

    private Void returnStmt (ReturnNode node) {
        throw new Return(node.expression == null ? null : get(node.expression));
    }

    // ---------------------------------------------------------------------------------------------

    private Void varDecl (VarDeclarationNode node) {
        Scope scope = reactor.get(node, "scope");
        assign(scope, node.name, get(node.initializer), reactor.get(node, "type"));
        return null;
    }

    // ---------------------------------------------------------------------------------------------

    private void assign (Scope scope, String name, Object value, Type targetType) {
        if (value instanceof Long && targetType instanceof FloatType)
            value = ((Long) value).doubleValue();
        storage.set(scope, name, value);
    }

    // ------------------------------------ new functions ------------------------------------------

    private Object arrayOp
        (DiadicOperator operator, Object left, Object right, Type leftType, Type rightType) {
        long[] aileft = new long[10000];
        long[] airight = new long[10000];
        double[] afleft = new double[10000];
        double[] afright = new double[10000];
        long ileft = 0;
        long iright = 0;
        double fleft = 0.0;
        double fright = 0.0;

        int llength = 0;
        int rlength = 0;

        boolean isfloat = leftType instanceof FloatType || rightType instanceof FloatType;
        if(leftType instanceof ArrayType){
            if(((ArrayType) leftType).componentType instanceof FloatType){
                isfloat = true;
            }
        }
        if(rightType instanceof ArrayType){
            if(((ArrayType) rightType).componentType instanceof FloatType){
                isfloat = true;
            }
        }
        Object[] aleft = new Object[10000];
        Object[] aright = new Object[10000];

        if(leftType instanceof ArrayType)
            aleft = (Object[]) left;

        if(rightType instanceof ArrayType)
            aright = (Object[]) right;


        // cast object into their values 
        if (leftType instanceof ArrayType) {
            if (((ArrayType) leftType).componentType instanceof IntType) {
                aileft = castObjectArrayToLong(aleft);
                llength = aileft.length;
            } else if (((ArrayType) leftType).componentType instanceof FloatType) {
                afleft = castObjectArrayToDouble(aleft);
                llength = afleft.length;
            }
        } else {
            if (leftType instanceof IntType) {
                ileft = (long) left;
            } else if (leftType instanceof FloatType)
                fleft = (double) left;
        }

        if (rightType instanceof ArrayType) {
            if (((ArrayType) rightType).componentType instanceof IntType) {
                airight = castObjectArrayToLong(aright);
                rlength = airight.length;
            } else if (((ArrayType) rightType).componentType instanceof FloatType) {
                afright = castObjectArrayToDouble(aright);
                rlength = afright.length;
            }
        } else {
            if (rightType instanceof IntType) {
                iright = (long) right;
            } else if (rightType instanceof FloatType)
                fright = (double) right;
        }

        //convert long to double if necessary
        if(isfloat)
        {
            if(leftType instanceof IntType)
            {
                fleft = (double) ileft;
            }
            else if(leftType instanceof  ArrayType)
            {
                if(((ArrayType) leftType).componentType instanceof IntType){
                    afleft = castLongArrayToDouble(aileft);
                }
            }

            if(rightType instanceof IntType)
            {
                fright = (double) iright;
            }
            else if(rightType instanceof  ArrayType)
            {
                if(((ArrayType) rightType).componentType instanceof IntType){
                    afright = castLongArrayToDouble(airight);
                }
            }
        }

        // Check if array have same length
        if (leftType instanceof ArrayType && rightType instanceof ArrayType) {

            if (llength != rlength)
                throw new Error("Length Error. Tried to process 2 arrays of different length.");

        }

        int length = Integer.max(llength, rlength);
        Object Result[] = new Object[length];

        if (leftType instanceof ArrayType && rightType instanceof ArrayType) {
            for (int i = 0; i < length; i++) {
                Object val = getvalDiadicOperation(operator, isfloat, aileft[i], airight[i], afleft[i], afright[i]);
                Result[i] = val;
            }
        }
        else if(leftType instanceof ArrayType)
        {
            for (int i = 0; i < length; i++) {
                Object val = getvalDiadicOperation(operator, isfloat, aileft[i],iright, afleft[i], fright);
                Result[i] = val;
            }
        }
        else{
            for (int i = 0; i < length; i++) {
                Object val = getvalDiadicOperation(operator, isfloat, ileft, airight[i], fleft, afright[i]);
                Result[i] = val;
            }
        }

        return Result;
    }

    private Object getvalDiadicOperation (DiadicOperator operator, Boolean floating, long ileft, long iright, double fleft, double fright) {
        if (floating)
            switch (operator) {
                case MULTIPLY:
                    return fleft * fright;
                case DIVIDE:
                    return fleft / fright;
                case REMAINDER:
                    return fleft % fright;
                case ADD:
                    return fleft + fright;
                case SUBTRACT:
                    return fleft - fright;
                case GREATER:
                    return  fleft > fright ? 1.0 : 0.0;
                case LOWER:
                    return fleft < fright ? 1.0 : 0.0;
                case GREATER_EQUAL:
                    return fleft >= fright ? 1.0 : 0.0;
                case LOWER_EQUAL:
                    return fleft <= fright ? 1.0 : 0.0;
                case EQUALITY:
                    return fleft == fright ? 1.0 : 0.0;
                case NOT_EQUALS:
                    return fleft != fright ? 1.0 : 0.0;
                default:
                    throw new Error("should not reach here");
            }
        else
            switch (operator) {
                case MULTIPLY:
                    return ileft * iright;
                case DIVIDE:
                    return ileft / iright;
                case REMAINDER:
                    return ileft % iright;
                case ADD:
                    return ileft + iright;
                case SUBTRACT:
                    return ileft - iright;
                case GREATER:
                    return  ileft > iright ? 1 : 0;
                case LOWER:
                    return ileft < iright ? 1 : 0;
                case GREATER_EQUAL:
                    return ileft >= iright ? 1 : 0;
                case LOWER_EQUAL:
                    return ileft <= iright ? 1 : 0;
                case EQUALITY:
                    return ileft == iright ? 1 : 0;
                case NOT_EQUALS:
                    return ileft != iright ? 1 : 0;
                default:
                    throw new Error("should not reach here");
            }
    }

    public Long castObjectToLong(Object object) {
        return Long.valueOf(object.toString());
    }

    public long[] castObjectArrayToLong(Object[] objects){
        long[] longs = new long[objects.length];

        for(int i = 0; i < objects.length;i++)
        {
            longs[i] = (long) objects[i];
        }
        return longs;
    }

    public double[] castObjectArrayToDouble(Object[] objects){
        double[] doubles = new double[objects.length];

        for(int i = 0; i < objects.length;i++)
        {
            doubles[i] = (double) objects[i];
        }
        return doubles;
    }

    public double[] castLongArrayToDouble(long[] longs){
        double[] doubles = new double[longs.length];

        for(int i = 0; i < longs.length;i++)
        {
            doubles[i] = (double) longs[i];
        }
        return doubles;
    }

    private Object monadicForkExpression (MonadicForkNode node) {
        Type type = reactor.get(node.operand, "type");
        MonadicOperator operatorL = node.operatorL;
        MonadicOperator operatorR = node.operatorR;
        DiadicOperator operatorM = node.operatorM;
        Object operand = get(node.operand);

        Object left = monadicExpressionCalculate(type,operand,operatorL);
        Type leftType = findObjectType(left);
        Object right = monadicExpressionCalculate(type,operand,operatorR);
        Type rightType = findObjectType(right);
        Object result = diadicExpressionCalculate(leftType,rightType,left,right,operatorM) ;
        return result;
    }

    private Type findObjectType(Object object){
        if(object instanceof Long)
        {
            return IntType.INSTANCE;
        }
        else if (object instanceof Double)
        {
            return FloatType.INSTANCE;
        }
        else if(object instanceof  Object[])
        {
            Object[] object2 = (Object[]) object;
            if(object2[0] instanceof  Long)
            {
                return new  ArrayType(IntType.INSTANCE);
            }
            else if(object2[0] instanceof  Double)
            {
                return new  ArrayType(FloatType.INSTANCE);
            }
        }

        throw new Error("should not reach here. Didnt find class of object "+ object.getClass().getCanonicalName());
    }

    private Object diadicForkExpression (DiadicForkNode node) {
        Type typeLeft = reactor.get(node.operandL, "type");
        Type typeRight = reactor.get(node.operandR,"type");
        DiadicOperator operatorL = node.operatorL;
        DiadicOperator operatorR = node.operatorR;
        DiadicOperator operatorM = node.operatorM;
        Object operandL = get(node.operandL);
        Object operandR = get(node.operandR);

        Object left = diadicExpressionCalculate(typeLeft,typeRight,operandL,operandR,operatorL);
        Type leftType = findObjectType(left);
        Object right = diadicExpressionCalculate(typeLeft,typeRight,operandL,operandR,operatorR);
        Type rightType = findObjectType(right);
        Object result = diadicExpressionCalculate(leftType,rightType,left,right,operatorM) ;
        return result;
    }
}
