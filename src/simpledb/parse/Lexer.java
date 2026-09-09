package simpledb.parse;

import java.util.*;

/**
 * 词法分析器（Lexer）。
 * <p>
 * 功能：
 * <ul>
 *   <li>逐字符扫描 SQL 源文本，生成 Token 流</li>
 *   <li>支持关键字（大小写不敏感）、标识符、整数常量、字符串常量（含转义）</li>
 *   <li>支持比较运算符 = != &lt; &lt;= &gt; &gt;=，算术运算符 + - * /</li>
 *   <li>支持分隔符 ( ) , ;</li>
 *   <li>支持单行注释 -- 和多行注释 /* ... *​/</li>
 *   <li>每个 Token 记录行号和列号，便于错误定位</li>
 *   <li>提供新 API（Token-based）和旧 API（matchXxx / eatXxx）双接口</li>
 * </ul>
 *
 * @author Edward Sciore (原始), 增强版
 */
public class Lexer {
    // ==================== 关键字集合 ====================
    private static final Set<String> KEYWORDS = new HashSet<>(Arrays.asList(
            "select", "from", "where", "and", "or", "not",
            "insert", "into", "values",
            "delete", "update", "set",
            "create", "table", "view", "as", "index", "on",
            "int", "varchar", "null"
    ));

    // 关键字字符串 -> TokenType 的映射
    private static final Map<String, TokenType> KEYWORD_MAP = new HashMap<>();
    static {
        KEYWORD_MAP.put("select",   TokenType.SELECT);
        KEYWORD_MAP.put("from",     TokenType.FROM);
        KEYWORD_MAP.put("where",    TokenType.WHERE);
        KEYWORD_MAP.put("and",      TokenType.AND);
        KEYWORD_MAP.put("or",       TokenType.OR);
        KEYWORD_MAP.put("not",      TokenType.NOT);
        KEYWORD_MAP.put("insert",   TokenType.INSERT);
        KEYWORD_MAP.put("into",     TokenType.INTO);
        KEYWORD_MAP.put("values",   TokenType.VALUES);
        KEYWORD_MAP.put("delete",   TokenType.DELETE);
        KEYWORD_MAP.put("update",   TokenType.UPDATE);
        KEYWORD_MAP.put("set",      TokenType.SET);
        KEYWORD_MAP.put("create",   TokenType.CREATE);
        KEYWORD_MAP.put("table",    TokenType.TABLE);
        KEYWORD_MAP.put("view",     TokenType.VIEW);
        KEYWORD_MAP.put("as",       TokenType.AS);
        KEYWORD_MAP.put("index",    TokenType.INDEX);
        KEYWORD_MAP.put("on",       TokenType.ON);
        KEYWORD_MAP.put("int",      TokenType.INT);
        KEYWORD_MAP.put("varchar",  TokenType.VARCHAR);
        KEYWORD_MAP.put("null",     TokenType.NULL);
    }

    // ==================== 源文本与扫描状态 ====================
    private final String source;
    private int pos;      // 当前字符在 source 中的下标
    private int line;     // 当前行号（从 1 开始）
    private int column;   // 当前列号（从 1 开始）

    // ==================== 当前 Token（旧 API 兼容） ====================
    private Token currentToken;

    // ==================== 构造 ====================

    /**
     * 创建词法分析器，对 SQL 语句 s 进行扫描。
     * @param s SQL 源文本
     */
    public Lexer(String s) {
        this.source = s;
        this.pos = 0;
        this.line = 1;
        this.column = 1;
        nextToken();
    }

    // =================================================================
    //  新 API：基于 Token 的接口
    // =================================================================

    /**
     * 返回当前 Token（不消费）。
     */
    public Token peek() {
        return currentToken;
    }

    /**
     * 消费并返回当前 Token，然后前进到下一个 Token。
     */
    public Token next() {
        Token t = currentToken;
        nextToken();
        return t;
    }

    /**
     * 如果当前 Token 的类型匹配，则消费并返回 true；否则返回 false。
     */
    public boolean match(TokenType type) {
        return currentToken.type() == type;
    }

    /**
     * 消费当前 Token，要求其类型为指定类型，否则抛出语法错误。
     */
    public Token eat(TokenType type) {
        if (currentToken.type() != type) {
            throw syntaxError("期望 " + type + "，但遇到 '" + currentToken.lexeme()
                    + "' (" + currentToken.type() + ")");
        }
        return next();
    }

    // =================================================================
    //  旧 API 兼容：matchXxx / eatXxx（保持现有 Parser 不被破坏）
    // =================================================================

    /**
     * 当前 Token 是否为指定的分隔符字符。
     */
    public boolean matchDelim(char d) {
        return currentToken.lexeme().length() == 1
                && currentToken.lexeme().charAt(0) == d;
    }

    /**
     * 消费分隔符，否则抛出异常。
     */
    public void eatDelim(char d) {
        if (!matchDelim(d))
            throw syntaxError("期望 '" + d + "'，但遇到 '" + currentToken.lexeme() + "'");
        nextToken();
    }

