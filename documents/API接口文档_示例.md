# 风险预警平台 API 接口文档

> 文档版本：v1.0  
> 更新日期：2025年12月26日  
> 项目名称：Risk_Warning_Platform

---

## 目录

- [1. 用户管理模块 (UserController)](#1-用户管理模块-usercontroller)
  - [1.1 用户注册](#11-用户注册)
  - [1.2 用户登录](#12-用户登录)
  - [1.3 获取当前用户信息](#13-获取当前用户信息)
  - [1.4 根据用户ID获取用户信息](#14-根据用户id获取用户信息)
  - [1.5 更新用户信息](#15-更新用户信息)
  - [1.6 根据邮箱查询用户信息](#16-根据邮箱查询用户信息)
- [2. 企业管理模块 (EnterpriseController)](#2-企业管理模块-enterprisecontroller)
  - [2.1 创建企业](#21-创建企业)
  - [2.2 添加企业成员](#22-添加企业成员)
  - [2.3 获取所有企业列表](#23-获取所有企业列表)
  - [2.4 获取企业成员列表](#24-获取企业成员列表)
- [3. 项目管理模块 (ProjectController)](#3-项目管理模块-projectcontroller)
  - [3.1 创建项目](#31-创建项目)
  - [3.2 添加项目成员](#32-添加项目成员)
  - [3.3 获取所有项目列表](#33-获取所有项目列表)
  - [3.4 获取项目成员列表](#34-获取项目成员列表)
- [4. 文件上传管理模块 (FileController)](#4-文件上传管理模块-filecontroller)
  - [4.1 初始化文件上传](#41-初始化文件上传)
  - [4.2 上传文件分片](#42-上传文件分片)
  - [4.3 确认文件上传完成](#43-确认文件上传完成)
  - [4.4 删除临时文件](#44-删除临时文件)
- [5. 知识库向量化管理模块 (EsVectorizationController)](#5-知识库向量化管理模块-esvectorizationcontroller)
  - [5.1 向量化指标库](#51-向量化指标库)
  - [5.2 向量化法规库](#52-向量化法规库)
  - [5.3 向量化行为库](#53-向量化行为库)
  - [5.4 批量向量化所有索引](#54-批量向量化所有索引)
- [6. 向量搜索测试模块 (VectorizationTestController)](#6-向量搜索测试模块-vectorizationtestcontroller)
  - [6.1 单文本向量化测试](#61-单文本向量化测试)
  - [6.2 批量文本向量化测试](#62-批量文本向量化测试)
  - [6.3 获取向量维度](#63-获取向量维度)
  - [6.4 搜索相似指标](#64-搜索相似指标)
  - [6.5 搜索相似法规](#65-搜索相似法规)
  - [6.6 分类匹配查询](#66-分类匹配查询)
- [7. 预警结果聚合查询模块 (ReportAggregationController)](#7-预警结果聚合查询模块-reportaggregationcontroller)
  - [7.1 获取项目预警总览](#71-获取项目预警总览)
  - [7.2 按维度聚合查询](#72-按维度聚合查询)
  - [7.3 按风险等级聚合查询](#73-按风险等级聚合查询)
  - [7.4 获取高风险指标列表](#74-获取高风险指标列表)
  - [7.5 获取风险趋势分析](#75-获取风险趋势分析)
  - [7.6 获取指标得分分布](#76-获取指标得分分布)

---

## 1. 用户管理模块 (UserController)

**模块说明**：负责用户认证、注册、信息管理等功能

**基础路径**：`/user`

---

### 1.1 用户注册

**接口名称**：用户注册

**请求路径**：`/user/register`

**请求方式**：`POST`

**接口状态**：✅ 已实现

**接口描述**：新用户注册接口，用于创建新的用户账号

**是否需要认证**：否

#### 请求参数

**Content-Type**: `application/json`

**Body 参数**：

| 参数名   | 类型   | 是否必填 | 说明                       | 示例                  | 校验规则                   |
| -------- | ------ | -------- | -------------------------- | --------------------- | -------------------------- |
| email    | String | 是       | 用户邮箱，作为登录账号     | user@example.com      | @Email, @NotBlank          |
| password | String | 是       | 用户密码                   | Password123           | @NotBlank, @Size(6-20)     |
| fullName | String | 是       | 用户真实姓名               | 张三                  | @NotBlank, @Size(max=50)   |

**请求示例**：

```json
{
  "email": "newuser@example.com",
  "password": "Password123",
  "fullName": "张三"
}
```

#### 返回参数

**成功响应** (200)：

| 参数名             | 类型   | 是否为空 | 说明             | 示例                 |
| ------------------ | ------ | -------- | ---------------- | -------------------- |
| code               | int    | 否       | 响应状态码       | 200                  |
| message            | String | 否       | 响应消息         | "success"            |
| data               | Object | 否       | 返回数据对象     | -                    |
| data.id            | Long   | 否       | 用户ID           | 1                    |
| data.email         | String | 否       | 用户邮箱         | newuser@example.com  |
| data.fullName      | String | 否       | 用户姓名         | 张三                 |
| data.avatarUrl     | String | 可       | 用户头像URL      | null                 |
| data.createdAt     | String | 否       | 创建时间         | 2025-12-26T10:00:00Z |
| data.updatedAt     | String | 否       | 更新时间         | 2025-12-26T10:00:00Z |

**成功响应示例**：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 1,
    "email": "newuser@example.com",
    "fullName": "张三",
    "avatarUrl": null,
    "createdAt": "2025-12-26T10:00:00Z",
    "updatedAt": "2025-12-26T10:00:00Z"
  }
}
```

**失败响应示例**：

```json
{
  "code": 500,
  "message": "邮箱已被注册",
  "data": null
}
```

---

### 1.2 用户登录

**接口名称**：用户登录

**请求路径**：`/user/login`

**请求方式**：`POST`

**接口状态**：✅ 已实现

**接口描述**：用户登录接口，返回访问令牌和用户信息

**是否需要认证**：否

#### 请求参数

**Content-Type**: `application/json`

**Body 参数**：

| 参数名   | 类型   | 是否必填 | 说明           | 示例             | 校验规则               |
| -------- | ------ | -------- | -------------- | ---------------- | ---------------------- |
| email    | String | 是       | 用户邮箱       | user@example.com | @Email, @NotBlank      |
| password | String | 是       | 用户密码       | Password123      | @NotBlank, @Size(6-20) |

**请求示例**：

```json
{
  "email": "user@example.com",
  "password": "Password123"
}
```

#### 返回参数

**成功响应** (200)：

| 参数名          | 类型   | 是否为空 | 说明             | 示例                 |
| --------------- | ------ | -------- | ---------------- | -------------------- |
| code            | int    | 否       | 响应状态码       | 200                  |
| message         | String | 否       | 响应消息         | "success"            |
| data            | Object | 否       | 返回数据对象     | -                    |
| data.token      | String | 否       | JWT访问令牌      | eyJhbGciOiJIUzI1N... |
| data.tokenType  | String | 否       | 令牌类型         | Bearer               |
| data.user       | Object | 否       | 用户信息对象     | -                    |
| data.user.id    | Long   | 否       | 用户ID           | 1                    |
| data.user.email | String | 否       | 用户邮箱         | user@example.com     |
| data.user.fullName | String | 否    | 用户姓名         | 张三                 |
| data.user.avatarUrl | String | 可   | 用户头像URL      | https://example.com/avatar.jpg |
| data.user.createdAt | String | 否   | 创建时间         | 2025-12-26T10:00:00Z |
| data.user.updatedAt | String | 否   | 更新时间         | 2025-12-26T10:00:00Z |

**成功响应示例**：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "tokenType": "Bearer",
    "user": {
      "id": 1,
      "email": "user@example.com",
      "fullName": "张三",
      "avatarUrl": "https://example.com/avatar.jpg",
      "createdAt": "2025-12-26T10:00:00Z",
      "updatedAt": "2025-12-26T10:00:00Z"
    }
  }
}
```

**失败响应示例**：

```json
{
  "code": 500,
  "message": "邮箱或密码错误",
  "data": null
}
```

---

### 1.3 获取当前用户信息

**接口名称**：获取当前用户信息

**请求路径**：`/user/profile`

**请求方式**：`GET`

**接口状态**：✅ 已实现

**接口描述**：获取当前登录用户的详细信息

**是否需要认证**：是（需要 @AuthRequired）

#### 请求参数

**Header 参数**：

| 参数名        | 类型   | 是否必填 | 说明          | 示例                  |
| ------------- | ------ | -------- | ------------- | --------------------- |
| Authorization | String | 是       | Bearer Token  | Bearer eyJhbGciOi...  |

**无 Body 参数**

#### 返回参数

**成功响应** (200)：

| 参数名         | 类型   | 是否为空 | 说明         | 示例                       |
| -------------- | ------ | -------- | ------------ | -------------------------- |
| code           | int    | 否       | 响应状态码   | 200                        |
| message        | String | 否       | 响应消息     | "success"                  |
| data           | Object | 否       | 用户信息对象 | -                          |
| data.id        | Long   | 否       | 用户ID       | 1                          |
| data.email     | String | 否       | 用户邮箱     | user@example.com           |
| data.fullName  | String | 否       | 用户姓名     | 张三                       |
| data.avatarUrl | String | 可       | 用户头像URL  | https://example.com/avatar.jpg |
| data.createdAt | String | 否       | 创建时间     | 2025-12-26T10:00:00Z       |
| data.updatedAt | String | 否       | 更新时间     | 2025-12-26T10:00:00Z       |

**成功响应示例**：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 1,
    "email": "user@example.com",
    "fullName": "张三",
    "avatarUrl": "https://example.com/avatar.jpg",
    "createdAt": "2025-12-26T10:00:00Z",
    "updatedAt": "2025-12-26T10:00:00Z"
  }
}
```

**失败响应示例**：

```json
{
  "code": 401,
  "message": "未授权，请先登录",
  "data": null
}
```

---

### 1.4 根据用户ID获取用户信息

**接口名称**：根据用户ID获取用户信息

**请求路径**：`/user/{userId}`

**请求方式**：`GET`

**接口状态**：✅ 已实现

**接口描述**：根据指定的用户ID查询用户信息

**是否需要认证**：否

#### 请求参数

**Path 参数**：

| 参数名 | 类型 | 是否必填 | 说明   | 示例 |
| ------ | ---- | -------- | ------ | ---- |
| userId | Long | 是       | 用户ID | 1    |

**无 Body 参数**

#### 返回参数

**成功响应** (200)：

| 参数名         | 类型   | 是否为空 | 说明         | 示例                       |
| -------------- | ------ | -------- | ------------ | -------------------------- |
| code           | int    | 否       | 响应状态码   | 200                        |
| message        | String | 否       | 响应消息     | "success"                  |
| data           | Object | 否       | 用户信息对象 | -                          |
| data.id        | Long   | 否       | 用户ID       | 1                          |
| data.email     | String | 否       | 用户邮箱     | user@example.com           |
| data.fullName  | String | 否       | 用户姓名     | 张三                       |
| data.avatarUrl | String | 可       | 用户头像URL  | https://example.com/avatar.jpg |
| data.createdAt | String | 否       | 创建时间     | 2025-12-26T10:00:00Z       |
| data.updatedAt | String | 否       | 更新时间     | 2025-12-26T10:00:00Z       |

**成功响应示例**：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 1,
    "email": "user@example.com",
    "fullName": "张三",
    "avatarUrl": "https://example.com/avatar.jpg",
    "createdAt": "2025-12-26T10:00:00Z",
    "updatedAt": "2025-12-26T10:00:00Z"
  }
}
```

**失败响应示例**：

```json
{
  "code": 500,
  "message": "用户不存在",
  "data": null
}
```

---

### 1.5 更新用户信息

**接口名称**：更新用户信息

**请求路径**：`/user/profile`

**请求方式**：`PUT`

**接口状态**：✅ 已实现

**接口描述**：更新当前登录用户的个人信息

**是否需要认证**：是（需要 @AuthRequired）

#### 请求参数

**Header 参数**：

| 参数名        | 类型   | 是否必填 | 说明         | 示例                 |
| ------------- | ------ | -------- | ------------ | -------------------- |
| Authorization | String | 是       | Bearer Token | Bearer eyJhbGciOi... |

**Content-Type**: `application/json`

**Body 参数**：

| 参数名    | 类型   | 是否必填 | 说明        | 示例                       |
| --------- | ------ | -------- | ----------- | -------------------------- |
| fullName  | String | 否       | 用户姓名    | 张三丰                     |
| avatarUrl | String | 否       | 用户头像URL | https://example.com/new_avatar.jpg |

**请求示例**：

```json
{
  "fullName": "张三丰",
  "avatarUrl": "https://example.com/new_avatar.jpg"
}
```

#### 返回参数

**成功响应** (200)：

| 参数名         | 类型   | 是否为空 | 说明         | 示例                       |
| -------------- | ------ | -------- | ------------ | -------------------------- |
| code           | int    | 否       | 响应状态码   | 200                        |
| message        | String | 否       | 响应消息     | "success"                  |
| data           | Object | 否       | 用户信息对象 | -                          |
| data.id        | Long   | 否       | 用户ID       | 1                          |
| data.email     | String | 否       | 用户邮箱     | user@example.com           |
| data.fullName  | String | 否       | 用户姓名     | 张三丰                     |
| data.avatarUrl | String | 可       | 用户头像URL  | https://example.com/new_avatar.jpg |
| data.createdAt | String | 否       | 创建时间     | 2025-12-26T10:00:00Z       |
| data.updatedAt | String | 否       | 更新时间     | 2025-12-26T12:30:00Z       |

**成功响应示例**：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 1,
    "email": "user@example.com",
    "fullName": "张三丰",
    "avatarUrl": "https://example.com/new_avatar.jpg",
    "createdAt": "2025-12-26T10:00:00Z",
    "updatedAt": "2025-12-26T12:30:00Z"
  }
}
```

**失败响应示例**：

```json
{
  "code": 401,
  "message": "未授权，请先登录",
  "data": null
}
```

---

### 1.6 根据邮箱查询用户信息

**接口名称**：根据邮箱查询用户信息

**请求路径**：`/user/email`

**请求方式**：`GET`

**接口状态**：✅ 已实现

**接口描述**：根据邮箱地址查询用户信息

**是否需要认证**：否

#### 请求参数

**Query 参数**：

| 参数名 | 类型   | 是否必填 | 说明     | 示例             |
| ------ | ------ | -------- | -------- | ---------------- |
| email  | String | 是       | 用户邮箱 | user@example.com |

**请求示例**：

```
GET /user/email?email=user@example.com
```

#### 返回参数

**成功响应** (200)：

| 参数名         | 类型   | 是否为空 | 说明         | 示例                       |
| -------------- | ------ | -------- | ------------ | -------------------------- |
| code           | int    | 否       | 响应状态码   | 200                        |
| message        | String | 否       | 响应消息     | "success"                  |
| data           | Object | 否       | 用户信息对象 | -                          |
| data.id        | Long   | 否       | 用户ID       | 1                          |
| data.email     | String | 否       | 用户邮箱     | user@example.com           |
| data.fullName  | String | 否       | 用户姓名     | 张三                       |
| data.avatarUrl | String | 可       | 用户头像URL  | https://example.com/avatar.jpg |
| data.createdAt | String | 否       | 创建时间     | 2025-12-26T10:00:00Z       |
| data.updatedAt | String | 否       | 更新时间     | 2025-12-26T10:00:00Z       |

**成功响应示例**：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 1,
    "email": "user@example.com",
    "fullName": "张三",
    "avatarUrl": "https://example.com/avatar.jpg",
    "createdAt": "2025-12-26T10:00:00Z",
    "updatedAt": "2025-12-26T10:00:00Z"
  }
}
```


### 认证说明

需要认证的接口需要在请求头中携带 JWT Token：

```
Authorization: Bearer {access_token}
```
