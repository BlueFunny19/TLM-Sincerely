# 开发手册与注意事项

> 注记：本文件自 `0da3fbe`（分支重命名 1.21.1→1.21.1neo 主支、main→1.20.1forge 维护支）起与 `1.21.1neo` 分支分叉，内容以本分支（Forge 1.20.1）为主；平台无关的通用教训可在两条分支间 cherry-pick 互移（口径见 AGENTS.md「文档分层规约」）。

## 项目结构

```
src/main/java/com/github/tartaricacid/tlm_sincerely/
├── SincerelyExtension.java      # 入口类，实现 ILittleMaid
├── ai/                          # AI Context 与 Tool
├── chatbar/
│   ├── ChatBarHandler.java       # 聊天栏事件与前缀解析
│   └── MaidFinder.java           # 女仆查找逻辑
├── client/
│   ├── gui/autowork/             # 自动工作 GUI：独立配置 Screen、棕色按钮、底部开关
│   └── network/ClientAutoWorkService.java  # S2C 快照客户端缓存
├── command/
│   ├── ChatCommand.java          # 对话命令
│   ├── MemoryCommand.java        # 记忆管理命令
│   ├── ConfirmCommand.java       # /tlmconfirm 命令确认
│   ├── CommandClassifier.java    # 命令名单双重判定
│   ├── MaidCommandExecutor.java  # 主人身份命令执行
│   ├── CommandConfirmationService.java  # 确认挂起与会话信任
│   └── UnicodeWordArgument.java  # 中文参数支持
├── memory/                       # 女仆记忆持久化
├── mixin/                        # Mixin（客户端 + 通用）
└── priority/
    ├── autowork/                  # 自动工作模式：状态/预设/网络/菜单/检测/决策
    └── detection/                 # 通用任务检测（农耕/甘蔗/攻击）
src/main/resources/
├── META-INF/mods.toml           # 模组元数据
├── assets/tlm_sincerely/lang/   # 国际化文件
├── pack.mcmeta                  # 资源包元数据
└── tlm_sincerely.mixins.json   # Mixin 配置
```

---

## 构建命令

**环境要求**：Java 17（系统默认 Java 25 会报错）

```powershell
# 设置 Java 17 环境
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot"

# 构建
.\gradlew.bat build --no-daemon

# 输出位置
build/libs/tlm_sincerely-1.20.1-forge-0.2.0-beta.jar
```

---

## 测试命令

```powershell
# 启动测试客户端（首次较慢，需下载资源）
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot"
.\gradlew.bat runClient --no-daemon

# 或双击项目根目录 runClient.bat
```

`copyModsToLocalRun` 仅为历史兼容空任务，不再复制任何模组。开发依赖统一通过 `runtimeOnly fg.deobf(...)` 加载。

---

### 女仆 API

主模组提供的关键 API：
- `EntityMaid` - 女仆实体类
- `MaidAIChatManager.chat()` - AI 对话接口
- `ChatClientInfo` - 聊天客户端信息
- `ILittleMaid` - 附属扩展接口

---

## API 参考（模组作者本机环境）

**主模组开发文档**：`wiki-reference/docs/wiki/dev/`

关键文件：
- `如何开始.md` - 入口注册方式
- `ai/overview.md` - AI 系统概述
- `ai/context.md` - 上下文注册

**主模组源码查询**（本机不保留源码副本，按需查询，从最优到兜底）：

1. **依赖 jar 反编译（首选，最贴近实际编译产物）**：`compileJava` 后主模组 jar 已在 Gradle 缓存：
   `~/.gradle/caches/modules-2/files-2.1/maven.modrinth/touhou-little-maid/1.5.3-forge+mc1.20.1/.../touhou-little-maid-1.5.3-forge+mc1.20.1.jar`
   IDE（IntelliJ 内置反编译器）搜索类名可直接阅读；需要字节码级证据（常量、方法体）时用：
   `javap -p -c -constants -classpath <jar> <全限定类名>`
   这是唯一能精确对应 1.5.3 release 的途径（GitHub `1.20` 分支是快照开发线，可能领先/滞后于正式版）。
2. **浅克隆官方仓库（需要跨文件浏览、git 历史时）**：
   `git clone --depth 1 --branch 1.20 https://github.com/TartaricAcid/TouhouLittleMaid.git`
   对应版本为 `1.5.3-forge`；如需更新 `git -C <目录> pull`。clone 后可用 rg/IDE 全文检索。
3. **GitHub 网页端**（仅看单个文件、不想 clone 时）：`https://github.com/TartaricAcid/TouhouLittleMaid/tree/1.20/src/main/java/...` 或按路径直达文件。
4. **运行目录兜底**：`run/mods/` 中的生产 jar 或开发环境 `run/` 下的 mods，无 Maven 缓存时可用反编译工具打开。

> 注意：GitHub 上该仓库只有 snapshot 预发布 tag，正式版（release）发布在 Modrinth。查询与当前依赖版本严格对应的实现时，一律以第 1 条（依赖 jar）为准；GitHub 源码仅用于理解结构与实现意图。

---

## 操作红线

### 1. 禁止删除整个 `run/` 目录
清理测试环境时**只删目标文件**，不要删除整个 `run/` 目录：
- ✓ 可删：`run/config/tlm_sincerely*`、`run/crash-reports/*`
- ✗ 不可删：`run/` 整体、`run/config/`、`run/mods/`、`run/logs/`

删除 `run/` 会丢失第三方 mod 的缓存配置（Embeddium、Configured 等），导致启动异常。

### 2. 修改 build.gradle 时区分"专属配置"和"共享基础设施"
以下两项是 **Mixin 框架全局 JVM 参数**，与是否使用自己的 Mixin 无关，不能删除：
```groovy
property 'mixin.env.remapRefMap', 'true'
property 'mixin.env.refMapRemappingFile', "${projectDir}/build/createSrgToMcp/output.srg"
```
所有使用 Mixin 的第三方 mod（Embeddium、Create、Sophisticated Backpacks 等）都依赖它们做方法名重映射。

### 3. 修改 build.gradle 后用 `git diff` 核对改动
每次修改完 `build.gradle`，检查 diff 确保没有误删共享依赖或配置项。

## 开发笔记与常见事项

### 配置冲突 `Config conflict detected!`
- **原因**：`@Mod` 和 `@LittleMaidExtension` 注解共存导致双重实例化
- **解决**：使用静态布尔值 `configRegistered` 防止重复注册

### Embeddium 兼容性
- `mixin.env.remapRefMap` 缺失时，Embeddium 的 `DrawContextMixin` 会抛出 `InvalidInjectionException`
- 该错误堆栈指向 Embeddium，但根因是 build.gradle 缺少 Mixin 全局 JVM 参数

### 自定义 ArgumentType 必须注册序列化器
- **现象**：自定义 `ArgumentType` 编译通过但进存档报 `"无效的玩家数据"`
- **日志关键行**：`Unrecognized argument type ... at ArgumentTypeInfos.byClass()`
- **根因**：Minecraft 在玩家登录时将命令树序列化发送给客户端，`ArgumentTypeInfos` 找不到对应类的序列化器 → 抛异常 → `ServerLoginPacketListenerImpl` 终止登录
- **解决**：在 Mod 构造器中早于命令注册时调用：
  ```java
  ArgumentTypeInfos.registerByClass(
      MyArgType.class,
      SingletonArgumentInfo.contextFree(() -> new MyArgType())
  );
  ```
- **注意**：`SingletonArgumentInfo.contextFree(Supplier)` 比 `new SingletonArgumentInfo<>(Supplier)` 类型推断更稳定

