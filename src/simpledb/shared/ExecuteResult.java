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

   private ResultType type;
   private List<String> columnNames;
   private List<List<Constant>> rows;
   private int affectedRows;

   private ExecuteResult(ResultType type, List<String> columnNames,
                         List<List<Constant>> rows, int affectedRows) {
      this.type = type;
      this.columnNames = columnNames;
      this.rows = rows;
      this.affectedRows = affectedRows;
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

   public List<String> getColumnNames() {
      return columnNames;
   }

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
