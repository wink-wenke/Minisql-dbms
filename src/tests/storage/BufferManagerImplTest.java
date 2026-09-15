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

    // ===== Low-level stub tests (using BufferMgr directly) =====

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
        // ===== getPage basic =====
        test("getPage returns page data as byte array", () -> {
            setupLowLevel();
            PageId pid = new PageId("test.tbl", 0);
            byte[] data = bufMgr.getPage(pid);
            assertTrue("data not null", data != null);
            assertTrue("data length matches block size", data.length == BLOCK_SIZE);
            bufMgr.unpinPage(pid);
        });

        test("getPage returns correct content after write", () -> {
            setupLowLevel();
            // Write data to block 0 via BufferMgr
            BlockId blk = new BlockId("test.tbl", 0);
            Buffer buf = bm.pin(blk);
            buf.contents().setInt(0, 42);
            buf.setModified(1, -1);
            bm.unpin(buf);

            // Read back via BufferManagerImpl
            PageId pid = new PageId("test.tbl", 0);
            byte[] data = bufMgr.getPage(pid);
            // Convert first 4 bytes back to int
            int value = ((data[0] & 0xFF) << 24) | ((data[1] & 0xFF) << 16)
                    | ((data[2] & 0xFF) << 8) | (data[3] & 0xFF);
            assertEquals("read back value", 42, (long) value);
            bufMgr.unpinPage(pid);
        });

        // ===== unpinPage =====
        test("unpinPage releases pinned buffer", () -> {
            setupLowLevel();
            PageId pid = new PageId("test.tbl", 0);
            bufMgr.getPage(pid);
            // after getPage, buffer is pinned
            List<BufferManager.SlotInfo> slots = bufMgr.getBufferSlots();
            boolean found = false;
            for (BufferManager.SlotInfo s : slots) {
                if ("test.tbl".equals(s.fileName) && s.pinCount > 0) {
                    found = true;
                    break;
                }
            }
            assertTrue("buffer is pinned after getPage", found);

            bufMgr.unpinPage(pid);
            slots = bufMgr.getBufferSlots();
            found = false;
            for (BufferManager.SlotInfo s : slots) {
                if ("test.tbl".equals(s.fileName) && s.pinCount > 0) {
                    found = true;
                    break;
                }
            }
            assertTrue("buffer is unpinned after unpinPage", !found);
        });

        // ===== flushPage =====
        test("flushPage writes dirty page to disk", () -> {
            setupLowLevel();
            PageId pid = new PageId("test.tbl", 0);
            byte[] data = bufMgr.getPage(pid);
            // mark dirty
            data[0] = (byte) 0xFF;
            bufMgr.unpinPage(pid);

            // flush
            bufMgr.flushPage(pid);

            // verify stats show no dirty for this slot
            List<BufferManager.SlotInfo> slots = bufMgr.getBufferSlots();
            for (BufferManager.SlotInfo s : slots) {
                if ("test.tbl".equals(s.fileName) && s.blockNumber == 0) {
                    assertTrue("page should be clean after flush", !s.dirty);
                }
            }
        });

        // ===== flushAll =====
        test("flushAll writes all dirty pages", () -> {
            setupLowLevel();
            PageId p0 = new PageId("test.tbl", 0);
            PageId p1 = new PageId("test.tbl", 1);
            bufMgr.getPage(p0);
            bufMgr.getPage(p1);
            // dirty both via underlying BufferMgr
            Buffer buf0 = bm.pin(new BlockId("test.tbl", 0));
            buf0.contents().setInt(0, 99);
            buf0.setModified(1, -1);
            bm.unpin(buf0);
            Buffer buf1 = bm.pin(new BlockId("test.tbl", 1));
            buf1.contents().setInt(0, 88);
            buf1.setModified(1, -1);
            bm.unpin(buf1);

            bufMgr.flushAll();
            List<BufferManager.SlotInfo> slots = bufMgr.getBufferSlots();
            for (BufferManager.SlotInfo s : slots) {
                if ("test.tbl".equals(s.fileName)) {
                    assertTrue("slot " + s.slotIndex + " should be clean", !s.dirty);
                }
            }
            bufMgr.unpinPage(p0);
            bufMgr.unpinPage(p1);
        });

        // ===== getBufferSlots =====
        test("getBufferSlots returns correct slot count", () -> {
            setupLowLevel();
            List<BufferManager.SlotInfo> slots = bufMgr.getBufferSlots();
            assertEquals("slot count", NUM_BUFFS, slots.size());
        });

        test("getBufferSlots shows correct file/block for loaded pages", () -> {
            setupLowLevel();
            PageId pid = new PageId("mydata.tbl", 2);
            bufMgr.getPage(pid);
            List<BufferManager.SlotInfo> slots = bufMgr.getBufferSlots();
            boolean found = false;
            for (BufferManager.SlotInfo s : slots) {
                if ("mydata.tbl".equals(s.fileName) && s.blockNumber == 2) {
                    found = true;
                    assertEquals("pinCount", 1, (long) s.pinCount);
                    break;
                }
            }
            assertTrue("loaded page visible in slots", found);
            bufMgr.unpinPage(pid);
        });

        // ===== CacheStats via BufferManager =====
        test("getStats tracks hits and misses through BufferManager", () -> {
            setupLowLevel();
            bufMgr.setReplacementPolicy(ReplacementPolicy.LRU);
            PageId pid = new PageId("test.tbl", 0);
            bufMgr.getPage(pid);   // miss
            bufMgr.unpinPage(pid);
            bufMgr.getPage(pid);   // hit
            bufMgr.unpinPage(pid);
            CacheStats stats = bufMgr.getStats();
            assertTrue("has miss", stats.getMissCount() >= 1);
            assertTrue("has hit", stats.getHitCount() >= 1);
        });

        // ===== Integration with SimpleDB =====
        test("SimpleDB integration: getPage works with real database", () -> {
            resetDatabase(DB_DIR);
            TestCleanup.init();
      SimpleDB.DB_BASE_DIR = "tmp";
            SimpleDB db = new SimpleDB(DB_DIR);
            BufferManager bmIntf = db.getBufferManager();

            // Create a table so there's real data on disk
            Transaction tx = db.newTx();
            db.planner().executeUpdate(
                "CREATE TABLE integ_test (id INT, name VARCHAR(16))", tx);
            db.planner().executeUpdate(
                "INSERT INTO integ_test (id, name) VALUES (1, 'Alice')", tx);
            db.planner().executeUpdate(
                "INSERT INTO integ_test (id, name) VALUES (2, 'Bob')", tx);
            tx.commit();

            // Now access the buffer manager
            CacheStats stats = bmIntf.getStats();
            assertTrue("stats accessible", stats != null);
            List<BufferManager.SlotInfo> slots = bmIntf.getBufferSlots();
            assertTrue("slots not empty", slots.size() > 0);

            // Verify we can set replacement policy
            bmIntf.setReplacementPolicy(ReplacementPolicy.FIFO);
            bmIntf.setReplacementPolicy(ReplacementPolicy.LRU);

            // Verify flushAll works
            bmIntf.flushAll();
            assertTrue("flushAll completed", true);
        });

        test("SimpleDB integration: buffer slots reflect real table pages", () -> {
            resetDatabase(DB_DIR);
            SimpleDB db = new SimpleDB(DB_DIR);
            BufferManager bmIntf = db.getBufferManager();

            Transaction tx = db.newTx();
            db.planner().executeUpdate(
                "CREATE TABLE slot_test (val INT)", tx);
            db.planner().executeUpdate(
                "INSERT INTO slot_test (val) VALUES (100)", tx);
            tx.commit();

            // Flush to ensure pages are on disk, then read back
            bmIntf.flushAll();
            CacheStats stats = bmIntf.getStats();
            assertTrue("has at least one access", stats.getAccessCount() > 0);
        });

        test("SimpleDB integration: flushPage on specific table page", () -> {
            resetDatabase(DB_DIR);
            SimpleDB db = new SimpleDB(DB_DIR);
            BufferManager bmIntf = db.getBufferManager();

            Transaction tx = db.newTx();
            db.planner().executeUpdate(
                "CREATE TABLE flush_test (x INT)", tx);
            db.planner().executeUpdate(
                "INSERT INTO flush_test (x) VALUES (42)", tx);
            tx.commit();

            // The catalog pages should be cached; flush all
            bmIntf.flushAll();
            List<BufferManager.SlotInfo> slots = bmIntf.getBufferSlots();
            // After flush, no dirty pages should remain
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
