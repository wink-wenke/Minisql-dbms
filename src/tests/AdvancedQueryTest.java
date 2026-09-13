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
 * Level-4 query features built on top of the SimpleDB physical operators:
 *
 *   UPDATE   -> StorageEngineImpl.updateRows
 *   JOIN     -> plan.ProductPlan (+ plan.SelectPlan for the join predicate)
 *   ORDER BY -> materialize.SortPlan
 *   GROUP BY -> materialize.GroupByPlan with COUNT/SUM/AVG/MIN/MAX
 *
 * The two tables join on student.majorid = department.deptid, deliberately
 * using different column names so the product schema has no duplicate field.
 */
public class AdvancedQueryTest extends TestBase {
   private static final String DB = "advancedb";
   private static final String DEPT = "department";
   private static final String STUDENT = "student";

   private static final List<ColumnDef> DEPT_COLS = Arrays.asList(
      new ColumnDef("deptid", ColumnType.INTEGER, 0),
      new ColumnDef("dname", ColumnType.VARCHAR, 12));

   private static final List<ColumnDef> STUDENT_COLS = Arrays.asList(
      new ColumnDef("sid", ColumnType.INTEGER, 0),
      new ColumnDef("sname", ColumnType.VARCHAR, 12),
      new ColumnDef("majorid", ColumnType.INTEGER, 0),
      new ColumnDef("score", ColumnType.INTEGER, 0));

   protected String suiteName() {
      return "AdvancedQueryTest - UPDATE / JOIN / ORDER BY / GROUP BY";
   }

