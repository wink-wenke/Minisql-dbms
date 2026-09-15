package simpledb.ast;

/**
 * ORDER BY 中的单个排序项：字段名 + 排序方向。
 */
public class OrderByEntry {
    private final String field;
    private final boolean ascending;

    public OrderByEntry(String field, boolean ascending) {
        this.field = field;
        this.ascending = ascending;
    }

    public String field() { return field; }
    public boolean isAscending() { return ascending; }

    @Override
    public String toString() {
        return field + (ascending ? " ASC" : " DESC");
    }
}
