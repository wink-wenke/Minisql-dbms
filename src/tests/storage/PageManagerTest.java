package tests.storage;

import java.io.File;
import java.util.*;
import simpledb.file.*;
import simpledb.storage.*;
import tests.TestBase;

/**
 * Unit tests for PageManager: page allocation, release, read/write, free list reuse.
 */
public class PageManagerTest extends TestBase {
    private static final int BLOCK_SIZE = 4096;
    private static final String TMP_DIR = "pagemgr_test_tmp";

    private StubFileMgr fm;
    private PageManagerImpl pm;

    private void setup() {
        cleanup();
        fm = new StubFileMgr(TMP_DIR, BLOCK_SIZE);
        pm = new PageManagerImpl(fm);
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
        return "PageManagerTest - allocate/free/read/write/reuse";
    }

    protected void cases() throws Exception {
        // ===== 页面分配 =====
        test("allocatePage returns valid PageId", () -> {
            setup();
            PageId pid = pm.allocatePage("test.tbl");
            assertEquals("fileName", "test.tbl", pid.fileName());
            assertTrue("pageNumber >= 0", pid.pageNumber() >= 0);
        });

        test("allocatePage increments page count", () -> {
            setup();
            assertEquals("initial count", 0, (long) pm.pageCount("test.tbl"));
            pm.allocatePage("test.tbl");
            assertEquals("after 1 alloc", 1, (long) pm.pageCount("test.tbl"));
            pm.allocatePage("test.tbl");
            assertEquals("after 2 alloc", 2, (long) pm.pageCount("test.tbl"));
        });

        test("allocatePage returns zeroed content", () -> {
            setup();
            PageId pid = pm.allocatePage("test.tbl");
            byte[] buf = new byte[BLOCK_SIZE];
            pm.readPage(pid, buf);
            for (int i = 0; i < BLOCK_SIZE; i++) {
                assertEquals("byte[" + i + "] should be 0", 0, (long) buf[i]);
            }
        });

        // ===== 页面写入与读取 =====
        test("writePage and readPage round-trip", () -> {
            setup();
            PageId pid = pm.allocatePage("test.tbl");
            byte[] data = new byte[BLOCK_SIZE];
            // write pattern
            for (int i = 0; i < BLOCK_SIZE; i++) data[i] = (byte) (i % 256);
            pm.writePage(pid, data);

            byte[] readBuf = new byte[BLOCK_SIZE];
            pm.readPage(pid, readBuf);
            for (int i = 0; i < BLOCK_SIZE; i++) {
                assertEquals("byte[" + i + "]", (long) data[i], (long) readBuf[i]);
            }
        });

        test("writePage overwrites previous content", () -> {
            setup();
            PageId pid = pm.allocatePage("test.tbl");

            byte[] first = new byte[BLOCK_SIZE];
            Arrays.fill(first, (byte) 0xFF);
            pm.writePage(pid, first);

            byte[] second = new byte[BLOCK_SIZE];
            Arrays.fill(second, (byte) 0x00);
            pm.writePage(pid, second);

            byte[] readBuf = new byte[BLOCK_SIZE];
            pm.readPage(pid, readBuf);
            for (int i = 0; i < BLOCK_SIZE; i++) {
                assertEquals("byte[" + i + "] should be 0", 0, (long) readBuf[i]);
            }
        });

        // ===== 页面释放 =====
        test("freePage adds to free list and increments free count", () -> {
            setup();
            PageId pid = pm.allocatePage("test.tbl");
            assertEquals("free count before", 0, (long) pm.freePageCount("test.tbl"));
            pm.freePage(pid);
            assertEquals("free count after", 1, (long) pm.freePageCount("test.tbl"));
        });

        test("freePage zeros the page content", () -> {
            setup();
            PageId pid = pm.allocatePage("test.tbl");
            byte[] data = new byte[BLOCK_SIZE];
            Arrays.fill(data, (byte) 0xAB);
            pm.writePage(pid, data);

            pm.freePage(pid);
            byte[] readBuf = new byte[BLOCK_SIZE];
            pm.readPage(pid, readBuf);
            for (int i = 0; i < BLOCK_SIZE; i++) {
                assertEquals("byte[" + i + "] should be 0 after free", 0, (long) readBuf[i]);
            }
        });

        // ===== 空闲页复用 =====
        test("allocatePage reuses freed page", () -> {
            setup();
            PageId p0 = pm.allocatePage("test.tbl");
            pm.allocatePage("test.tbl");
            assertEquals("page count", 2, (long) pm.pageCount("test.tbl"));

            pm.freePage(p0);
            assertEquals("free count", 1, (long) pm.freePageCount("test.tbl"));

            // next allocate should reuse the freed page number
            PageId reused = pm.allocatePage("test.tbl");
            assertEquals("reused page number", p0.pageNumber(), reused.pageNumber());
            assertEquals("free count after reuse", 0, (long) pm.freePageCount("test.tbl"));
        });

        test("reused page is zeroed", () -> {
            setup();
            PageId p0 = pm.allocatePage("test.tbl");
            byte[] data = new byte[BLOCK_SIZE];
            Arrays.fill(data, (byte) 0xCC);
            pm.writePage(p0, data);

            pm.freePage(p0);
            PageId reused = pm.allocatePage("test.tbl");

            byte[] readBuf = new byte[BLOCK_SIZE];
            pm.readPage(reused, readBuf);
            for (int i = 0; i < BLOCK_SIZE; i++) {
                assertEquals("byte[" + i + "] zero after reuse", 0, (long) readBuf[i]);
            }
        });

        test("multiple free/reuse cycles", () -> {
            setup();
            PageId p0 = pm.allocatePage("test.tbl");
            PageId p1 = pm.allocatePage("test.tbl");
            PageId p2 = pm.allocatePage("test.tbl");

            // free all three
            pm.freePage(p0);
            pm.freePage(p1);
            pm.freePage(p2);
            assertEquals("all freed", 3, (long) pm.freePageCount("test.tbl"));

            // reuse smallest page number first (TreeSet ordering)
            PageId r0 = pm.allocatePage("test.tbl");
            assertEquals("first reused", p0.pageNumber(), r0.pageNumber());
            PageId r1 = pm.allocatePage("test.tbl");
            assertEquals("second reused", p1.pageNumber(), r1.pageNumber());
            PageId r2 = pm.allocatePage("test.tbl");
            assertEquals("third reused", p2.pageNumber(), r2.pageNumber());
            assertEquals("free list empty", 0, (long) pm.freePageCount("test.tbl"));
        });

        // ===== 多文件管理 =====
        test("different files have independent page spaces", () -> {
            setup();
            PageId a0 = pm.allocatePage("a.tbl");
            pm.allocatePage("b.tbl");
            pm.allocatePage("a.tbl");

            assertEquals("a page count", 2, (long) pm.pageCount("a.tbl"));
            assertEquals("b page count", 1, (long) pm.pageCount("b.tbl"));
            assertEquals("a0 file", "a.tbl", a0.fileName());
        });

        test("free in one file does not affect another", () -> {
            setup();
            PageId a0 = pm.allocatePage("a.tbl");
            pm.allocatePage("b.tbl");
            pm.freePage(a0);

            assertEquals("a free count", 1, (long) pm.freePageCount("a.tbl"));
            assertEquals("b free count", 0, (long) pm.freePageCount("b.tbl"));
        });

        // ===== pageCount =====
        test("pageCount tracks all allocated pages", () -> {
            setup();
            for (int i = 0; i < 10; i++) pm.allocatePage("big.tbl");
            assertEquals("10 pages", 10, (long) pm.pageCount("big.tbl"));
        });

        test("pageCount for non-existent file is 0", () -> {
            setup();
            assertEquals("empty", 0, (long) pm.pageCount("noexist.tbl"));
        });

        // ===== default allocatePage =====
        test("allocatePage() without filename uses default.tbl", () -> {
            setup();
            PageId pid = pm.allocatePage();
            assertEquals("default file", "default.tbl", pid.fileName());
        });
    }

    // ---- In-memory FileMgr stub (same pattern as BufferMgrTest) ----
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

    public static void main(String[] args) {
        System.exit(new PageManagerTest().run() ? 0 : 1);
    }
}
