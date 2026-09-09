# MiniSQL 接口定义文档

> 版本: v1.0 | 日期: 2026-09-08
>
> 本文档定义三大模块的接口契约，作为并行开发的基准。
> 三人各自基于接口 Mock 开发，最后集成。

---

## 1. 系统总体架构与数据流

```
┌──────────────────────────────────────────────────────────┐
│                      CLI / API                           │
└─────────────────────────┬────────────────────────────────┘
                          │ SQL String
                          ↓
┌──────────────────────────────────────────────────────────┐
│                 Module 1: SQL Compiler                    │
│                                                          │
│  SQL ──→ Lexer ──→ TokenStream                          │
│              ──→ Parser ──→ AST                          │
│              ──→ SemanticAnalyzer(ast, catalog)           │
│              ──→ PlanBuilder(ast) ──→ LogicalPlan        │
│                                                          │
│  对外输出: LogicalPlan                                    │
│  对外依赖: CatalogReader（只读）                           │
└─────────────────────────┬────────────────────────────────┘
                          │ LogicalPlan
                          ↓
┌──────────────────────────────────────────────────────────┐
│                 Module 2: DB Engine                       │
│                                                          │
│  Executor.execute(plan, tx)                              │
│     ├─ LogicalPlan ──→ EnginePlan (现有 Plan 接口)        │
│     ├─ EnginePlan.open() ──→ Scan                        │
│     ├─ 遍历 Scan 获取/修改数据                            │
│     └─ 通过 StorageEngine 读写 Page                      │
│                                                          │
│  CatalogMgr: 管理表/列元数据                              │
│  Transaction: 并发控制 + 崩溃恢复                         │
│                                                          │
│  对外输入: LogicalPlan                                    │
│  对外依赖: PageManager / BufferManager                    │
└─────────────────────────┬────────────────────────────────┘
                          │ PageId + Data
                          ↓
┌──────────────────────────────────────────────────────────┐
│               Module 3: Storage System                    │
│                                                          │
│  PageManager: 页分配/释放/读写                            │
│  BufferManager: 缓存管理 (LRU/FIFO)                      │
│  FileManager: 磁盘 I/O                                   │
│  LogManager: WAL 日志                                     │
│                                                          │
│  对外输入: PageId + byte[]                                │
│  对外依赖: 无（最底层）                                    │
└──────────────────────────────────────────────────────────┘
```

---

## 2. 模块间共享数据类型

这些类型被多个模块共同使用，定义在 `simpledb.shared` 包中。

### 2.1 常量值 — `Constant`

**现有代码**: `simpledb.query.Constant`（保留，扩展）

```java
package simpledb.shared;

/**
 * 数据库中的值。可以是 INTEGER 或 VARCHAR。
 * 被编译器（AST LiteralNode）、引擎（Predicate 求值）、存储层共享。
 */
public class Constant implements Comparable<Constant> {
    private Integer ival;
    private String  sval;

    public Constant(Integer ival);
    public Constant(String sval);

    public boolean isInt();
    public boolean isString();
    public int asInt();
    public String asString();

    // Comparable: INT 按数值比较, VARCHAR 按字典序
    public int compareTo(Constant o);
    public boolean equals(Object o);
    public int hashCode();
    public String toString();
}
```

### 2.2 字段类型 — `ColumnType`

**新增**

```java
package simpledb.shared;

/**
 * 字段类型枚举。替代 java.sql.Types 的魔法数字。
 */
public enum ColumnType {
    INTEGER, VARCHAR;

    public String toString();
}
```

### 2.3 字段信息 — `ColumnDef`

**新增**（替代 `Schema.FieldInfo` 内部类）

```java
package simpledb.shared;

/**
 * 单个字段的定义：名称 + 类型 + 长度(VARCHAR专用)。
 */
public class ColumnDef {
    public final String name;
    public final ColumnType type;
    public final int length;  // VARCHAR 时有效, INTEGER 时为 0

    public ColumnDef(String name, ColumnType type, int length);
}
```

### 2.4 Schema — `Schema`

**现有代码**: `simpledb.record.Schema`（保留，作为引擎层内部类型）

> Schema 属于引擎层内部数据结构，不作为模块间接口参数直接暴露。
> 编译器通过 `CatalogReader.getColumns()` 获取列信息，返回 `List<ColumnDef>`。
> 引擎内部再转换为 Schema。

### 2.5 错误信息 — `CompileError`

**新增**

```java
package simpledb.shared;

/**
 * 编译期错误，包含错误类型、位置、原因。
 * 编译器所有阶段（Lexer/Parser/Semantic）统一抛出。
 */
public class CompileError extends Exception {
    public final ErrorType type;
    public final int line;
    public final int column;
    public final String message;

    public enum ErrorType {
        LEXICAL,      // 词法错误: 非法字符、未闭合字符串
        SYNTAX,       // 语法错误: 缺分号、括号不匹配
        SEMANTIC      // 语义错误: 表不存在、列不存在、类型不匹配
    }

    public CompileError(ErrorType type, int line, int column, String message);
    public CompileError(ErrorType type, String message);  // 无位置信息时使用

    /**
     * 格式化输出: "SemanticError at line 3, column 19: column 'score' does not exist in table 'student'"
     */
    public String toString();
}
```

