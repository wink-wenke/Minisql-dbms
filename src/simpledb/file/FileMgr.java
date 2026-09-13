package simpledb.file;

import java.io.*;
import java.util.*;

public class FileMgr {
   private File dbDirectory;
   private int blocksize; //块大小 4KB
   private boolean isNew;
   private Map<String,RandomAccessFile> openFiles = new HashMap<>();

   //负责真正的磁盘IO，在把内存中的page和磁盘上的block进行读写操作
   public FileMgr(File dbDirectory, int blocksize) {
      this.dbDirectory = dbDirectory;
      this.blocksize = blocksize;
      isNew = !dbDirectory.exists();

      //记录数据库目录，判断是否新建
      if (isNew)
         dbDirectory.mkdirs();

      // remove any leftover temporary tables
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


   //追加新块到文件末尾
   public synchronized BlockId append(String filename) {
      int newblknum = length(filename);
      BlockId blk = new BlockId(filename, newblknum);
      byte[] b = new byte[blocksize];
      try {
         RandomAccessFile f = getFile(blk.fileName());
         f.seek(blk.number() * blocksize);
         f.write(b);
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
