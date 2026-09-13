package tests.mock;

import java.util.*;
import simpledb.engine.CatalogReader;
import simpledb.engine.CatalogWriter;
import simpledb.shared.ColumnDef;
import simpledb.tx.Transaction;

/**
 * In-memory system catalog for the engine unit tests.
 *
 * It deliberately ignores the transaction argument: these tests assert the
 * engine's own decisions, not how a catalog persists itself. Keeping the
 * catalog in a map also means a test can set up any schema in one line
 * instead of creating a database on disk first.
 */
public class MockCatalog implements CatalogReader, CatalogWriter {
   private final Map<String, List<ColumnDef>> tables = new LinkedHashMap<>();

   /** Test fixture helper: registers a table without going through CREATE. */
   public void define(String tableName, ColumnDef... columns) {
      tables.put(tableName, Arrays.asList(columns));
   }

   public void createTable(String tableName, List<ColumnDef> columns, Transaction tx) {
      tables.put(tableName, new ArrayList<>(columns));
   }

   public boolean tableExists(String tableName, Transaction tx) {
      return tables.containsKey(tableName);
   }

   public boolean columnExists(String tableName, String columnName, Transaction tx) {
      return getColumn(tableName, columnName, tx) != null;
   }

   public ColumnDef getColumn(String tableName, String columnName, Transaction tx) {
      List<ColumnDef> columns = tables.get(tableName);
      if (columns == null)
         return null;
      for (ColumnDef def : columns)
         if (def.name().equalsIgnoreCase(columnName))
            return def;
      return null;
   }

   public List<ColumnDef> getColumns(String tableName, Transaction tx) {
      List<ColumnDef> columns = tables.get(tableName);
      return columns == null ? Collections.<ColumnDef>emptyList() : columns;
   }

   public int tableCount() {
      return tables.size();
   }
}
