package simpledb.logical;

import java.util.*;
import simpledb.query.Predicate;
import simpledb.shared.ColumnDef;

/**
 * Logical join of two child plans.
 *
 * The output schema is the concatenation of the two children's schemas, so a
 * join that reuses a column name on both sides (a foreign key, for example)
 * will surface that name twice. Callers therefore join on columns whose names
 * differ between the two tables (e.g. student.majorid = department.deptid),
 * and typically wrap the result in a ProjectPlan to keep the downstream
 * schema unambiguous.
 *
 * The physical side is plan.ProductPlan, optionally wrapped in a
 * plan.SelectPlan when a join predicate is supplied.
 */
public class JoinPlan extends LogicalPlan {
   private final LogicalPlan left;
   private final LogicalPlan right;
   private final Predicate predicate;

   public JoinPlan(LogicalPlan left, LogicalPlan right, Predicate predicate) {
      this.left = left;
      this.right = right;
      this.predicate = predicate;
   }

   public LogicalPlan left() {
      return left;
   }

   public LogicalPlan right() {
      return right;
   }

   /** Null means a cross product with no filtering. */
   public Predicate predicate() {
      return predicate;
   }

   public List<ColumnDef> outputSchema() {
      List<ColumnDef> result = new ArrayList<>(left.outputSchema());
      result.addAll(right.outputSchema());
      return result;
   }

   public String explain(int indent) {
      return pad(indent) + "Join" + (predicate == null ? "" : "[" + predicate + "]")
           + System.lineSeparator()
           + left.explain(indent + 2)
           + right.explain(indent + 2);
   }
}
