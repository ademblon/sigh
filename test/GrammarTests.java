import norswap.autumn.AutumnTestFixture;
import norswap.sigh.SighGrammar;
import norswap.sigh.ast.*;
import org.testng.annotations.Test;

import static java.util.Arrays.asList;
import static norswap.sigh.ast.DiadicOperator.*;
import static norswap.sigh.ast.MonadicOperator.*;

public class GrammarTests extends AutumnTestFixture {
    // ---------------------------------------------------------------------------------------------

    private final SighGrammar grammar = new SighGrammar();
    private final Class<?> grammarClass = grammar.getClass();

    // ---------------------------------------------------------------------------------------------

    private static IntLiteralNode intlit (long i) {
        return new IntLiteralNode(null, i);
    }

    private static FloatLiteralNode floatlit (double d) {
        return new FloatLiteralNode(null, d);
    }


    // ---------------------------------------------------------------------------------------------

    @Test
    public void testLiteralsAndUnary () {
        rule = grammar.expression;

        successExpect("42", intlit(42));
        successExpect("42.0", floatlit(42d));
        successExpect("\"hello\"", new StringLiteralNode(null, "hello"));
        successExpect("(42)", new ParenthesizedNode(null, intlit(42)));
        successExpect("(42 + 3)", new ParenthesizedNode(null, new DiadicExpressionNode(null, intlit(42), ADD, intlit(3))));
        successExpect("5 + (42 + 3)", new DiadicExpressionNode(null, intlit(5), ADD, new ParenthesizedNode(null, new DiadicExpressionNode(null, intlit(42), ADD, intlit(3)))));

        successExpect("[1, 2, 3]", new ArrayLiteralNode(null, asList(intlit(1), intlit(2), intlit(3))));
        successExpect("[[1, 2, 3],[1, 2]]", new ArrayLiteralNode(null, asList(new ArrayLiteralNode(null, asList(intlit(1), intlit(2), intlit(3))), new ArrayLiteralNode(null, asList(intlit(1), intlit(2))))));
        successExpect("null", new ReferenceNode(null, "null"));
    }

    // ---------------------------------------------------------------------------------------------

    @Test
    public void testNumericBinary () {
        rule = grammar.expression;
        successExpect("1 + 2", new DiadicExpressionNode(null, intlit(1), ADD, intlit(2)));
        successExpect("2 - 1", new DiadicExpressionNode(null, intlit(2), SUBTRACT,  intlit(1)));
        successExpect("2 * 3", new DiadicExpressionNode(null, intlit(2), MULTIPLY, intlit(3)));
        successExpect("2 / 3", new DiadicExpressionNode(null, intlit(2), DIVIDE, intlit(3)));
        successExpect("2 % 3", new DiadicExpressionNode(null, intlit(2), REMAINDER, intlit(3)));
        successExpect("2 ^ 3", new DiadicExpressionNode(null, intlit(2), EXPONENT, intlit(3)));
        successExpect("2 <> 3", new DiadicExpressionNode(null, intlit(2), CONCAT, intlit(3)));

        successExpect("1.0 + 2.0", new DiadicExpressionNode(null, floatlit(1), ADD, floatlit(2)));
        successExpect("2.0 - 1.0", new DiadicExpressionNode(null, floatlit(2), SUBTRACT, floatlit(1)));
        successExpect("2.0 * 3.0", new DiadicExpressionNode(null, floatlit(2), MULTIPLY, floatlit(3)));
        successExpect("2.0 / 3.0", new DiadicExpressionNode(null, floatlit(2), DIVIDE, floatlit(3)));
        successExpect("2.0 % 3.0", new DiadicExpressionNode(null, floatlit(2), REMAINDER, floatlit(3)));
        successExpect("2.0 ^ 3.0", new DiadicExpressionNode(null, floatlit(2), EXPONENT, floatlit(3)));
        successExpect("2.0 <> 3.0", new DiadicExpressionNode(null, floatlit(2), CONCAT, floatlit(3)));

        successExpect("1.0 + 2", new DiadicExpressionNode(null, floatlit(1), ADD, intlit(2)));
        successExpect("2.0 - 1", new DiadicExpressionNode(null, floatlit(2), SUBTRACT, intlit(1)));
        successExpect("2.0 * 3", new DiadicExpressionNode(null, floatlit(2), MULTIPLY, intlit(3)));
        successExpect("2.0 / 3", new DiadicExpressionNode(null, floatlit(2), DIVIDE, intlit(3)));
        successExpect("2.0 % 3", new DiadicExpressionNode(null, floatlit(2), REMAINDER, intlit(3)));
        successExpect("2.0 ^ 3", new DiadicExpressionNode(null, floatlit(2), EXPONENT, intlit(3)));
        successExpect("2.0 <> 3", new DiadicExpressionNode(null, floatlit(2), CONCAT, intlit(3)));

        successExpect("1 + 2.0", new DiadicExpressionNode(null, intlit(1), ADD, floatlit(2)));
        successExpect("2 - 1.0", new DiadicExpressionNode(null, intlit(2), SUBTRACT, floatlit(1)));
        successExpect("2 * 3.0", new DiadicExpressionNode(null, intlit(2), MULTIPLY, floatlit(3)));
        successExpect("2 / 3.0", new DiadicExpressionNode(null, intlit(2), DIVIDE, floatlit(3)));
        successExpect("2 % 3.0", new DiadicExpressionNode(null, intlit(2), REMAINDER, floatlit(3)));
        successExpect("2 ^ 3.0", new DiadicExpressionNode(null, intlit(2), EXPONENT, floatlit(3)));
        successExpect("2 <> 3.0", new DiadicExpressionNode(null, intlit(2), CONCAT, floatlit(3)));
    }