### 2.6 执行结果 — `ExecuteResult`

**新增**

```java
package simpledb.shared;

/**
 * 执行结果。SELECT 返回结果集，其他操作返回影响行数。
 */
public class ExecuteResult {
    public enum ResultType { QUERY, UPDATE }

    private ResultType type;
    private List<String> columnNames;     // QUERY 时有效
    private List<List<Constant>> rows;    // QUERY 时有效
    private int affectedRows;             // UPDATE 时有效

    // 工厂方法
    public static ExecuteResult queryResult(List<String> columns, List<List<Constant>> rows);
    public static ExecuteResult updateResult(int affectedRows);

    public ResultType getType();
    public List<String> getColumnNames();
    public List<List<Constant>> getRows();
    public int getAffectedRows();
    public String formatted();  // 格式化为表格字符串
}
```

---

## 3. Module 1: SQL Compiler 接口

> 包路径: `simpledb.compiler`
>
> 职责: SQL 文本 → LogicalPlan
>
> 不依赖: Storage / Execution / Transaction

### 3.1 顶层入口 — `SQLCompiler`

```java
package simpledb.compiler;

import simpledb.shared.CompileError;
import simpledb.logical.LogicalPlan;

/**
 * SQL 编译器顶层接口。
 * 输入 SQL 字符串，输出 LogicalPlan。
 * 编译器内部完成: Lexer → Parser → AST → Semantic → Plan
 */
public interface SQLCompiler {

    /**
     * 编译一条 SQL 语句。
     *
     * @param sql     SQL 文本
     * @param catalog 只读 Catalog，用于语义检查（表/列存在性、类型检查）
     * @return LogicalPlan 编译后的逻辑计划
     * @throws CompileError 词法/语法/语义错误
     */
    LogicalPlan compile(String sql, CatalogReader catalog) throws CompileError;
}
```

### 3.2 Catalog 只读接口 — `CatalogReader`

```java
package simpledb.compiler;

import simpledb.shared.ColumnDef;
import java.util.List;

/**
 * 编译器使用的 Catalog 只读视图。
 * 编译器不写入 Catalog，只查询表/列信息用于语义分析。
 *
 * 实现者: 引擎层的 CatalogMgr
 */
public interface CatalogReader {

    /** 表是否存在 */
    boolean tableExists(String tableName);

    /** 列是否存在 */
    boolean columnExists(String tableName, String columnName);

    /** 获取指定列的类型 */
    ColumnDef getColumn(String tableName, String columnName);

    /** 获取表的所有列定义（有序） */
    List<ColumnDef> getColumns(String tableName);
}
```

### 3.3 Lexer 接口 — `Lexer`

```java
package simpledb.compiler;

import simpledb.shared.CompileError;

/**
 * 词法分析器。
 * 输入 SQL 字符串，输出 Token 流。
 */
public interface Lexer {

    /**
     * 将 SQL 文本 tokenize。
     *
     * @param sql SQL 文本
     * @return Token 列表
     * @throws CompileError 词法错误（非法字符、未闭合字符串等）
     */
    List<Token> tokenize(String sql) throws CompileError;
}
```

### 3.4 Token

```java
package simpledb.compiler;

/**
 * 词法单元。
 */
public class Token {
    public final TokenType type;
    public final String lexeme;    // 原始文本
    public final int line;
    public final int column;

    public Token(TokenType type, String lexeme, int line, int column);
}

enum TokenType {
    // 关键字
    SELECT, FROM, WHERE, AND, OR, NOT,
    INSERT, INTO, VALUES, DELETE, UPDATE, SET,
    CREATE, TABLE, INT, VARCHAR, VIEW, AS, INDEX, ON,

    // 标识符
    IDENTIFIER,

    // 常量
    INT_LITERAL,     // 123
    STRING_LITERAL,  // 'hello'

    // 运算符
    EQ,              // =
    NE,              // !=
    LT,              // <
    LE,              // <=
    GT,              // >
    GE,              // >=
    PLUS,            // +
    MINUS,           // -
    STAR,            // *
    SLASH,           // /

    // 分隔符
    LPAREN,          // (
    RPAREN,          // )
    COMMA,           // ,
    SEMICOLON,       // ;

    EOF
}
```

### 3.5 Parser 接口 — `Parser`

```java
package simpledb.compiler;

import simpledb.ast.ASTNode;
import simpledb.shared.CompileError;

/**
 * 语法分析器。
 * 输入 Token 流，输出 AST。
 */
public interface Parser {

    /**
     * 将 Token 流解析为 AST。
     *
     * @param tokens Token 列表
     * @return AST 根节点
     * @throws CompileError 语法错误
     */
    ASTNode parse(List<Token> tokens) throws CompileError;
}
```

### 3.6 语义分析器 — `SemanticAnalyzer`

