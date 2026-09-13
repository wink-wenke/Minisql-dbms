package simpledb.engine;

import simpledb.metadata.MetadataMgr;
import simpledb.plan.*;
import simpledb.query.*;
import simpledb.record.*;
import simpledb.tx.Transaction;

/**
 * SimpleDB-backed storage engine facade.
 */
public class StorageEngineImpl implements StorageEngine {
   private MetadataMgr metadataMgr;

   public StorageEngineImpl(MetadataMgr metadataMgr) {
      this.metadataMgr = metadataMgr;
   }

   public Scan scan(String tableName, Transaction tx) {
      requireTable(tableName, tx);
      Layout layout = metadataMgr.getLayout(tableName, tx);
      return new TableScan(tx, tableName, layout);
   }

   public RID insertRow(String tableName, String[] columns, Constant[] values, Transaction tx) {
      if (columns.length != values.length)
         throw EngineException.arityMismatch(tableName, columns.length, values.length);
      requireTable(tableName, tx);

      Layout layout = metadataMgr.getLayout(tableName, tx);
      UpdateScan us = new TableScan(tx, tableName, layout);
      try {
         us.insert();
         for (int i = 0; i < columns.length; i++)
            setValue(us, tableName, columns[i], values[i]);
         return us.getRid();
      }
      finally {
         us.close();
      }
   }

   public int deleteRows(String tableName, Predicate predicate, Transaction tx) {
      requireTable(tableName, tx);
      Plan p = new TablePlan(tx, tableName, metadataMgr);
      p = new SelectPlan(p, predicate);
      UpdateScan us = (UpdateScan) p.open();
      int count = 0;
      try {
         while (us.next()) {
            us.delete();
            count++;
         }
         return count;
      }
      finally {
         us.close();
      }
   }

   public int getRecordCount(String tableName, Transaction tx) {
      Scan scan = scan(tableName, tx);
      int count = 0;
      try {
         while (scan.next())
            count++;
         return count;
      }
      finally {
         scan.close();
      }
   }

   /**
    * Writes one value and turns SimpleDB's raw null / cast failures into a
    * typed engine error. The row inserted by the caller is removed again so a
    * rejected INSERT does not leave a half-written record behind.
    */
   private void setValue(UpdateScan us, String tableName, String column, Constant value) {
      try {
         us.setVal(column, value);
      }
      catch (NullPointerException e) {
         us.delete();
         throw EngineException.typeMismatch(tableName, column,
               "value " + value + " is not an INT");
      }
      catch (ClassCastException e) {
         us.delete();
         throw EngineException.typeMismatch(tableName, column,
               "value " + value + " does not match the declared column type");
      }
   }

   private void requireTable(String tableName, Transaction tx) {
      if (!metadataMgr.tableExists(tableName, tx))
         throw EngineException.tableNotFound(tableName);
   }
}
