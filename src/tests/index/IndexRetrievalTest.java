package tests.index;

import java.util.Map;

import simpledb.index.Index;
import simpledb.metadata.*;
import simpledb.plan.*;
import simpledb.query.*;
import simpledb.record.RID;
import simpledb.server.SimpleDB;
import simpledb.tx.Transaction;
import tests.TestCleanup;

public class IndexRetrievalTest {
   public static void main(String[] args) {
        TestCleanup.init();
      SimpleDB.DB_BASE_DIR = "tmp";
      SimpleDB db = new SimpleDB("studentdb");
      Transaction tx = db.newTx();
      MetadataMgr mdm = db.mdMgr();

      // Open a scan on the data table.
      Plan studentplan = new TablePlan(tx, "student", mdm);
      UpdateScan studentscan = (UpdateScan) studentplan.open();

      // Open the index on MajorId.
      Map<String,IndexInfo> indexes = mdm.getIndexInfo("student", tx);
      IndexInfo ii = indexes.get("majorid");
      Index idx = ii.open();

      // Retrieve all index records having a dataval of 20.
      idx.beforeFirst(new Constant(20));
      while (idx.next()) {
         // Use the datarid to go to the corresponding STUDENT record.
         RID datarid = idx.getDataRid();
         studentscan.moveToRid(datarid);
         System.out.println(studentscan.getString("sname"));
      }

      // Close the index and the data table.
      idx.close();
      studentscan.close();
      tx.commit();
   }
}
