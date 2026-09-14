package simpledb.materialize;

import simpledb.query.*;

/**
 * The <i>avg</i> aggregation function.
 *
 * Keeps a running total and a record count, returning their integer quotient
 * as the group's average. SimpleDB only stores integer constants, so the
 * average is truncated to an int (this matches the course's storage model).
 */
public class AvgFn implements AggregationFn {
   private String fldname;
   private int sum;
   private int count;

   /**
    * Create an avg aggregation function for the specified field.
    * @param fldname the name of the aggregated field
    */
   public AvgFn(String fldname) {
      this.fldname = fldname;
   }

   public void processFirst(Scan s) {
      sum = s.getVal(fldname).asInt();
      count = 1;
   }

   public void processNext(Scan s) {
      sum += s.getVal(fldname).asInt();
      count++;
   }

   /**
    * Return the field's name, prepended by "avgof".
    */
   public String fieldName() {
      return "avgof" + fldname;
   }

   public Constant value() {
      return new Constant(sum / count);
   }
}
