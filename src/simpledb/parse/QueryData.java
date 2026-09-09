package simpledb.parse;

import java.util.*;

import simpledb.query.*;
import simpledb.materialize.AggregationFn;

/**
 * Data for the SQL <i>select</i> statement.
 * <p>
 * 增强版：支持 ORDER BY、GROUP BY、聚合函数。
 *
 * @author Edward Sciore (原始), 增强版
 */
public class QueryData {
    private List<String> fields;
    private Collection<String> tables;
    private Predicate pred;
    private List<String> orderby;
    private List<String> groupby;
    private List<AggregationFn> aggfns;

    /**
     * 基础构造（无 ORDER BY / GROUP BY）。
     */
    public QueryData(List<String> fields, Collection<String> tables, Predicate pred) {
        this(fields, tables, pred, Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
    }

    /**
     * 带 ORDER BY 构造。
     */
    public QueryData(List<String> fields, Collection<String> tables, Predicate pred, List<String> orderby) {
        this(fields, tables, pred, orderby, Collections.emptyList(), Collections.emptyList());
    }

    /**
     * 完整构造。
     */
    public QueryData(List<String> fields, Collection<String> tables, Predicate pred,
                     List<String> orderby, List<String> groupby, List<AggregationFn> aggfns) {
        this.fields = fields;
        this.tables = tables;
        this.pred = pred;
        this.orderby = orderby;
        this.groupby = groupby;
        this.aggfns = aggfns;
    }

    public List<String> fields() {
        return fields;
    }

    public Collection<String> tables() {
        return tables;
    }

    public Predicate pred() {
        return pred;
    }

    /**
     * 返回 ORDER BY 字段列表（可能为空）。
     */
    public List<String> orderby() {
        return orderby;
    }

    /**
     * 返回 GROUP BY 字段列表（可能为空）。
     */
    public List<String> groupby() {
        return groupby;
    }

    /**
     * 返回聚合函数列表（可能为空）。
     */
    public List<AggregationFn> aggfns() {
        return aggfns;
    }

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
            for (String fldname : orderby)
                result += fldname + ", ";
            result = result.substring(0, result.length() - 2);
        }
        return result;
    }
}
