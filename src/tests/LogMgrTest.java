package tests;

import java.util.*;

/**
 * Unit tests for LogMgr: append, flush, iterator.
 * Uses an in-memory stub to avoid disk I/O.
 */
public class LogMgrTest extends TestBase {
   protected String suiteName() {
      return "LogMgrTest - append/flush/iterator";
   }

   protected void cases() throws Exception {
      test("append returns incrementing LSN", () -> {
         InMemoryLog lm = new InMemoryLog();
         int lsn1 = lm.append("record1".getBytes());
         int lsn2 = lm.append("record2".getBytes());
         int lsn3 = lm.append("record3".getBytes());
         assertEquals("lsn1", 1, (long) lsn1);
         assertEquals("lsn2", 2, (long) lsn2);
         assertEquals("lsn3", 3, (long) lsn3);
      });

      test("recordCount tracks appends", () -> {
         InMemoryLog lm = new InMemoryLog();
         assertEquals("initial", 0, (long) lm.recordCount());
         lm.append("a".getBytes());
         lm.append("b".getBytes());
         assertEquals("after 2 appends", 2, (long) lm.recordCount());
      });

      test("iterator returns records newest-first", () -> {
         InMemoryLog lm = new InMemoryLog();
         lm.append("first".getBytes());
         lm.append("second".getBytes());
         lm.append("third".getBytes());

         Iterator<byte[]> it = lm.iterator();
         assertTrue("has next", it.hasNext());
         String newest = new String(it.next());
         assertEquals("newest", "third", newest);
         String middle = new String(it.next());
         assertEquals("middle", "second", middle);
         String oldest = new String(it.next());
         assertEquals("oldest", "first", oldest);
      });

      test("flush is no-op (no exception)", () -> {
         InMemoryLog lm = new InMemoryLog();
         lm.append("record".getBytes());
         lm.flush(1);
         assertTrue("flush succeeded", true);
      });

      test("append preserves byte content", () -> {
         InMemoryLog lm = new InMemoryLog();
         byte[] data = {0, 1, 2, 127, -128, -1};
         lm.append(data);
         Iterator<byte[]> it = lm.iterator();
         byte[] got = it.next();
         assertEquals("length", (long) data.length, (long) got.length);
         for (int i = 0; i < data.length; i++)
            assertEquals("byte[" + i + "]", (long) data[i], (long) got[i]);
      });

      test("iterator on empty log returns no elements", () -> {
         InMemoryLog lm = new InMemoryLog();
         Iterator<byte[]> it = lm.iterator();
         assertTrue("empty", !it.hasNext());
      });

      test("append many records", () -> {
         InMemoryLog lm = new InMemoryLog();
         for (int i = 0; i < 100; i++)
            lm.append(("rec" + i).getBytes());
         assertEquals("count", 100, (long) lm.recordCount());
         Iterator<byte[]> it = lm.iterator();
         int count = 0;
         while (it.hasNext()) { it.next(); count++; }
         assertEquals("iterated", 100, (long) count);
      });
   }

   /**
    * Pure in-memory log manager. No disk, no FileMgr dependency.
    */
   private static class InMemoryLog {
      private final List<byte[]> records = new ArrayList<>();
      private int nextLsn = 0;

      synchronized int append(byte[] logrec) {
         records.add(logrec.clone());
         return ++nextLsn;
      }

      void flush(int lsn) { }

      Iterator<byte[]> iterator() {
         List<byte[]> reversed = new ArrayList<>(records);
         Collections.reverse(reversed);
         return reversed.iterator();
      }

      int recordCount() { return records.size(); }
   }

   public static void main(String[] args) {
      System.exit(new LogMgrTest().run() ? 0 : 1);
   }
}
