package tests.parse;

import java.util.*;

import simpledb.parse.BadSyntaxException;
import simpledb.parse.Lexer;
import simpledb.parse.Token;
import simpledb.parse.TokenType;

/**
 * 词法分析器测试。
 * 覆盖：关键字、标识符、常量、运算符、分隔符、注释、转义、错误处理。
 */
public class LexerTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        System.out.println("┌─ 词法分析测试 ─────────────────────────────────────────┐\n");

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
        testFullSQL();

        System.out.println("\n├─ 词法分析结果: 通过 " + passed + " / 失败 " + failed + " / 总计 " + (passed + failed) + " ─┤");
    }

    // ======================== 测试用例 ========================

    static void testKeywords() {
        section("关键字识别（大小写不敏感）");
        // DML 关键字
        List<Token> tokens = Lexer.tokenize("SELECT select Select FROM from WHERE where");
        assertToken(tokens.get(0), TokenType.SELECT, "SELECT");
        assertToken(tokens.get(1), TokenType.SELECT, "select");
        assertToken(tokens.get(2), TokenType.SELECT, "Select");
        assertToken(tokens.get(3), TokenType.FROM, "FROM");
        assertToken(tokens.get(4), TokenType.FROM, "from");
        assertToken(tokens.get(5), TokenType.WHERE, "WHERE");
        assertToken(tokens.get(6), TokenType.WHERE, "where");

        // INSERT / DELETE / UPDATE
        tokens = Lexer.tokenize("INSERT INTO VALUES DELETE FROM UPDATE SET");
        assertToken(tokens.get(0), TokenType.INSERT, "INSERT");
        assertToken(tokens.get(1), TokenType.INTO, "INTO");
        assertToken(tokens.get(2), TokenType.VALUES, "VALUES");
        assertToken(tokens.get(3), TokenType.DELETE, "DELETE");
        assertToken(tokens.get(4), TokenType.FROM, "FROM");
        assertToken(tokens.get(5), TokenType.UPDATE, "UPDATE");
        assertToken(tokens.get(6), TokenType.SET, "SET");

        // DDL 关键字
        tokens = Lexer.tokenize("CREATE TABLE VIEW INDEX ON AS");
        assertToken(tokens.get(0), TokenType.CREATE, "CREATE");
        assertToken(tokens.get(1), TokenType.TABLE, "TABLE");
        assertToken(tokens.get(2), TokenType.VIEW, "VIEW");
        assertToken(tokens.get(3), TokenType.INDEX, "INDEX");
        assertToken(tokens.get(4), TokenType.ON, "ON");
        assertToken(tokens.get(5), TokenType.AS, "AS");

        // 数据类型
        tokens = Lexer.tokenize("INT VARCHAR");
        assertToken(tokens.get(0), TokenType.INT, "INT");
        assertToken(tokens.get(1), TokenType.VARCHAR, "VARCHAR");

        // 逻辑运算符
        tokens = Lexer.tokenize("AND OR NOT");
        assertToken(tokens.get(0), TokenType.AND, "AND");
        assertToken(tokens.get(1), TokenType.OR, "OR");
        assertToken(tokens.get(2), TokenType.NOT, "NOT");

        // ORDER / GROUP / EXPLAIN
        tokens = Lexer.tokenize("ORDER BY GROUP EXPLAIN ASC DESC");
        assertToken(tokens.get(0), TokenType.ORDER, "ORDER");
        assertToken(tokens.get(1), TokenType.BY, "BY");
        assertToken(tokens.get(2), TokenType.GROUP, "GROUP");
        assertToken(tokens.get(3), TokenType.EXPLAIN, "EXPLAIN");
        assertToken(tokens.get(4), TokenType.ASC, "ASC");
        assertToken(tokens.get(5), TokenType.DESC, "DESC");
    }

    static void testIdentifiers() {
        section("标识符识别");
        List<Token> tokens = Lexer.tokenize("student age user_name _id t1");
        assertToken(tokens.get(0), TokenType.IDENTIFIER, "student");
        assertToken(tokens.get(1), TokenType.IDENTIFIER, "age");
        assertToken(tokens.get(2), TokenType.IDENTIFIER, "user_name");
        assertToken(tokens.get(3), TokenType.IDENTIFIER, "_id");
        assertToken(tokens.get(4), TokenType.IDENTIFIER, "t1");
    }

    static void testIntConstants() {
        section("整数常量");
        List<Token> tokens = Lexer.tokenize("0 42 100 9999 -5");
        assertToken(tokens.get(0), TokenType.INT_CONST, "0");
        assertToken(tokens.get(1), TokenType.INT_CONST, "42");
        assertToken(tokens.get(2), TokenType.INT_CONST, "100");
        assertToken(tokens.get(3), TokenType.INT_CONST, "9999");
        // -5 → MINUS + INT_CONST
        assertToken(tokens.get(4), TokenType.MINUS, "-");
        assertToken(tokens.get(5), TokenType.INT_CONST, "5");
    }

    static void testStringConstants() {
        section("字符串常量");
        List<Token> tokens = Lexer.tokenize("'Alice' 'Bob' 'Hello World'");
        assertToken(tokens.get(0), TokenType.STRING_CONST, "'Alice'");
        assertToken(tokens.get(1), TokenType.STRING_CONST, "'Bob'");
        assertToken(tokens.get(2), TokenType.STRING_CONST, "'Hello World'");
    }

    static void testComparisonOperators() {
        section("比较运算符");
        List<Token> tokens = Lexer.tokenize("= != < <= > >=");
        assertToken(tokens.get(0), TokenType.EQUALS, "=");
        assertToken(tokens.get(1), TokenType.NOT_EQUALS, "!=");
        assertToken(tokens.get(2), TokenType.LESS, "<");
        assertToken(tokens.get(3), TokenType.LESS_EQUALS, "<=");
        assertToken(tokens.get(4), TokenType.GREATER, ">");
        assertToken(tokens.get(5), TokenType.GREATER_EQUALS, ">=");
    }

    static void testArithmeticOperators() {
        section("算术运算符");
        List<Token> tokens = Lexer.tokenize("+ - * /");
        assertToken(tokens.get(0), TokenType.PLUS, "+");
        assertToken(tokens.get(1), TokenType.MINUS, "-");
        assertToken(tokens.get(2), TokenType.STAR, "*");
        assertToken(tokens.get(3), TokenType.DIVIDE, "/");
    }

    static void testDelimiters() {
        section("分隔符");
        List<Token> tokens = Lexer.tokenize("( ) , ;");
        assertToken(tokens.get(0), TokenType.LPAREN, "(");
        assertToken(tokens.get(1), TokenType.RPAREN, ")");
        assertToken(tokens.get(2), TokenType.COMMA, ",");
        assertToken(tokens.get(3), TokenType.SEMICOLON, ";");
    }

    static void testComments() {
        section("注释处理");
        // 单行注释
        List<Token> tokens = Lexer.tokenize("SELECT -- this is a comment\nFROM");
        assertToken(tokens.get(0), TokenType.SELECT, "SELECT");
        assertToken(tokens.get(1), TokenType.FROM, "FROM");

        // 多行注释
        tokens = Lexer.tokenize("SELECT /* block\ncomment */ FROM");
        assertToken(tokens.get(0), TokenType.SELECT, "SELECT");
        assertToken(tokens.get(1), TokenType.FROM, "FROM");
    }

    static void testStringEscape() {
        section("字符串转义（'' 转义单引号）");
        List<Token> tokens = Lexer.tokenize("'Tom''s book'");
        assertToken(tokens.get(0), TokenType.STRING_CONST, "'Tom''s book'");
        String val = tokens.get(0).stringValue();
        check("stringValue 应为 Tom's book", val.equals("Tom's book"));
    }

    static void testErrorCases() {
        section("非法输入处理");
        // 非法字符
        try {
            Lexer.tokenize("SELECT @ FROM student");
            check("非法字符 @ 应抛出异常", false);
        } catch (BadSyntaxException e) {
            check("非法字符错误含行列位置", e.getLine() > 0 && e.getColumn() > 0);
            System.out.println("    ✓ " + e.getMessage());
        }

        // 未闭合字符串
        try {
            Lexer.tokenize("'Alice");
            check("未闭合字符串应抛出异常", false);
        } catch (BadSyntaxException e) {
            check("未闭合字符串错误含行列位置", e.getLine() > 0 && e.getColumn() > 0);
            System.out.println("    ✓ " + e.getMessage());
        }

        // 空输入
        List<Token> empty = Lexer.tokenize("");
        check("空输入应返回 EOF", empty.size() == 1 && empty.get(0).type() == TokenType.EOF);
    }

    static void testFullSQL() {
        section("完整 SQL 语句词法分析");

        String[] sqls = {
            "CREATE TABLE student(id INT, name VARCHAR(20), age INT);",
            "INSERT INTO student(id, name, age) VALUES (1, 'Alice', 20);",
            "SELECT id, name FROM student WHERE age > 18 AND id != 3;",
            "UPDATE student SET age = 21 WHERE name = 'Alice';",
            "DELETE FROM student WHERE id = 1;",
            "SELECT * FROM student WHERE age > 18 OR NOT (id = 3);",
            "CREATE INDEX idx_name ON student(name);",
            "SELECT id /* comment */ FROM student -- end"
        };

        for (String sql : sqls) {
            System.out.println("  SQL: " + sql);
            List<Token> tokens = Lexer.tokenize(sql);
            printTokens(tokens);
        }
    }

    // ======================== 辅助方法 ========================

    static void section(String name) {
        System.out.println("  [" + name + "]");
    }

    static void assertToken(Token token, TokenType expectedType, String expectedLexeme) {
        boolean typeOk = token.type() == expectedType;
        boolean lexOk = token.lexeme().equals(expectedLexeme);
        check("[" + expectedType + " '" + expectedLexeme + "']", typeOk && lexOk);
    }

    static void check(String desc, boolean condition) {
        if (condition) {
            passed++;
        } else {
            failed++;
            System.out.println("    [FAIL] " + desc);
        }
    }

    static void printTokens(List<Token> tokens) {
        StringBuilder sb = new StringBuilder("    → ");
        for (Token t : tokens) {
            if (t.type() == TokenType.EOF) continue;
            sb.append(String.format("[%s:%s] ", t.type(), t.lexeme()));
        }
        System.out.println(sb.toString());
    }
}
