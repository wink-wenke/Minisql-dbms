# 大型平台软件设计实习——DBMS 技术文档与分层得分目标

> **文档定位**：本文件依据《大型平台软件设计实习》指导书、2026 实训课程 PPT、SQL 编译器设计与实现 PPT 整理，用于项目的**工程化开发、任务拆解、阶段验收、答辩准备与得分目标管理**。
>
> **重要说明**：课程材料明确了“必做 Core / 进阶 Advanced / 扩展 Extension”的任务结构，但没有给出公开的、逐项量化的官方分值表。因此，本文中的“基础 / 良好 / 进阶 / 冲刺”分数区间属于**项目管理用的建议目标**，不是教师公布的正式评分细则。正式评分仍以课程教师现场验收、隐藏测试及最终要求为准。

---

## 1. 项目总体目标

本实训的核心不是单独实现一个 SQL Parser，而是沿着：

```text
SQL
  ↓
Token
  ↓
AST
  ↓
Semantic Analysis
  ↓
Logical Plan
  ↓
Optimization
  ↓
Execution
  ↓
Page / Buffer
  ↓
Disk
```

完成一个从“**语言层**”贯通到“**系统层**”的简化 DBMS。

指导书明确将总体任务拆分为三个部分：

1. **SQL 编译器**
   - 词法分析
   - 语法分析
   - 语义分析
   - 执行计划生成

2. **页式存储系统**
   - Page
   - 页面分配、释放、读写
   - Buffer
   - LRU / FIFO
   - 持久化与访问接口

3. **小型数据库系统**
   - Execution Engine
   - Storage Engine
   - System Catalog
   - CLI / API
   - 完整执行 `CREATE / INSERT / SELECT / DELETE`

课程材料强调：编译器并非孤立实验，而是数据库前端；其输出的 Logical Plan 应直接成为后续执行引擎的接口。

---

# 2. 系统总体架构

## 2.1 推荐架构

```text
┌──────────────────────────────────────────────┐
│                  CLI / API                   │
└──────────────────────┬───────────────────────┘
                       │ SQL
                       ↓
┌──────────────────────────────────────────────┐
│               SQL Compiler Frontend          │
│                                              │
│  Lexer → Parser → AST → Semantic → Plan     │
│                           │         │        │
│                           │         └────────┼── Logical Plan
│                           ↓                  │
│                        Catalog               │
└──────────────────────────┬───────────────────┘
                           │ Plan
                           ↓
┌──────────────────────────────────────────────┐
│                Execution Engine              │
│                                              │
│ CreateTable / Insert / SeqScan / Filter /    │
│ Project                                     │
└──────────────────────────┬───────────────────┘
                           │ Storage API
                           ↓
┌──────────────────────────────────────────────┐
│                 Storage Engine               │
│                                              │
│ Row ↔ Page ↔ File                            │
│ Free Page Management                         │
└──────────────────────────┬───────────────────┘
                           │ Page API
                           ↓
┌──────────────────────────────────────────────┐
│            Page / Buffer / File System       │
│                                              │
│ Page Allocation / Read / Write               │
│ Buffer Pool / LRU / FIFO / Flush             │
└──────────────────────────┬───────────────────┘
                           ↓
                     Persistent Disk
```

## 2.2 三大模块职责

| 模块 | 核心职责 | 对外主要接口 | 最终产物 |
|---|---|---|---|
| SQL Compiler | SQL → Token → AST → Semantic → Plan | `parse()`、`analyze()`、`build_plan()` | AST / Logical Plan |
| Storage System | Page / Buffer / File | `read_page()`、`write_page()`、`get_page()`、`flush_page()` | 持久化页数据 |
| DB Engine | Plan → Execute → Row/Page | `execute(plan)` | 查询结果 / 数据修改 |

---

# 3. 官方任务范围梳理

## 3.1 SQL 编译器：Core

课程要求至少支持：

```sql
CREATE TABLE
INSERT
SELECT
DELETE
```

基础查询还需要支持：

```sql
WHERE
比较运算
AND / OR / NOT
括号
```

建议首先锁定以下最小可验收子集：

```sql
CREATE TABLE student(
    id INT,
    name VARCHAR,
    age INT
);

INSERT INTO student(id, name, age)
VALUES (1, 'Alice', 20);

SELECT id, name
FROM student
WHERE age > 18 AND id != 3;

DELETE FROM student
WHERE id = 1;
```

### 3.1.1 Lexer

必须支持：

```text
KEYWORD
IDENTIFIER
CONST
OPERATOR
DELIMITER
```

Token 至少包含：

```text
<TokenType, Lexeme, Line, Column>
```

应识别：

```text
关键字：
SELECT FROM WHERE CREATE TABLE INSERT INTO VALUES DELETE

标识符：
student age user_name

常量：
20 3.14 'Alice' 'Tom''s book'

运算符：
= != > >= < <= + - * /

分隔符：
( ) , ;
```

