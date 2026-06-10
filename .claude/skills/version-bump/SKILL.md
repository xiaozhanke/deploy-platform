---
name: version-bump
description: 同步升级整个仓库的版本号。一次性改动后端 pom.xml、application.yml 的 spring.application.version、前端 package.json 三处，保证三者始终一致。用法 /version-bump 1.3.1
disable-model-invocation: true
---

# version-bump

把仓库版本号同步升到目标版本。本仓库强制三处版本号始终保持一致（见 CLAUDE.md「版本号」段），手动改容易漏掉其中一处。

## 参数

`$ARGUMENTS` = 目标版本号，形如 `1.3.1`。为空时先询问用户目标版本，不要擅自猜测。

## 需要同步修改的三处

1. `deploy-server/pom.xml`：紧跟 `<artifactId>deploy-server</artifactId>` 之后的 `<version>X.Y.Z</version>`（约第 13 行）。
   - ⚠️ **只改项目自身版本**。不要碰 `<parent>` 里的 `<version>3.5.14</version>`（Spring Boot 版本），也不要碰 `${lombok.version}` 之类的属性版本。
2. `deploy-server/src/main/resources/application.yml`：`spring.application.version` 的值，即 `name: deploy-server` 下一行的 `version: X.Y.Z`（约第 24 行）。
3. `deploy-web/package.json`：顶层字段 `"version": "X.Y.Z"`（约第 4 行）。

## 步骤

1. 校验目标版本号形如 `\d+\.\d+\.\d+`，不合法则报错退出。
2. 读取三处当前值，确认它们当前一致；若已存在漂移，先把漂移情况如实报告给用户，再继续。
3. 用精确匹配逐处替换为目标版本（注意上面 pom.xml 的避坑点）。
4. 复核：分别 grep 三处，确认均已是新版本号。
5. 汇报「旧版本 → 新版本」以及改动的三个文件路径。
6. **不要**自动 `git commit`，除非用户在本次对话里明确要求提交。
