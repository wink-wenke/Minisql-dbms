package simpledb.ast;

import simpledb.query.Predicate;

/**
 * DELETE 语句的 AST 节点，替代 DeleteData。
 */
public class DeleteNode extends AstNode {
    private String tblname;
    private Predicate pred;

    public DeleteNode(String tblname, Predicate pred) {
        this.tblname = tblname;
        this.pred = pred;
    }

    @Override
    public String nodeType() { return "DELETE"; }

    public String tableName() { return tblname; }
    public Predicate pred() { return pred; }
}
