package simpledb.engine;

import java.util.List;
import simpledb.shared.ColumnDef;
import simpledb.tx.Transaction;

/**
 * Write side of the system catalog.
 *
 * It is kept separate from {@link CatalogReader} so that a caller which only
 * needs to look tables up never gets the right to change them, and so tests
 * can stub the two halves independently.
 *
 * Column definitions are expressed with ColumnDef rather than
 * simpledb.record.Schema on purpose: the engine layer should not have to
 * know how SimpleDB lays a record out inside a page.
 */
public interface CatalogWriter {
   void createTable(String tableName, List<ColumnDef> columns, Transaction tx);
}
