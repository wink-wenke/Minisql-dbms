package tests;

import simpledb.storage.CacheStats;

/**
 * Unit tests for CacheStats.
 */
public class CacheStatsTest extends TestBase {
   protected String suiteName() {
      return "CacheStatsTest - statistics";
   }

   protected void cases() throws Exception {
      test("fresh stats are all zero", () -> {
         CacheStats s = new CacheStats();
         assertEquals("accessCount", 0, s.getAccessCount());
         assertEquals("hitCount", 0, s.getHitCount());
         assertEquals("missCount", 0, s.getMissCount());
         assertEquals("evictionCount", 0, s.getEvictionCount());
      });

      test("recordHit increments access and hit", () -> {
         CacheStats s = new CacheStats();
         s.recordHit();
         s.recordHit();
         assertEquals("accessCount", 2, s.getAccessCount());
         assertEquals("hitCount", 2, s.getHitCount());
         assertEquals("missCount", 0, s.getMissCount());
      });

      test("recordMiss increments access and miss", () -> {
         CacheStats s = new CacheStats();
         s.recordMiss();
         assertEquals("accessCount", 1, s.getAccessCount());
         assertEquals("missCount", 1, s.getMissCount());
         assertEquals("hitCount", 0, s.getHitCount());
      });

      test("recordEviction increments eviction count", () -> {
         CacheStats s = new CacheStats();
         s.recordEviction();
         s.recordEviction();
         s.recordEviction();
         assertEquals("evictionCount", 3, s.getEvictionCount());
      });

      test("hitRate computes correctly", () -> {
         CacheStats s = new CacheStats();
         s.recordHit();
         s.recordHit();
         s.recordMiss();
         double rate = s.hitRate();
         assertEquals("hitRate", 2.0 / 3.0, rate);
      });

      test("hitRate is 0 when no accesses", () -> {
         CacheStats s = new CacheStats();
         assertEquals("hitRate", 0.0, s.hitRate());
      });

      test("reset clears all counters", () -> {
         CacheStats s = new CacheStats();
         s.recordHit();
         s.recordMiss();
         s.recordEviction();
         s.reset();
         assertEquals("accessCount", 0, s.getAccessCount());
         assertEquals("hitCount", 0, s.getHitCount());
         assertEquals("missCount", 0, s.getMissCount());
         assertEquals("evictionCount", 0, s.getEvictionCount());
      });

      test("toString contains all stats", () -> {
         CacheStats s = new CacheStats();
         s.recordHit();
         String str = s.toString();
         assertTrue("has access", str.contains("1"));
         assertTrue("has hit rate", str.contains("%"));
      });
   }

   public static void main(String[] args) {
      System.exit(new CacheStatsTest().run() ? 0 : 1);
   }
}