    // ---------------------------------------------------------------------------------------------

    @Test public void testArrayStructAccess () {
        rule = grammar.expression;
        successExpect("[1][0]", new ArrayAccessNode(null,
            new ArrayLiteralNode(null, asList(intlit(1))), intlit(0)));
        successExpect("[1].length", new FieldAccessNode(null,
            new ArrayLiteralNode(null, asList(intlit(1))), "length"));
        successExpect("p.x", new FieldAccessNode(null, new ReferenceNode(null, "p"), "x"));
    }

    // ---------------------------------------------------------------------------------------------

    @Test public void testDeclarations() {
        rule = grammar.statement;

        successExpect("var x: Int = 1", new VarDeclarationNode(null,
            "x", new SimpleTypeNode(null, "Int"), intlit(1)));

        successExpect("struct P {}", new StructDeclarationNode(null, "P", asList()));

        successExpect("struct P { var x: Int; var y: Int }",
            new StructDeclarationNode(null, "P", asList(
                new FieldDeclarationNode(null, "x", new SimpleTypeNode(null, "Int")),
                new FieldDeclarationNode(null, "y", new SimpleTypeNode(null, "Int")))));

        successExpect("fun f (x: Int): Int { return 1 }",
            new FunDeclarationNode(null, "f",
                asList(new ParameterNode(null, "x", new SimpleTypeNode(null, "Int"))),
                new SimpleTypeNode(null, "Int"),
                new BlockNode(null, asList(new ReturnNode(null, intlit(1))))));
    }

    // ---------------------------------------------------------------------------------------------

    @Test public void testStatements() {
        rule = grammar.statement;

        successExpect("return", new ReturnNode(null, null));
        successExpect("return 1", new ReturnNode(null, intlit(1)));
        successExpect("print(1)", new ExpressionStatementNode(null,
            new FunCallNode(null, new ReferenceNode(null, "print"), asList(intlit(1)))));
        successExpect("{ return }", new BlockNode(null, asList(new ReturnNode(null, null))));


        successExpect("if true return 1 else return 2", new IfNode(null, new ReferenceNode(null, "true"),
            new ReturnNode(null, intlit(1)),
            new ReturnNode(null, intlit(2))));

        successExpect("if false return 1 else if true return 2 else return 3 ",
            new IfNode(null, new ReferenceNode(null, "false"),
                new ReturnNode(null, intlit(1)),
                new IfNode(null, new ReferenceNode(null, "true"),
                    new ReturnNode(null, intlit(2)),
                    new ReturnNode(null, intlit(3)))));

        successExpect("while 1 < 2 { return } ", new WhileNode(null,
            new DiadicExpressionNode(null, intlit(1), LOWER, intlit(2)),
            new BlockNode(null, asList(new ReturnNode(null, null)))));
    }

    //------------------------------------- new tests ----------------------------------------------

    @Test public void testOrderRightLeftBinary() {
        rule = grammar.expression;
        successExpect("1.0 + 2.0 + 3.0", new DiadicExpressionNode(null, floatlit(1), ADD,new DiadicExpressionNode(null, floatlit(2), ADD, floatlit(3))));
        successExpect("1.0 - 2.0 - 3.0", new DiadicExpressionNode(null, floatlit(1), SUBTRACT,new DiadicExpressionNode(null, floatlit(2), SUBTRACT, floatlit(3))));
        successExpect("1.0 * 2.0 * 3.0", new DiadicExpressionNode(null, floatlit(1), MULTIPLY,new DiadicExpressionNode(null, floatlit(2), MULTIPLY, floatlit(3))));
        successExpect("1.0 / 2.0 / 3.0", new DiadicExpressionNode(null, floatlit(1), DIVIDE,new DiadicExpressionNode(null, floatlit(2), DIVIDE, floatlit(3))));
        successExpect("1.0 % 2.0 % 3.0", new DiadicExpressionNode(null, floatlit(1), REMAINDER,new DiadicExpressionNode(null, floatlit(2), REMAINDER, floatlit(3))));


        successExpect("1 + 2 + 3", new DiadicExpressionNode(null, intlit(1), ADD,new DiadicExpressionNode(null, intlit(2), ADD, intlit(3))));
        successExpect("1 - 2 - 3", new DiadicExpressionNode(null, intlit(1), SUBTRACT,new DiadicExpressionNode(null,intlit(2), SUBTRACT, intlit(3))));
        successExpect("1 * 2 * 3", new DiadicExpressionNode(null, intlit(1), MULTIPLY,new DiadicExpressionNode(null, intlit(2), MULTIPLY, intlit(3))));
        successExpect("1 / 2 / 3", new DiadicExpressionNode(null, intlit(1), DIVIDE,new DiadicExpressionNode(null, intlit(2), DIVIDE, intlit(3))));
        successExpect("1 % 2 % 3", new DiadicExpressionNode(null, intlit(1), REMAINDER,new DiadicExpressionNode(null, intlit(2), REMAINDER, intlit(3))));
    }

