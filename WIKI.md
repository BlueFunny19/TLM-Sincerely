# 《车万女仆：真心为你》Wiki

> 面向玩家与开发者的功能说明文档。本文只描述当前版本的功能、命令、配置、兼容边界与代码索引，不记录开发历史。

## 项目信息

| 项目 | 内容 |
|---|---|
| 模组名称 | 《车万女仆：真心为你》 |
| Mod ID | `tlm_sincerely` |
| 平台 | Forge 1.20.1 |
| Java | 17 |
| 版本 | 0.2.0-beta |
| 主模组依赖 | Touhou Little Maid ≥ 1.5.3 |
| 必需依赖 | Cloth Config ≥ 11 |
| 可选依赖 | Jade ≥ 11、MaidFileManager ≥ 1.4、Maid Self-Talk ≥ 1.3.1 |
| 开发者 | terk |
| 许可证 | MIT |

安装时将构建产物放入 `mods` 目录，并确保 Touhou Little Maid 与 Cloth Config 已安装。Jade 不是必需依赖，仅用于在准星信息中显示自动工作状态。

---

## 一、快速开始

1. 安装 Touhou Little Maid、Cloth Config 与本模组。
2. 在游戏内通过配置界面或 `config/tlm_sincerely-common.toml` 调整功能。
3. 使用 `/tlmchat` 与附近女仆对话，或在聊天栏用 `@女仆甲 @女仆乙 内容` / `@全体 内容` 一句话与多只女仆对话。
4. 在女仆 GUI 中打开自动工作页，配置任务顺序并开启女仆的自动切换。
5. 使用 `/tlmmemory` 或 AI 工具管理女仆记忆。
6. 若允许女仆执行命令，通过配置开启 `run_command` 工具；破坏性命令可配置为需要主人确认。
7. 若安装 Maid Self-Talk 并开启其互聊，可在配置中开启 `tell_maid`，让女仆之间互相转告。
8. 想临时指挥女仆靠近 / 远离 / 前往某处 / 守护某人 / 留在原地 / 回家，或让她临时“全天候工作”时，直接用自然语言说即可（AI 工具 `maid_move` / `maid_guard` / `maid_work_time` / `maid_cancel`，见「三、6. 移动指令」）。

---

## 二、组织架构

模组按“接入层 — 功能层 — AI 层 — 数据层 — 客户端层”组织：

| 层级 | 职责 | 主要包位置 |
|---|---|---|
| 接入层 | 注册命令、事件、网络、TLM 扩展、任务数据 | `SincerelyMod`、`SincerelyExtension` |
| 对话层 | 聊天栏拦截、多目标解析、女仆查找、模式切换、改名 | `chatbar`、`command.ChatCommand` |
| 工作层 | 自动切换调度、优先级预设、检测结果、兼容报告 | `priority` |
| 记忆层 | 记忆存储、上下文注入、自动整理 | `memory` |
| 命令层 | 以主人身份执行命令、名单、确认、限流、审计 | `command`、`ai.tool.MaidCommandTool` |
| AI 层 | Context 注入与 Tool 调用 | `ai.context`、`ai.tool`、`ai.relay`、`ai.move` |
| 客户端层 | GUI、本地预设库、快照缓存、Jade 提示 | `client` |
| 数据层 | TOML 配置、JSON 预设、JSON 记忆、审计日志 | `config`、`priority.autowork`、`memory` |

### 源码包结构

```text
tlm_sincerely/
├── SincerelyMod.java
├── SincerelyExtension.java
├── ai/
│   ├── context/
│   ├── relay/
│   ├── move/
│   └── tool/
├── chatbar/
├── client/
│   ├── autowork/
│   ├── gui/
│   ├── network/
│   └── jade/
├── command/
├── config/
│   └── subconfig/
├── memory/
├── mixin/
├── schedule/
└── priority/
    ├── autowork/
    │   ├── compat/
    │   ├── menu/
    │   ├── network/
    │   └── push/
    ├── decision/
    └── detection/
        └── compat/
```

---

## 三、功能详解

### 1. 聊天栏女仆对话

#### 玩家功能

- 在聊天栏直接与附近的女仆对话。
- 支持 `@名字` 前缀指定女仆；严格前缀模式开启后，只有带前缀的消息会发送给女仆。
- 无前缀时，可按配置范围自动选择最近的女仆。
- 支持一句话发给多只女仆：`@女仆甲 @女仆乙 内容`，或用 `@全体 内容` 发给喊话范围（`TargetRange`）内自己名下的全部女仆（关键词可在配置中增删，比较时忽略大小写）。
- 多目标时，每只女仆收到的消息前会带一行“玩家同时对谁说话”的标注，让她知道现场还有哪些同伴，从而自行判断要不要把内容转告给同伴（见 5. 女仆转告工具）。
- 支持全局可见与私聊模式：私聊模式下玩家消息与女仆回复仅相关玩家可见。模式与可见性按玩家独立生效，随玩家数据持久化。
- 女仆回复会通过气泡与聊天栏展示。
- 支持中文女仆名字、UUID 精确选择、同名女仆提醒与附近女仆列表。
- 支持通过命令给女仆改名。

#### 多目标语法

| 写法 | 效果 |
|---|---|
| `@女仆甲 内容` | 旧的单目标形式，只发给女仆甲，不加“同时对谁说”标注 |
| `@女仆甲 @女仆乙 内容` | 同一句话发给两只女仆，各自独立回复；每只都收到“玩家同时对「女仆甲」「女仆乙」说”的标注 |
| `@全体 内容` | 发给喊话范围（`TargetRange`，默认 64 格）内自己名下全部存活女仆；`all` 等关键词等价（忽略大小写） |
| `@女仆甲 @全体 内容` | 全体关键词与具名目标可混用，结果去重 |

超出发送上限（默认 5，可配置 1~20）时只发给距离最近的若干只，并在聊天栏用黄色提示“已限制为最近的 N 只”。某个 `@名字` 找不到对应女仆时用红色列出未找到的名字，其余找到的女仆照常收到消息。

#### 相关命令

| 命令 | 说明 |
|---|---|
| `/tlmchat <消息>` | 与最近女仆对话 |
| `/tlmchat to <名字> <消息>` | 与指定名字女仆对话 |
| `/tlmchat uuid <UUID> <消息>` | 与指定 UUID 女仆对话 |
| `/tlmchat list` | 列出附近女仆 |
| `/tlmchat mode` | 切换自己的女仆对话模式（按玩家独立生效） |
| `/tlmchat mode on` | 开启自己的对话模式 |
| `/tlmchat mode off` | 关闭自己的对话模式 |
| `/tlmchat global` | 切换自己的全局/私聊可见性偏好（按玩家独立生效） |
| `/tlmchat rename <名字或 uuid:UUID> <新名字>` | 改名，仅可操作自己的女仆 |

#### 配置项

配置段：`[chatbar]`

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `ChatModeEnabled` | `true` | 新玩家的女仆对话模式默认值；玩家可用 `/tlmchat mode` 单独覆盖 |
| `GlobalChatVisible` | `true` | 新玩家的全局/私聊可见性默认值；玩家可用 `/tlmchat global` 单独覆盖 |
| `RequirePrefix` | `true` | 是否强制要求 `@` 前缀 |
| `AutoChatRange` | `5.0` | 无前缀时自动对话范围，`0` 表示禁用 |
| `PrefixPattern` | `@` | 名字前缀字符 |
| `MultiTargetEnabled` | `true` | 是否允许多女仆对话（`@a @b` / `@全体`）；关闭后退回旧的单目标行为 |
| `MaxTargets` | `5` | 一句话最多发给多少只女仆（1~20）；超出时只保留距离最近的若干只 |
| `TargetRange` | `64.0` | 喊话范围（格，1~256，直线距离、同维度）：`@名字` / `@全体`、`/tlmchat` 与 `/tlmmemory` 的女仆选择，以及 `tell_maid`、`maid_move` 找另一只女仆都用它；与无前缀自动对话的 `AutoChatRange` 相互独立 |
| `AllKeywords` | `["全体", "all"]` | 被当作“发给范围内全部女仆”的目标词，比较时忽略大小写 |

#### 当前边界

- 聊天栏 GUI 内的快捷按钮尚未提供。
- 聊天栏输入框内的 Tab 补全仅在命令环境中生效；`/tlmchat` 的参数补全可用。
- 女仆回复在多玩家环境下的广播行为需要在实机多人环境中验证。
- 女仆名字含空格时无法用 `@` 指定（解析在第一个空白处停止），这是已知限制。
- 多目标会让对话链接数增长：每只女仆都带着自己的历史、记忆与自动工作上下文独立请求一次，Token 消耗随目标数量近似线性增长；单轮 HTTP 超时 60 秒、最长约 16 轮工具调用，目前没有“取消某一批”的机制。
- 部分女仆在维护记忆或等待命令确认时会被跳过（与单目标时一致），因此“多目标只回来一部分回复”属于预期现象。
- 多目标不取消原消息的规则与单目标相同：只有玩家自己的私聊可见性偏好会决定是否隐藏原消息，全局可见时原消息（含全部 `@`）仍会被原版广播给全服。
- `/tlmchat` 命令仍然是单目标，不支持 `@全体`。

