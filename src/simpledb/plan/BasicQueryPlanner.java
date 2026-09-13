package simpledb.plan;

import java.util.*;
import simpledb.tx.Transaction;
import simpledb.metadata.*;
import simpledb.parse.*;
import simpledb.query.*;
import simpledb.record.Schema;
import simpledb.materialize.SortPlan;
import simpledb.materialize.GroupByPlan;
import simpledb.materialize.AggregationFn;

/**
 * 基础查询计划器，支持谓词下推优化。
 * <p>
 * 优化策略：
 * <ul>
 *   <li>Predicate Pushdown：将过滤条件下推到各个表，尽早过滤数据</li>
 *   <li>对于单表查询，直接将谓词下推到 TablePlan</li>
 *   <li>对于多表查询，将只涉及单表的谓词下推到对应的表</li>
 * </ul>
 *
 * @author Edward Sciore (原始), 增强版
 */
public class BasicQueryPlanner implements QueryPlanner {
   private MetadataMgr mdm;

   public BasicQueryPlanner(MetadataMgr mdm) {
      this.mdm = mdm;
   }

   public Plan createPlan(QueryData data, Transaction tx) {
      //Step 1: Create a plan for each mentioned table or view.
      List<Plan> plans = new ArrayList<>();
      List<String> tableNames = new ArrayList<>(data.tables());

      for (String tblname : tableNames) {
         String viewdef = mdm.getViewDef(tblname, tx);
         if (viewdef != null) {
            Parser parser = new Parser(viewdef);
            QueryData viewdata = parser.query();
            plans.add(createPlan(viewdata, tx));
         } else {
            Plan tablePlan = new TablePlan(tx, tblname, mdm);

            // Predicate Pushdown：将只涉及该表的谓词下推
            Predicate tablePred = data.pred().selectSubPred(tablePlan.schema());
            if (tablePred != null && !tablePred.isAlwaysTrue()) {
               tablePlan = new SelectPlan(tablePlan, tablePred);
            }

            plans.add(tablePlan);
         }
      }

      //Step 2: Create the product of all table plans
      Plan p = plans.remove(0);
      for (Plan nextplan : plans)
         p = new ProductPlan(p, nextplan);

      //Step 3: Add a selection plan for the remaining predicate
      //        (谓词中不能下推的部分，如跨表连接条件）
      Predicate remainingPred = removeAppliedPredicates(data.pred(), tableNames, tx);
      if (remainingPred != null && !remainingPred.isAlwaysTrue()) {
         p = new SelectPlan(p, remainingPred);
      }

      //Step 4: Add GROUP BY plan if specified
      List<String> groupby = data.groupby();
      if (groupby != null && !groupby.isEmpty()) {
         List<AggregationFn> aggfns = data.aggfns();
         p = new GroupByPlan(tx, p, groupby, aggfns);
      }

      //Step 5: Add ORDER BY sort plan (before projection, so sort field may not be selected)
      List<String> orderby = data.orderby();
      if (orderby != null && !orderby.isEmpty()) {
         p = new SortPlan(tx, p, orderby);
      }

      //Step 6: Project on the field names (skip for SELECT *)
      List<String> fields = data.fields();
      if (!(fields.size() == 1 && fields.get(0).equals("*"))) {
         p = new ProjectPlan(p, fields);
      }

      return p;
   }

   /**
    * 移除已经下推到各个表的谓词，返回剩余的谓词（如跨表连接条件）。
    */
   private Predicate removeAppliedPredicates(Predicate pred, List<String> tableNames, Transaction tx) {
      if (pred.isAlwaysTrue()) {
         return pred;
      }

      // 收集每个表的 schema
      List<Schema> schemas = new ArrayList<>();
      for (String tblname : tableNames) {
         Plan tp = new TablePlan(tx, tblname, mdm);
         schemas.add(tp.schema());
      }

      // 对于单表查询，所有谓词都已下推
      if (schemas.size() == 1) {
         return Predicate.truePred();
      }

      // 对于多表查询，保留不能单独适用于任一表的谓词（连接条件）
      Predicate remaining = pred;
      for (Schema sch : schemas) {
         Predicate subPred = remaining.selectSubPred(sch);
         if (subPred != null) {
            // 从 remaining 中移除已下推的部分
            remaining = removeSubPredicate(remaining, subPred);
         }
      }

      return remaining;
   }

   /**
    * 从谓词中移除指定的子谓词。
    * 简化实现：如果剩余谓词与要移除的相等，则返回 TRUE。
    */
   private Predicate removeSubPredicate(Predicate pred, Predicate toRemove) {
       // 简化处理：如果谓词结构相同，返回 TRUE
       if (pred.toString().equals(toRemove.toString())) {
           return Predicate.truePred();
       }
       return pred;
   }
}
