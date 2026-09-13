package tests;

import java.util.*;
import simpledb.engine.*;
import simpledb.logical.*;
import simpledb.query.*;
import simpledb.shared.*;
import simpledb.tx.Transaction;
import tests.mock.*;

/**
 * Unit tests for the engine dispatcher.
 *
 * Every collaborator is a stub, so nothing touches the disk and the whole
 * suite runs in milliseconds. These cases cover decisions that an
 * integration test cannot observe directly: which collaborator a statement
 * was routed to, and whether a rejected statement stopped before reaching
 * storage.
 */
public class ExecutorUnitTest extends TestBase {
   private static final String TABLE = "student";

   private static final ColumnDef ID = new ColumnDef("id", ColumnType.INTEGER, 0);
   private static final ColumnDef NAME = new ColumnDef("name", ColumnType.VARCHAR, 12);
   private static final ColumnDef AGE = new ColumnDef("age", ColumnType.INTEGER, 0);

   private static final List<ColumnDef> COLUMNS = Arrays.asList(ID, NAME, AGE);

   /**
    * The stubs ignore the transaction, so these tests pass null on purpose.
    * A unit test asserts the engine's own decisions, not what a catalog
    * would do with a real transaction.
    */
   private static final Transaction TX = null;

   protected String suiteName() {
      return "ExecutorUnitTest - dispatcher and validation with stubs";
   }