```java
package simpledb.compiler;

import simpledb.ast.ASTNode;
import simpledb.shared.CompileError;

/**
 * 语义分析器。
 * 对 AST 进行语义检查和名字绑定。
 *
 * 检查内容:
 * - 表是否存在
 * - 列是否存在
 * - INSERT 列数与 VALUES 数是否匹配
 * - INSERT 值类型与列类型是否匹配
 * - WHERE 表达式中的列是否合法
 */
public interface SemanticAnalyzer {

    /**
     * 分析 AST 的语义正确性。
     * 通过则返回（可能经过名字绑定的）AST，不通过则抛出 CompileError。
     *
     * @param ast     Parser 生成的 AST
     * @param catalog Catalog 只读视图
     * @return 验证通过的 AST
     * @throws CompileError 语义错误
     */
    ASTNode analyze(ASTNode ast, CatalogReader catalog) throws CompileError;
}
```

### 3.7 Plan 构建器 — `PlanBuilder`

```java
package simpledb.compiler;

import simpledb.ast.ASTNode;
import simpledb.logical.LogicalPlan;

/**
 * 逻辑计划构建器。
 * 将语义分析后的 AST 转换为 LogicalPlan。
 *
 * 转换规则:
 *   FROM   → SeqScan
 *   WHERE  → Filter
 *   SELECT → Project
 *   CREATE → CreateTable
 *   INSERT → Insert
 *   DELETE → Delete
 */
public interface PlanBuilder {

    /**
     * 将 AST 转换为 LogicalPlan。
     *
     * @param ast 语义分析后的 AST
     * @return LogicalPlan 树
     */
    LogicalPlan build(ASTNode ast);
}
```

---

## 4. AST 节点体系

> 包路径: `simpledb.ast`
>
> 编译器内部数据结构，不被引擎直接使用。

### 4.1 节点基类 — `ASTNode`

```java
package simpledb.ast;

/**
 * AST 节点基类。所有语句和表达式节点继承此类。
 */
public abstract class ASTNode {
    public final int line;
    public final int column;

    protected ASTNode(int line, int column);

    /** 返回节点类型的可读名称，用于错误信息和调试 */
    public abstract String nodeType();
}
```

### 4.2 语句节点

```java
// ---- CREATE TABLE ----
package simpledb.ast;

public class CreateTableNode extends ASTNode {
    public final String tableName;
    public final List<ColumnDefNode> columns;

    public CreateTableNode(String tableName, List<ColumnDefNode> columns, int line, int column);
}

public class ColumnDefNode extends ASTNode {
    public final String columnName;
    public final String type;       // "INT" or "VARCHAR"
    public final int maxLength;     // VARCHAR 时有效

    public ColumnDefNode(String columnName, String type, int maxLength, int line, int column);
}

// ---- INSERT ----
public class InsertNode extends ASTNode {
    public final String tableName;
    public final List<String> columns;
    public final List<ExpressionNode> values;

    public InsertNode(String tableName, List<String> columns,
                      List<ExpressionNode> values, int line, int column);
}

// ---- SELECT ----
public class SelectNode extends ASTNode {
    public final List<ExpressionNode> selectList;  // * 时为 null
    public final String tableName;
    public final ExpressionNode whereClause;        // 无 WHERE 时为 null

    public SelectNode(List<ExpressionNode> selectList, String tableName,
                      ExpressionNode whereClause, int line, int column);
}

// ---- DELETE ----
public class DeleteNode extends ASTNode {
    public final String tableName;
    public final ExpressionNode whereClause;        // 无 WHERE 时为 null

    public DeleteNode(String tableName, ExpressionNode whereClause, int line, int column);
}
```

### 4.3 表达式节点

```java
// ---- 表达式基类 ----
public abstract class ExpressionNode extends ASTNode {
    protected ExpressionNode(int line, int column);
}

// ---- 字面量 ----
public class LiteralNode extends ExpressionNode {
    public final Constant value;

    public LiteralNode(Constant value, int line, int column);
}

// ---- 字段引用 ----
public class FieldNode extends ExpressionNode {
    public final String fieldName;

    public FieldNode(String fieldName, int line, int column);
}

// ---- 二元表达式 ----
public class BinaryExprNode extends ExpressionNode {
    public final String operator;    // "=", "!=", "<", "<=", ">", ">=", "+", "-", "*", "/"
    public final ExpressionNode left;
    public final ExpressionNode right;

    public BinaryExprNode(String operator, ExpressionNode left,
                          ExpressionNode right, int line, int column);
}

// ---- 一元表达式 (NOT) ----
public class UnaryExprNode extends ExpressionNode {
    public final String operator;    // "NOT"
    public final ExpressionNode operand;

    public UnaryExprNode(String operator, ExpressionNode operand, int line, int column);
}

// ---- 复合布尔表达式 (AND / OR) ----
public class BooleanExprNode extends ExpressionNode {
    public final String operator;    // "AND" or "OR"
    public final ExpressionNode left;
    public final ExpressionNode right;

    public BooleanExprNode(String operator, ExpressionNode left,
                           ExpressionNode right, int line, int column);
}
```

---

## 5. LogicalPlan 节点体系

> 包路径: `simpledb.logical`
>
> **模块间数据契约**: 编译器输出 LogicalPlan，引擎消费 LogicalPlan。
>
> LogicalPlan 是纯数据结构，不持有 Transaction/MetadataMgr 等执行时对象。

