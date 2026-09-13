package simpledb.parse;

import java.util.*;

import simpledb.metadata.MetadataMgr;
import simpledb.query.*;
import simpledb.record.*;
import simpledb.tx.Transaction;

import static java.sql.Types.*;

/**
 * 语义分析器。
 * <p>
 * 职责：将"语法正确"提升为"语义可执行"。
 * <ul>
 *   <li>表存在性检查</li>
 *   <li>列存在性检查</li>
 *   <li>名字绑定（标识符 → Catalog）</li>
 *   <li>类型检查（INT vs VARCHAR 比较等）</li>
 *   <li>INSERT 列数 / 列顺序 / 值类型 检查</li>
 * </ul>
 *
 * 用法：
 * <pre>
 *   SemanticAnalyzer analyzer = new SemanticAnalyzer(mdm, tx);
 *   analyzer.analyzeQuery(queryData);
 *   analyzer.analyzeInsert(insertData);
 *   analyzer.analyzeDelete(deleteData);
 *   analyzer.analyzeUpdate(modifyData);
 * </pre>
 */
public class SemanticAnalyzer {
    private MetadataMgr mdm;
    private Transaction tx;

    public SemanticAnalyzer(MetadataMgr mdm, Transaction tx) {
        this.mdm = mdm;
        this.tx = tx;
    }

    // =================================================================
    //  公开接口：按语句类型分析
    // =================================================================

    /**
     * 分析 SELECT 语句。
     */
    public void analyzeQuery(QueryData data) {
        // 1. 检查所有表是否存在，并收集 schema
        Schema combinedSchema = checkTablesAndGetSchema(data.tables());

        // 2. 检查 SELECT 字段（排除 * 和聚合函数字段）
        for (String fld : data.fields()) {
            if (!fld.equals("*") && !isAggField(fld) && !combinedSchema.hasField(fld)) {
                throw new SemanticError(
                        "列 '" + fld + "' 不存在于表 " + data.tables());
            }
        }

        // 3. 检查 WHERE 子句中的列和类型
        checkPredicate(data.pred(), combinedSchema);
    }

    /**
     * 分析 INSERT 语句。
     */
    public void analyzeInsert(InsertData data) {
        // 1. 检查表是否存在
        Layout layout = checkTableExists(data.tableName());
        Schema schema = layout.schema();

        // 2. 检查列数是否匹配
        List<String> fields = data.fields();
        List<Constant> vals = data.vals();
        if (fields.size() != vals.size()) {
            throw new SemanticError(
                    "INSERT 列数与值数不匹配：列数=" + fields.size() + ", 值数=" + vals.size());
        }

        // 3. 检查每列是否存在，以及类型是否匹配
        for (int i = 0; i < fields.size(); i++) {
            String fldname = fields.get(i);
            if (!schema.hasField(fldname)) {
                throw new SemanticError(
                        "列 '" + fldname + "' 不存在于表 '" + data.tableName() + "'");
            }
            int expectedType = schema.type(fldname);
            Constant val = vals.get(i);
            checkConstantType(val, expectedType, data.tableName(), fldname);
        }
    }

    /**
     * 分析 DELETE 语句。
     */
    public void analyzeDelete(DeleteData data) {
        // 1. 检查表是否存在
        Layout layout = checkTableExists(data.tableName());
        Schema schema = layout.schema();

        // 2. 检查 WHERE 子句
        checkPredicate(data.pred(), schema);
    }

    /**
     * 分析 UPDATE 语句。
     */
    public void analyzeUpdate(ModifyData data) {
        // 1. 检查表是否存在
        Layout layout = checkTableExists(data.tableName());
        Schema schema = layout.schema();

        // 2. 检查目标列是否存在
        String targetField = data.targetField();
        if (!schema.hasField(targetField)) {
            throw new SemanticError(
                    "列 '" + targetField + "' 不存在于表 '" + data.tableName() + "'");
        }

        // 3. 检查赋值表达式的类型
        Expression newVal = data.newValue();
        int expectedType = schema.type(targetField);
        if (newVal.isFieldName()) {
            // SET age = other_column — 检查列是否存在且类型匹配
            String srcField = newVal.asFieldName();
            if (!schema.hasField(srcField)) {
                throw new SemanticError(
                        "列 '" + srcField + "' 不存在于表 '" + data.tableName() + "'");
            }
            int srcType = schema.type(srcField);
            if (srcType != expectedType) {
                throw new SemanticError(
                        "类型不匹配：'" + targetField + "' 期望 " + typeName(expectedType)
                                + "，但 '" + srcField + "' 是 " + typeName(srcType));
            }
        } else {
            // SET age = 20 — 检查常量类型
            checkConstantType(newVal.asConstant(), expectedType, data.tableName(), targetField);
        }

        // 4. 检查 WHERE 子句
        Predicate pred = data.pred();
        if (pred != null) {
            checkPredicate(pred, schema);
        }
    }

