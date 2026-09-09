package simpledb.parse;

import simpledb.query.*;

/**
 * Parser 测试类。
 * 测试内容：
 * 1. 基础 SELECT / INSERT / DELETE / CREATE TABLE
 * 2. 比较运算符 = != < <= > >=
 * 3. AND / OR / NOT 组合
 * 4. 括号与优先级
 * 5. SELECT *
 * 6. 语法错误处理
 */
public class ParserTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        System.out.println("===== Parser 测试开始 =====\n");

        testCreateTable();
        testInsert();
        testSelectBasic();
        testSelectStar();
        testDelete();
        testComparisonOps();
        testAndOrNot();
        testParentheses();
        testPriority();
        testFullSQL();
        testSyntaxErrors();

        System.out.println("\n===== 测试结果 =====");
        System.out.println("通过: " + passed + ", 失败: " + failed + ", 总计: " + (passed + failed));
    }

    // ======================== 测试用例 ========================

    static void testCreateTable() {
        section("CREATE TABLE");
        Parser p = new Parser("CREATE TABLE student(id INT, name VARCHAR(20), age INT);");
        Object result = p.updateCmd();
        check("应返回 CreateTableData", result instanceof CreateTableData);
        CreateTableData data = (CreateTableData) result;
        check("表名应为 student", data.tableName().equals("student"));
        check("schema 应有 3 个字段", data.newSchema().fields().size() == 3);
    }

    static void testInsert() {
        section("INSERT");
        Parser p = new Parser("INSERT INTO student(id, name, age) VALUES (1, 'Alice', 20);");
        InsertData data = p.insert();
        check("表名应为 student", data.tableName().equals("student"));
        check("字段数应为 3", data.fields().size() == 3);
        check("值数应为 3", data.vals().size() == 3);
        check("第1个值应为 1", data.vals().get(0).asInt() == 1);
        check("第2个值应为 Alice", data.vals().get(1).asString().equals("Alice"));
    }

    static void testSelectBasic() {
        section("SELECT 基础");
        Parser p = new Parser("SELECT id, name FROM student WHERE age > 18;");
        QueryData data = p.query();
        check("字段数应为 2", data.fields().size() == 2);
        check("第1个字段应为 id", data.fields().get(0).equals("id"));
        check("第2个字段应为 name", data.fields().get(1).equals("name"));
        check("表应为 student", data.tables().contains("student"));
        check("谓词不应为空", data.pred() != null);
    }

    static void testSelectStar() {
        section("SELECT *");
        Parser p = new Parser("SELECT * FROM student;");
        QueryData data = p.query();
        check("字段数应为 1", data.fields().size() == 1);
        check("字段应为 *", data.fields().get(0).equals("*"));
    }

    static void testDelete() {
        section("DELETE");
        Parser p = new Parser("DELETE FROM student WHERE id = 1;");
        DeleteData data = p.delete();
        check("表名应为 student", data.tableName().equals("student"));
        check("谓词不应为空", data.pred() != null);
    }

    static void testComparisonOps() {
        section("比较运算符");

        // =
        Parser p = new Parser("a = 1");
        Predicate pred = p.predicate();
        String s = pred.toString();
        check("= 解析: " + s, s.contains("="));

        // !=
        p = new Parser("a != 1");
        pred = p.predicate();
        s = pred.toString();
        check("!= 解析: " + s, s.contains("!="));

        // >
        p = new Parser("a > 1");
        pred = p.predicate();
        s = pred.toString();
        check("> 解析: " + s, s.contains(">") && !s.contains(">="));

        // >=
        p = new Parser("a >= 1");
        pred = p.predicate();
        s = pred.toString();
        check(">= 解析: " + s, s.contains(">="));

        // <
        p = new Parser("a < 1");
        pred = p.predicate();
        s = pred.toString();
        check("< 解析: " + s, s.contains("<") && !s.contains("<="));

        // <=
        p = new Parser("a <= 1");
        pred = p.predicate();
        s = pred.toString();
        check("<= 解析: " + s, s.contains("<="));
    }

    static void testAndOrNot() {
        section("AND / OR / NOT");

        // AND
        Parser p = new Parser("a = 1 AND b = 2");
        Predicate pred = p.predicate();
        String s = pred.toString();
        check("AND 解析: " + s, s.contains("AND"));

        // OR
        p = new Parser("a = 1 OR b = 2");
        pred = p.predicate();
        s = pred.toString();
        check("OR 解析: " + s, s.contains("OR"));

        // NOT
        p = new Parser("NOT a = 1");
        pred = p.predicate();
        s = pred.toString();
        check("NOT 解析: " + s, s.contains("NOT"));

        // AND + OR
        p = new Parser("a = 1 OR b = 2 AND c = 3");
        pred = p.predicate();
        s = pred.toString();
        check("AND+OR 解析（AND 优先）: " + s,
                s.contains("AND") && s.contains("OR"));
    }

    static void testParentheses() {
        section("括号");

        // (a = 1 OR b = 2) AND c = 3
        Parser p = new Parser("(a = 1 OR b = 2) AND c = 3");
        Predicate pred = p.predicate();
        String s = pred.toString();
        check("括号解析: " + s, s.contains("OR") && s.contains("AND"));

        // NOT (a = 1 OR b = 2)
        p = new Parser("NOT (a = 1 OR b = 2)");
        pred = p.predicate();
        s = pred.toString();
        check("NOT + 括号: " + s, s.contains("NOT") && s.contains("OR"));
    }

    static void testPriority() {
        section("表达式优先级");

        // a = 1 OR b = 2 AND c = 3
        // 应解析为: a=1 OR (b=2 AND c=3)
        Parser p = new Parser("a = 1 OR b = 2 AND c = 3");
        Predicate pred = p.predicate();
        String s = pred.toString();
        System.out.println("  a=1 OR b=2 AND c=3 -> " + s);
        check("顶层应为 OR", s.startsWith("(") && s.contains(" OR "));

        // NOT a = 1 AND b = 2
        // 应解析为: (NOT a=1) AND b=2
        p = new Parser("NOT a = 1 AND b = 2");
        pred = p.predicate();
        s = pred.toString();
        System.out.println("  NOT a=1 AND b=2 -> " + s);
        check("应包含 NOT 和 AND", s.contains("NOT") && s.contains("AND"));
    }

    static void testFullSQL() {
        section("完整 SQL 语句");

        // SELECT with complex WHERE
        Parser p = new Parser("SELECT id, name FROM student WHERE age > 18 AND id != 3;");
        QueryData data = p.query();
        check("SELECT 复杂条件: 字段数=2", data.fields().size() == 2);
        check("SELECT 复杂条件: 表名=student", data.tables().contains("student"));
        System.out.println("  WHERE -> " + data.pred().toString());

        // SELECT with OR and NOT
        p = new Parser("SELECT * FROM student WHERE age > 18 OR NOT (id = 3);");
        data = p.query();
        check("SELECT OR NOT: 字段=*", data.fields().get(0).equals("*"));
        System.out.println("  WHERE -> " + data.pred().toString());

        // DELETE with complex condition
        p = new Parser("DELETE FROM student WHERE id = 1 OR (age < 18 AND name = 'Bob');");
        DeleteData del = p.delete();
        check("DELETE 复杂条件: 表名=student", del.tableName().equals("student"));
        System.out.println("  WHERE -> " + del.pred().toString());

        // UPDATE
        p = new Parser("UPDATE student SET age = 21 WHERE name = 'Alice';");
        ModifyData mod = p.modify();
        check("UPDATE: 表名=student", mod.tableName().equals("student"));
        check("UPDATE: 字段=age", mod.targetField().equals("age"));
    }

    static void testSyntaxErrors() {
        section("语法错误处理");

        // 不完整的 SQL
        try {
            Parser p = new Parser("SELECT FROM student;");
            p.query();
            check("不完整SQL应报错", false);
        } catch (BadSyntaxException e) {
            check("不完整SQL错误含位置", e.getLine() > 0);
            System.out.println("  [预期错误] " + e.getMessage());
        }

        // 缺少右括号
        try {
            Parser p = new Parser("SELECT id FROM student WHERE (a = 1;");
            p.query();
            check("缺右括号应报错", false);
        } catch (BadSyntaxException e) {
            check("缺右括号错误含位置", e.getLine() > 0);
            System.out.println("  [预期错误] " + e.getMessage());
        }
    }

    // ======================== 辅助方法 ========================

    static void section(String name) {
        System.out.println("[" + name + "]");
    }

    static void check(String desc, boolean condition) {
        if (condition) {
            passed++;
        } else {
            failed++;
            System.out.println("  [FAIL] " + desc);
        }
    }
}
