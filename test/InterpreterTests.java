import norswap.autumn.AutumnTestFixture;
import norswap.autumn.Grammar;
import norswap.autumn.Grammar.rule;
import norswap.autumn.ParseResult;
import norswap.autumn.positions.LineMapString;
import norswap.sigh.SemanticAnalysis;
import norswap.sigh.SighGrammar;
import norswap.sigh.ast.SighNode;
import norswap.sigh.interpreter.Interpreter;
import norswap.sigh.interpreter.Null;
import norswap.uranium.Reactor;
import norswap.uranium.SemanticError;
import norswap.utils.IO;
import norswap.utils.TestFixture;
import norswap.utils.data.wrappers.Pair;
import norswap.utils.visitors.Walker;
import org.testng.annotations.Test;
import java.util.HashMap;
import java.util.Set;

import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertThrows;

public final class InterpreterTests extends TestFixture {

    // ---------------------------------------------------------------------------------------------

    private final SighGrammar grammar = new SighGrammar();
    private final AutumnTestFixture autumnFixture = new AutumnTestFixture();

    {
        autumnFixture.runTwice = false;
        autumnFixture.bottomClass = this.getClass();
    }

    // ---------------------------------------------------------------------------------------------

    private Grammar.rule rule;

    // ---------------------------------------------------------------------------------------------

    private void check (String input, Object expectedReturn) {
        assertNotNull(rule, "You forgot to initialize the rule field.");
        check(rule, input, expectedReturn, null);
    }

    // ---------------------------------------------------------------------------------------------

    private void check (String input, Object expectedReturn, String expectedOutput) {
        assertNotNull(rule, "You forgot to initialize the rule field.");
        check(rule, input, expectedReturn, expectedOutput);
    }

    // ---------------------------------------------------------------------------------------------

    private void check (rule rule, String input, Object expectedReturn, String expectedOutput) {
        // TODO
        // (1) write proper parsing tests
        // (2) write some kind of automated runner, and use it here

        autumnFixture.rule = rule;
        ParseResult parseResult = autumnFixture.success(input);
        SighNode root = parseResult.topValue();

        Reactor reactor = new Reactor();
        Walker<SighNode> walker = SemanticAnalysis.createWalker(reactor);
        Interpreter interpreter = new Interpreter(reactor);
        walker.walk(root);
        reactor.run();
        Set<SemanticError> errors = reactor.errors();

        if (!errors.isEmpty()) {
            LineMapString map = new LineMapString("<test>", input);
            String report = reactor.reportErrors(it ->
                it.toString() + " (" + ((SighNode) it).span.startString(map) + ")");
            //            String tree = AttributeTreeFormatter.format(root, reactor,
            //                    new ReflectiveFieldWalker<>(SighNode.class, PRE_VISIT, POST_VISIT));
            //            System.err.println(tree);
            throw new AssertionError(report);
        }

        Pair<String, Object> result = IO.captureStdout(() -> interpreter.interpret(root));
        assertEquals(result.b, expectedReturn);
        if (expectedOutput != null) assertEquals(result.a, expectedOutput);
    }

    // ---------------------------------------------------------------------------------------------

    private void checkExpr (String input, Object expectedReturn, String expectedOutput) {
        rule = grammar.root;
        check("return " + input, expectedReturn, expectedOutput);
    }

    // ---------------------------------------------------------------------------------------------

    private void checkExpr (String input, Object expectedReturn) {
        rule = grammar.root;
        check("return " + input, expectedReturn);
    }

    // ---------------------------------------------------------------------------------------------

    private void checkThrows (String input, Class<? extends Throwable> expected) {
        assertThrows(expected, () -> check(input, null));
    }

    // ---------------------------------------------------------------------------------------------

    @Test
    public void testLiteralsAndUnary () {
        checkExpr("42", 42L);
        checkExpr("42.0", 42.0d);
        checkExpr("\"hello\"", "hello");
        checkExpr("(42)", 42L);
        checkExpr("[1, 2, 3]", new Object[]{1L, 2L, 3L});
        checkExpr("null", Null.INSTANCE);

        //todo fix this
        /*
        checkExpr("!false", true);
        checkExpr("!true", false);
        checkExpr("!!true", true); */
    }

    // ---------------------------------------------------------------------------------------------

