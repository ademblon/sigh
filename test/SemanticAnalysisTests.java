import norswap.autumn.AutumnTestFixture;
import norswap.autumn.positions.LineMapString;
import norswap.sigh.SemanticAnalysis;
import norswap.sigh.SighGrammar;
import norswap.sigh.ast.ArrayLiteralNode;
import norswap.sigh.ast.MonadicForkNode;
import norswap.sigh.ast.SighNode;
import norswap.uranium.Reactor;
import norswap.uranium.UraniumTestFixture;
import norswap.utils.visitors.Walker;
import org.testng.annotations.Test;

import static java.util.Arrays.asList;
import static norswap.sigh.ast.DiadicOperator.*;
import static norswap.sigh.ast.DiadicOperator.REMAINDER;
import static norswap.sigh.ast.MonadicOperator.*;
import static norswap.sigh.ast.MonadicOperator.GRAB_LAST;

/**
 * NOTE(norswap): These tests were derived from the {@link InterpreterTests} and don't test anything
 * more, but show how to idiomatically test semantic analysis. using {@link UraniumTestFixture}.
 */
public final class SemanticAnalysisTests extends UraniumTestFixture
{
    // ---------------------------------------------------------------------------------------------

    private final SighGrammar grammar = new SighGrammar();
    private final AutumnTestFixture autumnFixture = new AutumnTestFixture();

    {
        autumnFixture.rule = grammar.root();
        autumnFixture.runTwice = false;
        autumnFixture.bottomClass = this.getClass();
    }

    private String input;

    @Override protected Object parse (String input) {
        this.input = input;
        return autumnFixture.success(input).topValue();
    }

    @Override protected String astNodeToString (Object ast) {
        LineMapString map = new LineMapString("<test>", input);
        return ast.toString() + " (" + ((SighNode) ast).span.startString(map) + ")";
    }

    // ---------------------------------------------------------------------------------------------

    @Override protected void configureSemanticAnalysis (Reactor reactor, Object ast) {
        Walker<SighNode> walker = SemanticAnalysis.createWalker(reactor);
        walker.walk(((SighNode) ast));
    }

    // ---------------------------------------------------------------------------------------------

    @Test public void testLiteralsAndUnary() {
        successInput("return 42");
        successInput("return 42.0");
        successInput("return \"hello\"");
        successInput("return (42)");
        successInput("return [1, 2, 3]");

        //todo fix this
        /*
        successInput("return true");
        successInput("return false");
        successInput("return null");
        successInput("return !false");
        successInput("return !true");
        successInput("return !!true"); */
    }

    // ---------------------------------------------------------------------------------------------

    @Test public void testNumericBinary() {
        successInput("return 1 + 2");
        successInput("return 2 - 1");
        successInput("return 2 * 3");
        successInput("return 2 / 3");
        successInput("return 3 / 2");
        successInput("return 2 % 3");
        successInput("return 3 % 2");

        successInput("return 1.0 + 2.0");
        successInput("return 2.0 - 1.0");
        successInput("return 2.0 * 3.0");
        successInput("return 2.0 / 3.0");
        successInput("return 3.0 / 2.0");
        successInput("return 2.0 % 3.0");
        successInput("return 3.0 % 2.0");

        successInput("return 1 + 2.0");
        successInput("return 2 - 1.0");
        successInput("return 2 * 3.0");
        successInput("return 2 / 3.0");
        successInput("return 3 / 2.0");
        successInput("return 2 % 3.0");
        successInput("return 3 % 2.0");

        successInput("return 1.0 + 2");
        successInput("return 2.0 - 1");
        successInput("return 2.0 * 3");
        successInput("return 2.0 / 3");
        successInput("return 3.0 / 2");
        successInput("return 2.0 % 3");
        successInput("return 3.0 % 2");
    }

    // ---------------------------------------------------------------------------------------------

    @Test public void testOtherBinary() {
      ;

        successInput("return 1 + \"a\"");
        successInput("return \"a\" + 1");

        successInput("return 1 == 1");
        successInput("return 1 == 2");
        successInput("return 1.0 == 1.0");
        successInput("return 1.0 == 2.0");
        successInput("return 1 == 1.0");


        successInput("return \"hi\" == \"hi\"");
        successInput("return [1] == [1]");

        successInput("return 1 != 1");
        successInput("return 1 != 2");
        successInput("return 1.0 != 1.0");
        successInput("return 1.0 != 2.0");
        successInput("return 1 != 1.0");

        successInput("return \"hi\" != \"hi\"");
        successInput("return [1] != [1]");
    }