### 5.1 Plan 节点基类

```java
package simpledb.logical;

import simpledb.shared.ColumnDef;
import java.util.List;

/**
 * 逻辑计划节点基类。
 *
 * 设计原则:
 * - 纯数据结构，不依赖 Transaction / MetadataMgr / BufferPool
 * - 包含 explain() 方法用于 Plan 可视化
 * - 包含 schema 推断方法用于语义验证
 */
public abstract class LogicalPlan {

    /**
     * 返回该节点输出的 Schema（列名 + 类型）。
     * 用于编译器验证和计划可视化。
     */
    public abstract List<ColumnDef> outputSchema();

    /**
     * 返回计划树的可读字符串表示（缩进树形）。
     * 用于 Plan 可视化和调试。
     *
     * 示例:
     *   Project[name, age]
     *     Filter[age > 18]
     *       SeqScan[student]
     */
    public abstract String explain(int indent);
}
```

### 5.2 具体 Plan 节点

```java
// ---- SeqScan: 全表扫描 ----
package simpledb.logical;

public class SeqScanPlan extends LogicalPlan {
    public final String tableName;

    public SeqScanPlan(String tableName);

    // outputSchema 由引擎执行时从 Catalog 获取
    // 编译器阶段可返回 null 或占位
    public List<ColumnDef> outputSchema();
    public String explain(int indent);
}

// ---- Filter: 条件过滤 ----
public class FilterPlan extends LogicalPlan {
    public final LogicalPlan child;
    public final ExpressionNode predicate;  // 复用 AST 表达式节点

    public FilterPlan(LogicalPlan child, ExpressionNode predicate);
    public List<ColumnDef> outputSchema();  // 与 child 相同
    public String explain(int indent);
}

// ---- Project: 列投影 ----
public class ProjectPlan extends LogicalPlan {
    public final LogicalPlan child;
    public final List<String> columns;      // SELECT 的列名列表，null 表示 *

    public ProjectPlan(LogicalPlan child, List<String> columns);
    public List<ColumnDef> outputSchema();  // 只包含 columns 指定的列
    public String explain(int indent);
}

// ---- CreateTable: 建表 ----
public class CreateTablePlan extends LogicalPlan {
    public final String tableName;
    public final List<ColumnDefNode> columns;  // 复用 AST 的列定义

    public CreateTablePlan(String tableName, List<ColumnDefNode> columns);
    public List<ColumnDef> outputSchema();     // 空（DDL 不返回数据）
    public String explain(int indent);
}

// ---- Insert: 插入 ----
public class InsertPlan extends LogicalPlan {
    public final String tableName;
    public final List<String> columns;
    public final List<ExpressionNode> values;

    public InsertPlan(String tableName, List<String> columns, List<ExpressionNode> values);
    public List<ColumnDef> outputSchema();     // 空
    public String explain(int indent);
}

// ---- Delete: 删除 ----
public class DeletePlan extends LogicalPlan {
    public final String tableName;
    public final ExpressionNode whereClause;   // null 表示删除全部

    public DeletePlan(String tableName, ExpressionNode whereClause);
    public List<ColumnDef> outputSchema();     // 空
    public String explain(int indent);
}
```

---

## 6. Module 2: DB Engine 接口

> 包路径: `simpledb.engine`
>
> 职责: LogicalPlan → 执行结果
>
> 依赖: Storage 模块的 PageManager / BufferManager

### 6.1 执行器 — `Executor`

```java
package simpledb.engine;

import simpledb.logical.LogicalPlan;
import simpledb.shared.ExecuteResult;
import simpledb.shared.CompileError;

/**
 * 执行引擎顶层接口。
 * 接收 LogicalPlan，返回执行结果。
 */
public interface Executor {

    /**
     * 执行一条 SQL 对应的逻辑计划。
     *
     * 内部流程:
     *   LogicalPlan → EnginePlan (现有 Plan 接口) → Scan → 结果
     *
     * @param plan 逻辑计划
     * @param tx   当前事务
     * @return 执行结果
     * @throws Exception 执行期错误
     */
    ExecuteResult execute(LogicalPlan plan, Transaction tx) throws Exception;
}
```

### 6.2 Plan 转换器 — `PlanConverter`

```java
package simpledb.engine;

import simpledb.logical.LogicalPlan;
import simpledb.plan.Plan;

/**
 * 将 LogicalPlan（编译器产物）转换为 EnginePlan（现有 Plan 接口）。
 * 这是编译器和引擎之间的桥接层。
 */
public interface PlanConverter {

    /**
     * 转换逻辑计划为执行计划。
     *
     * @param logicalPlan 编译器输出的逻辑计划
     * @param tx          当前事务（用于获取 Layout / Statistics）
     * @return 可执行的 Plan
     */
    Plan convert(LogicalPlan logicalPlan, Transaction tx);
}
```

### 6.3 Catalog 管理器 — `CatalogMgr`

