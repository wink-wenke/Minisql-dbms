package simpledb.parse;

import java.util.*;

import simpledb.query.*;
import simpledb.record.*;
import simpledb.materialize.*;
import simpledb.ast.*;

/**
 * SQL 语法分析器（递归下降）。
 * <p>
 * 支持的语句：SELECT / INSERT / DELETE / UPDATE / CREATE TABLE / VIEW / INDEX
 * <p>
 * 表达式优先级（由高到低）：
 * <pre>
 *   expression  → or_expr
 *   or_expr     → and_expr { OR and_expr }
 *   and_expr    → not_expr { AND not_expr }
 *   not_expr    → NOT not_expr | comparison
 *   comparison  → primary [ comp_op primary ]
 *   primary     → IDENTIFIER | constant | '(' expression ')'
 * </pre>
 *
 * @author Edward Sciore (原始), 增强版
 */
public class Parser {
    private Lexer lex;
    private String originalSql;

    public Parser(String s) {
        this.originalSql = s;
        lex = new Lexer(s);
    }

    // =================================================================
    //  表达式解析（递归下降，处理优先级）
    // =================================================================

    /**
     * 解析完整表达式（入口）。
     * expression → arith_expr
     * <p>
     * 支持算术运算：+ - * /
     * 优先级：* / > + -
     */
    public Expression expression() {
        return addExpr();
    }

    /**
     * 解析加减表达式：term { (+|-) term }
     */
    private Expression addExpr() {
        Expression left = mulExpr();
        while (lex.match(TokenType.PLUS) || lex.match(TokenType.MINUS)) {
            Token op = lex.next();
            Expression right = mulExpr();
            Expression.ArithOp arithOp = (op.type() == TokenType.PLUS)
                    ? Expression.ArithOp.PLUS
                    : Expression.ArithOp.MINUS;
            left = new Expression(left, right, arithOp);
        }
        return left;
    }

    /**
     * 解析乘除表达式：primary { (*|/) primary }
     */
    private Expression mulExpr() {
        Expression left = primaryExpr();
        while (lex.match(TokenType.STAR) || lex.match(TokenType.DIVIDE)) {
            Token op = lex.next();
            Expression right = primaryExpr();
            Expression.ArithOp arithOp = (op.type() == TokenType.STAR)
                    ? Expression.ArithOp.MULTIPLY
                    : Expression.ArithOp.DIVIDE;
            left = new Expression(left, right, arithOp);
        }
        return left;
    }

    /**
     * 解析基本表达式：IDENTIFIER | constant | '(' expression ')'
     */
    private Expression primaryExpr() {
        if (lex.match(TokenType.LPAREN)) {
            lex.eat(TokenType.LPAREN);
            Expression expr = expression();
            lex.eat(TokenType.RPAREN);
            return expr;
        }
        if (lex.matchId())
            return new Expression(field());
        else
            return new Expression(constant());
    }

    /**
     * 解析 or_expr：and_expr { OR and_expr }
     */
    private Predicate orExpr() {
        Predicate left = andExpr();
        while (lex.matchKeyword("or")) {
            lex.eatKeyword("or");
            Predicate right = andExpr();
            left = Predicate.or(left, right);
        }
        return left;
    }

    /**
     * 解析 and_expr：not_expr { AND not_expr }
     */
    private Predicate andExpr() {
        Predicate left = notExpr();
        while (lex.matchKeyword("and")) {
            lex.eatKeyword("and");
            Predicate right = notExpr();
            left = Predicate.and(left, right);
        }
        return left;
    }

    /**
     * 解析 not_expr：
     * <pre>
     *   not_expr → NOT not_expr
     *            | '(' or_expr ')'    // 括号包裹的谓词
     *            | comparison
     * </pre>
     */
    private Predicate notExpr() {
        if (lex.matchKeyword("not")) {
            lex.eatKeyword("not");
            Predicate child = notExpr();
            return Predicate.not(child);
        }
        // 括号包裹的谓词：( ... OR ... AND ... )
        if (lex.matchDelim('(')) {
            lex.eatDelim('(');
            Predicate inner = orExpr();
            lex.eatDelim(')');
            return inner;
        }
        return comparison();
    }

