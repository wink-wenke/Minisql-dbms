package simpledb.storage;

import java.util.*;
import simpledb.file.BlockId;
import simpledb.buffer.Buffer;
import simpledb.buffer.BufferMgr;

/**
 * 存储模块交互测试管理器。
 * 直接使用 BufferMgr 做原子 pin+modify+unpin，
 * 确保写入在同一缓冲帧内完成，不会因中间 unpin 导致淘汰。
 */
public class StorageTestManager {
    private static final String TEST_FILE = "storage_test.tbl";

    private final BufferMgr rawBm;
    private final BufferManager bufferManager;
    private final List<AllocatedPage> allocatedPages = new ArrayList<>();
    private final List<CacheEvent> accessHistory = new ArrayList<>();
    private int eventSeq = 0;

    public StorageTestManager(BufferManager bufferManager, BufferMgr rawBm) {
        this.bufferManager = bufferManager;
        this.rawBm = rawBm;
    }

    // ========== Page Operations ==========

    public synchronized Map<String, Object> allocPage() {
        int pageNum = allocatedPages.size();
        allocatedPages.add(new AllocatedPage(pageNum, TEST_FILE));

        BlockId blk = new BlockId(TEST_FILE, pageNum);
        Buffer buf = rawBm.pin(blk);
        buf.setModified(1, -1);
        rawBm.unpin(buf);

        CacheEvent event = new CacheEvent(++eventSeq, "ALLOC", TEST_FILE, pageNum, false);
        accessHistory.add(event);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("pageNum", pageNum);
        result.put("fileName", TEST_FILE);
        result.put("cacheEvent", event.toMap());
        result.put("stats", getStatsSnapshot());
        result.put("slots", getSlotsSnapshot());
        return result;
    }

