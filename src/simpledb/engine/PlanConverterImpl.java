package simpledb.engine;

import java.util.*;
import simpledb.logical.*;
import simpledb.materialize.*;
import simpledb.metadata.MetadataMgr;
import simpledb.plan.*;
import simpledb.query.*;
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
         OrderByPlan order = (OrderByPlan) plan;
         return new SortPlan(tx, convert(order.child(), tx), order.sortFields());
      }
      if (plan instanceof JoinPlan) {
         JoinPlan join = (JoinPlan) plan;
         Plan left = convert(join.left(), tx);
         Plan right = convert(join.right(), tx);
         Plan product = new ProductPlan(left, right);
         if (join.predicate() != null)
            return new SelectPlan(product, join.predicate());
         return product;
      }
      if (plan instanceof simpledb.logical.GroupByPlan) {
         simpledb.logical.GroupByPlan gb = (simpledb.logical.GroupByPlan) plan;
         List<AggregationFn> fns = new ArrayList<>();
         for (AggregateSpec spec : gb.aggregates())
            fns.add(makeAggFn(spec));
         return new simpledb.materialize.GroupByPlan(tx, convert(gb.child(), tx),
               gb.groupFields(), fns);
      }
      throw EngineException.planConversion(plan.getClass().getSimpleName());
   }

   private AggregationFn makeAggFn(AggregateSpec spec) {
      switch (spec.func()) {
         case COUNT: return new CountFn(spec.column());
         case SUM:   return new SumFn(spec.column());
         case AVG:   return new AvgFn(spec.column());
         case MIN:   return new MinFn(spec.column());
         case MAX:   return new MaxFn(spec.column());
         default:    throw EngineException.planConversion("aggregate " + spec.func());
      }
   }
}
