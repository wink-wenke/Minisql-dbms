package simpledb.logical;

import java.util.*;

/**
 * Logical projection over a child plan.
 */
public class ProjectPlan extends LogicalPlan {
   private final LogicalPlan child;
   private final List<String> columns;

   public ProjectPlan(LogicalPlan child, List<String> columns) {
      this.child = child;
      this.columns = new ArrayList<>(columns);
   }

   public LogicalPlan child() {
      return child;
   }

   public List<String> columns() {
      return Collections.unmodifiableList(columns);
   }

   public String explain(int indent) {
      return pad(indent) + "Project" + columns + System.lineSeparator()
           + child.explain(indent + 2);
   }
}
