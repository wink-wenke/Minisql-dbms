package simpledb.logical;

import java.util.*;
import simpledb.shared.ColumnDef;

/**
 * Pure logical plan consumed by the DB engine.
 */
public abstract class LogicalPlan {
   /**
    * Column definitions this node produces.
    *
    * Returns an empty list when the plan has not been bound to a catalog yet.
    * Binding flows bottom-up: a SeqScanPlan carries the definitions resolved
    * by semantic analysis, and every node above derives its own schema from
    * its child. Statements that produce no rows (Insert, Delete) stay empty.
    */
   public List<ColumnDef> outputSchema() {
      return Collections.emptyList();
   }

   public abstract String explain(int indent);

   protected String pad(int indent) {
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < indent; i++)
         sb.append(' ');
      return sb.toString();
   }
}
