# MyCraftWorldChestLoot

适用于 Paper 1.12.2 的大世界随机宝箱插件。
插件版本为 `1.1.1`。

## 依赖

#### 软依赖

- WorldGuard 6.2.2（用于区域绑定）
- WorldEdit（与 WorldGuard 6.2.2 配套，用于区域坐标解析）
- Zaphkiel（用于通过物品 ID 动态构建自定义物品）
- PlaceholderAPI（用于解析奖池开启条件中的玩家占位符）

没有安装 WorldGuard 或配套 WorldEdit 时，插件仍可正常启动，但不会解析 `regions` 区域绑定，只支持世界级绑定。Zaphkiel 未安装时，仅无法生成对应的 Zaphkiel 自定义物品。PlaceholderAPI 未安装时，不含占位符的常量条件仍可判断，包含 `%...%` 占位符的条件会判定为不满足。

## 构建

项目使用 Java 8 字节码。Windows 环境可以执行：

```powershell
.\gradlew.bat clean build
```

成品位于 `build/libs/MyCraftWorldChestLoot-1.1.1.jar`。

## 配置

首次启动生成：

```text
plugins/MyCraftWorldChestLoot/
├─ config.yml
├─ message.yml
├─ cooldowns.yml
└─ LootTables/
   ├─ SampleLoot.yml
   └─ SampleLootZaphkiel.yml
```

奖池仅从 `LootTables/*.yml` 加载。推荐奖池文件只保留 `LootList`，其中继续使用 PhatLoots 的 `LootCollection` 和 `Item` 序列化结构；旧版完整 PhatLoots 文件仍可读取。`SampleLootZaphkiel.yml` 额外展示本插件的 `ZaphkielItem` 格式。

`settings.ShuffleLoot` 控制奖励是否随机散布。每个抽中的奖励条目占用独立槽位，即使多个条目生成了完全相同的物品也不会在首次生成时自动合并。

`settings.AllowDuplicateItemsFromCollections` 控制集合在一次抽取多个奖励时是否允许重复命中同一个奖励条目。设置为 `false` 时采用不放回抽取；设置为 `true` 时采用放回抽取，与 PhatLoots 原配置行为一致。

`settings.ForgetInventoryTime` 控制虚拟箱子内容在 JVM 内存中的保留秒数。缓存过期但冷却尚未结束时，玩家会看到同尺寸的空箱子。

### 奖池设置

`config.yml` 中的 `settings-loot-tables.<奖池名>` 可以集中覆盖奖池的 `Global`、`Name` 和 `Reset`。这里的设置优先于 `LootTables/<奖池名>.yml` 中的旧写法；未配置的字段继续读取奖池文件，双方都没有配置时使用 `settings` 下的默认值。GUI 标题优先级为 `links` 映射标题、`Name`、奖池文件名。

`RoundDownTime` 也会按上述优先级读取，并用于将相对冷却起点对齐到整分钟、整小时或整天。`LootConditions` 同样按上述优先级读取。

```yaml
settings-loot-tables:
  SampleLoot:
    Global: false
    LootConditions: []
    Name: SampleLoot
    Reset: "2h"
```

### 奖池开启条件

`LootConditions` 使用 PlaceholderAPI 占位符进行判断。列表从上到下按 AND 关系依次检查；第一条不满足后立即停止，不打开宝箱、不写入冷却，也不执行开箱命令：

```yaml
LootConditions:
- "%player_level% >= 100"
- "%player_health% >= 20"
```

支持数字运算符 `>=`、`<=`、`>`、`<`、`==`、`!=`，字符串支持 `==` 和 `!=`，字符串比较不区分大小写。行内可以使用 `&&`、`||` 和括号，优先级为比较、`&&`、`||`：

```yaml
LootConditions:
- "(%player_level% >= 100 && %player_health% >= 20) || %player_name% == server"
```

比较带空格的字符串时需要使用单引号或双引号。每条条件可在末尾使用 `--message` 设置失败文本；没有配置时发送 `message.yml` 中的 `condition-not-met`：

```yaml
LootConditions:
- "%player_level% >= 100 --message &c等级未满100级"
- "%player_health% >= 20 --message &c生命未满20点"
- "%player_name% == server --message &c你的名称不为server"
```

非法表达式、无法解析的占位符以及对字符串使用大小关系运算符都会按条件不满足处理。自定义失败文本支持 `&` 颜色代码。

