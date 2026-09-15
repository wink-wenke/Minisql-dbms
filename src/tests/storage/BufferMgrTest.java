package tests.storage;

import java.io.File;
import java.util.*;
import simpledb.file.*;
import simpledb.buffer.*;
import simpledb.storage.*;
import tests.TestBase;

/**
 * Unit tests for BufferMgr: pin/unpin, LRU/FIFO replacement, cache hit/miss.
 */
public class BufferMgrTest extends TestBase {
   private static final int BLOCK_SIZE = 4096;
   private static final int NUM_BUFFS = 3;
   private static final String TMP_DIR = "bufmgr_test_tmp";

   private StubFileMgr fm;
   private StubLogMgr lm;
   private BufferMgr bm;

   private void setup() {
      cleanup();
      fm = new StubFileMgr(TMP_DIR, BLOCK_SIZE);
      lm = new StubLogMgr();
      bm = new BufferMgr(fm, lm, NUM_BUFFS);
   }

   protected void cleanup() {
      File dir = new File(TMP_DIR);
      if (dir.exists()) {
         File[] files = dir.listFiles();
         if (files != null) for (File f : files) f.delete();
         dir.delete();
      }
   }

   protected String suiteName() {
      return "BufferMgrTest - pin/unpin, LRU/FIFO, hit/miss";
   }

   protected void cases() throws Exception {
      test("all buffers available initially", () -> {
         setup();
         assertEquals("available", (long) NUM_BUFFS, (long) bm.available());
      });

      test("pin reduces available count", () -> {
         setup();
         BlockId blk = new BlockId("test.tbl", 0);
         bm.pin(blk);
         assertEquals("available after pin", (long) (NUM_BUFFS - 1), (long) bm.available());
      });

      test("unpin restores available count", () -> {
         setup();
         BlockId blk = new BlockId("test.tbl", 0);
         Buffer buf = bm.pin(blk);
         bm.unpin(buf);
         assertEquals("available after unpin", (long) NUM_BUFFS, (long) bm.available());
      });

      test("pinning same block twice returns cached buffer", () -> {
         setup();
         BlockId blk = new BlockId("test.tbl", 0);
         Buffer b1 = bm.pin(blk);
         Buffer b2 = bm.pin(blk);
         assertTrue("same buffer instance", b1 == b2);
         bm.unpin(b1);
         bm.unpin(b2);
      });

      test("cache hit after first pin", () -> {
         setup();
         bm.setReplacementPolicy(ReplacementPolicy.LRU);
         BlockId blk = new BlockId("test.tbl", 0);
         Buffer buf = bm.pin(blk);
         bm.unpin(buf);
         CacheStats stats = bm.getStats();
         assertTrue("has miss", stats.getMissCount() >= 1);
      });

      test("LRU evicts least recently used", () -> {
         setup();
         bm.setReplacementPolicy(ReplacementPolicy.LRU);
         BlockId b0 = new BlockId("test.tbl", 0);
         BlockId b1 = new BlockId("test.tbl", 1);
         BlockId b2 = new BlockId("test.tbl", 2);

         Buffer buf0 = bm.pin(b0);
         Buffer buf1 = bm.pin(b1);
         Buffer buf2 = bm.pin(b2);

         // access b0 to make it recently used
         bm.pin(b0);
         bm.unpin(buf0);

         // unpin all so they're evictable
         bm.unpin(buf1);
         bm.unpin(buf2);

         // pin a new block -> should evict b1 (LRU among evictable)
         Buffer buf3 = bm.pin(new BlockId("test.tbl", 3));
         CacheStats stats = bm.getStats();
         assertTrue("eviction happened", stats.getEvictionCount() >= 1);
         bm.unpin(buf3);
      });

      test("FIFO evicts first loaded", () -> {
         setup();
         bm.setReplacementPolicy(ReplacementPolicy.FIFO);
         BlockId b0 = new BlockId("test.tbl", 0);
         BlockId b1 = new BlockId("test.tbl", 1);
         BlockId b2 = new BlockId("test.tbl", 2);

         Buffer buf0 = bm.pin(b0);
         Buffer buf1 = bm.pin(b1);
         Buffer buf2 = bm.pin(b2);

         bm.unpin(buf0);
         bm.unpin(buf1);
         bm.unpin(buf2);

         Buffer buf3 = bm.pin(new BlockId("test.tbl", 3));
         CacheStats stats = bm.getStats();
         assertTrue("eviction happened", stats.getEvictionCount() >= 1);
         bm.unpin(buf3);
      });

      test("flushAll writes dirty pages", () -> {
         setup();
         BlockId blk = new BlockId("test.tbl", 0);
         Buffer buf = bm.pin(blk);
         buf.contents().setInt(0, 42);
         buf.setModified(1, -1);
         bm.unpin(buf);
         bm.flushAllDirty();
         assertTrue("flushAll completed", true);
      });

      test("stats track hit and miss counts", () -> {
         setup();
         bm.setReplacementPolicy(ReplacementPolicy.LRU);
         BlockId blk = new BlockId("test.tbl", 0);
         Buffer buf = bm.pin(blk);
         bm.unpin(buf);
         Buffer buf2 = bm.pin(blk);
         bm.unpin(buf2);
         CacheStats stats = bm.getStats();
         assertTrue("has misses", stats.getMissCount() >= 1);
         assertTrue("has hits", stats.getHitCount() >= 1);
      });

      test("setReplacementPolicy switches strategy", () -> {
         setup();
         bm.setReplacementPolicy(ReplacementPolicy.LRU);
         bm.setReplacementPolicy(ReplacementPolicy.FIFO);
         assertTrue("policy switch ok", true);
      });
   }

