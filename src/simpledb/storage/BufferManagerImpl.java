package simpledb.storage;

import simpledb.file.BlockId;
import simpledb.file.Page;
import simpledb.buffer.Buffer;
import simpledb.buffer.BufferMgr;

/**
 * BufferManager 的实现。包装升级后的 BufferMgr。
 */
public class BufferManagerImpl implements BufferManager {
    private final BufferMgr bm;

    public BufferManagerImpl(BufferMgr bm) {
        this.bm = bm;
    }

    @Override
    public byte[] getPage(PageId pageId) {
        BlockId blk = toBlockId(pageId);
        Buffer buff = bm.pin(blk);
        Page contents = buff.contents();
        java.nio.ByteBuffer bb = contents.contents();
        byte[] data = new byte[bb.capacity()];
        bb.position(0);
        bb.get(data);
        // 注意：不在此处 unpin，调用方需通过 unpinPage() 释放
        return data;
    }

    @Override
    public void flushPage(PageId pageId) {
        BlockId blk = toBlockId(pageId);
        bm.flushPage(blk);
    }

    @Override
    public void flushAll() {
        bm.flushAllDirty();
    }

    @Override
    public void unpinPage(PageId pageId) {
        BlockId blk = toBlockId(pageId);
        bm.unpinPage(blk);
    }

    @Override
    public CacheStats getStats() {
        return bm.getStats();
    }

    @Override
    public void setReplacementPolicy(ReplacementPolicy policy) {
        bm.setReplacementPolicy(policy);
    }

    private BlockId toBlockId(PageId pageId) {
        return new BlockId(pageId.fileName(), pageId.pageNumber());
    }
}