    // ---------------------------------------------------------------------------------------------

    @Test public void testVarDecl() {
        successInput("var x: Int = 1; return x");
        successInput("var x: Float = 2.0; return x");

        successInput("var x: Int = 0; return x = 3");
        successInput("var x: String = \"0\"; return x = \"S\"");

        failureInputWith("return x + 1", "Could not resolve: x");
        failureInputWith("return x + 1; var x: Int = 2", "Variable used before declaration: x");

        // implicit conversions
        successInput("var x: Float = 1 ; x = 2");
    }

    // ---------------------------------------------------------------------------------------------

    @Test public void testRootAndBlock () {
        successInput("return");
        successInput("return 1");
        successInput("return 1; return 2");

        successInput("print(\"a\")");
        successInput("print(\"a\" + 1)");
        successInput("print(\"a\"); print(\"b\")");

        successInput("{ print(\"a\"); print(\"b\") }");

        successInput(
            "var x: Int = 1;" +
            "{ print(\"\" + x); var x: Int = 2; print(\"\" + x) }" +
            "print(\"\" + x)");
    }

    // ---------------------------------------------------------------------------------------------

    @Test public void testCalls() {
        successInput(
            "fun add (a: Int, b: Int): Int { return a + b } " +
            "return add(4, 7)");

        successInput(
            "struct Point { var x: Int; var y: Int }" +
            "return $Point(1, 2)");

        successInput("var str: String = null; return print(str + 1)");

        failureInputWith("return print(1)", "argument 0: expected String but got Int");
    }

    // ---------------------------------------------------------------------------------------------

    @Test public void testArrayStructAccess() {
        successInput("return [1][0]");
        successInput("return [1.0][0]");
        successInput("return [1, 2][1]");

        successInput("return [1].length");
        successInput("return [1, 2].length");

        successInput("var array: Int[] = null; return array[0]");
        successInput("var array: Int[] = null; return array.length");

        successInput("var x: Int[] = [0, 1]; x[0] = 3; return x[0]");
        successInput("var x: Int[] = []; x[0] = 3; return x[0]");
        successInput("var x: Int[] = null; x[0] = 3");

        successInput(
            "struct P { var x: Int; var y: Int }" +
            "return $P(1, 2).y");

        successInput(
            "struct P { var x: Int; var y: Int }" +
            "var p: P = null;" +
            "return p.y");

        successInput(
            "struct P { var x: Int; var y: Int }" +
            "var p: P = $P(1, 2);" +
            "p.y = 42;" +
            "return p.y");

        successInput(
            "struct P { var x: Int; var y: Int }" +
            "var p: P = null;" +
            "p.y = 42");

        failureInputWith(
            "struct P { var x: Int; var y: Int }" +
            "return $P(1, 2).z",
            "Trying to access missing field z on struct P");
    }



    // ---------------------------------------------------------------------------------------------

    @Test public void testInference() {
        successInput("var array: Int[] = []");
        successInput("var array: String[] = []");
        successInput("fun use_array (array: Int[]) {} ; use_array([])");
    }

    // ---------------------------------------------------------------------------------------------

    @Test public void testTypeAsValues() {
        successInput("struct S{} ; return \"\"+ S");
        successInput("struct S{} ; var type: Type = S ; return \"\"+ type");
    }

    // ---------------------------------------------------------------------------------------------

    @Test public void testUnconditionalReturn()
    {
        successInput("fun f(): Int { if (1) return 1 else return 2 } ; return f()");

        // TODO: would be nice if this pinpointed the if-statement as missing the return,
        //   not the whole function declaration
        failureInputWith("fun f(): Int { if (1) return 1 } ; return f()",
            "Missing return in function");
    }

    //------------------------------------- new tests ----------------------------------------------

