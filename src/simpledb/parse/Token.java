package simpledb.parse;

/**
 * 词法单元 Token。
 * 每个 Token 记录：类型、原始文本（lexeme）、行号、列号。
 */
public class Token {
    private final TokenType type;
    private final String lexeme;
    private final int line;
    private final int column;

    public Token(TokenType type, String lexeme, int line, int column) {
        this.type = type;
        this.lexeme = lexeme;
        this.line = line;
        this.column = column;
    }

    public TokenType type() {
        return type;
    }

    public String lexeme() {
        return lexeme;
    }

    public int line() {
        return line;
    }

    public int column() {
        return column;
    }

    /**
     * 返回 Token 的整数值（仅对 INT_CONST 有意义）。
     */
    public int intValue() {
        return Integer.parseInt(lexeme);
    }

    /**
     * 返回 Token 的字符串值（仅对 STRING_CONST 有意义）。
     * 去除首尾单引号，并将内部的 '' 转义还原为 '。
     */
    public String stringValue() {
        if (lexeme.length() >= 2 && lexeme.startsWith("'") && lexeme.endsWith("'")) {
            // 去掉首尾引号
            String inner = lexeme.substring(1, lexeme.length() - 1);
            // 将 '' 转义还原为单个 '
            return inner.replace("''", "'");
        }
        return lexeme;
    }

    @Override
    public String toString() {
        return String.format("<%s, '%s', line=%d, col=%d>", type, lexeme, line, column);
    }
}
