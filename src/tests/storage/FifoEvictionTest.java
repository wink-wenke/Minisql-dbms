package tests.storage;

import java.io.File;
import java.util.*;
import simpledb.file.*;
import simpledb.buffer.*;
import simpledb.storage.*;
import tests.TestBase;

/**
 * 严格验证 FIFO 淘汰顺序的测试。
 * 确保 FIFO 总是淘汰最早加载（loadTime 最小）的 unpinned 缓冲区。
 */
public class FifoEvictionTest extends TestBase {
   private static final int BLOCK_SIZE = 4096;
   private static final int NUM_BUFFS = 3;
   private static final String TMP_DIR = "fifo_test_tmp";

   private StubFileMgr fm;
   private StubLogMgr lm;
   private BufferMgr bm;

   private void setup() {
      cleanup();
      fm = new StubFileMgr(TMP_DIR, BLOCK_SIZE);
      lm = new StubLogMgr();
      bm = new BufferMgr(fm, lm, NUM_BUFFS);
      bm.setReplacementPolicy(ReplacementPolicy.FIFO);
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
      return "FifoEvictionTest - FIFO淘汰顺序严格验证";
   }

   private List<simpledb.buffer.BufferMgr.SlotInfo> getSlots() {
      return bm.getSlotInfoList();
   }

   private void printSlots(String label) {
      List<simpledb.buffer.BufferMgr.SlotInfo> slots = getSlots();
      System.out.println("    [状态] " + label);
      for (simpledb.buffer.BufferMgr.SlotInfo s : slots) {
         String file = s.fileName != null ? s.fileName : "(空)";
         int block = s.blockNumber;
         System.out.println("      帧" + s.slotIndex + " → " + file + ":" + block
               + "  pins=" + s.pinCount + " dirty=" + s.dirty);
      }
   }

