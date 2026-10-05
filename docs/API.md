# API 参考 · Pinyin Search API

> 面向**依赖方（其他模组开发者）**的完整 API 参考。
> 本文与代码里的 JavaDoc 一一对应；`api` 包就是 v1.0 的完整对外面，不多一行。

**三种查看方式**

| 方式 | 位置 | 适合 |
|---|---|---|
| 📖 本文（Markdown） | `docs/API.md` | 在 GitHub / 编辑器里直接读，含用法与语义约定 |
| 🔌 接入模板（照抄就能用） | [`docs/INTEGRATION.md`](INTEGRATION.md) | 你要把拼音搜索接进自己的模组时，从这份开始 |
| 🌐 生成好的 HTML（离线可开） | `docs/apidocs/index.html` | 双击即看，可整目录打包发给别人；也可作为 GitHub Pages 内容 |
| 🧠 IDE 悬停（最省事） | JitPack 提供的 `-javadoc` / `-sources` 产物 | 写代码时直接看参数/返回说明 |

```gradle
// IDE 里能悬停看中文 JavaDoc（多数 IDE 会自动拉取，拉不到就显式加 classifier）
compileOnly 'com.github.2779789119:pinyinsearch:1.1.3'
compileOnly 'com.github.2779789119:pinyinsearch:1.1.3:javadoc'
compileOnly 'com.github.2779789119:pinyinsearch:1.1.3:sources'
```

> 改了 `api` 包后，请跑 `gradlew javadocToDocs` 重新同步 `docs/apidocs/`（CI 里也会重新生成）。

---

## 目录

