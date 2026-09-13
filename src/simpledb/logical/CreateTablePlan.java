package simpledb.logical;

import java.util.*;
import simpledb.shared.ColumnDef;

/**
 * Logical CREATE TABLE command.
 */
public class CreateTablePlan extends LogicalPlan {
   private final String tableName;
   private final List<ColumnDef> columns;

   public CreateTablePlan(String tableName, List<ColumnDef> columns) {
      this.tableName = tableName;
      this.columns = new ArrayList<>(columns);
   }

   public String tableName() {
      return tableName;
   }

   public List<ColumnDef> columns() {
      return Collections.unmodifiableList(columns);
   }

   public List<ColumnDef> outputSchema() {
      return Collections.unmodifiableList(columns);
   }

   public String explain(int indent) {
      return pad(indent) + "CreateTable[" + tableName + " " + columns + "]";
   }
}