```java
package simpledb.engine;

import simpledb.compiler.CatalogReader;
import simpledb.shared.ColumnDef;
import simpledb.record.Schema;
import java.util.List;

/**
 * 系统目录管理器。
 * 同时实现 CatalogReader（供编译器只读查询）和写入接口（供引擎维护元数据）。
 *
 * 这是编译器和引擎共享的桥梁:
 * - 编译器通过 CatalogReader 接口查询表/列信息
 * - 引擎通过此接口的写入方法管理元数据
 */
public interface CatalogMgr extends CatalogReader {

    // ---- 写入接口（仅引擎使用） ----

    /** 创建表，注册到系统目录 */
    void createTable(String tableName, Schema schema, Transaction tx);

    /** 删除表（如果支持 DROP TABLE） */
    void dropTable(String tableName, Transaction tx);

    /** 获取表的物理 Layout（字段偏移量等） */
    Schema getLayout(String tableName, Transaction tx);

    // ---- CatalogReader 实现 ----

    // boolean tableExists(String tableName);
    // boolean columnExists(String tableName, String columnName);
    // ColumnDef getColumn(String tableName, String columnName);
    // List<ColumnDef> getColumns(String tableName);
}
```

### 6.4 存储引擎 — `StorageEngine`

```java
package simpledb.engine;

import simpledb.shared.Constant;
import simpledb.record.RID;
import java.util.Iterator;

/**
 * 存储引擎接口。
 * 提供表级别的行操作，屏蔽底层 Page/Buffer 细节。
 * 被执行算子（SeqScan/Insert/Delete）使用。
 */
public interface StorageEngine {

    /**
     * 在指定表中插入一行。
     *
     * @param tableName 表名
     * @param values    按列顺序的值
     * @param tx        当前事务
     * @return 插入的记录 ID
     */
    RID insertRow(String tableName, Constant[] values, Transaction tx);

    /**
     * 删除指定表中的指定行。
     *
     * @param tableName 表名
     * @param rid       要删除的记录 ID
     * @param tx        当前事务
     */
    void deleteRow(String tableName, RID rid, Transaction tx);

    /**
     * 获取指定表的记录数（用于统计）。
     */
    int getRecordCount(String tableName, Transaction tx);
}
```

---

## 7. Module 3: Storage System 接口

> 包路径: `simpledb.storage`
>
> 职责: 页的分配/读写/缓存/持久化
>
> 依赖: 无（最底层模块）

### 7.1 PageManager — 页管理器

```java
package simpledb.storage;

/**
 * 页管理器。提供页的分配、释放、读写能力。
 *
 * 页大小固定为 4096 字节 (4KB)。
 * 每个页有唯一的 PageId (文件名 + 页号)。
 */
public interface PageManager {

    /** 页大小（字节） */
    int PAGE_SIZE = 4096;

    /**
     * 分配一个新页，返回其 PageId。
     * 新页内容全零。
     */
    PageId allocatePage();

    /**
     * 释放一个页，将其加入空闲列表。
     */
    void freePage(PageId pageId);

    /**
     * 读取指定页的内容到 buffer。
     */
    void readPage(PageId pageId, byte[] buffer);

    /**
     * 将 buffer 内容写入指定页。
     */
    void writePage(PageId pageId, byte[] buffer);

    /**
     * 获取指定文件的总页数。
     */
    int pageCount(String fileName);
}
```

### 7.2 PageId — 页标识

```java
package simpledb.storage;

/**
 * 页的唯一标识。由文件名和页号组成。
 *
 * 保留现有 simpledb.file.BlockId 的功能，统一命名。
 */
public class PageId {
    private final String fileName;
    private final int pageNumber;

    public PageId(String fileName, int pageNumber);

    public String fileName();
    public int pageNumber();

    public boolean equals(Object o);
    public int hashCode();
    public String toString();
}
```

### 7.3 BufferManager — 缓存管理器

```java
package simpledb.storage;

/**
 * 缓存管理器。在内存中缓存磁盘页，减少 I/O。
 *
 * 支持 LRU 和 FIFO 两种替换策略。
 * 提供缓存命中/未命中统计。
 */
public interface BufferManager {

    /**
     * 获取指定页。如果页已在缓存中则直接返回（HIT），
     * 否则从磁盘读入缓存（MISS），必要时淘汰一页。
     *
     * @param pageId 要获取的页
     * @return 页内容的 byte[]
     */
    byte[] getPage(PageId pageId);

    /**
     * 将指定页写回磁盘（如果 dirty）。
     */
    void flushPage(PageId pageId);

    /**
     * 将所有脏页写回磁盘。
     */
    void flushAll();

    /**
     * 标记指定页为 unpinned（可被淘汰）。
     */
    void unpinPage(PageId pageId);

    /**
     * 获取缓存统计信息。
     */
    CacheStats getStats();

    /**
     * 设置替换策略。
     */
    void setReplacementPolicy(ReplacementPolicy policy);
}

// ---- 替换策略枚举 ----
enum ReplacementPolicy {
    LRU,    // 最近最少使用
    FIFO    // 先进先出
}

// ---- 缓存统计 ----
class CacheStats {
    public final long accessCount;   // 总访问次数
    public final long hitCount;      // 命中次数
    public final long missCount;     // 未命中次数
    public final long evictionCount; // 淘汰次数

    public double hitRate();         // 命中率 = hitCount / accessCount

    public String toString();        // 格式化输出统计信息
}
```

