package simpledb.parse;

import simpledb.metadata.MetadataMgr;
import simpledb.server.SimpleDB;
import simpledb.tx.Transaction;
import java.io.File;

/**
 * SemanticAnalyzer 测试。
 * 需要 SimpleDB 环境（会创建临时数据库目录 semantictest）。
 *
 * 测试内容：
 * 1. 表存在性检查
 * 2. 列存在性检查
 * 3. INSERT 列数 / 类型检查
 * 4. WHERE 列存在性 / 类型检查
 * 5. UPDATE 列存在性 / 类型检查
 * 6. 正常语句不报错
 */
public class SemanticAnalyzerTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        System.out.println("===== SemanticAnalyzer 测试开始 =====\n");

        // 初始化数据库
        deleteDir("semantictest");
        SimpleDB db = new SimpleDB("semantictest");
        Transaction tx = db.newTx();
        MetadataMgr mdm = db.mdMgr();

        // 创建测试表
        db.planner().executeUpdate("CREATE TABLE student(id INT, name VARCHAR(20), age INT)", tx);
        db.planner().executeUpdate("CREATE TABLE course(cid INT, title VARCHAR(30))", tx);
        tx.commit();

        tx = db.newTx();
        SemanticAnalyzer analyzer = new SemanticAnalyzer(mdm, tx);

        testSelectValid(analyzer);
        testSelectStar(analyzer);
        testSelectTableNotExist(analyzer);
        testSelectColumnNotExist(analyzer);
        testSelectTypeMismatch(analyzer);
        testInsertValid(analyzer);
        testInsertColumnCountMismatch(analyzer);
        testInsertColumnTypeMismatch(analyzer);
        testInsertColumnNotExist(analyzer);
        testDeleteValid(analyzer);
        testDeleteTableNotExist(analyzer);
        testUpdateValid(analyzer);
        testUpdateColumnNotExist(analyzer);
        testWhereOrNot(analyzer);

        tx.commit();

        System.out.println("\n===== 测试结果 =====");
        System.out.println("通过: " + passed + ", 失败: " + failed + ", 总计: " + (passed + failed));
    }

    // ======================== 测试用例 ========================

    static void testSelectValid(SemanticAnalyzer a) {
        section("SELECT 正常");
        Parser p = new Parser("SELECT id, name FROM student WHERE age > 18 AND id != 3;");
        a.analyzeQuery(p.query());
        check("正常 SELECT 不应报错", true);
    }

    static void testSelectStar(SemanticAnalyzer a) {
        section("SELECT *");
        Parser p = new Parser("SELECT * FROM student;");
        a.analyzeQuery(p.query());
        check("SELECT * 不应报错", true);
    }

    static void testSelectTableNotExist(SemanticAnalyzer a) {
        section("SELECT 表不存在");
        try {
            Parser p = new Parser("SELECT id FROM nonexistent;");
            a.analyzeQuery(p.query());
            check("表不存在应报错", false);
        } catch (SemanticError e) {
            check("表不存在错误: " + e.getMessage(), e.getMessage().contains("不存在"));
        }
    }

    static void testSelectColumnNotExist(SemanticAnalyzer a) {
        section("SELECT 列不存在");
        try {
            Parser p = new Parser("SELECT id, score FROM student;");
            a.analyzeQuery(p.query());
            check("列不存在应报错", false);
        } catch (SemanticError e) {
            check("列不存在错误: " + e.getMessage(), e.getMessage().contains("score"));
        }
    }

    static void testSelectTypeMismatch(SemanticAnalyzer a) {
        section("SELECT WHERE 类型不匹配");
        try {
            // age 是 INT，'Alice' 是 VARCHAR
            Parser p = new Parser("SELECT id FROM student WHERE age = 'Alice';");
            a.analyzeQuery(p.query());
            check("类型不匹配应报错", false);
        } catch (SemanticError e) {
            check("类型不匹配错误: " + e.getMessage(), e.getMessage().contains("类型"));
        }
    }

    static void testInsertValid(SemanticAnalyzer a) {
        section("INSERT 正常");
        Parser p = new Parser("INSERT INTO student(id, name, age) VALUES (1, 'Alice', 20);");
        a.analyzeInsert(p.insert());
        check("正常 INSERT 不应报错", true);
    }

    static void testInsertColumnCountMismatch(SemanticAnalyzer a) {
        section("INSERT 列数不匹配");
        try {
            Parser p = new Parser("INSERT INTO student(id, name) VALUES (1, 'Alice', 20);");
            a.analyzeInsert(p.insert());
            check("列数不匹配应报错", false);
        } catch (SemanticError e) {
            check("列数不匹配错误: " + e.getMessage(), e.getMessage().contains("列数"));
        }
    }

    static void testInsertColumnTypeMismatch(SemanticAnalyzer a) {
        section("INSERT 类型不匹配");
        try {
            // id 期望 INT，但给了 'hello'
            Parser p = new Parser("INSERT INTO student(id, name, age) VALUES ('hello', 'Alice', 20);");
            a.analyzeInsert(p.insert());
            check("类型不匹配应报错", false);
        } catch (SemanticError e) {
            check("类型不匹配错误: " + e.getMessage(), e.getMessage().contains("INT"));
        }
    }

    static void testInsertColumnNotExist(SemanticAnalyzer a) {
        section("INSERT 列不存在");
        try {
            Parser p = new Parser("INSERT INTO student(id, score) VALUES (1, 100);");
            a.analyzeInsert(p.insert());
            check("列不存在应报错", false);
        } catch (SemanticError e) {
            check("列不存在错误: " + e.getMessage(), e.getMessage().contains("score"));
        }
    }

    static void testDeleteValid(SemanticAnalyzer a) {
        section("DELETE 正常");
        Parser p = new Parser("DELETE FROM student WHERE id = 1;");
        a.analyzeDelete(p.delete());
        check("正常 DELETE 不应报错", true);
    }

    static void testDeleteTableNotExist(SemanticAnalyzer a) {
        section("DELETE 表不存在");
        try {
            Parser p = new Parser("DELETE FROM nonexistent WHERE id = 1;");
            a.analyzeDelete(p.delete());
            check("表不存在应报错", false);
        } catch (SemanticError e) {
            check("表不存在错误: " + e.getMessage(), e.getMessage().contains("不存在"));
        }
    }

    static void testUpdateValid(SemanticAnalyzer a) {
        section("UPDATE 正常");
        Parser p = new Parser("UPDATE student SET age = 21 WHERE name = 'Alice';");
        a.analyzeUpdate(p.modify());
        check("正常 UPDATE 不应报错", true);
    }

    static void testUpdateColumnNotExist(SemanticAnalyzer a) {
        section("UPDATE 列不存在");
        try {
            Parser p = new Parser("UPDATE student SET score = 100 WHERE id = 1;");
            a.analyzeUpdate(p.modify());
            check("列不存在应报错", false);
        } catch (SemanticError e) {
            check("列不存在错误: " + e.getMessage(), e.getMessage().contains("score"));
        }
    }

    static void testWhereOrNot(SemanticAnalyzer a) {
        section("WHERE OR / NOT 语义检查");
        // 正常
        Parser p = new Parser("SELECT id FROM student WHERE age > 18 OR NOT (id = 3);");
        a.analyzeQuery(p.query());
        check("OR/NOT 正常不应报错", true);

        // OR 中列不存在
        try {
            p = new Parser("SELECT id FROM student WHERE age > 18 OR score = 100;");
            a.analyzeQuery(p.query());
            check("OR 中列不存在应报错", false);
        } catch (SemanticError e) {
            check("OR 中列不存在错误: " + e.getMessage(), e.getMessage().contains("score"));
        }
    }

    // ======================== 辅助方法 ========================

    static void section(String name) {
        System.out.println("[" + name + "]");
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

    static void check(String desc, boolean condition) {
        if (condition) {
            passed++;
        } else {
            failed++;
            System.out.println("  [FAIL] " + desc);
        }
    }
}
