package tests;

import java.util.*;
import tests.storage.BufferMgrTest;
import tests.storage.PageManagerTest;
import tests.storage.BufferManagerImplTest;

/**
 * Entry point for the engine test suites.
 *
 * Run it from the project root:
 *   java -cp build tests.RunAllTests
 */
public class RunAllTests {
   public static void main(String[] args) {
      List<TestBase> suites = new ArrayList<>();
      // storage module unit tests
      suites.add(new BufferMgrTest());
      suites.add(new PageManagerTest());
      suites.add(new BufferManagerImplTest());
      // engine + integration tests
      suites.add(new ExecutorUnitTest());
      suites.add(new FullPipelineTest());
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
