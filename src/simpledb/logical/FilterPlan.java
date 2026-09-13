package simpledb.logical;

import java.util.List;
import simpledb.query.Predicate;
import simpledb.shared.ColumnDef;

/**
 * Logical filter over a child plan.
 *
 * Filtering never changes the row shape, so the output schema is whatever
 * the child produces.
 */
public class FilterPlan extends LogicalPlan {
   private final LogicalPlan child;
   private final Predicate predicate;

   public FilterPlan(LogicalPlan child, Predicate predicate) {
      this.child = child;
      this.predicate = predicate;
   }

   public LogicalPlan child() {
      return child;
   }

   public Predicate predicate() {
      return predicate;
   }

   public List<ColumnDef> outputSchema() {
      return child.outputSchema();
   }

   public String explain(int indent) {
      return pad(indent) + "Filter[" + predicate + "]" + System.lineSeparator()
           + child.explain(indent + 2);
   }
}
