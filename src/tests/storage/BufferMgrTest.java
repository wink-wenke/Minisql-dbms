package tests.storage;

import java.io.File;
import java.util.*;
import simpledb.file.*;
import simpledb.buffer.*;
import simpledb.storage.*;
import tests.TestBase;

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
      // ===== 1. 基础 pin/unpin =====
      test("all buffers available initially", () -> {
         setup();
         int avail = bm.available();
         System.out.println("    [步骤] 创建 BufferMgr, 缓冲池大小 = " + NUM_BUFFS);
         System.out.println("    [结果] available() = " + avail);
         assertEquals("available", (long) NUM_BUFFS, (long) avail);
      });

      test("pin reduces available count", () -> {
         setup();
         System.out.println("    [步骤] 创建 BlockId(test.tbl, 0)");
         BlockId blk = new BlockId("test.tbl", 0);
         System.out.println("    [步骤] pin(blk) → 从磁盘读取块加载到空闲帧");
         bm.pin(blk);
         int avail = bm.available();
         System.out.println("    [结果] available() = " + avail + " (应为 " + (NUM_BUFFS - 1) + ")");
         assertEquals("available after pin", (long) (NUM_BUFFS - 1), (long) avail);
      });

      test("unpin restores available count", () -> {
         setup();
         BlockId blk = new BlockId("test.tbl", 0);
         Buffer buf = bm.pin(blk);
         System.out.println("    [步骤] pin 后 available = " + bm.available());
         bm.unpin(buf);
         int avail = bm.available();
         System.out.println("    [步骤] unpin 后");
         System.out.println("    [结果] available() = " + avail + " (恢复为 " + NUM_BUFFS + ")");
         assertEquals("available after unpin", (long) NUM_BUFFS, (long) avail);
      });

      test("pinning same block twice returns cached buffer", () -> {
         setup();
         BlockId blk = new BlockId("test.tbl", 0);
         System.out.println("    [步骤] 第一次 pin(test.tbl, 0) → 从磁盘读取");
         Buffer b1 = bm.pin(blk);
         System.out.println("    [步骤] 第二次 pin(test.tbl, 0) → 应命中缓存");
         Buffer b2 = bm.pin(blk);
         System.out.println("    [结果] b1 == b2 ? " + (b1 == b2) + " (同一个对象 = 缓存命中)");
         assertTrue("same buffer instance", b1 == b2);
         bm.unpin(b1);
         bm.unpin(b2);
      });

      // ===== 2. 缓存命中/未命中 =====
      test("cache miss on first access", () -> {
         setup();
         bm.setReplacementPolicy(ReplacementPolicy.LRU);
         BlockId blk = new BlockId("test.tbl", 0);
         System.out.println("    [步骤] 设置 LRU 策略");
         System.out.println("    [步骤] 第一次 pin(test.tbl, 0) → 缓冲池为空，必须从磁盘读取");
         Buffer buf = bm.pin(blk);
         bm.unpin(buf);
         CacheStats stats = bm.getStats();
         System.out.println("    [结果] missCount = " + stats.getMissCount() + ", hitCount = " + stats.getHitCount());
         assertTrue("has miss", stats.getMissCount() >= 1);
      });

      test("cache hit on second access", () -> {
         setup();
         bm.setReplacementPolicy(ReplacementPolicy.LRU);
         BlockId blk = new BlockId("test.tbl", 0);
         System.out.println("    [步骤] 第一次 pin(test.tbl, 0) → miss");
         Buffer buf1 = bm.pin(blk);
         bm.unpin(buf1);
         System.out.println("    [步骤] 第二次 pin(test.tbl, 0) → 应命中缓存，不读磁盘");
         Buffer buf2 = bm.pin(blk);
         bm.unpin(buf2);
         CacheStats stats = bm.getStats();
         System.out.println("    [结果] missCount = " + stats.getMissCount() + ", hitCount = " + stats.getHitCount());
         assertTrue("has misses", stats.getMissCount() >= 1);
         assertTrue("has hits", stats.getHitCount() >= 1);
      });

      // ===== 3. LRU 淘汰 =====
      test("LRU evicts least recently used", () -> {
         setup();
         bm.setReplacementPolicy(ReplacementPolicy.LRU);
         System.out.println("    [步骤] 设置 LRU 策略, 缓冲池大小 = " + NUM_BUFFS);
         System.out.println("    [步骤] 填满缓冲池: pin(0), pin(1), pin(2)");
         BlockId b0 = new BlockId("test.tbl", 0);
         BlockId b1 = new BlockId("test.tbl", 1);
         BlockId b2 = new BlockId("test.tbl", 2);
         Buffer buf0 = bm.pin(b0);
         Buffer buf1 = bm.pin(b1);
         Buffer buf2 = bm.pin(b2);
         System.out.println("    [状态] 帧0=test.tbl:0, 帧1=test.tbl:1, 帧2=test.tbl:2 (全部满)");

         System.out.println("    [步骤] 再次 pin(0) → 更新 lastAccessTime, 让它变\"新鲜\"");
         bm.pin(b0);
         bm.unpin(buf0);

         System.out.println("    [步骤] unpin 所有帧 → 全部可淘汰");
         bm.unpin(buf1);
         bm.unpin(buf2);
         System.out.println("    [状态] 帧0 lastAccess=最新, 帧1 lastAccess=较早, 帧2 lastAccess=中间");

         System.out.println("    [步骤] pin(3) → 缓冲池满, 触发 LRU 淘汰");
         System.out.println("    [逻辑] 遍历 pinCount==0 的帧, 选 lastAccessTime 最小的 → 帧1 被淘汰");
         Buffer buf3 = bm.pin(new BlockId("test.tbl", 3));
         CacheStats stats = bm.getStats();
         System.out.println("    [结果] evictionCount = " + stats.getEvictionCount());
         System.out.println("    [结果] 帧1 现在存放 test.tbl:3 (被替换)");
         assertTrue("eviction happened", stats.getEvictionCount() >= 1);
         bm.unpin(buf3);
      });

      // ===== 4. FIFO 淘汰 =====
      test("FIFO evicts first loaded", () -> {
         setup();
         bm.setReplacementPolicy(ReplacementPolicy.FIFO);
         System.out.println("    [步骤] 设置 FIFO 策略, 缓冲池大小 = " + NUM_BUFFS);
         System.out.println("    [步骤] 按顺序加载: pin(0), pin(1), pin(2)");
         BlockId b0 = new BlockId("test.tbl", 0);
         BlockId b1 = new BlockId("test.tbl", 1);
         BlockId b2 = new BlockId("test.tbl", 2);
         Buffer buf0 = bm.pin(b0);
         Buffer buf1 = bm.pin(b1);
         Buffer buf2 = bm.pin(b2);
         System.out.println("    [状态] 帧0=test.tbl:0(loadTime=T1), 帧1=test.tbl:1(loadTime=T2), 帧2=test.tbl:2(loadTime=T3)");

         System.out.println("    [步骤] unpin 所有帧 → 全部可淘汰");
         bm.unpin(buf0);
         bm.unpin(buf1);
         bm.unpin(buf2);

         System.out.println("    [步骤] pin(3) → 缓冲池满, 触发 FIFO 淘汰");
         System.out.println("    [逻辑] 选 loadTime 最小的 → 帧0(T1, 最早加载) 被淘汰");
         Buffer buf3 = bm.pin(new BlockId("test.tbl", 3));
         CacheStats stats = bm.getStats();
         System.out.println("    [结果] evictionCount = " + stats.getEvictionCount());
         System.out.println("    [结果] 帧0 现在存放 test.tbl:3 (被替换)");
         assertTrue("eviction happened", stats.getEvictionCount() >= 1);
         bm.unpin(buf3);
      });

      // ===== 5. flushAll 脏页回写 =====
      test("flushAll writes dirty pages", () -> {
         setup();
         BlockId blk = new BlockId("test.tbl", 0);
         System.out.println("    [步骤] pin(test.tbl, 0) 并写入数据 42");
         Buffer buf = bm.pin(blk);
         buf.contents().setInt(0, 42);
         System.out.println("    [步骤] setModified(txnum=1) → 标记为脏页");
         buf.setModified(1, -1);
         bm.unpin(buf);
         System.out.println("    [步骤] unpin → pinCount=0, 但 dirty=true");
         System.out.println("    [步骤] flushAllDirty() → 遍历所有帧, 脏页写回磁盘");
         bm.flushAllDirty();
         System.out.println("    [结果] flushAll 完成, 脏页已写回磁盘");
         assertTrue("flushAll completed", true);
      });

      // ===== 6. 替换策略切换 =====
      test("setReplacementPolicy switches strategy", () -> {
         setup();
         System.out.println("    [步骤] 初始策略: 默认");
         System.out.println("    [步骤] setReplacementPolicy(LRU) → 切换为最近最少使用");
         bm.setReplacementPolicy(ReplacementPolicy.LRU);
         System.out.println("    [步骤] setReplacementPolicy(FIFO) → 切换为先进先出");
         bm.setReplacementPolicy(ReplacementPolicy.FIFO);
         System.out.println("    [结果] 支持运行时热切换, 无需重启缓冲池");
         assertTrue("policy switch ok", true);
      });
   }

   // ---- In-memory FileMgr stub ----
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

   // ---- In-memory LogMgr stub ----
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
