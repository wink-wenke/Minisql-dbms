package simpledb.query;

import simpledb.record.*;

/**
 * SQL 表达式，支持字段引用、常量和算术运算。
 * <p>
 * 表达式类型：
 * <ul>
 *   <li>常量表达式：如 42, 'Alice'</li>
 *   <li>字段引用表达式：如 id, name</li>
 *   <li>算术表达式：如 id + 1, age * 2, price - discount</li>
 * </ul>
 *
 * @author Edward Sciore (原始), 增强版
 */
public class Expression {
    // 算术运算符枚举
    public enum ArithOp {
        NONE,   // 无运算（普通表达式）
        PLUS,   // +
        MINUS,  // -
        MULTIPLY, // *
        DIVIDE  // /
    }

    private Constant val = null;
    private String fldname = null;

    // 算术表达式支持
    private Expression left = null;
    private Expression right = null;
    private ArithOp arithOp = ArithOp.NONE;

    /**
     * 创建常量表达式。
     */
    public Expression(Constant val) {
        this.val = val;
    }

    /**
     * 创建字段引用表达式。
     */
    public Expression(String fldname) {
        this.fldname = fldname;
    }

    /**
     * 创建算术表达式：left op right。
     */
    public Expression(Expression left, Expression right, ArithOp op) {
        this.left = left;
        this.right = right;
        this.arithOp = op;
    }

    /**
     * 求值表达式。
     * 对于当前扫描记录，计算表达式的值。
     *
     * @param s 扫描器
     * @return 表达式的值
     */
    public Constant evaluate(Scan s) {
        // 算术表达式
        if (arithOp != ArithOp.NONE) {
            Constant leftVal = left.evaluate(s);
            Constant rightVal = right.evaluate(s);
            return evaluateArithmetic(leftVal, rightVal, arithOp);
        }

        // 常量或字段引用
        return (val != null) ? val : s.getVal(fldname);
    }

    /**
     * 执行算术运算。
     */
    private Constant evaluateArithmetic(Constant left, Constant right, ArithOp op) {
        // 尝试整数运算
        try {
            int l = left.asInt();
            int r = right.asInt();
            switch (op) {
                case PLUS:     return new Constant(l + r);
                case MINUS:    return new Constant(l - r);
                case MULTIPLY: return new Constant(l * r);
                case DIVIDE:
                    if (r == 0) throw new ArithmeticException("Division by zero");
                    return new Constant(l / r);
            }
        } catch (ArithmeticException e) {
            // 除零错误，直接抛出
            throw e;
        } catch (Exception e) {
            // 整数运算失败，尝试字符串拼接（仅支持 +）
            if (op == ArithOp.PLUS) {
                try {
                    String lStr = left.asString();
                    String rStr = right.asString();
                    return new Constant(lStr + rStr);
                } catch (Exception e2) {
                    // 字符串拼接也失败
                }
            }
            throw new UnsupportedOperationException(
                    "Cannot perform arithmetic on non-numeric values: " + left + " " + op + " " + right);
        }
        return left;
    }

    /**
     * 判断是否为字段引用表达式。
     */
    public boolean isFieldName() {
        return fldname != null && arithOp == ArithOp.NONE;
    }

    /**
     * 判断是否为算术表达式。
     */
    public boolean isArithmetic() {
        return arithOp != ArithOp.NONE;
    }

    /**
     * 获取常量值（仅对常量表达式有效）。
     */
    public Constant asConstant() {
        return val;
    }

    /**
     * 获取字段名（仅对字段引用表达式有效）。
     */
    public String asFieldName() {
        return fldname;
    }

    /**
     * 获取左操作数（仅对算术表达式有效）。
     */
    public Expression left() {
        return left;
    }

    /**
     * 获取右操作数（仅对算术表达式有效）。
     */
    public Expression right() {
        return right;
    }

    /**
     * 获取算术运算符（仅对算术表达式有效）。
     */
    public ArithOp arithOp() {
        return arithOp;
    }

    /**
     * 判断表达式中所有字段是否都在给定 schema 中。
     */
    public boolean appliesTo(Schema sch) {
        if (arithOp != ArithOp.NONE) {
            return left.appliesTo(sch) && right.appliesTo(sch);
        }
        return (val != null) ? true : sch.hasField(fldname);
    }

    /**
     * 收集表达式中引用的所有字段名。
     */
    public void collectFields(java.util.Set<String> fields) {
        if (arithOp != ArithOp.NONE) {
            left.collectFields(fields);
            right.collectFields(fields);
        } else if (fldname != null) {
            fields.add(fldname);
        }
    }

    /**
     * 尝试常量折叠：如果表达式的所有操作数都是常量，则计算结果。
     * 返回折叠后的表达式（可能是新的常量表达式）。
     */
    public Expression foldConstants() {
        if (arithOp == ArithOp.NONE) {
            return this;
        }

        Expression foldedLeft = left.foldConstants();
        Expression foldedRight = right.foldConstants();

        // 如果两侧都是常量，执行运算
        if (foldedLeft.val != null && foldedRight.val != null) {
            try {
                Constant result = evaluateArithmetic(foldedLeft.val, foldedRight.val, arithOp);
                return new Expression(result);
            } catch (Exception e) {
                // 运算失败，返回原表达式
            }
        }

        return new Expression(foldedLeft, foldedRight, arithOp);
    }

    public String toString() {
        if (arithOp != ArithOp.NONE) {
            String opStr;
            switch (arithOp) {
                case PLUS:     opStr = " + "; break;
                case MINUS:    opStr = " - "; break;
                case MULTIPLY: opStr = " * "; break;
                case DIVIDE:   opStr = " / "; break;
                default:       opStr = " ? "; break;
            }
            return "(" + left.toString() + opStr + right.toString() + ")";
        }
        return (val != null) ? val.toString() : fldname;
    }
}
