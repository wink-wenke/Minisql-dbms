package simpledb.parse;

import simpledb.plan.*;
import simpledb.query.Scan;
import simpledb.server.SimpleDB;
import simpledb.tx.Transaction;

import java.util.*;
import java.io.File;

/**
 * Fuzz Testing：随机生成 SQL 测试编译器健壮性。
 * <p>
 * 测试目标：
 * <ul>
 *   <li><b>Crash</b>：编译器不应因任何输入崩溃</li>
 *   <li><b>Wrong Accept</b>：非法SQL不应被接受执行</li>
 *   <li><b>Wrong Reject</b>：合法SQL不应被拒绝</li>
 *   <li><b>Error Location</b>：错误位置应合理</li>
 * </ul>
 *
 * 方法：
 * 1. 生成合法 SQL 模板 + 变异 → 应全部通过或报语义/语法错误（不崩溃）
 * 2. 生成非法 SQL（随机字符、残缺语句）→ 应全部报错（不崩溃、不误接受）
 */
public class FuzzTest {

    private static int passed = 0;
    private static int failed = 0;
    private static int crashCount = 0;
    private static int wrongAccept = 0;
    private static int wrongReject = 0;

    private static final Random rand = new Random(42);

    public static void main(String[] args) {
        System.out.println("===== Fuzz Testing =====\n");

        deleteDir("fuzztest");
        SimpleDB db = new SimpleDB("fuzztest");
        Transaction tx = db.newTx();
        Planner planner = db.planner();

        // 建表
        planner.executeUpdate("CREATE TABLE student(id INT, name VARCHAR(20), age INT)", tx);
        planner.executeUpdate("CREATE TABLE course(cid INT, title VARCHAR(30))", tx);
        planner.executeUpdate("INSERT INTO student(id, name, age) VALUES (1, 'Alice', 20)", tx);
        planner.executeUpdate("INSERT INTO student(id, name, age) VALUES (2, 'Bob', 17)", tx);
        planner.executeUpdate("INSERT INTO student(id, name, age) VALUES (3, 'Charlie', 22)", tx);
        planner.executeUpdate("INSERT INTO course(cid, title) VALUES (101, 'Math')", tx);
        tx.commit();

        tx = new Transaction(db.fileMgr(), db.logMgr(), db.bufferMgr());

        // 测试 1：合法 SQL 模板（应全部成功执行）
        testValidSQLs(planner, tx);

        // 测试 2：合法 SQL + 小变异（应报错但不崩溃）
        testMutatedSQLs(planner, tx);

        // 测试 3：随机非法字符串（应报错但不崩溃）
        testRandomStrings(planner, tx);

        // 测试 4：残缺 SQL（应报错但不崩溃）
        testIncompleteSQLs(planner, tx);

        // 测试 5：边界情况
        testEdgeCases(planner, tx);

        // 测试 6：EXPLAIN 命令
        testExplain(planner, tx);

        tx.commit();

        System.out.println("\n===== Fuzz 测试结果 =====");
        System.out.println("总测试数:  " + (passed + failed));
        System.out.println("通过:      " + passed);
        System.out.println("失败:      " + failed);
        System.out.println("Crash:     " + crashCount + (crashCount == 0 ? " ✅" : " ❌"));
        System.out.println("Wrong Accept: " + wrongAccept + (wrongAccept == 0 ? " ✅" : " ❌"));
        System.out.println("Wrong Reject: " + wrongReject);
    }

    // ======================== 测试 1：合法 SQL ========================

    static void testValidSQLs(Planner planner, Transaction tx) {
        section("合法 SQL");
        String[] validSQLs = {
            "SELECT id, name FROM student WHERE age > 18",
            "SELECT * FROM student WHERE age >= 20",
            "SELECT id FROM student WHERE name = 'Alice' AND age > 18",
            "SELECT id FROM student WHERE age > 20 OR age < 18",
            "SELECT id FROM student WHERE NOT age > 20",
            "SELECT id FROM student WHERE (age > 18 OR id = 1) AND id != 3",
            "DELETE FROM student WHERE id = 1",
            "INSERT INTO student(id, name, age) VALUES (99, 'Test', 25)",
            "SELECT * FROM student WHERE age > 18 AND id != 3",
            "SELECT cid FROM course WHERE cid = 101",
        };

        for (String sql : validSQLs) {
            try {
                if (sql.startsWith("SELECT")) {
                    Plan p = planner.createQueryPlan(sql, tx);
                    Scan s = p.open();
                    int count = 0;
                    while (s.next()) count++;
                    s.close();
                    check("✓ " + abbrev(sql), true);
                } else {
                    planner.executeUpdate(sql, tx);
                    check("✓ " + abbrev(sql), true);
                }
            } catch (Exception e) {
                wrongReject++;
                check("合法SQL被拒绝: " + abbrev(sql) + " → " + e.getMessage(), false);
            }
        }
    }