    @Test
    public void testNumericBinary () {
        checkExpr("1 + 2", 3L);
        checkExpr("2 - 1", 1L);
        checkExpr("2 * 3", 6L);
        checkExpr("2 / 3", 0L);
        checkExpr("3 / 2", 1L);
        checkExpr("2 % 3", 2L);
        checkExpr("3 % 2", 1L);
        checkExpr("2 ^ 3", 8L);
        checkExpr("3 ^ 2", 9L);
        checkExpr("2 <> 3", new Object[]{2L, 3L});
        checkExpr("3 <> 2", new Object[]{3L, 2L});

        checkExpr("1.0 + 2.0", 3.0d);
        checkExpr("2.0 - 1.0", 1.0d);
        checkExpr("2.0 * 3.0", 6.0d);
        checkExpr("2.0 / 3.0", 2d / 3d);
        checkExpr("3.0 / 2.0", 3d / 2d);
        checkExpr("2.0 % 3.0", 2.0d);
        checkExpr("3.0 % 2.0", 1.0d);
        checkExpr("2.0 ^ 3.0", 8.0d);
        checkExpr("3.0 ^ 2.0", 9.0d);
        checkExpr("2.0 <> 3.0", new Object[]{2d, 3d});
        checkExpr("3.0 <> 2.0", new Object[]{3d, 2d});

        checkExpr("1 + 2.0", 3.0d);
        checkExpr("2 - 1.0", 1.0d);
        checkExpr("2 * 3.0", 6.0d);
        checkExpr("2 / 3.0", 2d / 3d);
        checkExpr("3 / 2.0", 3d / 2d);
        checkExpr("2 % 3.0", 2.0d);
        checkExpr("3 % 2.0", 1.0d);
        checkExpr("2 ^ 3.0", 8d);
        checkExpr("3 ^ 2.0", 9d);
        checkExpr("2 <> 3.0", new Object[]{2d, 3d});
        checkExpr("3 <> 2.0", new Object[]{3d, 2d});

        checkExpr("1.0 + 2", 3.0d);
        checkExpr("2.0 - 1", 1.0d);
        checkExpr("2.0 * 3", 6.0d);
        checkExpr("2.0 / 3", 2d / 3d);
        checkExpr("3.0 / 2", 3d / 2d);
        checkExpr("2.0 % 3", 2.0d);
        checkExpr("3.0 % 2", 1.0d);
        checkExpr("2.0 ^ 3", 8d);
        checkExpr("3.0 ^ 2", 9d);
        checkExpr("2.0 <> 3", new Object[]{2d, 3d});
        checkExpr("3.0 <> 2", new Object[]{3d, 2d});
    }



    // ---------------------------------------------------------------------------------------------

    @Test
    public void testVarDecl () {
        check("var x: Int = 1; return x", 1L);
        check("var x: Float = 2.0; return x", 2d);

        check("var x: Int = 0; return x = 3", 3L);
        check("var x: String = \"0\"; return x = \"S\"", "S");

        // implicit conversions
        check("var x: Float = 1; x = 2; return x", 2.0d);
    }

    // ---------------------------------------------------------------------------------------------

    @Test
    public void testRootAndBlock () {
        rule = grammar.root;
        check("return", null);
        check("return 1", 1L);
        check("return 1; return 2", 1L);

        check("print(\"a\")", null, "a\n");
        check("print(\"a\" + 1)", null, "a1\n");
        check("print(\"a\"); print(\"b\")", null, "a\nb\n");

        check("{ print(\"a\"); print(\"b\") }", null, "a\nb\n");

        check(
            "var x: Int = 1;" +
            "{ print(\"\" + x); var x: Int = 2; print(\"\" + x) }" +
            "print(\"\" + x)",
            null, "1\n2\n1\n");
    }

    // ---------------------------------------------------------------------------------------------

    @Test
    public void testCalls () {
        check(
            "fun add (a: Int, b: Int): Int { return a + b } " +
                "return add(4, 7)",
            11L);

        HashMap<String, Object> point = new HashMap<>();
        point.put("x", 1L);
        point.put("y", 2L);

        check(
            "struct Point { var x: Int; var y: Int }" +
                "return $Point(1, 2)",
            point);

        check("var str: String = null; return print(str + 1)", "null1", "null1\n");
    }

    // ---------------------------------------------------------------------------------------------

