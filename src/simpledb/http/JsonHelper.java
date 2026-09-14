package simpledb.http;

import java.util.List;
import simpledb.query.Constant;
import simpledb.shared.ExecuteResult;
import simpledb.parse.Token;
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
        sb.append(",\"hitRate\":").append(String.format("%.4f", stats.hitRate()));
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
}