工程要求：

- 关键字大小写不敏感
- 字符串内容保持原样
- 跳过空白
- 处理单行 / 多行注释
- 处理字符串与转义
- 多字符运算符不能被错误拆分
- 非法输入不能导致程序崩溃
- 错误应包含：**错误类型 + 位置 + 原因**

---

## 3.2 Parser + AST

Parser 的目标不是简单判断“SQL 合法”，而是构建可供后续阶段继续使用的 AST。

推荐 AST：

```text
Statement
├── CreateTableStmt
├── InsertStmt
├── SelectStmt
└── DeleteStmt

Expression
├── BinaryExpr
├── UnaryExpr
├── IdentifierExpr
└── LiteralExpr
```

AST 应包含：

```text
表名
列名
操作符
子表达式
源码位置
```

### 推荐 SQL 子集文法

```ebnf
statement       -> create_stmt
                 | insert_stmt
                 | select_stmt
                 | delete_stmt ;

select_stmt     -> SELECT select_list
                   FROM IDENTIFIER
                   where_opt ';' ;

select_list     -> '*'
                 | IDENTIFIER { ',' IDENTIFIER } ;

where_opt       -> WHERE expression
                 | ε ;

delete_stmt     -> DELETE FROM IDENTIFIER where_opt ';' ;

create_stmt     -> CREATE TABLE IDENTIFIER '('
                   column_def { ',' column_def }
                   ')' ';' ;

column_def      -> IDENTIFIER type ;

type            -> INT
                 | VARCHAR ;

insert_stmt     -> INSERT INTO IDENTIFIER
                   '(' id_list ')'
                   VALUES '(' value_list ')' ';' ;
```

### 表达式优先级

推荐：

```text
expression
    → or_expr

or_expr
    → and_expr
      { OR and_expr }

and_expr
    → not_expr
      { AND not_expr }

not_expr
    → NOT not_expr
    | comparison

comparison
    → primary
      [ comp_op primary ]

primary
    → IDENTIFIER
    | CONST
    | '(' expression ')'
```

必须保证：

```text
NOT > 比较运算 > AND > OR
```

例如：

```sql
a = 1 OR b = 2 AND c = 3
```

应解析为：

```text
OR
├── a = 1
└── AND
    ├── b = 2
    └── c = 3
```

---

# 4. Semantic Analysis

语义分析负责把“语法正确”提升为“语义可执行”。

## 4.1 必做检查

### 表存在性

```text
SELECT * FROM student;
```

必须检查：

```text
student 是否已在 Catalog 中注册
```

### 列存在性

```text
SELECT score FROM student;
```

如果 `score` 不存在，应输出类似：

```text
SemanticError:
column 'score' does not exist in table 'student'
```

### 名字绑定

```text
Identifier
    ↓
Catalog
    ↓
具体表 / 列定义
```

### 类型检查

基础类型规则可集中定义：

```text
INT + INT         → INT
INT > INT         → BOOL
VARCHAR = VARCHAR → BOOL
BOOL AND BOOL     → BOOL
NOT BOOL          → BOOL
INT + VARCHAR     → ERROR
```

### INSERT 检查

必须检查：

```text
列数
列顺序
值类型
```

例如：

```sql
INSERT INTO student(id, name)
VALUES ('Alice', 1);
```

应识别：

```text
student.id expects INT, but VARCHAR found.
student.name expects VARCHAR, but INT found.
```

---

# 5. Catalog 设计

Catalog 在编译器中承担类似“符号表”的角色：

```text
Symbol Table ↔ Catalog
```

推荐维护：

```text
createTable()
findTable()
findColumn()
getType()
```

示例：

```text
Catalog
├── student
│   ├── id   : INT
│   ├── name : VARCHAR
│   └── age  : INT
└── course
    ├── cid   : INT
    ├── title : VARCHAR
    └── score : INT
```

系统集成时，Catalog 不应只存在于内存中；最终 DBMS 要让表元数据具备持久化能力。

---

# 6. Logical Plan 设计

Logical Plan 是：

```text
SQL Compiler
        ↓
Logical Plan
        ↓
Execution Engine
```

两大模块之间的稳定接口。

## 6.1 最低算子集合

```text
CreateTable
Insert
SeqScan
Filter
Project
```

## 6.2 AST → Plan

对于：

```sql
SELECT name
FROM student
WHERE age > 18;
```

计划应为：

```text
Project[name]
    ↓
Filter[age > 18]
    ↓
SeqScan[student]
```

转换规则：

```text
FROM      → 数据源 / SeqScan
WHERE     → Filter
SELECT    → Project
CREATE    → CreateTable
INSERT    → Insert
```

Plan 节点只保存执行需要的信息，不再携带无关语法细节。

