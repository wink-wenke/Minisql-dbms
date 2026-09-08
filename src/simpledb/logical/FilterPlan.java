package simpledb.logical;

import simpledb.query.Predicate;

/**
 * Logical filter over a child plan.
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

   public String explain(int indent) {
      return pad(indent) + "Filter[" + predicate + "]" + System.lineSeparator()
           + child.explain(indent + 2);
   }
}