   protected void cases() throws Exception {

      // ===== 测试1: 基本FIFO — 淘汰最早加载的帧 =====
      test("FIFO淘汰最早加载的帧", () -> {
         setup();
         System.out.println("    [步骤] 填满3个槽位: pin(0), pin(1), pin(2)");
         BlockId b0 = new BlockId("test.tbl", 0);
         BlockId b1 = new BlockId("test.tbl", 1);
         BlockId b2 = new BlockId("test.tbl", 2);

         Buffer buf0 = bm.pin(b0);
         Buffer buf1 = bm.pin(b1);
         Buffer buf2 = bm.pin(b2);
         printSlots("填满后");

         // unpin所有 → 全部可淘汰
         bm.unpin(buf0);
         bm.unpin(buf1);
         bm.unpin(buf2);
         printSlots("全部unpin后");

         // 记录淘汰前的帧状态
         List<simpledb.buffer.BufferMgr.SlotInfo> before = getSlots();
         int beforeFrame0Block = before.get(0).blockNumber;

         System.out.println("    [步骤] pin(3) → 触发FIFO淘汰");
         System.out.println("    [预期] 帧0 (test.tbl:0, 最早加载) 应被淘汰");

         Buffer buf3 = bm.pin(new BlockId("test.tbl", 3));
         printSlots("淘汰后");

         // 验证: 帧0应该被替换为test.tbl:3
         List<simpledb.buffer.BufferMgr.SlotInfo> after = getSlots();
         boolean frame0Replaced = !"test.tbl".equals(after.get(0).fileName)
               || after.get(0).blockNumber != beforeFrame0Block;
         // 帧1和帧2应该没变
         boolean frame1Unchanged = "test.tbl".equals(after.get(1).fileName)
               && after.get(1).blockNumber == before.get(1).blockNumber;
         boolean frame2Unchanged = "test.tbl".equals(after.get(2).fileName)
               && after.get(2).blockNumber == before.get(2).blockNumber;

         System.out.println("    [验证] 帧0被替换: " + frame0Replaced);
         System.out.println("    [验证] 帧1未变: " + frame1Unchanged);
         System.out.println("    [验证] 帧2未变: " + frame2Unchanged);

         assertTrue("帧0应被替换", frame0Replaced);
         assertTrue("帧1不应变", frame1Unchanged);
         assertTrue("帧2不应变", frame2Unchanged);

         bm.unpin(buf3);
      });

      // ===== 测试2: FIFO不因重新pin而改变淘汰顺序 =====
      test("FIFO不因重新pin改变淘汰顺序(与LRU的关键区别)", () -> {
         setup();
         System.out.println("    [步骤] 填满: pin(0)→帧0, pin(1)→帧1, pin(2)→帧2");
         BlockId b0 = new BlockId("test.tbl", 0);
         BlockId b1 = new BlockId("test.tbl", 1);
         BlockId b2 = new BlockId("test.tbl", 2);

         Buffer buf0 = bm.pin(b0);
         Buffer buf1 = bm.pin(b1);
         Buffer buf2 = bm.pin(b2);

         // 全部unpin
         bm.unpin(buf0);
         bm.unpin(buf1);
         bm.unpin(buf2);
         printSlots("全部unpin");

         // 重新pin(0) — cache hit, 不调用assignToBlock, loadTime不变
         System.out.println("    [步骤] 重新pin(0) → cache hit, loadTime不变");
         System.out.println("    [说明] FIFO的关键特性: 重新访问不会让帧变\"新\"");
         Buffer buf0Again = bm.pin(b0);
         bm.unpin(buf0Again);
         printSlots("重新pin(0)后");

         // FIFO顺序不变: 帧0(最早) < 帧1(中间) < 帧2(最晚)
         // 帧0仍然应该被淘汰
         System.out.println("    [步骤] pin(3) → 帧0仍是最老的, 应被淘汰");
         Buffer buf3 = bm.pin(new BlockId("test.tbl", 3));
         printSlots("淘汰后");

         List<simpledb.buffer.BufferMgr.SlotInfo> after = getSlots();
         // 帧0应该被替换(它仍然是最早加载的)
         boolean frame0Evicted = !"test.tbl".equals(after.get(0).fileName)
               || after.get(0).blockNumber != 0;
         // 帧1应该还在(它比帧0晚加载)
         boolean frame1Kept = "test.tbl".equals(after.get(1).fileName)
               && after.get(1).blockNumber == 1;
         // 帧2应该还在(它最晚加载)
         boolean frame2Kept = "test.tbl".equals(after.get(2).fileName)
               && after.get(2).blockNumber == 2;

         System.out.println("    [验证] 帧0被淘汰: " + frame0Evicted + " (最早加载, 即使被重新pin过)");
         System.out.println("    [验证] 帧1保留: " + frame1Kept);
         System.out.println("    [验证] 帧2保留: " + frame2Kept);

         assertTrue("帧0应被淘汰(最早加载, 重新pin不影响FIFO)", frame0Evicted);
         assertTrue("帧1应保留", frame1Kept);
         assertTrue("帧2应保留", frame2Kept);

         bm.unpin(buf3);
      });

      // ===== 测试3: pinned帧不会被淘汰 =====
      test("pinned帧不会被淘汰", () -> {
         setup();
         System.out.println("    [步骤] 填满: pin(0), pin(1), pin(2)");
         BlockId b0 = new BlockId("test.tbl", 0);
         BlockId b1 = new BlockId("test.tbl", 1);
         BlockId b2 = new BlockId("test.tbl", 2);

         Buffer buf0 = bm.pin(b0);
         Buffer buf1 = bm.pin(b1);
         Buffer buf2 = bm.pin(b2);

         // 只unpin帧0和帧2, 帧1保持pinned
         bm.unpin(buf0);
         bm.unpin(buf2);
         printSlots("帧1保持pinned");

         System.out.println("    [步骤] pin(3) → 帧1被pin住不能淘汰，应在帧0和帧2中选");
         System.out.println("    [预期] 帧0(loadTime最早)应被淘汰");

         Buffer buf3 = bm.pin(new BlockId("test.tbl", 3));
         printSlots("淘汰后");

         List<simpledb.buffer.BufferMgr.SlotInfo> after = getSlots();
         // 帧1不应变(被pin住)
         boolean frame1Untouched = "test.tbl".equals(after.get(1).fileName)
               && after.get(1).blockNumber == 1;
         // 帧0应该被替换(最早加载且未pin)
         boolean frame0Evicted = !"test.tbl".equals(after.get(0).fileName)
               || after.get(0).blockNumber != 0;

         System.out.println("    [验证] 帧1未被触碰: " + frame1Untouched);
         System.out.println("    [验证] 帧0被淘汰: " + frame0Evicted);

         assertTrue("帧1不应变(pinned)", frame1Untouched);
         assertTrue("帧0应被淘汰(最早且unpinned)", frame0Evicted);

         bm.unpin(buf3);
      });

      // ===== 测试4: 多轮淘汰验证FIFO顺序的稳定性 =====
      test("多轮淘汰FIFO顺序稳定", () -> {
         setup();
         System.out.println("    [步骤] 填满: pin(A), pin(B), pin(C)");
         BlockId bA = new BlockId("test.tbl", 0); // A
         BlockId bB = new BlockId("test.tbl", 1); // B
         BlockId bC = new BlockId("test.tbl", 2); // C

         Buffer bufA = bm.pin(bA);
         Buffer bufB = bm.pin(bB);
         Buffer bufC = bm.pin(bC);

         bm.unpin(bufA);
         bm.unpin(bufB);
         bm.unpin(bufC);
         printSlots("初始: A在帧0, B在帧1, C在帧2");

         // 第1轮淘汰: 应淘汰帧0(A最早)
         System.out.println("    [步骤] 第1轮: pin(D) → 应淘汰A(帧0)");
         Buffer bufD = bm.pin(new BlockId("test.tbl", 3));
         printSlots("第1轮后: D替换A");

         List<simpledb.buffer.BufferMgr.SlotInfo> s1 = getSlots();
         assertEquals("第1轮-帧0应为D", "test.tbl", s1.get(0).fileName);
         assertEquals("第1轮-帧0块号", 3, s1.get(0).blockNumber);
         assertEquals("第1轮-帧1应为B", 1, s1.get(1).blockNumber);
         assertEquals("第1轮-帧2应为C", 2, s1.get(2).blockNumber);
         bm.unpin(bufD);

         // 第2轮淘汰: 应淘汰帧1(B最老)
         System.out.println("    [步骤] 第2轮: pin(E) → 应淘汰B(帧1)");
         Buffer bufE = bm.pin(new BlockId("test.tbl", 4));
         printSlots("第2轮后: E替换B");

         List<simpledb.buffer.BufferMgr.SlotInfo> s2 = getSlots();
         assertEquals("第2轮-帧0应为D", 3, s2.get(0).blockNumber);
         assertEquals("第2轮-帧1应为E", 4, s2.get(1).blockNumber);
         assertEquals("第2轮-帧2应为C", 2, s2.get(2).blockNumber);
         bm.unpin(bufE);

         // 第3轮淘汰: 应淘汰帧2(C最老)
         System.out.println("    [步骤] 第3轮: pin(F) → 应淘汰C(帧2)");
         Buffer bufF = bm.pin(new BlockId("test.tbl", 5));
         printSlots("第3轮后: F替换C");

         List<simpledb.buffer.BufferMgr.SlotInfo> s3 = getSlots();
         assertEquals("第3轮-帧0应为D", 3, s3.get(0).blockNumber);
         assertEquals("第3轮-帧1应为E", 4, s3.get(1).blockNumber);
         assertEquals("第3轮-帧2应为F", 5, s3.get(2).blockNumber);
         bm.unpin(bufF);

         System.out.println("    [结果] FIFO循环淘汰: A→B→C→D→E→F, 每轮淘汰最早的帧");
      });

      // ===== 测试5: loadTime=0边界问题 — 验证未初始化帧的行为 =====
      test("loadTime初始值为0，验证未初始化帧不干扰FIFO", () -> {
         setup();
         System.out.println("    [说明] 新建Buffer时loadTime=0，而nanoTime()返回正值");
         System.out.println("    [步骤] 只填2个槽位(留1个空)");

         BlockId b0 = new BlockId("test.tbl", 0);
         BlockId b1 = new BlockId("test.tbl", 1);

         Buffer buf0 = bm.pin(b0);
         Buffer buf1 = bm.pin(b1);

         printSlots("2个槽位被占用");

         // 此时帧2仍是空的(loadTime=0)
         // 如果FIFO选了帧2作为victim，说明loadTime=0导致空帧被误选
         // 实际上空帧(blk=null)被选中不会导致数据丢失，但逻辑上不精确

         bm.unpin(buf0);
         bm.unpin(buf1);

         System.out.println("    [步骤] pin(2) → 检查是否选中空帧(帧2)作为victim");
         Buffer buf2 = bm.pin(new BlockId("test.tbl", 2));
         printSlots("pin(2)后");

         List<simpledb.buffer.BufferMgr.SlotInfo> after = getSlots();
         // 帧0和帧1应该还在（不应该被替换，因为帧2是空的应该先被用）
         boolean frame0Ok = "test.tbl".equals(after.get(0).fileName) && after.get(0).blockNumber == 0;
         boolean frame1Ok = "test.tbl".equals(after.get(1).fileName) && after.get(1).blockNumber == 1;

         System.out.println("    [验证] 帧0保留: " + frame0Ok);
         System.out.println("    [验证] 帧1保留: " + frame1Ok);

         // 空帧(帧2)应该被优先使用，不应该触发eviction
         CacheStats stats = bm.getStats();
         System.out.println("    [验证] evictionCount=" + stats.getEvictionCount() + " (应为0，空帧直接使用)");

         assertTrue("帧0应保留", frame0Ok);
         assertTrue("帧1应保留", frame1Ok);
         // 使用空帧不应触发eviction(没有旧数据需要替换)
         assertEquals("空帧使用不应计为eviction", 0, stats.getEvictionCount());

         bm.unpin(buf2);
      });

      // ===== 测试6: 混合操作后FIFO顺序 =====
      test("混合pin/unpin后FIFO仍正确", () -> {
         setup();
         System.out.println("    [步骤] 填满: pin(0), pin(1), pin(2)");
         BlockId b0 = new BlockId("test.tbl", 0);
         BlockId b1 = new BlockId("test.tbl", 1);
         BlockId b2 = new BlockId("test.tbl", 2);

         Buffer buf0 = bm.pin(b0);
         Buffer buf1 = bm.pin(b1);
         Buffer buf2 = bm.pin(b2);

         System.out.println("    [步骤] 复杂操作序列:");
         System.out.println("      1. unpin(0), unpin(1) → 帧0和帧1可淘汰");
         bm.unpin(buf0);
         bm.unpin(buf1);

         System.out.println("      2. pin(1) → 帧1被重新pin住(loadTime更新)");
         Buffer buf1Again = bm.pin(b1);

         System.out.println("      3. unpin(2) → 帧2可淘汰");
         bm.unpin(buf2);

         System.out.println("      4. pin(3) → 应淘汰帧0(最早loadTime且未pin)");
         printSlots("操作后状态");

         // 帧0: unpinned, loadTime=T1(最早)
         // 帧1: pinned, loadTime=T4(最新)
         // 帧2: unpinned, loadTime=T3(中间)
         // 应淘汰帧0

         Buffer buf3 = bm.pin(new BlockId("test.tbl", 3));
         printSlots("淘汰后");

         List<simpledb.buffer.BufferMgr.SlotInfo> after = getSlots();
         // 帧0应被替换
         boolean frame0Evicted = !"test.tbl".equals(after.get(0).fileName)
               || after.get(0).blockNumber != 0;
         // 帧1应保留(pinned)
         boolean frame1Kept = "test.tbl".equals(after.get(1).fileName)
               && after.get(1).blockNumber == 1;
         // 帧2应保留(比帧0晚)
         boolean frame2Kept = "test.tbl".equals(after.get(2).fileName)
               && after.get(2).blockNumber == 2;

         System.out.println("    [验证] 帧0被淘汰: " + frame0Evicted);
         System.out.println("    [验证] 帧1保留(pinned): " + frame1Kept);
         System.out.println("    [验证] 帧2保留: " + frame2Kept);

         assertTrue("帧0应被淘汰", frame0Evicted);
         assertTrue("帧1应保留(pinned)", frame1Kept);
         assertTrue("帧2应保留", frame2Kept);

         bm.unpin(buf1Again);
         bm.unpin(buf3);
      });
   }

   // ---- Stubs ----
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
      System.exit(new FifoEvictionTest().run() ? 0 : 1);
   }
}
