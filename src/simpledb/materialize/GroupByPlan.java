package simpledb.materialize;

import java.util.*;
import simpledb.tx.Transaction;
import simpledb.record.Schema;
import simpledb.plan.Plan;
import simpledb.query.*;
import static java.sql.Types.*;

/**
 * The Plan class for the <i>groupby</i> operator.
 * @author Edward Sciore
 */
public class GroupByPlan implements Plan {
   private Plan p;
   private List<String> groupfields;
   private List<AggregationFn> aggfns;
   private Schema sch = new Schema();
   
   public GroupByPlan(Transaction tx, Plan p, List<String> groupfields, List<AggregationFn> aggfns) {
      this.p = new SortPlan(tx, p, groupfields);
      this.groupfields = groupfields;
      this.aggfns = aggfns;
      for (String fldname : groupfields)
         sch.add(fldname, p.schema());
      for (AggregationFn fn : aggfns) {
         String srcField = extractSourceField(fn.fieldName());
         if (fn instanceof CountFn || fn instanceof SumFn) {
            sch.addIntField(fn.fieldName());
         } else if (p.schema().hasField(srcField)) {
            int type = p.schema().type(srcField);
            int length = p.schema().length(srcField);
            sch.addField(fn.fieldName(), type, length);
         } else {
            sch.addIntField(fn.fieldName());
         }
      }
   }

   /**
    * 从聚合字段名中提取源字段名。
    * countofid → id, maxofname → name
    */
   private String extractSourceField(String aggFieldName) {
      if (aggFieldName.startsWith("countof")) return aggFieldName.substring(7);
      if (aggFieldName.startsWith("maxof"))   return aggFieldName.substring(5);
      if (aggFieldName.startsWith("minof"))   return aggFieldName.substring(5);
      if (aggFieldName.startsWith("sumof"))   return aggFieldName.substring(5);
      return aggFieldName;
   }
   
   public Scan open() {
      Scan s = p.open();
      return new GroupByScan(s, groupfields, aggfns);
   }
   
   public int blocksAccessed() {
      return p.blocksAccessed();
   }
   
   public int recordsOutput() {
      int numgroups = 1;
      for (String fldname : groupfields)
         numgroups *= p.distinctValues(fldname);
      return numgroups;
   }
   
   public int distinctValues(String fldname) {
      if (p.schema().hasField(fldname))
         return p.distinctValues(fldname);
      else
         return recordsOutput();
   }
   
   public Schema schema() {
      return sch;
   }

   public Plan child() { return p; }
   public List<String> groupFields() { return groupfields; }
   public List<AggregationFn> aggFunctions() { return aggfns; }
}