    // ======================== 测试 2：变异 SQL ========================

    static void testMutatedSQLs(Planner planner, Transaction tx) {
        section("变异 SQL（应报错但不崩溃）");
        String[] mutatedSQLs = {
            "SELECT id, FROM student",
            "SELECT id FROM nonexistent WHERE age > 18",
            "SELECT score FROM student",
            "INSERT INTO student(id, name, age) VALUES (1, 'Alice', 20, 99)",
            "INSERT INTO student(id, name, age) VALUES ('x', 'Alice', 20)",
            "DELETE FROM nonexistent WHERE id = 1",
            "SELECT * FROM student WHERE age = 'Alice'",
            "SELECT * FROM student WHERE (age > 18",
            "SELECT * FROM student WHERE age > 18)",
        };

        for (String sql : mutatedSQLs) {
            try {
                if (sql.startsWith("SELECT")) {
                    Plan p = planner.createQueryPlan(sql, tx);
                    Scan s = p.open();
                    while (s.next()) {}
                    s.close();
                    wrongAccept++;
                    check("错误SQL被接受: " + abbrev(sql), false);
                } else {
                    planner.executeUpdate(sql, tx);
                    wrongAccept++;
                    check("错误SQL被接受: " + abbrev(sql), false);
                }
            } catch (BadSyntaxException e) {
                check("✓ 语法错误: " + abbrev(sql), true);
            } catch (SemanticError e) {
                check("✓ 语义错误: " + abbrev(sql), true);
            } catch (Exception e) {
                crashCount++;
                check("⚠ 异常(非崩溃): " + abbrev(sql) + " → " + e.getClass().getSimpleName(), false);
            }
        }
    }

    // ======================== 测试 3：随机字符串 ========================

    static void testRandomStrings(Planner planner, Transaction tx) {
        section("随机非法字符串（应报错但不崩溃）");
        int count = 0;
        for (int i = 0; i < 100; i++) {
            String random = randomString(rand.nextInt(20) + 1);
            try {
                if (rand.nextBoolean()) {
                    Plan p = planner.createQueryPlan(random, tx);
                    Scan s = p.open();
                    while (s.next()) {}
                    s.close();
                    wrongAccept++;
                } else {
                    planner.executeUpdate(random, tx);
                    wrongAccept++;
                }
            } catch (BadSyntaxException e) {
                count++;
            } catch (SemanticError e) {
                count++;
            } catch (Exception e) {
                crashCount++;
            }
        }
        check("100 个随机字符串全部安全处理 (" + count + "/100)", count == 100);
    }

    // ======================== 测试 4：残缺 SQL ========================

    static void testIncompleteSQLs(Planner planner, Transaction tx) {
        section("残缺 SQL（应报错但不崩溃）");
        String[] incomplete = {
            "",
            "SELECT",
            "SELECT id",
            "SELECT id FROM",
            "SELECT id FROM student WHERE",
            "WHERE age > 18",
            "INSERT",
            "INSERT INTO",
            "DELETE",
            "CREATE",
            "();",
            ";;;",
        };

        for (String sql : incomplete) {
            try {
                if (sql.startsWith("SELECT")) {
                    Plan p = planner.createQueryPlan(sql, tx);
                    Scan s = p.open();
                    while (s.next()) {}
                    s.close();
                    wrongAccept++;
                } else if (sql.isEmpty() || sql.startsWith("WHERE") || sql.startsWith("();") || sql.startsWith(";;;")) {
                    // 不以关键词开头，应报语法错误
                    planner.createQueryPlan(sql, tx);
                    wrongAccept++;
                } else {
                    planner.executeUpdate(sql, tx);
                    wrongAccept++;
                }
            } catch (BadSyntaxException e) {
                check("✓ 残缺SQL: '" + abbrev(sql) + "'", true);
            } catch (SemanticError e) {
                check("✓ 残缺SQL: '" + abbrev(sql) + "'", true);
            } catch (Exception e) {
                crashCount++;
                check("⚠ 残缺SQL异常: '" + abbrev(sql) + "' → " + e.getClass().getSimpleName(), false);
            }
        }
    }

