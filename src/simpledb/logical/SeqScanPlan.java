package simpledb.logical;

import java.util.*;
import simpledb.shared.ColumnDef;

/**
 * Logical full table scan.
 *
 * A scan is the leaf of a query plan, so it is also the place where the
 * column definitions resolved by semantic analysis enter the plan tree.
 * Pass them in through the two-argument constructor to make
 * outputSchema() meaningful for the nodes above.
 */
public class SeqScanPlan extends LogicalPlan {
   private final String tableName;
   private final List<ColumnDef> columns;

   public SeqScanPlan(String tableName) {
      this(tableName, Collections.<ColumnDef>emptyList());
   }

   public SeqScanPlan(String tableName, List<ColumnDef> columns) {
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
      return pad(indent) + "SeqScan[" + tableName + "]";
   }
}
