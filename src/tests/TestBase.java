package tests;

import java.io.File;
import java.util.*;
import simpledb.engine.EngineException;

/**
 * Minimal assertion harness shared by the engine test suites.
 *
 * The project carries no JUnit dependency, so a suite extends this class and
 * registers its cases through test(). Running a suite prints one line per
 * case and reports whether everything passed.
 */
public abstract class TestBase {
   public interface Case {
      void run() throws Exception;
   }

   private final List<String> failures = new ArrayList<>();
   private int passed;

   protected abstract String suiteName();

   protected abstract void cases() throws Exception;

   public final boolean run() {
      System.out.println("=== " + suiteName() + " ===");
      try {
         cases();
      }
      catch (Throwable t) {
         failures.add("suite aborted: " + describe(t));
      }
      for (String failure : failures)
         System.out.println("  FAIL  " + failure);
      System.out.println("  " + passed + " passed, " + failures.size() + " failed");
      System.out.println();
      return failures.isEmpty();
   }

   protected void test(String name, Case body) {
      try {
         body.run();
         passed++;
         System.out.println("  ok    " + name);
      }
      catch (Throwable t) {
         failures.add(name + " -> " + describe(t));
      }
   }

   protected void assertTrue(String what, boolean condition) {
      if (!condition)
         throw new AssertionError("expected " + what);
   }

   protected void assertEquals(String what, Object expected, Object actual) {
      boolean same = expected == null ? actual == null : expected.equals(actual);
      if (!same)
         throw new AssertionError(what + ": expected <" + expected + "> but was <" + actual + ">");
   }

   /**
    * Asserts that a call is rejected with one specific engine error type.
    * A wrong type counts as a failure, which is what makes the error
    * classification itself testable.
    */
   protected void expectError(EngineException.ErrorType expected, Case body) {
      try {
         body.run();
      }
      catch (EngineException e) {
         assertEquals("error type", expected, e.getType());
         return;
      }
      catch (Throwable t) {
         throw new AssertionError("expected EngineException[" + expected
               + "] but got " + describe(t));
      }
      throw new AssertionError("expected EngineException[" + expected
            + "] but the call succeeded");
   }

   protected String describe(Throwable t) {
      if (t instanceof EngineException)
         return ((EngineException) t).formatted();
      return t.getClass().getSimpleName() + ": " + t.getMessage();
   }

   /**
    * Deletes the database directory so the next SimpleDB starts from scratch.
    * FileMgr treats a missing directory as a new database, which is exactly
    * what makes these suites repeatable.
    */
   protected static void resetDatabase(String dirname) {
      File dir = new File(dirname);
      if (!dir.exists())
         return;
      File[] files = dir.listFiles();
      if (files != null)
         for (File file : files)
            file.delete();
      dir.delete();
   }
}
