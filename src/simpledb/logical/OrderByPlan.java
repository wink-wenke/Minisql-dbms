package simpledb.logical;

import java.util.*;
import simpledb.shared.ColumnDef;

/**
 * Logical ORDER BY over a child plan.
 *
 * Sorting does not change the row shape, so the output schema is the
 * child's. The physical side is materialize.SortPlan, which spills the
 * input into a temporary table and runs a sort pass over it.
 */
public class OrderByPlan extends LogicalPlan {
   private final LogicalPlan child;
   private final List<String> sortFields;

   public OrderByPlan(LogicalPlan child, List<String> sortFields) {
      this.child = child;
      this.sortFields = new ArrayList<>(sortFields);
   }

   public LogicalPlan child() {
      return child;
   }

   public List<String> sortFields() {
      return Collections.unmodifiableList(sortFields);
   }

   public List<ColumnDef> outputSchema() {
      return child.outputSchema();
   }

   public String explain(int indent) {
      return pad(indent) + "OrderBy" + sortFields + System.lineSeparator()
           + child.explain(indent + 2);
   }
}
