package simpledb.plan;

import simpledb.query.*;
import simpledb.server.SimpleDB;
import simpledb.tx.Transaction;

/**
 * Optimizer 测试。
 * 测试内容：
 * 1. 常量折叠：c=c → TRUE, c!=c → FALSE
 * 2. 布尔化简：TRUE AND x → x, FALSE OR x → x
 * 3. 冗余节点消除：Filter[TRUE] 删除, Project[*] 删除
 * 4. Plan 可视化输出
 */
public class OptimizerTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        System.out.println("===== Optimizer 测试开始 =====\n");

        testPredicateSimplify();
        testPlanOptimization();
        testPlanVisualization();

        System.out.println("\n===== 测试结果 =====");
        System.out.println("通过: " + passed + ", 失败: " + failed + ", 总计: " + (passed + failed));
    }

    static void testPredicateSimplify() {
        section("谓词简化");

        // c = c → TRUE
        Predicate p = new Predicate(new Term(
                new Expression(new Constant(42)),
                new Expression(new Constant(42)),
                CompOp.EQUALS));
        Predicate s = p.simplify();
        check("42=42 → TRUE", s.isAlwaysTrue());

        // c != c → FALSE
        p = new Predicate(new Term(
                new Expression(new Constant(42)),
                new Expression(new Constant(42)),
                CompOp.NOT_EQUALS));
        s = p.simplify();
        check("42!=42 → FALSE", s.isAlwaysFalse());

        // 10 < 20 → TRUE
        p = new Predicate(new Term(
                new Expression(new Constant(10)),
                new Expression(new Constant(20)),
                CompOp.LESS));
        s = p.simplify();
        check("10<20 → TRUE", s.isAlwaysTrue());

        // 30 > 20 → TRUE
        p = new Predicate(new Term(
                new Expression(new Constant(30)),
                new Expression(new Constant(20)),
                CompOp.GREATER));
        s = p.simplify();
        check("30>20 → TRUE", s.isAlwaysTrue());

        // TRUE AND x → x
        Predicate x = new Predicate(new Term(
                new Expression("age"),
                new Expression(new Constant(18)),
                CompOp.GREATER));
        p = Predicate.and(Predicate.truePred(), x);
        s = p.simplify();
        check("TRUE AND (age>18) → (age>18)", s.toString().equals(x.toString()));

        // FALSE AND x → FALSE
        p = Predicate.and(Predicate.falsePred(), x);
        s = p.simplify();
        check("FALSE AND (age>18) → FALSE", s.isAlwaysFalse());

        // TRUE OR x → TRUE
        p = Predicate.or(Predicate.truePred(), x);
        s = p.simplify();
        check("TRUE OR (age>18) → TRUE", s.isAlwaysTrue());

        // FALSE OR x → x
        p = Predicate.or(Predicate.falsePred(), x);
        s = p.simplify();
        check("FALSE OR (age>18) → (age>18)", s.toString().equals(x.toString()));

        // NOT TRUE → FALSE
        p = Predicate.not(Predicate.truePred());
        s = p.simplify();
        check("NOT TRUE → FALSE", s.isAlwaysFalse());

        // NOT FALSE → TRUE
        p = Predicate.not(Predicate.falsePred());
        s = p.simplify();
        check("NOT FALSE → TRUE", s.isAlwaysTrue());
    }

    static void testPlanOptimization() {
        section("Plan 优化（需要数据库环境）");

        SimpleDB db = new SimpleDB("optimizertest");
        Transaction tx = db.newTx();
        db.planner().executeUpdate("CREATE TABLE T1(A INT, B VARCHAR(20))", tx);
        db.planner().executeUpdate("INSERT INTO T1(A, B) VALUES (1, 'hello')", tx);
        tx.commit();

        tx = new Transaction(db.fileMgr(), db.logMgr(), db.bufferMgr());
        Planner planner = db.planner();

        // SELECT * FROM T1 → 应去掉 ProjectPlan
        Plan p = planner.createQueryPlan("SELECT * FROM T1", tx);
        String before = Optimizer.visualize(p);
        Plan opt = Optimizer.optimize(p);
        String after = Optimizer.visualize(opt);
        System.out.println("  优化前:\n" + indent(before));
        System.out.println("  优化后:\n" + indent(after));
        check("SELECT * 优化后应去掉 ProjectPlan", !after.contains("Project"));

        // SELECT A FROM T1 WHERE 1=1 → 应去掉 Filter
        p = planner.createQueryPlan("SELECT A FROM T1 WHERE 1=1", tx);
        before = Optimizer.visualize(p);
        opt = Optimizer.optimize(p);
        after = Optimizer.visualize(opt);
        System.out.println("  优化前:\n" + indent(before));
        System.out.println("  优化后:\n" + indent(after));
        check("WHERE 1=1 优化后应去掉 Filter", !after.contains("Filter"));

        tx.commit();
    }

    static void testPlanVisualization() {
        section("Plan 可视化");

        SimpleDB db = new SimpleDB("visualtest");
        Transaction tx = db.newTx();
        db.planner().executeUpdate("CREATE TABLE student(id INT, name VARCHAR(20), age INT)", tx);
        tx.commit();

        tx = new Transaction(db.fileMgr(), db.logMgr(), db.bufferMgr());
        Plan p = db.planner().createQueryPlan("SELECT id, name FROM student WHERE age > 18 AND id != 3", tx);
        String viz = Optimizer.visualize(p);
        System.out.println("  SELECT id, name FROM student WHERE age > 18 AND id != 3:");
        System.out.println(indent(viz));
        check("可视化应包含 Project", viz.contains("Project"));
        check("可视化应包含 Filter", viz.contains("Filter"));
        check("可视化应包含 SeqScan", viz.contains("SeqScan"));

        // 优化后的可视化
        Plan opt = Optimizer.optimize(p);
        String optViz = Optimizer.visualize(opt);
        System.out.println("  优化后:");
        System.out.println(indent(optViz));

        tx.commit();
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

    static String indent(String s) {
        StringBuilder sb = new StringBuilder();
        for (String line : s.split("\n")) {
            sb.append("    ").append(line).append("\n");
        }
        return sb.toString();
    }
}
