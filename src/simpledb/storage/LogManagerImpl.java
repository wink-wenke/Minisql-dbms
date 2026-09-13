package simpledb.storage;

import java.util.Iterator;
import simpledb.log.LogMgr;

/**
 * LogManager 的实现。内部持有 LogMgr，方法直接委托。
 */
public class LogManagerImpl implements LogManager {
    private final LogMgr lm;

    public LogManagerImpl(LogMgr lm) {
        this.lm = lm;
    }

    @Override
    public long append(byte[] record) {
        return lm.append(record);
    }

    @Override
    public void flush(long lsn) {
        lm.flush((int) lsn);
    }

    @Override
    public Iterator<byte[]> iterator() {
        return lm.iterator();
    }
}