### 7.4 FileManager — 文件管理器

```java
package simpledb.storage;

/**
 * 文件管理器。管理数据库目录下的文件读写。
 * 提供底层磁盘 I/O 能力。
 *
 * 现有 simpledb.file.FileMgr 的角色，接口不变。
 */
public interface FileManager {

    /**
     * 读取指定块的内容到 Page。
     */
    void read(PageId pageId, byte[] buffer);

    /**
     * 将 Page 内容写入指定块。
     */
    void write(PageId pageId, byte[] buffer);

    /**
     * 追加一个新块到文件末尾，返回其 PageId。
     */
    PageId append(String fileName);

    /**
     * 获取指定文件的块数。
     */
    int length(String fileName);

    /**
     * 获取块大小。
     */
    int blockSize();
}
```

### 7.5 LogManager — 日志管理器

```java
package simpledb.storage;

/**
 * Write-Ahead Log 管理器。
 * 现有 simpledb.log.LogMgr 的角色。
 */
public interface LogManager {

    /**
     * 追加一条日志记录。
     *
     * @param record 日志记录字节数组
     * @return 日志序列号 (LSN)
     */
    long append(byte[] record);

    /**
     * 将日志缓冲区刷到磁盘。
     */
    void flush(long lsn);

    /**
     * 创建日志迭代器（从最新到最旧）。
     */
    LogIterator iterator();
}

interface LogIterator {
    boolean hasNext();
    byte[] next();
}
```

---

## 8. 现有代码保留/改造清单

### 8.1 保留不变的类

| 类 | 包 | 说明 |
|---|---|---|
| `Scan` | `simpledb.query` | 迭代器接口，引擎内部使用 |
| `UpdateScan` | `simpledb.query` | 可写扫描接口 |
| `SelectScan` | `simpledb.query` | 条件过滤 Scan |
| `ProjectScan` | `simpledb.query` | 列投影 Scan |
| `ProductScan` | `simpledb.query` | 笛卡尔积 Scan |
| `TableScan` | `simpledb.record` | 全表扫描 |
| `RecordPage` | `simpledb.record` | 页内记录管理 |
| `RID` | `simpledb.record` | 记录标识 |
| `Plan` | `simpledb.plan` | 执行计划接口（引擎内部） |
| `SelectPlan` | `simpledb.plan` | 执行层 Filter Plan |
| `ProjectPlan` | `simpledb.plan` | 执行层 Project Plan |
| `TablePlan` | `simpledb.plan` | 执行层全表扫描 Plan |
| `ProductPlan` | `simpledb.plan` | 执行层笛卡尔积 Plan |
| `Transaction` | `simpledb.tx` | 事务管理 |
| `LogMgr` | `simpledb.log` | WAL 日志 |
| `LogIterator` | `simpledb.log` | 日志迭代 |
| `RecoveryMgr` | `simpledb.tx.recovery` | 崩溃恢复 |
| `ConcurrencyMgr` | `simpledb.tx.concurrency` | 并发控制 |
| `LockTable` | `simpledb.tx.concurrency` | 锁表 |
| `Buffer` | `simpledb.buffer` | 单个缓冲区 |
| `HashIndex` | `simpledb.index.hash` | 哈希索引 |
| `BTreeIndex` | `simpledb.index.btree` | B+ 树索引 |

### 8.2 需要改造的类

| 现有类 | 改造方向 | 负责模块 |
|--------|---------|---------|
| `Lexer` (`simpledb.parse`) | 重构为支持 `>` `<` `!=` `>=` `<=` `OR` `NOT`、输出 Token 列表、包含行号列号 | SQL Compiler |
| `Parser` (`simpledb.parse`) | 重构为输出 AST 而非 Data 对象；支持完整表达式优先级 | SQL Compiler |
| `Term` (`simpledb.query`) | 扩展支持 `!=` `<` `<=` `>` `>=`（当前仅 `=`） | SQL Compiler / Engine |
| `Predicate` (`simpledb.query`) | 扩展支持 OR / NOT（当前仅 AND） | SQL Compiler / Engine |
| `SimpleDB` (`simpledb.server`) | 改为使用新 Compiler + Executor 入口 | Engine |
| `BasicQueryPlanner` (`simpledb.plan`) | 改为接收 LogicalPlan 而非 QueryData | Engine |
| `BasicUpdatePlanner` (`simpledb.plan`) | 同上 | Engine |
| `BufferMgr` (`simpledb.buffer`) | 添加 LRU/FIFO 策略、缓存统计 | Storage |
| `FileMgr` (`simpledb.file`) | BLOCK_SIZE 改为 4096；添加空闲页管理 | Storage |

### 8.3 需要新增的类

