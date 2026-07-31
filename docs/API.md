# AE2: Utility Java API

本文档描述 `com.lhy.ae2utility.api` 下的稳定 Java API。当前 `Ae2UtilityApi.API_VERSION` 为 `1`。

## 兼容边界

只有 `com.lhy.ae2utility.api` 及其子包属于公共契约。以下包是实现细节，不应由第三方模组直接引用：

- `com.lhy.ae2utility.network`
- `com.lhy.ae2utility.service`
- `com.lhy.ae2utility.mixin`
- `com.lhy.ae2utility.integration`

API 在完整 AE2: Utility 模组 JAR 中发布，不提供独立运行的 API 模组。API 使用 Minecraft、NeoForge、AE2 类型；机器注册还使用 JEI API 类型。

## 添加编译依赖

将已发布的 AE2: Utility 完整 JAR 放入开发项目的 `libs/`，仅作为编译依赖：

```groovy
dependencies {
    compileOnly files("libs/ae2utility-1.7.4.jar")
}
```

运行开发客户端/服务端时，再按正常模组依赖方式把 AE2: Utility 和它的必需依赖加入 runtime。你的 `neoforge.mods.toml` 应声明依赖：

```toml
[[dependencies.your_mod_id]]
modId="ae2utility"
type="required"
versionRange="[1.7.3,)"
ordering="AFTER"
side="BOTH"
```

如果只在安装 AE2: Utility 时启用集成，请将依赖设为 `optional`，并在访问 API 类前使用 `ModList.get().isLoaded("ae2utility")` 保护类加载。

## 入口与线程规则

```java
import com.lhy.ae2utility.api.Ae2UtilityApi;

int apiVersion = Ae2UtilityApi.API_VERSION;
var patterns = Ae2UtilityApi.patternEncoding();
var transfers = Ae2UtilityApi.recipeTransfer();
var cards = Ae2UtilityApi.cards();
var recipeFinder = Ae2UtilityApi.recipeFinder();
```

- 编码与拉料是服务端权威操作，必须在 Minecraft 服务端主线程调用，并传入 `ServerPlayer`。
- API 不代替你的客户端数据包。客户端操作应先发给你的服务端 handler，再在 handler 中调用 API。
- 机器和配方分类器应在 JEI 注册相应 handler/索引之前注册，推荐在模组 common/client setup 阶段完成。
- 编码请求仍受 AE2: Utility 服务端配置、批次上限、当前菜单、ME 安全权限、空白样板和重复样板检查约束。

## 样板编码

输入使用 AE2 `GenericStack`。每个外层输入元素代表一个配方槽，内层列表代表该槽允许的候选原料。

```java
var request = new PatternEncodingRequest(
        inputSlots,
        outputs,
        recipeId,
        "Copper Plate",
        "press",
        "Press",
        PatternUploadMode.ENCODE_ONLY,
        false,
        false,
        true,
        false
);

PatternEncodingResult result = Ae2UtilityApi.patternEncoding()
        .encode(serverPlayer, request);
```

批量请求共享会话 id，并由服务端执行数量限制：

```java
var batch = new PatternEncodingBatch(requests, sessionId, false);
PatternEncodingResult result = Ae2UtilityApi.patternEncoding()
        .encodeBatch(serverPlayer, batch);
```

`PatternUploadMode.UPLOAD` 会进入当前可用的上传路径。若 ExtendedAE Plus/ECO 不可用、供应器需要玩家选择或目标拒绝写入，现有服务会通过聊天提示和上传队列反馈处理结果。

`PatternEncodingResult.ACCEPTED` 表示请求已通过 API 入口并交给同步服务处理，不表示一定生成了样板；最终结果仍由服务端业务校验决定。

## 配方拉料

`IngredientRequest` 会复制传入的 `ItemStack`，调用方后续修改原列表不会改变请求。

```java
var ingredients = List.of(
        new IngredientRequest(List.of(new ItemStack(Items.IRON_INGOT)), 2)
);

var options = new TransferOptions(false, true);
var result = Ae2UtilityApi.recipeTransfer()
        .pullToOpenTerminal(serverPlayer, ingredients, options);
```

可用目标：

