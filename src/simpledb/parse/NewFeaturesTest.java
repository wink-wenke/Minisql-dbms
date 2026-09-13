package simpledb.parse;

import simpledb.plan.*;
import simpledb.query.Scan;
import simpledb.server.SimpleDB;
import simpledb.tx.Transaction;
import java.io.File;

/**
 * 新功能测试：算术表达式、Projection Pruning、Predicate Pushdown。
 * <p>
 * 测试内容：
 * 1. 算术表达式：+ - * / 运算
 * 2. 算术表达式在 WHERE 子句中的使用
 * 3. 常量折叠优化（算术表达式）
 * 4. Projection Pruning 验证
 * 5. Predicate Pushdown 验证
 */
public class NewFeaturesTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        System.out.println("===== 新功能测试 =====\n");

        // 清除上次运行的数据库目录，确保干净环境
        deleteDir("newfeaturetest");

        SimpleDB db = new SimpleDB("newfeaturetest");
        Transaction tx = db.newTx();
        Planner planner = db.planner();

        // ===== 建表 =====
        section("CREATE TABLE");
        planner.executeUpdate("CREATE TABLE product(id INT, name VARCHAR(20), price INT, quantity INT)", tx);
        planner.executeUpdate("INSERT INTO product(id, name, price, quantity) VALUES (1, 'Apple', 100, 50)", tx);
        planner.executeUpdate("INSERT INTO product(id, name, price, quantity) VALUES (2, 'Banana', 50, 100)", tx);
        planner.executeUpdate("INSERT INTO product(id, name, price, quantity) VALUES (3, 'Cherry', 200, 30)", tx);
        planner.executeUpdate("INSERT INTO product(id, name, price, quantity) VALUES (4, 'Date', 150, 0)", tx);
        check("建表和插入成功", true);

        // ===== 算术表达式测试 =====
        section("算术表达式");

        // 测试加法
        int count = countQuery(planner, tx,
                "SELECT id FROM product WHERE price + 50 > 150");
        check("price + 50 > 150 应返回2条 (Cherry,Date), 实际=" + count, count == 2);

        // 测试减法
        count = countQuery(planner, tx,
                "SELECT id FROM product WHERE price - 50 > 100");
        check("price - 50 > 100 应返回1条 (Cherry), 实际=" + count, count == 1);

        // 测试乘法
        count = countQuery(planner, tx,
                "SELECT id FROM product WHERE price * 2 > 300");
        check("price * 2 > 300 应返回1条 (Cherry), 实际=" + count, count == 1);

        // 测试除法（整数除法）
        count = countQuery(planner, tx,
                "SELECT id FROM product WHERE price / 2 < 80");
        check("price / 2 < 80 应返回3条 (Banana,Cherry,Date), 实际=" + count, count == 3);

        // 测试复杂算术表达式
        count = countQuery(planner, tx,
                "SELECT id FROM product WHERE price * quantity > 5000");
        check("price * quantity > 5000 应返回1条 (Cherry), 实际=" + count, count == 1);

        // ===== 常量折叠测试 =====
        section("常量折叠（算术表达式）");

        // 测试常量折叠：10 + 5 应该被折叠为 15
        // 注意：price > 10 + 5 中，10 + 5 会被解析为算术表达式
        // 但在当前实现中，常量折叠只在 Term.simplify() 中调用
        // 所以需要确保优化器调用了 simplify
        Plan p2 = planner.createQueryPlan("SELECT id FROM product WHERE price > 10 + 5", tx);
        String viz2 = Optimizer.visualize(p2);
        System.out.println("  price > 10 + 5 Plan:");
        System.out.println("  " + viz2.replace("\n", "\n  "));

        // 验证常量折叠是否生效
        // 10 + 5 被折叠为 15，所有 price > 15 的记录都应返回
        count = countQuery(planner, tx,
                "SELECT id FROM product WHERE price > 10 + 5");
        check("price > 10 + 5 (应折叠为 15) 应返回4条, 实际=" + count, count == 4);

        // 测试常量折叠：100 * 2 应该被折叠为 200
        // price < 200 应返回 3 条 (Apple:100, Banana:50, Date:150)
        count = countQuery(planner, tx,
                "SELECT id FROM product WHERE price < 100 * 2");
        check("price < 100 * 2 (应折叠为 200) 应返回3条, 实际=" + count, count == 3);

        // ===== Projection Pruning 测试 =====
        section("Projection Pruning");

        // 测试只选择需要的列
        Plan p = planner.createQueryPlan("SELECT name, price FROM product WHERE price > 100", tx);
        String viz = Optimizer.visualize(p);
        System.out.println("  Plan 可视化:");
        System.out.println("  " + viz.replace("\n", "\n  "));
        check("Projection Pruning: Plan 包含 Project", viz.contains("Project"));

        // ===== Predicate Pushdown 测试 =====
        section("Predicate Pushdown");

        // 测试谓词下推
        p = planner.createQueryPlan("SELECT id, name FROM product WHERE price > 100", tx);
        viz = Optimizer.visualize(p);
        System.out.println("  Plan 可视化:");
        System.out.println("  " + viz.replace("\n", "\n  "));
        check("Predicate Pushdown: Plan 包含 Filter", viz.contains("Filter"));

        // ===== 算术表达式 + 优化测试 =====
        section("算术表达式 + 优化");

        // 测试算术表达式的常量折叠优化
        p = planner.createQueryPlan("SELECT id FROM product WHERE price > 50 + 50", tx);
        viz = Optimizer.visualize(p);
        System.out.println("  Plan 可视化 (price > 50 + 50):");
        System.out.println("  " + viz.replace("\n", "\n  "));
        check("常量折叠: 50+50 应被折叠为 100", !viz.contains("50 + 50") || viz.contains("100"));

        // ===== 边界情况测试 =====
        section("边界情况");

        // 测试除零错误（在运行时抛出）
        try {
            // 注意：除零错误会在表达式求值时抛出，而不是在编译时
            // 因为常量折叠会在优化阶段尝试计算 1/0，但会捕获异常
            count = countQuery(planner, tx,
                    "SELECT id FROM product WHERE price / 0 > 100");
            // 如果没有抛出异常，说明除零被忽略了（不应该发生）
            check("除零应报错", false);
        } catch (ArithmeticException e) {
            check("除零错误: " + e.getMessage(), true);
        } catch (UnsupportedOperationException e) {
            // 常量折叠可能抛出 UnsupportedOperationException
            check("除零错误（UnsupportedOperationException）: " + e.getMessage(), true);
        } catch (Exception e) {
            check("除零应抛出异常, 实际=" + e.getClass().getSimpleName(), false);
        }

        // 测试字符串与数字的算术运算（应该失败）
        try {
            count = countQuery(planner, tx,
                    "SELECT id FROM product WHERE name + 'test' = 'Appletest'");
            check("字符串拼接应失败（类型不匹配）", false);
        } catch (SemanticError e) {
            check("字符串拼接应报类型错误: " + e.getMessage(), true);
        } catch (Exception e) {
            check("字符串拼接应抛出 SemanticError, 实际=" + e.getClass().getSimpleName(), false);
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
            System.out.println("  [PASS] " + desc);
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