    /**
     * 当前 Token 是否为整数常量。
     */
    public boolean matchIntConstant() {
        return currentToken.type() == TokenType.INT_CONST;
    }

    /**
     * 消费整数常量并返回其值。
     */
    public int eatIntConstant() {
        if (!matchIntConstant())
            throw syntaxError("期望整数常量，但遇到 '" + currentToken.lexeme() + "'");
        int val = currentToken.intValue();
        nextToken();
        return val;
    }

    /**
     * 当前 Token 是否为字符串常量。
     */
    public boolean matchStringConstant() {
        return currentToken.type() == TokenType.STRING_CONST;
    }

    /**
     * 消费字符串常量并返回其内容（已去除引号）。
     */
    public String eatStringConstant() {
        if (!matchStringConstant())
            throw syntaxError("期望字符串常量，但遇到 '" + currentToken.lexeme() + "'");
        String val = currentToken.stringValue();
        nextToken();
        return val;
    }

    /**
     * 当前 Token 是否为指定关键字（忽略大小写）。
     */
    public boolean matchKeyword(String w) {
        return currentToken.lexeme().equalsIgnoreCase(w)
                && KEYWORDS.contains(w.toLowerCase());
    }

    /**
     * 消费关键字，否则抛出异常。
     */
    public void eatKeyword(String w) {
        if (!matchKeyword(w))
            throw syntaxError("期望关键字 '" + w + "'，但遇到 '" + currentToken.lexeme() + "'");
        nextToken();
    }

    /**
     * 当前 Token 是否为标识符（非关键字）。
     */
    public boolean matchId() {
        return currentToken.type() == TokenType.IDENTIFIER;
    }

    /**
     * 消费标识符并返回其名称。
     */
    public String eatId() {
        if (!matchId())
            throw syntaxError("期望标识符，但遇到 '" + currentToken.lexeme() + "' (" + currentToken.type() + ")");
        String s = currentToken.lexeme();
        nextToken();
        return s;
    }

    // =================================================================
    //  错误报告辅助
    // =================================================================

    /**
     * 创建带位置信息的语法错误异常。
     */
    public BadSyntaxException syntaxError(String message) {
        return new BadSyntaxException(message, line, column);
    }

    // =================================================================
    //  内部扫描逻辑
    // =================================================================

    /**
     * 推进到下一个 Token，更新 currentToken。
     */
    private void nextToken() {
        skipWhitespaceAndComments();
        if (pos >= source.length()) {
            currentToken = new Token(TokenType.EOF, "<EOF>", line, column);
            return;
        }

        int startLine = line;
        int startCol = column;
        char ch = source.charAt(pos);

        // ---- 数字 ----
        if (Character.isDigit(ch)) {
            currentToken = scanNumber(startLine, startCol);
            return;
        }

        // ---- 字符串（单引号） ----
        if (ch == '\'') {
            currentToken = scanString(startLine, startCol);
            return;
        }

        // ---- 标识符 / 关键字 ----
        if (Character.isLetter(ch) || ch == '_') {
            currentToken = scanIdentifier(startLine, startCol);
            return;
        }

        // ---- 运算符 / 分隔符 ----
        currentToken = scanSymbol(startLine, startCol);
    }

    /**
     * 跳过空白字符和注释。
     */
    private void skipWhitespaceAndComments() {
        while (pos < source.length()) {
            char ch = source.charAt(pos);

            // 空白
            if (ch == ' ' || ch == '\t' || ch == '\r') {
                advance();
                continue;
            }
            // 换行
            if (ch == '\n') {
                advanceNewline();
                continue;
            }

            // 单行注释 --
            if (ch == '-' && pos + 1 < source.length() && source.charAt(pos + 1) == '-') {
                advance(); // -
                advance(); // -
                while (pos < source.length() && source.charAt(pos) != '\n') {
                    advance();
                }
                continue;
            }

            // 多行注释 /* ... */
            if (ch == '/' && pos + 1 < source.length() && source.charAt(pos + 1) == '*') {
                advance(); // /
                advance(); // *
                while (pos + 1 < source.length()) {
                    if (source.charAt(pos) == '*' && source.charAt(pos + 1) == '/') {
                        advance(); // *
                        advance(); // /
                        break;
                    }
                    if (source.charAt(pos) == '\n') {
                        advanceNewline();
                    } else {
                        advance();
                    }
                }
                continue;
            }

            break;
        }
    }

    /**
     * 扫描整数常量。
     */
    private Token scanNumber(int startLine, int startCol) {
        int start = pos;
        while (pos < source.length() && Character.isDigit(source.charAt(pos))) {
            advance();
        }
        String lexeme = source.substring(start, pos);
        return new Token(TokenType.INT_CONST, lexeme, startLine, startCol);
    }