- `pullToOpenTerminal`：当前打开的 AE2 存储/合成/样板编码菜单。
- `pullToInventory`：通过玩家可用的无线终端，从 ME 网络取出物品并放入背包。
- `pullToMachine`：向当前机器菜单的注册输入槽填充物品。

`maxTransfer` 会尝试计算当前库存和槽位容量允许的最大组数。`craftMissing` 仅对支持自动合成缺失材料的终端路径有效。

## 注册机器拉料

机器注册是客户端 JEI 集成点，id 必须使用你自己的命名空间：

```java
Ae2UtilityApi.recipeTransfer().registerMachine(
        new MachineTransferRegistration(
                ResourceLocation.fromNamespaceAndPath("example", "crusher"),
                CrusherMenu.class,
                EXAMPLE_CRUSHING_RECIPE_TYPE,
                new int[] { 0, 1 }
        )
);
```

约束：

- 同一 id 只能注册一次；重复注册抛出 `IllegalArgumentException`。
- `inputSlotIndices` 是 `AbstractContainerMenu` 中的真实槽位索引，不是 JEI 布局索引。
- 注册应在 JEI 调用 AE2: Utility 的 `registerRecipeTransferHandlers` 之前完成；过晚注册不会补建已经完成的 JEI handler。
- 服务端调用 `pullToMachine` 时必须使用相同 id、当前 `containerId`，并通过菜单类型校验。

内置 profile 可以用 `ae2utility:furnace`、`ae2utility:smoker`、`ae2utility:blast_furnace`、`ae2utility:stonecutter`、`ae2utility:smithing` 和 `ae2utility:inscriber` 访问。

## 功能卡

### NBT 撕裂规则

```java
CardApi cards = Ae2UtilityApi.cards();
NbtTearRule rule = cards.getNbtTearRule(cardStack);

cards.setNbtTearRule(cardStack, new NbtTearRule(Set.of(itemId)));
boolean matches = cards.matchesNbtTear(expectedKey, returnedKey, rule);
```

`NbtTearRule.ALL_ITEMS` 的 id 集合为空，表示允许所有未进入服务端黑名单的物品按 item id 放宽匹配。写入方法要求传入真实 AE2: Utility 功能卡，否则抛出 `IllegalArgumentException`。

### 红石发信状态机

卡模式和持续时间可以直接读取或更新：

```java
cards.setRedstoneMode(cardStack, RedstoneSignalMode.UNTIL_RECIPE_COMPLETE);
cards.setSignalDurationTicks(cardStack, 20);
```

第三方样板供应器可以实现 `RedstoneSignalHost`，并在稳定服务端 tick 调用统一状态机：

```java
cards.updateRedstoneSignal(host, providerBusy, returnInventoryPending, false);
```

成功接受样板时调用：

```java
cards.onSuccessfulPatternPush(host, providerBusy, returnInventoryPending);
```

`triggerPulse` 和 `setContinuousSignal` 由宿主负责更新方块、持久化状态并通知邻居。`allowCraftOnFallingEdge` 仅用于无法精确观察产物返还事件的供应器；能够精确捕获返还时应传 `false`，避免重复脉冲。

## 配方查找器分类

注册分类器时使用唯一 id 和优先级。优先级高的分类器先运行；所有结果会与内置分类器结果合并。

```java
Ae2UtilityApi.recipeFinder().registerClassifier(
        ResourceLocation.fromNamespaceAndPath("example", "alloys"),
        100,
        ingredient -> ingredient instanceof ItemStack stack && isAlloy(stack)
                ? Set.of("example.alloy")
                : Set.of()
);
```

规则：

- feature key 不得为空，最大 64 个字符。
- 重复 id 会抛出 `IllegalArgumentException`。
- 分类器应是无副作用、快速且线程安全的纯函数；JEI 全局索引会对大量原料调用它。
- 未被扩展分类器识别的原料仍会经过内置分类器，并至少得到内置兜底特征。

`RecipeCandidateView` 是不包含内部网络包或 JEI recipe 对象的只读候选投影，可用于展示和二次筛选。

## 兼容策略

- `API_VERSION = 1` 生命周期内，不删除公共类型和方法，不改变现有参数语义。
- 可在兼容版本中增加新方法、枚举值或结果状态；调用方不应假设枚举永远只有当前值。
- 主版本升级会提高 `API_VERSION`，并在 changelog 中记录迁移方式。
- 内部包不受上述承诺约束。