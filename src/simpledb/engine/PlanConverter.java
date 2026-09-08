package simpledb.engine;

import simpledb.logical.LogicalPlan;
import simpledb.plan.Plan;
import simpledb.tx.Transaction;

/**
 * Converts module-level logical plans to existing SimpleDB physical plans.
 */
public interface PlanConverter {
   Plan convert(LogicalPlan plan, Transaction tx);
}