---

# 7. Query Optimization

课程材料将优化作为重要进阶能力，至少建议实现 2 个规则，并能展示优化前后结构变化。

## 7.1 推荐优化规则

### 规则 1：常量折叠

```text
age > 10 + 8
        ↓
age > 18
```

### 规则 2：布尔化简

```text
x AND TRUE
        ↓
x
```

### 规则 3：Projection Pruning

只保留查询真正需要的列。

### 规则 4：Predicate Pushdown

在 JOIN / 子查询等扩展场景中，尽量提前过滤。

### 规则 5：冗余节点消除

例如：

```text
Project[*]
Filter[TRUE]
```

可以直接删除。

---

# 8. 页式存储系统

## 8.1 Page

课程指导书要求采用固定大小页，示例为：

```text
Page Size = 4KB
```

每页具有唯一页号。

最小功能：

```text
allocate_page()
free_page()
read_page(page_id)
write_page(page_id, data)
```

推荐页结构：

```text
Page
├── Header
│   ├── page_id
│   ├── page_type
│   ├── free_space
│   └── record_count / slot_count
├── Row / Slot Data
└── Free Space
```

> 这里不要求一开始复刻 MySQL 等工业数据库的复杂页结构；实训目标是形成清晰、稳定、可持久化的页式存储模型。

---

# 9. Buffer Pool / Cache

缓存系统用于减少重复磁盘 I/O。

## 9.1 必做能力

```text
get_page(page_id)
flush_page(page_id)
```

同时记录：

```text
cache hit
cache miss
page replacement
```

## 9.2 替换策略

至少支持：

```text
LRU
FIFO
```

其中建议优先实现 LRU，再加入 FIFO 作为对比实验。

## 9.3 建议日志

```text
GET page=10  HIT
GET page=11  MISS
EVICT page=3 policy=LRU
FLUSH page=10
```

测试报告建议统计：

```text
总访问次数
命中次数
未命中次数
命中率
淘汰次数
```

---

# 10. Storage Engine

Storage Engine 负责：

```text
Row ↔ Page ↔ File
```

## 10.1 记录序列化

推荐设计：

```text
Row
 ↓ serialize
Byte Buffer
 ↓
Page
 ↓
File
```

读取时：

```text
File
 ↓
Page
 ↓ deserialize
Row
```

## 10.2 必须考虑

- 一个表对应哪些页
- 新记录写入哪个页
- 页空间不足时如何申请新页
- 删除后如何处理空闲空间
- 空闲页如何管理
- 数据如何从内存刷回磁盘

---

# 11. Execution Engine

执行引擎负责执行 Logical Plan。

## 11.1 基础执行算子

### CreateTable

职责：

```text
创建表结构
+
更新 Catalog
+
持久化元数据
```

### Insert

职责：

```text
逻辑记录
→ Row
→ 序列化
→ 找到目标 Page
→ 写入
→ Flush / Persistence
```

### SeqScan

职责：

```text
遍历指定表所有数据页
→ 逐条读取 Row
→ 输出记录
```

### Filter

职责：

```text
输入 Row
→ 计算 WHERE
→ true 通过
→ false 丢弃
```

### Project

职责：

```text
输入 Row
→ 按 SELECT 列表提取
→ 输出结果
```

---

# 12. DELETE 设计

课程要求：

```sql
DELETE FROM student WHERE id = 1;
```

能够正确执行。

实现方式可以采用：

```text
方案 A：删除标记（推荐作为基础实现）
方案 B：物理删除
```

基础阶段优先保证：

```text
DELETE 成功
+
再次 SELECT 时不可见
+
程序重启后结果正确
```

---

# 13. 数据持久化

这是 DBMS 从“能运行的 Demo”升级到“真正数据库原型”的关键。

课程明确要求：

> 所有表数据和元数据（系统目录）必须通过页式存储系统进行持久化，程序重启后数据不丢失。

因此必须完成：

```text
CREATE TABLE
   ↓
Catalog 持久化

INSERT
   ↓
Page 持久化

程序重启
   ↓
重新加载 Catalog
   ↓
重新读取 Page
   ↓
数据仍可查询
```

最低验收流程：

```sql
CREATE TABLE student(id INT, name VARCHAR, age INT);

INSERT INTO student(id,name,age)
VALUES (1,'Alice',20);

SELECT * FROM student;
```

退出程序。

再次启动：

```sql
SELECT * FROM student;
```

仍然能查到原数据。

---

# 14. CLI / 用户交互

建议实现：

```text
MiniDB >
```

支持：

```sql
CREATE TABLE ...
INSERT INTO ...
SELECT ...
DELETE ...
.quit
```

至少做到：

```text
成功 → 明确提示
查询 → 返回结果集
失败 → 返回错误类型、位置、原因
```

---

# 15. 推荐工程目录

