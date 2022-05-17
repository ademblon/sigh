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

    // --------------------------------- modified functions ----------------------------------------


    private Void ifStmt (IfNode node) {
        Object cond = get(node.condition);
        Type condType = reactor.get(node.condition, "type");
        if(condType instanceof IntType)
        {
            if ((long) get(node.condition) != 0)
                get(node.trueStatement);
            else if (node.falseStatement != null)
                get(node.falseStatement);
        }
        else if(condType instanceof FloatType)
        {
            if ((double) get(node.condition) != 0.0)
                get(node.trueStatement);
            else if (node.falseStatement != null)
                get(node.falseStatement);
        }
        else if(((ArrayType) condType).componentType instanceof IntType)
        {
            if ( (long) ((Object[]) get(node.condition))[0] != 0)
                get(node.trueStatement);
            else if (node.falseStatement != null)
                get(node.falseStatement);
        }
        else
        {
            if ( (double) ((Object[]) get(node.condition))[0] != 0.0)
                get(node.trueStatement);
            else if (node.falseStatement != null)
                get(node.falseStatement);
        }

        return null;
    }

    private Void whileStmt (WhileNode node) {
        Object cond = get(node.condition);
        Type condType = reactor.get(node.condition, "type");
        if(condType instanceof IntType)
        {
            while ((long) get(node.condition) != 0)
                get(node.body);
        }
        else
        {
            while ((double) get(node.condition) != 0.0)
                get(node.body);
        }

        return null;
    }

    // ------------------ new functions ------------------------------------------------------------

    // ------------------------------------- Math function -----------------------------------------

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

    // ---------------------------------Type Conversion function -----------------------------------
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
        if (operator == DiadicOperator.ADD && (leftType instanceof StringType || rightType instanceof StringType))
            return convertToString(left) + convertToString(right);

        boolean floating = istypefloat(leftType) || istypefloat(rightType);
        boolean numeric = (leftType instanceof FloatType || leftType instanceof IntType) && (rightType instanceof FloatType || rightType instanceof IntType);
        boolean array = leftType instanceof ArrayType || rightType instanceof ArrayType;

        if (array)
            return arrayOp(operator, left, right, leftType, rightType,floating);
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

    private boolean oropdouble(double left, double right){
        return left == 0.0 && right == 0.0;
    }

    private boolean oroplong(long left, long right){
        return left == 0 && right == 0;
    }

    private boolean andopdouble(double left, double right){
        return left != 0.0 && right != 0.0;
    }

    private boolean andoplong(long left, long right){
        return left != 0 && right != 0;
    }


    // ---------------------------------------------------------------------------------------------

    private Object numericOp
        (DiadicOperator operator, boolean floating, Number left, Number right) {
        long ileft, iright;
        double fleft, fright;

        fleft = left.doubleValue();
        fright = right.doubleValue();

        ileft = left.longValue();
        iright = right.longValue();

        Object result;
        return getvalDiadicOperation(operator,floating,ileft,iright,fleft,fright);
    }



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
        double fvalue = 1.0;
        long lvalue = 1;

        if(floating)
            fvalue = (double) operand;
        else
            lvalue = (long) operand;

        switch (operator) {
            case NOT:
                return floating ? (Object) la_gamma(fvalue) : (Object) factorial(lvalue);
            case GRAB_LAST:
                return floating ? (Object) fvalue : (Object) lvalue;
            case SUM_SLASH:
                return floating ? (Object) fvalue : (Object) lvalue;
            case MULT_SLASH:
                return floating ? (Object) fvalue : (Object) lvalue;
            case MIN_SLASH:
                return floating ? (Object) fvalue : (Object) lvalue;
            case DIV_SLASH:
                return floating ? (Object) fvalue : (Object) lvalue;
            case SELF_ADD:
                return floating ? (Object) (fvalue + fvalue) : (Object) (lvalue + lvalue);
            case SELF_MULT:
                return floating ?  (Object) (fvalue * fvalue) : (Object) (lvalue * lvalue);
            case HASHTAG:
                return floating ? (Object) 1.0 : (Object) ((long) 1);
            default:
                return floating ? (Object) fvalue : (Object) lvalue;
        }
    }

    private Object monadicOpArray(MonadicOperator  operator, Type opType, Object operand)
    {
        boolean floating = ((ArrayType) opType).componentType instanceof FloatType;
        double[] fvalue = {0};
        long[] lvalue = {0};
        int length;
        if(floating) {
            fvalue = castObjectArrayToDouble((Object[]) operand);
            length = fvalue.length;
        }
        else {
            lvalue = castObjectArrayToLong((Object[]) operand);
            length =  lvalue.length;
        }
        Object[] result = new Object[length];

        if( operator.equals(MonadicOperator.NOT)|| operator.equals(MonadicOperator.SELF_ADD) || operator.equals(MonadicOperator.SELF_MULT))
        {
            for(int i = 0; i < length; i++)
            {
                if(operator.equals(MonadicOperator.NOT))
                    result[i] = floating ? (Object) la_gamma(fvalue[i]) : (Object) factorial(lvalue[i]);
                else if(operator.equals(MonadicOperator.SELF_ADD))
                    result[i] = floating ? (Object) (fvalue[i] + fvalue[i]) : (Object) (lvalue[i] + lvalue[i]);
                else // operator.equals(MonadicOperator.SELF_MULT)
                    result[i] = floating ? (Object) (fvalue[i] * fvalue[i]) : (Object) (lvalue[i] * lvalue[i]);
            }
            return  result;
        }
        else if ( operator.equals(MonadicOperator.GRAB_LAST))
        {
            return floating ? (Object) (fvalue[length-1]) : (Object) (lvalue[length-1]) ;
        }
        else if (operator.equals(MonadicOperator.HASHTAG))
        {
            return length;
        }
        else {
            if(floating) {
                for (int i = length - 2; i >= 0; i--) {
                    if (operator.equals(MonadicOperator.SUM_SLASH))
                        fvalue[i] = fvalue[i] + fvalue[i + 1];
                    else if (operator.equals(MonadicOperator.MULT_SLASH))
                        fvalue[i] = fvalue[i] * fvalue[i + 1];
                    else if (operator.equals(MonadicOperator.DIV_SLASH))
                        fvalue[i] = fvalue[i] / fvalue[i + 1];
                    else if (operator.equals(MonadicOperator.MIN_SLASH))
                        fvalue[i] = fvalue[i] - fvalue[i + 1];
                    else
                        fvalue[i] = fvalue[i];

                }
                return (Object) fvalue[0];
            }
            else
            {
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

    private boolean istypefloat(Type type)
    {
        boolean isfloat = type instanceof FloatType;
        if(type instanceof ArrayType){
            if(((ArrayType) type).componentType instanceof FloatType){
                isfloat = true;
            }
        }
        return isfloat;
    }

    private Object arrayOp
        (DiadicOperator operator, Object left, Object right, Type leftType, Type rightType, boolean floating) {
        long[] aileft = new long[10000];
        long[] airight = new long[10000];
        double[] afleft = new double[10000];
        double[] afright = new double[10000];
        long ileft = 0;
        long iright = 0;
        double fleft = 0.0;
        double fright = 0.0;

        int llength = 1;
        int rlength = 1;

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
                afleft = castLongArrayToDouble(aileft);
                llength = aileft.length;
            } else if (((ArrayType) leftType).componentType instanceof FloatType) {
                afleft = castObjectArrayToDouble(aleft);
                llength = afleft.length;
            }
        } else {
            if (leftType instanceof IntType) {
                ileft = (long) left;
                fleft = (double) ileft;
            } else if (leftType instanceof FloatType)
                fleft = (double) left;
        }

        if (rightType instanceof ArrayType) {
            if (((ArrayType) rightType).componentType instanceof IntType) {
                airight = castObjectArrayToLong(aright);
                afright = castLongArrayToDouble(airight);
                rlength = airight.length;
            } else if (((ArrayType) rightType).componentType instanceof FloatType) {
                afright = castObjectArrayToDouble(aright);
                rlength = afright.length;
            }
        } else {
            if (rightType instanceof IntType) {
                iright = (long) right;
                fright = (double) right;
            } else if (rightType instanceof FloatType)
                fright = (double) right;
        }


        // Check if array have same length
        if (leftType instanceof ArrayType && rightType instanceof ArrayType) {

            if (llength != rlength)
                throw new Error("Length Error. Tried to process 2 arrays of different length.");

        }
        Object Result[];
        if (operator == DiadicOperator.CONCAT){
            int length = llength + rlength;
            Result = new Object[length];

            int pointer = 0;

            if(leftType instanceof ArrayType)
            {
                for (int i = 0; i < llength; i++) {
                    if (floating)
                        Result[i] = afleft[i];
                    else
                        Result[i] = aileft[i];

                    pointer++;
                }
            }
            else
            {
                Result[0] = floating ? (Object) fleft : (Object) ileft;
                pointer = 1;
            }

            if(rightType instanceof ArrayType)
            {
                for (int i = 0; i < rlength; i++) {
                    if (floating)
                        Result[i+pointer] = afright[i];
                    else
                        Result[i+pointer] = airight[i];
                }
            }
            else
            {
                Result[pointer] = floating ? (Object) fright : (Object) iright;
            }
        }
        else {
            int length = Integer.max(llength, rlength);
            Result = new Object[length];

            if (leftType instanceof ArrayType && rightType instanceof ArrayType) {
                for (int i = 0; i < length; i++) {
                    Object val = getvalDiadicOperation(operator, floating, aileft[i], airight[i], afleft[i], afright[i]);
                    Result[i] = val;
                }
            } else if (leftType instanceof ArrayType) {
                for (int i = 0; i < length; i++) {
                    Object val = getvalDiadicOperation(operator, floating, aileft[i], iright, afleft[i], fright);
                    Result[i] = val;
                }
            } else {
                for (int i = 0; i < length; i++) {
                    Object val = getvalDiadicOperation(operator, floating, ileft, airight[i], fleft, afright[i]);
                    Result[i] = val;
                }
            }
        }
        return Result;
    }

    private Object getvalDiadicOperation (DiadicOperator operator, Boolean floating, long ileft, long iright, double fleft, double fright) {
        switch (operator) {
            case MULTIPLY:
                return floating ? (Object) (fleft * fright) : (Object) (ileft * iright);
            case DIVIDE:
                return floating ? (Object) (fleft / fright) : (Object) (ileft / iright);
            case REMAINDER:
                return floating ? (Object) (fleft % fright) : (Object) (ileft % iright);
            case ADD:
                return floating ? (Object) (fleft + fright) : (Object) (ileft + iright);
            case SUBTRACT:
                return floating ? (Object) (fleft - fright) : (Object) (ileft - iright);
            case EXPONENT:
                return floating ? (Object) Math.pow(fleft, fright) : (Object) ((long) Math.pow(ileft, iright));
            case GREATER:
                return floating ? (Object) (fleft > fright ? 1.0 : 0.0) : (Object) (ileft > iright ? (long) 1 : (long) 0);
            case LOWER:
                return floating ? (Object) (fleft < fright ? 1.0 : 0.0) : (Object) (ileft < iright ? (long) 1 : (long) 0);
            case GREATER_EQUAL:
                return floating ? (Object) (fleft >= fright ? 1.0 : 0.0) : (Object) (ileft >= iright ? (long) 1 : (long) 0);
            case LOWER_EQUAL:
                return floating ? (Object) (fleft <= fright ? 1.0 : 0.0) : (Object) (ileft <= iright ? (long) 1 : (long) 0);
            case EQUALITY:
                return floating ? (Object) (fleft == fright ? 1.0 : 0.0) : (Object) (ileft == iright ? (long) 1 : (long) 0);
            case NOT_EQUALS:
                return floating ? (Object) (fleft != fright ? 1.0 : 0.0) : (Object) (ileft != iright ? (long) 1 : (long) 0);
            case OR:
                return floating ? (Object) (oropdouble(fleft,fright) ? 0.0 : 1.0) : (Object) (oroplong(ileft,iright) ? (long) 0 : (long) 1);
            case AND:
                return floating ? (Object) (andopdouble(fleft,fright) ? 1.0 : 0.0) : (Object) (andoplong(ileft,iright) ? (long) 1 : (long) 0);
            case CONCAT: //only used in numeric op == int/float <> int/float, no  array involved
                return floating ? new Object[]{fleft, fright} : new Object[]{ileft, iright};
            default:
                throw new Error("should not reach here");
        }
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