### 2. 自动工作与任务优先级

#### 玩家功能

- 在女仆 GUI 中打开“自动工作”页，可：
  - 新建、删除、切换、重命名预设；
  - 将任务从“可用任务”加入“已排序任务”；
  - 调整已排序任务的顺序；
  - 移除任务。
- 该页没有独立的保存按钮：打开这只女仆的配置页时，增删任务、调整顺序与改名会**立刻**写入本机预设库并同时绑定到这只女仆；切换或新建预设同样立即生效。未打开该页的其他女仆不受影响。
- 女仆头像下方提供自动工作开关；全局调度关闭时，该开关仅显示状态，不会提交切换请求。
- 每个女仆保存自己的绑定快照：预设名、预设 UUID 与任务顺序。服务端调度只读取该快照。
- 玩家客户端拥有自己的预设库；服务端仅在启动时读取一次作为种子，之后不再代替客户端修改预设库。
- 玩家可把自己的预设推送给其他玩家；接收方确认后才会写入其客户端预设库，且不会自动改变任何女仆的绑定。
- 若安装 Jade，准星指向“已开启自动工作且全局调度开启”的女仆时，会与“主人：”“模式：”同列显示“处于工作自动切换模式”。

#### 自动切换行为

- 只有日程为 `WORK` 且女仆自身自动工作状态为启用的女仆会被调度。
- 任务顺序按列表顺序作为优先级，索引靠前的任务优先级更高。
- 切换前会检测目标是否真正可用，包括：
  - 任务是否注册、启用；
  - 是否符合兼容策略；
  - 是否有可检测的工作目标；
  - 需要硬性工具的任务是否持有对应工具。
- 部分任务允许从背包换到主手后再切换，例如钓鱼竿、灭火器、剪刀与攻击族武器。
- 骑乘（椅子、棋盘、船等）时仍会对全部绑定任务做可用性检测；要切到“不是当前这份骑乘工作”的任务前会先让女仆下车，当前骑乘工作仍可用时保持坐着。玩家 `shift+右键` 的坐下待命不受影响。
- 攻击族按任务家族校验武器：近战“攻击”**不会**把弓、弩、三叉戟、御币当作近战武器（身上只有弓时不会切近战）；弓兵需要弓与箭；弩、三叉戟、弹幕各自按原模组规则判定，且切换前会把武器换到主手。
- 攻击任务的检测距离与原模组该任务的实际反应距离一致：近战在跟随模式下按“主人到目标”的距离判断，弓兵按“女仆到目标”的距离判断，并且都要求视线可见。这样不会出现“检测到目标就切入、切过去却原地发呆”的情况。
- 正在进行中的任务受到繁忙保护，不会被普通切换打断；但该保护有最大时长，且任务被持续判定为不可用时会在有限时间后放行。
- 任务可用与不可用都需要连续确认，避免瞬时抖动。
- 存在反向切换抑制：在时间窗口内反复 A→B→A 会触发冷却。
- 当女仆已自动切入某任务，但任务持续可用且 Brain 长时间没有工作目标时，可强制刷新一次 Brain。
- 实验性攻击抢占默认关闭；开启后，配置中的攻击任务可以在不等待普通保持时间时抢占当前任务，但仍然需要通过武器与目标检测。
- 全局开关只暂停调度，不会清除女仆状态、缓存或真实任务；重新启用会从现有状态继续。
- `idle` 是系统兜底任务，不是可配置的工作目标，永远不会被自动切入。
- 开启“强制启用预选工作”后，兼容黑名单不再阻止自动切换，但任务仍必须被检测器判定为可用；`idle` 与没有检测器的任务依旧永不切入。开启期间玩家登录会收到一条额外警告。

#### 预设推送

- 推送是“提议—确认”流程：
  1. 发送方从自己的客户端预设库选择预设；
  2. 服务端创建一个待确认提议；
  3. 接收方在聊天栏看到提议后点击接受或拒绝；
  4. 接受后，预设才会写入接收方的客户端预设库。
- 推送不会直接修改任何女仆的绑定快照。
- 广播推送需要发送方拥有等级 2 及以上权限。
- 待确认提议存在超时，接收方下线会清理对应提议。

#### 兼容报告

使用 `/tlmautowork compat` 查看当前服务器注册任务的兼容情况：

- `SUPPORTED`：存在专用检测器。
- `FALLBACK`：通过通用接口检测，例如攻击族或农耕族。
- `UNSUPPORTED`：没有可靠检测器，或该任务语义不适合自动切换。
- `BLOCKED`：被服务器名单阻止。
- `cook` 属于部分支持：仅支持炉灶相关子任务，其他设备保持不可用；该任务会持续显示在问题视图中。

报告中的问题视图包含被阻止任务、未加白名单的不支持任务，以及部分支持任务。服务器可在登录时按提醒等级向玩家发送摘要；把 `[multi_task] DisableCompatReminder` 打开可关闭该提醒，手动执行 `/tlmautowork compat report` 仍可用。

### 3. 简易记忆系统

#### 玩家功能

- 每个女仆拥有独立的键值对记忆。
- 记忆分为两类：
  - 核心记忆：少量重要记忆，全文注入 AI 上下文；
  - 归档记忆：其余记忆，默认仅注入键名与截断预览。