    @Test
    public void testArrayStructAccess () {
        checkExpr("[1][0]", 1L);
        checkExpr("[1.0][0]", 1d);
        checkExpr("[1, 2][1]", 2L);

        // TODO check that this fails (& maybe improve so that it generates a better message?)
        // or change to make it legal (introduce a top type, and make it a top type array if thre
        // is no inference context available)
        // checkExpr("[].length", 0L);
        checkExpr("[1].length", 1L);
        checkExpr("[1, 2].length", 2L);

        checkThrows("var array: Int[] = null; return array[0]", NullPointerException.class);
        checkThrows("var array: Int[] = null; return array.length", NullPointerException.class);

        check("var x: Int[] = [0, 1]; x[0] = 3; return x[0]", 3L);
        checkThrows("var x: Int[] = []; x[0] = 3; return x[0]",
            ArrayIndexOutOfBoundsException.class);
        checkThrows("var x: Int[] = null; x[0] = 3",
            NullPointerException.class);

        check(
            "struct P { var x: Int; var y: Int }" +
                "return $P(1, 2).y",
            2L);

        checkThrows(
            "struct P { var x: Int; var y: Int }" +
                "var p: P = null;" +
                "return p.y",
            NullPointerException.class);

        check(
            "struct P { var x: Int; var y: Int }" +
                "var p: P = $P(1, 2);" +
                "p.y = 42;" +
                "return p.y",
            42L);

        checkThrows(
            "struct P { var x: Int; var y: Int }" +
                "var p: P = null;" +
                "p.y = 42",
            NullPointerException.class);
    }



    // ---------------------------------------------------------------------------------------------

    @Test
    public void testInference () {
        check("var array: Int[] = []", null);
        check("var array: String[] = []", null);
        check("fun use_array (array: Int[]) {} ; use_array([])", null);
    }

    // ---------------------------------------------------------------------------------------------

    @Test
    public void testTypeAsValues () {
        check("struct S{} ; return \"\"+ S", "S");
        check("struct S{} ; var type: Type = S ; return \"\"+ type", "S");
    }

    // ---------------------------------------------------------------------------------------------

    @Test public void testUnconditionalReturn()
    {
        check("fun f(): Int { if (1) return 1 else return 2 } ; return f()", 1L);
    }

    // ---------------------------------------------------------------------------------------------

    // NOTE(norswap): Not incredibly complete, but should cover the basics.

    //------------------------------------- new tests ----------------------------------------------

    @Test public void testOrderRightLeftBinary() {
        checkExpr("1 + 2 + 3", 6L);
        checkExpr("1 - 2 - 3", 2L);
        checkExpr("8 / 4 / 2", 4L);
        checkExpr("2 * 3 * 4", 24L);
        checkExpr("2 % 5 % 7", 2L);
        checkExpr("7 % 5 % 3", 1L);

        checkExpr("1.0 + 2.0 + 3.0", 6.0d);
        checkExpr("1.0 - 2.0 - 3.0", 2.0d);
        checkExpr("1.0 / 2.0 / 2.0", 1.0d);
        checkExpr("2.0 * 3.0 * 4.0", 24.0d);
        checkExpr("2.0 % 5.0 % 7.0", 2.0d);
        checkExpr("7.0 % 5.0 % 3.0", 1.0d);
    }

    @Test public void testMathPriority() {

        checkExpr("1 * 2 - 3", -1L);
        checkExpr("1 - 2 * 3 ", -5L);
        checkExpr("1 * 2 + 3", 5L);
        checkExpr("1 + 2 * 3", 7L);
        checkExpr("3 / 2 - 1", 3L);
        checkExpr("2 % 3 -1 ", 0L);
        checkExpr("3 - 2 % 3", 1L);

        checkExpr("1.0 * 2.0 - 3.0", -1.0d);
        checkExpr("1.0 - 2.0 * 3.0 ", -5.0d);
        checkExpr("1.0 * 2.0 + 3.0", 5.0d);
        checkExpr("1.0 + 2.0 * 3.0", 7.0d);
        checkExpr("3.0 / 2.0 - 1.0", 3.0d);
        checkExpr("2.0 % 3.0 -1.0 ", 0.0d);
        checkExpr("3.0 - 2.0 % 3.0", 1.0d);
    }