    @Test public void testMathPriority() {
        rule = grammar.expression;
        successExpect("1.0 + 2.0 * 3.0", new DiadicExpressionNode(null, floatlit(1), ADD,new DiadicExpressionNode(null, floatlit(2), MULTIPLY, floatlit(3))));
        successExpect("1.0 * 2.0 - 3.0", new DiadicExpressionNode(null, floatlit(1), MULTIPLY,new DiadicExpressionNode(null, floatlit(2), SUBTRACT, floatlit(3))));
        successExpect("1.0 / 2.0 * 3.0", new DiadicExpressionNode(null, floatlit(1), DIVIDE,new DiadicExpressionNode(null, floatlit(2), MULTIPLY, floatlit(3))));
        successExpect("1.0 + 2.0 / 3.0", new DiadicExpressionNode(null, floatlit(1), ADD,new DiadicExpressionNode(null, floatlit(2), DIVIDE, floatlit(3))));
        successExpect("1.0 % 2.0 - 3.0", new DiadicExpressionNode(null, floatlit(1), REMAINDER,new DiadicExpressionNode(null, floatlit(2), SUBTRACT, floatlit(3))));


        successExpect("1 % 2 + 3", new DiadicExpressionNode(null, intlit(1), REMAINDER,new DiadicExpressionNode(null, intlit(2), ADD, intlit(3))));
        successExpect("1 + 2 * 3", new DiadicExpressionNode(null, intlit(1), ADD,new DiadicExpressionNode(null,intlit(2), MULTIPLY, intlit(3))));
        successExpect("1 * 2 % 3", new DiadicExpressionNode(null, intlit(1), MULTIPLY,new DiadicExpressionNode(null, intlit(2), REMAINDER, intlit(3))));
        successExpect("1 - 2 / 3", new DiadicExpressionNode(null, intlit(1), SUBTRACT,new DiadicExpressionNode(null, intlit(2), DIVIDE, intlit(3))));
        successExpect("1 * 2 % 3", new DiadicExpressionNode(null, intlit(1), MULTIPLY,new DiadicExpressionNode(null, intlit(2), REMAINDER, intlit(3))));
    }