课程材料给出了一个推荐结构，可直接扩展为：

```text
database_system/
├── sql_compiler/
│   ├── lexer.py
│   ├── parser.py
│   ├── ast.py
│   ├── semantic.py
│   ├── planner.py
│   └── catalog.py
│
├── storage/
│   ├── page.py
│   ├── buffer.py
│   ├── file_manager.py
│   └── serializer.py
│
├── engine/
│   ├── executor.py
│   ├── storage_engine.py
│   ├── operators/
│   │   ├── create_table.py
│   │   ├── insert.py
│   │   ├── seq_scan.py
│   │   ├── filter.py
│   │   └── project.py
│   └── catalog_manager.py
│
├── optimizer/
│   ├── optimizer.py
│   └── rules.py
│
├── cli/
│   └── main.py
│
├── tests/
│   ├── sql/
│   ├── storage/
│   ├── engine/
│   ├── integration/
│   └── fuzz/
│
├── docs/
│   ├── grammar.md
│   ├── architecture.md
│   ├── ast.md
│   ├── catalog.md
│   ├── plan.md
│   └── storage.md
│
├── data/
├── scripts/
├── README.md
└── requirements.txt
```

---

# 16. 模块接口建议

## 16.1 SQL Compiler

```python
tokens = lexer.tokenize(sql)
ast = parser.parse(tokens)
semantic_result = semantic.analyze(ast, catalog)
plan = planner.build(semantic_result)
optimized_plan = optimizer.optimize(plan)
```

## 16.2 Storage

```python
page_id = file_manager.allocate_page()

page = buffer_pool.get_page(page_id)

page.write(...)

buffer_pool.flush_page(page_id)
```

## 16.3 Engine

```python
plan = compiler.compile(sql)
result = executor.execute(plan)
```

核心原则：

> 上层模块只依赖下层模块的稳定接口，不直接访问下层内部数据结构。

---

# 17. 测试体系

课程明确要求测试不能只做样例运行，而要覆盖正常、错误、边界、隐藏测试等情况。

## 17.1 正常测试

```text
CREATE
INSERT
SELECT
DELETE
```

### 示例

```sql
CREATE TABLE student(id INT, name VARCHAR, age INT);

INSERT INTO student(id,name,age)
VALUES (1,'Alice',20);

INSERT INTO student(id,name,age)
VALUES (2,'Bob',17);

SELECT id,name
FROM student
WHERE age > 18;

DELETE FROM student
WHERE id = 1;
```

---

## 17.2 词法错误

```text
非法字符：
@

未闭合字符串：
'Alice

非法数字格式

错误位置：
line + column
```

---

## 17.3 语法错误

```text
缺分号
括号不匹配
SELECT 结构不完整
WHERE 后没有表达式
```

错误提示建议：

```text
SyntaxError at line 3, column 19
unexpected token: ';'
expected: IDENTIFIER | CONST | '(' | NOT
```

---

## 17.4 语义错误

```text
表不存在
列不存在
类型不匹配
INSERT 列数不一致
INSERT 列序不一致
```

---

## 17.5 边界测试

至少考虑：

```text
空输入
多条 SQL
超长标识符
大小写混合
空字符串
极端整数
嵌套括号
AND / OR 混合
NOT
重复建表
删除后再次查询
大量数据插入
程序重启
```

---

# 18. Fuzz Testing（进阶）

课程 PPT 将 Fuzz Testing 作为高级测试能力。

基本流程：

```text
随机生成 SQL
     ↓
随机变异 / 破坏
     ↓
运行 Compiler
     ↓
检查行为
```

重点关注：

```text
Crash
Wrong Accept
Wrong Reject
Error Location
```

正确 SQL 应：

```text
稳定通过
```

非法 SQL 应：

```text
稳定拒绝
+
不崩溃
+
错误位置合理
```

---

# 19. AI 辅助开发要求

课程明确允许大模型辅助：

```text
代码骨架
测试补全
Debug
Parser 方案比较
编译错误解释
模块接口重构
```

但学生必须自己掌握：

```text
文法设计
AST 设计
语义规则
Plan 解释
优化等价性验证
最终集成
```

不能出现：

```text
代码能跑但讲不清
改一个需求就失效
隐藏测试大量失败
不知道错误属于 Lexer / Parser / Semantic / Plan 哪个阶段
无法解释 AI 生成代码
```

因此本项目的工程目标应从：

```text
“写出代码”
```

升级为：

```text
“能解释、能验证、能修改、能集成、能答辩”
```

---

# 20. 分阶段开发路线

## Phase 0：需求冻结

目标：

```text
明确 SQL 子集
明确模块边界
明确 AST
明确 Catalog
明确 Plan
明确 Page 接口
明确测试目录
```

交付物：

```text
README.md
grammar.md
architecture.md
接口定义
任务分工表
```

---