### Brigadier 1.1.8 不支持 Unicode 参数（MC 1.20.1）
- **现象**：命令中传入中文/非 ASCII 参数时报 `"参数后应有空格分隔，但发现了紧邻的数据"`
- **根因**：Brigadier 1.1.8 的 `StringReader.isAllowedInUnquotedString()` 只允许 `0-9A-Za-z_-.+`。`StringArgumentType.string()` 内部调用 `readUnquotedString()`，遇中文字符立即停止读取，参数值为空串
- **验证**：Minecraft bug [MC-260354](https://bugs.mojang.com/browse/MC-260354)，1.20.2 才修复
- **解决**：自建参数类型，逐字符读取直到空格（不依赖 `isAllowedInUnquotedString`）：
  ```java
  public class UnicodeWordArgument implements ArgumentType<String> {
      @Override
      public String parse(StringReader reader) {
          final int start = reader.getCursor();
          while (reader.canRead() && reader.peek() != ' ') {
              reader.skip();
          }
          return reader.getString().substring(start, reader.getCursor());
      }
  }
  ```
- **注意**：自定义类型同样需要 `ArgumentTypeInfos.registerByClass()` 注册序列化器

### Brigadier 命令树：greedyString 与 literal 同层歧义
- **现象**：命令树中 `greedyString` 和 `literal` 作为同一父节点的子节点时，部分路径无法命中或报 `"参数后应有空格分隔"`
- **根因**：`greedyString` 吞掉全部剩余输入，同一层的 `literal` 子节点永远无法被匹配到。Brigadier 的多路径解析会产生歧义
- **解决**：将 `core`/`archive` 从子字面量提升为独立子命令（如 `set` → archive，`set-core` → core），确保每个分支末端只有 `greedyString` 单一终端
- **反面模式**：
  ```java
  // ❌ 错误：greedyString 与 literal 同层
  .then(argument("value", greedyString())
      .executes(defaultAction)
      .then(literal("core").executes(coreAction)))
  
  // ✅ 正确：literal 在单独分支
  .then(argument("value", greedyString()).executes(defaultAction))
  .then(literal("core")
      .then(argument("value", greedyString()).executes(coreAction)))
  ```
### compatibilityLevel 必须匹配 Mixin 版本
Mixin 0.8.5 最大支持 `JAVA_13`，不能写 `JAVA_17`。写错会导致 Mixin 类不加载。
```json
{ "compatibilityLevel": "JAVA_8" }
```

### 自定义方法需要 `remap = false`
- 主模组/第三方 mod 的自定义方法（非原版 Minecraft）**没有 SRG 映射**
- `@Inject(method = "...", remap = false)` — 不加会编译失败：`Unable to locate obfuscation mapping`
- `@Accessor` 也不需要特殊处理（自定义字段名不会被 remap）

### `@Redirect` target 引用原版方法时的 remap
`ServerPlayer.sendSystemMessage(Component)` 是**原版方法**（SRG: `m_213846_`），不是 Forge 补丁方法。
- **关键**：mixin 到主模组类时 `@Mixin(remap = false)` 会使类内**所有**注入默认不 remap，包括 `@At` target 中的原版方法引用
- 若 target 引用原版方法但 remap=false，refmap 不生成映射条目 -> 生产 jar 中字节码是 SRG 名（`m_213846_`），target 仍是 MCP 名（`sendSystemMessage`）-> **注入不命中 -> 启动崩溃**
- **解决方案**：对 target 引用原版方法的注入，显式设 `@Redirect(remap = true)` 覆盖 `@Mixin(remap = false)`，让 AP 仅为该注入生成 refmap 条目（已验证：mod 类方法名不会被误映射，AP 自动跳过无 SRG 条目的方法）

### mixingradle refmap 路径
- 生成位置：`build/tmp/compileJava/compileJava-refmap.json`
- `add sourceSets.main` 会自动复制到 jar 和 classpath
- dev 环境 WARN "could not read refmap" 可忽略（Parchment 环境不需要）
- jar 出现 refmap 重复时加 `duplicatesStrategy = DuplicatesStrategy.EXCLUDE`

### 编译失败排除
- `error: package org.spongepowered.asm.mixin.injection.wrap does not exist` → `@WrapOperation` 来自 MixinExtras，不包含在 `mixingradle 0.7 + Mixin 0.8.5` 中。需要额外添加 MixinExtras 依赖或改用 `@Redirect`
- `Class version 61 required is higher than the class version supported (JAVA_8 supports class version 52)` → 无影响的 WARN，仅表示 Mixin 运行时 class version 高于声明的 compatibilityLevel

### Mixin 到主模组（TLM）自定义类时的 remap 处理
- **现象**：mixin 到 TLM 的类（如 `MaidAIChatManager`、`ChatBubbleManager`）时，注入的方法名在 refmap 中找不到映射
- **解决**：在 `@Mixin` 注解上设 `remap = false`（使类内所有注入默认不 remap）
- **例外**：若注入的 `@At` target 引用**原版 Minecraft 方法**（如 `ServerPlayer.sendSystemMessage`），需在该注入上显式设 `remap = true`，否则生产 jar 中 target 不命中 -> 启动崩溃（详见上一节）
- **判断规则**：target 引用 mod 类方法 -> remap=false 安全；target 引用 Minecraft 类方法 -> 必须 remap=true

### javap 输出换行截断导致方法签名误读
- **现象**：`javap -p` 输出跨行截断，长参数列表易误读参数个数
- **案例**：`MaidAIChatManager.tts` 实际 4 参数，跨行显示被误读为 5 参数，导致 mixin handler 参数不匹配崩溃
- **解决**：关键签名应单独 javap 确认，不依赖跨行截断的输出

### `@Redirect` target 描述符格式
- 格式：`L<owner>;method_name(descriptor)V`
- 泛型擦除：`List<LLMMessage>` 描述符为 `Ljava/util/List;`

### `@Inject` HEAD cancellable 静默拦截
- void 方法用 `CallbackInfo`，返回值方法用 `CallbackInfoReturnable<T>`
- 用于维护轮静默：拦截气泡、TTS 等输出通道

### 跨 GUI 注入 widget（T-2 修正）
 - TLM 的 `MaidContainerGuiEvent.Init.addButton(String, AbstractWidget)` 是安全的 widget 注入点，无需反射 `addRenderableWidget`
 - **旧方案**曾用 `@Invoker` 或反射注入到 `addTaskListButton`，但会在 `initBaseWidgets` 之前执行，导致 `scheduleButton` 未初始化时 `renderTooltip` 触发 NPE
 - 结论：凡是在女仆 GUI 中追加 widget，一律使用 `MaidContainerGuiEvent.Init` 事件，不要在 `addTaskListButton` 的 HEAD mixin 中操作

### MaidContainerGuiEvent 触发时机（T-2 修正）
 - TLM 在 `initBaseWidgets()` **之后** post `MaidContainerGuiEvent.Init`，此时 `scheduleButton` 等基础 widget 已就绪
 - Init 事件的 `addButton` 会被 TLM 自动 `addRenderableWidget`
 - 如果在 `addTaskListButton` 的 HEAD mixin 里添加 widget，会先于 `scheduleButton` 创建，导致渲染 NPE

### Screen.getFocused / setFocused 实际所在类（补充）
 - `javap -p Screen` 看不到 `getFocused` / `setFocused`，实际定义在父类 `AbstractContainerEventHandler`
 - SRG 名 `m_7222_` / `m_7522_`，编译时正常解析；MCP 映射在 decompile jar 中可能被裁剪
 - 调用方式：`Screen.getFocused()` 返回 `GuiEventListener`，`Screen.setFocused(GuiEventListener)` 设置焦点

### 接口 Mixin 在 Mixin 0.8.5 中不受支持
 - **现象**：`@Mixin(ContainerEventHandler.class)` + `@Inject(method = "charTyped")` 在 compileJava 时报 `Injector in interface is unsupported`
 - **根因**：Mixin 0.8.5 的注解处理器不支持在接口 Mixin 中注入处理器（仅支持类 Mixin）
 - **解决**：改用 Forge `ScreenEvent.CharacterTyped.Pre` 客户端事件，在事件处理器中检查屏幕类型并转发字符输入

### 颜色 ARGB 格式
 - `GuiGraphics.drawString` 的颜色参数是完整的 ARGB 格式
 - `0x3380FF` 是半透明蓝色（alpha=0x33），`0xFF3380FF` 才是不透明蓝色
 - `0xFF5555` 是半透明红色，`0xFFFF5555` 才是不透明红色
 - 结论：GUI 文本颜色必须使用 `0xFF` 开头的完整 ARGB，否则字体透明不可见

### TLM 顶部 Tab 扩展路线
 - 顶部水平 Tab 由 `MaidTabs#getTabs(AbstractMaidContainerGui)` 返回 `MaidTabButton[]` 数组
 - 通过 Mixin `@Inject(method = "getTabs", at = @At("RETURN"), cancellable = true, remap = false)` 追加新 Tab
 - 已用槽位：0:107(MAIN)、1:132(TASK_CONFIG)、2:157(MAID_CONFIG)；第 4 槽用 u=182
 - 按钮构造：`MaidTabButton(x, y, left, String key, OnPress)`，tooltip 自动取 `gui.touhou_little_maid.button.<key>` / `<key>.desc`
 - 独立配置页面应继承 `AbstractMaidContainerGui`，不应继承 `MaidTaskConfigGui`（否则 TLM 会同时高亮两个 Tab）

### 独立菜单 MenuType 注册范式（参照 maid_useful_task）
 - 服务端：`DeferredRegister<MenuType<?>>` + `IForgeMenuType.create((wid, inv, buf) -> new Container(wid, inv, buf.readInt()))`
 - 客户端：`FMLClientSetupEvent.enqueueWork(() -> MenuScreens.register(menuType, Screen::new))`
 - 开屏：`NetworkHooks.openScreen(serverPlayer, MenuProvider, buf -> buf.writeInt(entityId))`
 - 容器：继承 `TaskConfigContainer` 可自动获取物品栏槽位、owner 校验等基础功能

### getXSize() ≠ 任务配置内容区宽度
 - TLM 女仆 GUI 总宽 `imageWidth = 256`（`getXSize()` 返回）
 - 右侧任务配置内容区固定为 `176 × 137`，起始于 `leftPos + 80, topPos + 28`
 - 若用 `getXSize()` 作为覆盖层宽度，会越出女仆 GUI 边界
 - 结论：覆盖层/独立配置页必须硬编码 `176 × 137`

### TLM Tooltip 渲染的 scheduleButton 空指针
 - `AbstractMaidContainerGui.renderTooltip` 无条件访问 `scheduleButton.isHovered()`
 - 若在 GUI 重建过程中有帧渲染到此方法，`scheduleButton` 尚未初始化会触发 NPE
 - 解决：在 `AbstractMaidContainerGuiMixin` 中对 `renderTooltip` HEAD 注入空值保护
 - 关键坑：`renderTooltip` 是 override 自 vanilla 的方法，运行时被混淆为 `m_280072_`，注入点**不能写 `remap = false`**（去掉后走 refmap 自动映射）。`remap = false` 只在 userdev 里能过（编译期名字是 `renderTooltip`），发布版必崩 `could not find any targets matching 'renderTooltip'`；而 TLM 自有方法（`taskButtonPressed`、`getTabs`）运行时不混淆，才应保留 `remap = false`

### FMLCommonSetupEvent 必须注册到 mod event bus
  - `SincerelyExtension` 构造器中的 `MinecraftForge.EVENT_BUS` 是 Forge 事件总线，不会收到 `FMLCommonSetupEvent`
  - 必须显式 `FMLJavaModLoadingContext.get().getModEventBus().addListener(this::onCommonSetup)`
  - 否则 `AutoWorkNetworking.register()` 永远不执行，channel 为 null

### Compat 报告点击命令对玩家也需开放
  - 默认 `/tlmautowork` 根命令要求权限 2，但兼容报告是 **阅读型** 入口，分类计数点击按钮继承根权限 → 普通玩家点不动
  - **解法**：根命令去掉 `requires` 限制，把 `requires(2)` 移到具体管理子命令（`blacklist`、`whitelist`、`set reminder`、`reload`）
  - 旧聊天中的 `/tlmautowork compat report <page>` 链接仍能直接点击
  - 警示：根命令放空权限后，OP 只能从“查看报告”点出，**改名单仍需 OP**；需要双重检查 Brigadier literal 分支

### 决策引擎不能被"当前任务 UNKNOWN"无条件阻断
  - **症状**：自动工作完全失效；女仆有确认过的 `AVAILABLE` 候选，仍不切换
  - **根因**：`TaskSwitchDecisionEngine.evaluateNormalSwitch` 中两处 `UNKNOWN → return` 分支会无视候选
    - `:118` `EXTERNAL_UNKNOWN_CURRENT`：当前任务不在 preset 且非 idle
    - `:133`：当前任务在 preset 但 `UNKNOWN`（无 Detector / 兼容策略排除 / 扫描中）
  - **解法（平衡）**：
    1. preset 外 UNKNOWN → 自动工作已启用 + 候选确认 → 允许接管，reason `EXTERNAL_UNKNOWN_REPLACED`
    2. preset 内 UNKNOWN 但仍可用 → 仅当候选更高优先级可切换，reason `HIGHER_PRIORITY_OVER_UNKNOWN`
    3. preset 内已失效（未注册/disabled/被兼容策略排除）→ 任意确认候选可替换，reason `CURRENT_UNSUPPORTED_OR_DISABLED`
    4. 保留 AVAILABLE 优先级保护 + UNAVAILABLE 确认次数 + 反向切换冷却
  - **诊断依据**：将成功切换日志从 DEBUG 提升到 INFO，方便直接观察；reason 字段改为可读枚举（`CURRENT_AVAILABLE`/`HIGHER_PRIORITY_AVAILABLE`/`CURRENT_UNAVAILABLE_CONFIRMING`/`CURRENT_UNKNOWN_KEEP`/`EXTERNAL_CURRENT_REPLACED` 等）

### TaskScanCursor.matches 不应比较 center（follow 模式）
  - **症状**：跟随模式下女仆每移动 4 格就重置整个增量扫描，扫描永完不成
  - **根因**：`matches()` 包含 `center.equals(newCenter)` 或距离比较；非 Home 时 center 随女仆移动变化，每 tick matches 失败 → cursor 重新 start → ring=0
  - **解法**：`matches()` 只比较 `homeMode/horizontalRange/verticalRange`，不比较 center
  - **代价**：cursor 中心在扫描开始时固定，整轮不刷新；正确语义是"扫完这一轮再换中心"，而不是"持续追踪新位置"
  - **联动**：`PATH_BUDGET_EXHAUSTED` 时保存 `cursor.next()`（跳过当前候选），避免密集候选区（树冠、农场）反复卡在同一位置

### Busy guard：Brain memory 作为"正在干活"证据（T-2）
  - **症状**：`maid_useful_task:maid_tree` 砍树途中被其他 AVAILABLE 普通任务切走；TreeDetector 全扫一轮附近无自然树即报 `UNAVAILABLE`，确认 2 次后任何确认候选都能接管
  - **机制**：`TaskAutoSwitchHandler.observeMaidBusy` 每 tick 观察 `ATTACK_TARGET / WALK_TARGET / PATH / InitEntities.TARGET_POS` 任一 PRESENT → `MaidSwitchState.markBusyObserved`；`TaskSwitchDecisionEngine.evaluateNormalSwitch` 最前经 `suppressedByBusyGuard` 拦截正常切换。观察不依赖切换历史：从未被 auto-switch 切换过的女仆（首次启用、手动/外部设置的任务）同样受保护
  - **不卡死的三个边界**：brain 空闲超过 `BusyIdleForgiveTicks`(60) 放行并结束本次 busy period；`UNAVAILABLE` 确认数达标后最多再 hold `BusyUnavailableHoldTicks`(80)；每段连续 busy period 最多保护 `BusyGuardMaxTicks`(600)，后续经过空闲期再次工作会建立新的 `busyStartTick`
  - **关键约束**：idle 任务必须显式排除（idle 闲逛也有 WALK_TARGET）；攻击抢占走 `handleExperimentalAttackPreempt` 独立路径天然不受影响；`recordSwitch`/`resetForTickRegression` 必须 `resetBusyGuard()`，否则旧任务 busy 证据残留
  - **诊断**：guard 阻止时 `[TaskStability] ... reason=BUSY_GUARD_ACTIVE busyLastTick=... unavailableSinceTick=...`（debug 级）

### MSK 公共 Handler API 与 `IFarmTask` 不能互通
  - **症状**：maidsoulkitchen 0.3.0.9 的 `TaskBerryFarm`/`TaskFruitFarm` 继承自己 `ICompatFarmTask<Handler>`，不是 TLM `IFarmTask` → `instanceof IFarmTask` 检测器恒为 false
  - **根因**：MSK 农场任务用动态 Handler 责任链，但历史代理按 TLM `IFarmTask` 写判定，永远不命中
  - **解法**：
    1. `supports()` 改为 `task instanceof ICompatFarmTask<?>`；handler 链通过 `ICompatFarmTask.getCompatHandler(maid)` 读取
    2. `compileOnly` MSK deobf JAR，避免 `runtimeOnly` 缺失时崩服
    3. Bootstrap 注册时对 `ICompatFarmTask`/`ICompatFarmHandler`/`BerryFruitData` 三个类做 `Class.forName` 存在性检查
  - **副作用**：`getCompatHandler` 可能构建静态 Handler 集合，缓存 handler 引用避免反复构造；版本变化时**只读取** `canHarvest`，禁止写 Brain 缓存

### 资源反向反编译残留物不要提交
  - 调研时为了字节码证据会在 `studio/`、`com/`、`META-INF/` 临时解压依赖 JAR
  - `.gitignore` 必须显式忽略这些目录；本轮补 `/studio/`、`/com/`、`/META-INF/`、`/assets/`、`/data/`
  - 提交前 `git status` 检查是否有新未追踪的 `*.class`、`.class.json`、附属 mod 资源目录

### 硬性工具需求：Detector 允许背包 + 切换前装备
  - **症状**：fishing/shears/extinguishing 检测器只认主手工具，背包有钓鱼竿/剪刀/灭火器仍报 `UNAVAILABLE`，女仆永远不切换
  - **机制**：`HardToolRequirement` + `MaidHardToolService`（`hasAny`/`findBest` 只读查询，`equipTaskRequirement` 装备）；检测器改用 `hasAny` 判定，`CompatDetectorBootstrap` 注册 `taskUid -> 需求` 映射
  - **装备语义**：按 TLM 1.5.3 `TaskEquipUtil.tryEquipFromBackpack` 的相同算法，只操作 `getAvailableBackpackInv()`（不含手部槽），整槽提取目标工具、将原主手写回该槽，再设置新主手。项目声明兼容 TLM ≥1.5.3，因此不直接链接可能晚于最低版本出现的工具类
  - **接线位置**：`TaskSwitchDecisionEngine.switchTo` 在 `maid.setTask` 前调用 `equipTaskRequirement(maid, targetUid)`；若检测缓存过期期间工具已被移走并返回 `MISSING`，本次切换中止。自动 `setTask` 不触发 TLM 的 `onFunctionCallSwitch` 装备回调，所以必须显式装备
  - **不要纳入**：honey（双分支非单一强制，瓶子背包直耗）、locate（主手物品决定目标派生方式，盲装备会改变行为）
  - **边界**：只注册 fishing/shears/extinguishing 三种单一硬需求；`CombinedInvWrapper` 位于 `net.minecraftforge.items.wrapper`，且 `IItemHandler` 本身没有 `setStackInSlot`

### MSM 存储目标不能用 default_storage_blocks tag 代替
  - **症状**：`storage_manage` 长期 `BLOCK_BUDGET_EXHAUSTED` / `FULL_SCAN_NO_STORAGE`，从未返回 `AVAILABLE`，但 MSM 自身能识别并使用仓库
  - **根因**：真实 `PlaceMoveBehavior` 优先读取 `ViewedInventoryMemory.positionFlatten()`；`ItemHandlerStorage.isValidTarget` 只要求 BlockEntity 暴露 `ITEM_HANDLER`。`default_storage_blocks` tag 用于选择交互位置，不是仓库有效性条件
  - **解法**：Detector 反射读取 `VIEWED_INVENTORY` 并优先检查已查看仓库；有效性移除 tag 限制；本地 fallback 垂直范围由 ±7 收窄为附属实际五层扫描

### MSM 版本必须 ≥1.15.x，且 Storage 改名 Target 要双路径兼容
  - **症状**：runClient 中女仆一切入 `storage_manage` 的 PLACE 行为即崩服：`ChatBubbleMgrMixin` 的 `@Shadow getEndTime` 在 TLM 1.5.3 的 `ChatBubbleManger`（已重写为弃用壳）中不存在
  - **根因**：MSM 1.4.1 自身与 TLM 1.5.3 不兼容，与调用方 Detector 无关；MSM 1.15.3 明确适配 TLM 新 AI 系统，1.15.x 的 mixins.json 已删除该条目
  - **解法**：`build.gradle` 升级到 `curse.maven:maid-storage-manager-1210244:7976861`（1.15.6）；Detector 反射 `storage.Storage#getPos` 改为先试旧类、缺失则用 `storage.Target#getPos`（字段方法签名相同）
  - **注意**：MSK 的 cook/berry 反射类不受 MSM 升级影响（MSM 不内嵌 MSK）；升级后 storage_manage 保持可调度，仍需实机验证 PLACE/RESORT 路径

### 灭火只灭生物身上的火，不处理地面火焰
  - TLM `MaidExtinguishingTask.start` 三分支：着火主人（2 格内直接灭，否则走近）→ 女仆自身 → AABB 膨胀 (2,1,2) 内着火驯服动物；地面火焰与着火敌对生物从不在语义内
  - Detector 与之一致：地面点火测试报 `NO_FIRE_TARGET` 是正确行为，不是检测失效；文档已在兼容矩阵注明该边界

### 农耕可达性必须对齐 MaidPathFindingBFS，且区分单点 / 包围盒
  - **症状**：成熟浆果、可可、水果存在，但 Detector 持续返回 `FULL_SCAN_ALL_UNREACHABLE`，实机日志无 `AVAILABLE`
  - **根因**：
    1. `EntityMaid.canPathReach` 的 A* 要求目标格本身是寻路节点，而浆果丛（`SWEET_BERRY_BUSH → DAMAGE_OTHER`）、可可（`COCOA`）、树叶（`LEAVES`）的 malus 都是 -1，永远不会成为节点 → 单点检查恒 false。
    2. TLM/MSK 的真实 AI 用的是 `MaidPathFindingBFS`，而且不同任务语义不同：`MaidFarmMoveTask`（`IFarmTask` 默认脑）只查基准点单点；`MaidFarmSurroundingMoveTask`（TLM 的 `cocoa`、`melon`）查基准点周围 `(-1,0,-1)..(1,1,1)` 的 3×2×3 任一格；MSK `MaidCompatFruitMoveTask` 查的是 `crop.below(searchYOffset)` 这个**基准点**单点，范围/主人距离也判在基准点。
  - **解法**：统一用 `FarmReach.canReach(map, pos, surrounding)`；每次 Detector 扫描复用一个 `MaidPathFindingBFS` 并在 finally 里 `finish()`；路径预算只在命中成熟候选后消费。
  - **坑**：`TaskScanCursor` 一旦绕过中心就会在旧中心扫描；home 模式必须把中心纳入 `matches`（跟随模式中心随女仆移动，不能纳入，否则每 tick 重扫）。
  - **坑**：路径预算耗尽时不要把游标推进到 `nextCursor`，否则该候选整轮被永久跳过；应保持原游标下个 tick 重试。

### `@Mod` 与 `@LittleMaidExtension` 必须拆成两个类
  - TLM 1.5.3 会在 `FMLCommonSetupEvent` 中扫描 `@LittleMaidExtension`，并通过反射再次创建扩展实例；同一个类同时标注 `@Mod` 会导致 Forge 事件监听器、命令和生命周期处理重复注册/执行
  - `SincerelyMod` 只负责 Forge common 配置、mod/Forge event bus、Menu、network 与 server lifecycle
  - `SincerelyExtension` 只负责 `ILittleMaid` 的 Tool、Context、TaskData 等扩展注册
  - client-only 配置界面注册必须放在 `DistExecutor` / `Dist.CLIENT` 分支，公共 `@Mod` 构造路径不能直接解析 Cloth Config GUI 类

### 异步 LLM 维护不能用 ThreadLocal 充当完整请求身份
  - `ThreadLocal` 只能标记同步创建 callback 的瞬间；TLM 的 LLM、Tool 与 history summary 都会跨异步边界
  - TLM 1.5.3 的 `LLMCallback` 在构造时持有同一 `messages` 列表，工具续链复用该对象；可用 callback identity + messages identity 标记整个请求链
  - `HistorySummaryCallback` 与 `AutoGenSettingCallback` 覆写 `onSuccess`/`onFailure`，Mixin 到基类终态方法不会自动覆盖子类覆写；必须分别核实并接线
  - 普通聊天在 history summary 阶段尚未创建最终 callback，因此还要在 `MaidAIChatManager.chat` 的 HEAD/RETURN 跟踪 pending submission，避免维护与摘要后恢复的普通聊天并发
  - 维护超时必须终止当前代次并清除 snapshot/chain；旧 callback 迟到时只能识别为过期请求，不能清理或修改新一轮维护状态

### 维护历史清理必须按消息对象身份，不按长度截断
  - 仅保存 `history.size()` 并删除尾部/头部 N 条会误删维护期间完成的普通对话，也可能残留超时后到达的维护回复
  - 启动维护时保存原 `LLMMessage` 对象的 identity set，结束时只删除不在原集合中的消息
  - snapshot 必须与维护代次绑定或在 timeout 时完整废弃，不能让旧 callback 使用同 UUID 的新 snapshot

### 记忆保存采用“副本修改、落盘成功再提交缓存”
  - `load()` 返回缓存对象副本，调用方不能直接污染共享 cache
  - 保存时按 maid UUID 串行，先把 candidate 写入同目录随机临时文件，再 `ATOMIC_MOVE + REPLACE_EXISTING`；仅成功后替换 cache
  - 保存失败必须保留旧 cache 与旧目标文件，并在 finally best-effort 删除临时文件
  - 损坏/非对象 JSON 要隔离为 `.corrupted.<timestamp>`，不能静默返回空对象后覆盖原文件
  - `ConcurrentHashMap<UUID, MaidMemory>` 只保护映射，不保护 `MaidMemory` 内部 `LinkedHashMap`；不能把共享可变对象直接暴露给异步 Tool

### 自动任务换装与 `setTask` 必须是同一事务
  - 工具交换应快照“完整主手 + 完整目标槽”，整槽提取后交换；不能提取 1 个再覆盖目标槽剩余堆叠
  - `maid.setTask(targetTask)` 抛异常时必须恢复原主手与原槽，否则会出现“任务未切换但工具已变更”
  - 装备阶段内部异常和后续 `setTask` 异常都要走同一个 rollback contract

### 检测缓存中的 UNKNOWN 不能重置或创建 UNAVAILABLE 计时
  - `UNAVAILABLE` 开始/延续 busy guard 的不可用计时
  - `AVAILABLE` 明确清除计时
  - `UNKNOWN/EXPIRED` 只能保留同任务已有计时，不能自行创建计时，也不能把短暂缓存过期解释成任务恢复可用

### 命令执行的名单判定必须沿 `getChild()` 递归（1.5.3 基线核实）
- **构造**：原版 `ExecuteCommand` 用 `literal("run").redirect(dispatcher.getRoot())` 注册 run；实体/条件分支用 `fork` / `redirect`（`redirect` 与 `fork` 都会在节点上设置 redirect）
- **Brigadier 1.1.8 行为**（`CommandDispatcher.parseNodes`）：子节点带 redirect 时，会新建一个以 redirect 目标为根的 `CommandContextBuilder` 并 `context.withChild(...)`，然后**立即返回外层 context**；因此 `ParseResults#getContext()` 只暴露最外层节点（如 `[execute]`），`run` 之后的命令节点全部在 child 链上
- **结论**：判定必须 `getChild()` 递归收集 LiteralCommandNode 名字，否则 `/execute run kill @e` 只能看到 `execute`
- **验证方式**（无需启动游戏）：把编译产物 + brigadier-1.1.8.jar + forge recomp jar 加入 classpath，用反射调用 `CommandClassifier.collectContextNames` 与 `scanTextNames`，对样例集断言：
  - 树判定：`execute run kill @e → [execute, run, kill]`；`execute as Steve run tlmchat hi → [execute, as, run, tlmchat]`；`execute as Steve run execute run kill @e → [execute, as, run, kill]`；`give @s diamond 1 → [give]`
  - 文本判定：`minecraft:kill ... → kill`；`execute in minecraft:the_nether run fill ... → fill`；`tlmconfirm run <uuid> → tlmconfirm`
  - 注意探针里实体选择器参数要用 `word()` 能解析的写法（`@a` 含 `@` 会被 Brigadier 的 unquoted string 拒绝），否则是探针失真而非代码问题
- **文本兜底不可省**：命名空间写法、解析失败、模组命令别名都要靠原文扫描兜住

### 两个 chat HEAD 注入的顺序与 `ci.isCancelled()` 语义
- 同一方法同一注入点的多个 `@Inject` 处理器**共用一个 `CallbackInfo` 实例**；`CallbackInfo#isCancelled()` 是 Mixin 0.8.5 的公开方法（已 javap 核实）
- 处理器执行顺序由 mixin 应用顺序（配置顺序/priority）决定，不是稳定契约；本项目的 `ChatMaintenanceGuardMixin`（维护忙线）**不检查** `isCancelled()`，因此 `CommandConfirmationChatGuardMixin`（命令确认忙线）采取双保险：
  1. 自身 `ci.isCancelled()` 先返回，避免与已取消方重复发言
  2. 维护忙线激活时（`isMaintaining && !isInternalMaintenanceCall`）直接返回，把提示让给维护 Guard，从而**与注入顺序无关**
- 新增注入排在 `ChatMaintenanceGuardMixin` 之后（mixins.json 顺序）作为额外的顺序约定
- 内部维护调用（`MemoryMaintenanceManager.isInternalMaintenanceCall()`）不拦截，避免打断后台整理

### 命令确认挂起的生命周期与 future 完成
- `CommandConfirmationService` 的 pending 表、会话信任、tick 扫描全部只在服务端线程访问；`LLMCallback.addToolResult` 有主线程断言，因此**所有完成路径都必须在服务端线程**：`/tlmconfirm` 命令、tick 扫描、登出事件、停服事件
- 完成顺序固定为：先 `callback.addToolResult(text, toolCallId)`，再 `future.complete(callback)`；future 完成会触发 TLM 的 `handleAsync(..., serverExecutor)`，在服务端线程续跑工具批
- 离开 pending 状态的每条路径都要完成 future：确认、取消、信任、超时、主人登出、女仆死亡/移除（按实体引用判 `isAlive()/isRemoved()`，不用区块查询以免把"未加载"误判为"已移除"）、服务器停止
- `/tlmconfirm` 先移除 pending 再执行，保证 token 一次性、重复点击幂等；token 用 UUID，从不进入模型可见文本，且 `tlmconfirm` 本身在黑名单中

### 审计日志轮转
- 路径用 `FMLPaths.GAMEDIR` 拼 `logs/tlm_sincerely/command_audit.log`（`FMLPaths` 没有 LOGSDIR）
- 每次 append 后 `Files.size` 超限就 `Files.move(..., REPLACE_EXISTING)` 到 `command_audit.log.1`；Windows 上可行是因为写入是独立的一次 open/close，没有常开句柄
- 写盘失败只 WARN，绝不打断命令流程；命令文本按 `\`、`"`、换行/制表符转义，且命令本身已经拒绝换行

### future 完成会内联续跑 TLM 工具批次（再入陷阱）
- `LLMCallback` 用 `handleAsync(..., serverExecutor)` 消费工具的 future；在服务端线程上 `future.complete()` 时 `BlockableEventLoop` 的 `runningTask()` 为假（ServerTickEvent 直接派发，不在 `doRunTask` 内）→ 续跑**同步内联**执行
- 后果：在"迭代 pending 表并逐个 complete"的循环里，同一批次的下一条 `run_command` 可能插入新的 pending → `ConcurrentModificationException`；在 tick 路径会冒泡成崩溃报告
- 规避：所有 pending 遍历一律**先快照、先移除、再完成**（见 `CommandConfirmationService.onServerTick/onOwnerLoggedOut/cancelAll`）
- 相关：`complete()` 若 `addToolResult` 抛异常应 `completeExceptionally`，让 TLM 的 `onToolErrorCall` 兜底生成错误 tool result，而不是让 future 永久挂起

### 命令确认的衍生产出
- 确认消息、气泡、回执一律经 `MaidCommandExecutor.sanitizeForDisplay` 剥离 `§` 与控制字符：模型生成的命令文本可能含格式码，直接进聊天组件会造成视觉伪装
- 挂起时女仆气泡含命令原文（计划要求），该气泡随实体数据同步给能看见女仆的玩家；确认消息本身仍只发主人
- 文本扫描会剥离任意 token 的 `namespace:` 前缀，属保守策略：资源 ID 恰为名单条目时会命中（宁可多确认/多拒绝）

### TLM 数据包 Skill 命名空间必须为 touhou_little_maid
- **现象**：在附属模组的 `data/<modid>/skills/...` 下放置 `skill.md`，TLM 启动后无法扫描到该 Skill。
- **根因**：TLM 1.5.3 的 `SkillsDataReloadListener` 在重载资源时，硬编码过滤了 `resourceLocation.getNamespace().equals("touhou_little_maid")`。
- **解决**：附属若要内置随 mod 数据包分发的 Skill，必须放置于 `data/touhou_little_maid/skills/<skill-name>/skill.md`。
- **调用时机优化**：为防止大型参考 Skill 在初次对话中被模型盲目加载占用上下文，在 Skill 的 YAML `description` 中通过明确限制语（“仅在先前 run_command 失败报错或需构建复杂高阶 1.20.1 指令时调用”），可实现真正的报错自愈式惰性加载。

### Memory harness 的并发、持久化与安全约束
  - 同一 session 同时只能有一个 chat；后续请求返回 `409` 或显式排队，不能让两个 agent loop 共享 history/memory 并发运行
  - record/replay transport 必须按 session 复用，文件名统一 `turn-N.json`；auto-gen 使用独立 transport，不能推进主聊天 cursor
  - SSE 必须监听 response close 并向 agent/transport 传播 `AbortSignal`；客户端断开后不得继续消耗 token 或写入状态
  - Memory CRUD 返回成功前必须真正 await 原子落盘，debounce 只能用于明确允许 best-effort 的路径
  - config/scenario 缩小 `maxMemories` 时，若现有或待载入记忆超限必须拒绝并保持原状态
  - 非回环监听必须要求 token；未知异常默认返回通用错误，完整路径、上游 body 与凭据只写服务端日志
  - Node 原子写使用同目录随机临时文件 + rename replace；Windows 短暂锁定可重试，但不能先删除旧文件制造空窗

### 自动工作：客户端预设库与绑定快照（2026-09-17）
- **调度必须服务端跑 ≠ 预设必须全服共享**：调度只要求数据在服务端可见，因此"给女仆选预设"实现为一次性烤快照（`AutoWorkState.order`，NBT 标签 `bound`）。客户端库是私有文件，服务端只在绑定/首触时读取 id/name/order；避免"某玩家改库，别人女仆跟着变"的隐式共享。旧数据只有 presetId 时按冻结种子补烤。
- **TLM `TaskAttack.isWeapon` 会把弓当近战武器，近战判定不能直接复用**：本模组近战 `attack` 的 `MELEE_WEAPON` 显式排除弓、弩、三叉戟、御币；弓兵（`ranged_attack`）要求弓+箭（`getAllSupportedProjectiles`），弩/戟/弹幕各自按 `isWeapon` 检查。武器注册进 `MaidHardToolService`，`setTask` 前换到主手。
- **棋盘检测：`sitId == 自身` 要视为占用中的可用**：下棋时棋盘被自己占用，若把"被占用"一律判 `UNAVAILABLE`，女仆会被切走并在 `switchTo` 里 `stopRiding` 下车。`BuiltinBoardGamesDetector` 对自身 `sitId` 返回可用，其他占用者判不可用。
- **`canBrainMoving()` 含 `isPassenger()`**：骑乘（椅子/棋盘/船）时它返回 false，不能拿它当"完全不能工作"的判据。需要"坐下/睡眠/拴绳不可工作但骑乘仍扫描"的语义时，先判断 `getVehicle() == null` 再查 `canBrainMoving()`（`BuiltinHoneyDetector` 即此写法）。
- **线格式 encode 上限必须与 decode 上限一致**：解码端对超限（预设 64 条、每条 order 512）抛异常会直接断开连接；编码端要用同一常量做 `Math.min` 钳制、超量静默截断，不能只依赖本地数据的规范性。
- **Jade 为 optional compileOnly**：只有 `AutoWorkJadePlugin` / `AutoWorkJadeProvider` 这类插件类可以引用 Jade API（由 `@WailaPlugin` 在 Jade 存在时加载），其他公共路径不得引入硬依赖，缺失时模组必须正常启动。
- **配置页要即时重绑，不要引入"未应用"中间态**：`AutoWorkConfigScreen` 对该页女仆的增删改序/改名必须直接写本地库并重发 `SetMaidAutoWorkPresetC2SPacket`（`rebakeActivePreset()`），不要做"改动先挂起、点 `<`/`>` 才生效"的提示条——服务端不会自动同步，玩家会以为保存失败。库与绑定仍是两个概念：只有这只女仆的页面在改动时才重绑，其他女仆与推送入库都不受影响。
- **攻击检测不要直接读 `findFirstValidAttackTarget(maid)`**：它读的是 `NEAREST_VISIBLE_LIVING_ENTITIES`/`NEAREST_LIVING_ENTITIES` 记忆，而那份记忆的扫描范围由女仆**当前**任务决定；同时 `maid.canAttack` 会把判定委托给当前任务。结果可能给出"切过去后 TLM 的 `StopAttackingIfTargetInvalid#farAway` 立刻丢弃"的假目标，表现为女仆切入攻击工作后原地发呆。正确做法是用 `IMaidTask.searchDimension/searchRadius` 建搜索盒，并按该任务自己的 `farAway` 语义比较距离（`TaskAttack`：跟随模式按主人到目标；`TaskBowAttack`：女仆到目标），再配合 `attackTask.canAttack` 与 `canSee`。
- **Jade 开发环境会断言插件 UID 翻译**：`JadeClient.onGui` 对每个 `getUid()` 要求存在 `config.jade.plugin_<namespace>.<path>`（本模组即 `config.jade.plugin_tlm_sincerely.auto_work`）。缺 key 时 `AssertionError` 会在加载 overlay 阶段崩客户端（正式包通常不断言）。信息栏正文仍用 `jade.tlm_sincerely.auto_work.active`。

### 2026-09-29 移植主支审查修复（P1×6 + P2）与 MaidFileManager 联动（forge 线，compileJava + build 验证）

- **"Forge correct() 空安全"是错误结论，`defineInList` 的坑两条线都有**：实机整合包（Forge 47.x）`tlm_sincerely-common.toml` 中 `memory = {}` 空表 → `correct()` 对缺失键以 null 调校验器 → 不可变 `List.of(...).contains(null)` 抛 NPE，且 FML 3.0.x 同样只计 "1 errors found" 不进日志。连锁反应比主支那次更直观：配置 NPE → 资源重载失败 → Minecraft "恢复模式"清空全部已选资源包（`Caught error loading resourcepacks, removing all selected resourcepacks`）→ 中文语言包被移除、文本回退英文 → 创建世界时崩溃。修复同主支：`new ArrayList<>(List.of(...))`。**此前 HANDOFF/主支记忆里"Forge 的 correct() 是空安全的、本分支不存在该问题"的判断不成立，两线规则统一：allowedValues 必须可变。**
- **跨 loader cherry-pick 会把 `neoforge.mods.toml` 的改动按文件相似度映射到本分支 `mods.toml`**：git rename detection 落上的是 NeoForge 1.21 语法（`type = "optional"`），Forge 1.20.1 的段格式是 `mandatory = false`。跨线搬运 mods.toml 必须人工核对段格式，不能信 auto-merge。
- **Forge 1.20.1 的 `@EventBusSubscriber` 必须显式 `bus = Mod.EventBusSubscriber.Bus.MOD`**：`AutoWorkConfigReloader` 监听 `ModConfigEvent.Reloading`（IModBusEvent），不写 bus 会被注册到 FORGE 总线导致事件永远不触发（Forge 1.20.1 没有 FML 4.x 的按 IModBusEvent 自动分流）。
- **mixin refmap 只为 vanilla 混淆目标生成条目**：本模组 12 个 mixin 在 refmap 里只有 2 条（`renderTooltip`、`sendSystemMessage`，都是 vanilla 方法），注入 TLM/本模组类目标的条目不需要 SRG remap——对照检查 refmap 时不要把"条目数 ≪ mixin 数"误判为生成缺失。
- **Forge 1.20.1 的 `PacketDistributor` 没有静态 `sendToServer`**：C2S 走 `AutoWorkNetworking.channel().sendToServer(...)`；S2C 单发统一封装在 `AutoWorkNetworking.sendToPlayer(player, payload)`（`channel().send(PacketDistributor.PLAYER.with(() -> player), ...)`），与主支 NeoForge 版同名 API 对齐，减少跨线 cherry-pick 冲突面。
- **平台无关教训自主支 2026-09-28 节互移**（详见主支 DEVELOPMENT.md 同日节，适用本分支）：mixin finalness 用 `@Final` 注解（无成员标记注解，不能写 `@Final(remap = false)`）；TLM `tryCompressBeforeChat` 延迟路径要求维护标记按女仆 UUID 追踪而非仅 ThreadLocal；TLM HTTP 回调线程访问世界状态必须 `server.execute`，且 `shouldStopChat` 静默 return 不触发回调——登记表必须有时间戳兜底清扫；网络包 encode 端计数必须 `Math.min` 到 decode 上限；扫描型 Detector 一律 `getRestrictRadius() - 1`；evidence 字符串匹配用精确常量；`getPersistentData()` 顶层键退出重进保留但死亡重生回退（需跨死亡放 `PlayerPersisted`）；JDK Proxy 不自动执行接口 default 方法（`isAvailable()` 要显式返回 `true`）与 Object 方法。

### 联机进服即断连：命令树 argument type 未进 registry（2026-09-30，双实例复现 + JFR 取证，已结案）
- **症状与表象**：联机（LAN/FRP 服务器）进服数秒即被踢，客户端报 `Internal Exception: io.netty.handler.codec.DecoderException: java.lang.IndexOutOfBoundsException: readerIndex(N)+length(1) exceeds writerIndex(N)`，**客户端 latest.log/debug.log 完全无堆栈**（vanilla `Connection.exceptionCaught` 只 disconnect 不写日志，踢出理由只显示在断开画面）；单人不复现（loopback 同走 wire，但玩家环境数据无触发差异）
- **完整排查链**：round-trip 测试证明 release wire 同 jar 编解码自洽（`releasewire` 测试包，保留）→ 排除 schema 内部缺陷与 1MiB 截断 → 本地双实例（ForgeGradle dev，两实例同 classpath）复现成功排除"两端 jar 混用" → **JFR 异常事件**（`-XX:StartFlightRecording=...,settings=profile` + `jcmd JFR.start jdk.JavaExceptionThrow#enabled=true`，JFR 的 `dumponexit` 要进程退出才落盘、运行中用 `jcmd JFR.dump`）拿到 vanilla 吞掉的完整堆栈：越界点在 **`ClientboundCommandsPacket.readNode` 的 `readVarIntArray`**——是**命令树同步包**，与本模组网络包无关
- **根因**：Forge 47 的命令树包对 argument 节点写的是 `BuiltInRegistries.COMMAND_ARGUMENT_TYPE.getId(serializer)`（registry 数字 id），而 `ArgumentTypeInfos.registerByClass` **只填 BY_CLASS map 不进 registry**——本模组 `UnicodeWordArgument` 只调了 registerByClass，写出 id=-1，客户端反查 null、整棵树字节流错位越界。直接 `Registry.register` 又会在 mod 构造期撞 "Can not register to a locked registry"——**正解是 MOD 总线 `RegisterEvent`（NeoForge 21.1 主支早已用 `DeferredRegister` 修过同一坑，forge 线代码从旧版本 fork 没带上修复；本次已对齐主支 DeferredRegister 模式）**
- **双实例 dev 联机环境（本分支长期可用）**：`build.gradle` runs 块新增 `joiner` run（workingDirectory `run2`、`args --username TesterB`），`gradlew runClient`（host，run/）+ `gradlew runJoiner`（joiner，run2/）即可双开；FG 的 dev mod 通过 `MOD_CLASSES` 环境变量定位（`<modid>%%<classes dir>;...`），手动 java 启动第二实例必须带上，否则 mixin config 资源读不到直接 `MixinInitialisationError`；两实例离线用户名必须不同，否则被 "此名称已被占用" 拒绝（LAN 下前一会话未超时释放也会报这个，稍等重连即可）
- **run/ 目录分支隔离**：默认 workingDirectory 两分支共用 `run/`，曾混入主支 NeoForge jar 导致 forge 启动失败（移入 `.tmp-mdk/run-mods-1.21.1-backup/`）；建议主支把 workingDirectory 改到独立目录实现分支级隔离

## 测试流程

```
# 1. 构建
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot"
.\gradlew.bat build --no-daemon

# 2. 启动测试客户端
.\gradlew.bat runClient --no-daemon

# 3. 验证配置文件
cat run\config\tlm_sincerely-common.toml

# 4. 查看崩溃日志
ls run\crash-reports\
cat run\logs\latest.log

# 5. 清理特定配置（如需）
Remove-Item run\config\tlm_sincerely* -Force
```

---

## 多女仆对话与 tell_maid 转告（2026-10 设计要点与踩坑）

- **解析器必须与旧正则字节级兼容，回退路径靠"无效结果"而不是靠 try/catch**：`AtTargetParser.parse` 返回 `valid=false` 时 `ChatBarHandler` 原样走旧的 `^<prefix>(.+?)\s+(.+)$`。为守住"只有一个 `@名字 内容` 时行为不变"，把 `@ 正文`（裸前缀后紧跟空白）与"没有正文"都判为无效——旧正则对 `@ 内容` 同样不匹配，落到的就是 `RequirePrefix=false` 的自动最近路径。任何"顺手更聪明"的解析（比如允许名字含空格、允许 `@` 单独成词）都会悄悄改掉这条路径的不变量。
- **多目标的判定用"解析器接手"而不是"命中数量"**：`handledByMultiTarget` 语义是"旧正则不该再跑"，与是否真的找到女仆无关。旧代码用同一个标志位表达"前缀被用过"，因此自动最近匹配在 `@名字` 未命中时同样不触发；改动时若按 `targets.isEmpty()` 判断，会让 `@不存在的名字 内容` 突然变成"和最近的女仆聊天"。
- **"同时对谁说"标注只在真正多只时加**：`annotated = multiTargetMessage && targets.size() > 1`。单目标保持旧 prompt 形状，避免影响既有对话历史与 Token 预算；`@全体` 只命中一只时同样不标注。
- **对话语言取玩家的客户端语言（`ServerPlayer#getLanguage()`），不要用 `getTTSLanguage()`**：主 Mod 自己的聊天界面用 `ChatClientInfo.fromMaid` 填的就是客户端语言，它会进入 `chatLanguage` 并替换角色设定里的 `${chat_language}`；TTS 语言只管语音合成。早期实现把 TTS 语言当对话语言传给 `ChatClientInfo`，结果女仆的 TTS 语言设成英文时，会被要求用英文回复（表现为某只女仆单独说英文）。已在 `ChatBarHandler`、`ChatCommand`、`MemoryCommand`、`MemoryMaintenanceManager` 四处改正；多目标标注与各类提示词按这个语言选中/英文。
- **配置项 `defineList` 的默认值必须是可变 List**：与 `MaidCommandConfig` 同口径用 `new ArrayList<>(DEFAULT_ALL_KEYWORDS)`，否则会重演 `memory = {}` 时 `List.of(...).contains(null)` 抛 NPE、FML 只记 "1 errors found" 的连锁故障。
- **`ITool.trigger(EntityMaid, ChatCompletion)` 是工具可见性的唯一闸门**（已用 javap 核实：`LLMOpenAIClient` 在 `ToolRegister.getAllTools()` 的 entrySet 循环里调它，返回 false 直接 `goto` 下一条）：工具的开关配置放在 `trigger` 里最省事，不必自己过滤工具列表；`onCallAsync` 里再查一次配置只是防御。
- **工具参数 codec 用 `optionalFieldOf(name, "")` 而不是 `fieldOf`**：TLM 在 `LLMCallback.onSingleCall` 里用 `codec.parse(JsonOps.INSTANCE, jsonObject)`，缺字段时 `fieldOf` 会让整次解析失败并返回 TLM 自己的通用报错；`optionalFieldOf` 落到空字符串后由 `ITool.invalidParam` 给出"哪个参数、什么期望"的提示，模型下一轮更容易自我纠正。
- **反射桥只在"类加载成功"这一层做永久缓存，方法查找失败不要缓存 false**：`SelfTalkApi` 的类存在性一次判定即可，`getMethod` 失败重试的代价可以忽略；真正要防的是把"未安装"和"API 不匹配"混成一个不可恢复的降级，那会让日志再也说不出原因。任何异常都吞在桥内并返回 `false`，转成给模型的英文说明，绝不让对方 mod 的内部错误冒泡到工具链。
- **转告的线程保证与命令工具同构**：`LLMCallback.isOnServerThread()` 为真直接跑，否则 `runOnServerThread` 包一层再用 `whenComplete` 把结果桥回 `CompletableFuture`。Self Talk 明确要求服务端主线程，实体查询（`getEntitiesOfClass`）同样不能在异步线程做。
- **限流必须"被拒绝不记账"**：先查冷却与配额、通过后再写入时间戳，否则模型连续重试会把自己的分钟额度刷满，表现为"越试越久不能转告"。冷却键用 UUID 排序后的无方向对，保证 A→B 与 B→A 互相封锁（防回环）。
- **转告状态没有世界存档载体**：冷却与计数只在内存，`SincerelyMod.onServerStopped` 调 `TellMaidTool.clearRateLimitState()` 清空；这与 `CommandConfirmationService` 的会话信任同级别，不需要 SavedData。
- **`tell_maid` "一次对话最多一次"只能是软约束**：TLM 没有可用的批次/会话标识能传给工具（`ChatClientHistory` 不暴露给工具），硬限制只能靠冷却与每分钟上限；已在工具说明与 WIKI 里写明这一限制。
- **第三方 API 只在报告里按约定签名调用，不预先假定对方已实现**：本次实现时 `maid_self_talk` 仓库里还没有 `SelfTalkApi`（同期的另一任务在写），因此桥接按约定签名 + `isAvailable()` 双检查，签名不匹配时降级为英文失败说明。集成前先用 `javap` 核对对方实际产物再决定是否收紧。

### 2026-10-10 提示词按对话语言选中/英文（`ai/PromptLang`）

- **规则**：对话语言（主 Mod 为本次对话记录的 `getChatLanguage()`，缺省 `en_us`）以 `zh` 开头 → 用中文提示词；其余语言 → 英文提示词，并在提示词末尾追加一句"请始终用某某语言回复"（`PromptLang.replyDirective`，繁体中文追加"请用繁体中文"，简体中文与英文不追加）。小模型（如 deepseek-flash）对英文系统提示容易中英夹杂，所以中文玩家得到完整的中文提示词。
- **适用范围**：记忆指引与路由说明（`MemoryGuidanceMixin`）、`tlm_memory` / `run_command` / `auto_work` / `tell_maid` 的工具说明、参数说明和返回给模型的结果文本、记忆与自动工作上下文的取值、记忆整理与 `/tlmmemory summarize` 的提示、多目标标注。**工具 id、参数名、枚举取值（remember/recall/core/archive 等）是机器标识，保持英文原样。**
- **上下文栏目标题不能按语言切换**：`AbstractMaidContext` 的 `key()/label()` 是 `final`，标题在构造时固定，所以标题写成中英并列（如 `女仆的长期记忆 (Maid persistent memories)`）；指引文本里引用的是中文那一半。
- **语言标签要消毒**：`ChatClientInfo.language` 来自客户端包，原样拼进系统提示等于给玩家一个提示词注入口；`PromptLang.normalize` 只接受 `xx` / `xx_yy` 形态，其余回落 `en_us`（与 Self Talk 的 `sanitizeLanguage` 同口径）。
- **记忆 key 的语言**：中文提示词要求 key 用简短中文词语（如 `玩家爱好`），英文提示词仍要求英文 snake_case；代码对 key 没有格式校验，旧的英文 key 继续可用。
- **仍是英文的、会回给模型的文本**：`MaidCommandExecutor.runAndReport`（命令执行结果）与 `CommandConfirmationService`（确认流程的结果文本）尚未中文化，下一轮再处理。
- **验证状态**：本机无法跑 Gradle，`PromptLang` 的判断逻辑用纯 Java 自检通过；工具类仅做了语法体检与 stub 编译（`ChatBarHandler`/`TellMaidTool`/`SelfTalkBridge`），记得进游戏用中文、英文、日文三种语言各试一轮。

### 2026-10-11 移动指令 maid_move（`ai/move`，真实 mapped classpath 编译 + JUnit 验证）

- **移动实现选 Brain 的 `WALK_TARGET`，不选 `getNavigation().moveTo`（javap 核实）**：`MaidBrain.registerCoreGoals` 把 vanilla `MoveToTargetSink` 注册进女仆 Brain 的 CORE activity（`MaidBrain` 字节码里有 `new MoveToTargetSink` 与 `setCoreActivities(Set.of(Activity.CORE))`），它消费 `MemoryModuleType.WALK_TARGET`；主模组自己的 `MaidFollowOwnerTask` / `MaidWalkToLivingEntityTask` 也走 `BehaviorUtils.setWalkAndLookTargetMemories(...)`（SRG `m_22590_`，在 `MaidWalkToLivingEntityTask` 的 `lambda$start$5` 里可见）。所以"让女仆走到某处"的惯用写法是写 `WALK_TARGET` 记忆；直接操纵 `PathNavigation` 会绕过 Brain，与主模组行为不一致。坐标目标用 `new WalkTarget(Vec3, speed, closeEnough)`，实体目标用 `BehaviorUtils.setWalkAndLookTargetMemories`（顺带设置 `LOOK_TARGET`）。步速倍率取 `0.5F`，与 `MaidBrain` 里 `new MaidFollowOwnerTask(0.5f, 2)` 的实参一致。
- **守护的攻击目标照抄 `SwitchWorkTaskTool#attackResult` 的顺序（javap -c 核实）**：`level.getEntity(entityId)` → 检查存活 → 记下 `maid.getTarget()` → `maid.setTarget(新目标)` → `IAttackTask.canAttack(maid, 目标)` 为 false 时 `maid.setTarget(旧目标)` 回滚并放弃 → 通过则 `maid.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, 目标)`（SRG `f_26372_`）。`canAttack` 的默认实现里已经包含"排除盔甲架、`AbstractEntityFromItem`、玩家、有主人的 `TamableAnimal`、自定义名以 `MaidNoAttack` 开头的实体"等规则，所以不要再自建一套过滤（本工具只额外限定 `Enemy` 且不是女仆）。
- **"当前任务是不是战斗类"用 `instanceof IAttackTask` 判断，且不擅自 `setTask`**：`SwitchWorkTaskTool` 是"先切任务再设目标"，`maid_move` 没有切任务的权利，只做"守在身边"，并把原因写进给模型的返回（`MoveText.guardNoCombatNote`）。强行 `setTask` 会绕过 `AutoWorkInternalSetTaskGuard` 与换装事务（见本文件"自动任务换装与 `setTask` 必须是同一事务"），把自动工作状态搞脏。
- **状态判定用 `EntityMaid#canBrainMoving()` 反推原因**：它的字节码就是 `isMaidInSittingPose() || isPassenger() || isSleeping() || isLeashed()` 取反 —— 正好是"坐下 / 睡觉 / 骑乘 / 被拴住"。工具侧按同样顺序反查是哪一项（用于返回文案），tick 侧只调 `canBrainMoving()` 决定本周期是否暂停下发移动（暂停不等于取消指令）。
- **调度器钩子是"逐只女仆"循环里的一行，位置在 `observeMaidBusy` 之后**：`if (MoveDirectiveManager.isActive(maid.getUUID())) continue;`，位于 `TaskAutoSwitchHandler.onServerTick` 第三趟循环的 `observeMaidBusy(...)` 与 `handleExperimentalAttackPreempt(...)` 之间。效果：busy 观察（`markBusyObserved` / `trackCurrentAvailability`）照常更新，检测调度（`TaskDetectionScheduler.tick`，在循环之前）照常跑，只有"攻击抢占 + 普通切换"被跳过，指令生效期间不会有任何 `setTask`。代价是这只女仆已排队的 `IMMEDIATE_EVALUATION_REQUESTS` 会被消费掉（下个决策 tick 会重新评估，无副作用）。**不要在循环开头 `continue`**：那会连 busy 观察一起跳过，指令结束后繁忙保护会看到过期的 `busyStartTick`。
- **"每个主人同时生效指令数"的计数必须"覆盖自己的不算新增"**：`MoveOrders.put` 先看这只女仆有没有旧指令，有就直接覆盖（不查上限），没有才查 `countForOwner`。否则模型重发同一条指令会被自己的旧指令卡住，表现成"第二次说就不动了"。
- **远离是"算点"而不是"挂行为"**：主模组的 `SetWalkTargetAwayFrom` / `MaidRunAwayTask` 是给 Brain 行为用的；`maid_move` 的 `retreat` 用 `MoveMath.retreatPoint` 算出远离点当坐标走过去，算不出（与目标水平重合）或附近没有可站立位置就保持不动、不取消指令。远离点与采样偏移是纯 Java，单测直接覆盖。
- **验证手段升级：优先用 `mapped_parchment` 的 deobf jar 做 javac classpath，不要再手写 stub**。`.claude/temp/zhcheck/cp.txt` 拼出了真实 classpath（`forge_gradle/minecraft_user_repo/.../forge-1.20.1-47.4.0_mapped_parchment_2023.08.20-1.20.1.jar` + `deobf_dependencies/maven/modrinth/touhou-little-maid/.../touhou-little-maid-1.5.3-forge+mc1.20.1_mapped_parchment_....jar` + DFU / Gson / Cloth Config 等），因此可以真跑 `javac` 全量编译 `src/main/java`（133 个文件）与 `src/test/java`（15 个文件）并得到 exit 0；纯 Java 单测用 JUnit Platform launcher 直接跑（本次 `maid_move` 新增 32 个用例全绿）。`.claude/temp/zhcheck/check.ps1 -IncludeTest` 是现成入口。

### 2026-10-10 多段回复分条显示（`ReplySplitter`）

- 主 Mod 在 `ChatBubbleManager.addLLMChatText` 里把回复拼成 `<女仆名> 正文` 一整条聊天消息；模型一分段，聊天栏里就是一大块带空行的文字。`MaidChatBroadcastMixin` 已经重定向了这一次 `sendSystemMessage`，所以在这里按换行拆成多条（每条补同样的 `<女仆名> ` 前缀、沿用原消息的样式），拆分逻辑放在无 MC 依赖的 `chatbar/ReplySplitter`。
- **只拆聊天栏显示**：气泡、TTS 和历史记录走的是别的路径，不受影响。没有拆成逐句，只按换行拆——逐句拆会把一句话里的逗号句号也切开，反而不自然；想更碎需要让模型在提示词里多分行，而不是靠代码硬切。
- 配置项 `chatbar.SplitReplyLines`（默认开），配置界面与两个语言文件已同步。

### 2026-10-12 移动指令的寻路失败兜底（`ai/move/MoveProgress`、`MaidSpeech`、传送兜底）

- **"女仆答应了却没动"要能靠日志定位，所以每一环都补了 INFO**：工具侧在"指令写入"与"被拒绝"（no-maid / not-server-level / disabled / unknown-action / owner-unavailable / target-resolution / missing-coords / immobile / limit-reached）各自记一条，管理器侧在"判定卡住（原因 + 当前距离）/ 传送（起点→终点 + 第几次）/ 取消或放弃（原因）"各记一条。日志统一用女仆 UUID 前 8 位（完整 UUID 在日志里看不出差别），工具侧额外带女仆名。没有这些日志时，玩家只会说"她没动"，无法区分是"指令没登记""目标解析失败""WALK_TARGET 被别的行为覆盖"还是"真的走不过去"。
- **卡住判定做成无 MC 依赖的纯 Java 类（`MoveProgress`），时间由调用方以 tick 传入**：`tick(goalSatisfied, paused, distance, cantReach, nowTick)` 每个处理周期（10 tick）调一次。"有进展"是相对**历史最佳距离**（`distance <= bestDistance - 1.0`）而不是相对上一周期，因为慢速女仆 10 tick 可能只挪 0.5 格——按"相邻两周期差值"判定会把正常行走误判成卡住；按记录判定时，只要 100 tick 内刷新过一次记录就不算卡住。
- **`CANT_REACH_WALK_TARGET_SINCE` 的核对结果（javap，vanilla `MoveToTargetSink`）**：`tryComputePath` 在 `path.canReach()` 为 false 且 `!hasMemoryValue(CANT_REACH)` 时 `setMemory(CANT_REACH, gameTime)`，可走通 / 已到达 / 已放弃时 `eraseMemory(CANT_REACH)`；它的构造器把该记忆注册为 `MemoryStatus.REGISTERED`，`MaidBrain` 的 `getMemoryTypes()` 也列出了这个类型，所以 `maid.getBrain().hasMemoryValue(...)` 既安全（`Brain#checkMemory` 对缺失键返回 false）又有意义。本工具把它当**快路径**：无进展且该标记连续出现 2 个处理周期（20 tick）就判卡住，比 5 秒的 no-progress 快得多；但只要该周期有进展就清零计数，避免"只走通部分路径、标记常驻但人确实在走"被误判。
- **判定本身要节流，否则兜底关闭时日志刷屏**：`MoveProgress` 每报一次卡住就把下一次允许报告的 tick 推到 100 tick 之后（第一次仍可走 CANT_REACH 的 20 tick 快路径）。另外传送后的 100 tick 冷却期内 `tick` 直接返回 NONE——不这样做的话，CANT_REACH 每 20 tick 就会触发一次判定，冷却期内的判定全是无用功。
- **传送用的是主模组自己的动作，不是 `Entity#teleportTo`（javap 证据）**：`EntityMaid#teleportToOwner(LivingEntity)` 循环 10 次取 `dx∈[-3,3], dy∈[-1,1], dz∈[-3,3]` 的随机偏移调用私有的 `maybeTeleportTo(...)`；后者先用 `teleportTooClosed`（要求与目标水平距离 ≥2 格）与 `canTeleportTo`（下方不在 `MAID_AVOID_BLOCK` 标签里、`getBlockPathTypeStatic` 是 WALKABLE/WATER、`noCollision`）校验，然后依次 `moveTo(x+0.5, y, z+0.5, getYRot(), getXRot())` → `getNavigation().stop()` → `eraseMemory(WALK_TARGET / LOOK_TARGET / ATTACK_TARGET / PATH)`；`MaidFollowOwnerTask` 在 `teleportToOwner` 之后还会补一次 `getNavigationManager().resetNavigation()`（把导航切回地面并把游泳状态清零，javap 可见它 `setNavigation(basicNavigation)` + 两个导航 `stop()` + `setWantToSwim(false)` + `setReadyToLand(false)` + `mode = GROUND`）。本工具的 `teleportMaid` 照抄这一整套收尾动作，只是落点自己算。**没有直接调 `teleportToOwner`**：它要求目标是 `LivingEntity`、落点只能在与目标 ±3 格且水平距离 ≥2 格的随机位置，goto / retreat 的坐标目标用不上，approach / guard 也没法"优先女仆原来的方向"。
- **落点选择是纯函数（`MoveMath#landingCandidates`）**：approach / guard 取"目标周围 `distance` 格的环"，第一个候选正对女仆原来所在的方向（`atan2(maid.z-dest.z, maid.x-dest.x)`），再按 ±45°、±90°、±135°、180° 依次偏转；goto / retreat 只有坐标本身一个候选。每个候选再交给既有的 `findStandable`（固定顺序的偏移表）做"附近偏移"，所以环上被挡住时也能退到 ±2 格内。目标与女仆水平重合时 `atan2(0,0)=0`，从 +x 开始，不需要特判。
- **"找落脚点"用的是本模组自己的 `findStandable`，不是主模组的 `canTeleportTo`**：后者是私有的，且只校验"能不能站"（不保证离目的地足够近）；`findStandable` 的偏移表天然就是需求里的"附近偏移"，与 `retreat` 的既有行为同口径。
- **`goto` 的"到位"要给滞回带，否则会误传送**：如果严格按 `ARRIVE_RADIUS=2` 算"到位"，女仆到位后只漂出 3 格时既不会重新下发 `WALK_TARGET`（要 >4 格才发），又会被算作"没到位"→ 5 秒后判定卡住 → 被传送 3 格并说一句"路被挡住了"（观感很差）。所以 `Goal` 里把 `satisfied`（用于重置计时）与 `walking`（是否重新下发）分开：滞回带内 `walking=false`，与"暂停"同样处理——不算卡住也不计时。`satisfied=false` 但 `walking=false` 时也不会误报。
- **给女仆说话直接复用主模组的 `ChatBubbleManager#addLLMChatText(String,long)`（javap 证据）**：它内部 `TextChatBubbleData.type2(Component.literal(text))` + `getChatBubbleDataCollection().remove(id)` + `add(...)` + `forceUpdateChatBubble()`，然后在 `maid.getOwner() instanceof ServerPlayer` 时把 `"<" + 女仆名 + "> " + text` 以 `ChatFormatting.GRAY` 发出去。第二个参数是"先移除哪条气泡"（主模组传的是 thinking 气泡的 id），我们没有 thinking 气泡，传 `-1`（集合里不存在，等同不删）。**"只发主人还是广播全服"不用自己判断**：本模组的 `MaidChatBroadcastMixin` 已经重定向了那一次 `sendSystemMessage`，按 `ChatBarPlayerState.isGlobalVisible(owner)` 决定广播/私聊，并按 `SplitReplyLines` 分条——自己再发一条会重复。`ChatBubbleSilenceMixin` 会在记忆整理期间把整条调用取消掉（气泡和聊天都没了），这是预期行为。
- **`CANT_REACH` 计数在"已满足目标 / 暂停"时清零**：暂停（坐下 / 睡觉 / 骑乘 / 被拴住）期间 `MoveToTargetSink` 不跑，标记可能是陈旧的；清零比冻结更安全。
- **验证状态**：真实 mapped classpath 全量 `javac`（154 个源文件）exit 0；JUnit Platform 跑全包 110 个用例 0 失败，其中本次新增 `MoveProgressTest` 18 个、`MoveLandingTest` 12 个。**未做实机验证**：传送落点是否总能找到、跟随行为与 `goto` 的实际争夺、`MoveTeleportMaxDistance` 的手感都需要进游戏确认。

### 2026-10-13 移动指令：stay / go_home / 永久指令 / 暂停跟随与跨重启恢复（`ai/move`）

- **为什么需要"暂停跟随"（javap 核实，mapped TLM 1.5.3）**：`MaidFollowOwnerTask` 在 `MaidBrain.registerCoreGoals` 里**无条件**注册到 `Activity.CORE`，生效条件是运行时的 `maidStateConditions = !maid.isHomeModeEnable() && maid.canBrainMoving()`；`start` 里 `stopDist = (int) getRestrictRadius() - 2`、`maxDist = stopDist + 4`，距离超过 `maxDist` 直接 `teleportToOwner(owner)` + `resetNavigation()`，否则只要 `WALK_TARGET` 不指向主人就 `BehaviorUtils.setWalkAndLookTargetMemories(maid, owner, 0.5F, 2)` 覆盖它。本模组每 10 tick 才下发一次移动，所以 `stay` / `goto` / `retreat` 想"不跟着主人"就必须在生效期间让 `isHomeModeEnable()` 为 `true`。**目标就是主人的 `approach` / `guard` 不暂停**——那种指令本来就该让跟随行为继续生效。
- **"有没有工作坐标"不能用 `hasRestriction()` / `getRestrictRadius()` 判断（javap 核实）**：`EntityMaid` 覆写了 `m_21534_/m_21535_/m_21536_/m_21444_/m_21446_`（`getRestrictCenter` / `getRestrictRadius` / `hasRestriction` / `isWithinRestriction` / `restrictTo`）这一整组，全部读写同步数据；其中 `hasRestriction()` 的字节码就是 `return isHomeModeEnable();`，而 `RESTRICT_RADIUS` 在 `defineSynchedData` 里的默认值是 `MaidConfig.MAID_NON_HOME_RANGE`（默认 8），**不是** `Mob#clearRestriction` 写入的 -1 哨兵。真正的工作坐标载体是日程坐标 `EntityMaid#getSchedulePos()`（`SchedulePos.isConfigured()` / `getWorkPos()`，持久化在 `MaidSchedulePos` 标签里）；`SchedulePos#setHomeModeEnable(maid,pos)` 只在 `!configured` 时写 work/idle/sleep 三个坐标（也就是玩家设定工作坐标那一次）。所以 `go_home` 用 `isConfigured() && getWorkPos() != null` 判定，用 `getWorkPos()` 当传送目标。
- **暂停动作的顺序：先 `clearRestriction()` 再 `setHomeModeEnable(true)`**。理由：`EntityMaid#restrictTo` / `SchedulePos#restrictTo` 开头就 `if (!isHomeModeEnable()) return;`，居家模式下主模组的日程刷新会按当前 activity 把限制写回工作/待机范围；先清限制再开居家模式，可以少一次无谓的"先限制再解除"。`clearRestriction()` 会把 `RESTRICT_RADIUS` 置为 -1，让 `isWithinRestriction` 恒真，避免女仆因为活动范围走不到目标；`MaidConfigManager#setHomeModeEnable` 只写 `DATA_HOME_MODE` 同步数据，不会反过来把工作坐标写回去（javap 核实）。
- **恢复要"精确恢复"，不能一律关掉居家模式**：保存的记录是 `(homeMode, 是否有工作坐标, 中心 BlockPos, 半径)` 四项，恢复时先 `restrictTo(保存的中心, 保存的半径)` 再把 `setHomeModeEnable` 写回保存值。女仆原本是"居家 + 工作坐标"时，一律关闭会破坏玩家的设置；原本是普通跟随（典型情形）时结果与"关闭"相同。**不要依赖 `Mob#clearRestriction()` 的字段副作用**：女仆覆写了访问器，基类字段与同步数据是两套东西，恢复必须显式写同步数据。
- **"新指令替换旧指令时不重复保存"是本任务最容易写错的地方**：如果每来一条新指令都 `captureSuspension()`，第二条指令会把"已经暂停过"的状态（`homeMode=true`、限制已清）当原状态存下来，指令结束后永远恢复不回跟随模式。所以 `onOrderReplaced` 的规则是：**需要暂停且已有记录 → 沿用旧记录**（只写日志），需要暂停但没有记录 → 新建，不需要暂停 → 先恢复。管理器里为此把"暂停记录"放在 `SUSPENSIONS`（按 UUID），而不是只挂在指令对象上；指令对象上的 `followSuspension` 只用于持久化与 `stay` 的外部改动检测。
- **外部改动检测只在"当前居家模式 ≠ 保存值"时触发**：玩家在女仆界面里、或用跟随工具把居家模式改了，`decideRestore(currentHomeMode)` 返回 `EXTERNAL_CHANGE`，此时结束指令但**不恢复**（否则会把玩家刚改的设置覆盖回去），只写 INFO 日志。`FollowSuspension` 做成不可变纯 Java 类，这条规则可以用单测固定住。
- **持久化：纯数据结构与 NBT 适配分开**。`MoveOrderData`（指令）与 `FollowSuspension`（原状态）都不引用 MC 类型，`MoveOrderNbt` 只做 `CompoundTag` 搬运；标签名 `tlm_sincerely:move_order` 放在女仆 `Entity#getPersistentData()` 下（javap 核实 1.20.1 `Entity#getPersistentData()` 存在）。写入点在 `putOrder`（登记成功后才写，拒绝时不写），清除点统一在 `finishOrder` / `restoreSuspension`。时间口径用 `ServerLevel#getGameTime()`（存档持久化的世界时间）：它与 `MinecraftServer#getTickCount()` 同源且不会在重启后归零，所以"剩余 tick = expireAtGameTime - nowGameTime"在重启后依然成立，不需要额外换算表。永久指令存 `-1`。
- **跨重启决策表（`onMaidLoaded`，由 `EntityJoinLevelEvent` 驱动）**：内存里已有她的指令 → 什么都不做（同一会话内区块反复加载时内存态才是最新的）；标签里没有指令 → 什么都不做；到期或 `expireAtGameTime <= 0` 不可信 → 立刻恢复暂停前原状态并清标签、不重新登记（重启期间玩家看不到，不必补说到期台词）；永久 / 未到期 → 重新登记回 `ORDERS`（永久指令的提醒计时从重启后重新开始，限时指令保留原到期时刻），并把标签里的暂停记录装回 `SUSPENSIONS`；名额已满（这次会话里已有别的指令）→ 按"拿不准就恢复并丢弃"处理。数据读不出来（动作无法识别 / UUID 非法 / 字段缺失）时只清标签——没有原状态可恢复，但也不会留下一条永远挂着的指令。
- **`clear()`（服务端停止）绝对不能执行恢复动作**：此刻世界正在保存，女仆实体的标签里已经带着"暂停前原状态"；如果在这里恢复，等于把状态写回去又让标签失去意义（更糟的是 `ServerStoppedEvent` 之后实体可能不能再安全写入）。下次加载由 `onMaidLoaded` 负责恢复或重新登记。这一点与 `TellMaidTool.clearRateLimitState()` 那种"纯内存丢弃"不同，要在代码注释里写明。
- **`go_home` 是永久性改变，不产生指令**：先判断有没有工作坐标（没有 → `MoveText.noWorkPos`，不改变任何状态），再结束并恢复当前暂停中的指令（这样拿到的是真实工作坐标，且不会把女仆留在被改过的状态），然后在工作坐标周围 ±4 格（`MoveMath#horizontalSearchPoints`，每格一个、近的优先）里找可站立点，找不到 → `MoveText.homeNoSpot` 且状态不变（此时指令已经被结束、原状态已恢复，等于"什么都没发生"），找到 → 复用 `teleportMaid` 的收尾动作传送过去、`setHomeModeEnable(true)`、让女仆说 `MoveText.saidWentHome`。不存标签、不设到期、不占指令名额（`MoveAction#isOrder()` 为 false）；`go_home` 不接受 `duration_seconds` / `permanent`（传了返回 `MoveText.goHomeIgnoredArgs` 且什么都不做，避免模型"顺手"传参造成语义混淆）。
- **永久指令的提醒做成独立的纯逻辑类 `MoveReminder`**：`arm(nowTick, intervalTicks)` / `due(nowTick, intervalTicks)` / `clear()`，一帧的处理周期是 10 tick，所以提醒会比标称间隔晚最多 10 tick；错过多次间隔时只在该周期提醒一次并把下一次推到"现在 + 间隔"，避免服务器卡顿后连说好几遍。间隔由 `MovePermanentReminderMinutes` 决定（0 = 关闭），`0` 不能进 `MoveReminder` 的除法（这里没有除法，只是 `nextReminderTick` 不推进）。跨重启重新登记时用 `nowGameTick` 重新 `arm`，也就是需求里的"提醒计时从重启后重新开始"。
- **到期提示只给"自然到期"**：`onServerTick` 发现 `order.isExpired(nowGameTick)` 时先 `removeOrder`（含恢复原状态）再 `noticeExpired`，这样女仆说的"时间到了"与"已恢复原状态"是同一刻的事实。`cancel` / 被新指令替换 / 目标失效 / 传送兜底放弃各自有别的说法（或不需要说话），不重复说。
- **给模型看的上下文（`ai/context/MaidMoveContext`）是永久指令的必要配套**：永久指令不会到期，模型在后续对话里必须知道"她还在执行哪条指令、怎么停"，否则玩家说"停下"时它会答非所问。栏目值为"没有"或 `MoveText.contextActive(describe, permanent, 剩余秒数)`，其中 description 由 `MoveDescribe` 统一拼装（主人 → `MoveText.ownerName`，女仆 → 名字，goto → 坐标，stay → 不用 target），保证提醒 / 到期台词 / 上下文三处说法一致（文字本身仍全部来自 `MoveText`）。栏目标题在构造时固定、不能按语言切换，所以中英并列（与 `MaidMemoryContext` 同口径）。
- **`EntityJoinLevelEvent` 的接线放在 `SincerelyMod` 而不是 `TaskAutoSwitchHandler`**：后者已有的 `onEntityJoinLevel` 订阅者只管自动工作调度缓存（`WORKING_MAIDS` / `ensureBoundSnapshot`），移动指令恢复是另一件事，混在一起会让"谁在读 `getPersistentData()`"变得难查。事件在实体数据（含持久化标签）读出之后触发，可以直接读标签。
- **验证状态**：真实 mapped classpath 全量 `javac`（165 个源文件）exit 0；JUnit Platform 跑全包 160 个用例 0 失败（本次新增 `MoveOrderNbtTest` 11 个、`FollowSuspensionTest` 6 个、`MoveReminderTest` 8 个、`MoveOrderDataTest` 6 个、`MoveSuspensionRuleTest` 14 个，并把 `MoveOrdersTest` 扩到 18 个）。**未做实机验证**：暂停跟随与主模组日程刷新对限制半径的争夺、`go_home` 落点命中性、跨重启重新登记的实际时序、永久提醒在真实对话里的观感，都需要进游戏确认。

### 2026-10-14 移动指令：临时放宽工作时间（`ai/move/ScheduleRelief`、`work_time`）

- **睡觉判定只用 `EntityMaid#isSleeping()`（继承自 `Entity`，mapped 名下可直接调用，工具里早就在用）**：`canBrainMoving()` 在睡觉 / 坐下 / 骑乘 / 被拴住时都为 false，粒度太粗——需求只要求"正在睡觉"这一个条件，坐着的女仆照样要放宽日程，所以判定输入只用 `isSleeping()`（`ScheduleRelief.decide` 的 `sleeping` 参数）。日程接口按 `javap` 核实：`EntityMaid#getSchedule()` / `setSchedule(MaidSchedule)` 都在，`MaidSchedule` 的枚举值是 `DAY` / `NIGHT` / `ALL`；`setSchedule` 的字节码只写 `SCHEDULE_MODE` 同步数据、服务端再 `refreshBrain`，没有任何其它副作用，可以安全地当"状态标记"用。持久化只存枚举 `name()`，不存枚举对象。
- **决策表做成纯 Java 的 `ScheduleRelief`（与 `FollowSuspension` 同一套"状态守卫"范式）**：`decide(sleeping, currentlyAll, hasSavedRelief, configAllows)` 的输出是 `UNCHANGED / FORCE_ALL / REUSE`，判定顺序是"已有记录且当前仍是 ALL → REUSE；睡觉 → UNCHANGED；本来就是 ALL → UNCHANGED；配置关闭 → UNCHANGED；否则 FORCE_ALL"。`REUSE` 是这套逻辑里最容易写错的一条：替换指令时如果重新保存，就会把"已经被改成 ALL 的日程"当成原日程存下来，指令结束后永远恢复不回去（与暂停跟随的"不重复保存"完全同构）。`decideRestore(savedOriginal, currentlyAll)` 只有 `savedOriginal != null && currentlyAll` 才 `RESTORE`，否则 `EXTERNAL_CHANGE`。
- **`work_time` 不受 `MoveForceWorkSchedule` 影响**：调用方用 `order.action().isWorkTime() || MoveConfig.MOVE_FORCE_WORK_SCHEDULE.get()` 算 `configAllows`，所以配置关闭时普通移动指令不放宽，`work_time` 照旧（它自己就是为了放宽），但仍受 `MoveEnabled` 约束（工具入口统一检查）。
- **`work_time` 完全不走移动路径**：`MoveAction.WORK_TIME` 的 `needsTarget()/needsCoords()` 都是 false、`suspendsFollow()` 落到 default 的 false、`isOrder()/acceptsDuration()` 与其他限时指令一致；`step` 里它在 `resolveGoal` 之前就 `return`，因此不下发 `WALK_TARGET`、不建 `MoveProgress`、不参与卡住/传送兜底。工具侧也在 `immobileState` 检查**之前**分流，所以"坐着 / 骑乘 / 被拴住"的女仆能执行 `work_time`（只拒绝 `isSleeping()` 与"日程本来就是 ALL"两种，且都不创建指令、不改变任何状态）。注意：移动类指令本身仍保留旧的 `immobileState` 拦截，所以"睡觉 → 只追加 `scheduleSleepingNote`"在移动指令上其实不可达（睡觉会被 `cannotMoveNow` 先拒绝），该分支靠 `ScheduleReliefTest` 固定行为、作为防御性逻辑保留。
- **持久化把"原日程"并入现有记录，不另起一套**：`MoveOrderData` 末尾加 `@Nullable String originalSchedule`（`timed` / `permanent` 各加一个带该参数的重载，旧签名保留，避免大面积改调用点），`MoveOrderNbt` 增加 `originalSchedule` 字符串键；读取时键不存在或为空串都当 `null`（旧记录 = "没有放宽过日程"）。`MoveOrderNbt.read` 是唯一解析 `CompoundTag` 的地方，兼容性只在这一处处理。
- **恢复统一挂在 `finishOrder` 上**：到期 / `cancel` / 被丢弃 / 目标失效 / 传送兜底放弃 / step 异常 / 女仆不可用 / 被替换成不放宽的指令，全部经 `finishOrder` → `restoreSuspension` + `restoreScheduleRelief`。`cancelAndRestore` 改成返回 `CancelResult(hadOrder, suspensionRestored, scheduleRestored)`（`finishOrder` 用私有 `RestoredState` 回传"是否真的恢复了"），这样工具的 `restoredNote` 只在**确实恢复**时追加，不会因为"外部改动导致没恢复"而误报。恢复动作是 `setSchedule(保存的 name 解析出的枚举)`；解析失败（旧版本枚举改名 / 手改存档）时只写 WARN、不恢复。
- **外部改动检测分两处，语义不同**：`restoreScheduleRelief` 在结束时检测（不恢复、丢记录）；`step` 里每个处理周期先 `reconcileScheduleRelief`，移动指令继续、`work_time` 直接 `removeOrder`（它的唯一作用已被玩家覆盖）。检测方式是"内存里有记录但 `getSchedule() != ALL`"，与暂停跟随的"当前值 ≠ 保存值"同构；`ScheduleRelief.decide` 里 `hasSavedRelief && !currentlyAll` 会退化成 `FORCE_ALL`，所以即使调用方忘了先 reconcile 也不会把旧值当原状态。
- **跨重启决策表的增量**：`onMaidLoaded` 重新登记时，标签里的 `originalSchedule` 要满足"当前日程仍是 ALL"才装回 `SCHEDULE_RELIEF`；否则视为玩家接管，把该字段从内存与重新登记的对象里去掉（不再恢复）。丢弃指令的三条路径（已到期 / 不可信 / 没名额）走 `restoreFromReload`，那里现在也会先装回 `SCHEDULE_RELIEF` 再 `restoreScheduleRelief`。`clear()`（服务端停止）继续只清内存、不恢复，把 `SCHEDULE_RELIEF` 一并清空即可。
- **自动工作调度器保持原样（只在报告里评估）**：`TaskAutoSwitchHandler` 的单行钩子 `MoveDirectiveManager.isActive(maid.getUUID())` 是按"每只女仆有没有生效指令"判断的，`work_time` 同样命中，于是调度器在 `work_time` 期间不替她自动切任务。这是合适的：`work_time` 的目的是"让她能工作"，而"能不能工作"由日程决定；调度器跳过只是不自动切换，玩家 / 模型手动切工作任务照常生效（`SwitchWorkTaskTool` 不读这个钩子）。反过来若让调度器在 `work_time` 期间照常工作，就等于给 `work_time` 加了"顺便自动选任务"的隐藏语义，反而更难解释。因此没有改 `priority/` 下任何文件。
- **验证状态**：真实 mapped classpath 全量 `javac`（167 个源文件）exit 0；JUnit Platform 跑全包 **187 个用例 0 失败**（本次新增 `ScheduleReliefTest` 11 个，并把 `MoveOrderNbtTest` 扩到 15 个、`MoveOrderDataTest` 扩到 10 个、`MoveOrdersTest` 扩到 20 个、`MoveActionTest` 扩到 11 个）。**未做实机验证**：主模组日程刷新 / 睡觉状态变化与我们 `setSchedule` 的交互、`work_time` 后玩家切工作任务的真实体验、重启后原日程恢复的时序。

### 2026-10-10 移动指令拆成四个工具（`ai/tool/Maid{Move,Guard,WorkTime,Cancel}Tool`）

- **只拆"工具面"，不拆"指令表"**：`maid_move` / `maid_guard` / `maid_work_time` 登记的仍是同一种 `MoveOrderData`（`MoveAction` 枚举保持原样），共用一张指令表、一套暂停跟随与恢复、一个 tick 循环。原因是"同一只女仆同一时间只有一条指令、新指令覆盖旧指令"这条规则本来就是跨动作的；拆成多张表反而要处理"守护被走位覆盖"之类的跨表冲突。`MoveAction` 里仍保留 `CANCEL`（存档里的旧数据与内部结束指令用），所以 `MoveOrderNbt` 的存档格式没有任何变化，旧存档里写着 `guard` / `work_time` 的指令照常能读回来。
- **公共骨架 `MoveToolBase<R>`**：服务端线程切换（`onCallAsync` → `runOnServerThread`）、`MoveEnabled` 总开关、女仆/世界校验、拒绝日志都在这里；子类只实现 `handle(result, ctx)` 返回给模型的文字。以前这些全在一个 544 行的 `MaidMoveTool` 里，每加一个动作都要穿过同一个大 `dispatch`。
- **`MoveOrderPlacer` 收口"登记一条指令"的流程**：建指令 → `onOrderReplaced`（暂停跟随）→ `onScheduleRelief`（放宽日程）→ `putOrder`（占名额）→ 名额不够时撤销前两步的副作用。原来 `dispatch` 和 `workTime` 各抄了一遍，现在三个工具共用。**顺序不能换**：必须先处理暂停/放宽再 `putOrder`，名额不够时才有东西可撤。
- **`MoveDirectiveManager` 瘦身 1264 → 1002 行**：无状态的叶子方法按职责搬出——`MoveNavigation`（`walkToEntity` / `walkToPoint` / `findStandable` / `teleportMaid` / `hasCantReachMemory`）、`GuardCombat`（守护时设攻击目标）、`GoHome`（`go_home` 与 `hasWorkPos`）、`MoveLog`（`shortId` / `fmt` / `fmtVec`）。`GoHome` 要调用管理器的 `removeOrder`，所以它从 `private` 放宽成包内可见。有状态的部分（指令表、暂停跟随记录、日程放宽记录、跨重启恢复、tick）暂时都留在管理器里；日程放宽那一块很快会被新的"加班"机制整块替换，所以这次没有为它单独做拆分。
- **`maid_cancel` 带一个可选的 `reason` 参数**：主 Mod 自带的所有工具都至少有一个参数，没有先例证明"完全没有参数"的工具能被各家模型接口接受（`ObjectParameter` 的 `properties` / `required` 为空时会序列化成什么也没验证），而我们在云端没法实机试。`reason` 只写日志。
- **模型传错工具时给提示而不是报"未知"**：`maid_move` 收到 `guard` / `work_time` / `cancel` 时返回 `MoveText.wrongTool`（指明该用哪个工具），小模型下一轮就能改对。日志里 reason=`wrong-tool` 可以统计这种情况有多常见。
- **顺手修的两个小问题**：英文对话下"永久指令已生效"的文字里混进了硬编码的中文动作描述（原来用 `detail()` 拼中文再传给 `startedPermanent`），现在统一走 `MoveText.orderVerb(zh, ...)`；`MoveDefaultDurationSeconds` 大于 `MoveMaxDurationSeconds` 时缺省时长原来不受上限约束，现在由纯函数 `MoveMath.resolveDurationSeconds` 一并夹住（有单测）。
- **验证状态**：云端 Gradle 全量构建通过，190 个单测 0 失败（新增 `MoveMath.resolveDurationSeconds` 的 3 个）。**未做实机验证**：四个工具的参数 schema 在真实模型接口上的表现（尤其是 `maid_cancel` 只有一个可选参数）、小模型选工具的准确率、拆分前后女仆行为是否一致。

### 2026-10-10 加班 T5a：临时改日程收归 `ai/overtime/OvertimeManager`

- **为什么把"临时改成全天工作"从指令里拿出来**：原来原日程存在 `MoveOrderData.originalSchedule`、内存里的 `SCHEDULE_RELIEF`、`work_time` 又单独走一遍，三份状态只能靠"指令还在"这一个前提保持一致；而"玩家让她在晚上切换工作任务"根本没有指令可以挂。现在改日程的唯一主人是 `OvertimeManager`：一张 `UUID → State(原日程, 持有者集合, 切换任务到期 tick)` 的表，持有者是 `ORDER`（一条生效中的移动类指令）和 `WORK_TASK`（切换工作任务）。**最后一个持有者释放才恢复日程**。
- **沿用"状态守卫"的全部约定**（与暂停跟随一致，判定在纯函数 `ScheduleRelief` 里，有单测）：已在加班就沿用旧记录（绝不把已经被改成 ALL 的日程当成原日程再存）；睡觉 / 本来就是 ALL / 配置不允许时不动；加班期间日程不再是 ALL = 玩家接管，丢记录不恢复；女仆不在场时 `release` 直接返回、状态原样留到重新加载；`clear()`（服务端停止）只清内存。
- **接入点是"重定向主模组那一次 `getScheduleDetail()` 调用"**（`SwitchWorkTaskToolMixin`，`@Redirect` + `require = 0`）：读字节码确认 `SwitchWorkTaskTool#onCall` 里恰好只有这一处调用，顺序是 `setTask` → `onFunctionCallSwitch` → `getScheduleDetail()`，非 WORK 就直接返回"不在工作时间"。我们批准加班时返回 `Activity.WORK`，主模组后面的逻辑（含设置攻击目标）就按工作时间正常跑完；不批准（睡觉中、配置关闭、玩家刚改过日程）就原样返回真实活动，行为和以前一样。比"事后改返回字符串"干净：事后改会重复 `addToolResult`。
- **"活干完了"没做成真正的检测**：`priority/detection` 的检测器是给调度器设计的（有逐 tick 的方块 / 寻路预算和跨周期的扫描游标），随手拿来按需调用，农耕类检测器拿不到游标会一直返回 `BLOCK_BUDGET_EXHAUSTED`，等于没用。所以切换任务触发的加班用"切到待命任务 / `OvertimeTaskHoldSeconds` 内没有新的切换"代替，并在文档里明说。以后若要做，应该复用调度器已经缓存的 `MaidDetectionCache` 结果，而不是另起扫描。
- **`ORDER` 持有者的兜底**：`OvertimeManager.onServerTick` 里发现状态占着 `ORDER` 但 `MoveDirectiveManager.isActive` 为 false（指令数据读不出来、被丢弃等）就释放，免得女仆一直卡在全天工作。`WORK_TIME` 指令则反过来：它的唯一作用是加班，所以 `step` 里发现加班管理器已不再持有 `ORDER`（玩家接管）就结束指令。
- **加载顺序不能换**：`SincerelyMod.onEntityJoinLevel` 必须先 `OvertimeManager.onMaidLoaded` 再 `MoveDirectiveManager.onMaidLoaded`，因为指令到期 / 数据不可信时会走 `restoreFromReload` → `release(ORDER)`，那时加班状态必须已经装回内存。
- **旧存档兼容**：旧版本把原日程写在指令标签的 `originalSchedule` 键里。`MoveOrderData` 去掉该字段、`MoveOrderNbt` 不再写它，但保留 `readLegacyOriginalSchedule` / `stripLegacyOriginalSchedule`：`OvertimeManager.onMaidLoaded` 在没有自己的标签时接收一次并立即去掉旧键，免得之后重复接收。
- **`MaidLookup`**：把散落的"按 UUID 在所有维度找女仆"收成一个小工具（`MoveDirectiveManager`、`OvertimeManager` 在用）。
- **验证状态**：云端 Gradle 全量构建通过；新增 `ScheduleTimelineTest`（6）、`OvertimeNbtTest`（11），并更新 `MoveOrderDataTest` / `MoveOrdersTest` / `MoveOrderNbtTest`。**未做实机验证**：`SwitchWorkTaskToolMixin` 的 `@Redirect` 能否在真实环境里命中（`require = 0`，失败只会退回"不在工作时间"）、加班期间主模组日程刷新与我们 `setSchedule` 的交互、重启后的恢复时序。

### 2026-10-10 加班 T5b：疲劳软上限 + 点心

- **纯逻辑与副作用分开**：`OvertimeFatigue`（累积 / 额外时长 / 要点心阶段 / 歇够清零，不依赖 Minecraft 类型）、`TreatTable`（解析 `命名空间:物品=秒数`，容错）、`OvertimeText`（中英台词）都有单测；`OvertimeManager` 里的 `tickFatigue` / `onTired` / `tickAsking` / `sendToRest` 只做取背包、说话、走路、取消指令这些副作用。
- **只在原日程是休息时段时累积**：判断用 `ScheduleTimeline.phaseAt(原日程, dayTime)`，所以工作 / 休闲时段的加班不会累。`ticksUntilPhaseChange` 用来算“这段休息什么时候结束”，锁定期（`LOCKOUT`，到那个游戏刻为止）据此设置。
- **疲劳与锁定期只存内存**：重启清零。存进标签要多一套兼容与迁移，而这是个软约束，不值得；已写进 WIKI 的限制里。
- **点心的收法有两条路**：右键（`OvertimeTreatHandler` 监听 `PlayerInteractEvent.EntityInteract`，仅主手、仅主人、仅在她“要点心”阶段才吃掉这次交互，其余交互照常交给主模组打开界面）和放进背包（要点心阶段每 20 tick 扫一次背包）。女仆台词里不写点心名字（`MaidSpeech` 只吃纯字符串），点心名字走另一条可本地化的系统消息发给主人。
- **去休息时取消手头的指令**：`sendToRest` 调 `MoveDirectiveManager.cancelAndRestore`，让指令占着的暂停跟随、加班一并恢复，再对剩下的加班状态做一次强制收尾。这是临时选择，已记入“待用户决定”。
- **要点心期间移动指令暂停下发**（`MoveDirectiveManager.step` 里 `OvertimeManager.isAsking`），否则每 10 tick 重下 `WALK_TARGET` 会和“走向主人”互相拉扯。
- **验证状态**：云端 Gradle 全量构建通过（229 个测试）；新增 `OvertimeFatigueTest`、`TreatTableTest`，并给 `ScheduleTimelineTest` 加了 `ticksUntilPhaseChange`。**未做实机验证**：右键递点心是否被主模组的交互抢先处理、`walkToEntity` 走向主人的效果、取消指令后的状态恢复。

### 2026-10-10 冒险日程 T6：给主模组的日程枚举追加第四个值

- **选方案 A（真的往 `MaidSchedule` 里加一个枚举值）**：界面按钮、`switch_schedule` 工具、存档、网络同步都直接认它，代价是要对主模组里所有对日程做 `switch` 的地方逐处补齐（读字节码查出来的清单见 WIKI 「7. 冒险日程」），并且有卸载风险（日程按名字存档，`valueOf` 无兜底，README 有警告）。
- **枚举扩展的做法**：`MaidScheduleEnumMixin` 在 `<clinit>` 里 `$VALUES` 赋值之后用 `@Invoker("<init>")` 造新值、换数组。运行期统一经 `AdventureSchedule.value()` 取，混入没生效就是 `null`，所有冒险逻辑自动不触发；启动时（`FMLCommonSetupEvent`）解析一次并写一行 INFO 日志，是否生效一眼能看到。
- **时间表是纯逻辑**：`AdventureWindow`（起点、时长、跨天窗口、分段、时间文字）有单测；`AdventureSchedule.timetable()` 把分段转成原版 `Schedule`，配置变了就重建。`ScheduleTimeline` 也认 `ADVENTURE`，加班疲劳对冒险女仆同样成立。
- **在真实服务端验证过混入**（这次第一次做，以后改混入都建议这样）：`sh gradlew runServer --no-daemon`（先在 `run/eula.txt` 写 `eula=true`；`run/` 已被 `.gitignore`）。临时加一个 `ServerStartedEvent` 监听，在里面 `new EntityMaid(level)`、设日程、读 `getScheduleDetail()`、`brain.updateActivityFromSchedule` 后看 `isActive`、存读 NBT、放一张原版床后反射调 `MaidBedTask.findBed/start`；全部符合预期后删掉这段临时代码。日志里 `Mixing XxxMixin ... into ...` 的 DEBUG 行能确认某个混入有没有套上（目标类被加载时才会出现）。注意 `pkill -f` 的模式要写成 `[f]orgeserveruserdev` 这种，否则会杀掉自己所在的 shell。
- **验证状态**：云端 Gradle 全量构建通过；服务端实测——枚举为 `[DAY, NIGHT, ALL, ADVENTURE]`，各时刻 `getScheduleDetail()`（0/5000/16999 工作、17000/20999 休息、21000/23999 工作）与大脑活动一致，存读档后仍是 `ADVENTURE`，`findBed` 对冒险女仆找到了原版床、对白班女仆返回 `null`，`start` 后女仆处于睡眠、床被标成有人；`SwitchWorkTaskToolMixin`（T5a）也在这次启动里确认套上。**未做实机验证（需要客户端）**：日程按钮的图标与提示（`ScheduleButtonMixin`）、`ScheduleModeContextMixin` 在真实对话里的文字、女仆真的走到原版床前躺下、天亮后起床。

### 2026-10-10 回复节奏 T8：`<等待>` 停顿标记与模型分句

- **标记的剥除与错开显示分两处，因为中间隔着主模组的代码**：剥除在 `LLMCallback.onSuccess` 的开头（`LLMCallbackReplyMarkerMixin`，对 `ResponseChat` 的两个公开字段 `chatText` / `ttsText` 直接改），这样主模组写历史、气泡、TTS 看到的都是没有标记的正文；显示在稍后的 `ChatBubbleManager.addLLMChatText` 里发聊天消息的那次调用（`MaidChatBroadcastMixin` 的重定向）。两处不能直接传参，所以解析时把"各段正文 + 停顿"登记进 `PendingReplyPauses`（按女仆，线程安全，30 秒过期），发聊天栏时按"同一只女仆 + 规整过空白的同样正文"取走，对不上就当没登记、照常即时显示。
- **剥除恒开、与开关无关**：模型自己乱写标记也不能露出来；开关只管"是否告诉模型可以写"（提示词）和"是否按标记错开聊天行"。标记所在位置换成换行，因为标记前后本来就是两句话。不动没有标记的文本（`strip` 对没有标记的输入原样返回同一个字符串），保证默认配置下行为和以前一模一样。
- **纯逻辑都在 `chatbar/ReplyPauses`（解析、剥除、排显示计划）与 `DelayedQueue`（按 tick 到点执行）**，各有单测；运行期只有 `ReplyDelivery`（挂在 `SincerelyMod` 的服务端 tick 与停止事件上）和两个混入是胶水。显示计划里单个标记的停顿夹在 0.3~10 秒，一条回复的累计停顿再受 `ReplyPauseMaxTotalSeconds` 约束。
- **提示词放在已有的系统消息注入点**（`MemoryGuidanceMixin`，与记忆说明、语言指令同处）；`ReplyStylePrompts.of` 在两个开关都关时返回 `null`，不加任何一个字。提示词是我写的、中英各一套，限定"一次回复最多 3 个、不放开头结尾、不代替省略号、只写在聊天文字里"。
- **局限（已写进 WIKI）**：气泡与语音仍一次性出现，只有聊天行错开；开了语音时聊天行本身由主模组在语音就绪时触发，停顿从那一刻算起；晚显示的行在玩家下线或记忆整理期间丢弃；重启后队列清空。
- **验证状态**：云端 Gradle 全量构建通过（279 个测试，新增 `ReplyPausesTest` / `DelayedQueueTest` / `PendingReplyPausesTest` / `ReplyStylePromptsTest`）；服务端实测：临时代码里 `new LLMCallback(...)` 后调 `onSuccess(new ResponseChat("…<等待>…<等待 2>…---ttsA<wait>ttsB"))`，聊天与语音正文里的标记都被换成了换行，三个相关混入（`LLMCallbackReplyMarkerMixin` / `MaidChatBroadcastMixin` / `MemoryGuidanceMixin`）都套上了（临时代码已删）。**未做实机验证（需要玩家与真实模型）**：聊天栏里错开显示的实际观感、模型是否真的按提示词分句 / 写标记、与语音同开时的时序。

### 2026-10-11 加班疲劳：话由模型生成，抱怨成为总开关

- **`ai/line/MaidLines`：通用的"请模型按人设说一句话，说不出就说备用台词"**。前缀用 `MaidAIChatManagerAccessor`（撬 `getMessages`，与玩家聊天同构，人设与历史自然带上）+ 一段情景提示词（`LinePrompts`，我写的，中英各一套）；回调 `LineCallback` 继承 `LLMCallback` 但自己收尾（不调父类：不写历史、不播 TTS、不弹思考气泡、失败不给主人发红字），并实现 `SilentCallback`，`LLMCallbackTrackingMixin` 据此不登记它（否则记忆聊天追踪十分钟后必然误报）。
- **"一定会说"是硬要求**：所有走不通的路（LLM 总开关关、站点不可用、没人设、前缀拼不出、同步出错、回复为空、失败）和 15 秒超时兜底都落到调用方传入的写死台词（`OvertimeText`）；回复与超时只算先到的一个（`AtomicBoolean`）。唯一的例外是“去睡觉”那句迟到而她已经睡着时不再出声。主人额外收到的可本地化提示消息（点心名字）照旧立即发出，所以即使模型很慢，主人也立刻知道她要什么。
- **抱怨 = 疲劳总开关**：`OvertimeManager.fatigueEnabled()` = 疲劳上限开关 且 抱怨开关；关掉抱怨就不累、不要点心、不去休息、没有休息锁定。原先“只在点心关闭时管抱怨”的语义去掉了。
- **访问器必须登记进 `tlm_sincerely.mixins.json`**：这次忘了登记，是在真实服务端里调用时被 `IllegalClassLoadError` 当场抓出来的——纯单元测试和编译都看不到，这类混入改动一定要在 `runServer` 里碰一次。访问器方法名带了 `tlmSincerely$` 前缀，避免和 Self Talk 里撬同一个方法的访问器在目标类里重名。
- **验证状态**：构建通过（291 个测试，新增 `LineTextTest`、`LinePromptsTest`）；服务端里确认访问器套上、没有模型时的回退路径不抛异常。**未验证（需要真实模型）**：生成的话的质量与时延、和语音同开时的表现。

### 2026-10-12 守护任务：名单 + 对账，不另造一套行为

- **守护 = 独立任务 `GuardTask`（继承 `TaskAttack`）+ 守护名单 + 驱动器**。名单是不可变的纯逻辑（`GuardCondition` / `GuardEntry` / `GuardRules` / `GuardDecider`，各有单测，包含“女仆 1 残血则优先守她、恢复后回去守女仆 2”的场景）；`GuardData`（名单 + 到期游戏刻 + 进入前的任务 UID）通过 `GuardTaskDataKeys` 存成 TLM 任务数据，随女仆存档。
- **不新写“跟随 + 打怪”的行为，而是对账**：`GuardTaskDriver` 在 `MaidTickEvent` 里每 20 tick 对每只守护任务女仆算一次“此刻该守谁”，与她身上的 `GUARD` 指令比较，不一致就登记新指令（覆盖旧指令），没人可守就撤指令。跟随、攻击、卡住传送、暂停跟随、加班、重启恢复全部沿用已验证的指令机制。代价是守护指令是“永久”的（时限由 `GuardData.expireAtGameTick` 管，到期时驱动器撤指令、回到原任务）。
- **边界约定**：别人下的指令（名单外的目标）不插手；工具里明确要求守护时才撤掉原有的其他指令；自己下的指令被中途丢弃（目标掉线、走不通放弃）后冷却 20 秒再重下，免得“放弃→重下→再放弃”刷屏；重启后内存里没有记录，但 `MoveDirectiveManager` 已恢复的名单内守护指令直接认领；任务被别处换走时撤自己的指令并清掉到期时间。
- **血量条件的滞回**：守起来之后要回升到 门槛 + 4 点（百分比 +15 个百分点）才算好，被更高优先级项挡住期间也持续更新“已成立”状态，轮到它时是连续的。
- **自动工作**：`TaskAutoSwitchHandler` 对守护任务的女仆和对有移动指令的女仆一样不切换。
- **验证状态**：构建通过；服务端里（临时代码，已删）用真实女仆验证：任务与任务数据已注册、NBT 往返与坏数据容忍、血量低于门槛才登记指令、滞回区内保持、恢复后撤指令、`leave` 回到原任务、到期由 `MaidTickEvent` 自然驱动结束。配置界面见下一小节。**尚未做**：（图标已做：任务图标是 `ItemStack`，所以注册了一个只当图标用的物品 `GuardItems.GUARD_TASK_ICON`，不进创造标签页；贴图 `textures/item/guard_task_icon.png` 由 Opus 5.5 绘制；游戏里的显示效果尚未实机确认）、真实模型对 `maid_guard` 新参数的理解、实机战斗观感。

### 2026-10-12 守护任务配置界面

- **打开路径**：`GuardTask#getTaskConfigGuiProvider` 返回 `GuardConfigContainer`（继承 `TaskConfigContainer`，类型登记在 `AutoWorkMenus.GUARD_CONFIG`），主模组的“任务配置”按钮经 `MaidConfigMessage` 调它；屏幕在 `AutoWorkClientSetup` 里 `MenuScreens.register`。屏幕继承 `MaidTaskConfigGui`（关闭时会让主模组刷新女仆大脑，和其他任务配置页一致）。代价：近战任务自带的“攻击对象”配置页（`AttackTaskConfigGui`）在守护任务下被取代。
- **界面不直接改数据**：每个操作发一个 `GuardEditC2SPacket`（挂在原有的 auto_work 频道上，索引 14，协议版本 4→5），服务端校验后改名单并立刻对账一次；名单通过任务数据同步回客户端，界面在 `containerTick` 里发现数据变了就重建控件。**操作用目标 UUID 定位而不是下标**，因为界面上的名单可能比服务端旧（模型刚改过）。服务端校验：发送者能控制这只女仆；新增目标必须是主人（在线）或同一主人名下另一只女仆；名单满了拒绝；`NaN` 数值忽略。
- **三个视图 + 原版按钮**：名单 / 编辑 / 添加，全部用原版 `Button` 与 `EditBox`，固定的面板内坐标（面板位置与自动工作配置页一致）。没有自绘贴图，是因为云端没法渲染客户端、没法目测，自绘的贴图坐标无法验证。输入框改数字后停顿 15 tick 自动提交；正在输入时不重建控件（免得丢焦点）；输入框聚焦时把按键交给输入框（否则按 E 会关掉界面）；换条件种类时门槛用新种类的默认值，不把旧种类的数字带过去。
- **语言文件里的字面百分号必须写 `%%`**：原版翻译格式化会把结尾的 `%` 当格式符，抛 “Unsupported format” 的异常；带参数的 `%s%%` 同理。
- **验证状态**：构建通过（324 个测试，新增 `GuardRules.shift` 的测试）；服务端里（临时代码，已删）用 `FakePlayer` 与真实女仆验证了编辑包的服务端逻辑：加入同主人女仆成功、陌生人 / 自己 / 随机 UUID 被拒、上移、改条件与半径、`NaN` 忽略、删除、取消时限、满 8 项被拒。**未验证（云端无法渲染客户端）**：界面的实际外观与排版（文字是否被截断 / 重叠、按钮位置）、点击与输入的手感、主模组的“任务配置”按钮是否真的打开这个界面、物品图标显示。
