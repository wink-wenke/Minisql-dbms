package simpledb.shared;

import java.util.*;
import simpledb.query.Constant;

/**
 * Unified result returned by the DB engine.
 */
public class ExecuteResult {
   public enum ResultType {
      QUERY,
      UPDATE
   }

   private final ResultType type;
   private final List<String> columnNames;
   private final List<List<Constant>> rows;
   private final int affectedRows;

   private ExecuteResult(ResultType type, List<String> columnNames,
                         List<List<Constant>> rows, int affectedRows) {
      this.type = type;
      this.columnNames = Collections.unmodifiableList(new ArrayList<>(columnNames));
      this.rows = deepCopy(rows);
      this.affectedRows = affectedRows;
   }

   /**
    * Copies the row list and every row in it, so a caller cannot reach back
    * into the engine's result and change what the next reader sees.
    */
   private static List<List<Constant>> deepCopy(List<List<Constant>> rows) {
      List<List<Constant>> copy = new ArrayList<>();
      for (List<Constant> row : rows)
         copy.add(Collections.unmodifiableList(new ArrayList<>(row)));
      return Collections.unmodifiableList(copy);
   }

   public static ExecuteResult queryResult(List<String> columns, List<List<Constant>> rows) {
      return new ExecuteResult(ResultType.QUERY, columns, rows, 0);
   }

   public static ExecuteResult updateResult(int affectedRows) {
      return new ExecuteResult(ResultType.UPDATE, Collections.emptyList(),
                               Collections.emptyList(), affectedRows);
   }

   public ResultType getType() {
      return type;
   }

   /**
    * Column names of a query result, or an empty list for an update result.
    * The returned list is an unmodifiable view.
    */
   public List<String> getColumnNames() {
      return columnNames;
   }

   /**
    * Rows of a query result. The returned list and every row inside it are
    * unmodifiable views.
    */
   public List<List<Constant>> getRows() {
      return rows;
   }

   public int getAffectedRows() {
      return affectedRows;
   }

   public String formatted() {
      if (type == ResultType.UPDATE)
         return affectedRows + " records processed";

      StringBuilder sb = new StringBuilder();
      for (String col : columnNames)
         sb.append(String.format("%15s", col));
      sb.append(System.lineSeparator());
      for (int i = 0; i < columnNames.size() * 15; i++)
         sb.append("-");
      sb.append(System.lineSeparator());
      for (List<Constant> row : rows) {
         for (Constant val : row)
            sb.append(String.format("%15s", val));
         sb.append(System.lineSeparator());
      }
      return sb.toString();
   }
}
