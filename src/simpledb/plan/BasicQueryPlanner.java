package simpledb.plan;

import java.util.*;
import simpledb.tx.Transaction;
import simpledb.metadata.*;
import simpledb.parse.*;
import simpledb.materialize.SortPlan;

/**
 * The simplest, most naive query planner possible.
 * @author Edward Sciore
 */
public class BasicQueryPlanner implements QueryPlanner {
   private MetadataMgr mdm;
   
   public BasicQueryPlanner(MetadataMgr mdm) {
      this.mdm = mdm;
   }
   
   public Plan createPlan(QueryData data, Transaction tx) {
      //Step 1: Create a plan for each mentioned table or view.
     List<Plan> plans = new ArrayList<>();
      for (String tblname : data.tables()) {
         String viewdef = mdm.getViewDef(tblname, tx);
         if (viewdef != null) {
            Parser parser = new Parser(viewdef);
            QueryData viewdata = parser.query();
            plans.add(createPlan(viewdata, tx));
         }
         else
            plans.add(new TablePlan(tx, tblname, mdm));
      }
      
      //Step 2: Create the product of all table plans
      Plan p = plans.remove(0);
      for (Plan nextplan : plans)
         p = new ProductPlan(p, nextplan);
      
      //Step 3: Add a selection plan for the predicate
      p = new SelectPlan(p, data.pred());
      
      //Step 4: Add ORDER BY sort plan (before projection, so sort field may not be selected)
      List<String> orderby = data.orderby();
      if (orderby != null && !orderby.isEmpty()) {
         p = new SortPlan(tx, p, orderby);
      }
      
      //Step 5: Project on the field names (skip for SELECT *)
      List<String> fields = data.fields();
      if (!(fields.size() == 1 && fields.get(0).equals("*"))) {
         p = new ProjectPlan(p, fields);
      }
      
      return p;
   }
}