    /**
     * 通用分析入口：根据语句类型自动分派。
     */
    public void analyze(Object stmt) {
        if (stmt instanceof QueryData) {
            analyzeQuery((QueryData) stmt);
        } else if (stmt instanceof InsertData) {
            analyzeInsert((InsertData) stmt);
        } else if (stmt instanceof DeleteData) {
            analyzeDelete((DeleteData) stmt);
        } else if (stmt instanceof ModifyData) {
            analyzeUpdate((ModifyData) stmt);
        }
        // CREATE TABLE / VIEW / INDEX 不需要语义检查（新建对象）
    }

    // =================================================================
    //  内部辅助方法
    // =================================================================

    /**
     * 检查表是否存在，返回 Layout。
     */
    private Layout checkTableExists(String tblname) {
        Layout layout = mdm.getLayout(tblname, tx);
        if (layout == null) {
            throw new SemanticError("表 '" + tblname + "' 不存在");
        }
        return layout;
    }

    /**
     * 检查多个表是否存在，返回合并后的 Schema。
     */
    private Schema checkTablesAndGetSchema(Collection<String> tables) {
        Schema combined = new Schema();
        for (String tblname : tables) {
            Layout layout = checkTableExists(tblname);
            combined.addAll(layout.schema());
        }
        return combined;
    }

    /**
     * 检查谓词中的列是否存在，以及类型是否兼容。
     */
    private void checkPredicate(Predicate pred, Schema schema) {
        List<Term> terms = pred.collectTerms();
        for (Term term : terms) {
            checkTerm(term, schema);
        }
    }

    /**
     * 检查单个 Term 的列和类型。
     */
    private void checkTerm(Term term, Schema schema) {
        Expression lhs = term.lhs();
        Expression rhs = term.rhs();

        int lhsType = getExpressionType(lhs, schema);
        int rhsType = getExpressionType(rhs, schema);

        // 类型检查：两侧类型必须兼容
        if (lhsType != rhsType) {
            // 允许 INT 和 VARCHAR 之间的比较（宽松策略）
            // 但严格来说应该报错
            String lhsStr = lhs.isFieldName() ? "'" + lhs.asFieldName() + "'" : lhs.asConstant().toString();
            String rhsStr = rhs.isFieldName() ? "'" + rhs.asFieldName() + "'" : rhs.asConstant().toString();
            throw new SemanticError(
                    "类型不匹配：" + lhsStr + " 是 " + typeName(lhsType)
                            + "，" + rhsStr + " 是 " + typeName(rhsType));
        }
    }

    /**
     * 获取表达式的类型。
     * 如果是字段引用，从 schema 中查找。
     * 如果是常量，根据值推断。
     * 如果是算术表达式，检查操作数类型并返回结果类型。
     */
    private int getExpressionType(Expression expr, Schema schema) {
        // 算术表达式
        if (expr.isArithmetic()) {
            int leftType = getExpressionType(expr.left(), schema);
            int rightType = getExpressionType(expr.right(), schema);

            // 算术运算要求两侧都是 INT
            if (leftType != INTEGER || rightType != INTEGER) {
                throw new SemanticError(
                        "算术运算要求操作数为 INT 类型，但左侧是 " + typeName(leftType)
                                + "，右侧是 " + typeName(rightType));
            }
            return INTEGER;
        }

        // 字段引用
        if (expr.isFieldName()) {
            String fldname = expr.asFieldName();
            if (!schema.hasField(fldname)) {
                throw new SemanticError("列 '" + fldname + "' 不存在");
            }
            return schema.type(fldname);
        }

        // 常量：判断是 INT 还是 VARCHAR
        Constant c = expr.asConstant();
        try {
            c.asInt();
            return INTEGER;
        } catch (Exception e) {
            return VARCHAR;
        }
    }

    /**
     * 检查常量类型是否与期望类型匹配。
     */
    private void checkConstantType(Constant val, int expectedType, String tblname, String fldname) {
        int actualType;
        try {
            val.asInt();
            actualType = INTEGER;
        } catch (Exception e) {
            actualType = VARCHAR;
        }

        if (actualType != expectedType) {
            throw new SemanticError(
                    "'" + tblname + "." + fldname + "' 期望 " + typeName(expectedType)
                            + "，但值 '" + val + "' 是 " + typeName(actualType));
        }
    }

    /**
     * 判断是否为聚合函数字段（countofxxx, maxofxxx, minofxxx, sumofxxx）。
     */
    private boolean isAggField(String fld) {
        return fld.startsWith("countof") || fld.startsWith("maxof")
                || fld.startsWith("minof") || fld.startsWith("sumof");
    }

    /**
     * 返回类型的可读名称。
     */
    private String typeName(int type) {
        switch (type) {
            case INTEGER: return "INT";
            case VARCHAR: return "VARCHAR";
            default:      return "UNKNOWN(" + type + ")";
        }
    }
}
