package tests.record;

import simpledb.record.Layout;
import simpledb.record.Schema;

public class LayoutTest {
   public static void main(String[] args) throws Exception {
      Schema sch = new Schema();
      sch.addIntField("A");
      sch.addStringField("B", 9);
      Layout layout = new Layout(sch);
      for (String fldname : layout.schema().fields()) {
         int offset = layout.offset(fldname);
         System.out.println(fldname + " has offset " + offset);
      }
   }
}
