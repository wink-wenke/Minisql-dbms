package simpledb.http;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import simpledb.engine.CatalogReader;
import simpledb.logical.AggregateSpec;
import simpledb.logical.CreateTablePlan;
import simpledb.logical.DeletePlan;
import simpledb.logical.FilterPlan;
import simpledb.logical.InsertPlan;
import simpledb.logical.JoinPlan;
import simpledb.logical.LogicalPlan;
import simpledb.logical.OrderByPlan;
import simpledb.logical.ProjectPlan;
import simpledb.logical.SeqScanPlan;
import simpledb.logical.UpdatePlan;
import simpledb.logical.GroupByPlan;
import simpledb.query.CompOp;
import simpledb.query.Constant;
import simpledb.query.Expression;
import simpledb.query.Predicate;
import simpledb.query.Term;
import simpledb.shared.ColumnDef;
import simpledb.shared.ColumnType;
import simpledb.tx.Transaction;

/**
 * 把一小撮 SQL 子集翻译成成员B引擎的 {@link LogicalPlan}。
 *
 * 这是 HTTP 层到引擎模块的适配层：现有 /api/execute 走的是 SimpleDB 原生
 * Planner，成员B的 Executor 并没有接进去；本类让前端可以直接驱动
 * LogicalPlan -> Executor 这条链路，从而演示 UPDATE / JOIN / ORDER BY /
 * GROUP BY 等 Level-4 能力。
 *
 * 支持语法：
 *   CREATE TABLE t (c1 INT, c2 VARCHAR(20), ...)
 *   INSERT INTO t (c1, c2) VALUES (1, 'abc')
 *   SELECT [cols|*|COUNT(x),SUM(y),AVG(y),MIN(y),MAX(y)]
 *          FROM t [JOIN t2 ON t1.a = t2.b] [WHERE c OP v [AND ...]]
 *          [GROUP BY g] [ORDER BY c [ASC|DESC]]
 *   UPDATE t SET c = v [, c2 = v2] [WHERE c OP v]
 *   DELETE FROM t [WHERE c OP v]
 *
 * 比较运算符：=  !=  <>  <  <=  >  >=
 */
public class EngineSqlTranslator {

    /** 翻译结果：计划 + 是否需要在结果层反转（ORDER BY DESC）。 */
    public static class Translation {
        public final LogicalPlan plan;
        public final boolean reverse;

        Translation(LogicalPlan plan, boolean reverse) {
            this.plan = plan;
            this.reverse = reverse;
        }
    }

    private final CatalogReader catalog;

    public EngineSqlTranslator(CatalogReader catalog) {
        this.catalog = catalog;
    }

    public Translation translate(String sql, Transaction tx) {
        String s = sql == null ? "" : sql.trim();
        if (s.endsWith(";")) s = s.substring(0, s.length() - 1).trim();
        if (s.isEmpty()) throw new IllegalArgumentException("SQL 为空");

        Cursor c = new Cursor(tokenize(s));
        String kw = c.next().toUpperCase();
        switch (kw) {
            case "CREATE": return new Translation(parseCreate(c), false);
            case "INSERT": return new Translation(parseInsert(c), false);
            case "DELETE": return new Translation(parseDelete(c), false);
            case "UPDATE": return new Translation(parseUpdate(c), false);
            case "SELECT": return parseSelect(c, tx);
            default:
                throw new IllegalArgumentException(
                    "不支持的语句，仅支持 CREATE/INSERT/SELECT/UPDATE/DELETE，收到: " + kw);
        }
    }

    // ============ 各语句 ============

    private LogicalPlan parseCreate(Cursor c) {
        c.expect("TABLE");
        String table = c.next();
        c.expect("(");
        List<ColumnDef> cols = new ArrayList<>();
        while (true) {
            String name = c.next();
            String type = c.next().toUpperCase();
            int len = 0;
            if (type.startsWith("VARCHAR")) {
                len = 20;
                if ("(".equals(c.peek())) {
                    c.next();
                    len = Integer.parseInt(c.next());
                    c.expect(")");
                }
                cols.add(new ColumnDef(name, ColumnType.VARCHAR, len));
            } else if (type.equals("INT") || type.equals("INTEGER")) {
                cols.add(new ColumnDef(name, ColumnType.INTEGER, 0));
            } else {
                throw new IllegalArgumentException("不支持的字段类型: " + type + "（仅支持 INT / VARCHAR(n)）");
            }
            if (c.eat(",")) continue;
            c.expect(")");
            break;
        }
        return new CreateTablePlan(table, cols);
    }

    private LogicalPlan parseInsert(Cursor c) {
        c.expect("INTO");
        String table = c.next();
        List<String> cols = new ArrayList<>();
        if ("(".equals(c.peek())) {
            c.next();
            while (true) {
                cols.add(c.next());
                if (c.eat(",")) continue;
                c.expect(")");
                break;
            }
        }
        c.expect("VALUES");
        c.expect("(");
        List<Constant> vals = new ArrayList<>();
        while (true) {
            vals.add(parseLiteral(c.next()));
            if (c.eat(",")) continue;
            c.expect(")");
            break;
        }
        return new InsertPlan(table, cols, vals);
    }

