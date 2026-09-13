package simpledb.logical;

import java.util.*;
import simpledb.shared.ColumnDef;

/**
 * Logical projection over a child plan.
 *
 * The output schema is the child schema narrowed down to the projected
 * columns. Identifiers are matched case-insensitively, matching the way the
 * lexer folds SQL keywords and names. A "*" entry means "every column of
 * the child".
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

   public List<ColumnDef> outputSchema() {
      List<ColumnDef> source = child.outputSchema();
      if (source.isEmpty())
         return Collections.emptyList();
      if (columns.contains("*"))
         return Collections.unmodifiableList(source);

      List<ColumnDef> result = new ArrayList<>();
      for (String name : columns) {
         for (ColumnDef def : source) {
            if (def.name().equalsIgnoreCase(name)) {
               result.add(def);
               break;
            }
         }
      }
      return result;
   }

   public String explain(int indent) {
      return pad(indent) + "Project" + columns + System.lineSeparator()
           + child.explain(indent + 2);
   }
}
