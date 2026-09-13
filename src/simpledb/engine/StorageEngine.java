package simpledb.engine;

import simpledb.query.*;
import simpledb.record.RID;
import simpledb.tx.Transaction;

/**
 * Table-level storage API used by the execution engine.
 */
public interface StorageEngine {
   Scan scan(String tableName, Transaction tx);

   RID insertRow(String tableName, String[] columns, Constant[] values, Transaction tx);

   int updateRows(String tableName, Predicate predicate, String[] columns,
                  Constant[] values, Transaction tx);

   int deleteRows(String tableName, Predicate predicate, Transaction tx);

   int getRecordCount(String tableName, Transaction tx);
}
