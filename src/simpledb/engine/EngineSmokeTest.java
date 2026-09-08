package simpledb.engine;

import java.util.*;
import simpledb.logical.*;
import simpledb.query.*;
import simpledb.server.SimpleDB;
import simpledb.shared.*;
import simpledb.tx.Transaction;

/**
 * Demonstrates the member-B engine boundary without depending on SQL parsing.
 */
public class EngineSmokeTest {
   public static void main(String[] args) {
      SimpleDB db = new SimpleDB("enginetestdb");
      Transaction tx = db.newTx();
      Executor executor = new ExecutorImpl(db.mdMgr());

      String table = "eng" + (System.currentTimeMillis() % 100000);
      List<ColumnDef> columns = Arrays.asList(
         new ColumnDef("id", ColumnType.INTEGER, 0),
         new ColumnDef("name", ColumnType.VARCHAR, 12),
         new ColumnDef("age", ColumnType.INTEGER, 0)
      );

      System.out.println(executor.execute(new CreateTablePlan(table, columns), tx).formatted());
      System.out.println(executor.execute(new InsertPlan(table,
         Arrays.asList("id", "name", "age"),
         Arrays.asList(new Constant(1), new Constant("Alice"), new Constant(20))), tx).formatted());
      System.out.println(executor.execute(new InsertPlan(table,
         Arrays.asList("id", "name", "age"),
         Arrays.asList(new Constant(2), new Constant("Bob"), new Constant(17))), tx).formatted());

      Predicate adult = new Predicate(new Term(new Expression("age"), ">",
         new Expression(new Constant(18))));
      LogicalPlan query = new simpledb.logical.ProjectPlan(
         new FilterPlan(new SeqScanPlan(table), adult),
         Arrays.asList("name", "age")
      );
      System.out.println(query.explain(0));
      System.out.println(executor.execute(query, tx).formatted());

      Predicate deleteAlice = new Predicate(new Term(new Expression("id"), new Expression(new Constant(1))));
      System.out.println(executor.execute(new DeletePlan(table, deleteAlice), tx).formatted());
      System.out.println(executor.execute(query, tx).formatted());

      tx.commit();
   }
}
