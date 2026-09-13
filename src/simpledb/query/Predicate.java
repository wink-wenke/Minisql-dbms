package simpledb.query;

import java.util.*;

import simpledb.plan.Plan;
import simpledb.record.*;

/**
 * 谓词（WHERE 条件），支持 AND / OR / NOT 树形结构。
 * <p>
 * 结构：
 * <pre>
 *   Predicate
 *   ├── TERM:  包含单个 Term（叶子节点）
 *   ├── AND:   left AND right
 *   ├── OR:    left OR right
 *   └── NOT:   NOT child
 * </pre>
 * 优先级：NOT > 比较 > AND > OR（由 Parser 保证）
 *
 * @author Edward Sciore (原始), 增强版
 */
public class Predicate {
    // ==================== 节点类型 ====================
    private enum PredType { TERM, AND, OR, NOT }

    private PredType type;
    private Term term;              // TERM 类型使用
    private Predicate left, right;  // AND / OR 使用 left 和 right；NOT 仅使用 left

    /**
     * 创建空谓词，等价于 TRUE。
     */
    public Predicate() {
        // 空谓词视为始终为 true（用于没有 WHERE 的情况）
        // 用一个永真的 Term 实现：1 = 1
        this.type = PredType.TERM;
        this.term = new Term(
                new Expression(new Constant(1)),
                new Expression(new Constant(1)),
                CompOp.EQUALS
        );
    }

    /**
     * 创建包含单个 Term 的谓词（叶子节点）。
     */
    public Predicate(Term t) {
        this.type = PredType.TERM;
        this.term = t;
    }

    /**
     * 创建指定类型的谓词节点。
     */
    private Predicate(PredType type, Predicate left, Predicate right) {
        this.type = type;
        this.left = left;
        this.right = right;
    }

    // ==================== 工厂方法 ====================

    /**
     * 创建 AND 谓词：this AND other。
     * 保持旧 API 兼容。
     */
    public void conjoinWith(Predicate other) {
        // 原地修改：将当前节点变为 AND 节点
        Predicate oldThis = this.copy();
        this.type = PredType.AND;
        this.left = oldThis;
        this.right = other;
        this.term = null;
    }

    /**
     * 创建 AND 谓词（静态工厂）。
     */
    public static Predicate and(Predicate left, Predicate right) {
        return new Predicate(PredType.AND, left, right);
    }

    /**
     * 创建 OR 谓词（静态工厂）。
     */
    public static Predicate or(Predicate left, Predicate right) {
        return new Predicate(PredType.OR, left, right);
    }

    /**
     * 创建 NOT 谓词（静态工厂）。
     */
    public static Predicate not(Predicate child) {
        return new Predicate(PredType.NOT, child, null);
    }

    /**
     * 深拷贝此谓词。
     */
    private Predicate copy() {
        Predicate p = new Predicate();
        p.type = this.type;
        p.term = this.term;
        p.left = (this.left != null) ? this.left.copy() : null;
        p.right = (this.right != null) ? this.right.copy() : null;
        return p;
    }

    // ==================== 求值 ====================

    /**
     * 判断此谓词对当前扫描记录是否为 true。
     */
    public boolean isSatisfied(Scan s) {
        switch (type) {
            case TERM:
                return term.isSatisfied(s);
            case AND:
                return left.isSatisfied(s) && right.isSatisfied(s);
            case OR:
                return left.isSatisfied(s) || right.isSatisfied(s);
            case NOT:
                return !left.isSatisfied(s);
            default:
                return true;
        }
    }

    // ==================== 查询优化辅助 ====================

    /**
     * 估算选择性因子。
     */
    public int reductionFactor(Plan p) {
        switch (type) {
            case TERM:
                return term.reductionFactor(p);
            case AND:
                return left.reductionFactor(p) * right.reductionFactor(p);
            case OR:
                // OR 的选择性因子近似为两者中较小的
                return Math.min(left.reductionFactor(p), right.reductionFactor(p));
            case NOT:
                return left.reductionFactor(p);
            default:
                return 1;
        }
    }

    /**
     * 查找 "F = c" 形式的等值条件（仅在 TERM 节点中查找）。
     */
    public Constant equatesWithConstant(String fldname) {
        switch (type) {
            case TERM:
                return term.equatesWithConstant(fldname);
            case AND: {
                Constant c = left.equatesWithConstant(fldname);
                return (c != null) ? c : right.equatesWithConstant(fldname);
            }
            default:
                return null;
        }
    }

