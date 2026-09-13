package simpledb.logical;

import simpledb.query.Predicate;

/**
 * Logical DELETE command.
 */
public class DeletePlan extends LogicalPlan {
   private final String tableName;
   private final Predicate predicate;

   public DeletePlan(String tableName, Predicate predicate) {
      this.tableName = tableName;
      this.predicate = predicate;
   }

   public String tableName() {
      return tableName;
   }

   public Predicate predicate() {
      return predicate;
   }

   public String explain(int indent) {
      return pad(indent) + "Delete[" + tableName + " where " + predicate + "]";
   }
}