## Phase 1：SQL Compiler Core

完成：

```text
Lexer
Parser
AST
Semantic
Catalog
Logical Plan
```

验收：

```text
CREATE / INSERT / SELECT / DELETE
```

以及：

```text
WHERE
比较运算
AND / OR / NOT
括号
```

---

## Phase 2：Storage Core

完成：

```text
Page
File Manager
Buffer Pool
LRU
FIFO
Flush
Persistence
```

验收：

```text
分配
释放
读
写
缓存命中统计
页替换日志
```

---

## Phase 3：DB Engine Integration

打通：

```text
SQL
→ Compiler
→ Plan
→ Executor
→ Storage Engine
→ Page
→ Disk
```

验收：

```text
CREATE
INSERT
SELECT
DELETE
```

完整执行。

---

## Phase 4：Persistence + Integration Test

重点：

```text
重启不丢数据
Catalog 不丢
Page 不丢
删除结果正确
查询结果正确
```

形成一条完整回归脚本。

---

## Phase 5：Optimization + Robustness

至少实现：

```text
2 个优化规则
```

同时增强：

```text
错误诊断
边界测试
Fuzz Testing
Plan 可视化
```

---

## Phase 6：答辩准备

准备随机抽查：

```text
parseExpression()
resolveColumn()
buildPlan()
get_page()
flush_page()
SeqScan
Filter
Project
```

还要能现场：

```text
画 AST
画 Plan
判断错误阶段
解释数据如何落盘
解释程序重启后如何恢复
```

---

# 21. 建议的四档得分目标

> **注意：以下是为了项目管理而设计的“分层目标”，不是教师公布的正式分值。**
>
> 思路是：先保证 Core，再逐步获取 Advanced / Extension 的区分度。

---

## Level 1：基础达标——“先拿稳基础分”

### 目标

实现一套真正可运行的简化 DBMS 最小闭环。

### SQL Compiler

- [x] Lexer
- [x] TokenType / Lexeme / Line / Column
- [x] CREATE
- [x] INSERT
- [x] SELECT
- [x] DELETE
- [x] 基础 AST
- [x] 基础 Catalog
- [x] 表存在性检查
- [x] 列存在性检查
- [x] 基础类型检查
- [x] Logical Plan
- [x] 基础错误提示

### Storage

- [x] Page
- [x] 4KB 固定页
- [x] Page Allocate
- [x] Page Read
- [x] Page Write
- [x] Page Free
- [x] 基础文件持久化

### Engine

- [x] CreateTable
- [x] Insert
- [x] SeqScan
- [x] Filter
- [x] Project
- [x] DELETE

### 测试

- [x] 正常 SQL
- [x] 基本语法错误
- [x] 基本语义错误

### 最低成果

必须能够演示：

```text
CREATE
→ INSERT
→ SELECT
→ DELETE
→ SELECT
```

并且程序可以正常运行、不因错误 SQL 崩溃。

### 建议得分目标

```text
60 分左右 / 达到课程基础要求
```

---

# Level 2：良好完成——“把系统真正做完整”

在 Level 1 基础上增加：

### SQL Compiler

- [x] AND
- [x] OR
- [x] NOT
- [x] 括号优先级
- [x] 复杂表达式
- [x] 更完整的错误定位
- [x] AST 结构稳定可扩展

### Storage

- [x] Buffer Pool
- [x] LRU
- [x] FIFO
- [x] Cache Hit / Miss
- [x] Replacement Log
- [x] Flush

### Engine

- [x] Row ↔ Page 映射
- [x] Serialization
- [x] Deserialization
- [x] 空闲页管理
- [x] 大量数据扫描

### Integration

- [x] Catalog 与存储系统真正打通
- [x] 所有数据通过 Page 持久化
- [x] 重启后数据仍可读取

### 测试

- [x] 核心回归测试
- [x] 边界测试
- [x] 重启持久化测试
- [x] 删除后再次查询

### 建议得分目标

```text
70～80 分
```

这个阶段意味着：

> 不只是“SQL 编译器能跑”，而是已经形成一个完整的小型数据库原型。

---

# Level 3：进阶优秀——“开始拉开项目差距”

在 Level 2 基础上增加：

### Query Optimization

至少完成两个：

```text
常量折叠
布尔化简
Projection Pruning
Predicate Pushdown
冗余节点消除
```

并展示：

```text
优化前 Plan
      ↓
Optimizer
      ↓
优化后 Plan
```

### Error Diagnosis

错误必须做到：

```text
类型
+
位置
+
原因
+
必要时给出 expected token
```

### Fuzz Testing

建立：

```text
合法 SQL 随机测试
非法 SQL 随机测试
Crash 检查
Wrong Accept 检查
Wrong Reject 检查
```

### Plan Visualization

支持：

```text
树形 Plan
```

或：

```text
JSON Plan
```

