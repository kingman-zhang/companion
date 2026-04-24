# CLAUDE.md — 项目架构规范

本文档描述本项目的架构模式、技术选型与编码规范，适用于基于此框架搭建的任何新项目。新项目启动时，根据实际情况替换占位符（如 `{project-name}`、`com.{group}`）。

---

## 模块结构约定

推荐的多模块 Maven 项目布局：

```
{project-name}/
├── {project}-framework/     # 自定义 Spring Boot Starter（横切关注点：日志、安全、幂等等）
├── {project}-component/     # 共享组件（biz-commons、db-base、公共工具）
├── {project}-module/        # 业务领域模块（按域拆分子模块）
│   ├── {project}-module-user/
│   ├── {project}-module-{domain}/
│   └── ...
├── {project}-api/           # REST API 入口（Controller + 全局配置）
├── {project}-consumer/      # 消息队列消费者
└── {project}-admin/         # 管理后台（可选）
```

**原则**：
- `framework` 只放与业务无关的基础设施代码
- `component` 放可跨域复用的业务公共代码
- `module` 内每个子模块只依赖 `component`，模块间禁止横向依赖
- `api` / `consumer` 是最终可运行的应用，依赖所需的 `module`

---

## 技术栈

| 技术 | 版本 | 用途 |
|------|------|------|
| Spring Boot | 3.1.x | 核心框架 |
| Spring Cloud Alibaba | 2023.0.x | NACOS 配置中心 |
| Java | 17 | 语言版本 |
| MongoDB | Spring Data | 主数据库（NoSQL） |
| Redis / Redisson | 3.x | 分布式缓存与锁 |
| RocketMQ | 2.x | 异步消息队列 |
| MapStruct | 1.5.x | Entity ↔ DTO 映射 |
| Lombok | 1.18.x | 样板代码消除 |
| Hutool | 5.8.x | 工具库（JWT、加解密等） |
| JJWT | 0.12.x | JWT Token |
| XXL-Job | 3.x | 分布式定时任务 |
| SkyWalking | 9.x | APM 链路追踪 |

具体版本以新项目 `pom.xml` 中 `<properties>` 为准。

---

## 架构分层

每个业务域遵循统一的五层结构：

```
Controller → Service Interface → ServiceImpl → Repository/Mapper → Entity
                                                      ↕
                                               DTO / Req / Resp
```

| 层 | 职责 | 规范 |
|----|------|------|
| **Controller** | 接收 HTTP 请求、参数校验、返回响应 | 不含业务逻辑，只调用 Service；返回 `IResult<T>` |
| **Service Interface** | 定义业务契约 | 每个 Service 必须有独立接口 |
| **ServiceImpl** | 实现业务逻辑 | 继承 `BaseServiceImpl`，后缀 `ServiceImpl` |
| **Repository/Mapper** | 数据访问 | 基于 Spring Data MongoDB |
| **Entity** | 数据库文档对象 | 继承 `AbstractBaseEntity`，加 `@Document` |
| **DTO** | 服务间数据传输 | 后缀 `Dto` |
| **Req / Resp** | API 入参 / 出参 | 后缀 `Req` / `Resp` |

---

## 命名规范

| 类型 | 规范 | 示例 |
|------|------|------|
| 包名 | `com.{group}.{module}.{domain}.{layer}` | `com.example.module.user.service` |
| Entity | 无特殊后缀，名词或名词短语 | `User`, `UserFollow` |
| DTO | `Dto` 后缀 | `UserDto`, `UserFollowDto` |
| 请求体 | `Req` 后缀 | `CreateUserReq`, `MakeFollowReq` |
| 响应体 | `Resp` 后缀 | `UserDetailResp` |
| Service 接口 | 无特殊后缀 | `UserService` |
| Service 实现 | `ServiceImpl` 后缀 | `UserServiceImpl` |
| Mapper | `EntityMapper<DTO, Entity>` | `UserMapper` |
| Consumer | `Consumer` 后缀 | `OrderPaidConsumer` |
| Event | `Event` 后缀 | `UserRegisteredEvent` |

---

## 通用基类

### AbstractBaseEntity — Entity 基类

所有 MongoDB Entity 必须继承此类：

