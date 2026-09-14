package tests;

import java.util.*;

import simpledb.engine.EngineException;
import simpledb.engine.Executor;
import simpledb.engine.ExecutorImpl;
import simpledb.logical.AggregateSpec;
import simpledb.logical.CreateTablePlan;
import simpledb.logical.DeletePlan;
import simpledb.logical.FilterPlan;
import simpledb.logical.GroupByPlan;
import simpledb.logical.InsertPlan;
import simpledb.logical.OrderByPlan;
import simpledb.logical.SeqScanPlan;
import simpledb.logical.UpdatePlan;
import simpledb.query.Constant;
import simpledb.query.Expression;
import simpledb.query.Predicate;
import simpledb.query.Term;
import simpledb.server.SimpleDB;
import simpledb.shared.ColumnDef;
import simpledb.shared.ColumnType;
import simpledb.shared.ExecuteResult;
import simpledb.tx.Transaction;

/**
 * 边界条件与异常处理（端到端，真实数据库）。
 *
 * ExecutorUnitTest 用 mock 覆盖单元级校验，本 suite 在真实 SimpleDB 实例上
 * 覆盖「空结果集」「影响 0 行」「各类非法输入被拒绝」这些运行时边界，
 * 对应评分标准中「是否处理主要边界情况和异常情况」。
 */
public class BoundaryTest extends TestBase {
   private static final String DB = "boundarydb";
   private static final String T = "student";

   private static final List<ColumnDef> COLS = Arrays.asList(
      new ColumnDef("sid", ColumnType.INTEGER, 0),
      new ColumnDef("sname", ColumnType.VARCHAR, 12),
      new ColumnDef("score", ColumnType.INTEGER, 0));

   protected String suiteName() {
      return "BoundaryTest - 边界条件与异常处理";
   }

   protected void cases() throws Exception {
      resetDatabase(DB);
      SimpleDB db = new SimpleDB(DB);
      Executor executor = new ExecutorImpl(db.mdMgr());
      Transaction tx = db.newTx();

      // ---------- 空结果集 ----------
      test("空表扫描返回 0 行而不是报错", () -> {
         executor.execute(new CreateTablePlan(T, COLS), tx);
         ExecuteResult r = executor.execute(new SeqScanPlan(T, COLS), tx);
         assertEquals("row count", 0, r.getRows().size());
      });

      // 灌入 3 行数据，供下面的用例使用
      insert(executor, tx, 101, "Alice", 90);
      insert(executor, tx, 102, "Bob", 80);
      insert(executor, tx, 103, "Carol", 70);

      test("WHERE 匹配不到任何行时返回 0 行", () -> {
         ExecuteResult r = executor.execute(
            new FilterPlan(new SeqScanPlan(T, COLS), eq("sid", 999)), tx);
         assertEquals("row count", 0, r.getRows().size());
      });

      test("UPDATE 匹配不到行时影响 0 行", () -> {
         ExecuteResult r = executor.execute(new UpdatePlan(T,
            Arrays.asList("score"), Arrays.asList(new Constant(0)), eq("sid", 999)), tx);
         assertEquals("affected rows", 0, r.getAffectedRows());
      });

      test("DELETE 匹配不到行时影响 0 行", () -> {
         ExecuteResult r = executor.execute(new DeletePlan(T, eq("sid", 999)), tx);
         assertEquals("affected rows", 0, r.getAffectedRows());
      });

      test("删除全部行后再查询得到 0 行", () -> {
         ExecuteResult del = executor.execute(new DeletePlan(T, new Predicate()), tx);
         assertEquals("deleted rows", 3, del.getAffectedRows());
         ExecuteResult r = executor.execute(new SeqScanPlan(T, COLS), tx);
         assertEquals("row count", 0, r.getRows().size());
      });

      // ---------- 空表上的聚合与排序 ----------
      test("空表 GROUP BY 返回 0 组而不是报错", () -> {
         ExecuteResult r = executor.execute(new GroupByPlan(
            new SeqScanPlan(T, COLS), Arrays.asList("sname"),
            Arrays.asList(new AggregateSpec(AggregateSpec.Func.COUNT, "sid"))), tx);
         assertEquals("group count", 0, r.getRows().size());
      });

      test("空表 ORDER BY 返回 0 行而不是报错", () -> {
         ExecuteResult r = executor.execute(new OrderByPlan(
            new SeqScanPlan(T, COLS), Arrays.asList("score")), tx);
         assertEquals("row count", 0, r.getRows().size());
      });

      // ---------- 非法输入被拒绝 ----------
      test("重复建表被拒绝（TABLE_EXISTS）", () -> {
         expectError(EngineException.ErrorType.TABLE_EXISTS, () ->
            executor.execute(new CreateTablePlan(T, COLS), tx));
      });

      test("建没有字段的表被拒绝（EMPTY_SCHEMA）", () -> {
         expectError(EngineException.ErrorType.EMPTY_SCHEMA, () ->
            executor.execute(new CreateTablePlan("empty_t",
               Collections.<ColumnDef>emptyList()), tx));
      });

      test("向不存在的表插入被拒绝（TABLE_NOT_FOUND）", () -> {
         expectError(EngineException.ErrorType.TABLE_NOT_FOUND, () ->
            executor.execute(new InsertPlan("no_such_table",
               Arrays.asList("sid"), Arrays.asList(new Constant(1))), tx));
      });

      test("删除不存在的表被拒绝（TABLE_NOT_FOUND）", () -> {
         expectError(EngineException.ErrorType.TABLE_NOT_FOUND, () ->
            executor.execute(new DeletePlan("no_such_table", new Predicate()), tx));
      });

      test("插入列数与值数不匹配被拒绝（ARITY_MISMATCH）", () -> {
         expectError(EngineException.ErrorType.ARITY_MISMATCH, () ->
            executor.execute(new InsertPlan(T,
               Arrays.asList("sid", "sname"), Arrays.asList(new Constant(1))), tx));
      });

      test("插入不存在的列被拒绝（COLUMN_NOT_FOUND）", () -> {
         expectError(EngineException.ErrorType.COLUMN_NOT_FOUND, () ->
            executor.execute(new InsertPlan(T,
               Arrays.asList("nope"), Arrays.asList(new Constant(1))), tx));
      });

      test("UPDATE 列数与值数不匹配被拒绝（ARITY_MISMATCH）", () -> {
         expectError(EngineException.ErrorType.ARITY_MISMATCH, () ->
            executor.execute(new UpdatePlan(T,
               Arrays.asList("score", "sname"),
               Arrays.asList(new Constant(1)), new Predicate()), tx));
      });

      tx.commit();
   }

   private void insert(Executor executor, Transaction tx, int sid, String name, int score) {
      executor.execute(new InsertPlan(T,
         Arrays.asList("sid", "sname", "score"),
         Arrays.asList(new Constant(sid), new Constant(name), new Constant(score))), tx);
   }

   private Predicate eq(String field, int value) {
      return new Predicate(new Term(
         new Expression(field), new Expression(new Constant(value))));
   }
}