### 测试质量

建立：

```text
单元测试
+
集成测试
+
边界测试
+
Fuzz Testing
```

并对结果做统计。

### 建议得分目标

```text
80～90 分
```

这一档的核心不是“代码更多”，而是：

```text
正确性
+
健壮性
+
可验证性
+
可解释性
```

---

# Level 4：扩展冲刺——“冲击最高区分度”

在 Level 3 基础上选择少量高级能力深入，不建议无计划地堆功能。

## 推荐扩展路线 A：SQL 扩展

```text
UPDATE
ORDER BY
GROUP BY
JOIN
NULL
更多数据类型
```

## 推荐扩展路线 B：优化器

```text
规则优化框架
Cost Model
EXPLAIN
```

## 推荐扩展路线 C：执行系统

```text
JOIN 算子
Index Scan
B+ Tree Index
```

## 推荐扩展路线 D：工程化

```text
完整 CI
自动化回归
Fuzz 自动执行
Plan 可视化
Benchmark
统一错误体系
模块化接口
```

### 建议得分目标

```text
90～95+ 分（取决于教师实际评分方式）
```

达到这一档的重点是：

> **不要为了“功能多”而牺牲稳定性。**

优先级应始终是：

```text
Core 正确
> Core 完整
> 测试充分
> 优化可靠
> 扩展功能
```

---

# 22. 推荐的项目“评分树”

为了开发时直观管理，可以把整个项目理解成：

```text
DBMS 实训
│
├── A. SQL 编译器
│   ├── A1 Lexer
│   ├── A2 Parser
│   ├── A3 AST
│   ├── A4 Semantic
│   ├── A5 Catalog
│   └── A6 Plan
│
├── B. 存储系统
│   ├── B1 Page
│   ├── B2 File
│   ├── B3 Buffer
│   ├── B4 LRU/FIFO
│   └── B5 Persistence
│
├── C. 数据库引擎
│   ├── C1 CreateTable
│   ├── C2 Insert
│   ├── C3 SeqScan
│   ├── C4 Filter
│   ├── C5 Project
│   └── C6 Delete
│
├── D. 测试
│   ├── D1 Unit
│   ├── D2 Integration
│   ├── D3 Boundary
│   ├── D4 Persistence
│   └── D5 Fuzz
│
├── E. 优化
│   ├── E1 Constant Folding
│   ├── E2 Boolean Simplification
│   ├── E3 Projection Pruning
│   ├── E4 Predicate Pushdown
│   └── E5 Redundant Node Elimination
│
└── F. 扩展
    ├── F1 JOIN
    ├── F2 GROUP BY
    ├── F3 ORDER BY
    ├── F4 Index
    ├── F5 EXPLAIN
    └── F6 Cost Model
```

---

# 23. 最推荐的开发优先级

如果团队时间有限，不建议直接从 JOIN、B+Tree、事务等高级功能开始。

推荐优先级：

```text
① Lexer / Parser / AST
        ↓
② Semantic / Catalog
        ↓
③ Logical Plan
        ↓
④ Page / File
        ↓
⑤ Buffer Pool / LRU
        ↓
⑥ Storage Engine
        ↓
⑦ Execution Engine
        ↓
⑧ Persistence
        ↓
⑨ Integration Test
        ↓
⑩ Optimization
        ↓
⑪ Fuzz / Visualization
        ↓
⑫ JOIN / Index / Cost Model
```

原因：

```text
没有 AST
→ 没有稳定编译器输出

没有 Plan
→ 执行引擎无法与编译器解耦

没有 Page
→ Storage Engine 无法落盘

没有 Persistence
→ 不能称为完整数据库原型

没有测试
→ 隐藏测试风险极高

没有 Core
→ 高级功能没有意义
```

---

# 24. 团队并行开发建议

三个人分别负责三大模块是合理的，但**不能采用严格串行的“一个人做完再交给下一个人”模式**，否则容易拖慢整体进度。

推荐：

```text
                接口先行
                   │
      ┌────────────┼────────────┐
      ↓            ↓            ↓
SQL Compiler   Storage      DB Engine
     │             │            │
     │             │            │
     └─────── Mock / Stub ──────┘
                   │
                   ↓
            Integration Test
                   │
                   ↓
                Main
```

核心方法：

> **实现并行，接口先固定，集成后置但持续进行。**

例如 SQL Compiler 可以先输出：

```json
{
  "type": "Select",
  "table": "student",
  "filter": {
    "column": "age",
    "op": ">",
    "value": 18
  },
  "columns": ["name"]
}
```

DB Engine 在真实 Compiler 完成前，可先用 Mock Plan 开发：

```text
Mock Plan
   ↓
Executor
   ↓
Storage Engine
```

这样三个模块不会互相卡住。

---

# 25. Git 工程化建议

推荐工作流：

