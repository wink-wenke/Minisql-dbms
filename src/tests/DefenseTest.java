package tests;

import tests.parse.LexerTest;
import tests.parse.ParserTest;
import tests.parse.SemanticAnalyzerTest;
import tests.plan.OptimizerTest;

/**
 * 答辩演示统一测试入口。
 * 依次执行词法分析、语法分析、语义分析、执行计划生成四个模块的测试。
 *
 * 运行方式：
 *   java -cp build tests.DefenseTest
 */
public class DefenseTest {

    private static final String SEPARATOR = "══════════════════════════════════════════════════════════════";
    private static final String SUBSEP    = "────────────────────────────────────────────────────────────";

    public static void main(String[] args) {
        long totalStart = System.currentTimeMillis();

        System.out.println(SEPARATOR);
        System.out.println("  MiniSQL 数据库管理系统 — 答辩测试");
        System.out.println(SEPARATOR);
        System.out.println();

        // 1. 词法分析
        runSection("词法分析（Lexical Analysis）", "4分", () -> LexerTest.main(new String[]{}));

        // 2. 语法分析
        runSection("语法分析（Syntax Analysis）", "4分", () -> ParserTest.main(new String[]{}));

        // 3. 语义分析
        runSection("语义分析（Semantic Analysis）", "4分", () -> SemanticAnalyzerTest.main(new String[]{}));

        // 4. 执行计划生成
        runSection("执行计划生成（Execution Plan）", "4分", () -> OptimizerTest.main(new String[]{}));

        long totalElapsed = System.currentTimeMillis() - totalStart;

        // 清理所有测试临时目录
        TestCleanup.deleteAll();

        System.out.println(SEPARATOR);
        System.out.println("  全部测试完成  耗时: " + totalElapsed + "ms");
        System.out.println(SEPARATOR);
    }

    private static void runSection(String title, String score, Runnable test) {
        System.out.println(SEPARATOR);
        System.out.println("  【" + title + "】 " + score);
        System.out.println(SEPARATOR);
        System.out.println();
        try {
            test.run();
        } catch (Exception e) {
            System.out.println("  [ERROR] 测试执行异常: " + e.getMessage());
            e.printStackTrace();
        }
        System.out.println();
        System.out.println(SUBSEP);
        System.out.println();
    }
}
