package simpledb.ast;

/**
 * CREATE INDEX 语句的 AST 节点，替代 CreateIndexData。
 */
public class CreateIndexNode extends AstNode {
    private String idxname;
    private String tblname;
    private String fldname;

    public CreateIndexNode(String idxname, String tblname, String fldname) {
        this.idxname = idxname;
        this.tblname = tblname;
        this.fldname = fldname;
    }

    @Override
    public String nodeType() { return "CREATE INDEX"; }

    public String indexName() { return idxname; }
    public String tableName() { return tblname; }
    public String fieldName() { return fldname; }
}