```text
main
  ↑
develop
  ↑
feature/*
```

开发：

```text
feature/lexer
feature/parser
feature/semantic
feature/page
feature/buffer
feature/executor
feature/storage
```

标准流程：

```text
创建 feature 分支
      ↓
本地开发
      ↓
单元测试
      ↓
提交 commit
      ↓
Push
      ↓
Pull Request
      ↓
Code Review
      ↓
合并 develop
      ↓
Integration Test
      ↓
develop
      ↓
阶段稳定
      ↓
合并 main
```

注意：

```text
Pull Request ≠ git pull
```

这里的 Pull Request 是“发起代码合并请求”。

而：

```text
git pull
```

是：

```text
从远程仓库拉取更新
+
尝试合并到当前本地分支
```

两者不是一回事。

---

# 26. 阶段性里程碑定义

## Milestone 1：编译器最小闭环

```text
SQL
→ Token
→ AST
→ Semantic
→ Plan
```

通过：

```sql
CREATE
INSERT
SELECT
DELETE
```

---

## Milestone 2：存储最小闭环

```text
Page
→ File
→ Buffer
→ Flush
```

通过：

```text
allocate
read
write
free
LRU/FIFO
```

---

## Milestone 3：数据库最小闭环

```text
SQL
→ Compiler
→ Plan
→ Executor
→ Storage
→ Disk
```

完成：

```text
CREATE
INSERT
SELECT
DELETE
```

---

## Milestone 4：真正可验收

```text
错误可定位
+
边界不崩溃
+
数据可持久化
+
测试可重复
+
模块接口稳定
```

---

## Milestone 5：冲刺高分

```text
Optimization
+
Fuzz
+
Plan Visualization
+
高级 SQL
```

---

# 27. 答辩必问问题清单

## 编译器

### Q1：为什么需要 Token？

因为 Parser 不应该直接处理原始字符流，而应处理结构稳定的词法单元。

### Q2：为什么需要 AST？

因为后续 Semantic 和 Plan Generation 不应该依赖原始 Token 序列，而需要结构化语义表示。

### Q3：为什么 AND 优先于 OR？

因为表达式文法通过 `or_expr → and_expr → not_expr → comparison` 明确编码了优先级。

### Q4：语法正确为什么还会报错？

因为：

```text
Syntax Correct
≠
Semantic Correct
```

例如：

```sql
SELECT score FROM student;
```

即使语法正确，`score` 也可能不存在。

---

## 数据库

### Q5：为什么需要 Catalog？

因为 DBMS 必须知道：

```text
表有哪些
列有哪些
每列是什么类型
```

它承担数据库中的名字解析与模式管理职责。

### Q6：为什么要 Page？

因为数据库不能只按照“行”管理磁盘 I/O，而需要以固定大小页为基本物理存储单位。

### Q7：为什么需要 Buffer Pool？

因为磁盘速度远低于内存，缓存可以减少重复 I/O。

### Q8：为什么需要 LRU？

因为最近访问的数据往往更有可能再次被访问，LRU 可作为一种基础缓存淘汰策略。

---

## 系统集成

### Q9：SQL 从输入到落盘经历什么？

```text
SQL
→ Lexer
→ Parser
→ AST
→ Semantic
→ Logical Plan
→ Executor
→ Storage Engine
→ Buffer
→ Page
→ Disk
```

### Q10：为什么不能让 Executor 直接操作文件？

因为这样会导致：

```text
Executor
和
Storage
```

强耦合。

正确方式是：

```text
Executor
   ↓
Storage Engine API
   ↓
Page / Buffer / File
```

---

# 28. 最终验收 Checklist

## Core

```text
[ ] CREATE TABLE
[ ] INSERT
[ ] SELECT
[ ] DELETE
[ ] WHERE
[ ] 比较运算
[ ] AND / OR / NOT
[ ] 括号
[ ] Lexer
[ ] AST
[ ] Semantic
[ ] Catalog
[ ] Logical Plan
[ ] Page
[ ] Buffer
[ ] LRU / FIFO
[ ] Storage Engine
[ ] Execution Engine
[ ] Persistence
```

## Quality

```text
[ ] 错误定位
[ ] 正常测试
[ ] 错误测试
[ ] 边界测试
[ ] 集成测试
[ ] 大量数据测试
[ ] 重启测试
```

## Advanced

```text
[ ] 2+ 优化规则
[ ] 优化前后 Plan 对比
[ ] Fuzz Testing
[ ] Plan Visualization
```

## Extension

```text
[ ] UPDATE
[ ] ORDER BY
[ ] GROUP BY
[ ] JOIN
[ ] Index
[ ] EXPLAIN
[ ] Cost Model
```

---

# 29. 项目最终交付物

课程材料建议最终交付内容不应只是源码，而应升级为一个“可验证的软件模块”。

建议最终仓库至少包含：