    /**
     * 解析 comparison：primary [ comp_op primary ]
     * <p>
     * 如果没有比较运算符，则返回空谓词（TRUE）。
     */
    private Predicate comparison() {
        Expression lhs = primary();
        if (isCompOp()) {
            CompOp op = compOp();
            Expression rhs = primary();
            return new Predicate(new Term(lhs, rhs, op));
        }
        // 单独的表达式（非标准，但容错处理）
        return new Predicate();
    }

    /**
     * 解析 primary：IDENTIFIER | constant
     * <p>
     * 注意：这里返回 Expression 对象，用于比较的两侧。
     */
    private Expression primary() {
        return expression();
    }

    /**
     * 判断当前 Token 是否为比较运算符。
     */
    private boolean isCompOp() {
        Token t = lex.peek();
        return t.type() == TokenType.EQUALS
                || t.type() == TokenType.NOT_EQUALS
                || t.type() == TokenType.LESS
                || t.type() == TokenType.LESS_EQUALS
                || t.type() == TokenType.GREATER
                || t.type() == TokenType.GREATER_EQUALS;
    }

    /**
     * 消费并返回比较运算符。
     */
    private CompOp compOp() {
        Token t = lex.peek();
        switch (t.type()) {
            case EQUALS:         lex.eat(TokenType.EQUALS);         return CompOp.EQUALS;
            case NOT_EQUALS:     lex.eat(TokenType.NOT_EQUALS);     return CompOp.NOT_EQUALS;
            case LESS:           lex.eat(TokenType.LESS);           return CompOp.LESS;
            case LESS_EQUALS:    lex.eat(TokenType.LESS_EQUALS);    return CompOp.LESS_EQUALS;
            case GREATER:        lex.eat(TokenType.GREATER);        return CompOp.GREATER;
            case GREATER_EQUALS: lex.eat(TokenType.GREATER_EQUALS); return CompOp.GREATER_EQUALS;
            default:
                throw lex.syntaxError("期望比较运算符，但遇到 '" + t.lexeme() + "'");
        }
    }

    // =================================================================
    //  谓词解析（WHERE 子句入口）
    // =================================================================

    /**
     * 解析 WHERE 子句中的谓词。
     * 保持旧 API 兼容。
     */
    public Predicate predicate() {
        return orExpr();
    }

    // =================================================================
    //  查询语句解析
    // =================================================================

    /**
     * 解析 SELECT 语句。
     */
    public SelectNode query() {
        lex.eatKeyword("select");
        List<String> fields = selectList();
        List<AggregationFn> aggfns = new ArrayList<>();
        lex.eatKeyword("from");
        Collection<String> tables = tableList();
        Predicate pred = new Predicate();
        if (lex.matchKeyword("where")) {
            lex.eatKeyword("where");
            pred = predicate();
        }
        List<String> groupby = Collections.emptyList();
        if (lex.matchKeyword("group")) {
            lex.eatKeyword("group");
            lex.eatKeyword("by");
            groupby = fieldList();
            aggfns = extractAggFns(fields, groupby);
        }
        List<OrderByEntry> orderby = Collections.emptyList();
        if (lex.matchKeyword("order")) {
            lex.eatKeyword("order");
            lex.eatKeyword("by");
            orderby = orderByList();
        }
        consumeEnd();
        return new SelectNode(fields, tables, pred, orderby, groupby, aggfns);
    }

    private List<String> selectList() {
        List<String> L = new ArrayList<>();
        // 支持 SELECT *
        if (lex.peek().type() == TokenType.STAR) {
            lex.eat(TokenType.STAR);
            L.add("*");
            return L;
        }
        L.add(field());
        while (lex.matchDelim(',')) {
            lex.eatDelim(',');
            L.add(field());
        }
        return L;
    }

    private Collection<String> tableList() {
        Collection<String> L = new ArrayList<>();
        L.add(lex.eatId());
        while (lex.matchDelim(',')) {
            lex.eatDelim(',');
            L.add(lex.eatId());
        }
        return L;
    }

