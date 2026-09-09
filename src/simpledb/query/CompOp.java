package simpledb.query;

/**
 * 比较运算符枚举。
 * 支持 =, !=, <, <=, >, >= 六种比较。
 */
public enum CompOp {
    EQUALS("="),
    NOT_EQUALS("!="),
    LESS("<"),
    LESS_EQUALS("<="),
    GREATER(">"),
    GREATER_EQUALS(">=");

    private final String symbol;

    CompOp(String symbol) {
        this.symbol = symbol;
    }

    public String symbol() {
        return symbol;
    }

    @Override
    public String toString() {
        return symbol;
    }
}