- [0. 30 秒上手](#0-30-秒上手)
- [1. `PinyinSearch`（门面）](#1-pinyinsearch门面)
- [2. `Profile`（不可变配置）](#2-profile不可变配置)
- [3. `Matcher`（大数据集）](#3-matcher大数据集)
- [4. 语义约定（所有方法共同遵守）](#4-语义约定所有方法共同遵守)
- [5. 复制粘贴示例](#5-复制粘贴示例)
- [6. 版本承诺](#6-版本承诺)
- [7. 已知限制](#7-已知限制)

---

## 0. 30 秒上手

```java
import com.pinyinsearch.api.PinyinSearch;
import com.pinyinsearch.api.Matcher;
import com.pinyinsearch.api.Profile;
```

```java
// ① 单对单：小列表（几十~几百条）逐条判断，不需要建索引
boolean hit = PinyinSearch.matches("钻石剑", "zsj");   // true

// ② 大数据集：建一次，反复用不同 query 搜
Matcher matcher = PinyinSearch.matcher(pool);          // pool: List<String>
List<Integer> indices = matcher.searchIndices("zsj");  // 命中下标，升序
List<String>  matches = matcher.search("zsj");         // 命中文本，保留 pool 顺序与重复项
```

**软依赖时必须写回退**（没装本库时连 `PinyinSearch` 都加载不了）：

```java
if (ModList.get().isLoaded("pinyin_search")) {
    return PinyinSearch.matches(text, query);
}
return text.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT));
```

> ⚠️ 不要把 `PinyinSearch` 写进静态字段/常量的类型，也不要继承/实现它 —— 那会在类加载阶段就抛
> `NoClassDefFoundError`，连「没装就回退」的机会都没有。稳一点的做法是把调用封进一个独立小类。

---

## 1. `PinyinSearch`（门面）

`com.pinyinsearch.api.PinyinSearch`
**final 类，私有构造，全部方法为 `static`。** 这是整个库的唯一入口。

### 方法总表

| 方法 | 说明 |
|---|---|
| `static boolean isAvailable()` | 库是否可用（正常恒为 `true`）。能力探测用，**不解析字典**，开销极低 |
| `static boolean matches(CharSequence text, CharSequence query)` | 单对单匹配，使用默认 `Profile` |
| `static boolean matches(CharSequence text, CharSequence query, Profile profile)` | 单对单匹配，指定 `Profile` |
| `static Matcher matcher(List<String> pool)` | 把文本池包装成 `Matcher`，使用默认 `Profile` |
| `static Matcher matcher(List<String> pool, Profile profile)` | 同上，指定 `Profile`（含引擎选择） |
| `static Profile defaultProfile()` | 默认 `Profile`（**同一个不可变实例**） |
| `static Profile.Builder profile()` | 自定义 `Profile` 的入口 |
| `static String normalize(String text)` | 归一化：小写 + 全角转半角 + 去首尾空白 |

### 1.1 `matches(text, query[, profile])`

| 参数 | 语义 |
|---|---|
| `text` | **被搜索的文本**（haystack），例如物品显示名。`null` → 返回 `true` |
| `query` | **用户输入**（needle）。`null` / 空串 / 全空白 → 返回 `true` |
| `profile` | 匹配配置。`null` → 使用 `defaultProfile()`，不抛异常 |

**返回 `true` 的条件**

- `text` 或 `query` 为 `null`（按「query 为空」处理，调用方无需自己判空）；
- `query` 归一化后为空串 / 全空白；
- 归一化后 `text` 原文包含 `query`；
- 拼音命中：全拼 / 声母 / 简拼 / 任意组合 / 带声调数字；`Profile` 开启模糊音后，模糊音变体同样命中。

**返回 `false`**：其余情况，包括完全无关的 query、默认 `Profile` 下未开启模糊音的变体
（例如「中国」+ `zong国`）、以及内部异常退化为「原文包含」后仍未命中。

**异常**：本方法**不抛异常**；内部任何 `Throwable` 一律捕获并退化为「原文包含」。
若连兜底的原文包含都失败（例如 OOM），返回 `false`。

**线程**：必须在**客户端主线程**调用。

### 1.2 `matcher(pool[, profile])`

| 参数 | 语义 |
|---|---|
| `pool` | 文本池，**必须是稳定有序的列表**（见下）。`null` 视为空列表；元素为 `null` 时按其「恒命中」语义处理并在结果里原样返回 |
| `profile` | 匹配配置。`null` → 使用 `defaultProfile()` |

- ⚠️ **为什么是 `List` 而不是 `Collection`**：`Matcher.searchIndices` 返回的是**下标**，
  而下标只有在稳定有序列表上才有意义。传 `HashSet` 时迭代顺序不保证稳定，下标无法可靠映射回元素。
- ⚠️ **默认引擎是 `Engine.LOOP`**（内部逐条调用同一个 `matches` 实现），创建时几乎零成本。
  若选了 `SIMPLE / TREE / CACHED`，索引会在**创建 `Matcher` 时同步构建** ——
  大文本池**不要**在渲染 / 输入回调里构建。
- 内部会**拷贝**一份 `pool`，之后修改传入的列表不影响该 `Matcher`。

### 1.3 `isAvailable()`

只检查「PinIn 类可加载 + 拼音表 `data.txt` 资源可定位」，**不会解析字典**，可在模组构造阶段调用。
正常打包时为 `true`；重定位 / 打包出错时为 `false`。

### 1.4 `normalize(text)`

小写 + 全角转半角（`Ａ` → `a`）+ 表意空格转普通空格 + 去首尾空白（`String.strip()`）。
`null` → 返回 `""`。

`matches` / `Matcher` 内部都会先归一化，**调用方不需要预处理**。
本方法的用途是你自己想建索引或缓存文本时保持一致。

---

## 2. `Profile`（不可变配置）

`com.pinyinsearch.api.Profile`
**不可变值对象**，正确实现 `equals` / `hashCode`（用于引擎缓存键）。
**没有公开构造器** —— 只能通过 `PinyinSearch.profile()`（全新建）或 `Profile#toBuilder()`（基于已有配置派生）拿到 `Builder`。

### 2.1 配置项与默认值

| 配置项 | 类型 | 默认 | 含义 |
|---|---|---|---|
| `scheme()` | `Scheme` | `QUANPIN` | 输入方式（拼法） |
| `engine()` | `Engine` | `LOOP` | `Matcher` 的底层索引引擎，只影响 `matcher(...)` 路径 |
| `fuzzyZhZ()` | `boolean` | `false` | 模糊音 `zh ↔ z` |
| `fuzzyShS()` | `boolean` | `false` | 模糊音 `sh ↔ s` |
| `fuzzyChC()` | `boolean` | `false` | 模糊音 `ch ↔ c` |
| `fuzzyAngAn()` | `boolean` | `false` | 模糊音 `ang ↔ an` |
| `fuzzyIngIn()` | `boolean` | `false` | 模糊音 `ing ↔ in` |
| `fuzzyEngEn()` | `boolean` | `false` | 模糊音 `eng ↔ en` |
| `fuzzyUV()` | `boolean` | `false` | 模糊音 `u ↔ v`（ü） |
| `accelerate()` | `boolean` | `true` | 即时匹配加速（PinIn 的 `accelerate`） |

### 2.2 `Scheme`（输入方式）

| 常量 | 含义 | 说明 |
|---|---|---|
| `QUANPIN` | 全拼 | 默认 |
| `SHUANGPIN_ZIRANMA` | 双拼 · 自然码 | **实验性**：会改变 query 的解析方式（用户输入被当成双拼按键串）。`中国` = `vsgo` |
| `SHUANGPIN_XIAOHE` | 双拼 · 小鹤 | **实验性**，同上 |
| `ZHUYIN_DACHEN` | 注音 · 大千 | **实验性**：query 按大千注音键位解析。`中国` = `5j/eji` |

> 开了双拼 / 注音后，**全拼串不再命中**（`zhongguo` ✗）—— 这正是「输入方式」的含义。

### 2.3 `Engine`（索引引擎）

| 常量 | 说明 |
|---|---|
| `LOOP` | **默认。** 逐条调用即时匹配实现，与 `matches(...)` 的等价性**由构造保证**；无真实索引，10k 词条量级仍可接受 |
| `SIMPLE` | PinIn `SimpleSearcher`：构建快、搜索慢。⚠️ 索引匹配与即时匹配是否逐条等价**未经验证**，用前请自行验收 |
| `TREE` | PinIn `TreeSearcher`：构建慢、搜索快、内存占用最大（约 9.5MB/实例） |
| `CACHED` | PinIn `CachedSearcher`：介于 `SIMPLE` 与 `TREE` 之间 |

### 2.4 `Builder` 方法

| 方法 | 说明 |
|---|---|
| `scheme(Scheme)` | 设置输入方式。传 `null` 保留当前值（不抛异常） |
| `engine(Engine)` | 设置索引引擎。传 `null` 保留当前值（不抛异常） |
| `fuzzyZhZ(boolean)` / `fuzzyShS` / `fuzzyChC` / `fuzzyAngAn` / `fuzzyIngIn` / `fuzzyEngEn` / `fuzzyUV` | 逐项开关模糊音 |
| `allFuzzy(boolean)` | 便捷：一次性开关全部 7 种模糊音 |
| `accelerate(boolean)` | 开关即时匹配加速 |
| `build()` | 构建出不可变的 `Profile`；不抛异常 |

> `Builder` 不是线程安全的；配置完请立即 `build()`，不要跨线程共享同一个 `Builder`。

### 2.5 `Profile` 实例方法

`scheme()` / `engine()` / 7 个 `fuzzyXxx()` / `accelerate()` / `equals` / `hashCode` / `toString()` / `toBuilder()`。

### 2.6 ★ 生命周期（这条很重要）

`Profile` 是**配置描述**，不是一次性用品：

- 构造 `PinIn` 引擎要**解析整份拼音字典**（重操作，单实例内存可达 MB 量级）；
- 库按 `Profile` 缓存引擎（容量上限 **16**，超出时淘汰最久未使用的，最坏退化为重新构造）；
- 因此请**在初始化时构造少数几个 `Profile` 并长期复用**，
  **不要在热路径（每帧 / 每次按键 / 每次查询）上反复 `build()`** —— 每个不同的 `Profile` 都会占住一份引擎。

```java
// ✅ 初始化时建好，长期复用
private static final Profile FUZZY = PinyinSearch.profile().allFuzzy(true).build();

// ❌ 热路径上反复构建
public boolean filter(String text, String query) {
    return PinyinSearch.matches(text, query, PinyinSearch.profile().allFuzzy(true).build());
}
```

---

## 3. `Matcher`（大数据集）

`com.pinyinsearch.api.Matcher`
接口。通过 `PinyinSearch.matcher(...)`（拼音实现）或 `Matcher.literal(...)`（纯原文包含实现）获得。

### 3.1 方法

| 方法 | 说明 |
|---|---|
| `List<Integer> searchIndices(CharSequence query)` | 返回命中文本池的**下标**，按文本池迭代顺序**升序**，每个元素只出现一次 |
| `List<String> search(CharSequence query)` | 便捷方法：完成下标 → 文本的映射，**保留文本池顺序与重复项，不去重** |
| `static Matcher literal(List<String> pool)` | 不依赖拼音的纯原文包含实现，接口形状一致 |

**`query` 语义**：`null` / 空串 / 全空白 → 返回**全部下标**（与「空查询命中一切」一致）。

**`searchIndices` 的返回类型说明**：大文本池下 `List<Integer>` 有装箱开销，换来的是 API 简洁；
性能敏感场景请自行用 `PinyinSearch.matches(...)` 遍历。

**异常**：两个方法都**不抛异常**，内部异常退化为「原文包含」语义。

**线程**：**必须在客户端主线程使用**。底层 PinIn 的缓存会随查询惰性增长，并发访问不安全。
（例外：索引构建完成后是只读的，所以「异步构建 → 构建完成后交回主线程使用」是安全的。）

### 3.2 硬性等价要求

对同一 `pool`、同一 `query`、同一 `Profile`：

```
matcher(pool, p).searchIndices(query)  ==  { i | matches(pool[i], query, p) == true }
```

- `Engine.LOOP`（**默认**）：等价性由构造保证（它就是逐条调同一个 `matches` 实现）。
- `Engine.SIMPLE / TREE / CACHED`：PinIn 的索引匹配与即时匹配是否语义一致**未经验证** ——
  本仓库的 `MatcherEquivalenceTest` 在 vendored PinIn 1.6.0 上实测四个引擎全部等价，
  但索引引擎仍属**显式选择**的优化，谁用谁承担已验证过的前提。重点盯：声调（数字）、模糊音开启时、双拼解析。

### 3.3 `Matcher.literal(pool)`

只做「归一化 + 大小写不敏感子串包含」，**不含任何拼音逻辑**。

```java
Matcher matcher = Matcher.literal(pool);        // 自己有「关闭拼音搜索」开关时可直接换实现
matcher.searchIndices("钻石");                   // 原文包含 ✅
matcher.searchIndices("zsj");                    // 不做拼音 ❌
```

> ⚠️ **它不能用来兜「本模组整体没装」的情况**：那种情况下连 `Matcher` 这个类都加载不了
> （`NoClassDefFoundError`）。「没装则行为不变」的回退必须由调用方自己写。

`Matcher.Literal` 是它的实现类型（公开只是为了能被接口的静态方法引用），请通过 `Matcher.literal(...)` 获取。

---

## 4. 语义约定（所有方法共同遵守）

| 情况 | 行为 |
|---|---|
| `text == null` 或 `query == null` | `matches` 返回 `true`；`Matcher` 的 `query == null` 视为空查询 |
| `query` 为空 / 全空白 | `matches` 返回 `true`；`Matcher` 返回全部下标 |
| `pool == null` | 视为空列表 |
| `pool` 中的 `null` 元素 | 入索引时按空串处理，但按 `matches(null, q) == true` 的语义**恒命中**，结果里原样返回 `null` |
| `profile == null` | 使用 `defaultProfile()` |
| 内部任何异常 | 一律捕获，退化为「原文包含」，**永不抛异常** |
| 归一化 | 小写 + 全角转半角 + 去首尾空白，**由库吃掉** |
| 副作用 | **无**：不写日志（除外部 debug 开关）、不改全局状态、不注册事件 |
| 全局状态 | `api` 包**不持有任何可变全局状态**（`Profile` 不可变，`PinyinSearch` 只有一份私有的、有容量上限的引擎缓存），因此被多个模组 jarJar 嵌入也完全安全 |
| 线程 | `matches` / `Matcher` 都要求**客户端主线程**；静态入口对并发做了防御性处理，但这不代表并发被支持 |

---

## 5. 复制粘贴示例

### 5.1 软依赖 + 回退（推荐给「愿意接受装了才有拼音搜索」的模组）

```gradle
repositories { maven { url 'https://jitpack.io' } }
dependencies { compileOnly 'com.github.2779789119:pinyinsearch:1.1.3' }
```

```java
public static boolean matchesQuery(String text, String query) {
    if (ModList.get().isLoaded("pinyin_search")) {
        return PinyinSearch.matches(text, query);
    }
    return text.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT));
}
```

### 5.2 给搜索框建索引

```java
// 初始化 / 打开界面时建一次
private Matcher matcher;

public void rebuild(List<String> names) {          // names 必须是稳定有序列表
    this.matcher = PinyinSearch.matcher(names);    // 默认 LOOP，创建成本极低
}

// 每次按键
public List<String> onQueryChanged(String query) {
    return matcher.search(query);
}
```

### 5.3 放宽匹配（模糊音，会让误命中变多）

```java
private static final Profile FUZZY = PinyinSearch.profile().allFuzzy(true).build();
PinyinSearch.matches(text, query, FUZZY);
```

### 5.4 使用者主动选了双拼

```java
private static final Profile XIAOHE =
        PinyinSearch.profile().scheme(Profile.Scheme.SHUANGPIN_XIAOHE).build();

Matcher m = PinyinSearch.matcher(pool, XIAOHE);
```

### 5.5 大文本池 + 已验收等价性时换索引引擎

```java
private static final Profile TREE = PinyinSearch.profile().engine(Profile.Engine.TREE).build();

// ⚠️ 不要在渲染 / 输入回调里做；索引是同步构建的
Matcher m = PinyinSearch.matcher(bigPool, TREE);
```

### 5.6 统一按 `Matcher` 抽象（含「关闭拼音」开关）

```java
Matcher m = pinyinEnabled ? PinyinSearch.matcher(pool) : Matcher.literal(pool);
List<String> hits = m.search(query);
```

### 5.7 自己缓存归一化后的文本

```java
String key = PinyinSearch.normalize(displayName);   // 行为在 1.x 内保持不变，可以安全地拿它建索引
```

---

## 6. 版本承诺

**1.x 内：**

- `api` 包下**所有公开类型**：不改 / 不删已有方法签名与公开字段，只允许**新增**方法或重载；
- `Matcher.searchIndices` / `search` 的**命中判定语义保持不变**；如需改变匹配语义，
  只能通过**新增方法**或**新增 `Profile` 配置项**（且新配置项必须有「不改变既有行为」的默认值）实现；
- `Profile` 的配置项**只增不改语义**；
- `normalize` 的输入输出行为保持稳定（一旦调用方拿它建过索引，行为变化会让索引失效）；
  如需调整，只能新增 `normalizeV2`。

**v1.1 的预留扩展点**：`Profile.Builder.readingOverride(text, pinyin)`（词级读音覆盖，处理上下文多音字）。
**v1.0 完全不提供——既没有实现，也没有 no-op 占位。**

---

## 7. 已知限制

完整清单与实测结论见 [README §5「已知限制」](../README.md#5-已知限制实测结论)。要点：

| 限制 | 结论 |
|---|---|
| 上下文多音字 | 单字的所有读音都参与匹配（「重庆」用 `chongqing`、`zhongqing` 都能搜到），但**无词级控制**；v1.1 才提供 |
| 字形辅助码 | PinIn 明确不支持，**永不做** |
| 简繁互搜 | ❌ 不互通（`钻石剑` ↔ `鑽石劍`）；繁体文本本身可以拼音搜 |
| 英文词首字母 | ❌ `Diamond Sword` 搜不到 `DS` |
| 声调 | 会被校验，且必须跟在完整音节后（`tie3` ✅ / `tie1` ❌ / `zh1国` ❌） |
| 双拼 / 注音 | 可用但**实验性** |
| 索引引擎 | 默认 `LOOP` 规避语义风险；索引引擎须自行验收 |
| 分词 | 不做 |
