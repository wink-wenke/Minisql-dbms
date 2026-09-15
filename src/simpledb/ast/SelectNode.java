package simpledb.ast;

import java.util.*;
import simpledb.query.Predicate;
import simpledb.materialize.AggregationFn;

/**
 * SELECT 查询的 AST 节点，替代 QueryData。
 */
public class SelectNode extends AstNode {
    private List<String> fields;
    private Collection<String> tables;
    private Predicate pred;
    private List<OrderByEntry> orderby;
    private List<String> groupby;
    private List<AggregationFn> aggfns;

    public SelectNode(List<String> fields, Collection<String> tables, Predicate pred) {
        this(fields, tables, pred, Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
    }

    public SelectNode(List<String> fields, Collection<String> tables, Predicate pred, List<OrderByEntry> orderby) {
        this(fields, tables, pred, orderby, Collections.emptyList(), Collections.emptyList());
    }

    public SelectNode(List<String> fields, Collection<String> tables, Predicate pred,
                      List<OrderByEntry> orderby, List<String> groupby, List<AggregationFn> aggfns) {
        this.fields = fields;
        this.tables = tables;
        this.pred = pred;
        this.orderby = orderby;
        this.groupby = groupby;
        this.aggfns = aggfns;
    }

    @Override
    public String nodeType() { return "SELECT"; }

    public List<String> fields() { return fields; }
    public Collection<String> tables() { return tables; }
    public Predicate pred() { return pred; }
    public List<OrderByEntry> orderby() { return orderby; }
    public List<String> groupby() { return groupby; }
    public List<AggregationFn> aggfns() { return aggfns; }

    public String toString() {
        String result = "select ";
        if (fields.isEmpty()) {
            result += "* ";
        } else {
            for (String fldname : fields)
                result += fldname + ", ";
            result = result.substring(0, result.length() - 2);
        }
        result += " from ";
        for (String tblname : tables)
            result += tblname + ", ";
        result = result.substring(0, result.length() - 2);
        String predstring = pred.toString();
        if (!predstring.equals(""))
            result += " where " + predstring;
        if (!groupby.isEmpty()) {
            result += " group by ";
            for (String fldname : groupby)
                result += fldname + ", ";
            result = result.substring(0, result.length() - 2);
        }
        if (!orderby.isEmpty()) {
            result += " order by ";
            for (OrderByEntry entry : orderby)
                result += entry + ", ";
            result = result.substring(0, result.length() - 2);
        }
        return result;
    }
}
