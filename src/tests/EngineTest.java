package tests;

import java.util.*;
import simpledb.engine.*;
import simpledb.logical.*;
import simpledb.metadata.MetadataMgr;
import simpledb.query.*;
import simpledb.server.SimpleDB;
import simpledb.shared.*;
import simpledb.tx.Transaction;

/**
 * Core behaviour of the member-B engine: the four statements the course
 * requires, the predicates they rely on, and the error classification.
 */
public class EngineTest extends TestBase {
   private static final String DB = "testdb";
   private static final String TABLE = "student";

   private static final List<ColumnDef> COLUMNS = Arrays.asList(
      new ColumnDef("id", ColumnType.INTEGER, 0),
      new ColumnDef("name", ColumnType.VARCHAR, 12),
      new ColumnDef("age", ColumnType.INTEGER, 0));

   protected String suiteName() {
      return "EngineTest - CREATE / INSERT / SELECT / DELETE + errors";
   }

   protected void cases() throws Exception {
      resetDatabase(DB);
      SimpleDB db = new SimpleDB(DB);
      MetadataMgr mdm = db.mdMgr();
      final Executor executor = new ExecutorImpl(mdm);
      final Transaction tx = db.newTx();

      test("create table", () -> {
         ExecuteResult r = executor.execute(new CreateTablePlan(TABLE, COLUMNS), tx);
         assertEquals("result type", ExecuteResult.ResultType.UPDATE, r.getType());
      });

      test("duplicate create table is rejected", () ->
         expectError(EngineException.ErrorType.TABLE_EXISTS,
            () -> executor.execute(new CreateTablePlan(TABLE, COLUMNS), tx)));

      test("insert two rows", () -> {
         assertEquals("affected", 1, insert(executor, tx, 1, "Alice", 20).getAffectedRows());
         assertEquals("affected", 1, insert(executor, tx, 2, "Bob", 17).getAffectedRows());
      });

      test("seq scan returns every row", () -> {
         ExecuteResult r = executor.execute(new SeqScanPlan(TABLE, COLUMNS), tx);
         assertEquals("row count", 2, r.getRows().size());
      });

      test("filter with > keeps matching rows only", () -> {
         ExecuteResult r = executor.execute(
            new FilterPlan(new SeqScanPlan(TABLE, COLUMNS), compare("age", ">", 18)), tx);
         assertEquals("row count", 1, r.getRows().size());
         assertEquals("name", new Constant("Alice"), r.getRows().get(0).get(1));
      });

      test("filter with != works", () -> {
         ExecuteResult r = executor.execute(
            new FilterPlan(new SeqScanPlan(TABLE, COLUMNS), compare("id", "!=", 1)), tx);
         assertEquals("row count", 1, r.getRows().size());
         assertEquals("id", new Constant(2), r.getRows().get(0).get(0));
      });

      test("project narrows the output columns", () -> {
         ExecuteResult r = executor.execute(
            new ProjectPlan(new SeqScanPlan(TABLE, COLUMNS), Arrays.asList("name")), tx);
         assertEquals("column count", 1, r.getColumnNames().size());
         assertEquals("column name", "name", r.getColumnNames().get(0));
         assertEquals("row count", 2, r.getRows().size());
      });

      test("delete removes matching rows", () -> {
         ExecuteResult r = executor.execute(
            new DeletePlan(TABLE, compare("id", "=", 1)), tx);
         assertEquals("affected rows", 1, r.getAffectedRows());
      });

      test("deleted row is invisible to later scans", () -> {
         ExecuteResult r = executor.execute(new SeqScanPlan(TABLE, COLUMNS), tx);
         assertEquals("row count", 1, r.getRows().size());
      });

      test("insert with a wrong column type is rejected and leaves no row", () -> {
         expectError(EngineException.ErrorType.TYPE_MISMATCH,
            () -> executor.execute(new InsertPlan(TABLE,
               Arrays.asList("id", "name", "age"),
               Arrays.asList(new Constant("notAnInt"), new Constant("Bob"),
                  new Constant(17))), tx));
         ExecuteResult r = executor.execute(new SeqScanPlan(TABLE, COLUMNS), tx);
         assertEquals("no half-written row left behind", 1, r.getRows().size());
      });

      test("storage engine counts the stored rows", () -> {
         StorageEngine storage = new StorageEngineImpl(mdm);
         assertEquals("record count", 1, storage.getRecordCount(TABLE, tx));
      });

      test("output schema treats * as every child column", () -> {
         LogicalPlan star = new ProjectPlan(
            new SeqScanPlan(TABLE, COLUMNS), Arrays.asList("*"));
         assertEquals("schema size", 3, star.outputSchema().size());
      });

      test("a partial insert fills only the given columns", () -> {
         ExecuteResult r = executor.execute(new InsertPlan(TABLE,
            Arrays.asList("id"), Arrays.asList(new Constant(3))), tx);
         assertEquals("affected", 1, r.getAffectedRows());
      });

      test("insert into a missing table is rejected", () ->
         expectError(EngineException.ErrorType.TABLE_NOT_FOUND,
            () -> executor.execute(new InsertPlan("nosuchtable",
               Arrays.asList("id"), Arrays.asList(new Constant(1))), tx)));

      test("insert into a missing column is rejected", () ->
         expectError(EngineException.ErrorType.COLUMN_NOT_FOUND,
            () -> executor.execute(new InsertPlan(TABLE,
               Arrays.asList("score"), Arrays.asList(new Constant(1))), tx)));

      test("column/value count mismatch is rejected", () ->
         expectError(EngineException.ErrorType.ARITY_MISMATCH,
            () -> executor.execute(new InsertPlan(TABLE,
               Arrays.asList("id", "name"), Arrays.asList(new Constant(1))), tx)));

      test("scan of a missing table is rejected", () ->
         expectError(EngineException.ErrorType.TABLE_NOT_FOUND,
            () -> executor.execute(new SeqScanPlan("nosuchtable"), tx)));

      test("delete from a missing table is rejected", () ->
         expectError(EngineException.ErrorType.TABLE_NOT_FOUND,
            () -> executor.execute(
               new DeletePlan("nosuchtable", compare("id", "=", 1)), tx)));

      test("output schema is bound bottom-up", () -> {
         LogicalPlan plan = new ProjectPlan(
            new SeqScanPlan(TABLE, COLUMNS), Arrays.asList("name", "id"));
         List<ColumnDef> schema = plan.outputSchema();
         assertEquals("schema size", 2, schema.size());
         assertEquals("first column", "name", schema.get(0).name());
         assertEquals("second column", "id", schema.get(1).name());
      });

      test("explain renders a plan tree", () -> {
         String text = new ProjectPlan(
            new FilterPlan(new SeqScanPlan(TABLE, COLUMNS), compare("age", ">", 18)),
            Arrays.asList("name")).explain(0);
         assertTrue("contains SeqScan", text.contains("SeqScan[student]"));
         assertTrue("contains Filter", text.contains("Filter"));
         assertTrue("contains Project", text.contains("Project"));
      });

      test("catalog reader resolves columns through a live transaction", () -> {
         assertEquals("table exists", true, mdm.tableExists(TABLE, tx));
         assertEquals("column exists", true, mdm.columnExists(TABLE, "age", tx));
         assertEquals("missing column", false, mdm.columnExists(TABLE, "score", tx));
         assertEquals("column count", 3, mdm.getColumns(TABLE, tx).size());
      });

      tx.commit();
   }

   private ExecuteResult insert(Executor executor, Transaction tx, int id, String name, int age) {
      return executor.execute(new InsertPlan(TABLE,
         Arrays.asList("id", "name", "age"),
         Arrays.asList(new Constant(id), new Constant(name), new Constant(age))), tx);
   }

   private Predicate compare(String column, String op, int value) {
      return new Predicate(new Term(new Expression(column), op,
         new Expression(new Constant(value))));
   }
}