    /**
     * 从 SELECT 字段列表中提取聚合函数。
     * 将 COUNT(xxx) / MAX(xxx) 等替换为对应的聚合字段名（countofxxx, maxofxxx）。
     */
    private List<AggregationFn> extractAggFns(List<String> fields, List<String> groupby) {
        List<AggregationFn> fns = new ArrayList<>();
        for (int i = 0; i < fields.size(); i++) {
            String fld = fields.get(i);
            String srcField;
            if (fld.startsWith("countof")) {
                srcField = fld.substring(7);
                if (!groupby.contains(srcField)) fns.add(new CountFn(srcField));
            } else if (fld.startsWith("maxof")) {
                srcField = fld.substring(5);
                if (!groupby.contains(srcField)) fns.add(new MaxFn(srcField));
            } else if (fld.startsWith("minof")) {
                srcField = fld.substring(5);
                if (!groupby.contains(srcField)) fns.add(new MinFn(srcField));
            } else if (fld.startsWith("sumof")) {
                srcField = fld.substring(5);
                if (!groupby.contains(srcField)) fns.add(new SumFn(srcField));
            }
        }
        return fns;
    }

    // =================================================================
    //  语句结束检查
    // =================================================================

    /**
     * 消费语句末尾的分号（如果有）。
     * 如果后面还有未消费的非 EOF token，抛出语法错误。
     */
    private void consumeEnd() {
        if (lex.match(TokenType.SEMICOLON)) {
            lex.eat(TokenType.SEMICOLON);
        }
        if (lex.peek().type() != TokenType.EOF) {
            throw lex.syntaxError("语句末尾有多余内容: '" + lex.peek().lexeme() + "'");
        }
    }

    // =================================================================
    //  更新语句解析
    // =================================================================

    /**
     * 解析更新命令（INSERT / DELETE / UPDATE / CREATE）。
     */
    public AstNode updateCmd() {
        if (lex.matchKeyword("explain"))
            return explain();
        else if (lex.matchKeyword("insert"))
            return insert();
        else if (lex.matchKeyword("delete"))
            return delete();
        else if (lex.matchKeyword("update"))
            return modify();
        else if (lex.matchKeyword("drop"))
            return dropTable();
        else
            return create();
    }

    private ExplainNode explain() {
        lex.eatKeyword("explain");
        SelectNode sel = query();
        // 从原始 SQL 中提取子查询部分（去掉 EXPLAIN 前缀和末尾分号）
        String subSql = originalSql.trim();
        if (subSql.toUpperCase().startsWith("EXPLAIN"))
            subSql = subSql.substring(7).trim();
        if (subSql.endsWith(";")) subSql = subSql.substring(0, subSql.length() - 1);
        return new ExplainNode(sel, subSql);
    }

    private AstNode create() {
        lex.eatKeyword("create");
        if (lex.matchKeyword("table"))
            return createTable();
        else if (lex.matchKeyword("view"))
            return createView();
        else
            return createIndex();
    }

    // =================================================================
    //  DELETE
    // =================================================================

    public DeleteNode delete() {
        lex.eatKeyword("delete");
        lex.eatKeyword("from");
        String tblname = lex.eatId();
        Predicate pred = new Predicate();
        if (lex.matchKeyword("where")) {
            lex.eatKeyword("where");
            pred = predicate();
        }
        consumeEnd();
        return new DeleteNode(tblname, pred);
    }

    // =================================================================
    //  INSERT
    // =================================================================

    public InsertNode insert() {
        lex.eatKeyword("insert");
        lex.eatKeyword("into");
        String tblname = lex.eatId();
        List<String> flds;
        if (lex.matchDelim('(') && !lex.matchKeyword("values")) {
            // INSERT INTO t(col1, col2) VALUES (...)
            lex.eatDelim('(');
            flds = fieldList();
            lex.eatDelim(')');
        } else {
            // INSERT INTO t VALUES (...) — 无列名，稍后由 Planner 补全
            flds = new ArrayList<>();
        }
        lex.eatKeyword("values");
        lex.eatDelim('(');
        List<Constant> vals = constList();
        lex.eatDelim(')');
        consumeEnd();
        return new InsertNode(tblname, flds, vals);
    }

