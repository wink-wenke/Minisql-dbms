package simpledb.materialize;

import simpledb.query.*;

/**
 * The <i>sum</i> aggregation function.
 *
 * Accumulates the integer value of the aggregated field across every record
 * of the current group. Mirrors the shape of SimpleDB's CountFn/MaxFn so it
 * can be dropped straight into a GroupByPlan.
 */
public class SumFn implements AggregationFn {
   private String fldname;
   private int sum;

   /**
    * Create a sum aggregation function for the specified field.
    * @param fldname the name of the aggregated field
    */
   public SumFn(String fldname) {
      this.fldname = fldname;
   }

   public void processFirst(Scan s) {
      sum = s.getVal(fldname).asInt();
   }

   public void processNext(Scan s) {
      sum += s.getVal(fldname).asInt();
   }

   /**
    * Return the field's name, prepended by "sumof".
    */
   public String fieldName() {
      return "sumof" + fldname;
   }

   public Constant value() {
      return new Constant(sum);
   }
}
