package simpledb.logical;

import java.util.*;
import simpledb.query.*;

/**
 * Logical UPDATE command.
 *
 * Assigns new values to the listed columns of every row matching the
 * predicate. When the predicate is null the update applies to every row.
 */
public class UpdatePlan extends LogicalPlan {
   private final String tableName;
   private final List<String> columns;
   private final List<Constant> values;
   private final Predicate predicate;

   public UpdatePlan(String tableName, List<String> columns, List<Constant> values,
                     Predicate predicate) {
      // 列数/值数一致性不在构造期校验：逻辑计划是纯数据结构，
      // 由 ExecutorImpl.executeUpdate 在执行期抛出 EngineException[ARITY_MISMATCH]，
      // 这样错误类型与 INSERT 等语句保持一致（也避免 logical 层反向依赖 engine 层）。
      this.tableName = tableName;
      this.columns = new ArrayList<>(columns);
      this.values = new ArrayList<>(values);
      this.predicate = predicate;
   }

   public String tableName() {
      return tableName;
   }

   public List<String> columns() {
      return Collections.unmodifiableList(columns);
   }

   public List<Constant> values() {
      return Collections.unmodifiableList(values);
   }

   /** Null means "every row". */
   public Predicate predicate() {
      return predicate;
   }

   public String explain(int indent) {
      return pad(indent) + "Update[" + tableName
         + " set " + columns + " = " + values
         + (predicate == null ? "" : " where " + predicate) + "]";
   }
}