    @Test public void testBinaryIntOpArray() {
        rule = grammar.expression;
        successExpect("1 + [2]", new DiadicExpressionNode(null, intlit(1), ADD, new ArrayLiteralNode(null, asList(intlit(2)))));
        successExpect("1 + [1, 2, 3]", new DiadicExpressionNode(null, intlit(1), ADD, new ArrayLiteralNode(null, asList(intlit(1),intlit(2),intlit(3)))));
        successExpect("1 * [2]", new DiadicExpressionNode(null, intlit(1), MULTIPLY, new ArrayLiteralNode(null, asList(intlit(2)))));
        successExpect("1 * [1, 2, 3]", new DiadicExpressionNode(null, intlit(1), MULTIPLY, new ArrayLiteralNode(null, asList(intlit(1),intlit(2),intlit(3)))));
        successExpect("1 - [2]", new DiadicExpressionNode(null, intlit(1), SUBTRACT, new ArrayLiteralNode(null, asList(intlit(2)))));
        successExpect("1 - [1, 2, 3]", new DiadicExpressionNode(null, intlit(1), SUBTRACT, new ArrayLiteralNode(null, asList(intlit(1),intlit(2),intlit(3)))));
        successExpect("1 / [2]", new DiadicExpressionNode(null, intlit(1), DIVIDE, new ArrayLiteralNode(null, asList(intlit(2)))));
        successExpect("1 / [1, 2, 3]", new DiadicExpressionNode(null, intlit(1), DIVIDE, new ArrayLiteralNode(null, asList(intlit(1),intlit(2),intlit(3)))));
        successExpect("1 % [2]", new DiadicExpressionNode(null, intlit(1), REMAINDER, new ArrayLiteralNode(null, asList(intlit(2)))));
        successExpect("1 % [1, 2, 3]", new DiadicExpressionNode(null, intlit(1), REMAINDER, new ArrayLiteralNode(null, asList(intlit(1),intlit(2),intlit(3)))));
        successExpect("1 ^ [2]", new DiadicExpressionNode(null, intlit(1), EXPONENT, new ArrayLiteralNode(null, asList(intlit(2)))));
        successExpect("1 ^ [1, 2, 3]", new DiadicExpressionNode(null, intlit(1), EXPONENT, new ArrayLiteralNode(null, asList(intlit(1),intlit(2),intlit(3)))));
        successExpect("1 <> [2]", new DiadicExpressionNode(null, intlit(1), CONCAT, new ArrayLiteralNode(null, asList(intlit(2)))));
        successExpect("1 <> [1, 2, 3]", new DiadicExpressionNode(null, intlit(1), CONCAT, new ArrayLiteralNode(null, asList(intlit(1),intlit(2),intlit(3)))));
        successExpect("1 < [2]", new DiadicExpressionNode(null, intlit(1), LOWER, new ArrayLiteralNode(null, asList(intlit(2)))));
        successExpect("1 < [1, 2, 3]", new DiadicExpressionNode(null, intlit(1), LOWER, new ArrayLiteralNode(null, asList(intlit(1),intlit(2),intlit(3)))));
        successExpect("1 > [2]", new DiadicExpressionNode(null, intlit(1), GREATER, new ArrayLiteralNode(null, asList(intlit(2)))));
        successExpect("1 > [1, 2, 3]", new DiadicExpressionNode(null, intlit(1), GREATER, new ArrayLiteralNode(null, asList(intlit(1),intlit(2),intlit(3)))));
        successExpect("1 <= [2]", new DiadicExpressionNode(null, intlit(1), LOWER_EQUAL, new ArrayLiteralNode(null, asList(intlit(2)))));
        successExpect("1 <= [1, 2, 3]", new DiadicExpressionNode(null, intlit(1), LOWER_EQUAL, new ArrayLiteralNode(null, asList(intlit(1),intlit(2),intlit(3)))));
        successExpect("1 >= [2]", new DiadicExpressionNode(null, intlit(1), GREATER_EQUAL, new ArrayLiteralNode(null, asList(intlit(2)))));
        successExpect("1 >= [1, 2, 3]", new DiadicExpressionNode(null, intlit(1), GREATER_EQUAL, new ArrayLiteralNode(null, asList(intlit(1),intlit(2),intlit(3)))));
    }

    @Test public void testBinaryDoubleOpArray() {
        rule = grammar.expression;
        successExpect("1.0 + [2.0]", new DiadicExpressionNode(null, floatlit(1), ADD, new ArrayLiteralNode(null, asList(floatlit(2)))));
        successExpect("1.0 + [1.0, 2.0, 3.0]", new DiadicExpressionNode(null, floatlit(1), ADD, new ArrayLiteralNode(null, asList(floatlit(1),floatlit(2),floatlit(3)))));
        successExpect("1.0 * [2.0]", new DiadicExpressionNode(null, floatlit(1), MULTIPLY, new ArrayLiteralNode(null, asList(floatlit(2)))));
        successExpect("1.0 * [1.0, 2.0, 3.0]", new DiadicExpressionNode(null, floatlit(1), MULTIPLY, new ArrayLiteralNode(null, asList(floatlit(1),floatlit(2),floatlit(3)))));
        successExpect("1.0 - [2.0]", new DiadicExpressionNode(null, floatlit(1), SUBTRACT, new ArrayLiteralNode(null, asList(floatlit(2)))));
        successExpect("1.0 - [1.0, 2.0, 3.0]", new DiadicExpressionNode(null, floatlit(1), SUBTRACT, new ArrayLiteralNode(null, asList(floatlit(1),floatlit(2),floatlit(3)))));
        successExpect("1.0 / [2.0]", new DiadicExpressionNode(null, floatlit(1), DIVIDE, new ArrayLiteralNode(null, asList(floatlit(2)))));
        successExpect("1.0 / [1.0, 2.0, 3.0]", new DiadicExpressionNode(null, floatlit(1), DIVIDE, new ArrayLiteralNode(null, asList(floatlit(1),floatlit(2),floatlit(3)))));
        successExpect("1.0 % [2.0]", new DiadicExpressionNode(null, floatlit(1), REMAINDER, new ArrayLiteralNode(null, asList(floatlit(2)))));
        successExpect("1.0 % [1.0, 2.0, 3.0]", new DiadicExpressionNode(null, floatlit(1), REMAINDER, new ArrayLiteralNode(null, asList(floatlit(1),floatlit(2),floatlit(3)))));
        successExpect("1.0 ^ [2.0]", new DiadicExpressionNode(null, floatlit(1), EXPONENT, new ArrayLiteralNode(null, asList(floatlit(2)))));
        successExpect("1.0 ^ [1.0, 2.0, 3.0]", new DiadicExpressionNode(null, floatlit(1), EXPONENT, new ArrayLiteralNode(null, asList(floatlit(1),floatlit(2),floatlit(3)))));
        successExpect("1.0 <> [2.0]", new DiadicExpressionNode(null, floatlit(1), CONCAT, new ArrayLiteralNode(null, asList(floatlit(2)))));
        successExpect("1.0 <> [1.0, 2.0, 3.0]", new DiadicExpressionNode(null, floatlit(1), CONCAT, new ArrayLiteralNode(null, asList(floatlit(1),floatlit(2),floatlit(3)))));
        successExpect("1.0 < [2.0]", new DiadicExpressionNode(null, floatlit(1), LOWER, new ArrayLiteralNode(null, asList(floatlit(2)))));
        successExpect("1.0 < [1.0, 2.0, 3.0]", new DiadicExpressionNode(null, floatlit(1), LOWER, new ArrayLiteralNode(null, asList(floatlit(1),floatlit(2),floatlit(3)))));
        successExpect("1.0 > [2.0]", new DiadicExpressionNode(null, floatlit(1), GREATER, new ArrayLiteralNode(null, asList(floatlit(2)))));
        successExpect("1.0 > [1.0, 2.0, 3.0]", new DiadicExpressionNode(null, floatlit(1), GREATER, new ArrayLiteralNode(null, asList(floatlit(1),floatlit(2),floatlit(3)))));
        successExpect("1.0 <= [2.0]", new DiadicExpressionNode(null, floatlit(1), LOWER_EQUAL, new ArrayLiteralNode(null, asList(floatlit(2)))));
        successExpect("1.0 <= [1.0, 2.0, 3.0]", new DiadicExpressionNode(null, floatlit(1), LOWER_EQUAL, new ArrayLiteralNode(null, asList(floatlit(1),floatlit(2),floatlit(3)))));
        successExpect("1.0 >= [2.0]", new DiadicExpressionNode(null, floatlit(1), GREATER_EQUAL, new ArrayLiteralNode(null, asList(floatlit(2)))));
        successExpect("1.0 >= [1.0, 2.0, 3.0]", new DiadicExpressionNode(null, floatlit(1), GREATER_EQUAL, new ArrayLiteralNode(null, asList(floatlit(1),floatlit(2),floatlit(3)))));
    }