| 新增类 | 包 | 负责模块 |
|--------|---|---------|
| `SQLCompilerImpl` | `simpledb.compiler` | SQL Compiler |
| `LexerImpl` | `simpledb.compiler` | SQL Compiler |
| `ParserImpl` | `simpledb.compiler` | SQL Compiler |
| `SemanticAnalyzerImpl` | `simpledb.compiler` | SQL Compiler |
| `PlanBuilderImpl` | `simpledb.compiler` | SQL Compiler |
| `Token` | `simpledb.compiler` | SQL Compiler |
| `TokenType` | `simpledb.compiler` | SQL Compiler |
| AST 节点类 (8个) | `simpledb.ast` | SQL Compiler |
| `LogicalPlan` 及子类 (6个) | `simpledb.logical` | SQL Compiler |
| `CompileError` | `simpledb.shared` | 共享 |
| `ExecuteResult` | `simpledb.shared` | 共享 |
| `ColumnType` | `simpledb.shared` | 共享 |
| `ColumnDef` | `simpledb.shared` | 共享 |
| `ExecutorImpl` | `simpledb.engine` | Engine |
| `PlanConverterImpl` | `simpledb.engine` | Engine |
| `CatalogMgrImpl` | `simpledb.engine` | Engine |
| `StorageEngineImpl` | `simpledb.engine` | Engine |
| `PageManagerImpl` | `simpledb.storage` | Storage |
| `BufferManagerImpl` | `simpledb.storage` | Storage |
| `LRUBufferPool` | `simpledb.storage` | Storage |
| `FIFOBufferPool` | `simpledb.storage` | Storage |
| `CacheStats` | `simpledb.storage` | Storage |

---

## 9. 集成流程示例

### 9.1 SELECT 完整流程

```java
// 1. 初始化
SQLCompiler compiler = new SQLCompilerImpl();
CatalogMgr catalog = new CatalogMgrImpl(tx);
Executor executor = new ExecutorImpl();

// 2. 编译
String sql = "SELECT name, age FROM student WHERE age > 18;";
LogicalPlan plan = compiler.compile(sql, catalog);
// plan 是: Project[name,age] → Filter[age>18] → SeqScan[student]

// 3. 执行
ExecuteResult result = executor.execute(plan, tx);

// 4. 输出
System.out.println(result.formatted());
// +-------+-----+
// | name  | age |
// +-------+-----+
// | Alice |  20 |
// +-------+-----+
```

### 9.2 INSERT 完整流程

```java
String sql = "INSERT INTO student(id, name, age) VALUES (1, 'Alice', 20);";
LogicalPlan plan = compiler.compile(sql, catalog);
// plan 是: InsertPlan[student, (id,name,age), (1,'Alice',20)]

ExecuteResult result = executor.execute(plan, tx);
// result.getAffectedRows() == 1
```

### 9.3 Plan 可视化

```java
LogicalPlan plan = compiler.compile("SELECT name FROM student WHERE age > 18;", catalog);
System.out.println(plan.explain(0));
// 输出:
// Project[name]
//   Filter[age > 18]
//     SeqScan[student]
```

---

## 10. 错误处理规范

### 10.1 错误信息格式

```
<ErrorType> at line <L>, column <C>: <message>
```

示例:

```
LexicalError at line 1, column 15: unexpected character '@'
SyntaxError at line 2, column 1: unexpected token ';', expected: IDENTIFIER | '(' | NOT
SemanticError at line 1, column 22: table 'scores' does not exist
SemanticError at line 1, column 15: column 'score' does not exist in table 'student'
SemanticError at line 3, column 30: type mismatch: expected INT, got VARCHAR
```

### 10.2 各阶段错误类型

| 阶段 | ErrorType | 示例 |
|------|-----------|------|
| Lexer | LEXICAL | 非法字符、未闭合字符串、非法数字 |
| Parser | SYNTAX | 缺分号、括号不匹配、关键字拼写错误 |
| Semantic | SEMANTIC | 表/列不存在、类型不匹配、列数不一致 |

---

## 11. 三人分工与接口依赖

```
              ┌─────────────────────────┐
              │   接口定义 (本文档)       │
              │   LogicalPlan            │
              │   CatalogReader          │
              │   PageManager            │
              │   BufferManager          │
              └────────────┬────────────┘
                           │
          ┌────────────────┼────────────────┐
          ↓                ↓                ↓
   Member A            Member B         Member C
   SQL Compiler        DB Engine        Storage System
          │                │                │
   实现:                 实现:              实现:
   - LexerImpl          - ExecutorImpl    - PageManagerImpl
   - ParserImpl         - PlanConverter   - BufferManagerImpl
   - SemanticAnalyzer   - CatalogMgrImpl - LRUBufferPool
   - PlanBuilder        - StorageEngine  - FIFOBufferPool
   - AST 节点            - 接入现有         - CacheStats
   - LogicalPlan 节点     Plan/Scan 算子
          │                │                │
          │ 依赖:           │ 依赖:           │ 依赖:
          │ CatalogReader  │ PageManager    │ 无
          │ (Mock)         │ BufferManager  │
          │                │ (Mock)         │
          └────────────────┴────────────────┘
                           │
                           ↓
                     集成测试
```

### 并行开发策略

