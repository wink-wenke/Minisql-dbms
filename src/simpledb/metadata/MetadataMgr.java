package simpledb.metadata;

import java.util.*;
import simpledb.tx.Transaction;
import simpledb.record.*;
import simpledb.engine.CatalogReader;
import simpledb.shared.ColumnDef;

public class MetadataMgr implements CatalogReader {
   private static TableMgr  tblmgr;
   private static ViewMgr   viewmgr;
   private static StatMgr   statmgr;
   private static IndexMgr  idxmgr;
   private static Transaction catalogTx;
   
   public MetadataMgr(boolean isnew, Transaction tx) {
      catalogTx = tx;
      tblmgr  = new TableMgr(isnew, tx);
      viewmgr = new ViewMgr(isnew, tblmgr, tx);
      statmgr = new StatMgr(tblmgr, tx);
      idxmgr  = new IndexMgr(isnew, tblmgr, statmgr, tx);
   }
   
   public void createTable(String tblname, Schema sch, Transaction tx) {
      tblmgr.createTable(tblname, sch, tx);
   }
   
   public Layout getLayout(String tblname, Transaction tx) {
      return tblmgr.getLayout(tblname, tx);
   }
   
   public void createView(String viewname, String viewdef, Transaction tx) {
      viewmgr.createView(viewname, viewdef, tx);
   }
   
   public String getViewDef(String viewname, Transaction tx) {
      return viewmgr.getViewDef(viewname, tx);
   }
   
   public void createIndex(String idxname, String tblname, String fldname, Transaction tx) {
      idxmgr.createIndex(idxname, tblname, fldname, tx);
   }
   
   public Map<String,IndexInfo> getIndexInfo(String tblname, Transaction tx) {
      return idxmgr.getIndexInfo(tblname, tx);
   }
   
   public StatInfo getStatInfo(String tblname, Layout layout, Transaction tx) {
      return statmgr.getStatInfo(tblname, layout, tx);
   }

   public boolean tableExists(String tableName) {
      return tblmgr.tableExists(tableName, catalogTx);
   }

   public boolean columnExists(String tableName, String columnName) {
      return tblmgr.columnExists(tableName, columnName, catalogTx);
   }

   public ColumnDef getColumn(String tableName, String columnName) {
      return tblmgr.getColumn(tableName, columnName, catalogTx);
   }

   public List<ColumnDef> getColumns(String tableName) {
      return tblmgr.getColumns(tableName, catalogTx);
   }
}
