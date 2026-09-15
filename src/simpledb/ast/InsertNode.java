package simpledb.ast;

import java.util.List;
import simpledb.query.Constant;

/**
 * INSERT 语句的 AST 节点，替代 InsertData。
 */
public class InsertNode extends AstNode {
    private String tblname;
    private List<String> flds;
    private List<Constant> vals;

    public InsertNode(String tblname, List<String> flds, List<Constant> vals) {
        this.tblname = tblname;
        this.flds = flds;
        this.vals = vals;
    }

    @Override
    public String nodeType() { return "INSERT"; }

    public String tableName() { return tblname; }
    public List<String> fields() { return flds; }
    public List<Constant> vals() { return vals; }
}
