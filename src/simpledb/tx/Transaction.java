package simpledb.tx;

import simpledb.file.*;
import simpledb.log.LogMgr;
import simpledb.buffer.*;
import simpledb.tx.recovery.*;
import simpledb.tx.concurrency.ConcurrencyMgr;

//协调所有组件，上层用户直接调用的方法
public class Transaction {
   private static int nextTxNum = 0;
   private static final int END_OF_FILE = -1;
   private RecoveryMgr    recoveryMgr;
   private ConcurrencyMgr concurMgr;
   private BufferMgr bm;
   private FileMgr fm;
   private int txnum;
   private BufferList mybuffers;
   

   public Transaction(FileMgr fm, LogMgr lm, BufferMgr bm) {
      this.fm = fm;
      this.bm = bm;
      txnum       = nextTxNumber();  //分配事务号
      recoveryMgr = new RecoveryMgr(this, txnum, lm, bm); //恢复管理器
      concurMgr   = new ConcurrencyMgr(); //并发管理器
      mybuffers = new BufferList(bm);   //事务自己的缓冲区列表
   }
   
   //提交事务
   public void commit() {
      recoveryMgr.commit();
      System.out.println("transaction " + txnum + " committed");
      concurMgr.release();
      mybuffers.unpinAll();
   }
   
   //事务回滚
   public void rollback() {
      recoveryMgr.rollback();
      System.out.println("transaction " + txnum + " rolled back");
      concurMgr.release();
      mybuffers.unpinAll();
   }
   
   //系统恢复
   public void recover() {
      bm.flushAll(txnum);
      recoveryMgr.recover();
   }
   
   //管理缓冲区固定
   public void pin(BlockId blk) {
      mybuffers.pin(blk);
   }
   
   //解除固定
   public void unpin(BlockId blk) {
      mybuffers.unpin(blk);
   }
   
   //读操作（需要S锁）
   public int getInt(BlockId blk, int offset) {
      concurMgr.sLock(blk);  //加共享锁
      Buffer buff = mybuffers.getBuffer(blk);  //从缓冲池中取数据
      return buff.contents().getInt(offset);  //读内存，不碰磁盘
   }
   
   //读操作
   public String getString(BlockId blk, int offset) {
      concurMgr.sLock(blk); 
      Buffer buff = mybuffers.getBuffer(blk);
      return buff.contents().getString(offset);
   }
   
   //写操作（需要X锁）
   public void setInt(BlockId blk, int offset, int val, boolean okToLog) {
      concurMgr.xLock(blk);  //加排他锁
      Buffer buff = mybuffers.getBuffer(blk); //取缓冲区
      int lsn = -1;
      if (okToLog)
         lsn = recoveryMgr.setInt(buff, offset, val); //先写日志
      Page p = buff.contents();
      p.setInt(offset, val);
      buff.setModified(txnum, lsn);
   }
   
   //写操作
   public void setString(BlockId blk, int offset, String val, boolean okToLog) {
      concurMgr.xLock(blk);
      Buffer buff = mybuffers.getBuffer(blk);
      int lsn = -1;
      if (okToLog)
         lsn = recoveryMgr.setString(buff, offset, val);
      Page p = buff.contents();
      p.setString(offset, val);
      buff.setModified(txnum, lsn);
   }


   public int size(String filename) {
      BlockId dummyblk = new BlockId(filename, END_OF_FILE);
      concurMgr.sLock(dummyblk);
      return fm.length(filename);
   }
   

   public BlockId append(String filename) {
      BlockId dummyblk = new BlockId(filename, END_OF_FILE);
      concurMgr.xLock(dummyblk);
      return fm.append(filename);
   }
   
   public int blockSize() {
      return fm.blockSize();
   }
   
   public int availableBuffs() {
      return bm.available();
   }
   
   private static synchronized int nextTxNumber() {
      nextTxNum++;
      return nextTxNum;
   }
}