    @Test public void testBinariesIntOpArray(){
        checkExpr("1 + [2]", new Object[]{3L});
        checkExpr("1 + [1, 2, 3]", new Object[]{2L, 3L, 4L});
        checkExpr("1 - [2]", new Object[]{-1L});
        checkExpr("1 - [1, 2, 3]", new Object[]{0L, -1L, -2L});
        checkExpr("2 * [2]", new Object[]{4L});
        checkExpr("2 * [1, 2, 3]", new Object[]{2L, 4L, 6L});
        checkExpr("6 / [2]", new Object[]{3L});
        checkExpr("6 / [1, 2, 3]", new Object[]{6L, 3L, 2L});
        checkExpr("6 % [2]", new Object[]{0L});
        checkExpr("3 % [1, 2, 3]", new Object[]{0L, 1L, 0L});
        checkExpr("2 < [2]", new Object[]{0L});
        checkExpr("2 < [1, 2, 3]", new Object[]{0L, 0L, 1L});
        checkExpr("2 > [2]", new Object[]{0L});
        checkExpr("2 > [1, 2, 3]", new Object[]{1L, 0L, 0L});
        checkExpr("2 <= [2]", new Object[]{1L});
        checkExpr("2 <= [1, 2, 3]", new Object[]{0L, 1L, 1L});
        checkExpr("2 >= [2]", new Object[]{1L});
        checkExpr("2 >= [1, 2, 3]", new Object[]{1L, 1L, 0L});
        checkExpr("6 ^ [2]", new Object[]{36L});
        checkExpr("3 ^ [1, 2, 3]", new Object[]{3L, 9L, 27L});
        checkExpr("6 <> [2]", new Object[]{6L, 2L});
        checkExpr("3 <> [1, 2, 3]", new Object[]{3L, 1L, 2L, 3L});
        checkExpr("0 || [0]",  new Object[]{0L});
        checkExpr("0 || [1, 17, 0]",  new Object[]{1L, 1L, 0L});
        checkExpr("1 && [0]",  new Object[]{0L});
        checkExpr("1 && [17]",  new Object[]{1L});
        checkExpr("1 && [1, 117, 0]",  new Object[]{1L, 1L, 0L});
    }

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
        checkExpr("6.0 ^ [2.0]", new Object[]{36.0d});
        checkExpr("3.0 ^ [1.0, 2.0, 3.0]", new Object[]{3.0d, 9.0d, 27.0d});
        checkExpr("2.0 < [2.0]", new Object[]{0d});
        checkExpr("2.0 < [1.0, 2.0, 3.0]", new Object[]{0d, 0d, 1d});
        checkExpr("2.0 > [2.0]", new Object[]{0d});
        checkExpr("2.0 > [1.0, 2.0, 3.0]", new Object[]{1d, 0d, 0d});
        checkExpr("2.0 <= [2.0]", new Object[]{1d});
        checkExpr("2.0 <= [1.0, 2.0, 3.0]", new Object[]{0d, 1d, 1d});
        checkExpr("2.0 >= [2.0]", new Object[]{1d});
        checkExpr("2.0 >= [1.0, 2.0, 3.0]", new Object[]{1d, 1d, 0d});
        checkExpr("6.0 <> [2.0]", new Object[]{6d, 2d});
        checkExpr("3.0 <> [1.0, 2.0, 3.0]", new Object[]{3d, 1d, 2d, 3d});
        checkExpr("0.0 || [0.0]",  new Object[]{0d});
        checkExpr("0.0 || [1.0, 17.0, 0.0]",  new Object[]{1d, 1d, 0d});
        checkExpr("1.0 && [0.0]",  new Object[]{0d});
        checkExpr("1.0 && [17.0]",  new Object[]{1d});
        checkExpr("1.0 && [1.0, 117.0, 0.0]",  new Object[]{1d, 1d, 0d});

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
        checkExpr("[2] < [2]", new Object[]{0L});
        checkExpr("[2, 2, 2] < [1, 2, 3]", new Object[]{0L, 0L, 1L});
        checkExpr("[2] > [2]", new Object[]{0L});
        checkExpr("[2, 2, 2] > [1, 2, 3]", new Object[]{1L, 0L, 0L});
        checkExpr("[2] <= [2]", new Object[]{1L});
        checkExpr("[2, 2, 2] <= [1, 2, 3]", new Object[]{0L, 1L, 1L});
        checkExpr("[2] >= [2]", new Object[]{1L});
        checkExpr("[2, 2, 2] >= [1, 2, 3]", new Object[]{1L, 1L, 0L});
        checkExpr("[6] <> [2]",  new Object[]{6L, 2L});
        checkExpr("[4, 3, 2] <> [1, 2, 3]",  new Object[]{4L, 3L, 2L, 1L, 2L, 3L});
        checkExpr("[6] ^ [2]",  new Object[]{36L});
        checkExpr("[4, 3, 2] ^ [1, 2, 3]",  new Object[]{4L, 9L, 8L});
        checkExpr("[0] || [0]",  new Object[]{0L});
        checkExpr("[0, 1, 0] || [1, 1, 0]",  new Object[]{1L, 1L, 0L});
        checkExpr("[1] && [0]",  new Object[]{0L});
        checkExpr("[1] && [17]",  new Object[]{1L});
        checkExpr("[0, 1, 0] && [1, 1, 0]",  new Object[]{0L, 1L, 0L});

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
        checkExpr("[6.0] ^ [2.0]",  new Object[]{36d});
        checkExpr("[4.0, 3.0, 2.0] ^ [1.0, 2.0, 3.0]",  new Object[]{4d, 9d, 8d});
        checkExpr("[2.0] < [2.0]", new Object[]{0d});
        checkExpr("[2.0, 2.0, 2.0] < [1.0, 2.0, 3.0]", new Object[]{0d, 0d, 1d});
        checkExpr("[2.0] > [2.0]", new Object[]{0d});
        checkExpr("[2.0, 2.0, 2.0] > [1.0, 2.0, 3.0]", new Object[]{1d, 0d, 0d});
        checkExpr("[2.0] <= [2.0]", new Object[]{1d});
        checkExpr("[2.0, 2.0, 2.0] <= [1.0, 2.0, 3.0]", new Object[]{0d, 1d, 1d});
        checkExpr("[2.0] >= [2.0]", new Object[]{1d});
        checkExpr("[2.0, 2.0, 2.0] >= [1.0, 2.0, 3.0]", new Object[]{1d, 1d, 0d});
        checkExpr("[6.0] <> [2.0]",  new Object[]{6d, 2d});
        checkExpr("[4.0, 3.0, 2.0] <> [1.0, 2.0, 3.0]",  new Object[]{4d, 3d, 2d, 1d, 2d, 3d});
        checkExpr("[0.0] || [0.0]",  new Object[]{0d});
        checkExpr("[0.0, 1.0, 0.0] || [1.0, 1.0, 0.0]",  new Object[]{1d, 1d, 0d});
        checkExpr("[1.0] && [0.0]",  new Object[]{0d});
        checkExpr("[1.0] && [17.0]",  new Object[]{1d});
        checkExpr("[0.0, 1.0, 0.0] && [1.0, 1.0, 0.0]",  new Object[]{0d, 1d, 0d});
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
        checkExpr("[6] ^ [2.0]",  new Object[]{36.0d});
        checkExpr("[4, 3, 2] ^ [1.0, 2.0, 3.0]",  new Object[]{4d, 9d, 8d});
        checkExpr("[2] < [2.0]", new Object[]{0d});
        checkExpr("[2, 2, 2] < [1.0, 2.0, 3.0]", new Object[]{0d, 0d, 1d});
        checkExpr("[2.0] > [2]", new Object[]{0d});
        checkExpr("[2.0, 2.0, 2.0] > [1, 2, 3]", new Object[]{1d, 0d, 0d});
        checkExpr("[2.0] <= [2]", new Object[]{1d});
        checkExpr("[2.0, 2.0, 2.0] <= [1, 2, 3]", new Object[]{0d, 1d, 1d});
        checkExpr("[2] >= [2.0]", new Object[]{1d});
        checkExpr("[2, 2, 2] >= [1.0, 2.0, 3.0]", new Object[]{1d, 1d, 0d});
        checkExpr("[6] <> [2.0]",  new Object[]{6d, 2d});
        checkExpr("[4, 3, 2] <> [1.0, 2.0, 3.0]",  new Object[]{4d, 3d, 2d, 1d, 2d, 3d});
    }

    // ---------------------------------------------------------------------------------------------

    @Test
    public void testOtherBinary () {

        checkExpr("1 + \"a\"", "1a");
        checkExpr("\"a\" + 1", "a1");

        checkExpr("1 == 1", 1L);
        checkExpr("1 == 2", 0L);
        checkExpr("1.0 == 1.0", 1d);
        checkExpr("1.0 == 2.0", 0d);

        checkExpr("1 == 1.0", 1d);
        checkExpr("[1] == [1]", new Object[]{1L});

        checkExpr("1 != 1", 0L);
        checkExpr("1 != 2", 1L);
        checkExpr("1.0 != 1.0", 0d);
        checkExpr("1.0 != 2.0", 1d);
        checkExpr("1 != 1.0", 0d);

        checkExpr("\"hi\" != \"hi2\"", true);
        checkExpr("[1] != [1]", new Object[]{0L});

        // test short circuit
        //TODO WTF IS THAT
        //checkExpr("true || print(\"x\") == \"y\"", true, "");
        //checkExpr("false && print(\"x\") == \"y\"", false, "");
    }

    @Test
    public void testMonadicExpressionIntArray () {


        checkExpr("+/ [1, 2, 3]", 6L);
        checkExpr("{: [5, 2, 7]",7L);
        checkExpr("./ [1, 2, 3]",6L);
        checkExpr(":/ [4, 6, 3]",2L);
        checkExpr("-/ [1, 3, 7]",5L);
        checkExpr("&/ [8, 2, 4]",1L);
        checkExpr("&/ [0, 8, 3]",0L);
        checkExpr("|/ [5, 1, 9]",1L);
        checkExpr("|/ [1, 0, 7]",1L);
        checkExpr("|/ [0, 0, 0]",0L);
        checkExpr("+: [2, 8, 1]",new Object[]{4L, 16L, 2L});
        checkExpr("*: [5, 3, 9]",new Object[]{25L, 9L, 81L});
        checkExpr("# [7, 12, 5, 8, 62, 32]", 6);
        checkExpr("! [1, 3, 5]",new Object[]{1L, 6L, 120L});
    }

    @Test
    public void testMonadicExpressionDoubleArray () {


        checkExpr("+/ [1.1, 2.2, 3.3]", 6.6d);
        checkExpr("{: [5.1, 2.2, 7.3]",7.3d);
        checkExpr("./ [1.0, 2.0, 3.0]",6d);
        checkExpr(":/ [4.0, 6.0, 3.0]",2d);
        checkExpr("-/ [1.0, 3.0, 7.0]",5d);
        checkExpr("&/ [1.0, 3.0, 7.0]",1d);
        checkExpr("|/ [1.0, 3.0, 7.0]",1d);
        checkExpr("&/ [1.0, 0.0, 7.0]",0d);
        checkExpr("|/ [1.0, 0.0, 7.0]",1.0d);
        checkExpr("|/ [0.0, 0.0, 0.0]",0d);
        checkExpr("+: [2.0, 8.0, 1.0]",new Object[]{4d, 16d, 2d});
        checkExpr("*: [5.0, 3.0, 9.0]",new Object[]{25d, 9d, 81d});
        checkExpr("# [3.0, 5.0, 6.0, 7.0]", 4);
        checkExpr("! [1.0, 3.0, 5.0]",new Object[]{1.0000000000000002d, 6.000000000000007d, 120.00000000000021d});
    }

    @Test
    public void testMonadicExpressionInt () {


        checkExpr("+/ 2", 2L);
        checkExpr("{: 2",2L);
        checkExpr("./ 2",2L);
        checkExpr(":/ 2",2L);
        checkExpr("-/ 2",2L);
        checkExpr("&/ 2",1L);
        checkExpr("&/ 0",0L);
        checkExpr("|/ 2",1L);
        checkExpr("|/ 0",0L);
        checkExpr("+: 3",6L);
        checkExpr("*: 3",9L);
        checkExpr("# 2", 1L);
        checkExpr("! 2",2L);
    }

    @Test
    public void testMonadicExpressionDouble () {


        checkExpr("+/ 1.1", 1.1d);
        checkExpr("{: 1.1",1.1d);
        checkExpr("./ 1.1",1.1d);
        checkExpr(":/ 1.1",1.1d);
        checkExpr("-/ 1.1",1.1d);
        checkExpr("&/ 1.1",1.0d);
        checkExpr("&/ 0.0",0d);
        checkExpr("|/ 1.1",1.0d);
        checkExpr("|/ 0.0",0d);
        checkExpr("+: 1.1",2.2d);
        checkExpr("*: 1.1",1.2100000000000002d);
        checkExpr("# 1.1", 1.0d);
        checkExpr(" ! 1.5",1.3293403881791384d);
    }

    @Test
    public void testMonadicForkExpressionIntArray () {

        checkExpr("(+/ + {:) [1, 2, 3]", 9L);
        checkExpr("(! + !) [1, 3, 5]", new Object[]{2L,12L, 240L});
        checkExpr("(./ / {:) [1, 2, 3]", 2L);
        checkExpr("({: % -/) [1, 2, 3]", 1L);
        checkExpr("(./ + !) [1, 3, 5]", new Object[]{16L,21L, 135L});
    }

    @Test
    public void testMonadicForkExpressionInt () {

        checkExpr("(+/ + {:) 2", 4L);
        checkExpr("(! + !) 2", 4L);
        checkExpr("(./ / {:) 2", 1L);
        checkExpr("({: % -/) 2", 0L);
        checkExpr("(./ + !) 2", 4L);
    }

    @Test
    public void testMonadicForkExpressionDoubleArray () {

        checkExpr("(+/ + {:) [1.0, 2.0, 3.0]", 9d);
        checkExpr("(! + !) [1.0, 3.0, 5.0]",new Object[]{2.0000000000000004d, 12.000000000000014d, 240.00000000000043d});
        checkExpr("(./ / {:) [1.0, 2.0, 3.0]", 2d);
        checkExpr("({: % -/) [1.0, 2.0, 3.0]", 1d);
    }

    @Test
    public void testMonadicForkExpressionDouble () {

        checkExpr("(+/ + {:) 2.0", 4d);
        checkExpr("(! + !) 1.0", 2.0000000000000004d);
        checkExpr("(./ / {:) 2.0", 1d);
        checkExpr("({: % -/) 2.0", 0d);
        checkExpr("(./ + {:) 2.0", 4d);
    }

    @Test
    public void testDiadicForkExpressionIntArrayArray () {

        checkExpr("[1, 2] (+ - *) [1, 2]", new Object[]{1L,0L});
        checkExpr("[1, 2] (/ + %) [1, 2]", new Object[]{1L,1L});
        checkExpr("[1, 2] (+ * +) [1, 2]", new Object[]{4L,16L});
        checkExpr("[1, 2] (- / /) [1, 2]", new Object[]{0L,0L});

    }

    @Test
    public void testDiadicForkExpressionInt () {

        checkExpr("1 (+ - *) 2", 1L);
        checkExpr("1 (/ + %) 2",1L);
        checkExpr("1 (+ * +) 2", 9L);
        checkExpr("3 (- / /) 2", 1L);

    }

    @Test
    public void testDiadicForkExpressionDoubleArrayArray () {

        checkExpr("[1.0, 2.0] (+ - *) [1.0, 2.0]", new Object[]{1d,0d});
        checkExpr("[1.0, 2.0] (/ + %) [1.0, 2.0]", new Object[]{1d,1d});
        checkExpr("[1.0, 2.0] (+ * +) [1.0, 2.0]", new Object[]{4d,16d});
        checkExpr("[1.0, 2.0] (- / /) [1.0, 2.0]", new Object[]{0d,0d});

    }

    @Test
    public void testDiadicForkExpressionDouble () {

        checkExpr("1.0 (+ - *) 2.0", 1d);
        checkExpr("1.0 (/ + %) 2.0",1.5d);
        checkExpr("1.0 (+ * +) 2.0", 9d);
        checkExpr("4.0 (- / /) 2.0", 1d);

    }

    // ---------------------------------------------------------------------------------------------



    @Test
    public void testIfWhile () {
        check("if (17) return 1 else return 2", 1L);
        check("if (0) return 1 else return 2", 2L);
        check("if (0.0) return 1 else if (1) return 2 else return 3 ", 2L);
        check("if (0.0) return 1 else if (0) return 2 else return 3 ", 3L);

        check("if (&/ [1,0,3]) return 1 else return 2", 2L);
        check("if (|/ [1,0,3]) return 1 else return 2", 1L);

        check("var i: Int = 0; while (i < 3) { print(\"\" + i); i = i + 1 } ", null, "0\n1\n2\n");
    }

}
