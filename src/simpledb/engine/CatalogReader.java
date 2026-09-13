package simpledb.engine;

import java.util.List;
import simpledb.shared.ColumnDef;
import simpledb.tx.Transaction;

/**
 * Read-only catalog view exposed to compiler and semantic analysis.
 *
 * Every lookup carries the caller's transaction, because the catalog tables
 * tblcat / fldcat are ordinary paged tables: reading them pins buffers and
 * therefore has to happen inside a live transaction.
 */
public interface CatalogReader {
   boolean tableExists(String tableName, Transaction tx);

   boolean columnExists(String tableName, String columnName, Transaction tx);

   ColumnDef getColumn(String tableName, String columnName, Transaction tx);

   List<ColumnDef> getColumns(String tableName, Transaction tx);
}