```java
public abstract class AbstractBaseEntity {
    @Id
    private String id;              // 主键（Snowflake 或 MongoDB ObjectId）
    @CreatedDate
    private LocalDateTime createTime;
    @LastModifiedDate
    private LocalDateTime modifyTime;
    @CreatedBy
    private String createUser;
    @LastModifiedBy
    private String modifyUser;
    private Boolean deleted = false; // 软删除标志，禁止物理删除
    private String packageNo;        // 租户隔离字段（多租户场景使用）
}
```

### BaseService — 服务基类

```java
// 泛型 CRUD 接口
public interface BaseService<D, TQuery> {
    D findById(String id);
    IPage<D> page(TQuery query);
    D save(D dto);
    D update(D dto);
    void deleteById(String id); // 实现为软删除
}

// MongoDB 通用实现（各 ServiceImpl 继承此类）
public abstract class BaseServiceImpl<DTO, Entity> implements BaseService<...> { ... }
```

### EntityMapper — DTO 映射接口（MapStruct）

```java
// 定义映射接口
public interface EntityMapper<D, E> {
    D toDto(E entity);
    E toEntity(D dto);
    List<D> toDto(List<E> entityList);
    List<E> toEntity(List<D> dtoList);
}

// 具体 Mapper 示例
@Mapper(componentModel = "spring")
public interface UserMapper extends EntityMapper<UserDto, User> {
    // 特殊字段映射在此处用 @Mapping 声明
}
```

---

## 统一响应格式

所有 Controller 方法必须返回 `IResult<T>`：

```java
// 成功
return IResult.success(data);

// 无数据成功
return IResult.success();

// 业务失败（带错误码）
return IResult.fail(CodeEnum.USER_NOT_FOUND);

// 在 Service 层抛出异常（Controller 层不需要 try-catch）
throw new ApiException(CodeEnum.USER_NOT_FOUND);
```

响应结构：

```json
{
  "code": 200,
  "data": { },
  "message": "success",
  "timestamp": 1700000000000
}
```

---

## 异常体系

```
ApiException                → 前端业务异常（含 i18n 错误码，HTTP 200 返回）
AdminException              → 管理后台异常
UserUnauthorizedException   → 认证失败（HTTP 401）
BusinessThrowableException  → 通用业务异常
AccessDeniedException       → 权限不足
SignVerifyException         → 签名校验失败
```

- 错误码统一定义在 `CodeEnum`，支持 i18n 多语言
- 全局异常由 `GlobalExceptionHandler`（`@RestControllerAdvice`）统一处理
- Service 层只抛异常，不在 Service 内捕获后吞掉

---

## 自定义注解

| 注解 | 作用 |
|------|------|
| `@IdempotentMethod(key, expireTime)` | 基于 Redisson 的分布式幂等锁，防重复提交 |
| `@SkipCheckLoginAuth` | 标记公开接口，跳过登录鉴权拦截器 |
| `@CallbackMethod` | 标记第三方回调方法，跳过签名/登录拦截 |
| `@LogicDelete` | 标记逻辑删除字段（框架自动处理查询过滤） |

---

## 安全与鉴权

- 基于 JWT Token，登录后 Token 存入请求 Header
- `AuthContext`（ThreadLocal）在拦截器中解析 Token 并存储登录用户
- 获取当前用户：`SecurityUtils.getCurrentUser()`
- 获取请求元数据（IP、设备 ID）：`RequestUtils.getXxx()`
- 公开接口加 `@SkipCheckLoginAuth`，第三方回调加 `@CallbackMethod`

---

## 分布式特性

### 分布式 ID

```java
// Snowflake 算法生成全局唯一 ID
String id = DistributeID.generate();
```

### 分布式锁（幂等控制）

```java
// key 支持 Spring EL 表达式，expireTime 单位：秒
@IdempotentMethod(key = "#userId + ':createOrder'", expireTime = 5)
public void createOrder(String userId) {
    // 相同 key 在 expireTime 内只允许一次执行
}
```

### 分布式定时任务（XXL-Job）

```java
@XxlJob("syncUserDataHandler")
public void syncUserData() {
    // 任务逻辑
}
```

在 XXL-Job 控制台注册并配置 cron 表达式，不在代码中硬编码调度时间。

---

## 消息队列（RocketMQ）

### 发送消息

