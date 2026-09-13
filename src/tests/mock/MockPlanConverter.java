package tests.mock;

import java.util.*;
import simpledb.engine.EngineException;
import simpledb.engine.PlanConverter;
import simpledb.logical.LogicalPlan;
import simpledb.plan.Plan;
import simpledb.query.*;
import simpledb.record.Schema;
import simpledb.tx.Transaction;

/**
 * Plan converter stub. Returns a fixed in-memory result, which lets the
 * executor's query path run without a physical plan, and records what it was
 * asked to convert.
 */
public class MockPlanConverter implements PlanConverter {
   private LogicalPlan lastPlan;
   private int convertCount;
   private EngineException failure;
   private final Plan result;

   public MockPlanConverter(List<String> columns, List<List<Constant>> rows) {
      this.result = new StubPlan(columns, rows);
   }

   /** Makes the next convert call fail, so error propagation can be checked. */
   public void failWith(EngineException e) {
      this.failure = e;
   }

   public Plan convert(LogicalPlan plan, Transaction tx) {
      lastPlan = plan;
      convertCount++;
      if (failure != null)
         throw failure;
      return result;
   }

   public LogicalPlan lastPlan() {
      return lastPlan;
   }

   public int convertCount() {
      return convertCount;
   }

   private static class StubPlan implements Plan {
      private final List<String> columns;
      private final List<List<Constant>> rows;
      private final Schema schema;

      StubPlan(List<String> columns, List<List<Constant>> rows) {
         this.columns = columns;
         this.rows = rows;
         Schema sch = new Schema();
         for (String column : columns)
            sch.addStringField(column, 20);
         this.schema = sch;
      }

      public Scan open() {
         return new StubScan(columns, rows);
      }

      public int blocksAccessed() {
         return 1;
      }

      public int recordsOutput() {
         return rows.size();
      }

      public int distinctValues(String fldname) {
         return rows.size();
      }

      public Schema schema() {
         return schema;
      }
   }

   private static class StubScan implements Scan {
      private final List<String> columns;
      private final List<List<Constant>> rows;
      private int cursor = -1;

      StubScan(List<String> columns, List<List<Constant>> rows) {
         this.columns = columns;
         this.rows = rows;
      }

      public void beforeFirst() {
         cursor = -1;
      }

      public boolean next() {
         cursor++;
         return cursor < rows.size();
      }

      public int getInt(String fldname) {
         return getVal(fldname).asInt();
      }

      public String getString(String fldname) {
         return getVal(fldname).asString();
      }

      public Constant getVal(String fldname) {
         int index = columns.indexOf(fldname);
         if (index < 0)
            throw new IllegalArgumentException("no such field: " + fldname);
         return rows.get(cursor).get(index);
      }

      public boolean hasField(String fldname) {
         return columns.contains(fldname);
      }

      public void close() {
      }
   }
}