    private List<String> fieldList() {
        List<String> L = new ArrayList<>();
        L.add(field());
        while (lex.matchDelim(',')) {
            lex.eatDelim(',');
            L.add(field());
        }
        return L;
    }

    private List<OrderByEntry> orderByList() {
        List<OrderByEntry> L = new ArrayList<>();
        L.add(orderByField());
        while (lex.matchDelim(',')) {
            lex.eatDelim(',');
            L.add(orderByField());
        }
        return L;
    }

    private OrderByEntry orderByField() {
        String fldname = field();
        boolean ascending = true;
        if (lex.matchKeyword("asc")) {
            lex.eatKeyword("asc");
            ascending = true;
        } else if (lex.matchKeyword("desc")) {
            lex.eatKeyword("desc");
            ascending = false;
        }
        return new OrderByEntry(fldname, ascending);
    }

    private List<Constant> constList() {
        List<Constant> L = new ArrayList<>();
        L.add(constant());
        while (lex.matchDelim(',')) {
            lex.eatDelim(',');
            L.add(constant());
        }
        return L;
    }

    // =================================================================
    //  UPDATE (MODIFY)
    // =================================================================

    public UpdateNode modify() {
        lex.eatKeyword("update");
        String tblname = lex.eatId();
        lex.eatKeyword("set");
        String fldname = field();
        lex.eatDelim('=');
        Expression newval = expression();
        Predicate pred = new Predicate();
        if (lex.matchKeyword("where")) {
            lex.eatKeyword("where");
            pred = predicate();
        }
        consumeEnd();
        return new UpdateNode(tblname, fldname, newval, pred);
    }

    // =================================================================
    //  CREATE TABLE
    // =================================================================

    public CreateTableNode createTable() {
        lex.eatKeyword("table");
        String tblname = lex.eatId();
        lex.eatDelim('(');
        Schema sch = fieldDefs();
        lex.eatDelim(')');
        consumeEnd();
        return new CreateTableNode(tblname, sch);
    }

    public DropTableNode dropTable() {
        lex.eatKeyword("drop");
        lex.eatKeyword("table");
        boolean ifExists = lex.matchKeyword("if");
        if (ifExists) {
            lex.eatKeyword("if");
            lex.eatKeyword("exists");
        }
        String tblname = lex.eatId();
        consumeEnd();
        return new DropTableNode(tblname, ifExists);
    }

    private Schema fieldDefs() {
        Schema schema = fieldDef();
        while (lex.matchDelim(',')) {
            lex.eatDelim(',');
            schema.addAll(fieldDefs());
        }
        return schema;
    }

    private Schema fieldDef() {
        String fldname = field();
        return fieldType(fldname);
    }

    private Schema fieldType(String fldname) {
        Schema schema = new Schema();
        if (lex.matchKeyword("int")) {
            lex.eatKeyword("int");
            schema.addIntField(fldname);
        } else {
            lex.eatKeyword("varchar");
            lex.eatDelim('(');
            int strLen = lex.eatIntConstant();
            lex.eatDelim(')');
            schema.addStringField(fldname, strLen);
        }
        return schema;
    }

    // =================================================================
    //  CREATE VIEW / INDEX
    // =================================================================

    public CreateViewNode createView() {
        lex.eatKeyword("view");
        String viewname = lex.eatId();
        lex.eatKeyword("as");
        SelectNode qd = query();
        consumeEnd();
        return new CreateViewNode(viewname, qd);
    }

    public CreateIndexNode createIndex() {
        lex.eatKeyword("index");
        String idxname = lex.eatId();
        lex.eatKeyword("on");
        String tblname = lex.eatId();
        lex.eatDelim('(');
        String fldname = field();
        lex.eatDelim(')');
        consumeEnd();
        return new CreateIndexNode(idxname, tblname, fldname);
    }

    // =================================================================
    //  基础元素
    // =================================================================

    public String field() {
        return lex.eatId();
    }

    public Constant constant() {
        if (lex.matchStringConstant())
            return new Constant(lex.eatStringConstant());
        else
            return new Constant(lex.eatIntConstant());
    }
}
