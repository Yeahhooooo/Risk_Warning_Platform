# 风险合规预警系统

## 系统概述

风险合规预警系统是一个基于Spring Cloud微服务架构的企业合规风险评估平台，支持智能化的行为分析、指标计算和风险预警。

## 技术架构

### 技术栈
- **前端**: Vue.js
- **后端**: Spring Cloud 2021.0.8 + Spring Boot 2.7.18
- **注册中心**: Nacos
- **API网关**: Spring Cloud Gateway
- **数据库**: PostgreSQL (关系型)
- **搜索引擎**: Elasticsearch 7.17.15
- **向量数据库**: Milvus
- **缓存**: Redis
- **消息队列**: Kafka
- **容器化**: Docker
- **AI算法**: BERT (文本向量化)

### 微服务架构

系统采用6个微服务构成：

| 服务名称                      | 端口   | 职责           |
|---------------------------|------|--------------|
| risk-warning-gateway      | 8088 | API网关，统一入口   |
| risk-warning-org          | 8095 | 用户、企业、项目管理   |
| risk-warning-processing   | 8084 | 信息处理，风险分析    |
| risk-warning-knowledge    | 8089 | 知识库管理(指标/法规) |
| risk-warning-report       | 8093 | 评估报告生成       |
| risk-warning-notification | 8091 | 通知和消息推送      |

## 快速启动

### 环境要求

- Java 8
- Maven 3.8+
- Docker 20.10+
- Docker Compose 2.0+

## 项目结构

```
Risk_Warning_Platform/
├── docker-compose.yml              # Docker基础设施编排
├── pom.xml                         # 父级Maven配置
├── start.sh                        # 系统启动脚本
├── stop.sh                         # 系统停止脚本
├── README.md                       # 项目说明文档
├── schema.sql                      # 数据库初始化脚本
├── es_mappings.json               # Elasticsearch索引映射
│
├── risk-warning-common/           # 公共模块
│   ├── src/main/java/
│   │   └── com/riskwarning/common/
│   │       ├── enums/             # 枚举类
│   │       ├── exception/         # 异常处理
│   │       └── result/            # 统一返回结果
│   └── pom.xml
│
├── risk-warning-gateway/          # API网关服务
├── risk-warning-registry/         # 服务注册中心
├── risk-warning-user/             # 用户认证服务
├── risk-warning-enterprise/       # 企业管理服务
├── risk-warning-project/          # 项目管理服务
├── risk-warning-data-collection/  # 数据收集服务
├── risk-warning-behavior-processing/ # 行为处理服务
├── risk-warning-knowledge/        # 知识库服务
├── risk-warning-matching-calculation/ # 匹配计算服务
├── risk-warning-risk-assessment/  # 风险评估服务
├── risk-warning-report/           # 报告服务
├── risk-warning-event/            # 事件服务
├── risk-warning-visualization/    # 可视化服务
└── risk-warning-notification/     # 通知服务
```

## 核心功能模块

### 1. 用户与权限管理
- 用户注册、登录、权限控制
- 企业级权限（admin/member）
- 项目级权限（project_admin/editor/viewer）
- JWT token认证

### 2. 企业信息管理
- 企业档案管理和认证
- 统一社会信用代码验证
- 企业成员和角色管理
- 行业和地域配置

### 3. 项目管理
- 项目创建和配置
- 项目生命周期管理
- 团队协作和权限分配
- 进度跟踪

### 4. 行为信息处理
- 文档上传和解析（PDF/Word/Excel）
- 行为信息标准化
- BERT向量化处理
- Elasticsearch索引存储

### 5. 智能匹配与计算
- AI语义匹配（行为-指标-法规）
- 动态指标计算引擎
- 多种计算规则支持
- 结果缓存和追溯

### 6. 风险评估与预警
- 风险规则引擎
- 实时风险触发
- 多级风险等级
- 预警消息推送

### 7. 评估报告
- 多格式报告生成（PDF/Word/Excel）
- 数据可视化图表
- 报告分享和权限控制

## API文档

启动系统后，可以通过以下方式访问API文档：

- Swagger UI: http://localhost:8080/doc.html
- OpenAPI JSON: http://localhost:8080/v3/api-docs

## 监控和日志

### 服务监控
- Spring Boot Actuator健康检查
- 访问地址: http://localhost:[端口]/actuator/health

### 日志文件
```bash
# 查看服务日志
tail -f logs/risk-warning-gateway.log
tail -f logs/risk-warning-user.log
# ... 其他服务日志
```

### Nacos服务监控
- 服务列表: http://localhost:8848/nacos/#/serviceManagement
- 配置管理: http://localhost:8848/nacos/#/configurationManagement

## 开发指南

### 添加新的微服务

1. **创建模块目录**
```bash
mkdir risk-warning-new-service
```

2. **创建pom.xml**（参考现有服务）

3. **创建启动类和配置文件**

4. **在根pom.xml中添加模块**
```xml
<modules>
    <module>risk-warning-new-service</module>
</modules>
```

5. **在网关配置中添加路由规则**

### 数据库操作

- PostgreSQL连接: `jdbc:postgresql://localhost:5432/risk_warning_platform`
- 用户名/密码: postgres/password
- JPA自动建表已开启

### 缓存使用

Redis数据库分配：
- Database 0: 用户服务
- Database 1: 企业服务  
- Database 2: 行为处理服务
- Database 3: 通知服务

## 部署指南

### Docker容器化部署

每个服务都包含Dockerfile，可以构建为Docker镜像：

```bash
# 构建特定服务镜像
docker build -t risk-warning-gateway:latest ./risk-warning-gateway/

# 或构建所有服务
./build-all-images.sh
```

### Kubernetes部署

可以基于Docker镜像创建Kubernetes部署文件。

### 生产环境配置

1. **修改数据库配置**
2. **配置邮件服务**  
3. **设置Redis密码**
4. **配置Kafka集群**
5. **启用服务认证**

## 常见问题

### Q1: 服务启动失败
**A**: 检查端口占用、数据库连接、Nacos注册中心状态

### Q2: 数据库连接失败
**A**: 确认PostgreSQL已启动，检查连接配置

### Q3: 服务注册失败  
**A**: 检查Nacos服务状态，确认网络连通性

### Q4: Elasticsearch连接失败
**A**: 确认Elasticsearch已启动，检查端口9200

## 贡献指南

1. Fork项目
2. 创建功能分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)  
5. 开启Pull Request

## 许可证

本项目采用 MIT 许可证 - 查看 [LICENSE](LICENSE) 文件了解详情。

## 联系方式

- 项目负责人: [Your Name]
- 邮箱: [your.email@example.com]
- 项目地址: [Project URL]
