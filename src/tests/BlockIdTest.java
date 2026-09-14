package tests;

import simpledb.file.BlockId;

/**
 * Unit tests for BlockId value object.
 */
public class BlockIdTest extends TestBase {
   protected String suiteName() {
      return "BlockIdTest - value object";
   }

   protected void cases() throws Exception {
      test("fileName and number are accessible", () -> {
         BlockId b = new BlockId("test.tbl", 5);
         assertEquals("fileName", "test.tbl", b.fileName());
         assertEquals("number", 5, (long) b.number());
      });

      test("equal blocks are equals", () -> {
         BlockId a = new BlockId("test.tbl", 3);
         BlockId b = new BlockId("test.tbl", 3);
         assertEquals("equals", true, a.equals(b));
      });

      test("different filename makes blocks unequal", () -> {
         BlockId a = new BlockId("a.tbl", 3);
         BlockId b = new BlockId("b.tbl", 3);
         assertEquals("equals", false, a.equals(b));
      });

      test("different block number makes blocks unequal", () -> {
         BlockId a = new BlockId("test.tbl", 1);
         BlockId b = new BlockId("test.tbl", 2);
         assertEquals("equals", false, a.equals(b));
      });

      test("equal blocks have same hashCode", () -> {
         BlockId a = new BlockId("test.tbl", 7);
         BlockId b = new BlockId("test.tbl", 7);
         assertEquals("hashCode", a.hashCode(), b.hashCode());
      });

      test("toString is readable", () -> {
         BlockId b = new BlockId("student.tbl", 0);
         String s = b.toString();
         assertTrue("contains file", s.contains("student.tbl"));
         assertTrue("contains block", s.contains("0"));
      });
   }

   public static void main(String[] args) {
      System.exit(new BlockIdTest().run() ? 0 : 1);
   }
}
