package simpledb.ast;

import simpledb.record.Schema;

/**
 * CREATE TABLE 语句的 AST 节点，替代 CreateTableData。
 */
public class CreateTableNode extends AstNode {
    private String tblname;
    private Schema sch;

    public CreateTableNode(String tblname, Schema sch) {
        this.tblname = tblname;
        this.sch = sch;
    }

    @Override
    public String nodeType() { return "CREATE TABLE"; }

    public String tableName() { return tblname; }
    public Schema newSchema() { return sch; }
}
