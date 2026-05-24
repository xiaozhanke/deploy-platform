# 管理端部署工具前端

## 项目简介

这是一个基于 Vue 3 的管理端部署工具前端项目，提供了现代化的用户界面和丰富的功能特性，用于管理和部署应用程序。

## 技术栈

| 组件               | 描述                                        | 版本                        |
| ------------------ | ------------------------------------------- | --------------------------- |
| **Node.js**        | JavaScript 运行时环境                       | >= 20.19 (推荐使用 LTS 版本) |
| **Vue**            | 前端框架，支持响应式数据绑定与组件化开发    | 3.5.13                      |
| **Vite**           | 现代化前端构建工具，提供快速热重载          | 7.3.3                       |
| **Vue Router**     | Vue.js 官方路由管理，支持动态路由和导航守卫 | 4.5.0                       |
| **Pinia**          | Vue.js 官方状态管理库，轻量、易用           | 3.0.1                       |
| **Element Plus**   | 基于 Vue 3 的 UI 组件库，提供丰富的界面组件 | 2.9.6                       |
| **sass**           | CSS 预处理器，支持变量、嵌套等高级特性      | 1.86.0                      |
| **Axios**          | HTTP 客户端，支持请求封装与拦截器           | 1.8.4                       |
| **dayjs**          | 轻量级日期处理库                            | 1.11.13                     |
| **@stomp/stompjs** | STOMP 协议客户端库，用于 WebSocket 通信     | 7.1.1                       |
| **codemirror**     | 代码编辑器，支持语法高亮和扩展插件          | 6.0.1                       |
| **@xterm/xterm**   | 终端模拟器，支持与服务器的实时交互          | 5.5.0                       |

## 开发环境要求

- Node.js >= 20.19
- npm >= 8.0.0
- 现代浏览器（推荐 Chrome 最新版）

## 开发工具推荐

- [VSCode](https://code.visualstudio.com/)
- [Vue - Official](https://marketplace.visualstudio.com/items?itemName=Vue.volar) (并禁用 Vetur)
- [ESLint](https://marketplace.visualstudio.com/items?itemName=dbaeumer.vscode-eslint)
- [Prettier](https://marketplace.visualstudio.com/items?itemName=esbenp.prettier-vscode)
- [EditorConfig](https://marketplace.visualstudio.com/items/?itemName=EditorConfig.EditorConfig)

## 项目设置

### 安装依赖

```bash
npm install
```

> 如果安装缓慢或失败，请先设置 npm 镜像，再执行 `npm install`
>
> ```bash
> npm config set registry https://registry.npmmirror.com
> ```
>

### 开发环境运行

```bash
npm run dev
```

### 生产环境构建

```bash
npm run build
```

构建产物将输出到 `/dist` 目录

### 代码质量检查

```bash
# 运行 ESLint 检查
npm run lint

# 使用 Prettier 格式化代码
npm run format
```

## 项目结构

```
deploy-web/
├── src/               # 源代码目录
│   ├── api/           # API 接口定义和请求封装
│   ├── assets/        # 静态资源
│   ├── components/    # 公共组件
│   ├── layouts/       # 布局组件
│   ├── router/        # 路由配置
│   ├── store/         # 状态管理
│   ├── services/       # 公共服务类
│   ├── styles/        # 全局样式文件
│   ├── types/         # TypeScript 类型定义
│   ├── utils/         # 工具函数
│   │   └── formatter/ # 格式化工具函数
│   ├── views/         # 页面视图
│   ├── App.vue        # 根组件
│   └── main.ts        # 应用入口文件
├── public/            # 公共资源
└── package.json       # 项目配置
```

## 注意事项

1. TypeScript 对 `.vue` 导入的类型支持：

   - 使用 `vue-tsc` 替换 `tsc` CLI 进行类型检查
   - 在编辑器中安装 [Vue - Official](https://marketplace.visualstudio.com/items?itemName=Vue.volar) 插件
   - 确保禁用 `Vetur` 插件以避免冲突

2. 开发规范：

   - 遵循 ESLint 和 Prettier 的代码规范
   - 使用 TypeScript 进行开发
   - 组件命名规范：

     - 组件文件使用 PascalCase（大驼峰）命名，如：`UserCard.vue`
     - 格式化工具函数文件使用 camelCase（小驼峰）命名，如：`formatDateTime.ts`
     - 在 `<template>` 中使用组件时，使用 kebab-case（小写加短横线），如：

       ```vue
       <!-- 正确示例 -->
       <template>
         <user-card :user="userData" />
         <system-status :status="systemStatus" />
       </template>

       <!-- 错误示例 -->
       <template>
         <!-- 不推荐，但语法没问题，可以使用 -->
         <UserCard :user="userData" />
         <!-- 不推荐，但语法没问题，可以使用 -->
         <SystemStatus :status="systemStatus" />
       </template>
       ```
