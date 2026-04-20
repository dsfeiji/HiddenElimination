# HiddenElimination

一个基于 Paper 的多人生存对抗小游戏插件。  
核心玩法是“隐藏淘汰规则 + 全员任务 + 积分系统 + 道具/套装兑换”。

## 环境要求

- Java 21
- Maven 3.9+
- Paper `1.21.4`（`paper-api 1.21.4-R0.1-SNAPSHOT`）

## 构建

```bash
mvn -DskipTests package
```

构建产物在 `target/hidden-elimination-1.0.0-SNAPSHOT.jar`。

## 安装

1. 将 JAR 放入服务器 `plugins/` 目录  
2. 启动服务器生成配置  
3. 根据需要修改 `plugins/HiddenElimination/config.yml`  
4. 重启或重载插件

## 主要功能

- 隐藏淘汰规则分配与周期公开  
- 全员任务系统（限时、结算、生命与积分）  
- 首个完成任务玩家可获得规则类道具使用权  
- 指南针菜单兑换系统：
  - 规则道具（二级页）：规则试探 / 规则屏蔽 / 误导广播
  - 积分兑换套装（多等级）
  - 矿物兑换积分（支持单类兑换与一键全部兑换）
- 按“生存排名 + 任务积分”综合决定胜者，并在屏幕中央展示获胜信息

## 命令

- `/he join` 加入房间
- `/he leave` 离开房间
- `/he ready` 准备
- `/he unready` 取消准备
- `/he status` 查看状态
- `/he start` 开始游戏（管理员）
- `/he stop` 结束游戏（管理员）
- `/he setlobby` 设置大厅（管理员）

## 关键配置

- `tasks.*`：任务发布间隔、限时、积分、生命
- `powerups.*`：规则道具、套装价格、矿物积分兑换比例
- `result-scoring.*`：结算权重
- `messages.*`：提示文本
- `world-border.*`：边界缩圈
