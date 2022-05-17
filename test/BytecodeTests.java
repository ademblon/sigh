import norswap.autumn.Autumn;
import norswap.autumn.ParseOptions;
import norswap.autumn.ParseResult;
import norswap.sigh.SemanticAnalysis;
import norswap.sigh.SighGrammar;
import norswap.sigh.ast.SighNode;
import norswap.sigh.bytecode.ByteArrayClassLoader;
import norswap.sigh.bytecode.BytecodeCompiler;
import norswap.sigh.bytecode.CompilationResult;
import norswap.uranium.Reactor;
import norswap.utils.IO;
import norswap.utils.visitors.Walker;
import org.testng.annotations.Test;

import static norswap.utils.Util.cast;
import static org.testng.Assert.assertEquals;

@SuppressWarnings("FieldCanBeLocal")
public class BytecodeTests
{
    // TODO test multi dimensional arrays

    // ---------------------------------------------------------------------------------------------

    /**
     * Checks that the input program can be compiled, run and prints the {@code expected} string if
     * non-null (to which a newline is appended if not empty).
     */
    public void check (String input, String expected)
    {
        SighGrammar grammar = new SighGrammar();
        ParseOptions options = ParseOptions.builder().recordCallStack(true).get();
        ParseResult parseResult = Autumn.parse(grammar.root, input, options);
        if (!parseResult.fullMatch) throw new AssertionError(parseResult.toString());

        SighNode tree = cast(parseResult.topValue());
        Reactor reactor = new Reactor();
        Walker<SighNode> walker = SemanticAnalysis.createWalker(reactor);
        walker.walk(tree);
        reactor.run();

        if (!reactor.errors().isEmpty())
            throw new AssertionError(reactor.reportErrors(Object::toString));

        String className = "BytecodeTestsRun";
        BytecodeCompiler compiler = new BytecodeCompiler(reactor);
        CompilationResult result = compiler.compile(className, tree);

        // using a new loader each time allows to overwrite the class every time.
        Class<?> mainClass = result.load(new ByteArrayClassLoader());

        if (expected == null) {
            CompilationResult.callMain(mainClass);
            return;
        }

        // TODO utils capture stdout with runnable
        String capture = IO.captureStdout(() -> {
            CompilationResult.callMain(mainClass);
            return null;
        }).a;

        if (!expected.isEmpty())
            expected = expected + "\n";

        assertEquals(capture, expected);
    }

    // ---------------------------------------------------------------------------------------------

    /**
     * Checks that the input <b>expression</b> can be converted to a string and printed, and that the printed
     * string corresponds to the {@code expected} string.
     */
    public void checkExpr (String input, String expected) {
        check("print(\"\" + ("+ input + "))", expected);
    }

    // ---------------------------------------------------------------------------------------------

    @Test
    public void basicPrintTest() {
        // explicit unroll basic tests that involve print
        check("print(\"hello\")", "hello");
        check("print(\"hello\") ; return", "hello");
        check("print(\"\" + 1)", "1");
        check("print(\"\" + 1 + 2)", "3");
        check("print(1 + 2 + \"\")", "12");
        check("print(\"\" + (1 + 2))", "3");
        check("print((1 + 2) + \"\"  )", "3");
    }

    // ---------------------------------------------------------------------------------------------

    @Test
    public void testLiteralsAndUnary () {
        checkExpr("42", "42");
        checkExpr("42.0", "42.0");
        checkExpr("\"hello\"", "hello");
        checkExpr("(42)", "42");
        checkExpr("[1, 2, 3]", "[1, 2, 3]");
        checkExpr("true", "true");
        checkExpr("false", "false");
        checkExpr("null", "null");
        //todo changer ca  (update: les tuteurs nous ont dit que le bytecode n'a pas d'importance)
      /*  checkExpr("!false", "true");
        checkExpr("!true", "false");
        checkExpr("!!true", "true"); */
    }

    // ---------------------------------------------------------------------------------------------