```text
源码
README
构建与运行说明
grammar.md
AST 设计
Catalog 设计
Plan 设计
Storage 设计
测试用例
测试结果
失败案例分析
Token 输出
AST 输出
Semantic 输出
Plan 输出
Optimized Plan 输出
端到端演示 SQL
实训报告
AI 辅助使用说明
```

---

# 30. 一条最终演示主线

建议答辩固定使用一条端到端 SQL 主线：

```sql
CREATE TABLE student(
    id INT,
    name VARCHAR,
    age INT
);

INSERT INTO student(id,name,age)
VALUES (1,'Alice',20);

INSERT INTO student(id,name,age)
VALUES (2,'Bob',17);

SELECT name
FROM student
WHERE age > 18;

DELETE FROM student
WHERE id = 1;

SELECT name
FROM student
WHERE age > 18;
```

展示：

```text
SQL
 ↓
Token Stream
 ↓
AST
 ↓
Semantic
 ↓
Logical Plan
 ↓
Optimized Plan
 ↓
Execution
 ↓
Page
 ↓
Disk
```

然后：

```text
关闭程序
 ↓
重新启动
 ↓
查询数据
 ↓
验证持久化
```

这条主线可以把课程三个部分：

```text
编译原理
+
操作系统
+
数据库
```

完整串起来，也是最符合实训核心目标的演示方式。

---

# 31. 最终项目目标建议

对于三人团队，建议把目标定成：

```text
第一目标：
100% 完成 Core

第二目标：
完成 Buffer + Persistence + Integration Test

第三目标：
完成 2~3 个优化规则

第四目标：
补齐错误诊断 + Fuzz / Boundary

第五目标：
选择 1~2 个高级扩展深入
```

不要把目标设为：

```text
“做得越多越好”
```

而应设为：

```text
“核心功能完整
+
架构清晰
+
模块解耦
+
测试充分
+
能解释
+
能现场修改
”
```

这样更符合 2026 实训 PPT 所强调的验收方式：教师关注的不只是“程序跑起来”，而是学生是否真正理解、验证、修改并驾驭整个系统。

---

# 附录 A：建议的最小接口集合

```python
# lexer
tokenize(sql) -> list[Token]

# parser
parse(tokens) -> AST

# semantic
analyze(ast, catalog) -> SemanticResult

# planner
build_plan(ast, semantic_result) -> LogicalPlan

# optimizer
optimize(plan) -> LogicalPlan

# catalog
create_table(name, columns)
find_table(name)
find_column(table, column)
get_type(table, column)

# page
allocate_page()
free_page(page_id)
read_page(page_id)
write_page(page_id, data)

# buffer
get_page(page_id)
flush_page(page_id)

# executor
execute(plan) -> Result

# storage engine
insert(table, row)
scan(table)
delete(table, predicate)
```

---

# 附录 B：推荐提交前的“一键回归”

```text
1. 删除测试数据库目录
2. 启动 DBMS
3. CREATE TABLE
4. INSERT 多条记录
5. SELECT 正常查询
6. SELECT 条件查询
7. DELETE
8. 再 SELECT
9. 输出 Page / Buffer 日志
10. 退出程序
11. 重新启动
12. 再次 SELECT
13. 执行错误 SQL
14. 执行边界 SQL
15. 输出全部测试统计
```

最终结果应形成：

```text
PASS
PASS
PASS
...
```

并能展示：

```text
Test Passed: xx / xx
Crash: 0
Wrong Accept: 0
Wrong Reject: 0
Persistence: PASS
```

---

# 附录 C：项目完成度分层总表

| 层级 | 核心能力 | 典型完成状态 | 建议目标 |
|---|---|---|---:|
| Level 1 基础 | Compiler Core + Page Core + Engine Core | 能完成最小 SQL→执行闭环 | 60 左右 |
| Level 2 良好 | Buffer + Persistence + Integration + 完整测试 | 成为完整 DB 原型 | 70～80 |
| Level 3 进阶 | Optimization + Fuzz + 强错误诊断 + Plan 可视化 | 有明显工程区分度 | 80～90 |
| Level 4 冲刺 | JOIN / Index / EXPLAIN / Cost Model 等精选扩展 | 高级系统能力 | 90～95+ |

---

# 文档依据

- 《大型平台软件设计实习》指导书：明确了实训总体目标、SQL 编译器、页式存储、数据库系统、测试与报告要求。
- 《大型平台软件设计实习 SQL 编译器设计与实现》PPT：明确了 Core / Advanced / Extension 任务结构、AST / Catalog / Plan、优化、Fuzz Testing、隐藏测试、现场验收方式及最终交付物。
- 《大型平台软件设计实习-2026-2》PPT：补充说明了 DBMS 三大模块、页面与缓存、执行过程、系统目录、项目结构及分阶段实现思路。