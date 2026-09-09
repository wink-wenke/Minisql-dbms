package simpledb.parse;

import java.util.*;

/**
 * Lexer 测试类。
 * 测试内容：
 * 1. 关键字识别（大小写不敏感）
 * 2. 标识符识别
 * 3. 整数常量和字符串常量
 * 4. 比较运算符 = != < <= > >=
 * 5. 算术运算符 + - * /
 * 6. 分隔符 ( ) , ;
 * 7. 单行注释 -- 和多行注释 /* ... *​/
 * 8. 字符串转义 ''
 * 9. 未闭合字符串错误
 * 10. 非法字符错误
 * 11. 完整 SQL 语句
 */
public class LexerTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        System.out.println("===== Lexer 测试开始 =====\n");

        // --- 基础 Token 测试 ---
        testKeywords();
        testIdentifiers();
        testIntConstants();
        testStringConstants();
        testComparisonOperators();
        testArithmeticOperators();
        testDelimiters();
        testComments();
        testStringEscape();
        testErrorCases();

        // --- 完整 SQL 语句测试 ---
        testFullSQL();

        System.out.println("\n===== 测试结果 =====");
        System.out.println("通过: " + passed + ", 失败: " + failed + ", 总计: " + (passed + failed));
    }

    // ======================== 测试用例 ========================

    static void testKeywords() {
        section("关键字识别（大小写不敏感）");
        List<Token> tokens = Lexer.tokenize("SELECT select Select FROM from WHERE where");
        assertTokenType(tokens.get(0), TokenType.SELECT, "SELECT");
        assertTokenType(tokens.get(1), TokenType.SELECT, "select");
        assertTokenType(tokens.get(2), TokenType.SELECT, "Select");
        assertTokenType(tokens.get(3), TokenType.FROM, "FROM");
        assertTokenType(tokens.get(4), TokenType.FROM, "from");
        assertTokenType(tokens.get(5), TokenType.WHERE, "WHERE");
        assertTokenType(tokens.get(6), TokenType.WHERE, "where");

        // 新增关键字
        tokens = Lexer.tokenize("OR or NOT not");
        assertTokenType(tokens.get(0), TokenType.OR, "OR");
        assertTokenType(tokens.get(1), TokenType.OR, "or");
        assertTokenType(tokens.get(2), TokenType.NOT, "NOT");
        assertTokenType(tokens.get(3), TokenType.NOT, "not");
    }

    static void testIdentifiers() {
        section("标识符识别");
        List<Token> tokens = Lexer.tokenize("student age user_name _id");
        assertTokenType(tokens.get(0), TokenType.IDENTIFIER, "student");
        assertTokenType(tokens.get(1), TokenType.IDENTIFIER, "age");
        assertTokenType(tokens.get(2), TokenType.IDENTIFIER, "user_name");
        assertTokenType(tokens.get(3), TokenType.IDENTIFIER, "_id");
    }

    static void testIntConstants() {
        section("整数常量");
        List<Token> tokens = Lexer.tokenize("0 42 100 9999");
        assertTokenType(tokens.get(0), TokenType.INT_CONST, "0");
        assertTokenType(tokens.get(1), TokenType.INT_CONST, "42");
        assertTokenType(tokens.get(2), TokenType.INT_CONST, "100");
        assertTokenType(tokens.get(3), TokenType.INT_CONST, "9999");
    }

    static void testStringConstants() {
        section("字符串常量");
        List<Token> tokens = Lexer.tokenize("'Alice' 'Bob' 'Hello World'");
        assertTokenType(tokens.get(0), TokenType.STRING_CONST, "'Alice'");
        assertTokenType(tokens.get(1), TokenType.STRING_CONST, "'Bob'");
        assertTokenType(tokens.get(2), TokenType.STRING_CONST, "'Hello World'");
    }

    static void testComparisonOperators() {
        section("比较运算符");
        List<Token> tokens = Lexer.tokenize("= != < <= > >=");
        assertTokenType(tokens.get(0), TokenType.EQUALS, "=");
        assertTokenType(tokens.get(1), TokenType.NOT_EQUALS, "!=");
        assertTokenType(tokens.get(2), TokenType.LESS, "<");
        assertTokenType(tokens.get(3), TokenType.LESS_EQUALS, "<=");
        assertTokenType(tokens.get(4), TokenType.GREATER, ">");
        assertTokenType(tokens.get(5), TokenType.GREATER_EQUALS, ">=");
    }

    static void testArithmeticOperators() {
        section("算术运算符");
        List<Token> tokens = Lexer.tokenize("+ - * /");
        assertTokenType(tokens.get(0), TokenType.PLUS, "+");
        assertTokenType(tokens.get(1), TokenType.MINUS, "-");
        assertTokenType(tokens.get(2), TokenType.STAR, "*");
        assertTokenType(tokens.get(3), TokenType.DIVIDE, "/");
    }

    static void testDelimiters() {
        section("分隔符");
        List<Token> tokens = Lexer.tokenize("( ) , ;");
        assertTokenType(tokens.get(0), TokenType.LPAREN, "(");
        assertTokenType(tokens.get(1), TokenType.RPAREN, ")");
        assertTokenType(tokens.get(2), TokenType.COMMA, ",");
        assertTokenType(tokens.get(3), TokenType.SEMICOLON, ";");
    }

    static void testComments() {
        section("注释处理");
        // 单行注释
        List<Token> tokens = Lexer.tokenize("SELECT -- this is a comment\nFROM");
        assertTokenType(tokens.get(0), TokenType.SELECT, "SELECT");
        assertTokenType(tokens.get(1), TokenType.FROM, "FROM");

        // 多行注释
        tokens = Lexer.tokenize("SELECT /* block\ncomment */ FROM");
        assertTokenType(tokens.get(0), TokenType.SELECT, "SELECT");
        assertTokenType(tokens.get(1), TokenType.FROM, "FROM");
    }

    static void testStringEscape() {
        section("字符串转义（'' 转义单引号）");
        List<Token> tokens = Lexer.tokenize("'Tom''s book'");
        assertTokenType(tokens.get(0), TokenType.STRING_CONST, "'Tom''s book'");
        // 验证 stringValue() 正确去除引号并保留转义后的单引号
        String val = tokens.get(0).stringValue();
        check("stringValue 应为 Tom's book", val.equals("Tom's book"));
    }

    static void testErrorCases() {
        section("错误处理");
        // 非法字符
        try {
            Lexer.tokenize("SELECT @ FROM student");
            check("应抛出异常（非法字符 @）", false);
        } catch (BadSyntaxException e) {
            check("非法字符错误应包含位置信息", e.getLine() > 0 && e.getColumn() > 0);
            System.out.println("  [预期错误] " + e.getMessage());
        }

        // 未闭合字符串
        try {
            Lexer.tokenize("'Alice");
            check("应抛出异常（未闭合字符串）", false);
        } catch (BadSyntaxException e) {
            check("未闭合字符串错误应包含位置信息", e.getLine() > 0 && e.getColumn() > 0);
            System.out.println("  [预期错误] " + e.getMessage());
        }
    }

    static void testFullSQL() {
        section("完整 SQL 语句");

        // CREATE TABLE
        System.out.println("  SQL: CREATE TABLE student(id INT, name VARCHAR, age INT);");
        List<Token> tokens = Lexer.tokenize("CREATE TABLE student(id INT, name VARCHAR, age INT);");
        printTokens(tokens);

        // INSERT
        System.out.println("  SQL: INSERT INTO student(id, name, age) VALUES (1, 'Alice', 20);");
        tokens = Lexer.tokenize("INSERT INTO student(id, name, age) VALUES (1, 'Alice', 20);");
        printTokens(tokens);

        // SELECT with comparison operators
        System.out.println("  SQL: SELECT id, name FROM student WHERE age > 18 AND id != 3;");
        tokens = Lexer.tokenize("SELECT id, name FROM student WHERE age > 18 AND id != 3;");
        printTokens(tokens);

        // DELETE
        System.out.println("  SQL: DELETE FROM student WHERE id = 1;");
        tokens = Lexer.tokenize("DELETE FROM student WHERE id = 1;");
        printTokens(tokens);

        // OR and NOT
        System.out.println("  SQL: SELECT * FROM student WHERE age > 18 OR NOT (id = 3);");
        tokens = Lexer.tokenize("SELECT * FROM student WHERE age > 18 OR NOT (id = 3);");
        printTokens(tokens);

        // 带注释的 SQL
        System.out.println("  SQL: SELECT id /* comment */ FROM student -- end comment");
        tokens = Lexer.tokenize("SELECT id /* comment */ FROM student -- end comment");
        printTokens(tokens);
    }

    // ======================== 辅助方法 ========================

    static void section(String name) {
        System.out.println("[" + name + "]");
    }

    static void assertTokenType(Token token, TokenType expectedType, String expectedLexeme) {
        check("Token 类型应为 " + expectedType + " (实际: " + token.type() + ")",
                token.type() == expectedType);
        check("Token lexeme 应为 '" + expectedLexeme + "' (实际: '" + token.lexeme() + "')",
                token.lexeme().equals(expectedLexeme));
    }

    static void check(String desc, boolean condition) {
        if (condition) {
            passed++;
        } else {
            failed++;
            System.out.println("  [FAIL] " + desc);
        }
    }

    static void printTokens(List<Token> tokens) {
        StringBuilder sb = new StringBuilder("    ");
        for (Token t : tokens) {
            sb.append(String.format("[%s '%s'] ", t.type(), t.lexeme()));
        }
        System.out.println(sb.toString());
    }
}
