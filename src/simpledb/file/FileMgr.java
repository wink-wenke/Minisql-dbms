package simpledb.file;

import java.io.*;
import java.util.*;

public class FileMgr {
   private File dbDirectory;
   private int blocksize; //块大小 4KB
   private boolean isNew;
   private Map<String,RandomAccessFile> openFiles = new HashMap<>();
   /** 每个文件的空闲页号集合（TreeSet 保证复用最小页号） */
   private final Map<String, TreeSet<Integer>> freePages = new HashMap<>();

   //负责真正的磁盘IO，在把内存中的page和磁盘上的block进行读写操作
   public FileMgr(File dbDirectory, int blocksize) {
      this.dbDirectory = dbDirectory;
      this.blocksize = blocksize;
      isNew = !dbDirectory.exists();

      //记录数据库目录，判断是否新建
      if (isNew)
         dbDirectory.mkdirs();

      //清理所有temp开头的临时文件
      for (String filename : dbDirectory.list())
         if (filename.startsWith("temp"))
         		new File(dbDirectory, filename).delete();
   }
   

   //把磁盘块读到page中，page是一个内存页，block是磁盘块
   public synchronized void read(BlockId blk, Page p) {
      try {
         RandomAccessFile f = getFile(blk.fileName());
         //核心操作的寻址方式，定位到目标块
         //块号*块大小=偏移量
         f.seek(blk.number() * blocksize);
         //通过NIO的channel把磁盘块读到page中
         f.getChannel().read(p.contents());
      }
      catch (IOException e) {
         throw new RuntimeException("cannot read block " + blk);
      }
   }

   //写内存中page的数据写到磁盘块
   public synchronized void write(BlockId blk, Page p) {
      try {
         RandomAccessFile f = getFile(blk.fileName());
         f.seek(blk.number() * blocksize);
         f.getChannel().write(p.contents());
      }
      catch (IOException e) {
         throw new RuntimeException("cannot write block" + blk);
      }
   }


   //追加新块到文件末尾   操作磁盘文件，由于磁盘文件存储是动态分配的
   public synchronized BlockId append(String filename) {
      int newblknum = length(filename); //算出当前文件有多少块
      BlockId blk = new BlockId(filename, newblknum);
      byte[] b = new byte[blocksize];
      try {
         RandomAccessFile f = getFile(blk.fileName());
         f.seek(blk.number() * blocksize); //定位到文件末尾
         f.write(b); //把全0数据写入磁盘
      }
      catch (IOException e) {
         throw new RuntimeException("cannot append block" + blk);
      }
      return blk;
   }

   //返回文件的块数
   public int length(String filename) {
      try {
         RandomAccessFile f = getFile(filename);
         return (int)(f.length() / blocksize);
      }
      catch (IOException e) {
         throw new RuntimeException("cannot access " + filename);
      }
   }

   public boolean isNew() {
      return isNew;
   }

   public int blockSize() {
      return blocksize;
   }

   //页面分配
   public synchronized PageId allocatePage(String fileName) {
      TreeSet<Integer> free = freePages.get(fileName); //优先从freepage复用已释放
      if (free != null && !free.isEmpty()) { //如果有
         int pageNum = free.first(); //取最小的空闲页号
         free.remove(pageNum);  //从空闲列表中移除
         byte[] zeros = new byte[blocksize];
         BlockId blk = new BlockId(fileName, pageNum);
         Page p = new Page(zeros);
         write(blk, p); //释放之后清零写入磁盘
         return new PageId(fileName, pageNum);
      }
      //如果没有空闲块可以复用，在文件末尾追加新块
      BlockId blk = append(fileName);
      return new PageId(blk.fileName(), blk.number());
   }

   //页释放
   public synchronized void freePage(String fileName, int pageNum) {
      byte[] zeros = new byte[blocksize];
      BlockId blk = new BlockId(fileName, pageNum);
      Page p = new Page(zeros);
      write(blk, p); //空闲页清零
      freePages.computeIfAbsent(fileName, k -> new TreeSet<>()).add(pageNum);
   }

   /**
    * 返回指定文件的空闲页数量。
    */
   public synchronized int freePageCount(String fileName) {
      TreeSet<Integer> free = freePages.get(fileName);
      return free == null ? 0 : free.size();
   }

   //文件句柄缓存器--保证同一个文件只打开一次，后续调用直接复用
   private RandomAccessFile getFile(String filename) throws IOException {
      RandomAccessFile f = openFiles.get(filename);
      if (f == null) {
         File dbTable = new File(dbDirectory, filename);
         f = new RandomAccessFile(dbTable, "rws");
         openFiles.put(filename, f);
      }
      return f;
   }
}
