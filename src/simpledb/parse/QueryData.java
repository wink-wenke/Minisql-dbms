package simpledb.parse;

import java.util.*;

import simpledb.query.*;

/**
 * Data for the SQL <i>select</i> statement.
 * <p>
 * 增强版：支持 ORDER BY 排序字段列表。
 *
 * @author Edward Sciore (原始), 增强版
 */
public class QueryData {
    private List<String> fields;
    private Collection<String> tables;
    private Predicate pred;
    private List<String> orderby;

    /**
     * 旧构造（无 ORDER BY）。
     */
    public QueryData(List<String> fields, Collection<String> tables, Predicate pred) {
        this(fields, tables, pred, Collections.emptyList());
    }

    /**
     * 新构造（带 ORDER BY）。
     */
    public QueryData(List<String> fields, Collection<String> tables, Predicate pred, List<String> orderby) {
        this.fields = fields;
        this.tables = tables;
        this.pred = pred;
        this.orderby = orderby;
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
        if (!orderby.isEmpty()) {
            result += " order by ";
            for (String fldname : orderby)
                result += fldname + ", ";
            result = result.substring(0, result.length() - 2);
        }
        return result;
    }
}
