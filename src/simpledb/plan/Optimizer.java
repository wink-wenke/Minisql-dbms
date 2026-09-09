package simpledb.plan;

import java.util.*;

import simpledb.query.Predicate;

/**
 * 查询优化器。
 * <p>
 * 对已生成的 Logical Plan 应用优化规则：
 * <ol>
 *   <li><b>常量折叠</b>：简化谓词中的常量表达式（c=c → TRUE, TRUE AND x → x）</li>
 *   <li><b>冗余节点消除</b>：删除 Filter[TRUE]、Project[*] 等无意义节点</li>
 * </ol>
 * <p>
 * 同时提供 Plan 树形可视化功能。
 */
public class Optimizer {

    /**
     * 对 Plan 应用所有优化规则，返回优化后的 Plan。
     */
    public static Plan optimize(Plan plan) {
        // 递归优化
        Plan optimized = applyRules(plan);
        return optimized;
    }

    /**
     * 递归应用优化规则。
     */
    private static Plan applyRules(Plan plan) {
        if (plan instanceof SelectPlan) {
            return optimizeSelect((SelectPlan) plan);
        }
        if (plan instanceof ProjectPlan) {
            return optimizeProject((ProjectPlan) plan);
        }
        // 其他 Plan 类型暂不做结构优化
        return plan;
    }

    /**
     * 优化 SelectPlan：
     * 1. 简化谓词（常量折叠 + 布尔化简）
     * 2. 如果谓词恒真，移除 SelectPlan 节点
     */
    private static Plan optimizeSelect(SelectPlan sp) {
        Predicate pred = sp.predicate();
        Plan child = sp.child();

        // 规则 1：简化谓词
        Predicate simplified = pred.simplify();

        // 规则 2：如果谓词恒真，移除 SelectPlan
        if (simplified.isAlwaysTrue()) {
            return applyRules(child);
        }

        // 规则 3：如果谓词恒假，保留（执行时不会返回任何行）
        if (simplified.isAlwaysFalse()) {
            return sp;
        }

        // 如果谓词被简化了（结构变化），创建新的 SelectPlan
        if (!simplified.toString().equals(pred.toString())) {
            return new SelectPlan(applyRules(child), simplified);
        }

        return sp;
    }

    /**
     * 优化 ProjectPlan：
     * 如果是 SELECT *，移除 ProjectPlan 节点
     */
    private static Plan optimizeProject(ProjectPlan pp) {
        List<String> fields = pp.fields();
        Plan optimizedChild = applyRules(pp.child());

        // 规则：SELECT * 不需要 ProjectPlan
        if (fields.size() == 1 && fields.get(0).equals("*")) {
            return optimizedChild;
        }

        return new ProjectPlan(optimizedChild, fields);
    }

    // =================================================================
    //  Plan 可视化
    // =================================================================

    /**
     * 将 Plan 树格式化为可读字符串。
     * <pre>
     *   Project[id, name]
     *     └─ Filter[age > 18 AND id != 3]
     *         └─ SeqScan[student]
     * </pre>
     */
    public static String visualize(Plan plan) {
        StringBuilder sb = new StringBuilder();
        visualizeHelper(plan, sb, "", true);
        return sb.toString();
    }

    private static void visualizeHelper(Plan plan, StringBuilder sb, String prefix, boolean isLast) {
        String connector = isLast ? "└─ " : "├─ ";
        String nodeStr = planToString(plan);
        sb.append(prefix).append(connector).append(nodeStr).append("\n");

        String childPrefix = prefix + (isLast ? "   " : "│  ");
        List<Plan> children = getChildren(plan);
        for (int i = 0; i < children.size(); i++) {
            visualizeHelper(children.get(i), sb, childPrefix, i == children.size() - 1);
        }
    }

    /**
     * 将 Plan 节点转为简短描述。
     */
    private static String planToString(Plan plan) {
        if (plan instanceof SelectPlan) {
            SelectPlan sp = (SelectPlan) plan;
            return "Filter[" + sp.predicate().toString() + "]";
        }
        if (plan instanceof ProjectPlan) {
            ProjectPlan pp = (ProjectPlan) plan;
            return "Project" + pp.fields().toString();
        }
        if (plan instanceof TablePlan) {
            TablePlan tp = (TablePlan) plan;
            return "SeqScan[" + tp.tableName() + "]";
        }
        if (plan instanceof ProductPlan) {
            return "Product";
        }
        return plan.getClass().getSimpleName();
    }

    /**
     * 获取 Plan 的子节点。
     */
    private static List<Plan> getChildren(Plan plan) {
        List<Plan> children = new ArrayList<>();
        if (plan instanceof SelectPlan) {
            children.add(((SelectPlan) plan).child());
        } else if (plan instanceof ProjectPlan) {
            children.add(((ProjectPlan) plan).child());
        } else if (plan instanceof ProductPlan) {
            ProductPlan pp = (ProductPlan) plan;
            children.add(pp.left());
            children.add(pp.right());
        }
        return children;
    }
}
