package simpledb.engine;

import java.util.List;
import simpledb.shared.ColumnDef;

/**
 * Read-only catalog view exposed to compiler and semantic analysis.
 */
public interface CatalogReader {
   boolean tableExists(String tableName);

   boolean columnExists(String tableName, String columnName);

   ColumnDef getColumn(String tableName, String columnName);

   List<ColumnDef> getColumns(String tableName);
}
