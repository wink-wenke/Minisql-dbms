package simpledb.buffer;

import simpledb.file.*;
import simpledb.log.LogMgr;
import simpledb.storage.CacheStats;
import simpledb.storage.ReplacementPolicy;

//缓冲区管理器，负责管理缓冲区池，提供缓冲区的分配和回收功能
//决定哪个块进内存，哪个页被淘汰，何时刷盘
public class BufferMgr {
   private Buffer[] bufferpool;
   private int numAvailable;
   private static final long MAX_TIME = 10000; // 10 seconds
   private ReplacementPolicy policy = ReplacementPolicy.LRU;
   private CacheStats stats = new CacheStats();
   private static boolean debug = true;
   

   //构造器，分配缓冲区
   public BufferMgr(FileMgr fm, LogMgr lm, int numbuffs) {
      bufferpool = new Buffer[numbuffs];//创建缓冲区数组
      numAvailable = numbuffs;//初始化可用缓冲区数量,全部可用
      for (int i=0; i<numbuffs; i++)
         bufferpool[i] = new Buffer(fm, lm); //每一个槽位初始化一个buffer
   }
   
   public synchronized int available() {
      return numAvailable;
   }
   

   public synchronized void flushAll(int txnum) {
      for (Buffer buff : bufferpool)
         if (buff.modifyingTx() == txnum)
         buff.flush();
   }

   public synchronized void flushAllDirty() {
      for (Buffer buff : bufferpool)
         buff.flush();
   }
   

   public synchronized void unpin(Buffer buff) {
      buff.unpin();
      if (!buff.isPinned()) {
         numAvailable++;
         notifyAll();
      }
   }
   
   
   public synchronized Buffer pin(BlockId blk) {
      try {
         long timestamp = System.currentTimeMillis();
         Buffer buff = tryToPin(blk);
         //如果没有可用缓冲区，等待一段时间后再尝试获取缓冲区
         while (buff == null && !waitingTooLong(timestamp)) {
            wait(MAX_TIME); //等待一段时间后再尝试获取缓冲区
            buff = tryToPin(blk);//再次尝试获取缓冲区
         }
         if (buff == null)
            throw new BufferAbortException();//如果仍然没有可用缓冲区，抛出异常
         return buff;
      }
      catch(InterruptedException e) {
         throw new BufferAbortException();
      }
   }  
   
   public void setReplacementPolicy(ReplacementPolicy policy) {
      this.policy = policy;
   }

   public CacheStats getStats() {
      return stats;
   }

   public void flushPage(BlockId blk) {
      for (Buffer buff : bufferpool) {
         BlockId b = buff.block();
         if (b != null && b.equals(blk)) {
            buff.flush();
            if (debug)
               System.out.println("FLUSH " + blk);
            return;
         }
      }
   }

   public void unpinPage(BlockId blk) {
      for (Buffer buff : bufferpool) {
         BlockId b = buff.block();
         if (b != null && b.equals(blk)) {
            unpin(buff);
            return;
         }
      }
   }

   private boolean waitingTooLong(long starttime) {
      return System.currentTimeMillis() - starttime > MAX_TIME;
   }
   
   //尝试获取缓冲区，如果缓冲区已存在，则直接返回；如果不存在，则选择一个未被使用的缓冲区进行分配
   private Buffer tryToPin(BlockId blk) {
      Buffer buff = findExistingBuffer(blk);//查找缓冲区中是否已经存在该磁盘块
      if (buff != null) {
         stats.recordHit(); //缓存命中 +1
         if (debug)
            System.out.println("GET " + blk + " HIT");
      } else {
         stats.recordMiss(); //缓存未命中 +1
         buff = chooseUnpinnedBuffer(); //未命中，走淘汰策略
         if (buff == null)
            return null;
         buff.assignToBlock(blk);
         if (debug)
            System.out.println("GET " + blk + " MISS");
      }
      if (!buff.isPinned())
         numAvailable--;
      buff.pin();
      return buff;
   }
   
   //查找缓冲区中是否已经存在该磁盘块,遍历整个缓冲池来逐个比对
   private Buffer findExistingBuffer(BlockId blk) {
      for (Buffer buff : bufferpool) {
         BlockId b = buff.block();
         if (b != null && b.equals(blk))
            return buff;
      }
      return null;
   }
   
   //核心函数，选择一个未被使用的缓冲区进行分配
   private Buffer chooseUnpinnedBuffer() {
      Buffer victim = null;
      for (Buffer buff : bufferpool) {
         if (!buff.isPinned()) {
            if (victim == null) {
               victim = buff; //第一个空闲的作为候选
            } else if (policy == ReplacementPolicy.LRU) {
               if (buff.lastAccessTime() < victim.lastAccessTime())
                  victim = buff;//选择最后访问时间最早的作为候选 LRU算法
            } else { // FIFO
               if (buff.loadTime() < victim.loadTime())
                  victim = buff; //选择加载时间最早的作为候选 FIFO算法
            }
         }
      }
      if (victim != null) {
         stats.recordEviction(); //记录淘汰次数
         BlockId old = victim.block();
         if (debug)
            System.out.println("EVICT " + (old != null ? old : "null") + " policy=" + policy);
      }
      return victim;
   }
}