1. **Day 1**: 三人共同 Review 本文档，确认接口无异议
2. **Day 2**: 各自创建 Mock 实现（基于接口）
3. **Day 3-N**: 各自开发，定期交叉验证接口调用
4. **集成期**: 替换 Mock 为真实实现，跑端到端测试

### Mock 示例（Member B 使用 Member A 的 Mock）

```java
// Member B 开发 Executor 时，使用 Mock 编译器
SQLCompiler mockCompiler = (sql, catalog) -> {
    // 直接返回预设的 LogicalPlan
    return new ProjectPlan(
        new FilterPlan(
            new SeqScanPlan("student"),
            new BinaryExprNode(">", new FieldNode("age", 0, 0),
                               new LiteralNode(new Constant(18), 0, 0), 0, 0)
        ),
        Arrays.asList("name")
    );
};
```

---

## 12. 包结构规划

```
src/
├── simpledb/
│   ├── shared/                    ← 新增: 共享类型
│   │   ├── Constant.java          ← 从 query 包迁移
│   │   ├── ColumnType.java        ← 新增
│   │   ├── ColumnDef.java         ← 新增
│   │   ├── CompileError.java      ← 新增
│   │   └── ExecuteResult.java     ← 新增
│   │
│   ├── compiler/                  ← 新增: SQL 编译器
│   │   ├── SQLCompiler.java       ← 接口
│   │   ├── SQLCompilerImpl.java   ← 实现
│   │   ├── Lexer.java             ← 接口
│   │   ├── LexerImpl.java         ← 实现
│   │   ├── Parser.java            ← 接口
│   │   ├── ParserImpl.java        ← 实现
│   │   ├── SemanticAnalyzer.java  ← 接口
│   │   ├── SemanticAnalyzerImpl.java
│   │   ├── PlanBuilder.java       ← 接口
│   │   ├── PlanBuilderImpl.java
│   │   ├── CatalogReader.java     ← 接口
│   │   ├── Token.java             ← 新增
│   │   └── TokenType.java         ← 新增
│   │
│   ├── ast/                       ← 新增: AST 节点
│   │   ├── ASTNode.java
│   │   ├── CreateTableNode.java
│   │   ├── InsertNode.java
│   │   ├── SelectNode.java
│   │   ├── DeleteNode.java
│   │   ├── ColumnDefNode.java
│   │   ├── ExpressionNode.java
│   │   ├── LiteralNode.java
│   │   ├── FieldNode.java
│   │   ├── BinaryExprNode.java
│   │   ├── UnaryExprNode.java
│   │   └── BooleanExprNode.java
│   │
│   ├── logical/                   ← 新增: LogicalPlan
│   │   ├── LogicalPlan.java       ← 抽象基类
│   │   ├── SeqScanPlan.java
│   │   ├── FilterPlan.java
│   │   ├── ProjectPlan.java
│   │   ├── CreateTablePlan.java
│   │   ├── InsertPlan.java
│   │   └── DeletePlan.java
│   │
│   ├── engine/                    ← 新增: 执行引擎
│   │   ├── Executor.java          ← 接口
│   │   ├── ExecutorImpl.java      ← 实现
│   │   ├── PlanConverter.java     ← 接口
│   │   ├── PlanConverterImpl.java ← 实现
│   │   ├── CatalogMgr.java        ← 接口 (extends CatalogReader)
│   │   ├── CatalogMgrImpl.java    ← 实现
│   │   └── StorageEngine.java     ← 接口
│   │
│   ├── storage/                   ← 新增: 存储系统接口
│   │   ├── PageManager.java       ← 接口
│   │   ├── PageManagerImpl.java   ← 实现
│   │   ├── PageId.java            ← 从 file 包迁移
│   │   ├── BufferManager.java     ← 接口
│   │   ├── BufferManagerImpl.java ← 实现
│   │   ├── LRUBufferPool.java     ← 新增
│   │   ├── FIFOBufferPool.java    ← 新增
│   │   ├── CacheStats.java        ← 新增
│   │   ├── ReplacementPolicy.java ← 枚举
│   │   ├── FileManager.java       ← 接口
│   │   └── LogManager.java        ← 接口
│   │
│   ├── file/                      ← 保留: 底层文件 I/O
│   │   ├── FileMgr.java           ← 改造: BLOCK_SIZE=4096
│   │   └── Page.java              ← 保留
│   │
│   ├── buffer/                    ← 保留: 改造为使用新接口
│   │   ├── Buffer.java            ← 保留
│   │   └── BufferMgr.java         ← 改造: 添加 LRU/FIFO
│   │
│   ├── log/                       ← 保留
│   ├── record/                    ← 保留
│   ├── tx/                        ← 保留
│   ├── query/                     ← 保留: 执行层算子
│   ├── plan/                      ← 保留: 执行层 Plan
│   ├── index/                     ← 保留
│   ├── metadata/                  ← 保留: 改造为实现 CatalogMgr
│   ├── opt/                       ← 保留: 查询优化
│   ├── materialize/               ← 保留
│   ├── multibuffer/               ← 保留
│   ├── jdbc/                      ← 保留
│   └── server/                    ← 改造: 使用新 Compiler + Executor
```
