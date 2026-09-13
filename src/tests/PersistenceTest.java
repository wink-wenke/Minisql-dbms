package tests;

import java.util.*;
import simpledb.engine.*;
import simpledb.logical.*;
import simpledb.query.*;
import simpledb.server.SimpleDB;
import simpledb.shared.*;
import simpledb.tx.Transaction;

/**
 * Covers the Level-2 acceptance criterion: whatever a run writes has to be
 * readable again after the database is reopened.
 *
 * Reopening is simulated by constructing a second SimpleDB over the same
 * directory, which reloads the catalog and the data pages from disk. It works
 * because Transaction.commit() flushes the transaction's buffers.
 */
public class PersistenceTest extends TestBase {
   private static final String DB = "persistdb";
   private static final String TABLE = "account";
   private static String dir;

   private static final List<ColumnDef> COLUMNS = Arrays.asList(
      new ColumnDef("id", ColumnType.INTEGER, 0),
      new ColumnDef("owner", ColumnType.VARCHAR, 16),
      new ColumnDef("balance", ColumnType.INTEGER, 0));

   protected String suiteName() {
      return "PersistenceTest - data and catalog survive a restart";
   }

   protected void cases() throws Exception {
      dir = freshDatabase(DB);

      test("rows written before a restart are still there", () -> {
         SimpleDB first = new SimpleDB(dir);
         Transaction tx = first.newTx();
         Executor executor = new ExecutorImpl(first.mdMgr());
         executor.execute(new CreateTablePlan(TABLE, COLUMNS), tx);
         insert(executor, tx, 1, "Alice", 100);
         insert(executor, tx, 2, "Bob", 50);
         tx.commit();

         SimpleDB reopened = new SimpleDB(dir);
         Transaction tx2 = reopened.newTx();
         Executor executor2 = new ExecutorImpl(reopened.mdMgr());
         ExecuteResult r = executor2.execute(new SeqScanPlan(TABLE, COLUMNS), tx2);
         assertEquals("row count after restart", 2, r.getRows().size());
         tx2.commit();
      });

      test("a delete before a restart stays deleted", () -> {
         SimpleDB first = new SimpleDB(dir);
         Transaction tx = first.newTx();
         Executor executor = new ExecutorImpl(first.mdMgr());
         ExecuteResult deleted = executor.execute(
            new DeletePlan(TABLE, compare("id", "=", 1)), tx);
         assertEquals("affected rows", 1, deleted.getAffectedRows());
         tx.commit();

         SimpleDB reopened = new SimpleDB(dir);
         Transaction tx2 = reopened.newTx();
         Executor executor2 = new ExecutorImpl(reopened.mdMgr());
         ExecuteResult r = executor2.execute(new SeqScanPlan(TABLE, COLUMNS), tx2);
         assertEquals("row count after restart", 1, r.getRows().size());
         assertEquals("surviving owner", new Constant("Bob"), r.getRows().get(0).get(1));
         tx2.commit();
      });

      test("a row inserted after a restart survives the next one", () -> {
         SimpleDB db = new SimpleDB(dir);
         Transaction tx = db.newTx();
         Executor executor = new ExecutorImpl(db.mdMgr());
         insert(executor, tx, 3, "Carol", 75);
         tx.commit();

         SimpleDB reopened = new SimpleDB(dir);
         Transaction tx2 = reopened.newTx();
         Executor executor2 = new ExecutorImpl(reopened.mdMgr());
         ExecuteResult r = executor2.execute(new SeqScanPlan(TABLE, COLUMNS), tx2);
         assertEquals("row count after restart", 2, r.getRows().size());
         tx2.commit();
      });

      test("the catalog is reloaded, not rebuilt empty", () -> {
         SimpleDB db = new SimpleDB(dir);
         Transaction tx = db.newTx();
         assertEquals("table exists", true, db.mdMgr().tableExists(TABLE, tx));
         assertEquals("column count", 3, db.mdMgr().getColumns(TABLE, tx).size());
         assertEquals("column type", ColumnType.VARCHAR,
               db.mdMgr().getColumn(TABLE, "owner", tx).type());
         tx.commit();
      });

      test("reopening does not lose the column order", () -> {
         SimpleDB db = new SimpleDB(dir);
         Transaction tx = db.newTx();
         ExecuteResult r = new ExecutorImpl(db.mdMgr())
            .execute(new SeqScanPlan(TABLE, COLUMNS), tx);
         assertEquals("column order", Arrays.asList("id", "owner", "balance"),
               r.getColumnNames());
         tx.commit();
      });
   }

   private void insert(Executor executor, Transaction tx, int id, String owner, int balance) {
      executor.execute(new InsertPlan(TABLE,
         Arrays.asList("id", "owner", "balance"),
         Arrays.asList(new Constant(id), new Constant(owner), new Constant(balance))), tx);
   }

   private Predicate compare(String column, String op, int value) {
      return new Predicate(new Term(new Expression(column), op,
         new Expression(new Constant(value))));
   }
}
