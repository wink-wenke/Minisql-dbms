package simpledb.engine;

import java.util.*;
import simpledb.logical.*;
import simpledb.metadata.MetadataMgr;
import simpledb.plan.Plan;
import simpledb.query.*;
import simpledb.shared.*;
import simpledb.tx.Transaction;

/**
 * Executes logical plans and returns a unified engine result.
 *
 * The engine talks to its collaborators only through the narrow interfaces
 * CatalogReader, CatalogWriter, PlanConverter and StorageEngine. That keeps
 * page-level types out of this class, and lets a test drive the whole
 * dispatcher with stubs instead of a real database.
 */
public class ExecutorImpl implements Executor {
   private final CatalogReader catalog;
   private final CatalogWriter writer;
   private final PlanConverter converter;
   private final StorageEngine storageEngine;

   /**
    * Production wiring: a single MetadataMgr plays both catalog roles, and
    * the default converter and storage engine are built on top of it.
    */
   public ExecutorImpl(MetadataMgr metadataMgr) {
      this(metadataMgr, metadataMgr, new PlanConverterImpl(metadataMgr),
            new StorageEngineImpl(metadataMgr));
   }

   /**
    * Test-friendly wiring: every collaborator can be replaced.
    */
   public ExecutorImpl(CatalogReader catalog, CatalogWriter writer,
                       PlanConverter converter, StorageEngine storageEngine) {
      this.catalog = catalog;
      this.writer = writer;
      this.converter = converter;
      this.storageEngine = storageEngine;
   }

   public ExecuteResult execute(LogicalPlan plan, Transaction tx) {
      if (plan instanceof CreateTablePlan)
         return executeCreateTable((CreateTablePlan) plan, tx);
      if (plan instanceof InsertPlan)
         return executeInsert((InsertPlan) plan, tx);
      if (plan instanceof DeletePlan)
         return executeDelete((DeletePlan) plan, tx);
      return executeQuery(plan, tx);
   }

   private ExecuteResult executeCreateTable(CreateTablePlan plan, Transaction tx) {
      if (catalog.tableExists(plan.tableName(), tx))
         throw EngineException.tableExists(plan.tableName());
      if (plan.columns().isEmpty())
         throw EngineException.emptySchema(plan.tableName());

      writer.createTable(plan.tableName(), plan.columns(), tx);
      return ExecuteResult.updateResult(0);
   }

   private ExecuteResult executeInsert(InsertPlan plan, Transaction tx) {
      requireTable(plan.tableName(), tx);
      if (plan.columns().size() != plan.values().size())
         throw EngineException.arityMismatch(plan.tableName(),
               plan.columns().size(), plan.values().size());
      for (String column : plan.columns())
         if (!catalog.columnExists(plan.tableName(), column, tx))
            throw EngineException.columnNotFound(plan.tableName(), column);

      String[] columns = plan.columns().toArray(new String[0]);
      Constant[] values = plan.values().toArray(new Constant[0]);
      storageEngine.insertRow(plan.tableName(), columns, values, tx);
      return ExecuteResult.updateResult(1);
   }

   private ExecuteResult executeDelete(DeletePlan plan, Transaction tx) {
      requireTable(plan.tableName(), tx);
      int affected = storageEngine.deleteRows(plan.tableName(), plan.predicate(), tx);
      return ExecuteResult.updateResult(affected);
   }

   private ExecuteResult executeQuery(LogicalPlan logicalPlan, Transaction tx) {
      Plan physicalPlan = converter.convert(logicalPlan, tx);
      Scan scan = physicalPlan.open();
      List<String> columns = physicalPlan.schema().fields();
      List<List<Constant>> rows = new ArrayList<>();
      try {
         while (scan.next()) {
            List<Constant> row = new ArrayList<>();
            for (String column : columns)
               row.add(scan.getVal(column));
            rows.add(row);
         }
         return ExecuteResult.queryResult(new ArrayList<>(columns), rows);
      }
      finally {
         scan.close();
      }
   }

   /**
    * Guards every statement that touches an existing table. Without this the
    * failure surfaces much later as a null layout inside TableScan, which is
    * impossible to attribute to a stage during integration.
    */
   private void requireTable(String tableName, Transaction tx) {
      if (!catalog.tableExists(tableName, tx))
         throw EngineException.tableNotFound(tableName);
   }
}
