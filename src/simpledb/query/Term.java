package simpledb.query;

import simpledb.plan.Plan;
import simpledb.record.*;

/**
 * 一个比较项，形如 expression op expression。
 * <p>
 * 支持的比较运算符：=, !=, <, <=, >, >=
 *
 * @author Edward Sciore (原始), 增强版
 */
public class Term {
    private Expression lhs, rhs;
    private CompOp op;

    /**
     * 创建一个比较项（默认为等值比较）。
     */
    public Term(Expression lhs, Expression rhs) {
        this(lhs, rhs, CompOp.EQUALS);
    }

    /**
     * 创建一个带运算符的比较项。
     */
    public Term(Expression lhs, Expression rhs, CompOp op) {
        this.lhs = lhs;
        this.rhs = rhs;
        this.op = op;
    }

    /**
     * 返回比较运算符。
     */
    public CompOp op() {
        return op;
    }

    /**
     * 返回左侧表达式。
     */
    public Expression lhs() {
        return lhs;
    }

    /**
     * 返回右侧表达式。
     */
    public Expression rhs() {
        return rhs;
    }

    /**
     * 对当前记录求值：根据比较运算符比较 lhs 和 rhs 的值。
     */
    public boolean isSatisfied(Scan s) {
        Constant lhsval = lhs.evaluate(s);
        Constant rhsval = rhs.evaluate(s);
        int cmp = lhsval.compareTo(rhsval);
        switch (op) {
            case EQUALS:         return cmp == 0;
            case NOT_EQUALS:     return cmp != 0;
            case LESS:           return cmp < 0;
            case LESS_EQUALS:    return cmp <= 0;
            case GREATER:        return cmp > 0;
            case GREATER_EQUALS: return cmp >= 0;
            default:             return false;
        }
    }

    /**
     * 估算选择性因子（与原版兼容）。
     */
    public int reductionFactor(Plan p) {
        String lhsName, rhsName;
        if (lhs.isFieldName() && rhs.isFieldName()) {
            lhsName = lhs.asFieldName();
            rhsName = rhs.asFieldName();
            return Math.max(p.distinctValues(lhsName),
                            p.distinctValues(rhsName));
        }
        if (lhs.isFieldName()) {
            lhsName = lhs.asFieldName();
            return p.distinctValues(lhsName);
        }
        if (rhs.isFieldName()) {
            rhsName = rhs.asFieldName();
            return p.distinctValues(rhsName);
        }
        if (lhs.asConstant().equals(rhs.asConstant()))
            return 1;
        else
            return Integer.MAX_VALUE;
    }

    /**
     * 判断是否为 "F = c" 形式（仅等值比较）。
     */
    public Constant equatesWithConstant(String fldname) {
        if (op != CompOp.EQUALS) return null;
        if (lhs.isFieldName() && lhs.asFieldName().equals(fldname) && !rhs.isFieldName())
            return rhs.asConstant();
        else if (rhs.isFieldName() && rhs.asFieldName().equals(fldname) && !lhs.isFieldName())
            return lhs.asConstant();
        else
            return null;
    }

    /**
     * 判断是否为 "F1 = F2" 形式（仅等值比较）。
     */
    public String equatesWithField(String fldname) {
        if (op != CompOp.EQUALS) return null;
        if (lhs.isFieldName() && lhs.asFieldName().equals(fldname) && rhs.isFieldName())
            return rhs.asFieldName();
        else if (rhs.isFieldName() && rhs.asFieldName().equals(fldname) && lhs.isFieldName())
            return lhs.asFieldName();
        else
            return null;
    }

    /**
     * 判断此 term 的两个表达式是否都适用于给定 schema。
     */
    public boolean appliesTo(Schema sch) {
        return lhs.appliesTo(sch) && rhs.appliesTo(sch);
    }

    /**
     * 简化 Term：对表达式进行常量折叠。
     */
    public Term simplify() {
        Expression simplifiedLhs = lhs.foldConstants();
        Expression simplifiedRhs = rhs.foldConstants();
        return new Term(simplifiedLhs, simplifiedRhs, op);
    }

    public String toString() {
        return lhs.toString() + " " + op.symbol() + " " + rhs.toString();
    }
}
