package simpledb.materialize;

import java.util.*;

import simpledb.query.*;

/**
 * A comparator for scans.
 * @author Edward Sciore
 */
public class RecordComparator implements Comparator<Scan> {
   private List<String> fields;
   
   public RecordComparator(List<String> fields) {
      this.fields = fields;
   }
   
   /** 返回排序字段列表（供可视化使用）。 */
   public List<String> sortFields() { return fields; }

   public int compare(Scan s1, Scan s2) {
      for (String fldname : fields) {
         Constant val1 = s1.getVal(fldname);
         Constant val2 = s2.getVal(fldname);
         int result = val1.compareTo(val2);
         if (result != 0)
            return result;
      }
      return 0;
   }
}
