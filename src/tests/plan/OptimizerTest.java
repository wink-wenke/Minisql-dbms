package tests.plan;

import simpledb.plan.Optimizer;
import simpledb.plan.Plan;
import simpledb.plan.Planner;
import simpledb.plan.SelectPlan;
import simpledb.plan.ProjectPlan;
import simpledb.plan.TablePlan;
import simpledb.query.*;
import simpledb.server.SimpleDB;
import simpledb.tx.Transaction;
import tests.TestCleanup;
import java.io.File;
import java.util.Arrays;

/**
 * 执行计划生成测试。
 * 覆盖：谓词简化（常量折叠、布尔化简）、Plan 优化、Plan 可视化输出。
 *
 * 注意：BasicQueryPlanner 已内置优化（SELECT * 不创建 Project、WHERE 1=1 不创建 Filter），
 * 因此测试优化器时需手动构造 Plan 树来验证优化规则。
 */
public class OptimizerTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        System.out.println("┌─ 执行计划生成测试 ───────────────────────────────────────┐\n");

        testPredicateSimplify();
        testPlanOptimization();
        testPlanVisualization();

        System.out.println("\n├─ 执行计划结果: 通过 " + passed + " / 失败 " + failed + " / 总计 " + (passed + failed) + " ─┤");

        // 清理临时目录
        TestCleanup.deleteAll();
        TestCleanup.deleteAll();
    }

    static void testPredicateSimplify() {
        section("谓词简化（常量折叠 & 布尔化简）");

        // 常量折叠
        Predicate p = new Predicate(new Term(
                new Expression(new Constant(42)),
                new Expression(new Constant(42)),
                CompOp.EQUALS));
        Predicate s = p.simplify();
        check("42=42 → TRUE（常量折叠）", s.isAlwaysTrue());

        p = new Predicate(new Term(
                new Expression(new Constant(42)),
                new Expression(new Constant(42)),
                CompOp.NOT_EQUALS));
        s = p.simplify();
        check("42!=42 → FALSE（常量折叠）", s.isAlwaysFalse());

        p = new Predicate(new Term(
                new Expression(new Constant(10)),
                new Expression(new Constant(20)),
                CompOp.LESS));
        s = p.simplify();
        check("10<20 → TRUE（常量折叠）", s.isAlwaysTrue());

        p = new Predicate(new Term(
                new Expression(new Constant(20)),
                new Expression(new Constant(10)),
                CompOp.LESS));
        s = p.simplify();
        check("20<10 → FALSE（常量折叠）", s.isAlwaysFalse());

        // 布尔化简
        Predicate x = new Predicate(new Term(
                new Expression("age"),
                new Expression(new Constant(18)),
                CompOp.GREATER));
        p = Predicate.and(Predicate.truePred(), x);
        s = p.simplify();
        check("TRUE AND (age>18) → (age>18)（布尔化简）", s.toString().equals(x.toString()));

        p = Predicate.and(Predicate.falsePred(), x);
        s = p.simplify();
        check("FALSE AND (age>18) → FALSE（布尔化简）", s.isAlwaysFalse());

        p = Predicate.or(Predicate.truePred(), x);
        s = p.simplify();
        check("TRUE OR (age>18) → TRUE（布尔化简）", s.isAlwaysTrue());

        p = Predicate.or(Predicate.falsePred(), x);
        s = p.simplify();
        check("FALSE OR (age>18) → (age>18)（布尔化简）", s.toString().equals(x.toString()));

        p = Predicate.not(Predicate.truePred());
        s = p.simplify();
        check("NOT TRUE → FALSE", s.isAlwaysFalse());

        p = Predicate.not(Predicate.falsePred());
        s = p.simplify();
        check("NOT FALSE → TRUE", s.isAlwaysTrue());

        // 复合谓词简化
        Predicate a = new Predicate(new Term(
                new Expression(new Constant(1)),
                new Expression(new Constant(1)),
                CompOp.EQUALS));
        Predicate b = new Predicate(new Term(
                new Expression(new Constant(2)),
                new Expression(new Constant(3)),
                CompOp.EQUALS));
        p = Predicate.and(a, b);  // (1=1) AND (2=3) → TRUE AND FALSE → FALSE
        s = p.simplify();
        check("(1=1) AND (2=3) → FALSE（复合简化）", s.isAlwaysFalse());

        p = Predicate.or(a, b);  // (1=1) OR (2=3) → TRUE OR FALSE → TRUE
        s = p.simplify();
        check("(1=1) OR (2=3) → TRUE（复合简化）", s.isAlwaysTrue());
    }

    static void testPlanOptimization() {
        section("执行计划优化");

        TestCleanup.deleteAll();
      SimpleDB.DB_BASE_DIR = "tmp";
        SimpleDB db = new SimpleDB("optimizertest");
        Transaction tx = db.newTx();
        db.planner().executeUpdate("CREATE TABLE T1(A INT, B VARCHAR(20))", tx);
        db.planner().executeUpdate("INSERT INTO T1(A, B) VALUES (1, 'hello')", tx);
        tx.commit();

        tx = new Transaction(db.fileMgr(), db.logMgr(), db.bufferMgr());
        TablePlan tablePlan = new TablePlan(tx, "T1", db.mdMgr());

        // --- 优化规则 1：SelectPlan(TRUE) 应被移除 ---
        System.out.println("    规则1: WHERE 42=42 → 移除 Filter");
        Predicate alwaysTrue = new Predicate(new Term(
                new Expression(new Constant(42)),
                new Expression(new Constant(42)),
                CompOp.EQUALS));
        Plan withFilterTrue = new SelectPlan(tablePlan, alwaysTrue);
        String beforeViz = Optimizer.visualize(withFilterTrue);
        System.out.println("      优化前: " + beforeViz.trim().replace("\n", "\n              "));
        Plan optimized = Optimizer.optimize(withFilterTrue);
        String afterViz = Optimizer.visualize(optimized);
        System.out.println("      优化后: " + afterViz.trim().replace("\n", "\n              "));
        check("Filter[42=42] 优化后应移除", !Optimizer.visualize(optimized).contains("Filter"));

        // --- 优化规则 2：非恒真谓词的 Filter 应保留 ---
        System.out.println("    规则2: WHERE A=1 → 保留 Filter");
        Predicate notAlwaysTrue = new Predicate(new Term(
                new Expression("A"),
                new Expression(new Constant(1)),
                CompOp.EQUALS));
        Plan withFilterNormal = new SelectPlan(tablePlan, notAlwaysTrue);
        beforeViz = Optimizer.visualize(withFilterNormal);
        System.out.println("      优化前: " + beforeViz.trim().replace("\n", "\n              "));
        optimized = Optimizer.optimize(withFilterNormal);
        afterViz = Optimizer.visualize(optimized);
        System.out.println("      优化后: " + afterViz.trim().replace("\n", "\n              "));
        check("Filter[A=1] 优化后应保留", Optimizer.visualize(optimized).contains("Filter"));

        // --- 优化规则 3：非 SELECT * 的 ProjectPlan 应保留 ---
        System.out.println("    规则3: SELECT A → 保留 ProjectPlan");
        Plan withProjectNormal = new ProjectPlan(tablePlan, Arrays.asList("A"));
        beforeViz = Optimizer.visualize(withProjectNormal);
        System.out.println("      优化前: " + beforeViz.trim().replace("\n", "\n              "));
        optimized = Optimizer.optimize(withProjectNormal);
        afterViz = Optimizer.visualize(optimized);
        System.out.println("      优化后: " + afterViz.trim().replace("\n", "\n              "));
        check("ProjectPlan([A]) 优化后应保留", Optimizer.visualize(optimized).contains("Project"));

        // --- Planner 集成测试：验证完整查询流程 ---
        System.out.println("    集成: SELECT * FROM T1（Planner + Optimizer 联合）");
        Planner planner = db.planner();
        Plan p = planner.createQueryPlan("SELECT * FROM T1", tx);
        String fullViz = Optimizer.visualize(p);
        System.out.println("      计划: " + fullViz.trim().replace("\n", "\n              "));
        check("SELECT * 完整计划不含 Project", !fullViz.contains("Project"));

        System.out.println("    集成: SELECT A FROM T1 WHERE A=1（Planner + Optimizer 联合）");
        p = planner.createQueryPlan("SELECT A FROM T1 WHERE A=1", tx);
        fullViz = Optimizer.visualize(p);
        System.out.println("      计划: " + fullViz.trim().replace("\n", "\n              "));
        check("SELECT A WHERE A=1 完整计划含 Project", fullViz.contains("Project"));

        tx.commit();
    }

    static void testPlanVisualization() {
        section("执行计划树形可视化");

        TestCleanup.deleteAll();
        SimpleDB db = new SimpleDB("visualtest");
        Transaction tx = db.newTx();
        db.planner().executeUpdate("CREATE TABLE student(id INT, name VARCHAR(20), age INT)", tx);
        tx.commit();

        tx = new Transaction(db.fileMgr(), db.logMgr(), db.bufferMgr());

        // 单表查询
        System.out.println("    SQL: SELECT id, name FROM student WHERE age > 18");
        Plan p = db.planner().createQueryPlan("SELECT id, name FROM student WHERE age > 18", tx);
        String viz = Optimizer.visualize(p);
        System.out.println("    执行计划（优化前）:");
        printPlan(viz);
        check("包含 Project 算子", viz.contains("Project"));
        check("包含 Filter 算子", viz.contains("Filter"));
        check("包含 SeqScan 算子", viz.contains("SeqScan"));

        // 优化后
        Plan opt = Optimizer.optimize(p);
        String optViz = Optimizer.visualize(opt);
        System.out.println("    执行计划（优化后）:");
        printPlan(optViz);

        // 统计信息
        System.out.println("    统计: 块访问=" + opt.blocksAccessed() + ", 输出行=" + opt.recordsOutput());
        check("块访问数 >= 0", opt.blocksAccessed() >= 0);
        check("输出行数 >= 0", opt.recordsOutput() >= 0);

        // 手动构造嵌套优化测试：Filter(TRUE) + Project([id,name]) 嵌套
        System.out.println("\n    嵌套优化: SELECT id, name FROM student WHERE 1=1");
        TablePlan tp = new TablePlan(tx, "student", db.mdMgr());
        Predicate t1 = new Predicate(new Term(
                new Expression(new Constant(1)),
                new Expression(new Constant(1)),
                CompOp.EQUALS));
        Plan nested = new ProjectPlan(new SelectPlan(tp, t1), Arrays.asList("id", "name"));
        System.out.println("      优化前:");
        printPlan(Optimizer.visualize(nested));
        Plan nestedOpt = Optimizer.optimize(nested);
        System.out.println("      优化后:");
        printPlan(Optimizer.visualize(nestedOpt));
        check("嵌套优化: Filter[1=1] 已移除", !Optimizer.visualize(nestedOpt).contains("Filter"));
        check("嵌套优化: Project 保留（非SELECT *）", Optimizer.visualize(nestedOpt).contains("Project"));

        tx.commit();
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

    static void printPlan(String planStr) {
        for (String line : planStr.split("\n")) {
            System.out.println("      " + line);
        }
    }
}
