# 攻城助手

微信小程序 + Spring Boot 后端，用于军团成员战力数据查询与管理。

## 技术栈

| 层级 | 技术 | 版本 |
|------|------|------|
| JDK | Java | 17 |
| 框架 | Spring Boot | 3.5.15 |
| ORM | MyBatis Plus | 3.5.16 |
| 连接池 | Druid | 1.2.28 |
| 数据库 | MySQL | 8.x |
| API文档 | SpringDoc OpenAPI | 2.8.17 |
| 工具 | Lombok | 1.18.46 |
| JSON | Jackson | Spring Boot 管理 |

## 项目结构

```
wx/
├── backend/                         # Spring Boot 后端
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/gongcheng/assistant/
│       │   ├── AssistantApplication.java
│       │   ├── config/              # MyBatis Plus、Druid、SpringDoc、全局异常
│       │   ├── controller/          # REST 接口
│       │   ├── dto/                 # 请求/响应对象
│       │   ├── entity/              # MyBatis Plus 实体
│       │   ├── mapper/              # MyBatis Plus Mapper
│       │   └── service/             # 业务逻辑（含外部接口拉取解析）
│       └── resources/
│           ├── application.properties
│           └── schema.sql           # 数据库建表脚本
└── miniprogram/                     # 微信小程序前端
    ├── app.js / app.json / app.wxss
    ├── project.config.json
    ├── pages/
    │   ├── index/                   # 首页：创建/加入军团
    │   ├── legion/                  # 军团主页：成员列表
    │   ├── member-add/              # 添加成员
    │   └── member-detail/           # 成员详情
    └── utils/
        └── api.js                   # 接口请求封装
```

## 功能说明

### 军团系统
- 首次使用需创建军团（设置名称和口令）或通过口令加入已有军团
- 军团信息存储在小程序本地，退出后需重新输入口令加入
- 只有加入军团后才能添加和管理成员

### 成员管理
- 添加成员：输入角色数据接口链接，后端自动调用接口并解析关键字段
- 成员列表：展示角色名、进度、攻击力、枪械伤害加成、暴击伤害加成
- 成员详情：查看完整信息、修改接口链接、刷新数据、删除成员
- 同一军团内接口链接不可重复

### 数据解析
后端调用用户输入的接口，从返回 JSON 的 `data.t5_data` 节点提取：
- `role_name` — 角色名
- `progress` — 进度
- `atk` — 攻击力
- `gun_dmg_bonus` — 枪械伤害加成
- `crit_dmg_bonus` — 暴击伤害加成

## 环境准备

### JDK 17
确保 JDK 17 已安装并配置环境变量。

### MySQL 8
数据库账号：`root` / `123456`（如不同请修改 `application.properties`）

如果使用 Docker 启动 MySQL：
```bash
docker run -d --name mysql -p 3306:3306 -e MYSQL_ROOT_PASSWORD=123456 mysql:8
```

### 创建数据库和表
```bash
# 方式一：执行 schema.sql
docker exec -i mysql mysql -u root -p123456 < backend/src/main/resources/schema.sql

# 方式二：手动执行
docker exec mysql mysql -u root -p123456 -e "CREATE DATABASE IF NOT EXISTS gongcheng DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
```

## 后端启动

```bash
cd backend
mvn spring-boot:run
```

或打包后运行：
```bash
cd backend
mvn package -DskipTests
java -jar target/assistant-1.0.0.jar
```

服务默认运行在 `http://localhost:8080`。

### 管理面板
- **Swagger API 文档**：http://localhost:8080/swagger-ui.html
- **Druid 监控**：http://localhost:8080/druid（账号 admin / 密码 admin123）

## 小程序运行

1. 打开微信开发者工具
2. 导入项目，目录选择 `miniprogram/`
3. AppID 可使用测试号（project.config.json 中默认为 touristappid）
4. 在 `miniprogram/app.js` 中修改 `baseUrl` 为后端服务地址
   - 开发者工具模拟器：`http://localhost:8080`
   - 真机调试：使用电脑局域网 IP，如 `http://192.168.x.x:8080`
5. 开发阶段在开发者工具中勾选「不校验合法域名」

## API 接口

### 军团

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/legion/create` | 创建军团 `{name, code}` |
| POST | `/api/legion/join` | 加入军团 `{code}` |
| GET | `/api/legion/{id}` | 查询军团信息 |

### 成员

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/member` | 添加成员 `{legionId, apiUrl}` |
| GET | `/api/member/list?legionId=xxx` | 成员列表 |
| GET | `/api/member/{id}` | 成员详情 |
| PUT | `/api/member/{id}` | 修改接口链接 `{apiUrl}` |
| POST | `/api/member/{id}/refresh` | 刷新数据 |
| DELETE | `/api/member/{id}` | 删除成员 |

## 注意事项

- 小程序正式发布时，需在微信公众平台配置后端域名的 `request合法域名`，且必须为 HTTPS
- 外部数据接口由后端代理调用，无跨域问题
- 后端调用外部接口超时设置：连接 10 秒，读取 30 秒
- 生产环境建议关闭 Swagger 和 Druid 监控面板
