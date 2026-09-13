package simpledb.engine;

import simpledb.logical.LogicalPlan;
import simpledb.shared.ExecuteResult;
import simpledb.tx.Transaction;

/**
 * DB engine entry point. It consumes compiler logical plans.
 */
public interface Executor {
   ExecuteResult execute(LogicalPlan plan, Transaction tx);
}