    /**
     * 扫描字符串常量（支持 '' 转义单引号）。
     * 返回的 lexeme 包含首尾引号。
     */
    private Token scanString(int startLine, int startCol) {
        advance(); // 跳过开头的 '
        StringBuilder sb = new StringBuilder();
        sb.append('\'');
        while (pos < source.length()) {
            char ch = source.charAt(pos);
            if (ch == '\'') {
                // 检查是否是转义 ''
                if (pos + 1 < source.length() && source.charAt(pos + 1) == '\'') {
                    sb.append("''");
                    advance();
                    advance();
                } else {
                    sb.append('\'');
                    advance(); // 跳过结尾的 '
                    return new Token(TokenType.STRING_CONST, sb.toString(), startLine, startCol);
                }
            } else if (ch == '\n') {
                // 字符串中不允许未关闭的换行
                throw syntaxErrorAt("未闭合的字符串常量（遇到换行）", startLine, startCol);
            } else {
                sb.append(ch);
                advance();
            }
        }
        throw syntaxErrorAt("未闭合的字符串常量（遇到文件结束）", startLine, startCol);
    }

    /**
     * 扫描标识符或关键字。
     */
    private Token scanIdentifier(int startLine, int startCol) {
        int start = pos;
        while (pos < source.length()) {
            char ch = source.charAt(pos);
            if (Character.isLetterOrDigit(ch) || ch == '_') {
                advance();
            } else {
                break;
            }
        }
        String lexeme = source.substring(start, pos);
        String lower = lexeme.toLowerCase();

        TokenType type = KEYWORD_MAP.getOrDefault(lower, TokenType.IDENTIFIER);
        return new Token(type, lexeme, startLine, startCol);
    }

    /**
     * 扫描运算符和分隔符。
     */
    private Token scanSymbol(int startLine, int startCol) {
        char ch = source.charAt(pos);

        switch (ch) {
            case '(':
                advance();
                return new Token(TokenType.LPAREN, "(", startLine, startCol);
            case ')':
                advance();
                return new Token(TokenType.RPAREN, ")", startLine, startCol);
            case ',':
                advance();
                return new Token(TokenType.COMMA, ",", startLine, startCol);
            case ';':
                advance();
                return new Token(TokenType.SEMICOLON, ";", startLine, startCol);
            case '+':
                advance();
                return new Token(TokenType.PLUS, "+", startLine, startCol);
            case '-':
                advance();
                return new Token(TokenType.MINUS, "-", startLine, startCol);
            case '*':
                advance();
                return new Token(TokenType.STAR, "*", startLine, startCol);
            case '/':
                advance();
                return new Token(TokenType.DIVIDE, "/", startLine, startCol);
            case '=':
                advance();
                return new Token(TokenType.EQUALS, "=", startLine, startCol);
            case '!':
                if (pos + 1 < source.length() && source.charAt(pos + 1) == '=') {
                    advance();
                    advance();
                    return new Token(TokenType.NOT_EQUALS, "!=", startLine, startCol);
                }
                advance();
                return new Token(TokenType.ERROR, "!", startLine, startCol);
            case '<':
                if (pos + 1 < source.length() && source.charAt(pos + 1) == '=') {
                    advance();
                    advance();
                    return new Token(TokenType.LESS_EQUALS, "<=", startLine, startCol);
                }
                advance();
                return new Token(TokenType.LESS, "<", startLine, startCol);
            case '>':
                if (pos + 1 < source.length() && source.charAt(pos + 1) == '=') {
                    advance();
                    advance();
                    return new Token(TokenType.GREATER_EQUALS, ">=", startLine, startCol);
                }
                advance();
                return new Token(TokenType.GREATER, ">", startLine, startCol);
            default:
                advance();
                return new Token(TokenType.ERROR, String.valueOf(ch), startLine, startCol);
        }
    }

    /**
     * 推进一个字符（非换行）。
     */
    private void advance() {
        pos++;
        column++;
    }

    /**
     * 推进一个换行符，行号 +1，列号归 1。
     */
    private void advanceNewline() {
        pos++;
        line++;
        column = 1;
    }

    /**
     * 在指定位置创建语法错误。
     */
    private BadSyntaxException syntaxErrorAt(String message, int errLine, int errCol) {
        return new BadSyntaxException(message, errLine, errCol);
    }

    // =================================================================
    //  调试辅助：列出所有 Token
    // =================================================================

    /**
     * 对给定的 SQL 文本做完整词法分析，返回 Token 列表（含 EOF）。
     * 静态方法，不修改当前 Lexer 实例状态。
     */
    public static List<Token> tokenize(String sql) {
        List<Token> tokens = new ArrayList<>();
        Lexer lexer = new Lexer(sql);
        while (lexer.peek().type() != TokenType.EOF) {
            if (lexer.peek().type() == TokenType.ERROR) {
                throw new BadSyntaxException(
                        "非法字符 '" + lexer.peek().lexeme() + "'",
                        lexer.peek().line(), lexer.peek().column());
            }
            tokens.add(lexer.next());
        }
        tokens.add(lexer.peek()); // 加入 EOF
        return tokens;
    }
}
