package tests;

import java.util.*;
import simpledb.engine.*;
import simpledb.file.BlockId;
import simpledb.logical.*;
import simpledb.metadata.MetadataMgr;
import simpledb.query.*;
import simpledb.record.*;
import simpledb.server.SimpleDB;
import simpledb.shared.*;
import simpledb.tx.Transaction;

/**
 * Guided tour of the layers a statement passes through on its way to disk.
 *
 *   java -cp build tests.StorageWalkthrough
 *
 * It answers, on a real database rather than in prose:
 *   - how the catalog stores the catalog tables
 *   - what byte offsets a Layout assigns to each field
 *   - how a slot records whether it is in use
 *   - what DELETE actually changes, and whether a freed slot is reused
 */
public class StorageWalkthrough {
   private static final String DB = "walkthroughdb";
   private static final String TABLE = "student";

   private static final List<ColumnDef> COLUMNS = Arrays.asList(
      new ColumnDef("id", ColumnType.INTEGER, 0),
      new ColumnDef("name", ColumnType.VARCHAR, 12),
      new ColumnDef("age", ColumnType.INTEGER, 0));

   public static void main(String[] args) {
      TestBase.resetDatabase(DB);
      SimpleDB db = new SimpleDB(DB);
      Transaction tx = db.newTx();
      MetadataMgr mdm = db.mdMgr();
      Executor executor = new ExecutorImpl(mdm);

      section("1. CREATE TABLE 之后，系统目录里多了什么");
      executor.execute(new CreateTablePlan(TABLE, COLUMNS), tx);
      System.out.println("tblcat（每个表一行）:");
      dumpTable(tx, mdm, "tblcat");
      System.out.println("fldcat（每个字段一行）:");
      dumpTable(tx, mdm, "fldcat");

      section("2. Layout：字段名到字节偏移的映射");
      Layout layout = mdm.getLayout(TABLE, tx);
      for (String fldname : layout.schema().fields())
         System.out.println("   " + fldname + " -> 偏移 " + layout.offset(fldname));
      System.out.println("   槽大小 = " + layout.slotSize() + " 字节"
         + "，块大小 = " + tx.blockSize() + " 字节"
         + "，每块可放 " + (tx.blockSize() / layout.slotSize()) + " 个槽");

      section("3. 插入三行后，逐槽查看");
      insert(executor, tx, 1, "Alice", 20);
      insert(executor, tx, 2, "Bob", 17);
      insert(executor, tx, 3, "Carol", 22);
      dumpSlots(tx, mdm, TABLE);

      section("4. 删掉 id=2，再看槽");
      System.out.println("   受影响行数 = "
         + executor.execute(new DeletePlan(TABLE, eq("id", 2)), tx).getAffectedRows());
      System.out.println("   注意 flag 变了，但字节还在原地：");
      dumpSlots(tx, mdm, TABLE);

      section("5. 再插一行，观察槽是否被复用");
      insert(executor, tx, 4, "Dave", 19);
      dumpSlots(tx, mdm, TABLE);

      section("6. 查询看到的最终状态");
      System.out.print(executor.execute(new SeqScanPlan(TABLE, COLUMNS), tx).formatted());

      tx.commit();
      TestBase.resetDatabase(DB);
   }

   private static void dumpTable(Transaction tx, MetadataMgr mdm, String table) {
      Layout layout = mdm.getLayout(table, tx);
      TableScan ts = new TableScan(tx, table, layout);
      try {
         while (ts.next()) {
            StringBuilder sb = new StringBuilder("   ");
            for (String fldname : layout.schema().fields())
               sb.append(fldname).append("=").append(ts.getVal(fldname)).append("  ");
            System.out.println(sb);
         }
      }
      finally {
         ts.close();
      }
   }

   /**
    * Reads the slot flags straight out of the page. The flag sits at the
    * very start of a slot, which is exactly why Layout reserves its first
    * four bytes.
    */
   private static void dumpSlots(Transaction tx, MetadataMgr mdm, String table) {
      Layout layout = mdm.getLayout(table, tx);
      String filename = table + ".tbl";
      for (int blknum = 0; blknum < tx.size(filename); blknum++) {
         BlockId blk = new BlockId(filename, blknum);
         tx.pin(blk);
         try {
            for (int slot = 0; (slot + 1) * layout.slotSize() <= tx.blockSize(); slot++) {
               int base = slot * layout.slotSize();
               int flag = tx.getInt(blk, base);
               StringBuilder sb = new StringBuilder("   块" + blknum + " 槽" + slot
                  + "  flag=" + flag);
               for (String fldname : layout.schema().fields())
                  sb.append("  ").append(fldname).append("=")
                    .append(read(tx, blk, layout, base, fldname));
               if (flag == 0)
                  sb.append("   <- 空闲（删除只改标记，旧数据仍在）");
               System.out.println(sb);
            }
         }
         finally {
            tx.unpin(blk);
         }
      }
   }

   private static Object read(Transaction tx, BlockId blk, Layout layout, int base, String fldname) {
      int pos = base + layout.offset(fldname);
      if (layout.schema().type(fldname) == java.sql.Types.INTEGER)
         return tx.getInt(blk, pos);
      return tx.getString(blk, pos);
   }

   private static void insert(Executor executor, Transaction tx, int id, String name, int age) {
      executor.execute(new InsertPlan(TABLE, Arrays.asList("id", "name", "age"),
         Arrays.asList(new Constant(id), new Constant(name), new Constant(age))), tx);
   }

   private static Predicate eq(String column, int value) {
      return new Predicate(new Term(new Expression(column), "=",
         new Expression(new Constant(value))));
   }

   private static void section(String title) {
      System.out.println();
      System.out.println("===== " + title + " =====");
   }
}