    private LogicalPlan parseUpdate(Cursor c) {
        String table = c.next();
        c.expect("SET");
        List<String> cols = new ArrayList<>();
        List<Constant> vals = new ArrayList<>();
        while (true) {
            cols.add(c.next());
            c.expect("=");
            vals.add(parseLiteral(c.next()));
            if (c.eat(",")) continue;
            break;
        }
        Predicate p = parseWhereOpt(c);
        return new UpdatePlan(table, cols, vals, p == null ? new Predicate() : p);
    }

    private LogicalPlan parseDelete(Cursor c) {
        c.expect("FROM");
        String table = c.next();
        Predicate p = parseWhereOpt(c);
        return new DeletePlan(table, p == null ? new Predicate() : p);
    }

    private Translation parseSelect(Cursor c, Transaction tx) {
        // ---- SELECT 列表 ----
        List<String> items = new ArrayList<>();
        while (true) {
            StringBuilder item = new StringBuilder();
            while (c.hasNext() && !",".equals(c.peek()) && !c.peek().equalsIgnoreCase("FROM"))
                item.append(c.next());
            if (item.length() == 0)
                throw new IllegalArgumentException("SELECT 列表为空");
            items.add(item.toString());
            if (c.eat(",")) continue;
            break;
        }
        c.expect("FROM");
        String t1 = c.next();

        // ---- JOIN ----
        String t2 = null;
        Predicate joinPred = null;
        if (c.peek() != null && c.peek().equalsIgnoreCase("JOIN")) {
            c.next();
            t2 = c.next();
            c.expect("ON");
            String lhs = c.next();
            c.expect("=");
            String rhs = c.next();
            joinPred = new Predicate(new Term(
                new Expression(stripTable(lhs)), new Expression(stripTable(rhs))));
        }

        // ---- WHERE ----
        Predicate where = parseWhereOpt(c);

        // ---- GROUP BY ----
        List<String> groupFields = null;
        if (c.peek() != null && c.peek().equalsIgnoreCase("GROUP")) {
            c.next();
            c.expect("BY");
            groupFields = new ArrayList<>();
            while (true) {
                groupFields.add(c.next());
                if (c.eat(",")) continue;
                break;
            }
        }

        // ---- ORDER BY ----
        List<String> orderFields = null;
        boolean reverse = false;
        if (c.peek() != null && c.peek().equalsIgnoreCase("ORDER")) {
            c.next();
            c.expect("BY");
            orderFields = new ArrayList<>();
            while (true) {
                orderFields.add(c.next());
                if (c.peek() != null && c.peek().equalsIgnoreCase("DESC")) {
                    c.next();
                    reverse = true;
                } else if (c.peek() != null && c.peek().equalsIgnoreCase("ASC")) {
                    c.next();
                }
                if (c.eat(",")) continue;
                break;
            }
        }

        // ---- 拆分聚合 vs 普通列 ----
        List<AggregateSpec> aggs = new ArrayList<>();
        List<String> plainCols = new ArrayList<>();
        boolean star = false;
        for (String item : items) {
            String up = item.toUpperCase();
            if (item.equals("*")) { star = true; continue; }
            int lp = up.indexOf('(');
            if (lp > 0 && up.endsWith(")")) {
                String fn = up.substring(0, lp);
                String col = item.substring(lp + 1, item.length() - 1).trim();
                AggregateSpec.Func f;
                try {
                    f = AggregateSpec.Func.valueOf(fn);
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("不支持的函数: " + fn
                        + "（仅支持 COUNT/SUM/AVG/MIN/MAX）");
                }
                aggs.add(new AggregateSpec(f, col));
            } else {
                plainCols.add(item);
            }
        }

        // ---- 自底向上搭计划 ----
        LogicalPlan plan;
        if (t2 != null)
            plan = new JoinPlan(new SeqScanPlan(t1, colsOf(t1, tx)),
                                new SeqScanPlan(t2, colsOf(t2, tx)), joinPred);
        else
            plan = new SeqScanPlan(t1, colsOf(t1, tx));

        if (where != null) plan = new FilterPlan(plan, where);

        if (!aggs.isEmpty() || groupFields != null) {
            List<String> groups = groupFields != null ? groupFields : Collections.<String>emptyList();
            plan = new GroupByPlan(plan, groups, aggs);
        } else if (orderFields != null) {
            // 引擎的 OrderByPlan 只支持升序：先投影出需要的列再排序
            if (!star) plan = new ProjectPlan(plan, plainCols);
            plan = new OrderByPlan(plan, orderFields);
        } else if (!star) {
            plan = new ProjectPlan(plan, plainCols);
        }

        return new Translation(plan, reverse);
    }

    // ============ 谓词 ============

    private Predicate parseWhereOpt(Cursor c) {
        if (c.peek() != null && c.peek().equalsIgnoreCase("WHERE")) {
            c.next();
            return parsePredicate(c);
        }
        return null;
    }

