package simpledb.materialize;

import java.util.*;

import simpledb.query.*;
import simpledb.ast.OrderByEntry;

/**
 * A comparator for scans, supporting ASC/DESC direction.
 * @author Edward Sciore
 */
public class RecordComparator implements Comparator<Scan> {
   private List<OrderByEntry> entries;

   public RecordComparator(List<OrderByEntry> entries) {
      this.entries = entries;
   }

   /** 返回排序项列表（供可视化使用）。 */
   public List<OrderByEntry> sortEntries() { return entries; }

   public int compare(Scan s1, Scan s2) {
      for (OrderByEntry entry : entries) {
         Constant val1 = s1.getVal(entry.field());
         Constant val2 = s2.getVal(entry.field());
         int result = val1.compareTo(val2);
         if (!entry.isAscending()) result = -result;
         if (result != 0)
            return result;
      }
      return 0;
   }
}
