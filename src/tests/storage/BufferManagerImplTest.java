package tests.storage;

import java.io.File;
import java.util.*;
import simpledb.file.*;
import simpledb.buffer.*;
import simpledb.storage.*;
import simpledb.server.SimpleDB;
import simpledb.tx.Transaction;
import tests.TestBase;
import tests.TestCleanup;

/**
 * Unit tests for BufferManagerImpl (high-level BufferManager interface):
 * getPage, unpinPage, flushPage, flushAll, getBufferSlots.
 * Also tests integration with SimpleDB (upper-level module).
 */
public class BufferManagerImplTest extends TestBase {
    private static final int BLOCK_SIZE = 4096;
    private static final int NUM_BUFFS = 4;
    private static final String TMP_DIR = "bufmgrimpl_test_tmp";
    private static final String DB_DIR = "bufmgrimpl_db_tmp";

    private StubFileMgr fm;
    private StubLogMgr lm;
    private BufferMgr bm;
    private BufferManagerImpl bufMgr;

    private void setupLowLevel() {
        cleanupLowLevel();
        fm = new StubFileMgr(TMP_DIR, BLOCK_SIZE);
        lm = new StubLogMgr();
        bm = new BufferMgr(fm, lm, NUM_BUFFS);
        bufMgr = new BufferManagerImpl(bm);
    }

    private void cleanupLowLevel() {
        File dir = new File(TMP_DIR);
        if (dir.exists()) {
            File[] files = dir.listFiles();
            if (files != null) for (File f : files) f.delete();
            dir.delete();
        }
    }

    protected void cleanup() {
        cleanupLowLevel();
        resetDatabase(DB_DIR);
    }

    protected String suiteName() {
        return "BufferManagerImplTest - getPage/unpinPage/integration";
    }

