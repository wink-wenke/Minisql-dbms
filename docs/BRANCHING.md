# 分支管理策略

## 分支结构

```
main (baseline)
├── develop (开发主线)
│   ├── feature/compiler (SQL编译器)
│   ├── feature/engine (数据库引擎)
│   ├── feature/storage (存储系统)
│   └── feature/test (测试)
├── release (发布分支)
└── hotfix (紧急修复)
```

## 分支说明

### main分支
- **用途**：基线版本，稳定可运行的代码
- **保护**：禁止直接提交，只能通过PR合并
- **版本标签**：`v1.0.0-baseline`, `v1.1.0`, `v2.0.0`等

### develop分支
- **用途**：日常开发主线，集成所有功能分支
- **保护**：代码审查后才能合并
- **更新频率**：每日同步feature分支

### feature/*分支
- **命名规范**：`feature/<模块名>-<功能描述>`
- **示例**：
  - `feature/compiler-lexer` - 词法分析器
  - `feature/compiler-parser` - 语法分析器
  - `feature/engine-executor` - 执行引擎
  - `feature/storage-bufferpool` - 缓冲池管理
- **生命周期**：功能完成后合并到develop并删除

### release/*分支
- **命名规范**：`release/v<版本号>`
- **示例**：`release/v1.1.0`
- **用途**：发布前的最后测试和修复

### hotfix/*分支
- **命名规范**：`hotfix/<问题描述>`
- **用途**：紧急修复生产环境问题

## 版本号规范

采用语义化版本号：`MAJOR.MINOR.PATCH`

- **MAJOR**：重大功能更新或不兼容的API修改
- **MINOR**：向下兼容的功能性新增
- **PATCH**：向下兼容的问题修正

### 示例版本演进
```
v1.0.0-baseline  # 基线版本
v1.0.1           # 修复bug
v1.1.0           # 新增功能（如UPDATE语句）
v1.2.0           # 新增功能（如JOIN支持）
v2.0.0           # 重大重构或架构变更
```

## 工作流程

### 1. 开始新功能开发
```bash
# 确保在最新的develop分支
git checkout develop
git pull origin develop

# 创建功能分支
git checkout -b feature/compiler-lexer

# 开发完成后提交
git add .
git commit -m "feat(compiler): 实现词法分析器"

# 推送功能分支
git push origin feature/compiler-lexer
```

### 2. 合并功能分支
```bash
# 切换到develop分支
git checkout develop

# 合并功能分支
git merge --no-ff feature/compiler-lexer

# 推送develop分支
git push origin develop

# 删除功能分支
git branch -d feature/compiler-lexer
git push origin --delete feature/compiler-lexer
```

### 3. 创建发布版本
```bash
# 从develop创建release分支
git checkout -b release/v1.1.0 develop

# 进行最后的测试和修复
# ...

# 合并到main和develop
git checkout main
git merge --no-ff release/v1.1.0
git tag -a v1.1.0 -m "Release v1.1.0"

git checkout develop
git merge --no-ff release/v1.1.0

# 推送所有更改
git push origin main --tags
git push origin develop

# 删除release分支
git branch -d release/v1.1.0
```

### 4. 紧急修复
```bash
# 从main创建hotfix分支
git checkout -b hotfix/fix-buffer-overflow main

# 修复问题
# ...

# 合并到main和develop
git checkout main
git merge --no-ff hotfix/fix-buffer-overflow
git tag -a v1.0.1 -m "Hotfix v1.0.1"

git checkout develop
git merge --no-ff hotfix/fix-buffer-overflow

# 推送所有更改
git push origin main --tags
git push origin develop

# 删除hotfix分支
git branch -d hotfix/fix-buffer-overflow
```

## 提交信息规范

采用Angular提交规范：

```
<type>(<scope>): <subject>

<body>

<footer>
```

### Type类型
- **feat**: 新功能
- **fix**: 修复bug
- **docs**: 文档更新
- **style**: 代码格式（不影响功能）
- **refactor**: 重构
- **test**: 测试相关
- **chore**: 构建/工具相关

### Scope范围
- **compiler**: SQL编译器模块
- **engine**: 数据库引擎模块
- **storage**: 存储系统模块
- **test**: 测试相关
- **docs**: 文档相关

### 示例提交信息
```
feat(compiler): 实现词法分析器

- 支持SQL关键字识别
- 支持标识符和常量解析
- 支持运算符和分隔符

Closes #12
```

## 分支保护规则

### main分支
- 禁止直接push
- 必须通过PR合并
- 需要至少1人代码审查
- CI/CD必须通过

### develop分支
- 禁止直接push
- 必须通过PR合并
- 需要至少1人代码审查

## 团队分工建议

| 成员 | 主要分支 | 辅助分支 |
|------|---------|---------|
| 成员A（编译器） | feature/compiler-* | develop |
| 成员B（引擎） | feature/engine-* | develop |
| 成员C（存储） | feature/storage-* | develop |

## 冲突解决

### 预防冲突
1. 每日同步develop分支
2. 功能分支尽量小且专注
3. 及时合并已完成的功能

### 解决冲突
1. 拉取最新develop分支
2. 合并develop到功能分支
3. 解决冲突后测试
4. 提交合并结果

```bash
# 同步develop分支
git checkout develop
git pull origin develop

# 切换到功能分支
git checkout feature/compiler-lexer

# 合并develop分支
git merge develop

# 解决冲突后
git add .
git commit -m "merge: 同步develop分支解决冲突"
```
