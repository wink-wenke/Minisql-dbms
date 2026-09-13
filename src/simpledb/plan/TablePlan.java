package simpledb.plan;

import simpledb.tx.Transaction;
import simpledb.metadata.*;
import simpledb.query.Scan;
import simpledb.record.*;

/** The Plan class corresponding to a table.
  * @author Edward Sciore
  */
public class TablePlan implements Plan {
   private String tblname;
   private Transaction tx;
   private Layout layout;
   private StatInfo si;
   
   public TablePlan(Transaction tx, String tblname, MetadataMgr md) {
      this.tblname = tblname;
      this.tx = tx;
      layout = md.getLayout(tblname, tx);
      si = md.getStatInfo(tblname, layout, tx);
   }
   
   public Scan open() {
      return new TableScan(tx, tblname, layout);
   }
   
   public int blocksAccessed() {
      return si.blocksAccessed();
   }
   
   public int recordsOutput() {
      return si.recordsOutput();
   }
   
   public int distinctValues(String fldname) {
      return si.distinctValues(fldname);
   }
   
   public Schema schema() {
      return layout.schema();
   }

   /** 返回表名（供优化器/可视化使用）。 */
   public String tableName() { return tblname; }
}
