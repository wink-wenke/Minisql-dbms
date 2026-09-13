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
 * Boundary cases the course rubric asks for: bulk data that spans blocks,
 * extreme values, empty input, and rejected input.
 *
 * The bulk cases matter more than they look. A page holds 14 slots, so every
 * earlier test only ever exercised a single block. These are the ones that
 * actually run TableScan's "move to the next block" branch.
 */
public class BoundaryTest extends TestBase {
   private static final String DB = "boundarydb";

   private static final List<ColumnDef> COLUMNS = Arrays.asList(
      new ColumnDef("id", ColumnType.INTEGER, 0),
      new ColumnDef("name", ColumnType.VARCHAR, 8),
      new ColumnDef("age", ColumnType.INTEGER, 0));

   protected String suiteName() {
      return "BoundaryTest - 跨块批量、极端值与非法输入";
   }

   protected void cases() throws Exception {
      String dir = freshDatabase(DB);
      SimpleDB db = new SimpleDB(dir);
      MetadataMgr mdm = db.mdMgr();
      final Executor executor = new ExecutorImpl(mdm);
      final Transaction tx = db.newTx();

      test("50 行数据跨多个块存储", () -> {
         executor.execute(new CreateTablePlan("big", COLUMNS), tx);
         for (int i = 0; i < 50; i++)
            insert(executor, tx, "big", i, "n" + i, i);
         ExecuteResult r = executor.execute(new SeqScanPlan("big", COLUMNS), tx);
         assertEquals("row count", 50, r.getRows().size());
         assertTrue("spans more than one block", tx.size("big.tbl") > 1);
      });

      test("跨块删除只删掉匹配的行", () -> {
         ExecuteResult r = executor.execute(new DeletePlan("big", lt("id", 40)), tx);
         assertEquals("affected rows", 40, r.getAffectedRows());
         ExecuteResult left = executor.execute(new SeqScanPlan("big", COLUMNS), tx);
         assertEquals("remaining rows", 10, left.getRows().size());
      });

      test("删除不存在的行不影响数据", () -> {
         ExecuteResult r = executor.execute(new DeletePlan("big", eq("id", 999999)), tx);
         assertEquals("affected rows", 0, r.getAffectedRows());
         assertEquals("rows unchanged", 10,
            executor.execute(new SeqScanPlan("big", COLUMNS), tx).getRows().size());
      });

      test("极端整数能原样存回", () -> {
         executor.execute(new CreateTablePlan("extreme", COLUMNS), tx);
         insert(executor, tx, "extreme", Integer.MAX_VALUE, "max", Integer.MIN_VALUE);
         ExecuteResult r = executor.execute(new SeqScanPlan("extreme", COLUMNS), tx);
         assertEquals("row count", 1, r.getRows().size());
         assertEquals("max int", new Constant(Integer.MAX_VALUE), r.getRows().get(0).get(0));
         assertEquals("min int", new Constant(Integer.MIN_VALUE), r.getRows().get(0).get(2));
      });

      test("空字符串可以存入 VARCHAR 列", () -> {
         executor.execute(new CreateTablePlan("blank", COLUMNS), tx);
         insert(executor, tx, "blank", 1, "", 0);
         ExecuteResult r = executor.execute(new SeqScanPlan("blank", COLUMNS), tx);
         assertEquals("stored value", new Constant(""), r.getRows().get(0).get(1));
      });

      test("空表查询返回表头但没有行", () -> {
         executor.execute(new CreateTablePlan("nothing", COLUMNS), tx);
         ExecuteResult r = executor.execute(new SeqScanPlan("nothing", COLUMNS), tx);
         assertEquals("row count", 0, r.getRows().size());
         assertEquals("column names", Arrays.asList("id", "name", "age"),
            r.getColumnNames());
      });

      test("超长字符串被拒绝，不破坏记录", () -> {
         executor.execute(new CreateTablePlan("overflow", COLUMNS), tx);
         expectError(EngineException.ErrorType.VALUE_TOO_LONG, () ->
            insert(executor, tx, "overflow", 1, "01234567890123456789", 1));
         assertEquals("no row written", 0,
            executor.execute(new SeqScanPlan("overflow", COLUMNS), tx).getRows().size());
      });

      test("刚好等于列宽的字符串可以存入", () -> {
         insert(executor, tx, "overflow", 2, "12345678", 2);
         assertEquals("row count", 1,
            executor.execute(new SeqScanPlan("overflow", COLUMNS), tx).getRows().size());
      });

      test("超长表名被拒绝并说明限制", () ->
         expectError(EngineException.ErrorType.NAME_TOO_LONG, () ->
            executor.execute(new CreateTablePlan("averyveryverylongtablename",
               COLUMNS), tx)));

      test("超长列名被拒绝", () ->
         expectError(EngineException.ErrorType.NAME_TOO_LONG, () ->
            executor.execute(new CreateTablePlan("badcol", Arrays.asList(
               new ColumnDef("thiscolumnnameiswaytoolong",
                  ColumnType.INTEGER, 0))), tx)));

      test("标识符大小写敏感（记录当前行为）", () -> {
         assertEquals("小写表名存在", true, mdm.tableExists("big", tx));
         assertEquals("大写表名不存在", false, mdm.tableExists("BIG", tx));
      });

      tx.commit();
   }

   private void insert(Executor executor, Transaction tx, String table,
                       int id, String name, int age) {
      executor.execute(new InsertPlan(table, Arrays.asList("id", "name", "age"),
         Arrays.asList(new Constant(id), new Constant(name), new Constant(age))), tx);
   }

   private Predicate eq(String column, int value) {
      return new Predicate(new Term(new Expression(column), "=",
         new Expression(new Constant(value))));
   }

   private Predicate lt(String column, int value) {
      return new Predicate(new Term(new Expression(column), "<",
         new Expression(new Constant(value))));
   }
}
