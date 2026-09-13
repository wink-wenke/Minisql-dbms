package tests;

import java.util.*;

/**
 * Entry point for the engine test suites.
 *
 * Run it from the project root:
 *   java -cp build tests.RunAllTests
 */
public class RunAllTests {
   public static void main(String[] args) {
      List<TestBase> suites = new ArrayList<>();
      suites.add(new ExecutorUnitTest());
      suites.add(new EngineTest());
      suites.add(new PersistenceTest());

      int failed = 0;
      for (TestBase suite : suites)
         if (!suite.run())
            failed++;

      System.out.println("----------------------------------------");
      if (failed == 0)
         System.out.println("ALL SUITES PASSED");
      else
         System.out.println(failed + " SUITE(S) FAILED");
      System.exit(failed == 0 ? 0 : 1);
   }
}
