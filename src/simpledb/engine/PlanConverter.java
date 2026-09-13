package simpledb.engine;

import simpledb.logical.LogicalPlan;
import simpledb.plan.Plan;
import simpledb.tx.Transaction;

/**
 * Converts module-level logical plans to existing SimpleDB physical plans.
 *
 * Only row-producing plans can be converted. SeqScan, Filter and Project
 * have a physical counterpart; CreateTable, Insert and Delete do not, and
 * are rejected with EngineException[PLAN_CONVERSION]. The executor routes
 * those three to the storage engine before conversion is ever attempted, so
 * reaching that error means a statement was misrouted.
 */
public interface PlanConverter {
   Plan convert(LogicalPlan plan, Transaction tx);
}
