package tests.mock;

import java.util.*;
import simpledb.engine.StorageEngine;
import simpledb.query.*;
import simpledb.record.RID;
import simpledb.tx.Transaction;

/**
 * Storage engine stub that records calls instead of touching a page.
 *
 * The recorded call log is what lets a test assert things a real engine
 * cannot easily show, for example that a rejected INSERT never reached
 * storage at all.
 */
public class MockStorageEngine implements StorageEngine {
   private final List<String> calls = new ArrayList<>();
   private int rowsToDelete;
   private int rowsToUpdate;

   /** Decides how many rows the next delete reports as affected. */
   public void setRowsToDelete(int rows) {
      this.rowsToDelete = rows;
   }

   /** Decides how many rows the next update reports as affected. */
   public void setRowsToUpdate(int rows) {
      this.rowsToUpdate = rows;
   }

   public Scan scan(String tableName, Transaction tx) {
      calls.add("scan:" + tableName);
      return null;
   }

   public RID insertRow(String tableName, String[] columns, Constant[] values, Transaction tx) {
      calls.add("insert:" + tableName + ":" + Arrays.toString(columns));
      return null;
   }

   public int updateRows(String tableName, Predicate predicate, String[] columns,
                         Constant[] values, Transaction tx) {
      calls.add("update:" + tableName + ":" + Arrays.toString(columns));
      return rowsToUpdate;
   }

   public int deleteRows(String tableName, Predicate predicate, Transaction tx) {
      calls.add("delete:" + tableName);
      return rowsToDelete;
   }

   public int getRecordCount(String tableName, Transaction tx) {
      calls.add("count:" + tableName);
      return 0;
   }

   public List<String> calls() {
      return calls;
   }

   public String lastCall() {
      return calls.isEmpty() ? null : calls.get(calls.size() - 1);
   }

   public int callCount() {
      return calls.size();
   }
}
