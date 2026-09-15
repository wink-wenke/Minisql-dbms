package simpledb.tx.concurrency;

import java.util.*;
import simpledb.file.BlockId;


public class ConcurrencyMgr {

 
   private static LockTable locktbl = new LockTable();
   //利用哈希表来跟踪当前事务所持有的锁类型
   private Map<BlockId,String> locks  = new HashMap<BlockId,String>();

   //请求共享锁
   public void sLock(BlockId blk) {
      if (locks.get(blk) == null) {
         locktbl.sLock(blk);
         locks.put(blk, "S");
      }
   }

   //请求排他锁
   public void xLock(BlockId blk) {
      if (!hasXLock(blk)) {
         sLock(blk);
         locktbl.xLock(blk);
         locks.put(blk, "X");
      }
   }

   //释放锁有锁
   public void release() {
      for (BlockId blk : locks.keySet()) 
         locktbl.unlock(blk);
      locks.clear();
   }

   //检查是否有排他锁
   private boolean hasXLock(BlockId blk) {
      String locktype = locks.get(blk);
      return locktype != null && locktype.equals("X");
   }
}