    /**
     * 查找 "F1 = F2" 形式的等值条件（仅在 TERM 节点中查找）。
     */
    public String equatesWithField(String fldname) {
        switch (type) {
            case TERM:
                return term.equatesWithField(fldname);
            case AND: {
                String s = left.equatesWithField(fldname);
                return (s != null) ? s : right.equatesWithField(fldname);
            }
            default:
                return null;
        }
    }

    /**
     * 提取适用于给定 schema 的子谓词（用于选择下推）。
     * 返回 null 表示没有适用的子谓词。
     */
    public Predicate selectSubPred(Schema sch) {
        switch (type) {
            case TERM:
                return term.appliesTo(sch) ? this : null;
            case AND: {
                Predicate l = left.selectSubPred(sch);
                Predicate r = right.selectSubPred(sch);
                if (l != null && r != null) return and(l, r);
                return (l != null) ? l : r;
            }
            case OR: {
                Predicate l = left.selectSubPred(sch);
                Predicate r = right.selectSubPred(sch);
                if (l != null && r != null) return or(l, r);
                return null; // OR 的两侧必须都适用
            }
            case NOT: {
                Predicate c = left.selectSubPred(sch);
                return (c != null) ? not(c) : null;
            }
            default:
                return null;
        }
    }

    /**
     * 提取适用于两个 schema 联合但不单独适用于任一 schema 的子谓词（用于连接条件）。
     */
    public Predicate joinSubPred(Schema sch1, Schema sch2) {
        Schema newsch = new Schema();
        newsch.addAll(sch1);
        newsch.addAll(sch2);

        switch (type) {
            case TERM:
                if (!term.appliesTo(sch1) && !term.appliesTo(sch2) && term.appliesTo(newsch))
                    return this;
                return null;
            case AND: {
                Predicate l = left.joinSubPred(sch1, sch2);
                Predicate r = right.joinSubPred(sch1, sch2);
                if (l != null && r != null) return and(l, r);
                return (l != null) ? l : r;
            }
            default:
                return null;
        }
    }

    // ==================== 收集所有 Term（用于调试） ====================

    /**
     * 收集此谓词树中的所有叶子 Term。
     */
    public List<Term> collectTerms() {
        List<Term> terms = new ArrayList<>();
        collectTermsHelper(terms);
        return terms;
    }

    private void collectTermsHelper(List<Term> terms) {
        switch (type) {
            case TERM:
                terms.add(term);
                break;
            case AND:
            case OR:
                left.collectTermsHelper(terms);
                right.collectTermsHelper(terms);
                break;
            case NOT:
                left.collectTermsHelper(terms);
                break;
        }
    }

    // ==================== 优化辅助 ====================

    /**
     * 判断此谓词是否为恒真（TRUE）。
     * TERM 节点中 lhs == rhs 且为常量 → TRUE
     * 空谓词（默认构造）→ TRUE
     */
    public boolean isAlwaysTrue() {
        switch (type) {
            case TERM:
                // 1 = 1 形式 → TRUE
                // 排除算术表达式（asConstant() 返回 null）
                if (!term.lhs().isFieldName() && !term.lhs().isArithmetic()
                        && !term.rhs().isFieldName() && !term.rhs().isArithmetic()
                        && term.op() == CompOp.EQUALS) {
                    Constant lc = term.lhs().asConstant();
                    Constant rc = term.rhs().asConstant();
                    if (lc == null || rc == null) return false;
                    return lc.equals(rc);
                }
                return false;
            case AND:
                return left.isAlwaysTrue() && right.isAlwaysTrue();
            case OR:
                return left.isAlwaysTrue() || right.isAlwaysTrue();
            case NOT:
                return left.isAlwaysFalse();
            default:
                return false;
        }
    }

    /**
     * 判断此谓词是否为恒假（FALSE）。
     */
    public boolean isAlwaysFalse() {
        switch (type) {
            case TERM:
                // 两个常量之间的比较，结果确定 → 判断是否恒假
                // 排除算术表达式（asConstant() 返回 null）
                if (!term.lhs().isFieldName() && !term.lhs().isArithmetic()
                        && !term.rhs().isFieldName() && !term.rhs().isArithmetic()) {
                    Constant lc = term.lhs().asConstant();
                    Constant rc = term.rhs().asConstant();
                    if (lc == null || rc == null) return false;
                    boolean eq = lc.equals(rc);
                    int cmp = lc.compareTo(rc);
                    switch (term.op()) {
                        case EQUALS:         return !eq;
                        case NOT_EQUALS:     return eq;
                        case LESS:           return cmp >= 0;
                        case LESS_EQUALS:    return cmp > 0;
                        case GREATER:        return cmp <= 0;
                        case GREATER_EQUALS: return cmp < 0;
                    }
                }
                return false;
            case AND:
                return left.isAlwaysFalse() || right.isAlwaysFalse();
            case OR:
                return left.isAlwaysFalse() && right.isAlwaysFalse();
            case NOT:
                return left.isAlwaysTrue();
            default:
                return false;
        }
    }