```java
@Autowired
private RocketMqSender rocketMqSender;

// 普通消息
rocketMqSender.send(topic, tag, messageBody);

// 延迟消息（如订单超时）
rocketMqSender.sendDelay(topic, tag, messageBody, delayLevel);
```

### 消费消息

```java
@Component
@RocketMQMessageListener(
    topic = "${rocketmq.consumer.topic.order-paid}",
    consumerGroup = "${rocketmq.consumer.group.order-paid}"
)
public class OrderPaidConsumer implements JsonTypeRocketMQListener<OrderPaidMessage> {
    @Override
    public void onMessage(OrderPaidMessage message) {
        // 消费逻辑，注意幂等处理
    }
}
```

Topic/Group 名称通过配置注入，不硬编码在代码中。

---

## 事件驱动（Spring Events）

用于同进程内的异步副作用（发通知、写审计日志等），避免主流程与副作用耦合：

```java
// 发布事件（在 Service 中）
applicationEventPublisher.publishEvent(new UserRegisteredEvent(this, userId));

// 监听事件（异步执行，不影响主流程）
@EventListener
@Async
public void onUserRegistered(UserRegisteredEvent event) {
    // 发送欢迎消息、初始化用户数据等
}
```

---

## 数据库约定

- **主库**：MongoDB，`@Document` 声明集合名
- **软删除**：所有删除操作设置 `deleted=true`，禁止物理删除；查询框架自动过滤已删除记录
- **索引**：`auto-index-creation=false`，索引在部署脚本中手动创建，不依赖启动自动创建
- **审计字段**：`createTime`、`modifyTime`、`createUser`、`modifyUser` 由 Spring Data Auditing 自动填充，业务代码不手动赋值
- **租户隔离**：多租户场景通过 `packageNo` 字段区分，框架层自动注入当前租户值
- **分布式 ID**：主键统一使用 `DistributeID.generate()` 生成，不使用 MongoDB 自动 ObjectId

---

## 配置管理

配置优先级（从低到高）：

1. NACOS：`default.yaml`（全局默认配置）
2. Classpath：`/config/*/`（本地分目录配置）
3. Classpath：`application-env.yml`（本地环境配置）
4. NACOS：`db.yaml`（数据库连接串）
5. NACOS：`sensitive.yaml`（密钥、第三方 AppSecret 等敏感配置）
6. NACOS：`{app-name}.yaml`（当前应用专属配置）

**原则**：
- 敏感信息（密码、密钥）只放 NACOS `sensitive.yaml`，不提交到代码仓库
- 需要动态刷新的 Bean 加 `@RefreshScope`
- 本地开发若无 NACOS，在 `bootstrap.yml` 中禁用 NACOS：`spring.cloud.nacos.config.enabled=false`

---

## 构建与运行

```bash
# 构建所有模块（跳过测试）
mvn clean install -DskipTests

# 构建指定应用模块及其依赖
mvn clean install -pl {project}-api -am -DskipTests

# 运行（指定环境）
java -jar {project}-api/target/{project}-api.jar --spring.profiles.active=dev
```

---

## 代码风格强制要求

1. **Lombok 优先**：Entity/DTO/VO 使用 `@Data`；Service 实现类使用 `@Slf4j` + `@RequiredArgsConstructor`
2. **接口与实现分离**：Service 必须先定义接口，注入时注入接口类型，不注入具体实现类
3. **统一响应**：Controller 返回类型必须是 `IResult<T>`，不直接暴露 Entity 或 DTO
4. **异常上抛**：Service 层抛异常，不吞异常；Controller 层不写 try-catch（交给全局处理器）
5. **禁止物理删除**：所有删除逻辑通过软删除实现
6. **MapStruct 映射**：Entity ↔ DTO 统一用 MapStruct，禁止手写 `setter` 逐字段赋值
7. **日志安全**：使用 `@Slf4j`，密码、Token、手机号等敏感字段脱敏后再打印
8. **事务谨慎**：MongoDB 事务性能差，优先通过补偿/幂等机制保证最终一致性，非必要不开事务
9. **常量统一**：魔法字符串/数字提取为常量类，禁止散落在业务代码中
10. **消费幂等**：所有 MQ 消费者必须实现幂等，防止重复消费导致数据异常
