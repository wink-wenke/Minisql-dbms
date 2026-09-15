package simpledb.ast;

/**
 * DROP TABLE 语句的 AST 节点，替代 DropTableData。
 */
public class DropTableNode extends AstNode {
    private String tblname;
    private boolean ifExists;

    public DropTableNode(String tblname, boolean ifExists) {
        this.tblname = tblname;
        this.ifExists = ifExists;
    }

    @Override
    public String nodeType() { return "DROP TABLE"; }

    public String tableName() { return tblname; }
    public boolean ifExists() { return ifExists; }
}
