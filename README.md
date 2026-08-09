# MyCraftWorldChestLoot

适用于 Paper 1.12.2 的大世界随机宝箱插件。
插件版本为 `1.0.3`。

## 依赖

#### 硬依赖

- WorldGuard 6.2.2
- WorldEdit（与 WorldGuard 6.2.2 配套版本）

#### 软依赖

- Zaphkiel（用于通过物品 ID 动态构建自定义物品）

## 构建

项目使用 Java 8 字节码。Windows 环境可以执行：

```powershell
.\gradlew.bat clean build
```

成品位于 `build/libs/MyCraftWorldChestLoot-1.0.3.jar`。

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

奖池仅从 `LootTables/*.yml` 加载，文件使用 PhatLoots 的 `PhatLoot`、`LootCollection` 和 `Item` 序列化结构。`SampleLootZaphkiel.yml` 额外展示本插件的 `ZaphkielItem` 格式。

`settings.ShuffleLoot` 控制奖励是否随机散布。每个抽中的奖励条目占用独立槽位，即使多个条目生成了完全相同的物品也不会在首次生成时自动合并。

`settings.AllowDuplicateItemsFromCollections` 控制集合在一次抽取多个奖励时是否允许重复命中同一个奖励条目。设置为 `false` 时采用不放回抽取；设置为 `true` 时采用放回抽取，与 PhatLoots 原配置行为一致。

`settings.ForgetInventoryTime` 控制虚拟箱子内容在 JVM 内存中的保留秒数。缓存过期但冷却尚未结束时，玩家会看到同尺寸的空箱子。

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
