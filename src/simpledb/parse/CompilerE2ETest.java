package simpledb.parse;

import simpledb.plan.*;
import simpledb.query.Scan;
import simpledb.server.SimpleDB;
import simpledb.tx.Transaction;
import java.io.File;

/**
 * 编译器模块端到端测试。
 * <p>
 * 完整流水线验证：
 * SQL → Lexer → Parser → Semantic → Plan → Optimizer → Execute → 结果
 * <p>
 * 测试内容：
 * 1. CREATE TABLE + INSERT + SELECT + DELETE 完整闭环
 * 2. 比较运算符 = != > >= < <=
 * 3. AND / OR / NOT 条件
 * 4. SELECT *
 * 5. 语义错误检测（表/列不存在、类型不匹配）
 * 6. 优化效果验证（Filter[TRUE] 被消除）
 * 7. Plan 可视化输出
 * 8. 错误不崩溃
 */
public class CompilerE2ETest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        System.out.println("===== 编译器端到端测试 =====\n");

        // 清除上次运行的数据库目录，确保干净环境
        deleteDir("e2etest");

        SimpleDB db = new SimpleDB("e2etest");
        Transaction tx = db.newTx();
        Planner planner = db.planner();

        // ===== 建表 =====
        section("CREATE TABLE");
        planner.executeUpdate("CREATE TABLE student(id INT, name VARCHAR(20), age INT)", tx);
        planner.executeUpdate("CREATE TABLE course(cid INT, title VARCHAR(30), score INT)", tx);
        check("建表成功", true);

        // ===== 插入数据 =====
        section("INSERT");
        planner.executeUpdate("INSERT INTO student(id, name, age) VALUES (1, 'Alice', 20)", tx);
        planner.executeUpdate("INSERT INTO student(id, name, age) VALUES (2, 'Bob', 17)", tx);
        planner.executeUpdate("INSERT INTO student(id, name, age) VALUES (3, 'Charlie', 22)", tx);
        planner.executeUpdate("INSERT INTO student(id, name, age) VALUES (4, 'David', 19)", tx);
        planner.executeUpdate("INSERT INTO course(cid, title, score) VALUES (101, 'Math', 90)", tx);
        check("插入成功", true);

        // ===== SELECT 基础查询 =====
        section("SELECT 基础");
        int count = countQuery(planner, tx, "SELECT id, name FROM student WHERE age > 18");
        check("age>18 应返回3条 (Alice,Charlie,David), 实际=" + count, count == 3);

        // ===== 比较运算符 =====
        section("比较运算符");
        count = countQuery(planner, tx, "SELECT id FROM student WHERE id != 1");
        check("id!=1 应返回3条, 实际=" + count, count == 3);

        count = countQuery(planner, tx, "SELECT id FROM student WHERE age >= 20");
        check("age>=20 应返回2条, 实际=" + count, count == 2);

        count = countQuery(planner, tx, "SELECT id FROM student WHERE age <= 17");
        check("age<=17 应返回1条, 实际=" + count, count == 1);

        count = countQuery(planner, tx, "SELECT id FROM student WHERE id < 3");
        check("id<3 应返回2条, 实际=" + count, count == 2);

        count = countQuery(planner, tx, "SELECT id FROM student WHERE id = 2");
        check("id=2 应返回1条, 实际=" + count, count == 1);

        // ===== AND / OR / NOT =====
        section("AND / OR / NOT");
        count = countQuery(planner, tx, "SELECT id FROM student WHERE age > 18 AND id != 3");
        check("age>18 AND id!=3 应返回2条, 实际=" + count, count == 2);

        count = countQuery(planner, tx, "SELECT id FROM student WHERE age > 20 OR age < 18");
        check("age>20 OR age<18 应返回2条, 实际=" + count, count == 2);

        count = countQuery(planner, tx, "SELECT id FROM student WHERE NOT age > 20");
        check("NOT age>20 应返回3条, 实际=" + count, count == 3);

        count = countQuery(planner, tx, "SELECT id FROM student WHERE (age > 18 OR id = 1) AND id != 3");
        check("括号条件 应返回2条, 实际=" + count, count == 2);

        // ===== SELECT * =====
        section("SELECT *");
        count = countQuery(planner, tx, "SELECT * FROM student");
        check("SELECT * 应返回4条, 实际=" + count, count == 4);

        // ===== ORDER BY =====
        section("ORDER BY");
        try {
            count = countQuery(planner, tx, "SELECT id, name FROM student ORDER BY age");
            check("ORDER BY age 应返回4条, 实际=" + count, count == 4);
        } catch (Exception e) {
            e.printStackTrace();
            check("ORDER BY 异常: " + e.getClass().getSimpleName(), false);
        }

        // ===== GROUP BY =====
        section("GROUP BY");
        try {
            Plan gp = planner.createQueryPlan("SELECT age, countofid FROM student GROUP BY age", tx);
            Scan gs = gp.open();
            int gcount = 0;
            while (gs.next()) gcount++;
            gs.close();
            check("GROUP BY age 应返回4组, 实际=" + gcount, gcount == 4);
        } catch (Exception e) {
            check("GROUP BY 异常: " + e.getMessage(), false);
        }

        // ===== Plan 可视化 =====
        section("Plan 可视化");
        Plan p = planner.createQueryPlan("SELECT id, name FROM student WHERE age > 18 AND id != 3", tx);
        String viz = Optimizer.visualize(p);
        System.out.println("  " + viz.replace("\n", "\n  "));
        check("可视化包含 Project", viz.contains("Project"));
        check("可视化包含 Filter", viz.contains("Filter"));
        check("可视化包含 SeqScan", viz.contains("SeqScan"));

        // ===== DELETE =====
        section("DELETE");
        planner.executeUpdate("DELETE FROM student WHERE id = 1", tx);
        count = countQuery(planner, tx, "SELECT * FROM student");
        check("删除后应剩3条, 实际=" + count, count == 3);

        // ===== 语义错误检测 =====
        section("语义错误检测");

        // 表不存在
        try {
            planner.createQueryPlan("SELECT id FROM nonexistent", tx);
            check("表不存在应报错", false);
        } catch (SemanticError e) {
            check("表不存在: " + e.getMessage(), e.getMessage().contains("不存在"));
        } catch (Exception e) {
            check("表不存在应抛SemanticError, 实际=" + e.getClass().getSimpleName(), false);
        }

        // 列不存在
        try {
            planner.createQueryPlan("SELECT score FROM student", tx);
            check("列不存在应报错", false);
        } catch (SemanticError e) {
            check("列不存在: " + e.getMessage(), e.getMessage().contains("score"));
        }

        // 类型不匹配
        try {
            planner.createQueryPlan("SELECT id FROM student WHERE age = 'Alice'", tx);
            check("类型不匹配应报错", false);
        } catch (SemanticError e) {
            check("类型不匹配: " + e.getMessage(), e.getMessage().contains("类型"));
        }

        // INSERT 列数不匹配
        try {
            planner.executeUpdate("INSERT INTO student(id, name) VALUES (1, 'Alice', 20)", tx);
            check("列数不匹配应报错", false);
        } catch (SemanticError e) {
            check("列数不匹配: " + e.getMessage(), e.getMessage().contains("列数"));
        }

        // INSERT 类型不匹配
        try {
            planner.executeUpdate("INSERT INTO student(id, name, age) VALUES ('x', 'Alice', 20)", tx);
            check("INSERT类型不匹配应报错", false);
        } catch (SemanticError e) {
            check("INSERT类型不匹配: " + e.getMessage(), e.getMessage().contains("INT"));
        }

        // ===== 语法错误检测 =====
        section("语法错误检测");
        try {
            planner.createQueryPlan("SELECT FROM student", tx);
            check("语法错误应报错", false);
        } catch (BadSyntaxException e) {
            check("语法错误含位置: " + e.getMessage(), e.getLine() > 0);
        }

        // ===== 优化验证 =====
        section("优化验证");
        p = planner.createQueryPlan("SELECT * FROM student WHERE 1=1", tx);
        viz = Optimizer.visualize(p);
        check("WHERE 1=1 优化后无Filter: " + viz.trim(), !viz.contains("Filter"));

        // ===== 算术表达式测试 =====
        section("算术表达式");
        try {
            // 测试算术表达式在 WHERE 子句中的使用
            // 注意：此时 Alice 已被删除，剩下 Bob(17), Charlie(22), David(19)
            // age + 2 > 20 → age > 18，应返回 2 条 (Charlie:22, David:19)
            count = countQuery(planner, tx, "SELECT id FROM student WHERE age + 2 > 20");
            check("age + 2 > 20 应返回2条, 实际=" + count, count == 2);

            // 测试乘法
            // age * 2 > 40 → age > 20，应返回 1 条 (Charlie:22)
            count = countQuery(planner, tx, "SELECT id FROM student WHERE age * 2 > 40");
            check("age * 2 > 40 应返回1条, 实际=" + count, count == 1);

            // 测试常量折叠
            // age > 10 + 8 → age > 18，应返回 2 条 (Charlie:22, David:19)
            count = countQuery(planner, tx, "SELECT id FROM student WHERE age > 10 + 8");
            check("age > 10 + 8 (常量折叠) 应返回2条, 实际=" + count, count == 2);
        } catch (Exception e) {
            check("算术表达式异常: " + e.getMessage(), false);
        }

        // ===== Predicate Pushdown 测试 =====
        section("Predicate Pushdown");
        try {
            p = planner.createQueryPlan("SELECT id, name FROM student WHERE age > 18", tx);
            viz = Optimizer.visualize(p);
            System.out.println("  " + viz.replace("\n", "\n  "));
            check("Predicate Pushdown: Plan 包含 Filter", viz.contains("Filter"));
        } catch (Exception e) {
            check("Predicate Pushdown 异常: " + e.getMessage(), false);
        }

        tx.commit();
        System.out.println("\n===== 测试结果 =====");
        System.out.println("通过: " + passed + ", 失败: " + failed + ", 总计: " + (passed + failed));
    }

    /**
     * 执行查询并返回结果行数。
     */
    private static int countQuery(Planner planner, Transaction tx, String sql) {
        Plan p = planner.createQueryPlan(sql, tx);
        Scan s = p.open();
        int count = 0;
        while (s.next()) count++;
        s.close();
        return count;
    }

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

    /**
     * 递归删除目录（用于清理上次运行的数据库）。
     */
    static void deleteDir(String path) {
        File dir = new File(path);
        if (dir.exists()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File f : files) {
                    if (f.isDirectory()) deleteDir(f.getPath());
                    else f.delete();
                }
            }
            dir.delete();
        }
    }
}