    @Test public void  testComplexBinaries()
    {
        successInput("return 1 + 2 + 3 + 4");
        successInput("return 2 - 1 + 5 - 7");
        successInput("return 2 * 3 / 7 * 2");
        successInput("return 2 - 3 % 5 * 7");
        successInput("return 3 / 2 - 5 * 7");
        successInput("return 2 + 3 - 10 / 8");
        successInput("return 3 % 2 % 8 * 10");

        successInput("return 1.0 + 2.0 + 3.0 + 4.0");
        successInput("return 2.0 - 1.0 + 5.0 - 7.0");
        successInput("return 2.0 * 3.0 / 7.0 * 2.0");
        successInput("return 2.0 - 3.0 % 5.0 * 7.0");
        successInput("return 3.0 / 2.0 - 5.0 * 7.0");
        successInput("return 2.0 + 3.0 - 10.0 / 8.0");
        successInput("return 3.0 % 2.0 % 8.0 * 10.0");

        successInput("return 1.0 + 2 + 3 + 4");
        successInput("return 2 - 1.0 + 5 - 7");
        successInput("return 2 * 3 / 7.0 * 2");
        successInput("return 2 - 3 % 5 * 7.0");
        successInput("return 3 / 2 - 5.0 * 7");
        successInput("return 2 + 3.0 - 10 / 8");
        successInput("return 3.0 % 2 % 8 * 10");

        successInput("return 1.0 + 2 + (3 + 4)");
        successInput("return 2 - (1.0 + 5) - 7");
        successInput("return (2 * 3 / 7.0 * 2)");
        successInput("return 2 - (3 % 5) * 7.0");
        successInput("return ((3 / 2) - (5.0 * 7))");
        successInput("return (2 + 3.0 - 10 / 8)");
        successInput("return (((3.0 % 2) % 8) * 10)");

        successInput("var X: Int= 3 + 2 * 3; " +
            "var Y: Int= 10;" +
            "var Z: Int = 17 + Y + X");
    }
    
    @Test public void testBinariesIntOpArray(){
        successInput("return 1 + [2]");
        successInput("return 1 + [1, 2, 3]");
        successInput("return 1 - [2]");
        successInput("return 1 - [1, 2, 3]");
        successInput("return 1 * [2]");
        successInput("return 1 * [1, 2, 3]");
        successInput("return 1 / [2]");
        successInput("return 1 / [1, 2, 3]");
        successInput("return 1 % [2]");
        successInput("return 1 % [1, 2, 3]");
        successInput("return 1 < [2]");
        successInput("return 1 < [1, 2, 3]");
        successInput("return 1 > [2]");
        successInput("return 1 > [1, 2, 3]");
        successInput("return 1 <= [2]");
        successInput("return 1 <= [1, 2, 3]");
        successInput("return 1 >= [2]");
        successInput("return 1 >= [1, 2, 3]");
    }

    @Test public void testBinariesArrayIntOpArray(){

        successInput("return [1] + [2]");
        successInput("return [1, 2, 3] + [1, 2, 3]");
        successInput("return [1] - [2]");
        successInput("return [1, 2, 3] - [1, 2, 3]");
        successInput("return [1] * [2]");
        successInput("return [1, 2, 3] * [1, 2, 3]");
        successInput("return [1] / [2]");
        successInput("return [1, 2, 3] / [1, 2, 3]");
        successInput("return [1] % [2]");
        successInput("return [1, 2, 3] % [1, 2, 3]");
        successInput("return [1] < [2]");
        successInput("return [1, 2, 3] < [1, 2, 3]");
        successInput("return [1] > [2]");
        successInput("return [1, 2, 3] > [1, 2, 3]");
        successInput("return [1] <= [2]");
        successInput("return [1, 2, 3] <= [1, 2, 3]");
        successInput("return [1] >= [2]");
        successInput("return [1, 2, 3] >= [1, 2, 3]");
    }

    @Test public void testBinariesDoubleOpArray(){

        successInput("return 1.0 + [2.0]");
        successInput("return 1.0 + [1.0, 2.0, 3.0]");
        successInput("return 1.0 - [2.0]");
        successInput("return 1.0 - [1.0, 2.0, 3.0]");
        successInput("return 1.0 * [2.0]");
        successInput("return 1.0 * [1.0, 2.0, 3.0]");
        successInput("return 1.0 / [2.0]");
        successInput("return 1.0 / [1.0, 2.0, 3.0]");
        successInput("return 1.0 % [2.0]");
        successInput("return 1.0 % [1.0, 2.0, 3.0]");
        successInput("return 1.0 < [2.0]");
        successInput("return 1.0 < [1.0, 2.0, 3.0]");
        successInput("return 1.0 > [2.0]");
        successInput("return 1.0 > [1.0, 2.0, 3.0]");
        successInput("return 1.0 <= [2.0]");
        successInput("return 1.0 <= [1.0, 2.0, 3.0]");
        successInput("return 1.0 >= [2.0]");
        successInput("return 1.0 >= [1.0, 2.0, 3.0]");
    }

