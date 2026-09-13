# MiniSQL 数据库管理系统

基于 SimpleDB 3.4 构建的小型数据库管理系统，作为《大型平台软件设计实习》课程项目。

## 项目简介

本项目是一个完整的数据库管理系统，综合运用编译原理、操作系统、数据库三门课程知识，实现从SQL文本输入到数据持久化的完整链路。

### 核心功能

- **SQL编译器**：词法分析、语法分析、语义分析、执行计划生成
- **数据库引擎**：执行算子、存储引擎、系统目录、数据持久化
- **存储系统**：页式存储、缓冲池管理、内存与磁盘调度

### 支持的SQL语句

```sql
-- 创建表
CREATE TABLE student(id INT, name VARCHAR, age INT);

-- 插入数据
INSERT INTO student(id, name, age) VALUES (1, 'Alice', 20);

-- 查询数据
SELECT id, name FROM student WHERE age > 18 AND id != 3;

-- 删除数据
DELETE FROM student WHERE id = 1;
```

## 项目结构

```
minisql/
├── src/                    # 源代码
│   ├── simpledb/          # SimpleDB核心代码
│   ├── simpleclient/      # 客户端示例
│   └── derbyclient/       # Derby客户端示例
├── docs/                   # 文档
├── tests/                  # 测试
├── 相关文档/               # 项目文档
└── 参考材料/               # 参考资料
```

## 快速开始

### 环境要求

- Java JDK 8+
- Maven 3.6+ (可选)

### 编译运行

```bash
# 编译项目
cd src/simpledb
javac -d build -sourcepath . **/*.java

# 启动服务器
java -cp build simpledb.server.StartServer studentdb

# 运行客户端
java -cp build simpleclient.embedded.CreateStudentDB
```
java -cp build simpledb.metadata.MetadataMgrTest

### 运行测试

Git Bash / Linux / macOS：

```bash
# 编译（含测试；derbyclient 需要 Derby 依赖，未纳入）
javac -encoding UTF-8 -d build -sourcepath src \
  $(find src/simpledb src/simpleclient src/tests -name "*.java")

# 运行引擎测试套件
java -cp build tests.RunAllTests
```

Windows PowerShell：

```powershell
javac -encoding UTF-8 -d build -sourcepath src (Get-ChildItem -Recurse -Path src\simpledb,src\simpleclient,src\tests -Filter *.java).FullName

java -cp build tests.RunAllTests
```

也可以只给入口文件，让 javac 顺着 `-sourcepath` 自己找依赖：

```powershell
javac -encoding UTF-8 -d build -sourcepath src src\tests\RunAllTests.java src\tests\StorageWalkthrough.java
```

存储层走查程序会打印目录自举、字段偏移、删除标记与槽位复用，可用于答辩演示：

```powershell
java -cp build tests.StorageWalkthrough
```

测试覆盖 CREATE / INSERT / SELECT / DELETE 主流程、`>` 与 `!=` 谓词、
错误分类，以及重启后数据与系统目录不丢失。
套件会自建 `testdb/` 与 `persistdb/`（已在 .gitignore 中），
每次运行前自动清空，可重复执行。

## 开发指南

### 分工建议

| 成员 | 负责模块 | 核心产出 |
|------|---------|---------|
| 成员A | SQL编译器 | Lexer + Parser + Semantic + Plan + Optimizer |
| 成员B | 数据库引擎 | Executor + StorageEngine + Catalog |
| 成员C | 存储系统 | PageManager + BufferPool + DiskIO |

### 开发流程

1. **接口定义**：先定义三模块间的接口契约
2. **并行开发**：各模块使用Mock对象独立开发
3. **增量集成**：分阶段替换Mock，逐步集成
4. **测试验证**：单元测试 + 集成测试 + Fuzz测试

## 相关文档

- [技术文档](相关文档/技术文档.md)
- [实训得分要点](相关文档/实训得分要点.md)
- [工程化开发流程](相关文档/工程化开发流程.md)

## 致谢

- SimpleDB 3.4 by Professor Joseph M. Hellerstein
- 《数据库系统概念》教材
