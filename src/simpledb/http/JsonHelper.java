package simpledb.http;

import java.util.List;
import simpledb.query.Constant;
import simpledb.query.Expression;
import simpledb.query.Predicate;
import simpledb.query.Term;
import simpledb.query.CompOp;
import simpledb.shared.ExecuteResult;
import simpledb.parse.Token;
import simpledb.ast.*;
import simpledb.record.Schema;
import simpledb.materialize.AggregationFn;
import simpledb.materialize.CountFn;
import simpledb.materialize.MaxFn;
import simpledb.materialize.MinFn;
import simpledb.materialize.SumFn;
import simpledb.storage.BufferManager;
import simpledb.storage.CacheStats;

/**
 * 轻量级 JSON 序列化工具，无需外部依赖。
 * 仅处理项目中需要序列化的几种数据类型。
 */
public class JsonHelper {

    public static String escape(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':  sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n");  break;
                case '\r': sb.append("\\r");  break;
                case '\t': sb.append("\\t");  break;
                default:   sb.append(c);
            }
        }
        return sb.toString();
    }

    public static String toJson(ExecuteResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"type\":\"").append(result.getType()).append("\"");

        if (result.getType() == ExecuteResult.ResultType.QUERY) {
            sb.append(",\"columns\":[");
            List<String> cols = result.getColumnNames();
            for (int i = 0; i < cols.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append("\"").append(escape(cols.get(i))).append("\"");
            }
            sb.append("],\"rows\":[");
            List<List<Constant>> rows = result.getRows();
            for (int r = 0; r < rows.size(); r++) {
                if (r > 0) sb.append(",");
                sb.append("[");
                List<Constant> row = rows.get(r);
                for (int c = 0; c < row.size(); c++) {
                    if (c > 0) sb.append(",");
                    Constant val = row.get(c);
                    if (val.asString() != null) {
                        sb.append("\"").append(escape(val.asString())).append("\"");
                    } else {
                        sb.append(val.asInt());
                    }
                }
                sb.append("]");
            }
            sb.append("]");
        } else {
            sb.append(",\"affectedRows\":").append(result.getAffectedRows());
        }
        sb.append("}");
        return sb.toString();
    }

    public static String toJsonTokens(List<Token> tokens) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"tokens\":[");
        for (int i = 0; i < tokens.size(); i++) {
            if (i > 0) sb.append(",");
            Token t = tokens.get(i);
            sb.append("{");
            sb.append("\"type\":\"").append(t.type()).append("\"");
            sb.append(",\"lexeme\":\"").append(escape(t.lexeme())).append("\"");
            sb.append(",\"line\":").append(t.line());
            sb.append(",\"column\":").append(t.column());
            sb.append("}");
        }
        sb.append("]}");
        return sb.toString();
    }

    public static String toJsonExplain(String sql, String planBefore, String planAfter,
                                        int blocksAccessed, int recordsOutput) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"sql\":\"").append(escape(sql)).append("\"");
        sb.append(",\"planBefore\":\"").append(escape(planBefore)).append("\"");
        sb.append(",\"planAfter\":\"").append(escape(planAfter)).append("\"");
        sb.append(",\"blocksAccessed\":").append(blocksAccessed);
        sb.append(",\"recordsOutput\":").append(recordsOutput);
        sb.append("}");
        return sb.toString();
    }

    public static String toJsonCacheStats(CacheStats stats) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"accessCount\":").append(stats.getAccessCount());
        sb.append(",\"hitCount\":").append(stats.getHitCount());
        sb.append(",\"missCount\":").append(stats.getMissCount());
        sb.append(",\"evictionCount\":").append(stats.getEvictionCount());
        sb.append(",\"hitRate\":").append(stats.hitRate());
        sb.append("}");
        return sb.toString();
    }

    public static String toJsonTables(List<String> tables) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"tables\":[");
        for (int i = 0; i < tables.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(escape(tables.get(i))).append("\"");
        }
        sb.append("]}");
        return sb.toString();
    }

    public static String toJsonColumns(String tableName, List<simpledb.shared.ColumnDef> columns) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"table\":\"").append(escape(tableName)).append("\",\"columns\":[");
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) sb.append(",");
            simpledb.shared.ColumnDef col = columns.get(i);
            sb.append("{");
            sb.append("\"name\":\"").append(escape(col.name())).append("\"");
            sb.append(",\"type\":\"").append(col.type()).append("\"");
            sb.append(",\"length\":").append(col.length());
            sb.append("}");
        }
        sb.append("]}");
        return sb.toString();
    }

    public static String toJsonBufferSlots(List<BufferManager.SlotInfo> slots) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"slots\":[");
        for (int i = 0; i < slots.size(); i++) {
            if (i > 0) sb.append(",");
            BufferManager.SlotInfo s = slots.get(i);
            sb.append("{");
            sb.append("\"slot\":").append(s.slotIndex);
            sb.append(",\"file\":").append(s.fileName != null ? "\"" + escape(s.fileName) + "\"" : "null");
            sb.append(",\"block\":").append(s.blockNumber);
            sb.append(",\"pins\":").append(s.pinCount);
            sb.append(",\"dirty\":").append(s.dirty);
            sb.append(",\"txnum\":").append(s.txnum);
            sb.append("}");
        }
        sb.append("]}");
        return sb.toString();
    }

    public static String error(String type, String message, int line, int column) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"success\":false");
        sb.append(",\"error\":{");
        sb.append("\"type\":\"").append(escape(type)).append("\"");
        sb.append(",\"message\":\"").append(escape(message)).append("\"");
        if (line > 0) sb.append(",\"line\":").append(line);
        if (column > 0) sb.append(",\"column\":").append(column);
        sb.append("}}");
        return sb.toString();
    }

    public static String success(String message) {
        return "{\"success\":true,\"message\":\"" + escape(message) + "\"}";
    }

    // ==================== AST Serialization ====================

    public static String toJsonAst(AstNode data) {
        StringBuilder sb = new StringBuilder();
        if (data instanceof SelectNode) {
            astQuery(sb, (SelectNode) data);
        } else if (data instanceof InsertNode) {
            astInsert(sb, (InsertNode) data);
        } else if (data instanceof DeleteNode) {
            astDelete(sb, (DeleteNode) data);
        } else if (data instanceof UpdateNode) {
            astModify(sb, (UpdateNode) data);
        } else if (data instanceof CreateTableNode) {
            astCreateTable(sb, (CreateTableNode) data);
        } else if (data instanceof CreateViewNode) {
            astCreateView(sb, (CreateViewNode) data);
        } else if (data instanceof CreateIndexNode) {
            astCreateIndex(sb, (CreateIndexNode) data);
        } else if (data instanceof DropTableNode) {
            astDropTable(sb, (DropTableNode) data);
        } else if (data instanceof ExplainNode) {
            astExplain(sb, (ExplainNode) data);
        } else {
            sb.append("{\"type\":\"Unknown\",\"label\":\"Unknown statement\"}");
        }
        return sb.toString();
    }

    // --- SELECT ---
    private static void astQuery(StringBuilder sb, SelectNode qd) {
        sb.append("{\"type\":\"Query\",\"label\":\"SELECT\",\"children\":[");

        // SelectList
        sb.append("{\"type\":\"SelectList\",\"label\":\"字段列表\",\"children\":[");
        List<String> fields = qd.fields();
        List<AggregationFn> aggfns = qd.aggfns();
        // 聚合函数按 fieldName() 映射到字段
        java.util.Map<String, String> aggMap = new java.util.HashMap<>();
        for (AggregationFn fn : aggfns) {
            String fnName = fn.getClass().getSimpleName().replace("Fn", "").toUpperCase();
            aggMap.put(fn.fieldName(), fnName);
        }
        for (int i = 0; i < fields.size(); i++) {
            if (i > 0) sb.append(",");
            String f = fields.get(i);
            String agg = aggMap.get(f);
            if (agg != null) {
                sb.append("{\"type\":\"Aggregation\",\"label\":\"").append(agg).append("\",\"detail\":\"").append(escape(f)).append("\"}");
            } else if (f.equals("*")) {
                sb.append("{\"type\":\"Wildcard\",\"label\":\"*\"}");
            } else {
                sb.append("{\"type\":\"Column\",\"label\":\"").append(escape(f)).append("\"}");
            }
        }
        sb.append("]},");
        // 是否有聚合
        sb.append("{\"type\":\"Aggregations\",\"label\":\"聚合\",\"detail\":\"").append(aggfns.size()).append("\"},");

        // FromClause
        sb.append("{\"type\":\"FromClause\",\"label\":\"FROM\",\"children\":[");
        java.util.Collection<String> tables = qd.tables();
        int idx = 0;
        for (String tbl : tables) {
            if (idx > 0) sb.append(",");
            sb.append("{\"type\":\"Table\",\"label\":\"").append(escape(tbl)).append("\"}");
            idx++;
        }
        sb.append("]}, ");

        // WhereClause
        Predicate pred = qd.pred();
        sb.append("{\"type\":\"WhereClause\",\"label\":\"WHERE\",\"children\":[");
        astPredicate(sb, pred);
        sb.append("]}, ");

        // GroupBy
        List<String> groupby = qd.groupby();
        sb.append("{\"type\":\"GroupBy\",\"label\":\"GROUP BY\",\"children\":[");
        for (int i = 0; i < groupby.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("{\"type\":\"Column\",\"label\":\"").append(escape(groupby.get(i))).append("\"}");
        }
        sb.append("]}, ");

        // OrderBy
        List<simpledb.ast.OrderByEntry> orderby = qd.orderby();
        sb.append("{\"type\":\"OrderBy\",\"label\":\"ORDER BY\",\"children\":[");
        for (int i = 0; i < orderby.size(); i++) {
            if (i > 0) sb.append(",");
            simpledb.ast.OrderByEntry entry = orderby.get(i);
            String dir = entry.isAscending() ? "ASC" : "DESC";
            sb.append("{\"type\":\"OrderByField\",\"label\":\"").append(escape(entry.field()))
              .append("\",\"detail\":\"").append(dir).append("\"}");
        }
        sb.append("]}");

        sb.append("]}");
    }

    // --- INSERT ---
    private static void astInsert(StringBuilder sb, InsertNode id) {
        sb.append("{\"type\":\"Insert\",\"label\":\"INSERT INTO\",\"children\":[");

        // Table
        sb.append("{\"type\":\"Table\",\"label\":\"").append(escape(id.tableName())).append("\"},");

        // Fields
        sb.append("{\"type\":\"FieldList\",\"label\":\"字段\",\"children\":[");
        List<String> fields = id.fields();
        for (int i = 0; i < fields.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("{\"type\":\"Column\",\"label\":\"").append(escape(fields.get(i))).append("\"}");
        }
        sb.append("]},");

        // Values
        sb.append("{\"type\":\"ValueList\",\"label\":\"VALUES\",\"children\":[");
        List<Constant> vals = id.vals();
        for (int i = 0; i < vals.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("{\"type\":\"Value\",\"label\":\"").append(escape(constantToString(vals.get(i)))).append("\"}");
        }
        sb.append("]}");

        sb.append("]}");
    }

    // --- DELETE ---
    private static void astDelete(StringBuilder sb, DeleteNode dd) {
        sb.append("{\"type\":\"Delete\",\"label\":\"DELETE FROM\",\"children\":[");

        sb.append("{\"type\":\"Table\",\"label\":\"").append(escape(dd.tableName())).append("\"},");

        sb.append("{\"type\":\"WhereClause\",\"label\":\"WHERE\",\"children\":[");
        astPredicate(sb, dd.pred());
        sb.append("]}");

        sb.append("]}");
    }

    // --- UPDATE ---
    private static void astModify(StringBuilder sb, UpdateNode md) {
        sb.append("{\"type\":\"Update\",\"label\":\"UPDATE\",\"children\":[");

        sb.append("{\"type\":\"Table\",\"label\":\"").append(escape(md.tableName())).append("\"},");

        // SET clause
        sb.append("{\"type\":\"SetClause\",\"label\":\"SET\",\"children\":[");
        sb.append("{\"type\":\"Assignment\",\"label\":\"").append(escape(md.targetField())).append("\",\"children\":[");
        astExpression(sb, md.newValue());
        sb.append("]}");
        sb.append("]},");

        // WHERE clause
        sb.append("{\"type\":\"WhereClause\",\"label\":\"WHERE\",\"children\":[");
        astPredicate(sb, md.pred());
        sb.append("]}");

        sb.append("]}");
    }

    // --- CREATE TABLE ---
    private static void astCreateTable(StringBuilder sb, CreateTableNode ctd) {
        sb.append("{\"type\":\"CreateTable\",\"label\":\"CREATE TABLE\",\"children\":[");

        sb.append("{\"type\":\"Table\",\"label\":\"").append(escape(ctd.tableName())).append("\"},");

        Schema sch = ctd.newSchema();
        List<String> flds = sch.fields();
        sb.append("{\"type\":\"ColumnDefs\",\"label\":\"列定义\",\"children\":[");
        for (int i = 0; i < flds.size(); i++) {
            if (i > 0) sb.append(",");
            String fname = flds.get(i);
            int ftype = sch.type(fname);
            int flen = sch.length(fname);
            String typeStr = (ftype == java.sql.Types.INTEGER) ? "int" : "varchar(" + flen + ")";
            sb.append("{\"type\":\"ColumnDef\",\"label\":\"").append(escape(fname))
              .append("\",\"detail\":\"").append(typeStr).append("\"}");
        }
        sb.append("]}");

        sb.append("]}");
    }

    // --- CREATE VIEW ---
    private static void astCreateView(StringBuilder sb, CreateViewNode cvd) {
        sb.append("{\"type\":\"CreateView\",\"label\":\"CREATE VIEW\",\"children\":[");
        sb.append("{\"type\":\"View\",\"label\":\"").append(escape(cvd.viewName())).append("\"},");
        sb.append("{\"type\":\"ViewDef\",\"label\":\"AS\",\"detail\":\"").append(escape(cvd.viewDef())).append("\"}");
        sb.append("]}");
    }

    // --- CREATE INDEX ---
    private static void astCreateIndex(StringBuilder sb, CreateIndexNode cid) {
        sb.append("{\"type\":\"CreateIndex\",\"label\":\"CREATE INDEX\",\"children\":[");
        sb.append("{\"type\":\"Index\",\"label\":\"").append(escape(cid.indexName())).append("\"},");
        sb.append("{\"type\":\"Table\",\"label\":\"").append(escape(cid.tableName())).append("\"},");
        sb.append("{\"type\":\"Column\",\"label\":\"").append(escape(cid.fieldName())).append("\"}");
        sb.append("]}");
    }

    // --- DROP TABLE ---
    private static void astDropTable(StringBuilder sb, DropTableNode dtd) {
        sb.append("{\"type\":\"DropTable\",\"label\":\"DROP TABLE\",\"children\":[");
        sb.append("{\"type\":\"Table\",\"label\":\"").append(escape(dtd.tableName())).append("\"}");
        if (dtd.ifExists()) {
            sb.append(",{\"type\":\"IfExists\",\"label\":\"IF EXISTS\"}");
        }
        sb.append("]}");
    }

    // --- EXPLAIN ---
    private static void astExplain(StringBuilder sb, ExplainNode en) {
        sb.append("{\"type\":\"Explain\",\"label\":\"EXPLAIN\",\"children\":[");
        // 内嵌被解释的 SELECT 查询 AST
        astQuery(sb, en.query());
        sb.append("]}");
    }

    // --- Expression (recursive) ---
    private static void astExpression(StringBuilder sb, Expression expr) {
        if (expr.isArithmetic()) {
            String op;
            switch (expr.arithOp()) {
                case PLUS:     op = "+"; break;
                case MINUS:    op = "-"; break;
                case MULTIPLY: op = "*"; break;
                case DIVIDE:   op = "/"; break;
                default:       op = "?";
            }
            sb.append("{\"type\":\"Arithmetic\",\"label\":\"").append(op).append("\",\"children\":[");
            astExpression(sb, expr.left());
            sb.append(",");
            astExpression(sb, expr.right());
            sb.append("]}");
        } else if (expr.isFieldName()) {
            sb.append("{\"type\":\"Field\",\"label\":\"").append(escape(expr.asFieldName())).append("\"}");
        } else {
            sb.append("{\"type\":\"Constant\",\"label\":\"").append(escape(constantToString(expr.asConstant()))).append("\"}");
        }
    }

    // --- Predicate (recursive) ---
    private static void astPredicate(StringBuilder sb, Predicate pred) {
        // 检查是否为空谓词（TRUE）
        if (pred.isAlwaysTrue()) {
            sb.append("{\"type\":\"True\",\"label\":\"TRUE\"}");
            return;
        }
        // 通过 toString 和 collectTerms 判断类型
        String s = pred.toString();
        if (s.isEmpty()) {
            sb.append("{\"type\":\"True\",\"label\":\"TRUE\"}");
            return;
        }

        // 简单的基于字符串的判断
        if (s.startsWith("(NOT ")) {
            sb.append("{\"type\":\"NOT\",\"label\":\"NOT\",\"children\":[");
            // 提取 NOT 内部的谓词
            String inner = s.substring(5, s.length() - 1); // 去掉 "(NOT " 和 ")"
            astPredicateString(sb, inner);
            sb.append("]}");
        } else if (s.contains(" AND ")) {
            // AND 连接
            sb.append("{\"type\":\"AND\",\"label\":\"AND\",\"children\":[");
            int depth = 0;
            int splitAt = -1;
            // 从内向外查找最外层 AND
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if (c == '(') depth++;
                else if (c == ')') depth--;
                else if (depth == 0 && s.startsWith(" AND ", i)) {
                    splitAt = i;
                    break;
                }
            }
            if (splitAt > 0) {
                astPredicateString(sb, s.substring(0, splitAt));
                sb.append(",");
                astPredicateString(sb, s.substring(splitAt + 5));
            } else {
                sb.append("{\"type\":\"Term\",\"label\":\"").append(escape(s)).append("\"}");
            }
            sb.append("]}");
        } else if (s.contains(" OR ")) {
            sb.append("{\"type\":\"OR\",\"label\":\"OR\",\"children\":[");
            int depth = 0;
            int splitAt = -1;
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if (c == '(') depth++;
                else if (c == ')') depth--;
                else if (depth == 0 && s.startsWith(" OR ", i)) {
                    splitAt = i;
                    break;
                }
            }
            if (splitAt > 0) {
                astPredicateString(sb, s.substring(0, splitAt));
                sb.append(",");
                astPredicateString(sb, s.substring(splitAt + 4));
            } else {
                sb.append("{\"type\":\"Term\",\"label\":\"").append(escape(s)).append("\"}");
            }
            sb.append("]}");
        } else {
            // 单个 Term
            astPredicateString(sb, s);
        }
    }

    private static void astPredicateString(StringBuilder sb, String s) {
        s = s.trim();
        if (s.isEmpty()) {
            sb.append("{\"type\":\"True\",\"label\":\"TRUE\"}");
            return;
        }
        if (s.equals("TRUE")) {
            sb.append("{\"type\":\"True\",\"label\":\"TRUE\"}");
            return;
        }
        if (s.equals("FALSE")) {
            sb.append("{\"type\":\"False\",\"label\":\"FALSE\"}");
            return;
        }
        if (s.startsWith("(NOT ")) {
            sb.append("{\"type\":\"NOT\",\"label\":\"NOT\",\"children\":[");
            astPredicateString(sb, s.substring(5, s.length() - 1));
            sb.append("]}");
            return;
        }

        // 检查 AND/OR（最外层）
        int depth = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') depth--;
            else if (depth == 0 && s.startsWith(" AND ", i)) {
                sb.append("{\"type\":\"AND\",\"label\":\"AND\",\"children\":[");
                astPredicateString(sb, s.substring(0, i));
                sb.append(",");
                astPredicateString(sb, s.substring(i + 5));
                sb.append("]}");
                return;
            }
        }
        depth = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') depth--;
            else if (depth == 0 && s.startsWith(" OR ", i)) {
                sb.append("{\"type\":\"OR\",\"label\":\"OR\",\"children\":[");
                astPredicateString(sb, s.substring(0, i));
                sb.append(",");
                astPredicateString(sb, s.substring(i + 4));
                sb.append("]}");
                return;
            }
        }

        // 去除外层括号
        if (s.startsWith("(") && s.endsWith(")")) {
            astPredicateString(sb, s.substring(1, s.length() - 1));
            return;
        }

        // Term：解析 lhs op rhs
        String[] ops = {"!=", "<=", ">=", "=", "<", ">"};
        for (String op : ops) {
            int pos = s.indexOf(" " + op + " ");
            if (pos > 0) {
                String lhs = s.substring(0, pos).trim();
                String rhs = s.substring(pos + op.length() + 2).trim();
                sb.append("{\"type\":\"Comparison\",\"label\":\"").append(op).append("\",\"children\":[");
                astExprString(sb, lhs);
                sb.append(",");
                astExprString(sb, rhs);
                sb.append("]}");
                return;
            }
        }

        // 无法解析，作为原始文本
        sb.append("{\"type\":\"Expression\",\"label\":\"").append(escape(s)).append("\"}");
    }

    private static void astExprString(StringBuilder sb, String s) {
        s = s.trim();
        // 检查算术运算
        int depth = 0;
        for (int i = s.length() - 1; i >= 0; i--) {
            char c = s.charAt(i);
            if (c == ')') depth++;
            else if (c == '(') depth--;
            else if (depth == 0 && (c == '+' || c == '-')) {
                sb.append("{\"type\":\"Arithmetic\",\"label\":\"").append(c).append("\",\"children\":[");
                astExprString(sb, s.substring(0, i));
                sb.append(",");
                astExprString(sb, s.substring(i + 1));
                sb.append("]}");
                return;
            }
        }
        depth = 0;
        for (int i = s.length() - 1; i >= 0; i--) {
            char c = s.charAt(i);
            if (c == ')') depth++;
            else if (c == '(') depth--;
            else if (depth == 0 && (c == '*' || c == '/')) {
                sb.append("{\"type\":\"Arithmetic\",\"label\":\"").append(c).append("\",\"children\":[");
                astExprString(sb, s.substring(0, i));
                sb.append(",");
                astExprString(sb, s.substring(i + 1));
                sb.append("]}");
                return;
            }
        }
        // 去除外层括号
        if (s.startsWith("(") && s.endsWith(")")) {
            astExprString(sb, s.substring(1, s.length() - 1));
            return;
        }
        // 常量或字段
        if ((s.startsWith("'") && s.endsWith("'")) || s.matches("-?\\d+")) {
            sb.append("{\"type\":\"Constant\",\"label\":\"").append(escape(s)).append("\"}");
        } else {
            sb.append("{\"type\":\"Field\",\"label\":\"").append(escape(s)).append("\"}");
        }
    }

    private static String constantToString(Constant c) {
        if (c.asString() != null) return "'" + c.asString() + "'";
        return String.valueOf(c.asInt());
    }
}