   protected void cases() throws Exception {
      test("create table is written to the catalog", () -> {
         MockCatalog catalog = new MockCatalog();
         Executor executor = build(catalog, new MockStorageEngine(), emptyConverter());

         executor.execute(new CreateTablePlan(TABLE, COLUMNS), TX);

         assertEquals("table registered", true, catalog.tableExists(TABLE, TX));
         assertEquals("column count", 3, catalog.getColumns(TABLE, TX).size());
      });

      test("create table never touches storage", () -> {
         MockCatalog catalog = new MockCatalog();
         MockStorageEngine storage = new MockStorageEngine();

         build(catalog, storage, emptyConverter())
            .execute(new CreateTablePlan(TABLE, COLUMNS), TX);

         assertEquals("storage untouched", 0, storage.callCount());
      });

      test("creating an existing table is rejected", () -> {
         MockCatalog catalog = new MockCatalog();
         catalog.define(TABLE, ID, NAME, AGE);

         expectError(EngineException.ErrorType.TABLE_EXISTS, () ->
            build(catalog, new MockStorageEngine(), emptyConverter())
               .execute(new CreateTablePlan(TABLE, COLUMNS), TX));
      });

      test("creating a table with no columns is rejected", () ->
         expectError(EngineException.ErrorType.EMPTY_SCHEMA, () ->
            build(new MockCatalog(), new MockStorageEngine(), emptyConverter())
               .execute(new CreateTablePlan(TABLE, Collections.<ColumnDef>emptyList()), TX)));

      test("insert is handed to the storage engine", () -> {
         MockCatalog catalog = new MockCatalog();
         catalog.define(TABLE, ID, NAME, AGE);
         MockStorageEngine storage = new MockStorageEngine();

         ExecuteResult r = build(catalog, storage, emptyConverter())
            .execute(insertPlan(1, "Alice", 20), TX);

         assertEquals("routed to storage", "insert:student:[id, name, age]", storage.lastCall());
         assertEquals("affected rows", 1, r.getAffectedRows());
      });

      test("insert into a missing table stops before storage", () -> {
         MockStorageEngine storage = new MockStorageEngine();

         expectError(EngineException.ErrorType.TABLE_NOT_FOUND, () ->
            build(new MockCatalog(), storage, emptyConverter())
               .execute(insertPlan(1, "Alice", 20), TX));

         assertEquals("storage untouched", 0, storage.callCount());
      });

      test("insert into a missing column stops before storage", () -> {
         MockCatalog catalog = new MockCatalog();
         catalog.define(TABLE, ID, NAME, AGE);
         MockStorageEngine storage = new MockStorageEngine();

         expectError(EngineException.ErrorType.COLUMN_NOT_FOUND, () ->
            build(catalog, storage, emptyConverter()).execute(new InsertPlan(TABLE,
               Arrays.asList("score"), Arrays.asList(new Constant(90))), TX));

         assertEquals("storage untouched", 0, storage.callCount());
      });

      test("column/value count mismatch stops before storage", () -> {
         MockCatalog catalog = new MockCatalog();
         catalog.define(TABLE, ID, NAME, AGE);
         MockStorageEngine storage = new MockStorageEngine();

         expectError(EngineException.ErrorType.ARITY_MISMATCH, () ->
            build(catalog, storage, emptyConverter()).execute(new InsertPlan(TABLE,
               Arrays.asList("id", "name"), Arrays.asList(new Constant(1))), TX));

         assertEquals("storage untouched", 0, storage.callCount());
      });

      test("delete reports the row count returned by storage", () -> {
         MockCatalog catalog = new MockCatalog();
         catalog.define(TABLE, ID, NAME, AGE);
         MockStorageEngine storage = new MockStorageEngine();
         storage.setRowsToDelete(2);

         ExecuteResult r = build(catalog, storage, emptyConverter())
            .execute(new DeletePlan(TABLE, eq("id", 1)), TX);

         assertEquals("routed to storage", "delete:student", storage.lastCall());
         assertEquals("affected rows", 2, r.getAffectedRows());
      });

      test("delete from a missing table stops before storage", () -> {
         MockStorageEngine storage = new MockStorageEngine();

         expectError(EngineException.ErrorType.TABLE_NOT_FOUND, () ->
            build(new MockCatalog(), storage, emptyConverter())
               .execute(new DeletePlan(TABLE, eq("id", 1)), TX));

         assertEquals("storage untouched", 0, storage.callCount());
      });

      test("a query is routed to the converter, not to storage", () -> {
         MockCatalog catalog = new MockCatalog();
         catalog.define(TABLE, ID, NAME, AGE);
         MockStorageEngine storage = new MockStorageEngine();
         MockPlanConverter converter = new MockPlanConverter(
            Arrays.asList("name"),
            Arrays.asList(Arrays.asList(new Constant("Alice")),
                          Arrays.asList(new Constant("Bob"))));

         ExecuteResult r = build(catalog, storage, converter)
            .execute(new SeqScanPlan(TABLE, COLUMNS), TX);

         assertEquals("converter called once", 1, converter.convertCount());
         assertEquals("storage untouched", 0, storage.callCount());
         assertEquals("row count", 2, r.getRows().size());
         assertEquals("result type", ExecuteResult.ResultType.QUERY, r.getType());
      });

      test("a query result carries the converted column names", () -> {
         MockCatalog catalog = new MockCatalog();
         catalog.define(TABLE, ID, NAME, AGE);
         MockPlanConverter converter = new MockPlanConverter(
            Arrays.asList("name", "age"),
            Arrays.asList(Arrays.asList(new Constant("Alice"), new Constant(20))));

         ExecuteResult r = build(catalog, new MockStorageEngine(), converter)
            .execute(new ProjectPlan(new SeqScanPlan(TABLE, COLUMNS),
               Arrays.asList("name", "age")), TX);

         assertEquals("column names", Arrays.asList("name", "age"), r.getColumnNames());
      });

      test("a converter failure is passed through unchanged", () -> {
         MockCatalog catalog = new MockCatalog();
         catalog.define(TABLE, ID, NAME, AGE);
         MockPlanConverter converter = new MockPlanConverter(
            Collections.<String>emptyList(), Collections.<List<Constant>>emptyList());
         converter.failWith(EngineException.planConversion("InsertPlan"));

         expectError(EngineException.ErrorType.PLAN_CONVERSION, () ->
            build(catalog, new MockStorageEngine(), converter)
               .execute(new SeqScanPlan(TABLE, COLUMNS), TX));
      });

      test("result rows cannot be modified by the caller", () -> {
         MockCatalog catalog = new MockCatalog();
         catalog.define(TABLE, ID, NAME, AGE);
         MockPlanConverter converter = new MockPlanConverter(
            Arrays.asList("name"), Arrays.asList(Arrays.asList(new Constant("Alice"))));

         ExecuteResult r = build(catalog, new MockStorageEngine(), converter)
            .execute(new SeqScanPlan(TABLE, COLUMNS), TX);

         boolean blocked = false;
         try {
            r.getRows().add(Arrays.asList(new Constant("Bob")));
         }
         catch (UnsupportedOperationException e) {
            blocked = true;
         }
         assertTrue("rows are unmodifiable", blocked);
      });
   }

   private Executor build(MockCatalog catalog, MockStorageEngine storage,
                          MockPlanConverter converter) {
      return new ExecutorImpl(catalog, catalog, converter, storage);
   }

   private MockPlanConverter emptyConverter() {
      return new MockPlanConverter(Collections.<String>emptyList(),
            Collections.<List<Constant>>emptyList());
   }

   private InsertPlan insertPlan(int id, String name, int age) {
      return new InsertPlan(TABLE, Arrays.asList("id", "name", "age"),
         Arrays.asList(new Constant(id), new Constant(name), new Constant(age)));
   }

   private Predicate eq(String column, int value) {
      return new Predicate(new Term(new Expression(column), "=",
         new Expression(new Constant(value))));
   }
}