   protected void cases() throws Exception {
      String dir = freshDatabase(DB);
      SimpleDB db = new SimpleDB(dir);
      MetadataMgr mdm = db.mdMgr();
      final Executor executor = new ExecutorImpl(mdm);
      final Transaction tx = db.newTx();

      // --- schema + seed data ------------------------------------------------
      test("create department table", () ->
         executor.execute(new CreateTablePlan(DEPT, DEPT_COLS), tx));
      test("create student table", () ->
         executor.execute(new CreateTablePlan(STUDENT, STUDENT_COLS), tx));

      insert(executor, tx, DEPT, Arrays.asList("deptid", "dname"),
         Arrays.asList(new Constant(1), new Constant("CS")));
      insert(executor, tx, DEPT, Arrays.asList("deptid", "dname"),
         Arrays.asList(new Constant(2), new Constant("Math")));
      insert(executor, tx, STUDENT, Arrays.asList("sid", "sname", "majorid", "score"),
         Arrays.asList(new Constant(101), new Constant("Alice"), new Constant(1), new Constant(90)));
      insert(executor, tx, STUDENT, Arrays.asList("sid", "sname", "majorid", "score"),
         Arrays.asList(new Constant(102), new Constant("Bob"), new Constant(1), new Constant(80)));
      insert(executor, tx, STUDENT, Arrays.asList("sid", "sname", "majorid", "score"),
         Arrays.asList(new Constant(103), new Constant("Carol"), new Constant(2), new Constant(70)));
      insert(executor, tx, STUDENT, Arrays.asList("sid", "sname", "majorid", "score"),
         Arrays.asList(new Constant(104), new Constant("Dave"), new Constant(2), new Constant(60)));

      // --- UPDATE ------------------------------------------------------------
      test("update changes the matching row only", () -> {
         Predicate p = eq("sid", 101);
         ExecuteResult r = executor.execute(
            new UpdatePlan(STUDENT, Arrays.asList("score"),
               Arrays.asList(new Constant(100)), p), tx);
         assertEquals("affected rows", 1, r.getAffectedRows());
      });

      test("updated value is visible afterwards", () -> {
         ExecuteResult r = executor.execute(new SeqScanPlan(STUDENT, STUDENT_COLS), tx);
         assertEquals("row count still 4", 4, r.getRows().size());
         List<Constant> alice = find(r, 0, 101);
         assertEquals("alice score updated to 100", new Constant(100), alice.get(3));
      });

      // --- JOIN --------------------------------------------------------------
      test("join output schema merges both tables", () -> {
         LogicalPlan join = new JoinPlan(
            new SeqScanPlan(STUDENT, STUDENT_COLS),
            new SeqScanPlan(DEPT, DEPT_COLS), eq("majorid", "deptid"));
         assertEquals("schema width 6", 6, join.outputSchema().size());
      });

      test("join pairs students with their department", () -> {
         LogicalPlan project = new ProjectPlan(
            new JoinPlan(new SeqScanPlan(STUDENT, STUDENT_COLS),
                         new SeqScanPlan(DEPT, DEPT_COLS), eq("majorid", "deptid")),
            Arrays.asList("sname", "dname"));
         ExecuteResult r = executor.execute(project, tx);
         assertEquals("4 joined rows", 4, r.getRows().size());

         List<String> pairs = new ArrayList<>();
         for (List<Constant> row : r.getRows())
            pairs.add(row.get(0).asString() + "|" + row.get(1).asString());
         Collections.sort(pairs);
         List<String> expected = Arrays.asList(
            "Alice|CS", "Bob|CS", "Carol|Math", "Dave|Math");
         Collections.sort(expected);
         assertEquals("joined pairs", expected.toString(), pairs.toString());
      });

      // --- ORDER BY ----------------------------------------------------------
      test("order by score returns rows in ascending order", () -> {
         LogicalPlan ordered = new OrderByPlan(
            new ProjectPlan(new SeqScanPlan(STUDENT, STUDENT_COLS),
               Arrays.asList("score")),
            Arrays.asList("score"));
         ExecuteResult r = executor.execute(ordered, tx);
         List<Integer> scores = new ArrayList<>();
         for (List<Constant> row : r.getRows())
            scores.add(row.get(0).asInt());
         assertEquals("sorted scores", Arrays.asList(60, 70, 80, 100).toString(),
            scores.toString());
      });

      // --- GROUP BY ----------------------------------------------------------
      test("group by schema is group columns plus aggregates", () -> {
         LogicalPlan gb = groupBy();
         List<ColumnDef> schema = gb.outputSchema();
         assertEquals("1 group + 5 aggregates = 6", 6, schema.size());
         List<String> names = new ArrayList<>();
         for (ColumnDef d : schema)
            names.add(d.name());
         assertTrue("has countofsid", names.contains("countofsid"));
         assertTrue("has sumofscore", names.contains("sumofscore"));
         assertTrue("has avgofscore", names.contains("avgofscore"));
         assertTrue("has minofscore", names.contains("minofscore"));
         assertTrue("has maxofscore", names.contains("maxofscore"));
      });

      test("group by aggregates per major", () -> {
         ExecuteResult r = executor.execute(groupBy(), tx);
         assertEquals("2 groups", 2, r.getRows().size());

         Map<Integer, List<Constant>> byMajor = new HashMap<>();
         for (List<Constant> row : r.getRows())
            byMajor.put(row.get(0).asInt(), row);

         // columns: majorid, countofsid, sumofscore, avgofscore, minofscore, maxofscore
         List<Constant> g1 = byMajor.get(1);
         assertEquals("major1 count", new Constant(2), g1.get(1));
         assertEquals("major1 sum", new Constant(180), g1.get(2));
         assertEquals("major1 avg", new Constant(90), g1.get(3));
         assertEquals("major1 min", new Constant(80), g1.get(4));
         assertEquals("major1 max", new Constant(100), g1.get(5));

         List<Constant> g2 = byMajor.get(2);
         assertEquals("major2 count", new Constant(2), g2.get(1));
         assertEquals("major2 sum", new Constant(130), g2.get(2));
         assertEquals("major2 avg", new Constant(65), g2.get(3));
         assertEquals("major2 min", new Constant(60), g2.get(4));
         assertEquals("major2 max", new Constant(70), g2.get(5));
      });

      tx.commit();
   }

   private LogicalPlan groupBy() {
      return new GroupByPlan(
         new SeqScanPlan(STUDENT, STUDENT_COLS),
         Arrays.asList("majorid"),
         Arrays.asList(
            new AggregateSpec(AggregateSpec.Func.COUNT, "sid"),
            new AggregateSpec(AggregateSpec.Func.SUM, "score"),
            new AggregateSpec(AggregateSpec.Func.AVG, "score"),
            new AggregateSpec(AggregateSpec.Func.MIN, "score"),
            new AggregateSpec(AggregateSpec.Func.MAX, "score")));
   }

   private ExecuteResult insert(Executor executor, Transaction tx, String table,
                                List<String> cols, List<Constant> vals) {
      return executor.execute(new InsertPlan(table, cols, vals), tx);
   }

   private List<Constant> find(ExecuteResult r, int keyIndex, int keyValue) {
      for (List<Constant> row : r.getRows())
         if (row.get(keyIndex).asInt() == keyValue)
            return row;
      throw new AssertionError("row with key " + keyValue + " not found");
   }

   private Predicate eq(String left, int right) {
      return new Predicate(new Term(
         new Expression(left), "=", new Expression(new Constant(right))));
   }

   private Predicate eq(String left, String right) {
      return new Predicate(new Term(
         new Expression(left), "=", new Expression(right)));
   }
}
