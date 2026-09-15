package simpledb.tx.concurrency;

import java.util.*;
import simpledb.file.BlockId;

//注意，此处的锁实现基本都是synchronized的，意味着在同一时刻只有一个线程可以访问锁表，这样可以避免并发访问导致的锁状态不一致问题。
class LockTable {
   //十秒超时
   private static final long MAX_TIME = 10000; // 10 seconds
   //全局锁表，这里的locks记录每个blockId的锁状态，正数表示共享锁的数量，负数表示排他锁
   private Map<BlockId,Integer> locks = new HashMap<BlockId,Integer>();
   
   //请求共享锁
   public synchronized void sLock(BlockId blk) {
      try {
         long timestamp = System.currentTimeMillis();
         while (hasXlock(blk) && !waitingTooLong(timestamp))
            wait(MAX_TIME);  //如果有排他锁，则等待
         if (hasXlock(blk))
            throw new LockAbortException(); //如果等待时间过长，则抛出异常
         int val = getLockVal(blk);  //获取当前锁的值
         locks.put(blk, val+1); //增加共享锁的数量
      }
      catch(InterruptedException e) {
         throw new LockAbortException();
      }
   }
   
   //请求排他锁
   synchronized void xLock(BlockId blk) {
      try {
         long timestamp = System.currentTimeMillis();
         while (hasOtherSLocks(blk) && !waitingTooLong(timestamp))
            wait(MAX_TIME); //如果有其他共享锁，则等待
         if (hasOtherSLocks(blk))
            throw new LockAbortException();
         locks.put(blk, -1); //设置为排他锁
      }
      catch(InterruptedException e) {
         throw new LockAbortException();
      }
   }
   
   //释放锁
   synchronized void unlock(BlockId blk) {
      int val = getLockVal(blk);
      if (val > 1)
         locks.put(blk, val-1);
      else {
         locks.remove(blk);
         notifyAll();
      }
   }
   
   private boolean hasXlock(BlockId blk) {
      return getLockVal(blk) < 0;
   }
   
   private boolean hasOtherSLocks(BlockId blk) {
      return getLockVal(blk) > 1;
   }
   
   private boolean waitingTooLong(long starttime) {
      return System.currentTimeMillis() - starttime > MAX_TIME;
   }
   
   private int getLockVal(BlockId blk) {
      Integer ival = locks.get(blk);
      return (ival == null) ? 0 : ival.intValue();
   }
}
