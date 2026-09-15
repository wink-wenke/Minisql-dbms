package tests.parse;

import simpledb.parse.*;
import simpledb.query.*;
import simpledb.ast.*;

/**
 * 语法分析器测试。
 * 覆盖：CREATE TABLE / INSERT / SELECT / UPDATE / DELETE 五类语句的 AST 构建，
 * 以及比较运算符、AND/OR/NOT、括号优先级、ORDER BY、语法错误检测。
 */
public class ParserTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        System.out.println("┌─ 语法分析测试 ─────────────────────────────────────────┐\n");

        testCreateTable();
        testInsert();
        testSelectBasic();
        testSelectStar();
        testUpdate();
        testDelete();
        testComparisonOps();
        testAndOrNot();
        testParentheses();
        testOrderBy();
        testSyntaxErrors();

        System.out.println("\n├─ 语法分析结果: 通过 " + passed + " / 失败 " + failed + " / 总计 " + (passed + failed) + " ─┤");
    }

    // ======================== 5 类 SQL 语句 AST 构建 ========================

    static void testCreateTable() {
        section("CREATE TABLE → AST");
        Parser p = new Parser("CREATE TABLE student(id INT, name VARCHAR(20), age INT);");
        AstNode node = p.updateCmd();
        check("返回 CreateTableNode", node instanceof CreateTableNode);
        CreateTableNode n = (CreateTableNode) node;
        check("表名 = student", n.tableName().equals("student"));
        check("字段数 = 3", n.newSchema().fields().size() == 3);
        System.out.println("    AST: CreateTable(student) → [id:INT, name:VARCHAR(20), age:INT]");
    }

    static void testInsert() {
        section("INSERT → AST");
        Parser p = new Parser("INSERT INTO student(id, name, age) VALUES (1, 'Alice', 20);");
        InsertNode node = p.insert();
        check("返回 InsertNode", node != null);
        check("表名 = student", node.tableName().equals("student"));
        check("字段数 = 3", node.fields().size() == 3);
        check("值数 = 3", node.vals().size() == 3);
        check("第1个值 = 1", node.vals().get(0).asInt() == 1);
        check("第2个值 = Alice", node.vals().get(1).asString().equals("Alice"));
        System.out.println("    AST: Insert(student) → fields=" + node.fields() + " vals=" + node.vals());
    }

    static void testSelectBasic() {
        section("SELECT ... WHERE → AST");
        Parser p = new Parser("SELECT id, name FROM student WHERE age > 18;");
        SelectNode node = p.query();
        check("返回 SelectNode", node != null);
        check("字段数 = 2", node.fields().size() == 2);
        check("字段 = [id, name]", node.fields().contains("id") && node.fields().contains("name"));
        check("表 = student", node.tables().contains("student"));
        check("谓词非空", node.pred() != null);
        System.out.println("    AST: Select([id,name], student, " + node.pred() + ")");
    }

    static void testSelectStar() {
        section("SELECT * → AST");
        Parser p = new Parser("SELECT * FROM student;");
        SelectNode node = p.query();
        check("字段 = [*]", node.fields().size() == 1 && node.fields().get(0).equals("*"));
        System.out.println("    AST: Select([*], student)");
    }

    static void testUpdate() {
        section("UPDATE → AST");
        Parser p = new Parser("UPDATE student SET age = 21 WHERE name = 'Alice';");
        UpdateNode node = p.modify();
        check("返回 UpdateNode", node != null);
        check("表名 = student", node.tableName().equals("student"));
        check("目标字段 = age", node.targetField().equals("age"));
        check("谓词非空", node.pred() != null);
        System.out.println("    AST: Update(student, age=21, " + node.pred() + ")");
    }

    static void testDelete() {
        section("DELETE → AST");
        Parser p = new Parser("DELETE FROM student WHERE id = 1;");
        DeleteNode node = p.delete();
        check("返回 DeleteNode", node != null);
        check("表名 = student", node.tableName().equals("student"));
        check("谓词非空", node.pred() != null);
        System.out.println("    AST: Delete(student, " + node.pred() + ")");
    }

    // ======================== 谓词解析 ========================

    static void testComparisonOps() {
        section("比较运算符");
        String[] ops = {"=", "!=", "<", "<=", ">", ">="};
        for (String op : ops) {
            Parser p = new Parser("a " + op + " 1");
            Predicate pred = p.predicate();
            String s = pred.toString();
            check(op + " → " + s, s.contains(op.equals("!=") ? "!=" : op));
        }
    }

    static void testAndOrNot() {
        section("AND / OR / NOT 优先级");
        // AND 优先级高于 OR
        Parser p = new Parser("a = 1 OR b = 2 AND c = 3");
        Predicate pred = p.predicate();
        String s = pred.toString();
        System.out.println("    a=1 OR b=2 AND c=3 → " + s);
        check("AND 优先于 OR", s.contains("AND") && s.contains("OR"));

        // NOT
        p = new Parser("NOT a = 1");
        pred = p.predicate();
        check("NOT 解析", pred.toString().contains("NOT"));
    }

    static void testParentheses() {
        section("括号改变优先级");
        Parser p = new Parser("(a = 1 OR b = 2) AND c = 3");
        Predicate pred = p.predicate();
        String s = pred.toString();
        System.out.println("    (a=1 OR b=2) AND c=3 → " + s);
        check("括号内 OR 优先", s.contains("OR") && s.contains("AND"));
    }

    static void testOrderBy() {
        section("ORDER BY");
        // 单字段 DESC
        Parser p = new Parser("SELECT * FROM student ORDER BY age DESC;");
        SelectNode node = p.query();
        check("ORDER BY 字段数 = 1", node.orderby().size() == 1);
        check("字段 = age, 方向 = DESC", node.orderby().get(0).field().equals("age") && !node.orderby().get(0).isAscending());

        // 多字段混合
        p = new Parser("SELECT * FROM student ORDER BY age DESC, name ASC;");
        node = p.query();
        check("多字段 ORDER BY = 2", node.orderby().size() == 2);
        System.out.println("    ORDER BY age DESC, name ASC → " + node.orderby());
    }

    // ======================== 语法错误检测 ========================

    static void testSyntaxErrors() {
        section("语法错误检测");

        // SELECT 缺字段
        try {
            new Parser("SELECT FROM student;").query();
            check("SELECT 缺字段应报错", false);
        } catch (BadSyntaxException e) {
            check("SELECT 缺字段: " + e.getMessage(), true);
            System.out.println("    ✓ " + e.getMessage());
        }

        // 缺右括号
        try {
            new Parser("SELECT id FROM student WHERE (a = 1;").query();
            check("缺右括号应报错", false);
        } catch (BadSyntaxException e) {
            check("缺右括号: " + e.getMessage(), true);
            System.out.println("    ✓ " + e.getMessage());
        }

        // INSERT 缺 VALUES
        try {
            new Parser("INSERT INTO student(id) ;").updateCmd();
            check("INSERT 缺 VALUES 应报错", false);
        } catch (BadSyntaxException e) {
            check("INSERT 缺 VALUES: " + e.getMessage(), true);
            System.out.println("    ✓ " + e.getMessage());
        }

        // DELETE 缺 FROM
        try {
            new Parser("DELETE student WHERE id = 1;").updateCmd();
            check("DELETE 缺 FROM 应报错", false);
        } catch (BadSyntaxException e) {
            check("DELETE 缺 FROM: " + e.getMessage(), true);
            System.out.println("    ✓ " + e.getMessage());
        }

        // 空输入
        try {
            new Parser("").updateCmd();
            check("空输入应报错", false);
        } catch (BadSyntaxException e) {
            check("空输入: " + e.getMessage(), true);
            System.out.println("    ✓ " + e.getMessage());
        }
    }

    // ======================== 辅助方法 ========================

    static void section(String name) {
        System.out.println("  [" + name + "]");
    }

    static void check(String desc, boolean condition) {
        if (condition) {
            passed++;
        } else {
            failed++;
            System.out.println("    [FAIL] " + desc);
        }
    }
}
