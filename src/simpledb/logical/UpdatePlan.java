package simpledb.logical;

import java.util.*;
import simpledb.query.*;

/**
 * Logical UPDATE command.
 *
 * Assigns new values to the listed columns of every row matching the
 * predicate. When the predicate is null the update applies to every row.
 */
public class UpdatePlan extends LogicalPlan {
   private final String tableName;
   private final List<String> columns;
   private final List<Constant> values;
   private final Predicate predicate;

   public UpdatePlan(String tableName, List<String> columns, List<Constant> values,
                     Predicate predicate) {
      if (columns.size() != values.size())
         throw new IllegalArgumentException("columns and values must have the same size");
      this.tableName = tableName;
      this.columns = new ArrayList<>(columns);
      this.values = new ArrayList<>(values);
      this.predicate = predicate;
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

   /** Null means "every row". */
   public Predicate predicate() {
      return predicate;
   }

   public String explain(int indent) {
      return pad(indent) + "Update[" + tableName
         + " set " + columns + " = " + values
         + (predicate == null ? "" : " where " + predicate) + "]";
   }
}