    @Test
    public void testNumericBinary () {
        checkExpr("1 + 2", "3");
        checkExpr("2 - 1", "1");
        checkExpr("2 * 3", "6");
        checkExpr("2 / 3", "0");
        checkExpr("3 / 2", "1");
        checkExpr("2 % 3", "2");
        checkExpr("3 % 2", "1");

        checkExpr("1.0 + 2.0", "3.0");
        checkExpr("2.0 - 1.0", "1.0");
        checkExpr("2.0 * 3.0", "6.0");
        checkExpr("2.0 / 3.0", "" + (2d / 3d));
        checkExpr("3.0 / 2.0", "" + (3d / 2d));
        checkExpr("2.0 % 3.0", "2.0");
        checkExpr("3.0 % 2.0", "1.0");

        checkExpr("1 + 2.0", "3.0");
        checkExpr("2 - 1.0", "1.0");
        checkExpr("2 * 3.0", "6.0");
        checkExpr("2 / 3.0", "" + (2d / 3d));
        checkExpr("3 / 2.0", "" + (3d / 2d));
        checkExpr("2 % 3.0", "2.0");
        checkExpr("3 % 2.0", "1.0");

        checkExpr("1.0 + 2", "3.0");
        checkExpr("2.0 - 1", "1.0");
        checkExpr("2.0 * 3", "6.0");
        checkExpr("2.0 / 3", "" + (2d / 3d));
        checkExpr("3.0 / 2", "" + (3d / 2d));
        checkExpr("2.0 % 3", "2.0");
        checkExpr("3.0 % 2", "1.0");

        //checkExpr("2 * (4-1) * 4.0 / 6 % (2+1)", "1.0");
    }



    // ---------------------------------------------------------------------------------------------

    @Test
    public void testVarDecl () {
        check("var x: Int = 1; print(\"\" + x)", "1");
        check("var x: Float = 2.0; print(\"\" + x)", "2.0");

        check("var x: Int = 0; x = 3; print(\"\" + x)", "3");

        // TODO fails
        //check("var x: String = \"0\"; print(x = \"S\")", "S");
        check("var x: String = \"0\"; x = \"S\"; print(x)", "S");

        // implicit conversions
        check("var x: Float = 1; x = 2; print(\"\" + x)", "2.0");
    }

    // ---------------------------------------------------------------------------------------------

    @Test public void testArrays() {
        checkExpr("[1, 2, 3]", "[1, 2, 3]");
        checkExpr("[\"a\", \"b\", \"c\"]", "[a, b, c]");
        checkExpr("[1.0, 2.0]", "[1.0, 2.0]");

        checkExpr("[1, 2, 3][0]", "1");
        checkExpr("[1, 2, 3][1]", "2");
        checkExpr("[1, 2, 3][2]", "3");
        checkExpr("[\"a\", \"b\", \"c\"][0]", "a");
        checkExpr("[\"a\", \"b\", \"c\"][2]", "c");

        check("var x: Float[] = [1.0, 2.0]; x[0] = 3.0; print(\"\" + x[0])", "3.0");
        check("var x: Float[] = [1.0, 2.0]; x[0] = 3; print(\"\" + x[0])", "3.0");
    }

    private final String printa = "print(\"a\")";
    private final String printb = "print(\"b\")";
    private final String printx = "print(\"\" + (x))";
    private final String printy = "print(\"\" + (y))";

    @Test public void testVariables() {
        check("var x: Int = 1;" + printx, "1");
        check("var x: String = \"a\";" + printx, "a");
        check("var x: Int = 1 ; " + printx + " ; x = 2 ; " + printx, "1\n2");

        // longs and double have double width
        check("var x: Int = 1 ; var y: Float = 2.0 ;" + printx + printy, "1\n2.0");
        check("var x: Int = 1 ; var y: String = \"a\" ;" + printx + printy, "1\na");
        check("var x: String = \"a\" ; var y: Int = 1 ;" + printx + printy, "a\n1");
        check("var x: Float = 1.0 ; var y: String = \"a\" ;" + printx + printy, "1.0\na");

        // implicit conversion
        check("var x: Float = 1 ;" + printx, "1.0");
        check("var x: Float = 1 ;" + printx + "x = 2 ;" + printx, "1.0\n2.0");
    }

    //todo make it work
    /*
    @Test public void testIfWhile() {
        check("if 1 == 1 " + printa, "a");
        check("if 1 == 1 " + printa + "else " + printb, "a");
        check("if 1 == 0 " + printa + "else " + printb, "b");

        check("var x: Int = 1 ; while x == 3 { " + printx + "}", "");
        check("var x: Int = 1 ; while x <= 3 { " + printx + " ; x = x + 1 }", "1\n2\n3");
    } */

    @Test public void testMethod() {
        check("fun test (x: String):String { return x } print(test(\"a\"))", "a");
        check("fun test (x: String) { print(x) } ; test(\"a\")", "a");
        check("fun test () { fun foo() { print(\"a\") } foo() foo() } test()", "a\na");
    }