    @Test public void testBinaryDoubleArrayOpArray() {
        rule = grammar.expression;
        successExpect("[1.0] + [3.0]", new DiadicExpressionNode(null,new ArrayLiteralNode(null, asList(floatlit(1))),ADD,new ArrayLiteralNode(null, asList(floatlit(3)))));
        successExpect("[1.0, 2.0, 3.0] + [4.0, 5.0, 6.0]", new DiadicExpressionNode(null,new ArrayLiteralNode(null, asList(floatlit(1),floatlit(2),floatlit(3))),ADD,new ArrayLiteralNode(null, asList(floatlit(4),floatlit(5),floatlit(6)))));
        successExpect("[1.0] - [3.0]", new DiadicExpressionNode(null,new ArrayLiteralNode(null, asList(floatlit(1))),SUBTRACT,new ArrayLiteralNode(null, asList(floatlit(3)))));
        successExpect("[1.0, 2.0, 3.0] - [4.0, 5.0, 6.0]", new DiadicExpressionNode(null,new ArrayLiteralNode(null, asList(floatlit(1),floatlit(2),floatlit(3))),SUBTRACT,new ArrayLiteralNode(null, asList(floatlit(4),floatlit(5),floatlit(6)))));
        successExpect("[1.0] * [3.0]", new DiadicExpressionNode(null,new ArrayLiteralNode(null, asList(floatlit(1))),MULTIPLY,new ArrayLiteralNode(null, asList(floatlit(3)))));
        successExpect("[1.0, 2.0, 3.0] * [4.0, 5.0, 6.0]", new DiadicExpressionNode(null,new ArrayLiteralNode(null, asList(floatlit(1),floatlit(2),floatlit(3))),MULTIPLY,new ArrayLiteralNode(null, asList(floatlit(4),floatlit(5),floatlit(6)))));
        successExpect("[1.0] / [3.0]", new DiadicExpressionNode(null,new ArrayLiteralNode(null, asList(floatlit(1))),DIVIDE,new ArrayLiteralNode(null, asList(floatlit(3)))));
        successExpect("[1.0, 2.0, 3.0] / [4.0, 5.0, 6.0]", new DiadicExpressionNode(null,new ArrayLiteralNode(null, asList(floatlit(1),floatlit(2),floatlit(3))),DIVIDE,new ArrayLiteralNode(null, asList(floatlit(4),floatlit(5),floatlit(6)))));
        successExpect("[1.0] % [3.0]", new DiadicExpressionNode(null,new ArrayLiteralNode(null, asList(floatlit(1))),REMAINDER,new ArrayLiteralNode(null, asList(floatlit(3)))));
        successExpect("[1.0, 2.0, 3.0] % [4.0, 5.0, 6.0]", new DiadicExpressionNode(null,new ArrayLiteralNode(null, asList(floatlit(1),floatlit(2),floatlit(3))),REMAINDER,new ArrayLiteralNode(null, asList(floatlit(4),floatlit(5),floatlit(6)))));
        successExpect("[1.0] ^ [3.0]", new DiadicExpressionNode(null,new ArrayLiteralNode(null, asList(floatlit(1))),EXPONENT,new ArrayLiteralNode(null, asList(floatlit(3)))));
        successExpect("[1.0, 2.0, 3.0] ^ [4.0, 5.0, 6.0]", new DiadicExpressionNode(null,new ArrayLiteralNode(null, asList(floatlit(1),floatlit(2),floatlit(3))),EXPONENT,new ArrayLiteralNode(null, asList(floatlit(4),floatlit(5),floatlit(6)))));
        successExpect("[1.0] <> [3.0]", new DiadicExpressionNode(null,new ArrayLiteralNode(null, asList(floatlit(1))),CONCAT,new ArrayLiteralNode(null, asList(floatlit(3)))));
        successExpect("[1.0, 2.0, 3.0] <> [4.0, 5.0, 6.0]", new DiadicExpressionNode(null,new ArrayLiteralNode(null, asList(floatlit(1),floatlit(2),floatlit(3))),CONCAT,new ArrayLiteralNode(null, asList(floatlit(4),floatlit(5),floatlit(6)))));
        successExpect("[1.0] < [2.0]", new DiadicExpressionNode(null, new ArrayLiteralNode(null, asList(floatlit(1))), LOWER, new ArrayLiteralNode(null, asList(floatlit(2)))));
        successExpect("[1.0, 2.0, 3.0] < [1.0, 2.0, 3.0]", new DiadicExpressionNode(null,new ArrayLiteralNode(null, asList(floatlit(1),floatlit(2),floatlit(3))), LOWER, new ArrayLiteralNode(null, asList(floatlit(1),floatlit(2),floatlit(3)))));
        successExpect("[1.0] > [2.0]", new DiadicExpressionNode(null, new ArrayLiteralNode(null, asList(floatlit(1))), GREATER, new ArrayLiteralNode(null, asList(floatlit(2)))));
        successExpect("[1.0, 2.0, 3.0] > [1.0, 2.0, 3.0]", new DiadicExpressionNode(null, new ArrayLiteralNode(null, asList(floatlit(1),floatlit(2),floatlit(3))), GREATER, new ArrayLiteralNode(null, asList(floatlit(1),floatlit(2),floatlit(3)))));
        successExpect("[1.0] <= [2.0]", new DiadicExpressionNode(null, new ArrayLiteralNode(null, asList(floatlit(1))), LOWER_EQUAL, new ArrayLiteralNode(null, asList(floatlit(2)))));
        successExpect("[1.0, 2.0, 3.0] <= [1.0, 2.0, 3.0]", new DiadicExpressionNode(null, new ArrayLiteralNode(null, asList(floatlit(1),floatlit(2),floatlit(3))), LOWER_EQUAL, new ArrayLiteralNode(null, asList(floatlit(1),floatlit(2),floatlit(3)))));
        successExpect("[1.0] >= [2.0]", new DiadicExpressionNode(null, new ArrayLiteralNode(null, asList(floatlit(1))), GREATER_EQUAL, new ArrayLiteralNode(null, asList(floatlit(2)))));
        successExpect("[1.0, 2.0, 3.0] >= [1.0, 2.0, 3.0]", new DiadicExpressionNode(null, new ArrayLiteralNode(null, asList(floatlit(1),floatlit(2),floatlit(3))), GREATER_EQUAL, new ArrayLiteralNode(null, asList(floatlit(1),floatlit(2),floatlit(3)))));

    }