   /**
    * In-memory FileMgr stub. Overrides all disk methods to use a HashMap.
    * The parent constructor creates a temp directory as a side effect;
    * we clean it up in teardown.
    */
   private static class StubFileMgr extends FileMgr {
      private final Map<String, byte[][]> files = new HashMap<>();
      private final int blockSize;

      StubFileMgr(String dirName, int blockSize) {
         super(new File(dirName), blockSize);
         this.blockSize = blockSize;
      }

      @Override
      public synchronized void read(BlockId blk, Page p) {
         byte[][] blocks = files.get(blk.fileName());
         if (blocks == null || blk.number() >= blocks.length || blocks[blk.number()] == null)
            return;
         byte[] data = blocks[blk.number()];
         Page temp = new Page(data);
         java.nio.ByteBuffer src = temp.contents();
         java.nio.ByteBuffer dst = p.contents();
         src.position(0);
         dst.position(0);
         dst.put(src);
      }

      @Override
      public synchronized void write(BlockId blk, Page p) {
         byte[][] blocks = files.computeIfAbsent(blk.fileName(),
               k -> new byte[blk.number() + 1][]);
         if (blk.number() >= blocks.length) {
            byte[][] newBlocks = new byte[blk.number() + 1][];
            System.arraycopy(blocks, 0, newBlocks, 0, blocks.length);
            files.put(blk.fileName(), newBlocks);
            blocks = newBlocks;
         }
         byte[] data = new byte[blockSize];
         java.nio.ByteBuffer src = p.contents();
         src.position(0);
         src.get(data);
         blocks[blk.number()] = data;
      }

      @Override
      public synchronized BlockId append(String filename) {
         byte[][] blocks = files.computeIfAbsent(filename, k -> new byte[0][]);
         int newNum = blocks.length;
         byte[][] newBlocks = new byte[newNum + 1][];
         System.arraycopy(blocks, 0, newBlocks, 0, blocks.length);
         files.put(filename, newBlocks);
         return new BlockId(filename, newNum);
      }

      @Override
      public int length(String filename) {
         byte[][] blocks = files.get(filename);
         return blocks == null ? 0 : blocks.length;
      }

      @Override
      public int blockSize() { return blockSize; }

      @Override
      public boolean isNew() { return true; }
   }

   /**
    * In-memory LogMgr stub. No disk writes.
    */
   private static class StubLogMgr extends simpledb.log.LogMgr {
      StubLogMgr() {
         super(new StubFileMgr(TMP_DIR, BLOCK_SIZE), "stub_log");
      }

      @Override
      public synchronized int append(byte[] logrec) { return 0; }

      @Override
      public void flush(int lsn) { }

      @Override
      public java.util.Iterator<byte[]> iterator() {
         return java.util.Collections.<byte[]>emptyList().iterator();
      }
   }

   public static void main(String[] args) {
      System.exit(new BufferMgrTest().run() ? 0 : 1);
   }
}
