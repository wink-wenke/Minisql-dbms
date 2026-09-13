package simpledb.buffer;

import simpledb.file.*;
import simpledb.log.LogMgr;


//缓冲区的最小单元 buffer = page + 管理信息(哪个快，谁改的，引用计数，时间戳)
public class Buffer {
   private FileMgr fm;
   private LogMgr lm;
   //内存缓冲区，装磁盘块数据的地方
   private Page contents;
   //磁盘块的标识符
   private BlockId blk = null;
   //引用计数，当前有几个使用者正在使用这个缓冲区
   private int pins = 0;
   //事务号，表示哪个事务修改了这个缓冲区
   private int txnum = -1;
   //日志序列号，表示修改这个缓冲区的日志记录的序号
   private int lsn = -1;
   private long lastAccessTime = 0;  // 用于 LRU 替换策略
   private long loadTime = 0;        // 用于 FIFO 替换策略

   public Buffer(FileMgr fm, LogMgr lm) {
      this.fm = fm;
      this.lm = lm;
      contents = new Page(fm.blockSize());
   }
   
   public Page contents() {
      return contents;
   }

   public BlockId block() {
      return blk;
   }

   public void setModified(int txnum, int lsn) {
      this.txnum = txnum;
      if (lsn >= 0)
         this.lsn = lsn;
   }

   public boolean isPinned() {
      return pins > 0;
   }
   
   public int modifyingTx() {
      return txnum;
   }


   //分配磁盘块到缓冲区
   void assignToBlock(BlockId b) {
      //如果当前存在脏页，先刷盘
      flush();
      //更新关联的磁盘块
      blk = b;
      //从磁盘读入数据到缓冲区
      fm.read(blk, contents);
      //重置引用计数
      pins = 0;
      //FIFO替换策略需要记录加载时间，LRU替换策略需要记录最后访问时间
      loadTime = System.nanoTime();
      lastAccessTime = loadTime;
   }

   void flush() {
      if (txnum >= 0) { //如果当前缓冲区是脏页
         lm.flush(lsn); //先把日志刷盘（WAL协议）
         fm.write(blk, contents); //再把缓冲区写回磁盘
         txnum = -1; //重置事务号，表示当前缓冲区不再是脏页
      }
   }

   //引用计数
   void pin() {
      pins++;
      lastAccessTime = System.nanoTime();
   }

   void unpin() {
      pins--;
   }
   long lastAccessTime() {
      return lastAccessTime;
   }

   long loadTime() {
      return loadTime;
   }

   void setLoadTime(long time) {
      this.loadTime = time;
   }

}