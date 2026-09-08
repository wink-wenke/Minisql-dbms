package simpledb.engine;

import simpledb.logical.*;
import simpledb.metadata.MetadataMgr;
import simpledb.plan.*;
import simpledb.tx.Transaction;

/**
 * Adapter from the new LogicalPlan contract to the existing Plan/Scan engine.
 */
public class PlanConverterImpl implements PlanConverter {
   private MetadataMgr metadataMgr;

   public PlanConverterImpl(MetadataMgr metadataMgr) {
      this.metadataMgr = metadataMgr;
   }

   public Plan convert(LogicalPlan plan, Transaction tx) {
      if (plan instanceof SeqScanPlan) {
         SeqScanPlan scan = (SeqScanPlan) plan;
         return new TablePlan(tx, scan.tableName(), metadataMgr);
      }
      if (plan instanceof FilterPlan) {
         FilterPlan filter = (FilterPlan) plan;
         return new SelectPlan(convert(filter.child(), tx), filter.predicate());
      }
      if (plan instanceof simpledb.logical.ProjectPlan) {
         simpledb.logical.ProjectPlan project = (simpledb.logical.ProjectPlan) plan;
         return new simpledb.plan.ProjectPlan(convert(project.child(), tx), project.columns());
      }
      throw new IllegalArgumentException("logical plan cannot be converted to query plan: "
            + plan.getClass().getSimpleName());
   }
}
