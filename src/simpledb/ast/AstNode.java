package simpledb.ast;

/**
 * AST 节点基类。
 * 所有 SQL 语句解析后的 AST 节点都继承此类。
 */
public abstract class AstNode {
    /**
     * 返回节点类型名称（如 "SELECT", "INSERT", "DELETE" 等）。
     */
    public abstract String nodeType();
}