    public synchronized Map<String, Object> freePage(int pageNum) {
        if (pageNum < 0 || pageNum >= allocatedPages.size() || allocatedPages.get(pageNum) == null) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", false);
            result.put("error", "Page " + pageNum + " not allocated");
            return result;
        }
        BlockId blk = new BlockId(TEST_FILE, pageNum);
        rawBm.flushPage(blk);
        allocatedPages.set(pageNum, null);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("pageNum", pageNum);
        result.put("stats", getStatsSnapshot());
        result.put("slots", getSlotsSnapshot());
        return result;
    }

    /**
     * 原子写入：pin → 修改字节 → 标记脏页 → unpin。
     */
    public synchronized Map<String, Object> writePage(int pageNum, int offset, int value) {
        if (pageNum < 0 || pageNum >= allocatedPages.size() || allocatedPages.get(pageNum) == null) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", false);
            result.put("error", "Page " + pageNum + " not allocated");
            return result;
        }

        BlockId blk = new BlockId(TEST_FILE, pageNum);
        Buffer buf = rawBm.pin(blk);

        // 原子修改：直接在缓冲区中写入
        java.nio.ByteBuffer contents = buf.contents().contents();
        contents.putInt(offset, value);

        buf.setModified(1, -1);
        rawBm.unpin(buf);

        CacheEvent event = new CacheEvent(++eventSeq, "WRITE", TEST_FILE, pageNum, false);
        accessHistory.add(event);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("pageNum", pageNum);
        result.put("offset", offset);
        result.put("value", value);
        result.put("cacheEvent", event.toMap());
        result.put("stats", getStatsSnapshot());
        result.put("slots", getSlotsSnapshot());
        return result;
    }

    /**
     * 原子读取：pin → 读取字节 → unpin。
     */
    public synchronized Map<String, Object> readPage(int pageNum, int offset) {
        if (pageNum < 0 || pageNum >= allocatedPages.size() || allocatedPages.get(pageNum) == null) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", false);
            result.put("error", "Page " + pageNum + " not allocated");
            return result;
        }

        BlockId blk = new BlockId(TEST_FILE, pageNum);
        Buffer buf = rawBm.pin(blk);

        int value = buf.contents().contents().getInt(offset);
        rawBm.unpin(buf);

        CacheEvent event = new CacheEvent(++eventSeq, "READ", TEST_FILE, pageNum, false);
        accessHistory.add(event);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("pageNum", pageNum);
        result.put("offset", offset);
        result.put("value", value);
        result.put("cacheEvent", event.toMap());
        result.put("stats", getStatsSnapshot());
        result.put("slots", getSlotsSnapshot());
        return result;
    }

    /**
     * 仅访问页面（pin → unpin），用于演示缓存命中/未命中。
     */
    public synchronized Map<String, Object> accessPage(int pageNum) {
        if (pageNum < 0 || pageNum >= allocatedPages.size() || allocatedPages.get(pageNum) == null) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", false);
            result.put("error", "Page " + pageNum + " not allocated");
            return result;
        }

        CacheStats before = bufferManager.getStats();
        BlockId blk = new BlockId(TEST_FILE, pageNum);
        Buffer buf = rawBm.pin(blk);
        rawBm.unpin(buf);
        CacheStats after = bufferManager.getStats();

        boolean hit = (after.getMissCount() == before.getMissCount());
        CacheEvent event = new CacheEvent(++eventSeq, "ACCESS", TEST_FILE, pageNum, hit);
        accessHistory.add(event);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("pageNum", pageNum);
        result.put("hit", hit);
        result.put("cacheEvent", event.toMap());
        result.put("stats", getStatsSnapshot());
        result.put("slots", getSlotsSnapshot());
        return result;
    }

    public synchronized void setPolicy(String policyName) {
        ReplacementPolicy policy = "FIFO".equalsIgnoreCase(policyName)
            ? ReplacementPolicy.FIFO : ReplacementPolicy.LRU;
        rawBm.setReplacementPolicy(policy);
    }

    public synchronized Map<String, Object> getState() {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("allocatedPages", getAllocatedPagesList());
        state.put("stats", getStatsSnapshot());
        state.put("slots", getSlotsSnapshot());
        state.put("accessHistory", getAccessHistoryList());
        return state;
    }

    public synchronized Map<String, Object> reset() {
        rawBm.flushAllDirty();
        rawBm.getStats().reset();  // 重置缓存统计
        allocatedPages.clear();
        accessHistory.clear();
        eventSeq = 0;
        return getState();
    }

    // ========== Internal Helpers ==========

    private Map<String, Object> getStatsSnapshot() {
        CacheStats stats = bufferManager.getStats();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("accessCount", stats.getAccessCount());
        m.put("hitCount", stats.getHitCount());
        m.put("missCount", stats.getMissCount());
        m.put("evictionCount", stats.getEvictionCount());
        m.put("hitRate", stats.hitRate());
        return m;
    }

    private List<Map<String, Object>> getSlotsSnapshot() {
        List<BufferManager.SlotInfo> raw = bufferManager.getBufferSlots();
        List<Map<String, Object>> list = new ArrayList<>();
        for (BufferManager.SlotInfo s : raw) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("slot", s.slotIndex);
            m.put("file", s.fileName);
            m.put("block", s.blockNumber);
            m.put("pins", s.pinCount);
            m.put("dirty", s.dirty);
            m.put("txnum", s.txnum);
            list.add(m);
        }
        return list;
    }

    private List<Map<String, Object>> getAllocatedPagesList() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (AllocatedPage p : allocatedPages) {
            if (p != null) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("pageNum", p.pageNum);
                m.put("fileName", p.fileName);
                list.add(m);
            }
        }
        return list;
    }

    private List<Map<String, Object>> getAccessHistoryList() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (CacheEvent e : accessHistory) {
            list.add(e.toMap());
        }
        return list;
    }

    // ========== Inner Classes ==========

    private static class AllocatedPage {
        final int pageNum;
        final String fileName;
        AllocatedPage(int pageNum, String fileName) {
            this.pageNum = pageNum;
            this.fileName = fileName;
        }
    }

    private static class CacheEvent {
        final int seq;
        final String type;
        final String file;
        final int block;
        final boolean hit;

        CacheEvent(int seq, String type, String file, int block, boolean hit) {
            this.seq = seq;
            this.type = type;
            this.file = file;
            this.block = block;
            this.hit = hit;
        }

        Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("seq", seq);
            m.put("type", type);
            m.put("file", file);
            m.put("block", block);
            m.put("hit", hit);
            return m;
        }
    }
}
