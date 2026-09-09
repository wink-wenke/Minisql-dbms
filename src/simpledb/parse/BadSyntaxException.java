package simpledb.parse;

/**
 * 语法错误异常，携带错误消息、行号和列号。
 * <p>
 * 支持两种构造方式：
 * <ul>
 *   <li>无参：兼容旧代码（消息为通用提示）</li>
 *   <li>带消息 + 位置：用于新 Lexer/Parser 的精确错误报告</li>
 * </ul>
 *
 * @author Edward Sciore (原始), 增强版
 */
@SuppressWarnings("serial")
public class BadSyntaxException extends RuntimeException {
    private int line = -1;
    private int column = -1;

    /**
     * 无参构造（兼容旧代码）。
     */
    public BadSyntaxException() {
        super("语法错误");
    }

    /**
     * 带消息的构造。
     */
    public BadSyntaxException(String message) {
        super(message);
    }

    /**
     * 带消息和位置的构造。
     */
    public BadSyntaxException(String message, int line, int column) {
        super(message);
        this.line = line;
        this.column = column;
    }

    /**
     * 返回错误所在行号（从 1 开始），-1 表示未知。
     */
    public int getLine() {
        return line;
    }

    /**
     * 返回错误所在列号（从 1 开始），-1 表示未知。
     */
    public int getColumn() {
        return column;
    }

    /**
     * 返回格式化的错误信息，含位置。
     */
    @Override
    public String getMessage() {
        if (line > 0 && column > 0) {
            return String.format("[行 %d, 列 %d] %s", line, column, super.getMessage());
        }
        return super.getMessage();
    }
}
