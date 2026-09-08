package simpledb.logical;

import java.util.*;
import simpledb.query.Constant;

/**
 * Logical INSERT command.
 */
public class InsertPlan extends LogicalPlan {
   private final String tableName;
   private final List<String> columns;
   private final List<Constant> values;

   public InsertPlan(String tableName, List<String> columns, List<Constant> values) {
      this.tableName = tableName;
      this.columns = new ArrayList<>(columns);
      this.values = new ArrayList<>(values);
   }

   public String tableName() {
      return tableName;
   }

   public List<String> columns() {
      return Collections.unmodifiableList(columns);
   }

   public List<Constant> values() {
      return Collections.unmodifiableList(values);
   }

   public String explain(int indent) {
      return pad(indent) + "Insert[" + tableName + " columns=" + columns
           + " values=" + values + "]";
   }
}