    /**
     * 简化谓词（常量折叠 + 布尔化简 + 算术表达式折叠）。
     * <ul>
     *   <li>TRUE AND x → x</li>
     *   <li>FALSE AND x → FALSE</li>
     *   <li>TRUE OR x → TRUE</li>
     *   <li>FALSE OR x → x</li>
     *   <li>NOT TRUE → FALSE</li>
     *   <li>NOT FALSE → TRUE</li>
     *   <li>c=c → TRUE, c!=c → FALSE</li>
     *   <li>age + 1 > 20 → 先折叠算术表达式</li>
     * </ul>
     */
    public Predicate simplify() {
        switch (type) {
            case TERM: {
                // 先简化 Term（折叠算术表达式）
                Term simplifiedTerm = term.simplify();
                Expression lhs = simplifiedTerm.lhs();
                Expression rhs = simplifiedTerm.rhs();

                // 常量折叠：c=c → TRUE, c!=c → FALSE
                if (!lhs.isFieldName() && !lhs.isArithmetic()
                        && !rhs.isFieldName() && !rhs.isArithmetic()) {
                    Constant lc = lhs.asConstant();
                    Constant rc = rhs.asConstant();
                    boolean eq = lc.equals(rc);
                    switch (simplifiedTerm.op()) {
                        case EQUALS:         return eq ? truePred() : falsePred();
                        case NOT_EQUALS:     return eq ? falsePred() : truePred();
                        case LESS:           return lc.compareTo(rc) < 0 ? truePred() : falsePred();
                        case LESS_EQUALS:    return lc.compareTo(rc) <= 0 ? truePred() : falsePred();
                        case GREATER:        return lc.compareTo(rc) > 0 ? truePred() : falsePred();
                        case GREATER_EQUALS: return lc.compareTo(rc) >= 0 ? truePred() : falsePred();
                    }
                }

                // 如果 Term 被简化了，返回新的 Predicate
                if (!simplifiedTerm.toString().equals(term.toString())) {
                    return new Predicate(simplifiedTerm);
                }
                return this;
            }
            case AND: {
                Predicate l = left.simplify();
                Predicate r = right.simplify();
                if (l.isAlwaysTrue()) return r;
                if (r.isAlwaysTrue()) return l;
                if (l.isAlwaysFalse()) return falsePred();
                if (r.isAlwaysFalse()) return falsePred();
                return and(l, r);
            }
            case OR: {
                Predicate l = left.simplify();
                Predicate r = right.simplify();
                if (l.isAlwaysTrue()) return truePred();
                if (r.isAlwaysTrue()) return truePred();
                if (l.isAlwaysFalse()) return r;
                if (r.isAlwaysFalse()) return l;
                return or(l, r);
            }
            case NOT: {
                Predicate c = left.simplify();
                if (c.isAlwaysTrue()) return falsePred();
                if (c.isAlwaysFalse()) return truePred();
                return not(c);
            }
            default:
                return this;
        }
    }

    /**
     * 返回恒真谓词。
     */
    public static Predicate truePred() {
        return new Predicate(new Term(
                new Expression(new Constant(1)),
                new Expression(new Constant(1)),
                CompOp.EQUALS));
    }

    /**
     * 返回恒假谓词。
     */
    public static Predicate falsePred() {
        return new Predicate(new Term(
                new Expression(new Constant(1)),
                new Expression(new Constant(0)),
                CompOp.EQUALS));
    }

    /**
     * 判断是否为 SELECT * 的投影（字段列表只有 "*"）。
     */
    public static boolean isSelectAll(List<String> fields) {
        return fields.size() == 1 && fields.get(0).equals("*");
    }

    // ==================== toString ====================

    public String toString() {
        switch (type) {
            case TERM:
                return term.toString();
            case AND:
                return "(" + left.toString() + " AND " + right.toString() + ")";
            case OR:
                return "(" + left.toString() + " OR " + right.toString() + ")";
            case NOT:
                return "(NOT " + left.toString() + ")";
            default:
                return "";
        }
    }
}
