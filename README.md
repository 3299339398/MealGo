# 食刻到家 MealGo

食刻到家（MealGo）是一个前后端分离的外卖管理与点餐演示项目，包含管理端、用户端、订单、支付、报表、Redis 营业状态和 WebSocket 订单通知。

本仓库基于课程项目进行二次开发，改造内容和原始项目边界见 [THIRD_PARTY.md](THIRD_PARTY.md)。项目名称、品牌资源、配置方式、订单金额核算、测试和容器化启动流程已整理为可独立展示的 MealGo 项目。

## 功能与改进

- 菜品、套餐、分类、员工和订单管理
- 用户点餐、地址簿、订单状态和历史订单
- 服务端重新读取菜品/套餐价格，防止使用前端提交金额
- 营业状态统一由 Redis 管理，下单前校验营业状态
- 订单金额、打包费和订单明细快照由后端统一计算
- 微信支付/退款流程保留可扩展接口，敏感配置改为环境变量
- WebSocket 订单通知
- H2 Mapper 集成测试、Service 单元测试和 WebSocket 集成测试
- Docker Compose 一键启动 MySQL、Redis、后端和 Nginx

## 技术栈

Java 17、Spring Boot 2.7、MyBatis、MySQL 8、Redis 7、Vue 编译产物、Nginx、Docker Compose、GitHub Actions。

## 项目结构

```text
sky-common/       公共配置和工具
sky-pojo/         实体、DTO、VO
sky-server/       Spring Boot 后端
frontend/dist/    MealGo 管理端静态文件
database/init.sql MySQL 表结构和演示数据
compose.yaml      MySQL、Redis、后端、Nginx 编排
```

## 启动方式

### Docker Compose（推荐）

需要 Docker Desktop。首次启动：

```bash
copy .env.example .env
docker compose up --build
```

访问管理端：<http://localhost:8081>  
后端接口：<http://localhost:8080>  
默认管理账号：`admin / 123456`

Compose 默认使用 MySQL `3307`、Redis `6380`、后端 `8080`、前端 `8081`，可在 `.env` 中修改。首次启动会执行 `database/init.sql`；如果已有同名数据卷，需要执行 `docker compose down -v` 后再初始化。

### 本地 Java 启动

先启动 MySQL 和 Redis，并准备 `sky-server/src/main/resources/application-dev.yml`（该文件只保留在本机，不提交到 Git）。然后执行：

```bash
mvn clean package
java -jar sky-server/target/mealgo-server.jar
```

也可以在 IDE 中运行 `sky-server/src/main/java/com/sky/SkyApplication.java`。前端静态文件放到 Nginx 的站点目录，并参考 `frontend/nginx.local.conf` 配置代理。

### 只启动 Redis

本机安装 Redis 时直接执行 `redis-server`；如果使用 Docker：

```bash
docker run --name mealgo-redis -p 6380:6379 -d redis:7-alpine
```

检查状态：`redis-cli -p 6380 ping`，返回 `PONG` 即可。

## 测试与检查

```bash
mvn test
docker compose --env-file .env.example config
```

## GitHub 展示建议

提交前请确认 `.env`、本地 `application-dev.yml`、微信支付证书和 OSS 密钥没有加入仓库。推荐在 GitHub 项目描述中写明：这是基于课程基础工程完成的二次开发项目，并列出自己负责的业务校验、稳定性修复、测试和部署改造。