- 女仆可通过 AI 工具自主记住、回忆、遗忘、搜索与合并记忆。
- 记忆系统会在阈值触发时自动整理归档记忆；整理期间相关对话出口会被静默，AI 工具仅允许查询、回忆与合并。女仆正在睡觉时会先推迟整理，醒来后再进行（整理对话经 `chat()` 以主人身份发起，否则会被 Self Talk 的「叫不醒」当成叫她）。
- 玩家可通过命令查看、导出、删除记忆，或触发 AI 回顾补写。
- 记忆文件按女仆 UUID 持久化，服务端重启后保留。
- 女仆经神龛/胶片复活后记忆与名字自动延续（按胶片快照迁移；祭坛配方复活暂不覆盖）。
- 安装 [MaidFileManager](https://github.com/zgxhzhr/MaidFileManager)（`maid_file_manager` 1.4.0+，可选联动）后，导出 `.maid` 女仆档案会自动携带该女仆的全部记忆；跨存档、跨整合包导入（含 1.20.1 Forge ↔ 1.21.1 NeoForge 跨版本）后记忆自动跟随到新女仆。同一档案对同一玩家重复导入时会先备份旧记忆文件再覆盖；未安装该模组时无此行为，其余功能不受影响。

#### 相关命令

| 命令 | 说明 |
|---|---|
| `/tlmmemory set <名字> <key> <value>` | 设置归档记忆 |
| `/tlmmemory set-core <名字> <key> <value>` | 设置核心记忆 |
| `/tlmmemory get <名字> <key>` | 查看记忆详情 |
| `/tlmmemory list <名字>` | 列出全部记忆 |
| `/tlmmemory forget <名字> <key>` | 删除记忆 |
| `/tlmmemory export <名字> [json/text/context]` | 导出记忆 |
| `/tlmmemory summarize <名字>` | 触发 AI 回顾并补写遗漏；女仆正在睡觉时会提示稍后再试，不发送请求 |

#### 配置项

配置段：`[memory]`

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `Enabled` | `true` | 记忆系统总开关 |
| `MaxMemories` | `50` | 每个女仆最大记忆数 |
| `CoreMemoryLimit` | `10` | 核心记忆全文注入上限 |
| `ContextPreviewLength` | `30` | 归档记忆截断长度 |
| `AutoEvict` | `true` | 满容时自动淘汰最旧归档记忆 |
| `MemoryGuidance` | `true` | 向 LLM 注入记忆引导 |
| `TidyEnabled` | `true` | 自动整理开关 |
| `TidyThreshold` | `0.8` | 整理触发阈值 |
| `TidyCooldownMinutes` | `20` | 整理冷却分钟数 |
| `ShowSource` | `false` | 是否显示记忆来源玩家 |
| `PreviewMode` | `full` | 归档预览模式：`full` 或 `keys_only` |

#### 当前边界

- 没有记忆 GUI。
- 没有日记本物品。
- 不支持女仆之间共享记忆。
- 搜索为关键词子串匹配，不是语义搜索。
- 自动整理期间会按女仆维度静默对话出口；若同时有其他来源的对话，可能受到附带影响。
- 记忆来源在工具层使用女仆主人名，多人共享场景下不能精确区分实际对话发起者。

### 4. 女仆命令执行工具

#### 玩家功能

- 允许女仆把主人的自然语言意图翻译为一条 Minecraft 命令。
- 命令以主人身份执行，位置、维度、朝向、`@s` 与相对坐标均按主人上下文处理。
- 权限上限只能下调主人真实权限，不会提升。
- 黑名单命令始终拒绝，且不会进入确认流程。
- 确认名单中的命令在开启确认功能后，需要主人点击可点击的确认按钮。
- 主人可选择“本次游戏内不再确认”；该信任仅在当前登录会话内有效，主人登出或服务端重启后清空。
- 挂起确认期间，该女仆的新对话会被拒绝。
- 实际执行的命令会回显主人聊天栏。
- 命令审计日志记录决策、命令名、时间与相关女仆/主人，不记录完整输出。

#### 相关命令

| 命令 | 说明 |
|---|---|
| `/tlmconfirm run <token>` | 确认并执行挂起命令 |
| `/tlmconfirm cancel <token>` | 取消挂起命令 |
| `/tlmconfirm trust <token>` | 确认执行，并在本次登录会话内信任该类根命令 |

确认按钮通常会直接调用上述命令；`token` 与主人绑定，其他玩家无法代替确认。

#### 配置项

配置段：`[maid_command]`

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `CommandToolEnabled` | `true` | 是否向女仆提供 `run_command` 工具 |
| `ConfirmationEnabled` | `false` | 确认名单中的命令是否需要主人确认 |
| `SessionConfirmationEnabled` | `true` | 是否允许“本次游戏内不再确认” |
| `ConfirmationTimeoutSeconds` | `60` | 确认超时秒数 |
| `ConfirmationRequiredCommands` | 见配置文件 | 需要确认的根命令名单 |
| `BlacklistedCommands` | 见配置文件 | 永久拒绝的根命令名单 |
| `MaxPermissionLevel` | `4` | 主人权限上限，只下调不提升 |
| `MaxCommandsPerRequest` | `3` | 单次对话链最大命令尝试次数 |
| `MaxCommandLength` | `1024` | 最大命令长度 |
| `ToolResultMaxChars` | `2000` | 回传给模型的结果截断长度 |
| `AuditLogMaxSizeMb` | `16` | 审计日志轮转上限 |

#### 当前边界

- 名单是缓解措施，不是沙箱；无法穷举所有模组命令、别名或别名包装。
- 命令执行与确认都要求主人在线。
- 名单粒度为根命令，不支持子命令或参数级控制。
- 命令输出不进行分页处理。

### 5. 女仆转告工具（AI 工具 `tell_maid`，需 Self Talk）

#### 玩家功能

- 安装可选联动「Maid Self-Talk」（`maid_self_talk` ≥ 1.3.1）并开启其互聊总开关后，女仆可以在对话中调用 `tell_maid` 工具，把一句话转告给你名下的另一只女仆并请她回应。
- 触发时机由女仆自己按语义判断：只有当玩家的话**同时**对多只女仆说、或玩家明确要求“转告/问一下另一只女仆”时才该调用；闲聊或不需要同伴参与时不应调用，一次对话最多一次。
- 转告成功后，你和附近玩家会在聊天栏看到一行灰色的 `<女仆甲 -> 女仆乙> 转告内容`；女仆乙的回复由 Self Talk 的互聊流程自己显示。
- 目标女仆必须属于同一位主人、与发起者同维度、存活、且位于喊话范围（`TargetRange`，默认 64 格）内，名字匹配规则与聊天栏一致（精确优先、否则子串、多命中取最近）；不能点名自己，也不能点名玩家。
- 防刷与防回环：同一对女仆（不分方向）在冷却时间内不能再次互相转告；同一主人名下的全部女仆每分钟有总次数上限。超出时工具只返回失败说明，让模型自行放弃，不会有任何实际转告。

#### 配置项

配置段：`[relay]`

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `RelayEnabled` | `true` | 是否把 `tell_maid` 工具提供给女仆；关闭后该工具不会出现在模型工具列表中 |
| `RelayCooldownSeconds` | `30` | 同一对女仆的转告冷却秒数，不分方向（0 = 不限制） |
| `RelayMaxPerMinutePerOwner` | `6` | 同一主人每分钟的转告总次数上限（1~120） |

#### 当前边界

- **必须安装 Maid Self-Talk 并开启其互聊总开关**：其互聊总开关默认关闭，此时 `tell_maid` 会返回失败说明（“Self Talk rejected the relay”），不会有任何转告发生；未安装该模组时返回“Self Talk is not installed”。
- 转告是女仆之间的独立请求，不占用玩家对话线程；Self Talk 内部若因限流/链长上限拒绝，本工具同样只返回失败说明。
- 冷却是“一对女仆”粒度且不分方向，多个玩家共享同一只女仆时冷却也共享。
- 工具的“一次对话最多一次”是写在工具说明里的软约束，本模组没有可用的会话批次标识来做硬限制；硬限制只有冷却与每分钟上限。

---

### 6. 移动指令（AI 工具 `maid_move` / `maid_guard` / `maid_work_time` / `maid_cancel`）

#### 玩家功能

- 用自然语言指挥女仆“靠近我 / 远离我 / 去某个地方 / 保护某人 / 在原地待着 / 回家 / 临时改成全天工作”：女仆理解后调用下面四个工具之一，在限定时间内执行。它们共用同一套指令登记、暂停跟随与恢复机制：同一只女仆同时只有一条指令，新指令（不论来自哪个工具）覆盖旧指令。
- `maid_move` 有五个动作：`approach`（走近目标并保持在 `distance` 格内）、`retreat`（远离目标并保持在 `distance` 格外）、`goto`（前往坐标 `x` / `y` / `z`）、`stay`（留在原地，同时不再跟随主人）、`go_home`（立刻传送回自己的工作坐标并进入居家模式）。模型把 `guard` / `work_time` / `cancel` 传给它时会收到"请改用某某工具"的提示，而不是只回"未知动作"。
- `maid_guard`：守在目标身边 `distance` 格内（默认 5），并攻击威胁目标的敌对生物。守护本质上是"跟着一个目标并战斗"，所以不放在 `maid_move` 里。
- `maid_work_time`：不移动，只把日程临时改成“全天工作”。
- `maid_cancel`：取消当前指令。只有一个可选参数 `reason`（仅写入日志）：主 Mod 的工具都至少带一个参数，没有先例证明完全无参数的工具能被各家模型接口接受，所以不冒这个险。
- 目标 `target` 可填 `me`（或「我」）表示主人，也可以填同一主人名下另一只女仆的名字；名字匹配规则与聊天栏一致（先精确、否则子串、多命中取最近），不能填自己。
- 距离 `distance` 限制在 1~64 格：`approach` 默认 3、`retreat` 默认 16、`guard` 默认 5。
- 指令是**限时**的：`duration_seconds` 缺省取 `MoveDefaultDurationSeconds`（默认 600 秒 = 10 分钟），上限由 `MoveMaxDurationSeconds` 决定（默认 3600 秒），到时自动结束。同一只女仆同时只有一条指令，新指令覆盖旧指令。
- **永久指令（`permanent`）**：玩家明确表示“一直这样”（例如“一直守着澪”“在这儿一直等我”）时，模型会把 `permanent` 设为 `true`，指令不再超时，直到玩家让它取消；此时工具返回文字会明确要求模型在回复里告诉玩家“我会一直这样做，想停下只要说一声”。永久指令同样占用 `MoveMaxActivePerOwner` 的名额；`MoveAllowPermanent` 关闭时这类请求会被拒绝并给出改用限时指令的提示。永久指令期间女仆会每隔 `MovePermanentReminderMinutes` 分钟自己说一句“我还在……呢”提醒玩家（0 表示关闭）。
- **`stay`（留在原地）**：把女仆临时切到居家模式并清掉工作坐标，使她不再跟随主人、原地待命；到期 / `cancel` / 其他指令替换时**精确恢复**她原来的状态（原本是跟随就恢复跟随，原本是居家 + 工作坐标就原样写回）。上面这些“不要跟着主人”的指令（还有 `goto`、`retreat`，以及目标不是主人本人的 `approach` / `guard`）都会在生效期间暂停跟随——因为主模组的 `MaidFollowOwnerTask` 会无条件把她拉回主人身边；目标就是主人的 `approach` / `guard` 不暂停跟随（本来就该跟着）。
- **`go_home`（回家）**：只有在女仆**设有工作坐标**时可用（判定的是她的日程坐标 `SchedulePos.isConfigured()` / `getWorkPos()`，不是限制半径），没有就返回“没有工作坐标，没法回家”，不改变任何状态。有工作坐标时：先结束并恢复她当前暂停中的指令，在当前维度把工作坐标周围 ±4 格内找一个能站的位置传送过去（找不到就返回“附近找不到能站的地方”，状态不变），然后打开居家模式（工作坐标保持不变），并让女仆自己说一句“我回家啦”。这是**永久性改变**：不产生指令、不超时、不能用 `maid_cancel` 取消，要让她重新跟随请用主模组的跟随工具。`go_home` 不接受 `duration_seconds` / `permanent` 参数（传了会返回提示且什么都不做）。
- **到期与提醒**：限时指令**自然到期**时女仆会说一句“……的时间到了，我恢复自由行动啦”（`cancel` / 被新指令替换 / 因故丢弃时不说）。永久指令的提醒由 `MovePermanentReminderMinutes` 控制。
- **`maid_work_time`（原来 `maid_move` 的 `work_time` 动作）**：不移动，只做一件事——让她加班（见下面的「加班」）。可以限时，也可以 `permanent=true` 永久；和其他指令一样占 `MoveMaxActivePerOwner` 名额、有到期提示与永久提醒、会出现在给模型看的“当前指令”栏目里、也能跨重启恢复。它**不移动、不暂停跟随、不产生卡住检测**；女仆正在睡觉时不做改动并返回“正在睡觉，现在不适合让她工作”，日程本来就是全天工作时返回“本来就是全天工作，不需要改动”，这两种情况都不会创建指令。
- **加班（日程临时改成全天工作）**：玩家要女仆干活而她本来不在工作时间时（背景：玩家没给女仆设置工作坐标时，夜里日程行为会拦下移动，切换工作任务会回答“不在工作时间”），本模组把她的日程临时改成 TLM 的“全天工作”（`MaidSchedule.ALL`），干完再恢复原来的日程（`DAY` / `NIGHT` / `ALL` 原样写回）。让她加班的有两种来源（持有者），任何一个还在就继续加班，**最后一个走了才恢复**：
  - **一条生效中的移动类指令**（`approach` / `retreat` / `goto` / `guard` / `stay` / `maid_work_time`；`go_home` 不加班）：指令结束（到期 / 取消 / 被丢弃 / 被替换 / 重启后发现已到期）时释放。
  - **玩家（通过模型）在非工作时间让她切换工作任务**：主模组原本会回答“已切换，但当前日程不是工作时间”，并且不再去设置攻击目标；现在改为先让她加班，再按工作时间继续走完主模组的逻辑。女仆被切到待命任务、或 `OvertimeTaskHoldSeconds` 秒内没有再切换任务时释放（每次再切换任务重新计时）。“活干完了”目前没有对每一种任务都可靠的判断，所以用这两条代替。
  - 女仆正在睡觉时不动日程（返回文字里会说明）；日程本来就是全天工作时不改也不保存。移动指令是否触发加班由 `MoveForceWorkSchedule` 控制（`maid_work_time` 不受影响），切换工作任务是否触发由 `OvertimeTaskSwitchEnabled` 控制。
  - 加班期间若玩家自己改了日程（女仆界面或主模组 `switch_schedule` 工具），视为玩家接管：不恢复、丢掉保存的原日程并写 INFO；移动指令本身照旧继续，`maid_work_time` 则直接结束（它的唯一作用已被玩家覆盖）。
  - **疲劳软上限与点心**：只有她原来的日程此刻是**休息时段**时的加班才累积疲劳（工作 / 休闲时段的加班不累积），累到 `OvertimeSoftCapSeconds` 她就“累了”，约 5 分钟不加班就完全恢复（免得隔一会儿再下一次指令就能无限加班）。累了之后：
    - 开着 `OvertimeTreatEnabled`（默认）且点心表（`OvertimeTreats`，默认 `minecraft:cake=300`）不空：先看她自己背包里有没有点心，有就吃（`OvertimeEatOwnTreats`）；没有就开口向主人要，同时给主人发一条可本地化的提示消息（点心名字走物品的本地化名），并走到主人身边。主人在“等待时长”（`OvertimeTreatWaitSeconds`）内**右键递给她**一个点心（主手，仅主人）、或放进她的背包，她就多撑点心表里写的秒数；等满时长没要到、或主人不在线 / 不在同维度 / 超出喊话范围（`TargetRange`），她就说一句“撑不住了”，**取消手头的指令**（连同其暂停跟随等状态一并恢复）、结束加班回去休息，并且**在这段休息时间结束之前不能再加班**——期间下达移动指令或 `maid_work_time` 会被拒绝或不再加班，返回文字里会告诉模型她已经累坏了。
    - 关闭点心：她只抱怨一句（`OvertimeComplainEnabled` 关掉则连抱怨也没有）继续干，下一次“累了”再过一个上限之后。
    - 她要点心期间，移动类指令暂停下发（免得和“走向主人”互相拉扯），拿到点心后继续。
    - 疲劳与“休息中不能加班”的状态只在内存里：服务器重启后清零（可接受的限制）。
- **跨服务器重启恢复**：指令与“暂停跟随前保存的原状态”写进女仆实体的持久化标签 `tlm_sincerely:move_order`；加班状态（原日程、持有者、切换任务的到期时间）单独写进 `tlm_sincerely:overtime`。重启后女仆加载时：加班状态先装回（日程仍是“全天工作”就接着加班；已经不是则视为玩家接管，丢掉记录），然后指令没到期（或永久）→ 重新登记回管理器，剩余时间照算，永久指令的提醒计时从重启后重新开始；已经到期或数据不可信 → 立刻恢复原状态，并释放这条指令占着的那份加班，不让女仆留在被改过的状态里。服务端停止时不执行恢复动作（此刻世界正在保存，标签已经写好）。旧版本把原日程写在指令标签的 `originalSchedule` 键里，读到时会一次性交给加班管理器接收。
- **玩家接管检测**：暂停跟随期间如果玩家在女仆界面里、或用主模组的跟随工具把居家模式改了回去，视为玩家接管——该指令立即结束、**不再恢复**，只写一条 INFO 日志；加班期间玩家改了日程的处理见上面的「加班」。
- 指令的运行时状态只存在于服务端内存，重启后由上面的持久化标签恢复；女仆死亡 / 被移除 / 所在维度卸载、目标消失 / 离线 / 跨维度时指令失效（恢复记录仍在标签里，重新加载时处理）。
- **路走不通时的兜底（传送 / 放弃）**：女仆连续 5 秒没有更靠近目的地（或原版寻路失败标记 `CANT_REACH_WALK_TARGET_SINCE` 连续出现）就判定为卡住。此时若 `MoveTeleportFallback` 开启：距离不超过 `MoveTeleportMaxDistance` 格 → 找一个能站的位置把她**直接传送过去**（同一条指令最多 3 次、相邻两次至少间隔 5 秒），并替她在聊天栏说一句“路被挡住了，我直接过来了”之类的话；距离超过上限 → 取消指令，她说明“离得太远了”；找不到能站的位置 → 取消指令，她说明“那边找不到能站的地方”；3 次传送用完仍然卡住 → 取消指令，她说明“怎么都过不去，我放弃了”。关闭该开关则保持旧行为：继续尝试走路，只在服务端日志里记一条 INFO，不传送也不说话。传送用的是主模组自己把女仆挪到主人身边时的同一套动作（`moveTo` + `navigation().stop()` + 清空 `WALK_TARGET`/`LOOK_TARGET`/`PATH` + `getNavigationManager().resetNavigation()`）。
- **`goto` 到位后指令不结束**：会一直保持到 `duration_seconds` 到期；期间她漂出“到位半径 + 2 格”才会重新下发移动，滞回带内的轻微移动不理会（“在这儿等着我”不会因为她被别的行为拉走两步就失效）。
- 守护时的攻击：只有当女仆当前的任务是战斗类任务（`IAttackTask`）时才会主动攻击。当前是农耕、钓鱼等非战斗任务时，工具**不会擅自换任务**，只做“守在身边”，并在给模型的返回里写明“要战斗需先切换到战斗类任务”。
- 与自动工作调度：指令生效期间（含 `maid_work_time`）、以及因切换工作任务而加班期间，自动工作调度器会跳过这只女仆（`TaskAutoSwitchHandler` 里的单行钩子），不会自动替她切走任务；结束后恢复正常调度。跳过只表示调度器不自动切换，手动 / 模型切换工作任务照常有效。
- 与主模组工具的分工：只想让女仆一直跟着玩家请用「切换跟随状态」工具，想让她去干活请用「切换工作任务」工具，坐下 / 待命各有专用工具；本工具只负责“限时走一趟”。

#### 配置项

配置段：`[move]`

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `MoveEnabled` | `true` | 是否把 `maid_move` / `maid_guard` / `maid_work_time` / `maid_cancel` 这四个工具提供给女仆；关闭后它们都不会出现在模型工具列表中 |
| `MoveDefaultDurationSeconds` | `600` | 模型没有传 `duration_seconds` 时使用的时长（秒，10~86400） |
| `MoveMaxDurationSeconds` | `3600` | 单条指令 `duration_seconds` 的上限秒数（10~86400），超出时按此值截断。**旧版本默认值为 300，旧配置文件里已保存的旧值不会自动迁移** |
| `MoveMaxActivePerOwner` | `8` | 同一主人名下全部女仆同时生效的指令条数上限（1~64），超出时新指令被拒绝并告知模型；永久指令同样占名额 |
| `MoveAllowPermanent` | `true` | 是否允许 `permanent=true` 的永久指令；关闭后这类请求被拒绝并提示模型改用限时指令 |
| `MovePermanentReminderMinutes` | `10` | 永久指令每隔多久让女仆自己说一句提醒（分钟，0~120，0 表示关闭）；重启后重新登记的永久指令从重启后重新计时 |
| `MoveTeleportFallback` | `true` | 卡住走不到目的地时是否直接传送（同一条指令最多 3 次、相邻两次至少间隔 5 秒）；关闭后继续尝试走路，只写 INFO 日志，不传送也不说话 |
| `MoveTeleportMaxDistance` | `64` | 传送兜底的最大距离（格，8~256）：女仆到目的地的距离超过它就放弃指令（她会说明“太远了”），只有不超过时才传送过去 |
| `MoveForceWorkSchedule` | `true` | 移动指令生效期间，只要女仆没在睡觉就让她加班（日程临时改成“全天工作”），指令结束后恢复原日程；关闭后除 `maid_work_time` 外都不再触发加班。你手动改日程会被视为接管，指令结束时不会把日程改回去 |

配置段：`[overtime]`（加班，见上面「加班」）

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `OvertimeTaskSwitchEnabled` | `true` | 玩家（通过模型）在女仆不是工作时间时让她切换工作任务，是否让她加班而不是回答“现在不是工作时间” |
| `OvertimeTaskHoldSeconds` | `300` | 切换工作任务触发的加班在没有新指令时最多持续多久（秒，30~3600）；每次再切换任务重新计时，用完或女仆被切到待命任务就恢复原日程 |
| `OvertimeSoftCapEnabled` | `true` | 是否启用“休息时段加班会累”的疲劳上限 |
| `OvertimeSoftCapSeconds` | `180` | 在休息时段加班多少秒后她会累（现实秒，30~3600） |
| `OvertimeComplainEnabled` | `true` | 点心功能关闭时，累了是否抱怨一句（开着点心时，要点心的那句话总会说） |
| `OvertimeTreatEnabled` | `true` | 累了是否向主人要点心；关闭则只抱怨并继续干 |
| `OvertimeTreats` | `["minecraft:cake=300"]` | 可接受的点心，格式 `命名空间:物品=秒数`，格式不对的条目忽略 |
| `OvertimeEatOwnTreats` | `true` | 累了时是否先吃自己背包里的点心 |
| `OvertimeTreatWaitSeconds` | `90` | 向主人要点心后最多等多久（秒，10~600），没要到就去休息 |

#### 当前边界

- **指令默认限时 10 分钟**：到时间自动结束并说一句到期提示，不会变成“永久跟随”。需要长期保持请让模型把 `permanent` 设为 `true`（受 `MoveAllowPermanent` 控制），需要长期跟随请用主模组的跟随工具。
- **“精确恢复原状态”的语义**：`stay` / `goto` / `retreat` / 目标不是主人的 `approach` / `guard` 在生效期间会把女仆临时切成“居家 + 无工作坐标”。指令结束时本模组把**暂停前保存的**居家模式与工作坐标原样写回：原本是普通跟随 → 恢复成普通跟随；原本就是居家 + 工作坐标 → 恢复成居家 + 原工作坐标（不会被强行关掉居家模式）。若女仆不在场（区块卸载、被移除）则恢复记录保留在实体标签里，等她重新加载时再恢复；服务端停止时也不做恢复，交给下次加载。
- **玩家改了模式就中止指令**：暂停期间如果玩家在女仆界面里、或用跟随工具把居家模式改了回去，该指令立即结束且**不再恢复**（避免把玩家刚改的设置覆盖回去），只写一条 INFO 日志。
- **加班的边界**：只有“正在睡觉”一个条件会阻止加班（坐着的女仆在 `maid_work_time` 下照常加班；移动类指令本身仍然会先因“坐着 / 睡觉 / 骑乘 / 被拴住”被拒绝）；女仆本来就处于 `ALL`（全天工作）时不改也不保存，因此结束时不会把玩家的“全天工作”设置改掉。**你手动改日程（女仆界面或主模组 `switch_schedule` 工具）会中止恢复**：本模组把这次改动视为玩家接管，丢掉保存的原日程、不再写回，移动指令本身继续有效；`maid_work_time` 因为唯一作用已被覆盖会直接结束。加班只影响日程，不改居家模式与工作坐标（那是 `stay` 等指令的“暂停跟随”在管）。切换工作任务触发的加班目前不会因为“这个任务已经没活可干”而提前结束，只会在切到待命任务或 `OvertimeTaskHoldSeconds` 用完时结束。
- **移动可能与女仆自己的任务行为交替**：本工具每 10 tick 重新下发 Brain 的 `WALK_TARGET`（与主模组移动 AI 同一条路径，`MoveToTargetSink` 消费该记忆），但女仆当前任务自身若也在走路（农耕、收集等），两者会交替生效，不保证直线走过去。**特别注意普通“跟随主人”模式（非 Home）**：主模组的 `MaidFollowOwnerTask` 无条件注册在女仆 Brain 的 CORE activity 上，只要女仆离主人超过“活动半径 - 2”格就会重新写 `WALK_TARGET` 指向主人（更远还会直接把她传送回主人身边），所以 `goto` / `retreat` 想让她“待在别处”时可能被跟随行为拉回。本模组为此在“不要跟着主人”的指令期间**临时把女仆切到居家模式**（见上面的“精确恢复原状态”），`target` 是主人的 `approach` / `guard` 不这么做。居家模式本身仍然限制活动范围（本模组会先 `clearRestriction`，但在有工作坐标的女仆上主模组的日程刷新可能把范围写回来），所以远距离目标仍可能走不到。
- **传送兜底只解决“走不过去”，不解决“被别的行为拉走”**：传送落点由目标周围的环 / 后退点 / 坐标决定，传送后按主模组的方式清空导航与 `WALK_TARGET`；如果女仆随后被跟随、农耕等行为再次拉走，会重新进入“走近 → 卡住 → 兜底”的循环，同一指令的传送次数上限（3 次）就是为此设的闸门。
- **坐下 / 睡觉 / 骑乘 / 被拴住**时不能移动：下发指令时直接返回“请先让她站起来 / 醒来”；指令生效期间进入这些状态则暂停下发移动（指令不取消，状态解除后继续），暂停期间不计入“卡住”的 5 秒。`work_time` 不做移动，不受这项检查限制（它只额外拒绝“正在睡觉”这一种情况）。
- **Home 模式**的女仆照常下发指令，但活动范围受限可能走不到目标，返回文字里会带一句提示。
- **`target` 指向另一只女仆时限定同维度、喊话范围（`TargetRange`，默认 64 格）内**；同名女仆在其他维度时返回“跨维度”的提示，超出范围则视为找不到（与聊天栏 `@名字` 的口径一致）。
- **守护只打敌对生物**：必须是 `Enemy`，且不是玩家 / 女仆 / 有主人的生物，并通过当前攻击任务的攻击排除规则（`IAttackTask#canAttack`）二次过滤；它不会替玩家判断“谁算威胁”。
- **守护设置的攻击目标在指令结束后不会主动清除**：与主模组“切换任务并指定攻击目标”的语义一致，之后是否继续攻击由女仆自己的战斗 AI 决定。
- **`go_home` 依赖 TLM 的“工作坐标”**：判定的是日程坐标 `SchedulePos.isConfigured()` / `getWorkPos()`（`EntityMaid#hasRestriction()` 在 TLM 里被覆写成 `isHomeModeEnable()`，`getRestrictRadius()` 的默认值是 `MAID_NON_HOME_RANGE` 而不是 -1，两者都不能用来判断“有没有工作坐标”）。`go_home` 只在**当前维度**传送，目标附近 ±4 格内找不到能站的位置就放弃且不改变状态。
- **限时指令的“剩余时间”与运行状态只在服务端内存**，跨重启靠女仆实体上的 `tlm_sincerely:move_order` 标签恢复；给模型看的“当前指令”栏目由 `ai/context/MaidMoveContext`（栏目标题「当前的移动指令」）提供。`MoveMaxActivePerOwner` 只是同一主人同时生效条数的上限，不是速率限制。

### 7. 冒险日程（给主模组的日程枚举追加第四个值 `ADVENTURE`）

- **是什么**：TLM 的 `MaidSchedule` 原来只有 `DAY`（白班）/ `NIGHT`（夜班）/ `ALL`（全天）。本模组在类初始化时给它追加第四个值 `ADVENTURE`（`MaidScheduleEnumMixin`，用构造器访问器造新值、替换 `$VALUES`）：几乎全天 `WORK`，只在睡眠窗口里 `REST`，**没有 `IDLE`**。默认窗口从 tick 17000（23:00）开始、长 4000 tick（到次日 03:00）；窗口可以跨过一天的结尾。时间表由 `schedule/AdventureWindow`（纯逻辑，有单测）生成，`AdventureSchedule.timetable()` 把它转成原版 `Schedule`（配置变了就重建，女仆下次刷新大脑时换上）。
- **为什么要一串混入**：主模组里有好几处对日程做 `switch`，对认不出的值会走默认分支（多数是当成白班，有一处直接抛 `IncompatibleClassChangeError`），所以逐处补齐：
  - `MaidBrainScheduleMixin`：`MaidBrain.registerSchedule` 对冒险女仆换上自己的时间表；
  - `EntityMaidScheduleMixin`：`getScheduleDetail()`（"此刻该做什么"，切换工作任务、工作坐标、当前活动上下文、界面提示都经过它）按冒险时间表回答；
  - `ScheduleModeContextMixin`：给模型的"当前日程"上下文原本遇到未知值会抛错，这里补上文字并说明含义；
  - `SwitchScheduleToolMixin`：`switch_schedule` 工具说明末尾补一句 `ADVENTURE` 的含义，聊天里的调用摘要改成"切换日程为冒险"；
  - `ScheduleButtonMixin`（仅客户端）：女仆界面的日程按钮为冒险画自己的图标（`textures/gui/schedule_adventure.png`，以主模组的"全天"图标为底、边框不变；原图中间那条白天 / 夜晚的对角分界线正好穿过图标正中，于是把它画成木旗杆（杆顶金色杆头、挂一面向右飘的红旗），一把剑在正中与旗杆交叉成 X（剑柄、护手在左上，剑尖插在右下），帐篷搭在 X 下半部分的夹角里；生成脚本留在本地，换图只需替换这张 61×13 的贴图）并显示冒险的时间表提示；
  - `AdventureBedTaskMixin`：`MaidBedTask.findBed` 在主模组没找到女仆床时，为冒险女仆在原版床（POI `HOME`）里找最近一张没人睡的床；`start` 里让她对原版床 `startSleeping`（原版会把床标成有人、醒来时放开）。女仆床始终优先。
  所有注入都是 `require = 0`：主模组改了内部结构只会让冒险日程退化（被当成白班），不会让游戏启动失败。编译期代码引用不到 `ADVENTURE`，运行期一律经 `AdventureSchedule.value()` / `is()`；混入没有生效时它返回 `null`，冒险逻辑自动不触发。
- **与加班的关系**：`ScheduleTimeline` 认识 `ADVENTURE`（按配置的窗口），冒险女仆在睡眠窗口里被安排加班同样会累积疲劳；加班结束时按名字写回 `ADVENTURE`。
- **卸载风险**：日程是按名字（`MaidScheduleMode`）存进女仆存档的，主模组读档时用 `MaidSchedule.valueOf` 且没有兜底。有女仆处于冒险日程时卸载本模组，加载这只女仆会出错——README 里有 `[!WARNING]` 提示，卸载前要把所有女仆切回原来的三种日程。
- **配置段 `[adventure]`**：

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `AdventureSleepStartTick` | `17000` | 每天从哪个 tick 开始睡（0 = 06:00，18000 = 00:00），0~23999，窗口可跨天 |
| `AdventureSleepDurationTicks` | `4000` | 睡多久（1000 tick = 1 小时），1000~20000；其余时间都在工作 |
| `AdventureVanillaBeds` | `true` | 附近没有女仆床时，冒险女仆是否睡原版的床（从不睡有人的床，始终优先女仆床） |

### 8. 回复节奏（模型分句与 `<等待>` 停顿标记）

- **两个彼此独立的开关，默认都关**：
  - `ReplyModelSplitEnabled`：在女仆的系统提示词里加一小段（`ai/ReplyStylePrompts`），要求模型像聊天软件发消息那样把回复拆成几条短句、每条占一行（最多 4 行）；聊天栏本来就会把每一行显示成一条消息（`SplitReplyLines`，需保持开启）。
  - `ReplyPauseMarkerEnabled`：告诉模型可以在两句话之间写 `<等待>` 表示停顿；聊天栏把标记之后的话晚一点显示。两个开关都关着时不加任何提示词，不增加 token。
  - 提示词挂在 `MemoryGuidanceMixin` 已有的系统消息注入点（`MaidAIChatManager.buildMessage` 里 `appendSummaryMessage` 之前），与记忆说明、语言指令同处，所以聊天与 Self Talk 的自话共用。
- **标记一律会被剥掉（兜底，不受开关影响）**：`LLMCallbackReplyMarkerMixin` 在 `LLMCallback.onSuccess` 的最开头处理 `ResponseChat`——聊天正文与 `---` 之后的语音正文里的标记都去掉，标记所在位置换成换行。因此气泡、语音（TTS）、聊天栏、聊天历史里永远不会出现标记，即使开关全关、模型自己乱写也一样。Self Talk 的自话 / 欢迎语回调在收尾里调用 `super.onSuccess`，同样经过这里（它的梦话 / 嘟囔不调父类，不会被提示这种写法）。
- **标记的写法宽松**：`<等待>`、`< 等待 >`、`<等待/>`、`<等待 2>`、`<等待:1.5秒>`、`<wait>`、`<wait 2s>`（不分大小写）。带数字按数字停（夹在 0.3~10 秒之间），不带用 `ReplyPauseSeconds`（默认 1.5 秒）；连续标记的停顿相加；开头与结尾的标记没有意义，被忽略。一条回复里所有停顿加起来不超过 `ReplyPauseMaxTotalSeconds`（默认 12 秒），超出的不再往后拖。
- **怎么错开显示**：解析时（可能在 LLM 响应线程）把"各段正文 + 各段之间的停顿"按女仆登记在 `PendingReplyPauses`；稍后 `ChatBubbleManager.addLLMChatText` 向主人发聊天消息时（`MaidChatBroadcastMixin`），按"同一只女仆 + 同样的正文（空白规整后比较）"取走登记，用 `ReplyPauses.plan` 排出计划：第一段马上显示，后面的段按累计停顿由 `ReplyDelivery`（`DelayedQueue`，每个服务端 tick 放行到点的）晚显示；每段里的换行仍按 `SplitReplyLines` 拆成多条。对不上、过期（30 秒）、只有一段时照常即时显示。晚显示的行在玩家下线、或女仆正在记忆整理（静默）时直接丢弃；服务端停止时队列清空。
- **只错开聊天行**：气泡与语音仍是整段一次性出现（它们走主模组自己的路径，没有"分段"的概念），所以停顿主要在聊天栏里看得出来；开了语音时，聊天行的显示本身由主模组在语音就绪时触发，停顿从那一刻算起。
- **配置段 `[reply]`**：

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `ReplyModelSplitEnabled` | `false` | 是否让模型把回复拆成几条短句、每条一行（提示词） |
| `ReplyPauseMarkerEnabled` | `false` | 是否告诉模型可以写 `<等待>`，并按标记错开聊天行（标记无论如何都会被剥掉） |
| `ReplyPauseSeconds` | `1.5` | 不带数字的标记停多久（秒，0.3~10） |
| `ReplyPauseMaxTotalSeconds` | `12.0` | 单条回复所有停顿的累计上限（秒，1~60） |

---

## 四、注册命令总表

| 命令 | 权限要求 | 运行侧 | 说明 |
|---|---|---|---|
| `/tlmchat` | 玩家 | 服务端 | 与附近女仆对话 |
| `/tlmchat to` | 玩家 | 服务端 | 按名字选择女仆 |
| `/tlmchat uuid` | 玩家 | 服务端 | 按 UUID 选择女仆 |
| `/tlmchat list` | 玩家 | 服务端 | 列出附近女仆 |
| `/tlmchat mode` | 玩家 | 服务端 | 切换自己的对话模式（按玩家生效） |
| `/tlmchat global` | 玩家 | 服务端 | 切换自己的全局/私聊偏好（按玩家生效） |
| `/tlmchat rename` | 仅女仆主人 | 服务端 | 女仆改名 |
| `/tlmmemory` | 仅女仆主人 | 服务端 | 记忆管理 |
| `/tlmautowork compat report` | 玩家 | 服务端 | 查看问题任务 |
| `/tlmautowork compat report all` | 玩家 | 服务端 | 查看全部任务 |
| `/tlmautowork compat supported` | 玩家 | 服务端 | 查看专用检测任务 |
| `/tlmautowork compat fallback` | 玩家 | 服务端 | 查看通用接口任务 |
| `/tlmautowork compat unsupported` | 玩家 | 服务端 | 查看不支持任务 |
| `/tlmautowork compat blocked` | 玩家 | 服务端 | 查看被阻止任务 |
| `/tlmautowork compat blacklist` | 等级 2 | 服务端 | 黑名单增删查 |
| `/tlmautowork compat whitelist` | 等级 2 | 服务端 | 白名单增删查 |
| `/tlmautowork compat set reminder` | 等级 2 | 服务端 | 登录提醒等级 |
| `/tlmautowork compat reload` | 等级 2 | 服务端 | 重载兼容配置 |
| `/tlmautowork preset send` | 玩家；广播需等级 2 | 客户端命令 | 推送单个预设 |
| `/tlmautowork preset sendlibrary` | 玩家；广播需等级 2 | 客户端命令 | 推送全部预设库 |
| `/tlmautowork preset accept` | 目标玩家 | 服务端 | 接受预设推送 |
| `/tlmautowork preset reject` | 目标玩家 | 服务端 | 拒绝预设推送 |
| `/tlmconfirm` | token 对应主人 | 服务端 | 命令确认流程 |

> `/tlmautowork preset send` 与 `/tlmautowork preset sendlibrary` 在客户端注册。未识别的客户端命令路径会回退到服务端命令分发器，因此 `compat`、`accept`、`reject` 等子命令仍然可用。

---

## 五、AI 能力注册

### Context

| ID | 说明 |
|---|---|
| `tlm_sincerely_memory` | 女仆持久化记忆：关于玩家、历史事件与偏好的键值事实 |
| `tlm_sincerely_auto_work` | 当前自动工作状态与绑定任务顺序 |

### Tool

| Tool ID | 可用 action | 说明 |
|---|---|---|
| `auto_work` | `query`、`set_auto_enabled`、`add_task`、`remove_task`、`move_task` | 查询并编辑当前女仆的绑定任务顺序 |
| `tlm_memory` | `remember`、`recall`、`forget`、`search`、`merge` | 女仆自主管理记忆 |
| `run_command` | 由 LLM 生成命令 | 以主人身份执行 Minecraft 命令 |
| `tell_maid` | 参数 `target`、`message` | 把一句话转告给同主人名下另一只女仆并请她回应；需安装并开启 Maid Self-Talk 的互聊，见「三、5. 女仆转告工具」 |
| `maid_move` | `approach`、`retreat`、`goto`、`stay`、`go_home` | 限时地走近 / 远离 / 前往坐标 / 留在原地，或永久地回家，见「三、6. 移动指令」 |
| `maid_guard` | 参数 `target`、`distance`、`duration_seconds`、`permanent` | 守在某人身边并攻击威胁他的敌对生物，见「三、6. 移动指令」 |
| `maid_work_time` | 参数 `duration_seconds`、`permanent` | 把日程临时改成全天工作，见「三、6. 移动指令」 |
| `maid_cancel` | 可选参数 `reason`（仅写日志） | 取消当前指令并恢复被临时改动的状态，见「三、6. 移动指令」 |

`auto_work` 不能创建、选择、重命名或删除预设：预设库属于玩家客户端，服务端不可见。工具会明确拒绝这些操作，并引导玩家在女仆 GUI 中完成。旧的数字优先级 `set` 语义已不再支持。

---

## 六、兼容工作模式

### 兼容等级

| 等级 | 含义 | 是否自动调度 |
|---|---|---|
| `SUPPORTED` | 存在专用检测器 | 允许 |
| `FALLBACK` | 通过通用任务接口检测 | 允许 |
| `UNSUPPORTED` | 无可靠检测器或任务不适合自动切换 | 不允许 |
| `PARTIAL_SUPPORT` | 仅支持已验证子集 | 允许，但持续展示在问题视图中 |
| `BLOCKED` | 被服务器名单阻止 | 不允许 |

### 当前兼容矩阵

| 来源 | 任务 UID | 兼容等级 | 说明 |
|---|---|---|---|
| Touhou Little Maid | `touhou_little_maid:honey` | `SUPPORTED` | 成熟蜂巢、产物空间与可达性 |
| Touhou Little Maid | `touhou_little_maid:feed` | `SUPPORTED` | 主人状态与公开食物任务优先级 |
| Touhou Little Maid | `touhou_little_maid:milk` | `SUPPORTED` | 空桶、背包空间与可达成年牛 |
| Touhou Little Maid | `touhou_little_maid:feed_animal` | `SUPPORTED` | 专用良性喂养检测，覆盖错误的攻击回退 |
| Touhou Little Maid | `touhou_little_maid:torch` | `SUPPORTED` | 亮度、放置面、火把与路径可达性 |
| Touhou Little Maid | `touhou_little_maid:fishing` | `SUPPORTED` | 需要鱼竿与可用水域/座位；切入前自动换到主手 |
| Touhou Little Maid | `touhou_little_maid:extinguishing` | `SUPPORTED` | 灭火器持有与生物着火目标；切入前自动换到主手 |
| Touhou Little Maid | `touhou_little_maid:shears` | `SUPPORTED` | 剪刀与可剪切实体；切入前自动换到主手 |
| Touhou Little Maid | `touhou_little_maid:board_games` | `SUPPORTED` | 未占用或当前女仆正在使用的棋盘 POI |
| Touhou Little Maid | `touhou_little_maid:attack` | `FALLBACK` | 近战武器与攻击目标 |
| Touhou Little Maid | `touhou_little_maid:ranged_attack` | `FALLBACK` | 弓与对应弹药 |
| Touhou Little Maid | `touhou_little_maid:crossbow_attack` | `FALLBACK` | 弩 |
| Touhou Little Maid | `touhou_little_maid:trident_attack` | `FALLBACK` | 三叉戟 |
| Touhou Little Maid | `touhou_little_maid:danmaku_attack` | `FALLBACK` | 御币 |
| Touhou Little Maid | `touhou_little_maid:idle` | 排除 | 系统兜底任务，不参与报告计数与自动调度 |
| 任意模组 | 实现 `IAttackTask` 的任务 | `FALLBACK` | 使用任务自身的武器判定；第三方攻击任务保持其原有契约 |
| 任意模组 | 实现 `IFarmTask` 的任务 | `FALLBACK` | 通用农耕检测，按基准点单点判可达，受区块与路径预算限制 |
| Touhou Little Maid | `touhou_little_maid:cocoa` / `melon` | `FALLBACK` | 通用农耕检测；这两个任务按“基准点周围 3×2×3 任一格可达”判定，与主模组一致 |
| MaidSoulKitchen | `maidsoulkitchen:berries_farm` | `SUPPORTED` | 需要公共 `ICompatFarmTask` 相关类存在；可达性按浆果丛周围 3×2×3 判定 |
| MaidSoulKitchen | `maidsoulkitchen:fruit_farm` | `SUPPORTED` | 按女仆 Handler 与成熟水果扫描；可达性判在果实下方基准点 |
| MaidSoulKitchen | `maidsoulkitchen:feed_animal_t` | `SUPPORTED` | 专用繁殖/清理检测，覆盖错误的攻击回退 |
| MaidSoulKitchen | `maidsoulkitchen:cook` | `PARTIAL_SUPPORT` | 仅支持炉灶设备；其他设备保持不可用 |
| Maid Useful Task | `maid_useful_task:maid_tree` | `SUPPORTED` | 只读自然树近似与可达性检测 |
| Maid Useful Task | `maid_useful_task:locate` | `UNSUPPORTED` | 本质是跟随主人的寻路任务，不适合自动切换 |
| Maid Useful Task | `maid_useful_task:revive_player` | `UNSUPPORTED` | 由玩家死亡事件驱动，没有稳定世界扫描语义 |
| Maid Storage Manager | `maid_storage_manager:storage_manage` | `SUPPORTED` | 支持 PLACE/RESORT；REQUEST、CO_WORK 与未核验状态保持未知 |
| 其他未适配任务 | 任意 UID | `UNSUPPORTED` | 无检测器时不自动切入，但可通过白名单显示在报告中 |

> Maid Storage Manager 的实测基线为 1.15.6。更旧的 1.4.1 版本与 Touhou Little Maid 1.5.3 存在不兼容，女仆切入 PLACE 时可能崩服；请使用 1.15.x 或更新版本。

### 可选联动

| 联动对象 | 类型 | 说明 |
|---|---|---|
| Jade ≥ 11 | 显示 | 准星信息中显示女仆自动工作状态 |
| MaidFileManager ≥ 1.4 | 数据迁移 | `.maid` 档案导出/导入携带记忆 |
| Maid Self-Talk ≥ 1.3.1 | AI 互聊 | 为 `tell_maid` 提供转告/互聊 API；未安装时 `tell_maid` 返回失败说明，其余功能不受影响 |

### 兼容配置

配置文件：`config/tlm_sincerely/auto_work_compat.json`

| 字段 | 默认值 | 说明 |
|---|---|---|
| `blacklist` | 空 | 阻止自动调度的任务 UID |
| `whitelist` | 空 | 显示在报告中但不改变调度权限的任务 UID |
| `knownBadFallback` | 空 | 已知不可靠的通用回退任务 UID |
| `reminderLevel` | `OP_ONLY` | 登录提醒等级：`ALL`、`OP_ONLY`、`DISABLED` |

内置的 `knownBadFallback` 包含旧版本中错误的攻击回退任务；当对应专用检测器注册后，专用检测优先，任务按 `SUPPORTED` 处理。

---

## 七、配置与数据文件

| 文件 | 说明 |
|---|---|
| `config/tlm_sincerely-common.toml` | 主配置文件，可通过 Cloth Config 或 Configured 编辑 |
| `config/tlm_sincerely/auto_work_presets.json` | 自动工作预设；服务端只读种子，客户端可写 |
| `config/tlm_sincerely/auto_work_compat.json` | 自动工作兼容名单与提醒等级 |
| `config/tlm_sincerely/maid_memories/<uuid>.json` | 按女仆 UUID 存储的记忆文件 |
| `logs/tlm_sincerely/command_audit.log` | 命令执行审计日志 |

### 自动工作配置

配置段：`[multi_task]`

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `Enabled` | `true` | 全局自动调度开关；关闭只暂停调度，不清理状态 |
| `PollInterval` | `100` | 调度轮询间隔，单位 tick |
| `ExperimentalAttackPreempt` | `false` | 实验性攻击抢占 |
| `ForceEnablePreselected` | `false` | 实验性绕过兼容黑名单，但仍需检测器报告可用 |
| `DisableCompatReminder` | `false` | 关闭进服的工作兼容聊天提醒；关闭时沿用提醒等级设置 |
| `ForceBrainRefreshOnStuck` | `true` | 任务可用但无目标时强制刷新 Brain |
| `AvailableConfirmations` | `1` | 连续可用确认次数 |
| `UnavailableConfirmations` | `2` | 连续不可用确认次数 |
| `MinimumTaskHoldTicks` | `60` | 普通任务最短保持时间 |
| `ReverseSwitchWindowTicks` | `240` | 反向切换统计窗口 |
| `ReverseSwitchThreshold` | `2` | 反向切换阈值 |
| `ReverseSwitchCooldownTicks` | `200` | 反向切换冷却 |
| `BusyGuardEnabled` | `true` | 繁忙保护 |
| `BusyIdleForgiveTicks` | `60` | 繁忙状态宽限时间 |
| `BusyUnavailableHoldTicks` | `80` | 不可用任务的额外保持时间 |
| `BusyGuardMaxTicks` | `600` | 单次繁忙保护最大时长 |
| `DetectionBlockBudgetPerTick` | `256` | 每服务器 tick 农耕方块检测预算 |
| `PathCheckBudgetPerTick` | `4` | 每服务器 tick 路径可达性检测预算 |

### 跨模组联动

| 联动对象 | 是否必需 | 说明 |
|---|---|---|
| [MaidFileManager](https://github.com/zgxhzhr/MaidFileManager) | 可选 | `.maid` 女仆档案导出/导入时携带记忆数据（需其 1.4.0+ 提供的迁移 SPI） |
| Maid Self-Talk（`maid_self_talk` ≥ 1.3.1） | 可选 | 提供 `tell_maid` 转告工具所需的互聊 API；未安装时工具返回失败说明，其余功能不受影响。其互聊总开关**默认关闭**，关闭时转告同样不会发生 |

---

## 八、开发者代码索引

> 以下是当前功能的源码入口与文档入口。具体实现细节请查看对应源码包与详细文档；本 Wiki 不重复记录实现细节。

### 入口与注册

| 功能 | 入口 |
|---|---|
| Forge 生命周期、命令、网络、服务绑定 | `SincerelyMod.java` |
| TLM 附属扩展、AI Tool、Context、任务数据 | `SincerelyExtension.java` |
| 兼容检测器引导 | `priority/detection/compat/CompatDetectorBootstrap.java` |
| 网络通道注册 | `priority/autowork/network/AutoWorkNetworking.java` |
| Mixin 配置 | `src/main/resources/tlm_sincerely.mixins.json` |

### 功能模块索引

| 模块 | 主要包/类 | 详细文档 |
|---|---|---|
| 聊天栏对话 | `chatbar/`、`command/ChatCommand.java` | [docs/聊天栏女仆对话模块.md](docs/聊天栏女仆对话模块.md) |
| 女仆查找 | `chatbar/MaidFinder.java` | [docs/聊天栏女仆对话模块.md](docs/聊天栏女仆对话模块.md) |
| 自动切换调度 | `priority/TaskAutoSwitchHandler.java`、`priority/decision/` | [docs/自动切换工作模块.md](docs/自动切换工作模块.md) |
| 预设与绑定快照 | `priority/autowork/` | [docs/自动切换工作模块.md](docs/自动切换工作模块.md) |
| 工作检测 | `priority/detection/` | [docs/自动切换工作模块.md](docs/自动切换工作模块.md) |
| 兼容报告 | `priority/autowork/compat/` | [docs/自动切换工作模块.md](docs/自动切换工作模块.md) |
| 自动工作 GUI | `client/gui/autowork/` | [docs/自动切换工作模块.md](docs/自动切换工作模块.md) |
| 客户端预设库 | `client/autowork/` | [docs/自动切换工作模块.md](docs/自动切换工作模块.md) |
| 记忆系统 | `memory/`、`memory/compat/MaidFileManagerBridge.java`、`ai/tool/MaidMemoryTool.java`、`ai/context/MaidMemoryContext.java` | [docs/简易记忆系统模块.md](docs/简易记忆系统模块.md) |
| 命令执行 | `command/`、`ai/tool/MaidCommandTool.java` | [docs/女仆命令执行模块.md](docs/女仆命令执行模块.md) |
| 多目标解析 | `chatbar/AtTargetParser.java`、`chatbar/MaidFinder.java` | [docs/聊天栏女仆对话模块.md](docs/聊天栏女仆对话模块.md) |
| 女仆转告 | `ai/tool/TellMaidTool.java`、`ai/relay/SelfTalkBridge.java`、`ai/relay/RelayRateLimiter.java` | [docs/聊天栏女仆对话模块.md](docs/聊天栏女仆对话模块.md) |
| 移动指令 | `ai/tool/MaidMoveTool.java`、`ai/tool/MaidGuardTool.java`、`ai/tool/MaidWorkTimeTool.java`、`ai/tool/MaidCancelTool.java`（四个工具共用骨架 `ai/tool/MoveToolBase.java` 与目标解析 `ai/tool/MoveTargetResolver.java`）、`ai/move/MoveOrderPlacer.java`（登记一条指令的统一流程）、`ai/move/MoveNavigation.java`（走路 / 找落脚点 / 传送）、`ai/move/GuardCombat.java`（守护时设置攻击目标）、`ai/move/GoHome.java`（回家）、`ai/move/MoveLog.java`（日志小工具）、`ai/move/MoveDirectiveManager.java`、`ai/move/MoveOrders.java`、`ai/move/MoveOrderData.java`、`ai/move/MoveOrderNbt.java`（持久化标签适配）、`ai/move/FollowSuspension.java`（暂停跟随与恢复决策）、`ai/overtime/OvertimeManager.java`（加班：谁让她加班、恢复、玩家接管、跨重启）、`ai/overtime/ScheduleRelief.java`（加班开始 / 结束的纯决策）、`ai/overtime/ScheduleTimeline.java`（几种日程在一天里各时段是工作 / 休闲 / 休息的对照）、`ai/overtime/OvertimeNbt.java`（加班状态的存档读写）、`mixin/SwitchWorkTaskToolMixin.java`（切换工作任务时触发加班）、`ai/move/MoveReminder.java`（永久指令提醒计时）、`ai/move/MoveDescribe.java`、`ai/move/MoveMath.java`、`ai/move/MoveProgress.java`（卡住检测与传送预算）、`ai/move/MaidSpeech.java`（女仆自己说一句话）、`ai/context/MaidMoveContext.java`（给模型看的当前指令栏目） | 无单独文档（见「三、6. 移动指令」） |
| 配置系统 | `config/`、`client/gui/ConfigScreen.java` | 无单独文档 |
| 客户端集成 | `client/network/`、`client/jade/`、`mixin/` | 无单独文档 |

### GUI 与客户端入口

| 功能 | 入口 |
|---|---|
| 女仆 GUI 自动工作页 | `client/gui/autowork/AutoWorkConfigScreen.java` |
| 自动工作开关按钮 | `client/gui/autowork/AutoWorkVirtualTaskButton.java` |
| 侧边栏按钮 | `client/gui/autowork/AutoWorkMaidTabButton.java` |
| 本地预设库 | `client/autowork/AutoWorkClientLibrary.java` |
| 推送命令 | `client/autowork/AutoWorkPushCommand.java` |
| 快照缓存 | `client/network/ClientAutoWorkService.java` |
| Jade 提示 | `client/jade/AutoWorkJadeProvider.java` |

---

## 九、常见边界与注意事项

- 本模组不会提升玩家权限；命令执行工具只能以主人现有权限上限运行命令。
- 自动工作只调度已注册且已启用的任务；未适配任务不会被强行切入。
- 女仆的自动工作状态与真实任务相互独立：关闭自动工作不会把女仆强行切回 `idle`。
- 预设库是客户端私有数据；服务端只保留启动时的只读种子与每个女仆的绑定快照。
- 推送预设只写入接收方的预设库，不会自动改变任何女仆当前绑定。
- 只有“打开某只女仆的自动工作页并改动其预设”“重新选择预设”“新建预设”会改写该女仆的绑定；编辑其他预设、推送入库、重启游戏都不会自动改已绑定女仆。
- 记忆整理期间会静默相关对话出口，这是预期行为，用于避免整理过程被玩家看到。
- 命令确认 token 与主人绑定；不要把 token 分享给其他玩家。
- 移动指令（`maid_move` 等）的限时状态在服务端内存里，但指令与“暂停跟随前保存的原状态”会写进女仆实体的持久化标签 `tlm_sincerely:move_order`，加班状态（原日程等）会写进 `tlm_sincerely:overtime`，因此服务器重启后能重新登记（剩余时间照算）或立刻恢复原状态；守护设置的攻击目标在指令结束后由女仆自己的战斗 AI 接管。
- 需要“不要跟着主人”的指令（`stay`、`goto`、`retreat`、目标不是主人的 `approach` / `guard`）会在生效期间把女仆临时切到居家模式并清掉工作坐标，结束时**精确恢复**到她原来的状态；期间玩家在界面里 / 用跟随工具改了居家模式就会被视为接管，指令结束且不再恢复。
- 移动类指令与 `maid_work_time` 生效期间，以及玩家在非工作时间让女仆切换工作任务时，女仆会“加班”：日程临时改成“全天工作”（女仆没在睡觉时；`MoveForceWorkSchedule` 关闭后移动指令不再触发，`maid_work_time` 不受影响），最后一个触发来源结束时恢复原日程；期间玩家自己改日程同样视为接管，只丢记录、不写回。
- 移动指令生效期间（含 `maid_work_time`）以及切换工作任务触发的加班期间，自动工作调度会跳过这只女仆；结束后调度自动恢复正常。
- 移动指令“走不通就传送”有次数与距离闸门（同一条指令最多 3 次、相邻两次间隔 ≥5 秒、距离上限见 `MoveTeleportMaxDistance`）；传送不能阻止女仆随后被跟随等行为拉走，`goto` / `retreat` 与普通跟随模式的冲突见「三、6. 当前边界」。
- 若需要排查兼容问题，优先使用 `/tlmautowork compat report`，而不是直接修改预设。