除 PAPI 占位符外，还支持以下不调用 PlaceholderAPI 的原生变量：

```yaml
LootConditions:
- "weather == sunny"
- "time >= 1800"
- "time == day"
- "permission == mycraft.vip"
- "level => 100"
- "experience => 10000"
- "health => 20"
- "foodlevel => 10"
- "saturation => 10"
```

`weather` 的取值为 `sunny`、`raining`、`thundering`，仅支持 `==`、`!=`。`permission == <权限>` 表示玩家拥有该权限，`permission != <权限>` 表示玩家不拥有该权限，也仅支持 `==`、`!=`。

`time` 是玩家所在世界的 `0-23999` 游戏刻，可使用所有数值运算符。与 PhatLoots 原行为一致，`time == day` 匹配 `0-13000` 及 `23850-23999`，`time == night` 匹配 `13000-23850`；边界 `13000` 和 `23850` 同时属于两者。`day`、`night` 写法只支持 `==`、`!=`。

`level` 是当前等级，`experience` 是根据当前等级和经验条进度计算的可用总经验值，`health` 是当前生命值，`foodlevel` 是饥饿值，`saturation` 是饱和度。这些数值变量支持全部比较运算符。为兼容配置习惯，`=>` 与 `>=` 含义相同。

### 集合条件与动态概率

`LootCollection` 可以单独配置 `LootConditions`。条件不满足时，该集合会在概率计算前从候选列表中排除，其余集合重新按自身权重参与抽取。集合条件静默执行，不向玩家发送消息，也不支持 `--message`：

```yaml
- ==: LootCollection
  LootConditions:
  - "level >= 100"
  - "%player_health% >= 20"
  LootList:
  - ==: Item
    # ItemStack 配置省略
    Probability: 100.0
  LowerNumberOfLoots: 1
  UpperNumberOfLoots: 1
  Probability: 20.0
```

`Item`、`ZaphkielItem` 和 `LootCollection` 的 `Probability` 均可填写返回数值的 PAPI 占位符：

```yaml
Probability: "%player_food_level%"
```

占位符结果会作为当前玩家本次开箱时的概率或权重。结果无法解析为数值、PlaceholderAPI 未安装或占位符未解析时，该条目的概率按 `0` 处理。负数同样按 `0` 处理；在独立百分比抽取中，大于 `100` 的结果等同于必定命中，在集合权重抽取中则作为普通权重参与比例计算。

同一次奖励生成中，相同集合条件和动态概率只计算一次，相同条件占位符也会缓存，以减少 PlaceholderAPI 调用。若奖池使用 `Global: true`，宝箱缓存内容由刷新后第一个生成奖励的玩家状态决定，之后打开该缓存的玩家看到相同内容。

`Reset` 支持相对时长：

```yaml
Reset: "0d+2h+0m+0s"
Reset: "2h"
Reset: "2.5h"
Reset: "2h+30m"
Reset: "2h+1800s"
```

`0d+2h+0m+0s` 与 `2h` 都表示两小时；`2.5h`、`2h+30m`、`2h+1800s` 都表示两个半小时。单位分别为 `d` 天、`h` 小时、`m` 分钟、`s` 秒。

固定时间刷新使用服务器系统时区。`daily` 表示每天在指定时间刷新：

```yaml
# 每天早上 7:00:00 刷新
Reset: "daily;7:0:0"
```

`weekly,<周几>` 表示每周指定日期刷新，`1=周一`、`7=周日`；`monthly,<日期>` 表示每月指定日期刷新；`yearly,<月>/<日>` 表示每年指定日期刷新：

```yaml
Reset:
- "daily;7:0:0"
- "weekly,2;19:0:0"
- "weekly,4;19:0:0"
- "monthly,20;15:0:0"
- "yearly,5/20;19:0:0"
```

上例表示每天早上7点、每周二和周四晚上7点、每月20日下午3点，以及每年5月20日晚上7点刷新。只有一个固定日程时可以直接写成单个字符串；多个日程使用列表。

`monthly` 还支持 `-1` 至 `-31` 的倒数日期：`monthly,-1` 表示每月最后一天，`monthly,-3` 表示每月倒数第三天。例如1月为29日，闰年2月为27日。正数日期在当月不存在时会跳过该月；负数倒数位置超出当月天数时同样跳过。

