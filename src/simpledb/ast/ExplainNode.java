package simpledb.ast;

/**
 * EXPLAIN 语句的 AST 节点。
 * 包装一个 SELECT 查询，输出其执行计划而不实际执行。
 */
public class ExplainNode extends AstNode {
    private final SelectNode query;
    private final String originalSql;

    public ExplainNode(SelectNode query, String originalSql) {
        this.query = query;
        this.originalSql = originalSql;
    }

    @Override
    public String nodeType() { return "EXPLAIN"; }

    public SelectNode query() { return query; }
    public String originalSql() { return originalSql; }
}
