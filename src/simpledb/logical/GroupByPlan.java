package simpledb.logical;

import java.util.*;
import simpledb.shared.ColumnDef;
import simpledb.shared.ColumnType;

/**
 * Logical GROUP BY over a child plan.
 *
 * Groups rows by the listed columns and computes one or more aggregate
 * functions per group. The output schema is the group columns followed by one
 * column per aggregate, named exactly as the physical aggregation functions
 * produce them ("countofX", "sumofX", ...).
 *
 * The physical side is materialize.GroupByPlan, which internally sorts by the
 * group columns and streams one row per group through GroupByScan.
 */
public class GroupByPlan extends LogicalPlan {
   private final LogicalPlan child;
   private final List<String> groupFields;
   private final List<AggregateSpec> aggregates;

   public GroupByPlan(LogicalPlan child, List<String> groupFields,
                      List<AggregateSpec> aggregates) {
      this.child = child;
      this.groupFields = new ArrayList<>(groupFields);
      this.aggregates = new ArrayList<>(aggregates);
   }

   public LogicalPlan child() {
      return child;
   }

   public List<String> groupFields() {
      return Collections.unmodifiableList(groupFields);
   }

   public List<AggregateSpec> aggregates() {
      return Collections.unmodifiableList(aggregates);
   }

   public List<ColumnDef> outputSchema() {
      List<ColumnDef> source = child.outputSchema();
      if (source.isEmpty())
         return Collections.emptyList();

      List<ColumnDef> result = new ArrayList<>();
      for (String gf : groupFields)
         for (ColumnDef def : source)
            if (def.name().equalsIgnoreCase(gf)) {
               result.add(def);
               break;
            }

      for (AggregateSpec spec : aggregates) {
         ColumnType type = ColumnType.INTEGER;
         if (spec.func() != AggregateSpec.Func.COUNT)
            for (ColumnDef def : source)
               if (def.name().equalsIgnoreCase(spec.column())) {
                  type = def.type();
                  break;
               }
         result.add(new ColumnDef(spec.resultFieldName(), type, 0));
      }
      return result;
   }

   public String explain(int indent) {
      StringBuilder sb = new StringBuilder();
      sb.append(pad(indent)).append("GroupBy").append(groupFields)
        .append(System.lineSeparator());
      for (AggregateSpec spec : aggregates)
         sb.append(pad(indent + 2)).append(spec.explain())
           .append(System.lineSeparator());
      sb.append(child.explain(indent + 2));
      return sb.toString();
   }
}