    private final String makePair =
        "struct Pair { var x: Int ; var y: Float }" +
        "var x: Pair = $Pair(1, 2.0) ;";

    @Test public void testStructs() {
        check(makePair + "print(\"\" + x.x + \":\" + x.y)", "1:2.0");
        check(makePair + "x.x = 3; print(\"\" + x.x)", "3");
        check(makePair + "x.y = 3; print(\"\" + x.y)", "3.0");
    }


    //------------------------------------- new tests ----------------------------------------------
    @Test public void testOrderRightLeftBinary() {
        checkExpr("1 + 2 + 3", "6");
        checkExpr("1 - 2 - 3", "2");
        checkExpr("8 / 4 / 2", "4");
        checkExpr("2 * 3 * 4", "24");
        checkExpr("2 % 5 % 7", "2");
        checkExpr("7 % 5 % 3", "1");

        checkExpr("1.0 + 2.0 + 3.0", "6.0");
        checkExpr("1.0 - 2.0 - 3.0", "2.0");
        checkExpr("1.0 / 2.0 / 2.0", "1.0");
        checkExpr("2.0 * 3.0 * 4.0", "24.0");
        checkExpr("2.0 % 5.0 % 7.0", "2.0");
        checkExpr("7.0 % 5.0 % 3.0", "1.0");
    }

    @Test public void testMathPriority() {
        checkExpr("1 * 2 - 3", "-1");
        checkExpr("1 - 2 * 3 ", "-5");
        checkExpr("1 * 2 + 3", "5");
        checkExpr("1 + 2 * 3", "7");
        checkExpr("3 / 2 - 1", "3");
        checkExpr("2 % 3 -1 ", "0");
        checkExpr("3 - 2 % 3", "1");

        checkExpr("1.0 * 2.0 - 3.0", "-1.0");
        checkExpr("1.0 - 2.0 * 3.0 ", "-5.0");
        checkExpr("1.0 * 2.0 + 3.0", "5.0");
        checkExpr("1.0 + 2.0 * 3.0", "7.0");
        checkExpr("3.0 / 2.0 - 1.0", "3.0");
        checkExpr("2.0 % 3.0 -1.0 ", "0.0");
        checkExpr("3.0 - 2.0 % 3.0", "1.0");
    }

