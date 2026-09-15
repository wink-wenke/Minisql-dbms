package tests;

import java.util.*;
import simpledb.buffer.BufferMgr;
import simpledb.engine.*;
import simpledb.logical.*;
import simpledb.metadata.MetadataMgr;
import simpledb.plan.*;
import simpledb.query.*;
import simpledb.server.SimpleDB;
import simpledb.shared.*;
import simpledb.storage.CacheStats;
import simpledb.tx.Transaction;
import tests.TestCleanup;

/**
 * Full pipeline integration test.
 *
 * Covers the complete flow: SQL parsing -> Engine execution -> Storage layer
 * verification, including transaction commit/rollback and cache statistics.
 *
 * Run from project root:
 *   java -cp out tests.FullPipelineTest
 */
public class FullPipelineTest extends TestBase {
   private static final String DB = "fullpipelinedb";

   protected String suiteName() {
      return "FullPipelineTest - Parser -> Engine -> Storage full pipeline";
   }

   protected void cases() throws Exception {
      resetDatabase(DB);
        TestCleanup.init();
      SimpleDB.DB_BASE_DIR = "tmp";
      SimpleDB db = new SimpleDB(DB);
      MetadataMgr mdm = db.mdMgr();
      Transaction tx = db.newTx();
      Planner planner = db.planner();
      Executor executor = new ExecutorImpl(mdm);

      // ========== Phase 1: Parser path (SQL strings) ==========
      test("Parser: CREATE TABLE via SQL", () -> {
         planner.executeUpdate(
            "CREATE TABLE course(cid INT, title VARCHAR(30), score INT)", tx);
         assertTrue("table exists", mdm.tableExists("course", tx));
      });

      test("Parser: INSERT 3 rows via SQL", () -> {
         planner.executeUpdate(
            "INSERT INTO course(cid, title, score) VALUES (1, 'Math', 90)", tx);
         planner.executeUpdate(
            "INSERT INTO course(cid, title, score) VALUES (2, 'English', 85)", tx);
         planner.executeUpdate(
            "INSERT INTO course(cid, title, score) VALUES (3, 'Physics', 92)", tx);
      });

      test("Parser: SELECT with WHERE via SQL", () -> {
         Plan p = planner.createQueryPlan(
            "SELECT cid, title FROM course WHERE score > 88", tx);
         Scan s = p.open();
         List<List<Constant>> rows = new ArrayList<>();
         while (s.next()) {
            rows.add(Arrays.asList(s.getVal("cid"), s.getVal("title")));
         }
         s.close();
         assertEquals("row count", 2, rows.size());
      });

      test("Parser: DELETE via SQL", () -> {
         int affected = planner.executeUpdate(
            "DELETE FROM course WHERE cid = 2", tx);
         assertEquals("affected rows", 1, affected);
      });

      // ========== Phase 2: Engine path (LogicalPlan) ==========
      test("Engine: CREATE TABLE via LogicalPlan", () -> {
         List<ColumnDef> scoreCols = Arrays.asList(
            new ColumnDef("sid", ColumnType.INTEGER, 0),
            new ColumnDef("sname", ColumnType.VARCHAR, 20),
            new ColumnDef("score", ColumnType.INTEGER, 0));
         ExecuteResult r = executor.execute(
            new CreateTablePlan("scores", scoreCols), tx);
         assertEquals("result type", ExecuteResult.ResultType.UPDATE, r.getType());
      });

      test("Engine: INSERT 3 rows via LogicalPlan", () -> {
         ExecuteResult r;
         r = executor.execute(new InsertPlan("scores",
            Arrays.asList("sid", "sname", "score"),
            Arrays.asList(new Constant(1), new Constant("Alice"), new Constant(95))), tx);
         assertEquals("insert 1", 1, r.getAffectedRows());

         r = executor.execute(new InsertPlan("scores",
            Arrays.asList("sid", "sname", "score"),
            Arrays.asList(new Constant(2), new Constant("Bob"), new Constant(78))), tx);
         assertEquals("insert 2", 1, r.getAffectedRows());

         r = executor.execute(new InsertPlan("scores",
            Arrays.asList("sid", "sname", "score"),
            Arrays.asList(new Constant(3), new Constant("Charlie"), new Constant(88))), tx);
         assertEquals("insert 3", 1, r.getAffectedRows());
      });

      test("Engine: SELECT with filter via LogicalPlan", () -> {
         List<ColumnDef> filterCols = Arrays.asList(
            new ColumnDef("sid", ColumnType.INTEGER, 0),
            new ColumnDef("sname", ColumnType.VARCHAR, 20),
            new ColumnDef("score", ColumnType.INTEGER, 0));
         Predicate pred = new Predicate(new Term(
            new Expression("score"), new Expression(new Constant(80)), CompOp.GREATER));
         ExecuteResult r = executor.execute(
            new FilterPlan(new SeqScanPlan("scores", filterCols), pred), tx);
         assertEquals("row count", 2, r.getRows().size());
      });

      test("Engine: DELETE via LogicalPlan", () -> {
         ExecuteResult r = executor.execute(
            new DeletePlan("scores", new Predicate(new Term(
               new Expression("sid"), new Expression(new Constant(2))))), tx);
         assertEquals("affected rows", 1, r.getAffectedRows());
      });

      // ========== Phase 3: Storage layer verification ==========
      test("Storage: record count matches after all operations", () -> {
         StorageEngine storage = new StorageEngineImpl(mdm);
         assertEquals("course records", 2, storage.getRecordCount("course", tx));
         assertEquals("scores records", 2, storage.getRecordCount("scores", tx));
      });

      test("Storage: cache stats show hits after repeated reads", () -> {
         CacheStats stats = db.bufferMgr().getStats();
         assertTrue("has access count", stats.getAccessCount() > 0);
         assertTrue("has hit count", stats.getHitCount() > 0);
      });

      // ========== Phase 4: Transaction commit/rollback ==========
      test("Transaction: COMMIT persists data", () -> {
         tx.commit();
         Transaction tx2 = db.newTx();
         StorageEngine storage = new StorageEngineImpl(mdm);
         assertEquals("course persists after commit", 2,
            storage.getRecordCount("course", tx2));
         assertEquals("scores persists after commit", 2,
            storage.getRecordCount("scores", tx2));
         tx2.commit();
      });

      test("Transaction: ROLLBACK undoes uncommitted writes", () -> {
         Transaction tx3 = db.newTx();
         executor.execute(new InsertPlan("scores",
            Arrays.asList("sid", "sname", "score"),
            Arrays.asList(new Constant(99), new Constant("Temp"), new Constant(50))), tx3);
         StorageEngine storage = new StorageEngineImpl(mdm);
         assertEquals("visible before rollback", 3,
            storage.getRecordCount("scores", tx3));
         tx3.rollback();

         Transaction tx4 = db.newTx();
         StorageEngine storage2 = new StorageEngineImpl(mdm);
         assertEquals("invisible after rollback", 2,
            storage2.getRecordCount("scores", tx4));
         tx4.commit();
      });

      // ========== Phase 5: Cross-path consistency ==========
      test("Cross-path: Parser and Engine see the same data", () -> {
         Transaction tx5 = db.newTx();

         // Engine path: scan scores
         List<ColumnDef> scanCols = Arrays.asList(
            new ColumnDef("sid", ColumnType.INTEGER, 0),
            new ColumnDef("sname", ColumnType.VARCHAR, 20),
            new ColumnDef("score", ColumnType.INTEGER, 0));
         ExecuteResult engineResult = executor.execute(
            new SeqScanPlan("scores", scanCols), tx5);

         // Parser path: scan scores
         Plan p = planner.createQueryPlan("SELECT * FROM scores", tx5);
         Scan s = p.open();
         int parserCount = 0;
         while (s.next()) parserCount++;
         s.close();

         assertEquals("engine vs parser row count",
            engineResult.getRows().size(), parserCount);
         tx5.commit();
      });

      // ========== Phase 6: Error handling across layers ==========
      test("Error: Parser rejects bad SQL", () -> {
         try {
            planner.createQueryPlan("SELEC * FROM course", tx);
            throw new AssertionError("should throw");
         } catch (Exception e) {
            assertTrue("parser error",
               e.getMessage().contains("expecting") || e.getMessage().contains("SELEC"));
         }
      });

      test("Error: Engine rejects duplicate table", () -> {
         List<ColumnDef> dupCols = Arrays.asList(
            new ColumnDef("x", ColumnType.INTEGER, 0));
         try {
            executor.execute(new CreateTablePlan("course", dupCols), tx);
            throw new AssertionError("should throw");
         } catch (EngineException e) {
            assertEquals("error type", EngineException.ErrorType.TABLE_EXISTS, e.getType());
         }
      });

      test("Error: Engine rejects insert into missing table", () -> {
         try {
            executor.execute(new InsertPlan("nosuch",
               Arrays.asList("a"), Arrays.asList(new Constant(1))), tx);
            throw new AssertionError("should throw");
         } catch (EngineException e) {
            assertEquals("error type", EngineException.ErrorType.TABLE_NOT_FOUND, e.getType());
         }
      });

      test("Error: Engine rejects insert with wrong column", () -> {
         try {
            executor.execute(new InsertPlan("scores",
               Arrays.asList("badcol"), Arrays.asList(new Constant(1))), tx);
            throw new AssertionError("should throw");
         } catch (EngineException e) {
            assertEquals("error type", EngineException.ErrorType.COLUMN_NOT_FOUND, e.getType());
         }
      });

      test("Error: Engine rejects arity mismatch", () -> {
         try {
            executor.execute(new InsertPlan("scores",
               Arrays.asList("sid", "sname"), Arrays.asList(new Constant(1))), tx);
            throw new AssertionError("should throw");
         } catch (EngineException e) {
            assertEquals("error type", EngineException.ErrorType.ARITY_MISMATCH, e.getType());
         }
      });

      // ========== Phase 7: Plan visualization ==========
      test("Explain: both paths produce readable output", () -> {
         // Engine path explain
         List<ColumnDef> explainCols = Arrays.asList(
            new ColumnDef("sid", ColumnType.INTEGER, 0),
            new ColumnDef("sname", ColumnType.VARCHAR, 20),
            new ColumnDef("score", ColumnType.INTEGER, 0));
         LogicalPlan enginePlan = new simpledb.logical.ProjectPlan(
            new FilterPlan(
               new SeqScanPlan("scores", explainCols),
               new Predicate(new Term(
                  new Expression("score"), new Expression(new Constant(80)), CompOp.GREATER))),
            Arrays.asList("sname"));
         String engineExplain = enginePlan.explain(0);
         assertTrue("engine explain has SeqScan", engineExplain.contains("SeqScan"));
         assertTrue("engine explain has Filter", engineExplain.contains("Filter"));
         assertTrue("engine explain has Project", engineExplain.contains("Project"));

         // Parser path explain
         String parserExplain = planner.explain(
            "SELECT sname FROM scores WHERE score > 80", tx);
         assertTrue("parser explain has SQL", parserExplain.contains("SELECT"));
      });

      tx.commit();
   }

   protected void cleanup() {
      resetDatabase(DB);
   }

   public static void main(String[] args) {
      boolean ok = new FullPipelineTest().run();
      System.exit(ok ? 0 : 1);
   }
}
