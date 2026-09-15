package simpledb.logical;

import simpledb.query.*;

/**
 * Logical UPDATE command.
 *
 * Sets {@code targetField = newValue} for every row matching the predicate.
 */
public class UpdatePlan extends LogicalPlan {
   private final String tableName;
   private final String targetField;
   private final Expression newValue;
   private final Predicate predicate;

   public UpdatePlan(String tableName, String targetField,
                     Expression newValue, Predicate predicate) {
      this.tableName = tableName;
      this.targetField = targetField;
      this.newValue = newValue;
      this.predicate = predicate;
   }

   public String tableName() {
      return tableName;
   }

   public String targetField() {
      return targetField;
   }

   public Expression newValue() {
      return newValue;
   }

   public Predicate predicate() {
      return predicate;
   }

   public String explain(int indent) {
      return pad(indent) + "Update[" + tableName + " set " + targetField
           + " = " + newValue + " where " + predicate + "]";
   }
}
