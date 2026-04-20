# HiddenElimination 模块化部署与配置说明

> 说明：`LobbyManager / GameManager / RuleManager / PlayerStateManager / BroadcastManager / WorldManager / SpectatorManager` 是插件内部模块，不是 7 个独立插件。
> 你只需要部署一个 `HiddenElimination.jar`，并通过 `config.yml` 配置这些模块行为。

---

## 1. 服务端环境准备

1. 安装 Java 21
2. 使用 Paper 1.21.x（建议与插件编译 API 版本一致）
3. 服务器目录结构正常（包含 `plugins` 目录）

检查命令：

```bash
java -version
```

---

## 2. 打包与部署

### 2.1 本地打包

在项目根目录执行：

```bash
mvn clean package -DskipTests
```

生成文件：

```text
target/hidden-elimination-1.0.0-SNAPSHOT.jar
```

### 2.2 部署到服务器

将 jar 放到：

```text
<服务器根目录>/plugins/
```

然后重启服务器（不要使用 `/reload`）。

---

## 3. 前置插件说明

当前 MVP 不依赖第三方前置插件。

必需项：

1. Paper 服务端
2. Java 21

---

## 4. 配置文件位置

插件启动后配置文件位于：

```text
plugins/HiddenElimination/config.yml
```

---

## 5. 推荐配置（按你的 7 模块）

将以下内容直接作为 `config.yml`：

```yml
# =========================
# HiddenElimination 总配置
# =========================

lobby-manager:
  enabled: true
  lobby-spawn:
    world: "lobby"
    x: 0.5
    y: 80.0
    z: 0.5
    yaw: 0.0
    pitch: 0.0

  items:
    ready-item:
      material: "GREEN_DYE"
      slot: 4
      name: "§a点击准备"
      lore:
        - "§7右键切换准备/取消准备"
    start-item:
      material: "NETHER_STAR"
      slot: 8
      name: "§b开始游戏"
      lore:
        - "§7管理员右键开始"

  min-ready-players: 2
  allow-host-start: false
  auto-join-on-login: true

game-manager:
  enabled: true
  start-countdown-seconds: 5
  game-max-seconds: 1800
  end-wait-seconds: 8

  states:
    waiting: "WAITING"
    running: "RUNNING"
    ending: "ENDING"

  allow-join-when-running: false

rule-manager:
  enabled: true

  condition-pool:
    - "JUMP"
    - "OPEN_INVENTORY"
    - "EAT_FOOD"
    - "ATTACK_PLAYER"
    - "BREAK_BLOCK"
    - "PLACE_BLOCK"
    - "SPRINT"
    - "SNEAK"
    - "DROP_ITEM"
    - "OPEN_CHEST"

  reveal:
    interval-seconds: 180
    random-mode: "UNREVEALED_ONLY"
    broadcast-format: "§e条件公布: §f{player} §e的淘汰条件是 §c{condition}"

  trigger:
    only-revealed-can-eliminate: true
    ignore-cancelled-events: true

player-state-manager:
  enabled: true

  default:
    joined: true
    ready: false
    alive: true
    spectator: false
    kills: 0
    survival-time-seconds: 0

  reset-after-round:
    ready: true
    alive: true
    spectator: true
    condition: true
    kills: false
    survival-time: false

broadcast-manager:
  enabled: true
  prefix: "§6[隐藏淘汰] §r"

  announce:
    game-start: "§a游戏开始！隐藏条件已分配。"
    game-end: "§e本局结束，返回大厅。"
    winner: "§b恭喜 §f{player} §b获胜！"
    eliminated: "§c{player} 被淘汰，原因: {reason}"
    player-ready: "§a{player} 已准备。"
    player-unready: "§e{player} 取消了准备。"

  title:
    enabled: true
    winner-title: "§6胜利者"
    winner-subtitle: "§f{player}"
    fade-in: 10
    stay: 50
    fade-out: 10

  sound:
    enabled: true
    game-start: "ENTITY_ENDER_DRAGON_GROWL"
    eliminate: "ENTITY_WITHER_HURT"
    winner: "UI_TOAST_CHALLENGE_COMPLETE"

world-manager:
  enabled: true
  game-world: "he_game"

  spawn:
    spread-radius: 200
    min-distance-between-players: 16
    safe-y-offset: 1

  border:
    enabled: true
    center-x: 0.0
    center-z: 0.0
    start-size: 2000.0
    end-size: 100.0
    start-delay-seconds: 30
    shrink-duration-seconds: 1200

  reset-after-round:
    teleport-back-to-lobby: true
    restore-border: true
    clear-temp-blocks: false

spectator-manager:
  enabled: true
  game-mode: "SPECTATOR"
  teleport-to-winner-on-end: true
  lock-interaction: true
  hide-from-active-players: false
  allow-spectator-chat: true

permissions:
  admin: "hiddenelimination.admin"
  start: "hiddenelimination.start"
  stop: "hiddenelimination.stop"

debug:
  enabled: false
  print-condition-trigger: false
  print-state-change: false
```

---

## 6. 大厅世界与游戏世界准备

1. 准备两个世界：
   - 大厅世界：`lobby`
   - 游戏世界：`he_game`
2. 确保服务端能加载这两个世界。
3. 在配置中设置：
   - `lobby-manager.lobby-spawn.world: lobby`
   - `world-manager.game-world: he_game`
4. 重启服务器。

---

## 7. 测试一局流程

1. 玩家进服，右键绿染料准备。
2. 管理员右键下界之星开始。
3. 检查玩家是否分散到游戏世界。
4. 等待 180 秒看条件公布。
5. 已公布条件的玩家触发行为后是否淘汰。
6. PvP 击杀是否淘汰。
7. 剩余 1 人时是否宣布胜利。
8. 是否全员回大厅并重置准备状态。

---

## 8. 常见报错与排查

### 8.1 命令不存在 `/he`

原因：插件未启用或 `plugin.yml` 命令定义异常。

排查：

1. 看启动日志是否有插件 enabled
2. 检查 jar 是否在 `plugins/`
3. 检查 `plugin.yml` 的 `commands.he`

### 8.2 开局失败（世界不存在）

原因：世界名错误。

排查：

1. 核对 `lobby` / `he_game` 名称
2. 确认该世界已被服务器加载

### 8.3 条件不触发

原因：条件未公布或条件池拼写错误。

排查：

1. 先确认该玩家条件已被公布
2. 检查 `condition-pool` 是否与枚举一致

### 8.4 旁观状态异常

原因：其他插件改游戏模式。

排查：

1. 暂时禁用冲突插件测试
2. 查看控制台是否有模式切换日志

---

## 9. 上线前检查清单

- [ ] Java 21 正常
- [ ] Paper 版本匹配
- [ ] jar 放置正确
- [ ] 世界名配置正确
- [ ] 缩圈参数合理
- [ ] 至少压测 3 局完整流程
- [ ] 断线重连状态正常
- [ ] 控制台无持续报错

---

## 10. 后续扩展建议

1. 增加条件权重系统（高风险条件低概率）
2. 多地图轮换
3. 多房间并发对局
4. 赛季积分与胜场统计
5. `messages.yml` 独立语言包
6. 更丰富旁观 UI（BossBar、ActionBar）

---

## 11. 备注

当前你服务端代码若尚未完全实现上述 7 模块所有配置项，建议：

1. 保留此配置结构作为目标标准
2. 在代码中逐步读取并落地对应节点
3. 不支持的字段先忽略，不影响启动