package simpledb.plan;

import simpledb.metadata.MetadataMgr;
import simpledb.tx.Transaction;
import simpledb.parse.*;

/**
 * SQL 编译器与执行计划的总入口。
 * <p>
 * 完整流水线：
 * <pre>
 *   SQL 文本
 *     → Parser（词法+语法分析）→ AST/数据对象
 *     → SemanticAnalyzer（语义分析，检查表/列/类型）
 *     → QueryPlanner / UpdatePlanner（生成 Logical Plan）
 *     → Optimizer（查询优化：常量折叠、冗余消除）
 *     → Plan（可执行的计划树）
 * </pre>
 *
 * @author Edward Sciore (原始), 增强版
 */
public class Planner {
    private QueryPlanner qplanner;
    private UpdatePlanner uplanner;
    private MetadataMgr mdm;

    public Planner(QueryPlanner qplanner, UpdatePlanner uplanner) {
        this.qplanner = qplanner;
        this.uplanner = uplanner;
        this.mdm = null;
    }

    /**
     * 带 MetadataMgr 的构造（用于语义分析）。
     */
    public Planner(QueryPlanner qplanner, UpdatePlanner uplanner, MetadataMgr mdm) {
        this.qplanner = qplanner;
        this.uplanner = uplanner;
        this.mdm = mdm;
    }

    /**
     * 为 SELECT 语句创建执行计划。
     * 流程：Parser → 语义分析 → 生成计划 → 优化
     */
    public Plan createQueryPlan(String qry, Transaction tx) {
        // 1. 语法分析
        Parser parser = new Parser(qry);
        QueryData data = parser.query();

        // 2. 语义分析
        verifyQuery(data, tx);

        // 3. 生成计划
        Plan plan = qplanner.createPlan(data, tx);

        // 4. 查询优化
        plan = Optimizer.optimize(plan);

        return plan;
    }

    /**
     * 执行更新语句（INSERT / DELETE / UPDATE / CREATE）。
     * 流程：Parser → 语义分析 → 分派执行
     */
    public int executeUpdate(String cmd, Transaction tx) {
        // 1. 语法分析
        Parser parser = new Parser(cmd);
        Object data = parser.updateCmd();

        // 2. 语义分析（CREATE 不需要）
        verifyUpdate(data, tx);

        // 3. 分派执行
        if (data instanceof InsertData)
            return uplanner.executeInsert((InsertData) data, tx);
        else if (data instanceof DeleteData)
            return uplanner.executeDelete((DeleteData) data, tx);
        else if (data instanceof ModifyData)
            return uplanner.executeModify((ModifyData) data, tx);
        else if (data instanceof CreateTableData)
            return uplanner.executeCreateTable((CreateTableData) data, tx);
        else if (data instanceof CreateViewData)
            return uplanner.executeCreateView((CreateViewData) data, tx);
        else if (data instanceof CreateIndexData)
            return uplanner.executeCreateIndex((CreateIndexData) data, tx);
        else
            return 0;
    }

    /**
     * 对 SELECT 语句做语义验证。
     */
    private void verifyQuery(QueryData data, Transaction tx) {
        if (mdm != null) {
            SemanticAnalyzer analyzer = new SemanticAnalyzer(mdm, tx);
            analyzer.analyzeQuery(data);
        }
    }

    /**
     * 对更新语句做语义验证。
     */
    private void verifyUpdate(Object data, Transaction tx) {
        if (mdm != null) {
            SemanticAnalyzer analyzer = new SemanticAnalyzer(mdm, tx);
            analyzer.analyze(data);
        }
    }
}