    @Test public void testBinaryIntArrayOpArray() {
        rule = grammar.expression;
        successExpect("[1] + [3]", new DiadicExpressionNode(null,new ArrayLiteralNode(null, asList(intlit(1))),ADD,new ArrayLiteralNode(null, asList(intlit(3)))));
        successExpect("[1, 2, 3] + [4, 5, 6]", new DiadicExpressionNode(null,new ArrayLiteralNode(null, asList(intlit(1),intlit(2),intlit(3))),ADD,new ArrayLiteralNode(null, asList(intlit(4),intlit(5),intlit(6)))));
        successExpect("[1] - [3]", new DiadicExpressionNode(null,new ArrayLiteralNode(null, asList(intlit(1))),SUBTRACT,new ArrayLiteralNode(null, asList(intlit(3)))));
        successExpect("[1, 2, 3] - [4, 5, 6]", new DiadicExpressionNode(null,new ArrayLiteralNode(null, asList(intlit(1),intlit(2),intlit(3))),SUBTRACT,new ArrayLiteralNode(null, asList(intlit(4),intlit(5),intlit(6)))));
        successExpect("[1] * [3]", new DiadicExpressionNode(null,new ArrayLiteralNode(null, asList(intlit(1))),MULTIPLY,new ArrayLiteralNode(null, asList(intlit(3)))));
        successExpect("[1, 2, 3] * [4, 5, 6]", new DiadicExpressionNode(null,new ArrayLiteralNode(null, asList(intlit(1),intlit(2),intlit(3))),MULTIPLY,new ArrayLiteralNode(null, asList(intlit(4),intlit(5),intlit(6)))));
        successExpect("[1] / [3]", new DiadicExpressionNode(null,new ArrayLiteralNode(null, asList(intlit(1))),DIVIDE,new ArrayLiteralNode(null, asList(intlit(3)))));
        successExpect("[1, 2, 3] / [4, 5, 6]", new DiadicExpressionNode(null,new ArrayLiteralNode(null, asList(intlit(1),intlit(2),intlit(3))),DIVIDE,new ArrayLiteralNode(null, asList(intlit(4),intlit(5),intlit(6)))));
        successExpect("[1] % [3]", new DiadicExpressionNode(null,new ArrayLiteralNode(null, asList(intlit(1))),REMAINDER,new ArrayLiteralNode(null, asList(intlit(3)))));
        successExpect("[1, 2, 3] % [4, 5, 6]", new DiadicExpressionNode(null,new ArrayLiteralNode(null, asList(intlit(1),intlit(2),intlit(3))),REMAINDER,new ArrayLiteralNode(null, asList(intlit(4),intlit(5),intlit(6)))));
        successExpect("[1] ^ [3]", new DiadicExpressionNode(null,new ArrayLiteralNode(null, asList(intlit(1))),EXPONENT,new ArrayLiteralNode(null, asList(intlit(3)))));
        successExpect("[1, 2, 3] ^ [4, 5, 6]", new DiadicExpressionNode(null,new ArrayLiteralNode(null, asList(intlit(1),intlit(2),intlit(3))),EXPONENT,new ArrayLiteralNode(null, asList(intlit(4),intlit(5),intlit(6)))));
        successExpect("[1] <> [3]", new DiadicExpressionNode(null,new ArrayLiteralNode(null, asList(intlit(1))),CONCAT,new ArrayLiteralNode(null, asList(intlit(3)))));
        successExpect("[1, 2, 3] <> [4, 5, 6]", new DiadicExpressionNode(null,new ArrayLiteralNode(null, asList(intlit(1),intlit(2),intlit(3))),CONCAT,new ArrayLiteralNode(null, asList(intlit(4),intlit(5),intlit(6)))));
        successExpect("[1] < [2]", new DiadicExpressionNode(null, new ArrayLiteralNode(null, asList(intlit(1))), LOWER, new ArrayLiteralNode(null, asList(intlit(2)))));
        successExpect("[1, 2, 3] < [1, 2, 3]", new DiadicExpressionNode(null,new ArrayLiteralNode(null, asList(intlit(1),intlit(2),intlit(3))), LOWER, new ArrayLiteralNode(null, asList(intlit(1),intlit(2),intlit(3)))));
        successExpect("[1] > [2]", new DiadicExpressionNode(null,new ArrayLiteralNode(null, asList(intlit(1))), GREATER, new ArrayLiteralNode(null, asList(intlit(2)))));
        successExpect("[1, 2, 3] > [1, 2, 3]", new DiadicExpressionNode(null, new ArrayLiteralNode(null, asList(intlit(1),intlit(2),intlit(3))), GREATER, new ArrayLiteralNode(null, asList(intlit(1),intlit(2),intlit(3)))));
        successExpect("[1] <= [2]", new DiadicExpressionNode(null, new ArrayLiteralNode(null, asList(intlit(1))), LOWER_EQUAL, new ArrayLiteralNode(null, asList(intlit(2)))));
        successExpect("[1, 2, 3] <= [1, 2, 3]", new DiadicExpressionNode(null, new ArrayLiteralNode(null, asList(intlit(1),intlit(2),intlit(3))), LOWER_EQUAL, new ArrayLiteralNode(null, asList(intlit(1),intlit(2),intlit(3)))));
        successExpect("[1] >= [2]", new DiadicExpressionNode(null, new ArrayLiteralNode(null, asList(intlit(1))), GREATER_EQUAL, new ArrayLiteralNode(null, asList(intlit(2)))));
        successExpect("[1, 2, 3] >= [1, 2, 3]", new DiadicExpressionNode(null, new ArrayLiteralNode(null, asList(intlit(1),intlit(2),intlit(3))), GREATER_EQUAL, new ArrayLiteralNode(null, asList(intlit(1),intlit(2),intlit(3)))));
    }