    // ======================== 测试 5：边界情况 ========================

    static void testEdgeCases(Planner planner, Transaction tx) {
        section("边界情况");

        // 大量 AND
        try {
            Plan p = planner.createQueryPlan("SELECT * FROM student WHERE id = 1 AND id = 1 AND id = 1 AND id = 1", tx);
            Scan s = p.open();
            while (s.next()) {}
            s.close();
            check("✓ 大量 AND", true);
        } catch (Exception e) {
            check("大量 AND 异常: " + e.getClass().getSimpleName(), false);
        }

        // 大量 OR
        try {
            Plan p = planner.createQueryPlan("SELECT * FROM student WHERE id = 1 OR id = 2 OR id = 3 OR id = 4", tx);
            Scan s = p.open();
            while (s.next()) {}
            s.close();
            check("✓ 大量 OR", true);
        } catch (Exception e) {
            check("大量 OR 异常: " + e.getClass().getSimpleName(), false);
        }

        // 嵌套 NOT
        try {
            Plan p = planner.createQueryPlan("SELECT * FROM student WHERE NOT NOT NOT id = 1", tx);
            Scan s = p.open();
            while (s.next()) {}
            s.close();
            check("✓ 嵌套 NOT", true);
        } catch (Exception e) {
            check("嵌套 NOT 异常: " + e.getClass().getSimpleName(), false);
        }

        // 深度嵌套括号
        try {
            Plan p = planner.createQueryPlan("SELECT * FROM student WHERE ((((id = 1))))", tx);
            Scan s = p.open();
            while (s.next()) {}
            s.close();
            check("✓ 深度嵌套括号", true);
        } catch (Exception e) {
            check("深度嵌套括号异常: " + e.getClass().getSimpleName(), false);
        }

        // 大小写混合
        try {
            Plan p = planner.createQueryPlan("select * from student where age > 18", tx);
            Scan s = p.open();
            int count = 0;
            while (s.next()) count++;
            s.close();
            check("✓ 大小写混合 (结果=" + count + ")", count > 0);
        } catch (Exception e) {
            check("大小写混合异常: " + e.getClass().getSimpleName(), false);
        }

        // 多余空格
        try {
            Plan p = planner.createQueryPlan("  SELECT   *   FROM   student   WHERE   age   >   18  ", tx);
            Scan s = p.open();
            int count = 0;
            while (s.next()) count++;
            s.close();
            check("✓ 多余空格 (结果=" + count + ")", count > 0);
        } catch (Exception e) {
            check("多余空格异常: " + e.getClass().getSimpleName(), false);
        }
    }

    // ======================== 测试 6：EXPLAIN ========================

    static void testExplain(Planner planner, Transaction tx) {
        section("EXPLAIN 命令");
        try {
            String result = planner.explain("SELECT id, name FROM student WHERE age > 18 AND id != 3", tx);
            check("EXPLAIN 返回非空", result != null && !result.isEmpty());
            check("EXPLAIN 包含优化前", result.contains("优化前"));
            check("EXPLAIN 包含优化后", result.contains("优化后"));
            check("EXPLAIN 包含 SeqScan", result.contains("SeqScan"));
            System.out.println("  输出:");
            for (String line : result.split("\n")) {
                System.out.println("    " + line);
            }
        } catch (Exception e) {
            check("EXPLAIN 异常: " + e.getClass().getSimpleName(), false);
        }
    }

    // ======================== 辅助方法 ========================

    static String randomString(int len) {
        String chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789!@#$%^&*();:[]{}|\\'\"<>,.?/+=_-~`";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len; i++) {
            sb.append(chars.charAt(rand.nextInt(chars.length())));
        }
        return sb.toString();
    }

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

    static String abbrev(String s) {
        return s.length() <= 50 ? s : s.substring(0, 47) + "...";
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
}