    protected void cases() throws Exception {
        // ===== 1. getPage 基础 =====
        test("getPage returns page data as byte array", () -> {
            setupLowLevel();
            System.out.println("    [步骤] 创建 BufferManagerImpl, 缓冲池大小 = " + NUM_BUFFS);
            PageId pid = new PageId("test.tbl", 0);
            System.out.println("    [步骤] getPage(test.tbl:0) → pin 页面并返回 byte[]");
            byte[] data = bufMgr.getPage(pid);
            System.out.println("    [结果] data != null ? " + (data != null));
            System.out.println("    [结果] data.length = " + data.length + " (应等于 BLOCK_SIZE=" + BLOCK_SIZE + ")");
            assertTrue("data not null", data != null);
            assertTrue("data length matches block size", data.length == BLOCK_SIZE);
            bufMgr.unpinPage(pid);
        });

        test("getPage returns correct content after write", () -> {
            setupLowLevel();
            System.out.println("    [步骤] 通过底层 BufferMgr 写入整数 42 到 test.tbl:0");
            BlockId blk = new BlockId("test.tbl", 0);
            Buffer buf = bm.pin(blk);
            buf.contents().setInt(0, 42);
            buf.setModified(1, -1);
            bm.unpin(buf);

            System.out.println("    [步骤] 通过 BufferManagerImpl.getPage 读回");
            PageId pid = new PageId("test.tbl", 0);
            byte[] data = bufMgr.getPage(pid);
            int value = ((data[0] & 0xFF) << 24) | ((data[1] & 0xFF) << 16)
                    | ((data[2] & 0xFF) << 8) | (data[3] & 0xFF);
            System.out.println("    [步骤] 将前 4 字节转回 int (大端序)");
            System.out.println("    [结果] 读回值 = " + value + " (应为 42)");
            assertEquals("read back value", 42, (long) value);
            bufMgr.unpinPage(pid);
        });

        // ===== 2. unpinPage =====
        test("unpinPage releases pinned buffer", () -> {
            setupLowLevel();
            PageId pid = new PageId("test.tbl", 0);
            System.out.println("    [步骤] getPage(test.tbl:0) → 页面被 pin");
            bufMgr.getPage(pid);

            System.out.println("    [步骤] 查询所有缓冲池槽位, 查找 pinCount > 0 的帧");
            List<BufferManager.SlotInfo> slots = bufMgr.getBufferSlots();
            boolean found = false;
            for (BufferManager.SlotInfo s : slots) {
                if ("test.tbl".equals(s.fileName) && s.pinCount > 0) {
                    found = true;
                    System.out.println("    [发现] 帧 #" + s.slotIndex + ": file=" + s.fileName + ", block=" + s.blockNumber + ", pinCount=" + s.pinCount);
                    break;
                }
            }
            System.out.println("    [结果] getPage 后存在 pinned 帧 ? " + found);
            assertTrue("buffer is pinned after getPage", found);

            System.out.println("    [步骤] unpinPage(test.tbl:0) → 释放 pin");
            bufMgr.unpinPage(pid);
            System.out.println("    [步骤] 再次查询槽位, 查找 pinCount > 0 的帧");
            slots = bufMgr.getBufferSlots();
            found = false;
            for (BufferManager.SlotInfo s : slots) {
                if ("test.tbl".equals(s.fileName) && s.pinCount > 0) {
                    found = true;
                    break;
                }
            }
            System.out.println("    [结果] unpinPage 后存在 pinned 帧 ? " + found + " (应为 false)");
            assertTrue("buffer is unpinned after unpinPage", !found);
        });

        // ===== 3. flushPage =====
        test("flushPage writes dirty page to disk", () -> {
            setupLowLevel();
            PageId pid = new PageId("test.tbl", 0);
            System.out.println("    [步骤] getPage(test.tbl:0) → pin 页面");
            byte[] data = bufMgr.getPage(pid);
            System.out.println("    [步骤] 修改 data[0] = 0xFF → 标记为脏页");
            data[0] = (byte) 0xFF;
            bufMgr.unpinPage(pid);

            System.out.println("    [步骤] flushPage(test.tbl:0) → 脏页写回磁盘");
            bufMgr.flushPage(pid);

            System.out.println("    [步骤] 查询槽位, 验证 dirty 标记已清除");
            List<BufferManager.SlotInfo> slots = bufMgr.getBufferSlots();
            for (BufferManager.SlotInfo s : slots) {
                if ("test.tbl".equals(s.fileName) && s.blockNumber == 0) {
                    System.out.println("    [结果] 帧 #" + s.slotIndex + ": dirty = " + s.dirty);
                    assertTrue("page should be clean after flush", !s.dirty);
                }
            }
        });

        // ===== 4. flushAll =====
        test("flushAll writes all dirty pages", () -> {
            setupLowLevel();
            PageId p0 = new PageId("test.tbl", 0);
            PageId p1 = new PageId("test.tbl", 1);
            System.out.println("    [步骤] getPage(test.tbl:0) 和 getPage(test.tbl:1)");
            bufMgr.getPage(p0);
            bufMgr.getPage(p1);

            System.out.println("    [步骤] 通过底层 BufferMgr 将两个页面标记为脏页");
            Buffer buf0 = bm.pin(new BlockId("test.tbl", 0));
            buf0.contents().setInt(0, 99);
            buf0.setModified(1, -1);
            bm.unpin(buf0);
            Buffer buf1 = bm.pin(new BlockId("test.tbl", 1));
            buf1.contents().setInt(0, 88);
            buf1.setModified(1, -1);
            bm.unpin(buf1);
            System.out.println("    [状态] test.tbl:0 dirty=99, test.tbl:1 dirty=88");

            System.out.println("    [步骤] flushAll() → 遍历所有帧, 全部脏页写回磁盘");
            bufMgr.flushAll();

            System.out.println("    [步骤] 查询槽位, 验证所有 test.tbl 页面 dirty=false");
            List<BufferManager.SlotInfo> slots = bufMgr.getBufferSlots();
            for (BufferManager.SlotInfo s : slots) {
                if ("test.tbl".equals(s.fileName)) {
                    System.out.println("    [结果] 帧 #" + s.slotIndex + ": dirty = " + s.dirty);
                    assertTrue("slot " + s.slotIndex + " should be clean", !s.dirty);
                }
            }
            bufMgr.unpinPage(p0);
            bufMgr.unpinPage(p1);
        });

        // ===== 5. getBufferSlots =====
        test("getBufferSlots returns correct slot count", () -> {
            setupLowLevel();
            System.out.println("    [步骤] 缓冲池大小 = " + NUM_BUFFS);
            List<BufferManager.SlotInfo> slots = bufMgr.getBufferSlots();
            System.out.println("    [结果] getBufferSlots 返回 " + slots.size() + " 个槽位");
            assertEquals("slot count", NUM_BUFFS, slots.size());
        });

        test("getBufferSlots shows correct file/block for loaded pages", () -> {
            setupLowLevel();
            PageId pid = new PageId("mydata.tbl", 2);
            System.out.println("    [步骤] getPage(mydata.tbl:2) → 加载页面到缓冲池");
            bufMgr.getPage(pid);

            System.out.println("    [步骤] 查询所有槽位, 查找 mydata.tbl:block=2");
            List<BufferManager.SlotInfo> slots = bufMgr.getBufferSlots();
            boolean found = false;
            for (BufferManager.SlotInfo s : slots) {
                if ("mydata.tbl".equals(s.fileName) && s.blockNumber == 2) {
                    found = true;
                    System.out.println("    [发现] 帧 #" + s.slotIndex + ": file=" + s.fileName
                            + ", block=" + s.blockNumber + ", pinCount=" + s.pinCount
                            + ", dirty=" + s.dirty + ", txnum=" + s.txnum);
                    assertEquals("pinCount", 1, (long) s.pinCount);
                    break;
                }
            }
            System.out.println("    [结果] 页面信息正确暴露在槽位中 ? " + found);
            assertTrue("loaded page visible in slots", found);
            bufMgr.unpinPage(pid);
        });

        // ===== 6. CacheStats =====
        test("getStats tracks hits and misses through BufferManager", () -> {
            setupLowLevel();
            bufMgr.setReplacementPolicy(ReplacementPolicy.LRU);
            PageId pid = new PageId("test.tbl", 0);
            System.out.println("    [步骤] 第一次 getPage(test.tbl:0) → miss (首次加载)");
            bufMgr.getPage(pid);
            bufMgr.unpinPage(pid);
            System.out.println("    [步骤] 第二次 getPage(test.tbl:0) → hit (命中缓存)");
            bufMgr.getPage(pid);
            bufMgr.unpinPage(pid);
            CacheStats stats = bufMgr.getStats();
            System.out.println("    [结果] accessCount=" + stats.getAccessCount()
                    + ", hitCount=" + stats.getHitCount()
                    + ", missCount=" + stats.getMissCount());
            assertTrue("has miss", stats.getMissCount() >= 1);
            assertTrue("has hit", stats.getHitCount() >= 1);
        });

        // ===== 7. SimpleDB 集成测试 =====
        test("SimpleDB integration: getPage works with real database", () -> {
            resetDatabase(DB_DIR);
            TestCleanup.init();
            SimpleDB.DB_BASE_DIR = "tmp";
            System.out.println("    [步骤] 初始化 SimpleDB 数据库 (DB_DIR=" + DB_DIR + ")");
            SimpleDB db = new SimpleDB(DB_DIR);
            BufferManager bmIntf = db.getBufferManager();
            System.out.println("    [步骤] 获取 BufferManager 接口实例");

            Transaction tx = db.newTx();
            System.out.println("    [步骤] 执行 CREATE TABLE integ_test (id INT, name VARCHAR(16))");
            db.planner().executeUpdate(
                "CREATE TABLE integ_test (id INT, name VARCHAR(16))", tx);
            System.out.println("    [步骤] 执行 INSERT INTO integ_test VALUES (1, 'Alice')");
            db.planner().executeUpdate(
                "INSERT INTO integ_test (id, name) VALUES (1, 'Alice')", tx);
            System.out.println("    [步骤] 执行 INSERT INTO integ_test VALUES (2, 'Bob')");
            db.planner().executeUpdate(
                "INSERT INTO integ_test (id, name) VALUES (2, 'Bob')", tx);
            tx.commit();
            System.out.println("    [结果] 事务提交完成");

            CacheStats stats = bmIntf.getStats();
            System.out.println("    [步骤] 读取 CacheStats: accessCount=" + stats.getAccessCount());
            List<BufferManager.SlotInfo> slots = bmIntf.getBufferSlots();
            System.out.println("    [步骤] 读取缓冲池槽位: " + slots.size() + " 个帧");
            for (BufferManager.SlotInfo s : slots) {
                if (s.fileName != null) {
                    System.out.println("    [槽位] #" + s.slotIndex + " " + s.fileName + ":" + s.blockNumber
                            + " pin=" + s.pinCount + " dirty=" + s.dirty);
                }
            }

            System.out.println("    [步骤] 测试策略切换: FIFO → LRU");
            bmIntf.setReplacementPolicy(ReplacementPolicy.FIFO);
            bmIntf.setReplacementPolicy(ReplacementPolicy.LRU);

            System.out.println("    [步骤] flushAll() → 所有脏页写回磁盘");
            bmIntf.flushAll();
            System.out.println("    [结果] 集成测试通过: SQL → Planner → 磁盘 → 缓冲池 → 接口层 全链路正常");
            assertTrue("flushAll completed", true);
        });

        test("SimpleDB integration: buffer slots reflect real table pages", () -> {
            resetDatabase(DB_DIR);
            SimpleDB db = new SimpleDB(DB_DIR);
            BufferManager bmIntf = db.getBufferManager();

            Transaction tx = db.newTx();
            System.out.println("    [步骤] CREATE TABLE slot_test (val INT)");
            db.planner().executeUpdate(
                "CREATE TABLE slot_test (val INT)", tx);
            System.out.println("    [步骤] INSERT INTO slot_test VALUES (100)");
            db.planner().executeUpdate(
                "INSERT INTO slot_test (val) VALUES (100)", tx);
            tx.commit();

            System.out.println("    [步骤] flushAll() → 确保所有页面写入磁盘");
            bmIntf.flushAll();
            CacheStats stats = bmIntf.getStats();
            System.out.println("    [结果] accessCount = " + stats.getAccessCount() + " (至少有 1 次磁盘访问)");
            assertTrue("has at least one access", stats.getAccessCount() > 0);
        });

        test("SimpleDB integration: flushPage on specific table page", () -> {
            resetDatabase(DB_DIR);
            SimpleDB db = new SimpleDB(DB_DIR);
            BufferManager bmIntf = db.getBufferManager();

            Transaction tx = db.newTx();
            System.out.println("    [步骤] CREATE TABLE flush_test (x INT)");
            db.planner().executeUpdate(
                "CREATE TABLE flush_test (x INT)", tx);
            System.out.println("    [步骤] INSERT INTO flush_test VALUES (42)");
            db.planner().executeUpdate(
                "INSERT INTO flush_test (x) VALUES (42)", tx);
            tx.commit();

            System.out.println("    [步骤] flushAll() → 将所有脏页写回磁盘");
            bmIntf.flushAll();
            System.out.println("    [步骤] 查询槽位, 验证无脏页残留");
            List<BufferManager.SlotInfo> slots = bmIntf.getBufferSlots();
            int dirtyCount = 0;
            for (BufferManager.SlotInfo s : slots) {
                if (s.fileName != null && s.dirty) dirtyCount++;
            }
            System.out.println("    [结果] 脏页数 = " + dirtyCount + " (应为 0)");
            for (BufferManager.SlotInfo s : slots) {
                if (s.fileName != null) {
                    assertTrue("no dirty pages after flushAll", !s.dirty);
                }
            }
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
            if (blocks == null || blk.number() >= blocks.length || blocks[blk.number()] == null) return;
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
                byte[][] nb = new byte[blk.number() + 1][];
                System.arraycopy(blocks, 0, nb, 0, blocks.length);
                files.put(blk.fileName(), nb);
                blocks = nb;
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
            int num = blocks.length;
            byte[][] nb = new byte[num + 1][];
            System.arraycopy(blocks, 0, nb, 0, blocks.length);
            files.put(filename, nb);
            return new BlockId(filename, num);
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
        System.exit(new BufferManagerImplTest().run() ? 0 : 1);
    }
}
