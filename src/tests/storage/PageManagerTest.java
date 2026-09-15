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
        // ===== 1. 页面分配 =====
        test("allocatePage returns valid PageId", () -> {
            setup();
            System.out.println("    [步骤] 创建 PageManager, 底层使用 StubFileMgr");
            PageId pid = pm.allocatePage("test.tbl");
            System.out.println("    [步骤] allocatePage(\"test.tbl\") → 分配一个新页面");
            System.out.println("    [结果] 返回 PageId(" + pid.fileName() + ", " + pid.pageNumber() + ")");
            assertEquals("fileName", "test.tbl", pid.fileName());
            assertTrue("pageNumber >= 0", pid.pageNumber() >= 0);
        });

        test("allocatePage increments page count", () -> {
            setup();
            System.out.println("    [步骤] 初始 pageCount(\"test.tbl\") = " + pm.pageCount("test.tbl"));
            assertEquals("initial count", 0, (long) pm.pageCount("test.tbl"));
            pm.allocatePage("test.tbl");
            System.out.println("    [步骤] 第 1 次 allocatePage → pageCount = " + pm.pageCount("test.tbl"));
            assertEquals("after 1 alloc", 1, (long) pm.pageCount("test.tbl"));
            pm.allocatePage("test.tbl");
            System.out.println("    [步骤] 第 2 次 allocatePage → pageCount = " + pm.pageCount("test.tbl"));
            assertEquals("after 2 alloc", 2, (long) pm.pageCount("test.tbl"));
        });

        test("allocatePage returns zeroed content", () -> {
            setup();
            System.out.println("    [步骤] allocatePage(\"test.tbl\") 分配新页面");
            PageId pid = pm.allocatePage("test.tbl");
            byte[] buf = new byte[BLOCK_SIZE];
            System.out.println("    [步骤] readPage 读取新分配的页面内容");
            pm.readPage(pid, buf);
            boolean allZero = true;
            for (int i = 0; i < BLOCK_SIZE; i++) {
                if (buf[i] != 0) { allZero = false; break; }
            }
            System.out.println("    [结果] 4096 字节全部为零 ? " + allZero);
            for (int i = 0; i < BLOCK_SIZE; i++) {
                assertEquals("byte[" + i + "] should be 0", 0, (long) buf[i]);
            }
        });

        // ===== 2. 页面写入与读取 =====
        test("writePage and readPage round-trip", () -> {
            setup();
            PageId pid = pm.allocatePage("test.tbl");
            byte[] data = new byte[BLOCK_SIZE];
            for (int i = 0; i < BLOCK_SIZE; i++) data[i] = (byte) (i % 256);
            System.out.println("    [步骤] writePage 写入模式: data[i] = i % 256");
            System.out.println("    [示例] data[0]=" + data[0] + ", data[1]=" + data[1] + ", data[255]=" + data[255] + ", data[256]=" + data[256]);
            pm.writePage(pid, data);

            byte[] readBuf = new byte[BLOCK_SIZE];
            System.out.println("    [步骤] readPage 读回, 逐字节对比");
            pm.readPage(pid, readBuf);
            boolean match = true;
            for (int i = 0; i < BLOCK_SIZE; i++) {
                if (data[i] != readBuf[i]) { match = false; break; }
            }
            System.out.println("    [结果] 写入与读取完全一致 ? " + match);
            for (int i = 0; i < BLOCK_SIZE; i++) {
                assertEquals("byte[" + i + "]", (long) data[i], (long) readBuf[i]);
            }
        });

        test("writePage overwrites previous content", () -> {
            setup();
            PageId pid = pm.allocatePage("test.tbl");

            byte[] first = new byte[BLOCK_SIZE];
            Arrays.fill(first, (byte) 0xFF);
            System.out.println("    [步骤] 第 1 次 writePage: 全部填充 0xFF");
            pm.writePage(pid, first);

            byte[] second = new byte[BLOCK_SIZE];
            Arrays.fill(second, (byte) 0x00);
            System.out.println("    [步骤] 第 2 次 writePage: 全部填充 0x00 (覆盖)");
            pm.writePage(pid, second);

            byte[] readBuf = new byte[BLOCK_SIZE];
            System.out.println("    [步骤] readPage 读回, 验证被覆盖为 0x00");
            pm.readPage(pid, readBuf);
            boolean allZero = true;
            for (int i = 0; i < BLOCK_SIZE; i++) {
                if (readBuf[i] != 0) { allZero = false; break; }
            }
            System.out.println("    [结果] 内容被完全覆盖为 0x00 ? " + allZero);
            for (int i = 0; i < BLOCK_SIZE; i++) {
                assertEquals("byte[" + i + "] should be 0", 0, (long) readBuf[i]);
            }
        });

        // ===== 3. 页面释放 =====
        test("freePage adds to free list and increments free count", () -> {
            setup();
            PageId pid = pm.allocatePage("test.tbl");
            System.out.println("    [步骤] allocatePage → pageCount=1, freePageCount=0");
            System.out.println("    [步骤] freePage 释放页面 → 加入空闲列表 (TreeSet)");
            pm.freePage(pid);
            System.out.println("    [结果] freePageCount = " + pm.freePageCount("test.tbl"));
            assertEquals("free count after", 1, (long) pm.freePageCount("test.tbl"));
        });

        test("freePage zeros the page content", () -> {
            setup();
            PageId pid = pm.allocatePage("test.tbl");
            byte[] data = new byte[BLOCK_SIZE];
            Arrays.fill(data, (byte) 0xAB);
            System.out.println("    [步骤] writePage 写入 0xAB 填充");
            pm.writePage(pid, data);

            System.out.println("    [步骤] freePage 释放页面 (应清零内容)");
            pm.freePage(pid);
            byte[] readBuf = new byte[BLOCK_SIZE];
            System.out.println("    [步骤] readPage 读回, 验证已清零");
            pm.readPage(pid, readBuf);
            boolean allZero = true;
            for (int i = 0; i < BLOCK_SIZE; i++) {
                if (readBuf[i] != 0) { allZero = false; break; }
            }
            System.out.println("    [结果] 释放后页面内容全部为零 ? " + allZero);
            for (int i = 0; i < BLOCK_SIZE; i++) {
                assertEquals("byte[" + i + "] should be 0 after free", 0, (long) readBuf[i]);
            }
        });

        // ===== 4. 空闲页复用 =====
        test("allocatePage reuses freed page", () -> {
            setup();
            PageId p0 = pm.allocatePage("test.tbl");
            PageId p1 = pm.allocatePage("test.tbl");
            System.out.println("    [步骤] 分配 p0(pageNum=" + p0.pageNumber() + "), p1(pageNum=" + p1.pageNumber() + ")");
            System.out.println("    [步骤] freePage(p0) → p0 加入空闲列表 TreeSet");
            pm.freePage(p0);
            System.out.println("    [步骤] freePageCount = " + pm.freePageCount("test.tbl"));

            System.out.println("    [步骤] 再次 allocatePage → 应从空闲列表取最小页号复用");
            PageId reused = pm.allocatePage("test.tbl");
            System.out.println("    [结果] reused.pageNumber = " + reused.pageNumber() + " (应等于 p0=" + p0.pageNumber() + ")");
            assertEquals("reused page number", p0.pageNumber(), reused.pageNumber());
            System.out.println("    [结果] freePageCount = " + pm.freePageCount("test.tbl") + " (空闲列表已空)");
            assertEquals("free count after reuse", 0, (long) pm.freePageCount("test.tbl"));
        });

        test("reused page is zeroed", () -> {
            setup();
            PageId p0 = pm.allocatePage("test.tbl");
            byte[] data = new byte[BLOCK_SIZE];
            Arrays.fill(data, (byte) 0xCC);
            System.out.println("    [步骤] writePage 写入 0xCC 填充到 p0");
            pm.writePage(p0, data);

            System.out.println("    [步骤] freePage(p0) → 清零并加入空闲列表");
            pm.freePage(p0);
            System.out.println("    [步骤] allocatePage → 复用 p0, 应自动清零");
            PageId reused = pm.allocatePage("test.tbl");

            byte[] readBuf = new byte[BLOCK_SIZE];
            System.out.println("    [步骤] readPage 读回复用后的页面");
            pm.readPage(reused, readBuf);
            boolean allZero = true;
            for (int i = 0; i < BLOCK_SIZE; i++) {
                if (readBuf[i] != 0) { allZero = false; break; }
            }
            System.out.println("    [结果] 复用后页面内容全部为零 ? " + allZero + " (安全性: 旧数据不泄露)");
            for (int i = 0; i < BLOCK_SIZE; i++) {
                assertEquals("byte[" + i + "] zero after reuse", 0, (long) readBuf[i]);
            }
        });

        test("multiple free/reuse cycles", () -> {
            setup();
            PageId p0 = pm.allocatePage("test.tbl");
            PageId p1 = pm.allocatePage("test.tbl");
            PageId p2 = pm.allocatePage("test.tbl");
            System.out.println("    [步骤] 分配 3 个页面: p0(0), p1(1), p2(2)");

            System.out.println("    [步骤] 依次释放 p0, p1, p2 → 空闲列表 = {0, 1, 2} (TreeSet 有序)");
            pm.freePage(p0);
            pm.freePage(p1);
            pm.freePage(p2);
            System.out.println("    [结果] freePageCount = " + pm.freePageCount("test.tbl"));

            System.out.println("    [步骤] 第 1 次 allocatePage → 取 TreeSet 最小值 0");
            PageId r0 = pm.allocatePage("test.tbl");
            System.out.println("    [结果] reused = pageNum " + r0.pageNumber());
            assertEquals("first reused", p0.pageNumber(), r0.pageNumber());

            System.out.println("    [步骤] 第 2 次 allocatePage → 取 TreeSet 最小值 1");
            PageId r1 = pm.allocatePage("test.tbl");
            System.out.println("    [结果] reused = pageNum " + r1.pageNumber());
            assertEquals("second reused", p1.pageNumber(), r1.pageNumber());

            System.out.println("    [步骤] 第 3 次 allocatePage → 取 TreeSet 最小值 2");
            PageId r2 = pm.allocatePage("test.tbl");
            System.out.println("    [结果] reused = pageNum " + r2.pageNumber());
            assertEquals("third reused", p2.pageNumber(), r2.pageNumber());

            System.out.println("    [结果] freePageCount = " + pm.freePageCount("test.tbl") + " (空闲列表已空)");
            assertEquals("free list empty", 0, (long) pm.freePageCount("test.tbl"));
        });

        // ===== 5. 多文件管理 =====
        test("different files have independent page spaces", () -> {
            setup();
            System.out.println("    [步骤] 为文件 a.tbl 分配页面");
            PageId a0 = pm.allocatePage("a.tbl");
            System.out.println("    [步骤] 为文件 b.tbl 分配页面");
            pm.allocatePage("b.tbl");
            System.out.println("    [步骤] 再为文件 a.tbl 分配页面");
            pm.allocatePage("a.tbl");

            System.out.println("    [结果] a.tbl pageCount = " + pm.pageCount("a.tbl"));
            System.out.println("    [结果] b.tbl pageCount = " + pm.pageCount("b.tbl"));
            System.out.println("    [逻辑] 每个文件有独立的页面编号空间, 互不干扰");
            assertEquals("a page count", 2, (long) pm.pageCount("a.tbl"));
            assertEquals("b page count", 1, (long) pm.pageCount("b.tbl"));
            assertEquals("a0 file", "a.tbl", a0.fileName());
        });

        test("free in one file does not affect another", () -> {
            setup();
            PageId a0 = pm.allocatePage("a.tbl");
            pm.allocatePage("b.tbl");
            System.out.println("    [步骤] a.tbl 分配 1 页, b.tbl 分配 1 页");
            System.out.println("    [步骤] freePage(a0) → 只释放 a.tbl 的页面");
            pm.freePage(a0);

            System.out.println("    [结果] a.tbl freePageCount = " + pm.freePageCount("a.tbl"));
            System.out.println("    [结果] b.tbl freePageCount = " + pm.freePageCount("b.tbl"));
            System.out.println("    [逻辑] 释放 a.tbl 不影响 b.tbl 的空闲列表");
            assertEquals("a free count", 1, (long) pm.freePageCount("a.tbl"));
            assertEquals("b free count", 0, (long) pm.freePageCount("b.tbl"));
        });

        // ===== 6. pageCount =====
        test("pageCount tracks all allocated pages", () -> {
            setup();
            System.out.println("    [步骤] 连续为 big.tbl 分配 10 个页面");
            for (int i = 0; i < 10; i++) pm.allocatePage("big.tbl");
            System.out.println("    [结果] pageCount = " + pm.pageCount("big.tbl"));
            assertEquals("10 pages", 10, (long) pm.pageCount("big.tbl"));
        });

        test("pageCount for non-existent file is 0", () -> {
            setup();
            System.out.println("    [步骤] 查询不存在的文件 \"noexist.tbl\"");
            System.out.println("    [结果] pageCount = " + pm.pageCount("noexist.tbl"));
            assertEquals("empty", 0, (long) pm.pageCount("noexist.tbl"));
        });

        // ===== 7. 默认文件名 =====
        test("allocatePage() without filename uses default.tbl", () -> {
            setup();
            System.out.println("    [步骤] 调用 allocatePage() 不传文件名");
            PageId pid = pm.allocatePage();
            System.out.println("    [结果] fileName = \"" + pid.fileName() + "\" (默认值)");
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
