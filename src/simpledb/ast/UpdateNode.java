package simpledb.ast;

import simpledb.query.Expression;
import simpledb.query.Predicate;

/**
 * UPDATE 语句的 AST 节点，替代 ModifyData。
 */
public class UpdateNode extends AstNode {
    private String tblname;
    private String fldname;
    private Expression newval;
    private Predicate pred;

    public UpdateNode(String tblname, String fldname, Expression newval, Predicate pred) {
        this.tblname = tblname;
        this.fldname = fldname;
        this.newval = newval;
        this.pred = pred;
    }

    @Override
    public String nodeType() { return "UPDATE"; }

    public String tableName() { return tblname; }
    public String targetField() { return fldname; }
    public Expression newValue() { return newval; }
    public Predicate pred() { return pred; }
}
