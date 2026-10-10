# 车万女仆：真心为你  TLM-sincerely

[![Minecraft](https://img.shields.io/badge/Minecraft-1.20.1-62B47A?style=flat-square)](https://www.minecraft.net/)
[![Forge](https://img.shields.io/badge/Forge-47.x-E04E14?style=flat-square)](https://files.minecraftforge.net/)
[![Java](https://img.shields.io/badge/Java-17-ED8B00?style=flat-square)](https://adoptium.net/)
[![License](https://img.shields.io/badge/License-MIT-blue?style=flat-square)](LICENSE)

> [!WARNING]
> AI 辅助开发声明
>
> 本项目的 `1.20.1forge` 分支（即本分支）是在原项目基础上，借助 AI 以 vibe coding 的方式添加定制功能的版本；
>
> 辅助开发使用的模型为 Claude Sonnet 5.5 和 DeepSeek V4.1 Flash；
>
> 代码经过人工逐行校对，但作者水平有限，仍然存在风险（可能有未被发现的错误、与其他模组的兼容问题等）；
>
> 如果你介意，请不要使用本分支

> 建议搭配改版的 [TLM-Self-Talk](https://github.com/BlueFunny19/TLM-Self-Talk)（`forge-1.20.1` 分支）一起使用；
>
> 女仆之间的「转告」联动（`tell_maid` 工具）需要它；
>
> 不搭配也可以正常使用本分支，只是少了这项联动；
>
> 文中标有「**改版新增**」「**改版变更**」的条目，就是本分支相对原版新增或修改的内容

《车万女仆：真心为你》是 [Touhou Little Maid / 车万女仆](https://www.curseforge.com/minecraft/mc-mods/touhou-little-maid) 的 Forge 1.20.1 附属模组，围绕女仆对话、工作调度和长期记忆提供增强功能。（1.21.1 NeoForge 版本由 `1.21.1neo` 分支维护）
目前实现了：
- 在聊天栏中与女仆agent进行对话
- 多女仆对话：`@女仆甲 @女仆乙 内容` / `@全体 内容`（**改版新增**）
- **核心功能**工作模式自动切换模式
- 女仆agent记忆系统
- 女仆agent指令系统
- 女仆agent命令执行（AI 可以主人的身份执行游戏内命令，含危险命令确认与审计日志）
- 女仆「转告」：女仆自己判断要不要把话转告给另一只女仆并请她回应（**改版新增**，需搭配改版 Self Talk）
- 女仆「移动指令」：靠近 / 远离 / 前往某处 / 守护某人 / 留在原地 / 回家 / 放宽工作时间，默认一直生效、玩家明确要求临时才限时（**改版新增**）
- 提示词按对话语言选择：中文用中文提示词，其他语言用英文并要求用该语言回复（**改版变更**）

欢迎反馈 bug 与提交 pr 修复

## 未来todo（画饼）

[] 迁移至1.21.1
[] 增加玩家视角截图功能，也可能是增加相机物品，可将截图内容发送给支持视觉的女仆agent
[] 新增物品“日记本”，绑定女仆，用于 GUI 化管理女仆的记忆
[] 新增物品“翻盖机”，绑定女仆，可唤起带输入框的聊天界面，与女仆agent沉浸式聊天

> 当前版本：`0.4.0-beta`
>
> Mod ID：`tlm_sincerely`
>
> 原作者：terk　|　定制改版：BlueFunny19（AI 辅助）

## 环境与前置

| 项目 | 要求 |
|---|---|
| Minecraft | 1.20.1 |
| Mod Loader | Forge 47.x，开发环境使用 47.4.0 |
| Java | 17 |
| Touhou Little Maid | 1.5.3 |
| Cloth Config API | 11.x，开发环境使用 11.1.136 |
| TLM Self-Talk（可选，改版新增） | 改版 ≥ 1.4.0（含 `SelfTalkApi`，建议 1.5.0）；用于「转告」联动，需要开启其「女仆互聊」总开关（默认关闭） |

## 功能

### 聊天栏女仆对话

- 在聊天栏中直接与最近的女仆对话。
- 使用 `@女仆名` 指定对话目标。
- **改版新增**：一句话同时对多只女仆说——`@颜 @澪 内容`，或 `@全体 内容`（喊话范围内全部女仆）。每只女仆各自独立回复，并且都会被告知"玩家同时对谁说话"，所以她们的模型可以自己判断要不要互相转告。单次最多 5 只（`MaxTargets`，可改 1~20），超出时只发给最近的几只并提示；喊话范围由 `TargetRange` 决定（默认 64 格，可改 1~256，`@名字` 与 `@全体` 共用）；找不到的名字用红字单独列出。可用 `MultiTargetEnabled` 关闭，关闭后退回单目标行为；`@全体` 的关键词可在 `AllKeywords` 里增删。
- **改版新增**：女仆的回复含多个段落时，在聊天栏里分成多条显示，像是连续说了几句话（`SplitReplyLines`，默认开；只影响聊天栏显示，不影响气泡和聊天记录）。
- **改版新增（默认关闭）**：回复节奏的两个独立开关（`reply` 分组）——`ReplyModelSplitEnabled` 让模型把回复拆成几条短句、每条一行；`ReplyPauseMarkerEnabled` 告诉模型可以在两句话之间写 `<等待>`，聊天栏会隔一会儿再显示标记后面的话（`ReplyPauseSeconds`，默认 1.5 秒；气泡与语音仍是一次性出现）。无论开关如何，回复里的 `<等待>` 标记都会被剥掉，不会出现在气泡、语音、聊天栏或历史里。
- **改版变更**：对话语言改用玩家的客户端语言（与主 Mod 自己的聊天界面一致）；聊天栏、`/tlmchat`、`/tlmmemory summarize` 与记忆整理四处都按这个语言处理。
- 支持按名称、模糊名称和 UUID 查找女仆。
- 支持严格前缀模式、自动对话范围和全局/私聊显示。
- 提供 `/tlmchat` 命令管理对话模式和目标。

### 多工作优先级

- 预设库保存在本机（客户端）配置目录；选预设时把整份任务顺序绑定到该女仆，之后改库不会自动改已绑定的女仆，也可以把预设推送给其他玩家。
- 为女仆建立可切换的工作优先级预设，支持纯列表顺序手动排序。
- 使用独立 Detector 检测攻击与 `IFarmTask` 工作，不通过快速切换任务进行探测。
- 主线程分帧扫描方块并验证路径可达性，能够跳过不可达工作并选择其他可用任务。
- 使用 AVAILABLE / UNAVAILABLE / UNKNOWN 缓存、确认次数和最短保持时间减少任务横跳。
- 自动切换后任务持续阻塞时，可按默认开启的配置强制刷新一次 Brain。
- 提供默认关闭的实验性攻击抢占，仅作用于绑定快照中已经配置的攻击任务。
- 骑乘时仍扫描全部绑定任务，切入非当前骑乘工作前自动下车。
- 近战攻击排除弓/弩/三叉戟/御币等远程武器；弓兵需同时持有弓与箭。

### 简易记忆系统

- 为每只女仆保存独立的 JSON 键值记忆。
- 区分核心记忆与归档记忆，并将必要内容注入 AI 上下文。
- 女仆 AI 可通过 `tlm_memory` Tool 自主记录、回忆和遗忘信息。
- 提供 `/tlmmemory` 命令进行查看、设置、删除、导出与回顾。
- 支持中文女仆名称和 `uuid:<UUID>` 精确选择。
- **改版变更**：记忆指引、`tlm_memory` 工具说明与返回文字、记忆整理与 `/tlmmemory summarize` 的提示都按对话语言提供中英两套；中文下记忆 key 改为用简短的中文词语（旧的英文 key 仍然可用）。

### 女仆移动指令（改版新增）

女仆的 AI 可以调用下面四个工具，按你的话行动：

| 工具 | 动作 | 效果 |
|---|---|---|
| `maid_move` | `approach` / `retreat` | 走近 / 远离某人（你或同一主人的另一只女仆），保持在指定距离内 / 外 |
| `maid_move` | `goto` | 前往指定坐标并在那里等着 |
| `maid_move` | `stay` | 留在原地、不再跟随主人；原有的工作坐标会被暂时清掉，结束后精确恢复 |
| `maid_move` | `go_home` | 无视距离传送回自己的工作坐标并开启居家模式（永久性改变，需要先设置工作坐标） |
| `maid_guard` | （无） | 守在某人身边，并攻击威胁他的敌对生物（需要女仆当前是战斗类任务才会主动攻击） |
| `maid_work_time` | （无） | 不移动，只把日程临时改成"全天工作"，让她夜里也能工作 |
| `maid_cancel` | （无） | 取消当前指令（被暂停的跟随、被放宽的日程都会恢复） |

- 指令**默认一直生效**，直到你让女仆取消；只有你明确要求"临时""先这样一会儿""守 3 分钟"时才会限时（没说时长默认 5 分钟 `MoveDefaultDurationSeconds`，最长默认 1 小时 `MoveMaxDurationSeconds`）。没有时限的指令会通过工具结果、模型上下文告诉女仆要明确告知你；想让她隔几分钟自己提醒一句，可以把 `MovePermanentReminderMinutes` 调成大于 0（默认关闭）。
- 移动类指令生效期间，只要女仆没在睡觉，就会让她"加班"：日程被临时改成全天工作，结束后恢复（`MoveForceWorkSchedule`）。
- 玩家让女仆切换工作任务、而她本来不在工作时间时，也会让她加班（不再回答"现在不是工作时间"）；切到待命任务，或一段时间没有新指令后，日程自动恢复（`OvertimeTaskSwitchEnabled` / `OvertimeTaskHoldSeconds`）。加班期间你手动改日程会被视为接管，不会再改回去。
- 在本该睡觉的时段里加班太久，女仆会累（`OvertimeSoftCapSeconds`，默认 180 秒）：她会走到你身边向你要点心（默认一个蛋糕多撑 300 秒，右键递给她或放进她的背包都行；她自己背包里有就直接吃）。等不到（默认 90 秒）她就会取消手头的指令回去休息，这段休息时间里不能再让她加班。点心表与各项开关见 `overtime` 分组（`OvertimeSoftCapEnabled`、`OvertimeTreatEnabled`、`OvertimeTreats`、`OvertimeEatOwnTreats`、`OvertimeTreatWaitSeconds`、`OvertimeComplainEnabled`）。她累了时说的话（抱怨、向你要点心、道谢、去睡觉）都由模型按性格生成，模型用不了时会说写死的备用台词，保证一定提醒你；`OvertimeComplainEnabled` 是疲劳的总开关，关掉她就根本不会累。
- 路走不通时：距离不太远就传送到位，太远就放弃并让女仆说一句话（`MoveTeleportFallback` / `MoveTeleportMaxDistance`）。
- 指令与暂停状态存在女仆自己的数据里，服务器重启后会继续生效或自动恢复；你在界面里手动改了模式，指令会自动终止，不会覆盖你的修改。
- 相关配置在 `move` 分组与配置界面里：`MoveEnabled`、`MoveDefaultDurationSeconds`、`MoveMaxDurationSeconds`、`MoveMaxActivePerOwner`、`MoveAllowPermanent`、`MovePermanentReminderMinutes`、`MoveForceWorkSchedule`、`MoveTeleportFallback`、`MoveTeleportMaxDistance`；加班在 `overtime` 分组：`OvertimeTaskSwitchEnabled`、`OvertimeTaskHoldSeconds`。

### 冒险日程（改版新增）

- 女仆界面里的日程按钮多了第四档"冒险"（白班 → 夜班 → 全天 → 冒险 → 白班）：几乎全天工作，没有休闲时段，只在每天一小段时间里睡觉（默认 23:00 到次日 03:00，可在 `adventure` 分组里改 `AdventureSleepStartTick` 与 `AdventureSleepDurationTicks`）。适合带着女仆长途冒险。
- 主模组的 `switch_schedule` 工具也能让模型切到冒险日程，给模型看的"当前日程"上下文里会说明它的含义。
- 冒险日程的女仆附近没有女仆床时，会去睡最近的一张没人睡的原版床（`AdventureVanillaBeds`，默认开）；有女仆床时始终优先女仆床。

> [!WARNING]
> 日程是按名字存进女仆存档里的。**如果有女仆正处于冒险日程，不要卸载本模组**：卸载后主模组读取这只女仆时会因为认不出 `ADVENTURE` 这个名字而出错。卸载前请先在游戏里把所有女仆的日程切回白班 / 夜班 / 全天。

### 女仆转告（改版新增，需搭配改版 Self Talk）

- 女仆的 AI 可以调用 `tell_maid` 工具，把一句话转告给同一主人名下的另一只女仆，对方通过 Self Talk 的互聊流程回应；成功后向主人和附近玩家显示一行 `<女仆甲 -> 女仆乙> 内容`。
- 带同一对女仆的冷却（默认 30 秒）与同一主人每分钟上限（默认 6 次）两重防刷；可用 `RelayEnabled` 关闭。
- 未安装改版 Self Talk，或其「女仆互聊」总开关（默认关闭）没开时，工具只返回失败说明。

## 常用命令

所有命令可见[功能 Wiki](WIKI.md)

```text
/tlmchat mode [on|off]
/tlmchat global
/tlmchat <消息>
/tlmchat to <名字> <消息>
/tlmchat uuid <UUID> <消息>
/tlmchat list

/tlmmemory set <名字> <key> <value>
/tlmmemory set-core <名字> <key> <value>
/tlmmemory get <名字> <key>
/tlmmemory list <名字>
/tlmmemory forget <名字> <key>
/tlmmemory export <名字> [json|text|context]
/tlmmemory summarize <名字>
```

## 构建

需要 Java 17：

```powershell
.\gradlew.bat build --no-daemon
```

构建产物位于：

```text
build/libs/tlm_sincerely-1.20.1-forge-0.4.0-beta.jar
```

开发环境可使用：

```powershell
.\gradlew.bat runClient --no-daemon
```

也可以双击根目录的 `runClient.bat`。项目开发约束与兼容说明见 [DEVELOPMENT.md](DEVELOPMENT.md)。

## 文档

- [功能 Wiki](WIKI.md)
- [开发注意事项](DEVELOPMENT.md)
- [已完成计划归档](docs/plans/)

## 配置与数据

```text
config/tlm_sincerely-common.toml
config/tlm_sincerely/auto_work_presets.json
config/tlm_sincerely/auto_work_compat.json
config/tlm_sincerely/maid_memories/<maid-uuid>.json
logs/tlm_sincerely/command_audit.log
```

## 许可证

本项目使用 [MIT License](LICENSE)。
