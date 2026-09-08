package simpledb.logical;

import java.util.*;
import simpledb.shared.ColumnDef;

/**
 * Pure logical plan consumed by the DB engine.
 */
public abstract class LogicalPlan {
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
