package simpledb.materialize;

import simpledb.query.*;

/**
 * The <i>min</i> aggregation function.
 *
 * Tracks the smallest value of the aggregated field seen so far in the group.
 * Identical to MaxFn except the comparison direction is reversed.
 */
public class MinFn implements AggregationFn {
   private String fldname;
   private Constant val;

   /**
    * Create a min aggregation function for the specified field.
    * @param fldname the name of the aggregated field
    */
   public MinFn(String fldname) {
      this.fldname = fldname;
   }

   public void processFirst(Scan s) {
      val = s.getVal(fldname);
   }

   public void processNext(Scan s) {
      Constant newval = s.getVal(fldname);
      if (newval.compareTo(val) < 0)
         val = newval;
   }

   /**
    * Return the field's name, prepended by "minof".
    */
   public String fieldName() {
      return "minof" + fldname;
   }

   public Constant value() {
      return val;
   }
}
