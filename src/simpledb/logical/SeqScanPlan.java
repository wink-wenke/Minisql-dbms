package simpledb.logical;

/**
 * Logical full table scan.
 */
public class SeqScanPlan extends LogicalPlan {
   private final String tableName;

   public SeqScanPlan(String tableName) {
      this.tableName = tableName;
   }

   public String tableName() {
      return tableName;
   }

   public String explain(int indent) {
      return pad(indent) + "SeqScan[" + tableName + "]";
   }
}
