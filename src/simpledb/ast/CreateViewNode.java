package simpledb.ast;

/**
 * CREATE VIEW 语句的 AST 节点，替代 CreateViewData。
 */
public class CreateViewNode extends AstNode {
    private String viewname;
    private SelectNode qrydata;

    public CreateViewNode(String viewname, SelectNode qrydata) {
        this.viewname = viewname;
        this.qrydata = qrydata;
    }

    @Override
    public String nodeType() { return "CREATE VIEW"; }

    public String viewName() { return viewname; }
    public String viewDef() { return qrydata.toString(); }
    public SelectNode viewQuery() { return qrydata; }
}
