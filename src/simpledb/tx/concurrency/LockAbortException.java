package simpledb.tx.concurrency;

//超时报异常，不再循环等待尝试获取锁
@SuppressWarnings("serial")
public class LockAbortException extends RuntimeException {
   public LockAbortException() {
   }
}
