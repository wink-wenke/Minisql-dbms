package simpledb.metadata;

import java.util.*;
import simpledb.tx.Transaction;
import simpledb.record.*;
import simpledb.engine.CatalogReader;
import simpledb.engine.CatalogWriter;
import simpledb.shared.ColumnDef;
import simpledb.shared.ColumnType;

public class MetadataMgr implements CatalogReader, CatalogWriter {
   private TableMgr  tblmgr;
   private ViewMgr   viewmgr;
   private StatMgr   statmgr;
   private IndexMgr  idxmgr;

   public MetadataMgr(boolean isnew, Transaction tx) {
      tblmgr  = new TableMgr(isnew, tx);
      viewmgr = new ViewMgr(isnew, tblmgr, tx);
      statmgr = new StatMgr(tblmgr, tx);
      idxmgr  = new IndexMgr(isnew, tblmgr, statmgr, tx);
   }
   
   public void createTable(String tblname, Schema sch, Transaction tx) {
      tblmgr.createTable(tblname, sch, tx);
   }

   /**
    * CatalogWriter entry point. Translating ColumnDef into a Schema belongs
    * here, next to the catalog, instead of leaking page-level types into the
    * execution engine.
    */
   public void createTable(String tableName, List<ColumnDef> columns, Transaction tx) {
      Schema schema = new Schema();
      for (ColumnDef column : columns) {
         if (column.type() == ColumnType.INTEGER)
            schema.addIntField(column.name());
         else
            schema.addStringField(column.name(), column.length());
      }
      createTable(tableName, schema, tx);
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

   public boolean tableExists(String tableName, Transaction tx) {
      return tblmgr.tableExists(tableName, tx);
   }

   public boolean columnExists(String tableName, String columnName, Transaction tx) {
      return tblmgr.columnExists(tableName, columnName, tx);
   }

   public ColumnDef getColumn(String tableName, String columnName, Transaction tx) {
      return tblmgr.getColumn(tableName, columnName, tx);
   }

   public List<ColumnDef> getColumns(String tableName, Transaction tx) {
      return tblmgr.getColumns(tableName, tx);
   }
}
