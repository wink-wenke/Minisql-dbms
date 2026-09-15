package simpledb.log;

import java.util.Iterator;
import simpledb.file.*;


//将日志记录追加到日志文件中，保证崩溃有有据可查
public class LogMgr {//核心思想WAL：所有修改先写日志再写数据，崩溃后靠日志恢复
   private FileMgr fm;//底层文件管理器
   private String logfile;//日志文件名
   private Page logpage;//日志页面
   private BlockId currentblk; //当前正在写入的日志块
   private int latestLSN = 0; //最后的日志序列号，是内存中的日志序列号
   private int lastSavedLSN = 0; //最后一次刷盘的LSN

   //初始化日志
   public LogMgr(FileMgr fm, String logfile) {
      this.fm = fm;
      this.logfile = logfile;
      byte[] b = new byte[fm.blockSize()];
      logpage = new Page(b); //分配日志页缓冲区
      int logsize = fm.length(logfile);
      if (logsize == 0)
         currentblk = appendNewBlock(); //新数据库，创建第一个日志块
      else {
         //已有日志文件，定位到最后一个块并都内存
         currentblk = new BlockId(logfile, logsize-1);
         fm.read(currentblk, logpage);
      }
   }

   public void flush(int lsn) {
      if (lsn >= lastSavedLSN)
         flush();
   }
   
   //返回日志迭代器，用于从当前日志块开始，向前遍历日志记录
   public Iterator<byte[]> iterator() {
      flush();
      return new LogIterator(fm, currentblk);
   }

   //把一条日志记录写入内存日志页中，空间不够就换新块。返回日志序列号
   public synchronized int append(byte[] logrec) {
      int boundary = logpage.getInt(0);   //读取当前日志页的边界位置
      int recsize = logrec.length;
      int bytesneeded = recsize + Integer.BYTES; //记录大小+长度前缀
      if (boundary - bytesneeded < Integer.BYTES) { //如果空间不够
         flush();        // 先把当前块刷盘
         currentblk = appendNewBlock(); //追加新块
         boundary = logpage.getInt(0); //新块的boundary
      }
      int recpos = boundary - bytesneeded;  //新记录的写入位置

      logpage.setBytes(recpos, logrec);  //写入记录
      logpage.setInt(0, recpos); // 更新boundary
      latestLSN += 1; //LSN自增
      return latestLSN; //返回日志序列号
   }


   private BlockId appendNewBlock() {
      BlockId blk = fm.append(logfile);     
      logpage.setInt(0, fm.blockSize());
      fm.write(blk, logpage);
      return blk;
   }


   //按照LSN刷盘
   private void flush() { //这里的刷盘操作遵循WAL协议，先刷日志再刷数据
      fm.write(currentblk, logpage); //把内存中的日志写入磁盘
      lastSavedLSN = latestLSN; //更新已保存的LSN
   }
}
