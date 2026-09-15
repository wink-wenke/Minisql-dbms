package simpledb.tx.recovery;

import java.util.*;
import simpledb.file.*;
import simpledb.log.*;
import simpledb.buffer.*;
import simpledb.tx.Transaction;
import static simpledb.tx.recovery.LogRecord.*;


public class RecoveryMgr {
   private LogMgr lm;
   private BufferMgr bm;
   private Transaction tx;
   private int txnum;


   public RecoveryMgr(Transaction tx, int txnum, LogMgr lm, BufferMgr bm) {
      this.tx = tx;
      this.txnum = txnum;
      this.lm = lm;
      this.bm = bm;
      StartRecord.writeToLog(lm, txnum);
   }

 
   public void commit() {
      bm.flushAll(txnum);
      int lsn = CommitRecord.writeToLog(lm, txnum);
      lm.flush(lsn);
   }


   public void rollback() {
      doRollback();
      bm.flushAll(txnum);
      int lsn = RollbackRecord.writeToLog(lm, txnum);
      lm.flush(lsn);
   }

   public void recover() {
      doRecover();
      bm.flushAll(txnum);
      int lsn = CheckpointRecord.writeToLog(lm);
      lm.flush(lsn);
   }


   public int setInt(Buffer buff, int offset, int newval) {
      int oldval = buff.contents().getInt(offset);
      BlockId blk = buff.block();
      return SetIntRecord.writeToLog(lm, txnum, blk, offset, oldval);
   }


   public int setString(Buffer buff, int offset, String newval) {
      String oldval = buff.contents().getString(offset);
      BlockId blk = buff.block();
      return SetStringRecord.writeToLog(lm, txnum, blk, offset, oldval);
   }

 
   private void doRollback() {
      Iterator<byte[]> iter = lm.iterator();
      while (iter.hasNext()) {
         byte[] bytes = iter.next();
         LogRecord rec = LogRecord.createLogRecord(bytes); 
         if (rec.txNumber() == txnum) {
            if (rec.op() == START)
               return;
            rec.undo(tx);
         }
      }
   }


   private void doRecover() {
      Collection<Integer> finishedTxs = new ArrayList<>();
      Iterator<byte[]> iter = lm.iterator();
      while (iter.hasNext()) {
         byte[] bytes = iter.next();
         LogRecord rec = LogRecord.createLogRecord(bytes);
         if (rec.op() == CHECKPOINT)
            return;
         if (rec.op() == COMMIT || rec.op() == ROLLBACK)
            finishedTxs.add(rec.txNumber());
         else if (!finishedTxs.contains(rec.txNumber()))
            rec.undo(tx);
      }
   }
}
