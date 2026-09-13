package simpledb.engine;

import java.util.*;
import simpledb.logical.*;
import simpledb.materialize.*;
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
         if (!metadataMgr.tableExists(scan.tableName(), tx))
            throw EngineException.tableNotFound(scan.tableName());
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
      if (plan instanceof OrderByPlan) {
         OrderByPlan ob = (OrderByPlan) plan;
         return new SortPlan(tx, convert(ob.child(), tx), ob.sortFields());
      }
      if (plan instanceof JoinPlan) {
         JoinPlan jp = (JoinPlan) plan;
         Plan product = new ProductPlan(convert(jp.left(), tx), convert(jp.right(), tx));
         if (jp.predicate() != null)
            product = new SelectPlan(product, jp.predicate());
         return product;
      }
      if (plan instanceof simpledb.logical.GroupByPlan) {
         simpledb.logical.GroupByPlan gb = (simpledb.logical.GroupByPlan) plan;
         Plan child = convert(gb.child(), tx);
         List<AggregationFn> fns = new ArrayList<>();
         for (AggregateSpec spec : gb.aggregates())
            fns.add(makeAggFn(spec));
         return new simpledb.materialize.GroupByPlan(tx, child, gb.groupFields(), fns);
      }
      throw EngineException.planConversion(plan.getClass().getSimpleName());
   }

   /**
    * Maps a logical aggregate request to the matching physical aggregation
    * function. The set mirrors what materialize provides; SUM/AVG/MIN were
    * added alongside this engine work because SimpleDB 3.4 ships only
    * CountFn and MaxFn.
    */
   private static AggregationFn makeAggFn(AggregateSpec spec) {
      switch (spec.func()) {
         case COUNT: return new CountFn(spec.column());
         case SUM:   return new SumFn(spec.column());
         case AVG:   return new AvgFn(spec.column());
         case MIN:   return new MinFn(spec.column());
         case MAX:   return new MaxFn(spec.column());
         default:
            throw EngineException.planConversion("unknown aggregate " + spec.func());
      }
   }
}
