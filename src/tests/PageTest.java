package tests;

import simpledb.file.Page;

/**
 * Unit tests for Page read/write operations.
 */
public class PageTest extends TestBase {
   private static final int BLOCK_SIZE = 4096;

   protected String suiteName() {
      return "PageTest - read/write";
   }

   protected void cases() throws Exception {
      test("getInt / setInt round-trip", () -> {
         Page p = new Page(BLOCK_SIZE);
         p.setInt(0, 42);
         assertEquals("getInt", 42, (long) p.getInt(0));
      });

      test("setInt at different offsets", () -> {
         Page p = new Page(BLOCK_SIZE);
         p.setInt(0, 100);
         p.setInt(4, 200);
         p.setInt(8, 300);
         assertEquals("offset 0", 100, (long) p.getInt(0));
         assertEquals("offset 4", 200, (long) p.getInt(4));
         assertEquals("offset 8", 300, (long) p.getInt(8));
      });

      test("getString / setString round-trip", () -> {
         Page p = new Page(BLOCK_SIZE);
         p.setString(0, "Alice");
         assertEquals("getString", "Alice", p.getString(0));
      });

      test("getString at different offsets", () -> {
         Page p = new Page(BLOCK_SIZE);
         p.setString(0, "Hello");
         p.setString(100, "World");
         assertEquals("first", "Hello", p.getString(0));
         assertEquals("second", "World", p.getString(100));
      });

      test("getBytes / setBytes round-trip", () -> {
         Page p = new Page(BLOCK_SIZE);
         byte[] data = {1, 2, 3, 4, 5};
         p.setBytes(0, data);
         byte[] got = p.getBytes(0);
         assertEquals("length", (long) data.length, (long) got.length);
         for (int i = 0; i < data.length; i++)
            assertEquals("byte[" + i + "]", (long) data[i], (long) got[i]);
      });

      test("maxLength computes correct bound", () -> {
         int max = Page.maxLength(10);
         assertTrue("maxLength > 10", max > 10);
         assertTrue("maxLength includes length prefix", max >= Integer.BYTES + 10);
      });

      test("contents() returns readable buffer", () -> {
         Page p = new Page(BLOCK_SIZE);
         p.setInt(0, 999);
         java.nio.ByteBuffer buf = p.contents();
         assertEquals("buffer position", 0, (long) buf.position());
         assertEquals("buffer int", 999, (long) buf.getInt());
      });

      test("negative values round-trip", () -> {
         Page p = new Page(BLOCK_SIZE);
         p.setInt(0, -1);
         assertEquals("negative", -1, (long) p.getInt(0));
      });

      test("zero fills fresh page", () -> {
         Page p = new Page(BLOCK_SIZE);
         for (int i = 0; i < 16; i++)
            assertEquals("zero at " + i, 0, (long) p.getInt(i * 4));
      });
   }

   public static void main(String[] args) {
      System.exit(new PageTest().run() ? 0 : 1);
   }
}
