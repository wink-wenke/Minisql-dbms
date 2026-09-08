package simpledb.engine;

import java.util.*;
import simpledb.logical.*;
import simpledb.metadata.MetadataMgr;
import simpledb.plan.Plan;
import simpledb.query.*;
import simpledb.record.Schema;
import simpledb.shared.*;
import simpledb.tx.Transaction;

/**
 * Executes logical plans and returns a unified engine result.
 */
public class ExecutorImpl implements Executor {
   private MetadataMgr metadataMgr;
   private PlanConverter converter;
   private StorageEngine storageEngine;

   public ExecutorImpl(MetadataMgr metadataMgr) {
      this.metadataMgr = metadataMgr;
      this.converter = new PlanConverterImpl(metadataMgr);
      this.storageEngine = new StorageEngineImpl(metadataMgr);
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
      Schema schema = new Schema();
      for (ColumnDef column : plan.columns()) {
         if (column.type() == ColumnType.INTEGER)
            schema.addIntField(column.name());
         else
            schema.addStringField(column.name(), column.length());
      }
      metadataMgr.createTable(plan.tableName(), schema, tx);
      return ExecuteResult.updateResult(0);
   }

   private ExecuteResult executeInsert(InsertPlan plan, Transaction tx) {
      String[] columns = plan.columns().toArray(new String[0]);
      Constant[] values = plan.values().toArray(new Constant[0]);
      storageEngine.insertRow(plan.tableName(), columns, values, tx);
      return ExecuteResult.updateResult(1);
   }

   private ExecuteResult executeDelete(DeletePlan plan, Transaction tx) {
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
}