当多个日程落在同一时刻时，插件只保存一个最近刷新时间，因此该时刻只刷新一次。`yearly,2/29` 等日期在当前年份不存在时，会跳到下一个具有该日期的年份。

旧版 `Days/Hours/Minutes/Seconds` 四字段结构仍可读取，但 GUI 保存后会转换为新的字符串格式。

### 开关箱命令

`settings` 下可以设置四种全局默认命令：

```yaml
settings:
  default-open-console-command: "say <player> 打开了 <pool>"
  default-close-console-command: ""
  default-open-player-command: ""
  default-close-player-command: ""
```

在 `settings-loot-tables.<奖池名>` 下使用 `open-console-command`、`close-console-command`、`open-player-command`、`close-player-command` 可以覆盖对应默认命令。字段既可以是单条字符串，也可以是 YAML 字符串列表。支持 `<player>`、`<uuid>`、`<pool>`、`<world>`、`<x>`、`<y>`、`<z>` 占位符。开箱命令在每次成功打开受管界面时执行，关箱命令在该界面关闭时执行。

普通箱子和单个陷阱箱使用 27 格界面；普通大箱子和陷阱大箱子使用 54 格界面。双箱左右两部分共用同一个区域绑定、奖励缓存和冷却记录。

## 区域绑定

区域绑定格式：

```yaml
links:
  <世界名>:
    regions:
      <WorldGuard区域名>:
        <方块类型>: <奖池名>:<可选界面标题>
    default:
      <方块类型>: <奖池名>:<可选界面标题>
```

例如：

```yaml
links:
  world:
    regions:
      dungeon:
        CHEST: rare:稀有宝箱
        SKULL:plantegg: rare:茄子宝箱
    default:
      CHEST: basic:基础宝箱
```

区域匹配优先于世界默认绑定。`default` 可以省略；未匹配绑定且 `settings.default-pool` 为空时，插件不会接管该箱子。省略可选标题时，界面使用奖池原名。

WorldGuard 和配套 WorldEdit 均为软依赖。缺少其中任一插件时，`regions` 节点不会生效，可以使用以下世界级写法：

```yaml
links:
  world:
    default:
      CHEST: basic:基础宝箱
      SKULL:plantegg: rare:茄子宝箱
```

也继续支持 PhatLoots 风格的世界直写形式：

```yaml
links:
  world:
    CHEST: basic:基础宝箱
    SKULL:plantegg: rare:茄子宝箱
```

没有区域依赖时，管理员执行 `/mcwcl link <奖池>` 会自动写入对应世界的 `default` 节点。

### 模型头颅刷新点

插件支持将所有者名称为 `model:<家具索引>` 的已放置头颅作为刷新点。固定前缀由 `settings.skull-model-prefix` 配置，例如头颅所有者名称为 `model:plantegg` 时，对应绑定键为 `SKULL:plantegg`：

```yaml
links:
  world:
    regions:
      dungeon:
        SKULL:plantegg: rare:茄子宝箱
```

模型头颅必须在 `links` 中具有完全匹配的绑定才会被接管，`settings.default-pool` 不会应用于未绑定的模型头颅。头颅使用 `settings.default-gui-size` 指定的界面大小，并保留打开、关闭音效，但不会播放箱盖动画。

## 管理命令

`/wcl` 是 `/mcwcl` 的别名。

```text
/mcwcl make <奖池>
/mcwcl info <奖池>
/mcwcl show <奖池> <玩家>
/mcwcl link <奖池>
/mcwcl unlink
/mcwcl reset
/mcwcl reset <奖池|*>
/mcwcl clean
/mcwcl list
/mcwcl reload
```

`make` 创建奖池，`info` 打开管理员编辑界面，`show` 为指定在线玩家打开只读概率预览。`link` 与 `unlink` 会根据管理员准星所指箱子或模型头颅，自动识别世界、WorldGuard 区域和绑定键。

不带参数的 `reset` 重置准星所指箱子；`reset <奖池名>` 重置指定奖池；`reset *` 重置全部奖池。重置只清除现有冷却记录，不修改奖池刷新时间。

## 权限管理

所有管理命令使用权限：

```text
mcwcl.admin
```

## 致谢

#### AI辅助
- ChatGPT-5.6 Sol
- Codex

#### 我的手艺
- 测试组 bilibiliHMP
- 测试组 Hermois
- 测试组 licha
- 测试组 qingye
- 测试组 Xtlylg
- 广大冒险者