    @Test public void testMonadicVerb() {
        rule = grammar.expression;
        successExpect("+/ [1, 2, 3]", new MonadicExpressionNode(null, SUM_SLASH, new ArrayLiteralNode(null, asList(intlit(1), intlit(2), intlit(3)))));
        successExpect("{: [5, 2, 7]", new MonadicExpressionNode(null, GRAB_LAST, new ArrayLiteralNode(null, asList(intlit(5), intlit(2), intlit(7)))));
        successExpect("-/ [4, 3]", new MonadicExpressionNode(null, MIN_SLASH, new ArrayLiteralNode(null, asList(intlit(4), intlit(3)))));
        successExpect("./ 9", new MonadicExpressionNode(null, MULT_SLASH, intlit(9)));
        successExpect(":/ [10, 5, 6]", new MonadicExpressionNode(null, DIV_SLASH, new ArrayLiteralNode(null, asList(intlit(10), intlit(5), intlit(6)))));
        successExpect("&/ [5, 0, 6]", new MonadicExpressionNode(null, AND_SLASH, new ArrayLiteralNode(null, asList(intlit(5), intlit(0), intlit(6)))));
        successExpect("|/ [8, 2, 0]", new MonadicExpressionNode(null, OR_SLASH, new ArrayLiteralNode(null, asList(intlit(8), intlit(2), intlit(0)))));
        successExpect("# [7, 9, 3, 5, 8]", new MonadicExpressionNode(null, HASHTAG, new ArrayLiteralNode(null, asList(intlit(7), intlit(9), intlit(3), intlit(5), intlit(8)))));
        successExpect("+: [3, 4, 7, 8]", new MonadicExpressionNode(null, SELF_ADD, new ArrayLiteralNode(null, asList(intlit(3), intlit(4), intlit(7), intlit(8)))));
        successExpect("*: [2, 9, 5]", new MonadicExpressionNode(null, SELF_MULT, new ArrayLiteralNode(null, asList(intlit(2), intlit(9), intlit(5)))));
        successExpect("5 + :/ [10, 5, 6]", new DiadicExpressionNode(null, intlit(5), ADD, new MonadicExpressionNode(null, DIV_SLASH, new ArrayLiteralNode(null, asList(intlit(10), intlit(5), intlit(6))))));

    }