    private Predicate parsePredicate(Cursor c) {
        Predicate p = null;
        while (true) {
            String lhs = stripTable(c.next());
            CompOp op = toOp(c.next());
            String rhsTok = c.next();
            Expression l = new Expression(lhs);
            Expression r;
            if (rhsTok.startsWith("'"))
                r = new Expression(new Constant(unquote(rhsTok)));
            else if (isNumber(rhsTok))
                r = new Expression(new Constant(Integer.valueOf(rhsTok)));
            else
                r = new Expression(stripTable(rhsTok));
            Predicate cur = new Predicate(new Term(l, r, op));
            p = (p == null) ? cur : Predicate.and(p, cur);
            if (c.peek() != null && c.peek().equalsIgnoreCase("AND")) {
                c.next();
                continue;
            }
            break;
        }
        return p;
    }

    private static CompOp toOp(String tok) {
        if ("=".equals(tok))  return CompOp.EQUALS;
        if ("!=".equals(tok) || "<>".equals(tok)) return CompOp.NOT_EQUALS;
        if ("<".equals(tok))  return CompOp.LESS;
        if ("<=".equals(tok)) return CompOp.LESS_EQUALS;
        if (">".equals(tok))  return CompOp.GREATER;
        if (">=".equals(tok)) return CompOp.GREATER_EQUALS;
        throw new IllegalArgumentException("不支持的比较运算符: " + tok);
    }

    // ============ 词法 ============

    private static List<String> tokenize(String s) {
        List<String> out = new ArrayList<>();
        int i = 0;
        while (i < s.length()) {
            char ch = s.charAt(i);
            if (Character.isWhitespace(ch)) { i++; continue; }
            if (ch == '\'') {
                int j = i + 1;
                StringBuilder sb = new StringBuilder();
                while (j < s.length() && s.charAt(j) != '\'') { sb.append(s.charAt(j)); j++; }
                out.add("'" + sb.toString() + "'");
                i = j + 1;
                continue;
            }
            if (ch == '(' || ch == ')' || ch == ',' || ch == '*' || ch == ';') {
                out.add(String.valueOf(ch));
                i++;
                continue;
            }
            if (ch == '=' || ch == '<' || ch == '>' || ch == '!') {
                if (i + 1 < s.length()) {
                    String two = "" + ch + s.charAt(i + 1);
                    if (two.equals("<=") || two.equals(">=") || two.equals("!=") || two.equals("<>")) {
                        out.add(two);
                        i += 2;
                        continue;
                    }
                }
                out.add(String.valueOf(ch));
                i++;
                continue;
            }
            int j = i;
            while (j < s.length() && !Character.isWhitespace(s.charAt(j)) && !isPunct(s.charAt(j))) j++;
            out.add(s.substring(i, j));
            i = j;
        }
        return out;
    }

    private static boolean isPunct(char ch) {
        return ch == '(' || ch == ')' || ch == ',' || ch == '*' || ch == ';'
            || ch == '=' || ch == '<' || ch == '>' || ch == '!' || ch == '\'';
    }

    // ============ 小工具 ============

    private List<ColumnDef> colsOf(String table, Transaction tx) {
        List<ColumnDef> c = catalog.getColumns(table, tx);
        if (c == null || c.isEmpty())
            throw new IllegalArgumentException("表不存在或没有字段: " + table
                + "（请先用 CREATE TABLE 建表）");
        return c;
    }

    private static String stripTable(String s) {
        int d = s.indexOf('.');
        return d >= 0 ? s.substring(d + 1) : s;
    }

    private static Constant parseLiteral(String tok) {
        if (tok.startsWith("'")) return new Constant(unquote(tok));
        if (isNumber(tok)) return new Constant(Integer.valueOf(tok));
        return new Constant(tok);
    }

    private static String unquote(String tok) {
        return tok.length() >= 2 ? tok.substring(1, tok.length() - 1) : tok;
    }

    private static boolean isNumber(String s) {
        if (s.isEmpty()) return false;
        int i = (s.charAt(0) == '-') ? 1 : 0;
        if (i >= s.length()) return false;
        for (; i < s.length(); i++)
            if (!Character.isDigit(s.charAt(i))) return false;
        return true;
    }

    /** 简单的 token 游标。 */
    private static class Cursor {
        private final List<String> t;
        private int i = 0;

        Cursor(List<String> t) { this.t = t; }

        boolean hasNext() { return i < t.size(); }

        String peek() { return hasNext() ? t.get(i) : null; }

        String next() {
            if (!hasNext()) throw new IllegalArgumentException("SQL 语句不完整，缺少内容");
            return t.get(i++);
        }

        boolean eat(String kw) {
            if (kw.equalsIgnoreCase(peek())) { i++; return true; }
            return false;
        }

        void expect(String kw) {
            if (!eat(kw))
                throw new IllegalArgumentException("语法错误：期望 '" + kw + "'，实际是 '" + peek() + "'");
        }
    }
}