    @Test public void testBinariesArrayDoubleOpArray(){

        successInput("return [1.0] + [2.0]");
        successInput("return [1.0, 2.0, 3.0] + [1.0, 2.0, 3.0]");
        successInput("return [1.0] - [2.0]");
        successInput("return [1.0, 2.0, 3.0] - [1.0, 2.0, 3.0]");
        successInput("return [1.0] * [2.0]");
        successInput("return [1.0, 2.0, 3.0] * [1.0, 2.0, 3.0]");
        successInput("return [1.0] / [2.0]");
        successInput("return [1.0, 2.0, 3.0] / [1.0, 2.0, 3.0]");
        successInput("return [1.0] % [2.0]");
        successInput("return [1.0, 2.0, 3.0] % [1.0, 2.0, 3.0]");
        successInput("return [1.0] < [2.0]");
        successInput("return [1.0, 2.0, 3.0] < [1.0, 2.0, 3.0]");
        successInput("return [1.0] > [2.0]");
        successInput("return [1.0, 2.0, 3.0] > [1.0, 2.0, 3.0]");
        successInput("return [1.0] <= [2.0]");
        successInput("return [1.0, 2.0, 3.0] <= [1.0, 2.0, 3.0]");
        successInput("return [1.0] >= [2.0]");
        successInput("return [1.0, 2.0, 3.0] >= [1.0, 2.0, 3.0]");
    }

    @Test public void testMonadicExpressionIntArray(){

        successInput("return +/ [1, 2, 3]");
        successInput("return {: [5, 2, 7]");
        successInput("return -/ [4, 3]");
        successInput("return ./ [1, 2, 3]");
        successInput("return :/ [10, 5, 6]");
    }

    @Test public void testMonadicExpressionDoubleArray(){

        successInput("return +/ [1.1, 2.1, 3.1]");
        successInput("return {: [5.1, 2.1, 7.1]");
        successInput("return -/ [4.1, 3.1]");
        successInput("return ./ [1.1, 2.1, 3.1]");
        successInput("return :/ [10.1, 5.1, 6.1]");
    }

    @Test public void testMonadicExpressionInt(){

        successInput("return +/ 2");
        successInput("return {: 2");
        successInput("return -/ 2");
        successInput("return ./ 2");
        successInput("return :/ 2");
    }

    @Test public void testMonadicExpressionDouble(){

        successInput("return +/ 2.1");
        successInput("return {: 2.1");
        successInput("return -/ 2.1");
        successInput("return ./ 2.1");
        successInput("return :/ 2.1");
    }

    @Test public void testIntMonadicExpressionIntArray(){

        successInput("return 2 + +/ [1, 2, 3]");
        successInput("return 2.5 + {: [5, 2, 7]");
        successInput("return 2 + -/ [4.5, 3.5]");
        successInput("return 2.7 + ./ [1.0, 2.0, 3.0]");
    }

    @Test public void testMonadicFork(){

        successInput("return (+/ + {:) [1, 2]");
        successInput("return ({: + {:) [1, 2]");
        successInput("return (:/ / +/) [1, 2]");
        successInput("return (-/ * ./) [1, 2]");
        successInput("return (+/ % {:) [1, 2]");
    }

    @Test public void testDiadicFork(){

        successInput("return [1, 3] (* - +) [1, 2]");
        successInput("return 1 (/ + %) 2");
        successInput("return [1, 2, 3] (+ * +) 1");
        successInput("return (-/ * ./) [1, 2]");
        successInput("return 4 (- / /) [1, 4, 2]");
    }

    // ---------------------------------------------------------------------------------------------

    @Test
    public void testIfWhile () {
        successInput("if (1) return 1 else return 2");
        successInput("if (0) return 1 else return 2");
        successInput("if (1.0) return 1 else if (17) return 2 else return 3 ");
        successInput("if (0.0) return 1 else if (25.5) return 2 else return 3 ");

        successInput("if ([17]) return 1 else return 2");
        successInput("if ([0]) return 1 else return 2");
        successInput("if ([0.0]) return 1 else if (1) return 2 else return 3 ");
        successInput("if ([0.0]) return 1 else if (0) return 2 else return 3 ");

        successInput("var i: Int = 0; while (i < 3) { print(\"\" + i); i = i + 1 } ");

        successInput("if 1 return 1");
        successInput("while 1 return 1");

    }


}
