package simpledb.logical;

/**
 * One aggregation request inside a GROUP BY: a function and the column it
 * applies to. The physical side maps each entry to the matching SimpleDB
 * {@code AggregationFn} in PlanConverterImpl.
 */
public class AggregateSpec {
   public enum Func {
      COUNT, SUM, AVG, MIN, MAX
   }

   private final Func func;
   private final String column;

   public AggregateSpec(Func func, String column) {
      this.func = func;
      this.column = column;
   }

   public Func func() {
      return func;
   }

   public String column() {
      return column;
   }

   /**
    * The output field name, kept in lock-step with the physical function
    * classes (CountFn -> "countof", SumFn -> "sumof", ...). This is what
    * lets the logical output schema and the executed result column names
    * agree.
    */
   public String resultFieldName() {
      switch (func) {
         case COUNT: return "countof" + column;
         case SUM:   return "sumof" + column;
         case AVG:   return "avgof" + column;
         case MIN:   return "minof" + column;
         case MAX:   return "maxof" + column;
         default:    throw new IllegalStateException("unknown aggregate " + func);
      }
   }

   public String explain() {
      return func.toString().toLowerCase() + "(" + column + ")";
   }
}