    @Test public void testMonadicFork(){
        rule = grammar.expression;
        successExpect("(+/ + {:) [1, 2]", new MonadicForkNode(null, SUM_SLASH, ADD, GRAB_LAST, new ArrayLiteralNode(null, asList(intlit(1), intlit(2)))));
        successExpect("({: + {:) [1, 2]", new MonadicForkNode(null, GRAB_LAST, ADD, GRAB_LAST, new ArrayLiteralNode(null, asList(intlit(1), intlit(2)))));
        successExpect("(:/ / +/) [1, 2]", new MonadicForkNode(null, DIV_SLASH, DIVIDE, SUM_SLASH, new ArrayLiteralNode(null, asList(intlit(1), intlit(2)))));
        successExpect("(-/ * ./) [1, 2]", new MonadicForkNode(null, MIN_SLASH, MULTIPLY, MULT_SLASH, new ArrayLiteralNode(null, asList(intlit(1), intlit(2)))));
        successExpect("(+/ % {:) [1, 2]", new MonadicForkNode(null, SUM_SLASH, REMAINDER, GRAB_LAST, new ArrayLiteralNode(null, asList(intlit(1), intlit(2)))));
        successExpect("(-/ + +/) (+/ % {:) [1, 2]", new MonadicForkNode(null, MIN_SLASH, ADD, SUM_SLASH, new MonadicForkNode(null, SUM_SLASH, REMAINDER, GRAB_LAST, new ArrayLiteralNode(null, asList(intlit(1), intlit(2))))));
    }

    @Test public void testDiadicFork(){
        rule = grammar.expression;
        successExpect("[1, 3] (* - +) [1, 2]", new DiadicForkNode(null, new ArrayLiteralNode(null, asList(intlit(1), intlit(3))), MULTIPLY, SUBTRACT, ADD, new ArrayLiteralNode(null, asList(intlit(1), intlit(2)))));
        successExpect("1 (/ + %) 2", new DiadicForkNode(null, intlit(1), DIVIDE, ADD, REMAINDER, intlit(2)));
        successExpect("[1, 2, 3] (+ * +) 1", new DiadicForkNode(null, new ArrayLiteralNode(null, asList(intlit(1), intlit(2), intlit(3))), ADD, MULTIPLY, ADD,intlit(1)));
        successExpect("4 (- / /) [1, 4, 2]", new DiadicForkNode(null, intlit(4), SUBTRACT, DIVIDE, DIVIDE, new ArrayLiteralNode(null, asList(intlit(1), intlit(4), intlit(2)))));
    }

}