  /*  @Test public void testBinariesIntOpArray(){
        checkExpr("1 + [2]", "[3]");
        checkExpr("1 + [1, 2, 3]", "[2, 3, 4]");
        checkExpr("1 - [2]", "[-1]");
        checkExpr("1 - [1, 2, 3]", "[0, -1, -2]");
        checkExpr("2 * [2]", "[4]");
        checkExpr("2 * [1, 2, 3]", "[2, 4, 6]");
        checkExpr("6 / [2]", "[3]");
        checkExpr("6 / [1, 2, 3]", "[6, 3, 2]");
        checkExpr("6 % [2]", "[0]");
        checkExpr("3 % [1, 2, 3]", "[0, 1, 0]");

    }*/
/*
    @Test public void testBinariesDoubleOpArray(){

        checkExpr("1.0 + [2.0]", new Object[]{3d});
        checkExpr("1.0 + [1.0, 2.0, 3.0]", new Object[]{2d, 3d, 4d});
        checkExpr("1.0 - [2.0]", new Object[]{-1d});
        checkExpr("1.0 - [1.0, 2.0, 3.0]", new Object[]{0d, -1d, -2d});
        checkExpr("2.0 * [2.0]", new Object[]{4d});
        checkExpr("2.0 * [1.0, 2.0, 3.0]", new Object[]{2d, 4d, 6d});
        checkExpr("6.0 / [2.0]", new Object[]{3d});
        checkExpr("6.0 / [1.0, 2.0, 3.0]", new Object[]{6d, 3d, 2d});
        checkExpr("6.0 % [2.0]", new Object[]{0d});
        checkExpr("3.0 % [1.0, 2.0, 3.0]", new Object[]{0d, 1d, 0d});

    }

    @Test public void testBinariesArrayIntOpArray(){

        checkExpr("[1] + [2]", new Object[]{3L});
        checkExpr("[1, 2, 3] + [1, 2, 3]",  new Object[]{2L, 4L, 6L});
        checkExpr("[1] - [2]",  new Object[]{-1L});
        checkExpr("[1, 5 ,2] - [1, 2, 3]", new Object[]{0L, 3L, -1L});
        checkExpr("[2] * [2]",  new Object[]{4L});
        checkExpr("[5, 4, 3] * [1, 2, 3]",  new Object[]{5L, 8L, 9L});
        checkExpr("[6] / [2]",  new Object[]{3L});
        checkExpr("[2, 8, 9] / [1, 2, 3]",  new Object[]{2L, 4L, 3L});
        checkExpr("[6] % [2]",  new Object[]{0L});
        checkExpr("[4, 3, 2] % [1, 2, 3]",  new Object[]{0L, 1L, 2L});

    }

    @Test public void testBinariesArrayDoubleOpArray(){
        checkExpr("[1.0] + [2.0]", new Object[]{3d});
        checkExpr("[1.0, 2.0, 3.0] + [1.0, 2.0, 3.0]",  new Object[]{2d, 4d, 6d});
        checkExpr("[1.0] - [2.0]",  new Object[]{-1d});
        checkExpr("[1.0, 5.0 ,2.0] - [1.0, 2.0, 3.0]", new Object[]{0d, 3d, -1d});
        checkExpr("[2.0] * [2.0]",  new Object[]{4d});
        checkExpr("[5.0, 4.0, 3.0] * [1.0, 2.0, 3.0]",  new Object[]{5d, 8d, 9d});
        checkExpr("[6.0] / [2.0]",  new Object[]{3d});
        checkExpr("[2.0, 8.0, 9.0] / [1.0, 2.0, 3.0]",  new Object[]{2d, 4d, 3d});
        checkExpr("[6.0] % [2.0]",  new Object[]{0d});
        checkExpr("[4.0, 3.0, 2.0] % [1.0, 2.0, 3.0]",  new Object[]{0d, 1d, 2d});
    }

    @Test public void testBinariesArrayIntDoubleOpArray(){
        checkExpr("[1] + [2.0]", new Object[]{3d});
        checkExpr("[1, 2, 3] + [1.0, 2.0, 3.0]",  new Object[]{2d, 4d, 6d});
        checkExpr("[1.0] - [2]",  new Object[]{-1d});
        checkExpr("[1, 5 ,2] - [1.0, 2.0, 3.0]", new Object[]{0d, 3d, -1d});
        checkExpr("[2.0] * [2]",  new Object[]{4d});
        checkExpr("[5.0, 4.0, 3.0] * [1, 2, 3]",  new Object[]{5d, 8d, 9d});
        checkExpr("[6.0] / [2.0]",  new Object[]{3d});
        checkExpr("[2.0, 8.0, 9.0] / [1, 2, 3]",  new Object[]{2d, 4d, 3d});
        checkExpr("[6] % [2.0]",  new Object[]{0d});
        checkExpr("[4, 3, 2] % [1.0, 2.0, 3.0]",  new Object[]{0d, 1d, 2d});
    } */

    // -------------------- new functions ----------------------------------------------------------

    // ---------------------------------------------------------------------------------------------

    //todo make it work
    @Test public void testLogic() {
        // check boolean logic


        checkExpr("1 + \"a\"", "1a");
        checkExpr("\"a\" + 1", "a1");
        checkExpr("\"a\" + true", "atrue");

        //todo changer ca  (update: les tuteurs nous ont dit que le bytecode n'a pas d'importance)
/*
        checkExpr("1 == 1", "1");
        checkExpr("1 == 2", "false");
        checkExpr("1.0 == 1.0", "true");
        checkExpr("1.0 == 2.0", "false");
        checkExpr("true == true", "true");
        checkExpr("false == false", "true");
        checkExpr("true == false", "false");
        checkExpr("1 == 1.0", "true");
        checkExpr("[1] == [1]", "false");

        checkExpr("1 != 1", "false");
        checkExpr("1 != 2", "true");
        checkExpr("1.0 != 1.0", "false");
        checkExpr("1.0 != 2.0", "true");
        checkExpr("true != true", "false");
        checkExpr("false != false", "false");
        checkExpr("true != false", "true");
        checkExpr("1 != 1.0", "false");
        checkExpr("\"hi\" != \"hi2\"", "true");
        checkExpr("[1] != [1]", "true");

        // test short circuit
        checkExpr("true || print(\"x\") == \"y\"", "true");
        checkExpr("false && print(\"x\") == \"y\"", "false"); */
    }
}
