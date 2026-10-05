# 通用拼音搜索（开发者库） · Pinyin Search API

**给模组开发者用的拼音搜索库模组。** 把完整的拼音匹配能力包装成「一行依赖、三行调用」。
不做 GUI 注入、不碰 JEI/REI、不提供任何玩家可见界面 —— 它是**能力提供方**。

**协议：MIT**（本模组自身代码）· 内置 PinIn 保留其 MIT 声明。

| 项 | 值 |
|---|---|
| modid | `pinyin_search` |
| MC / 加载器 | 1.20.1 / Forge 47.x |
| 前置依赖 | **无** |
| 体积 | ~204 KB（内含拼音表 302 KB 压缩后） |
| Java 包名 | `com.pinyinsearch` |

[English README](README_EN.md)

---

## 📖 API 文档（先看这个）

| 想做什么 | 去哪儿看 |
|---|---|
| **照着接进自己的模组**（依赖 + Bridge 类 + 搜索框改法，可整份照抄） | **[`docs/INTEGRATION.md`](docs/INTEGRATION.md)** |
| **通读一遍 API**（每个方法的参数 / 返回 / null / 线程 / 异常语义 + 复制粘贴示例） | **[`docs/API.md`](docs/API.md)** |
| **离线打开网页版**（双击即看，可整目录打包发给别人） | [`docs/apidocs/index.html`](docs/apidocs/index.html) |
| **写代码时悬停看中文 JavaDoc**（最省事） | JitPack 的 `-javadoc` / `-sources` 产物，见 §6.3 |
| 在线网页版 | 仓库开启 GitHub Pages 后由 CI 自动发布（`.github/workflows/build.yml`） |

```gradle
// IDE 里能悬停看中文 JavaDoc（多数 IDE 会自动拉取，拉不到就显式加 classifier）
compileOnly 'com.github.2779789119:pinyinsearch:1.1.0'
compileOnly 'com.github.2779789119:pinyinsearch:1.1.0:javadoc'
compileOnly 'com.github.2779789119:pinyinsearch:1.1.0:sources'
```

```bash
./gradlew javadoc          # 生成到 build/docs/javadoc
./gradlew javadocToDocs    # 同步到 docs/apidocs（改了 api 包后记得跑）
```

`api` 包就是 v1.0 的完整对外面，不多一行 —— 生成的文档也只包含 `api` 包（+ 模组入口），
没有 `impl` 与 vendored PinIn 的干扰。

---

## 1. 一行依赖 + 三行调用

```gradle
repositories { maven { url 'https://jitpack.io' } }

dependencies {
    // 软依赖：只编译期需要，运行期靠玩家/整合包安装
    compileOnly 'com.github.2779789119:pinyinsearch:1.1.0'
}
```

```java
if (ModList.get().isLoaded("pinyin_search")) {
    return PinyinSearch.matches(text, query);
}
// 没装本库：行为完全不变
return text.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT));
```

**没装本库时你的模组行为完全不变。** 这是别人敢接的前提。

> ⚠️ 上面那段之所以安全，是因为 `PinyinSearch` 只在**真正进入分支后**才会被解析（懒解析）。
> **不要把 `PinyinSearch` 写进静态字段/常量的类型、也不要继承/实现它** ——
> 那会在类加载阶段就抛 `NoClassDefFoundError`，连「没装就回退」的机会都没有。
> 更稳的做法是把调用封进一个独立的小类（如 `PinyinSearchBridge`），只在分支内引用它。

### 常用 API

```java
// 单对单匹配（小列表逐条判断，无需建索引）
PinyinSearch.matches(text, query);

// 大数据集：建一次包装，反复用不同 query 搜索
Matcher m = PinyinSearch.matcher(pool);      // pool 必须是稳定有序的 List<String>
List<Integer> idx = m.searchIndices("zsj");  // 命中下标，升序
List<String>  hit = m.search("zsj");         // 命中文本，保留 pool 顺序与重复项

// 放宽匹配（模糊音，会让误命中变多，慎用）
Profile fuzzy = PinyinSearch.profile().allFuzzy(true).build();
PinyinSearch.matches(text, query, fuzzy);

// 使用者主动选了双拼输入法
Matcher sp = PinyinSearch.matcher(pool,
        PinyinSearch.profile().scheme(Profile.Scheme.SHUANGPIN_XIAOHE).build());

// 大 pool 且已确认索引引擎与即时匹配等价时，才显式换引擎
Matcher big = PinyinSearch.matcher(pool, PinyinSearch.profile().engine(Profile.Engine.TREE).build());

// 归一化（小写 + 全角转半角 + 去首尾空白）
String normalized = PinyinSearch.normalize(raw);
```

---

## 2. 能力矩阵

**每一项能力都由内置 PinIn 提供，本库只做「暴露 + 默认值选择」。** 本库不发明任何拼音逻辑。

| 能力 | 是否支持 | 本库暴露方式 / 默认 | 例 |
|---|---|---|---|
| 原文包含 | ✅ 恒开，不可关 | 匹配算法自带 | `钻石` → 钻石剑 |
| 全拼 | ✅ 默认 | `Scheme.QUANPIN` | `zuanshijian` → 钻石剑 |
| 声母 / 简拼 / 任意组合 | ✅ 恒开，不可关 | 递归匹配 | `zsj`、`zuanjian`、`tiezh` |
| 声调（可带可忽略） | ✅ 恒开，不可关 | 内置（`PinyinFormat.NUMBER`） | `zhong1国`、`zhen1` |
| 双拼 · 自然码 / 小鹤 | ✅ **实验性**，默认关 | `Scheme.SHUANGPIN_*` | 中国 = `vsgo` |
| 注音 · 大千 | ✅ **实验性**，默认关 | `Scheme.ZHUYIN_DACHEN` | 中国 = `5j/eji` |
| 模糊音 ×7 | ✅ 默认全关 | `Builder.fuzzyXxx(...)` ×7 + `allFuzzy(boolean)` | 开 `fuzzyZhZ` 后 `zong国` 命中「中国」 |
| 简体 / 繁体 | ⚠️ **不互通**（见 §5） | 随字典生效 | 繁体文本自身可拼音搜 |
| 即时 / 索引两种匹配 | ✅ | `matches(...)` / `Profile.Engine`（默认 `LOOP`） | — |
| 即时匹配加速 | ✅ 默认开 | `Profile.accelerate()` | — |
| 字形辅助码 | ❌ **永不做** | — | PinIn 明确不支持 |
| 上下文多音字（词级读音覆盖） | ❌ v1.0 不提供 | v1.1 的 `Profile.readingOverride` | `重庆` 见 §5 |
| 英文子串（大小写不敏感） | ✅ | 原文包含路径 | `diamond` → `Diamond Sword` |
| 英文词首字母 | ❌ | — | `DS` **搜不到** `Diamond Sword` |

**默认值的选择理由：**

- **声母 / 简拼 / 声调默认开**：不带来误命中，纯增益。
- **模糊音 / 双拼 / 注音默认关**：前者是「放宽」（误命中上升）；后两者会**改变 query 的解析方式**，属于使用者主动选择的输入法，不该默认改变所有人的搜索行为。
- **`accelerate` 默认开**：本 API 的主力场景（一次 query × 多次 text）正是 PinIn 注释里说的稳定调用。
- **引擎默认 `LOOP`**：让「逐条能搜到、建索引后搜不到」这类诡异行为**在默认路径上不可能发生**（等价性由构造保证）。

---

## 3. API 语义（约定）

所有 `api` 包下的公开类型与方法都有完整 JavaDoc。共同的硬约定：

| 情况 | 行为 |
|---|---|
| `text == null` 或 `query == null` | `matches` 返回 `true`（按「query 为空」处理，调用方无需自己判空） |
| `query` 为空 / 全空白 | 返回 `true`；`Matcher.search*` 返回**全部下标** |
| 内部任何异常 | 一律捕获，退化为「原文包含」，**永不抛异常** |
| 归一化 | 小写 + 全角转半角 + 去首尾空白，**由库吃掉**，调用方不需要预处理 |
| `Matcher` 入参 | `List<String>`（不是 `Collection`）—— `searchIndices` 返回的**下标**只有在稳定有序列表上才有意义 |
| `pool` 中的 `null` 元素 | 按「空串」入索引，但按 `matches(null, q) == true` 的语义**恒命中**，结果里原样返回 `null` |
| 线程 | **必须在客户端主线程使用**；`Matcher` 不保证线程安全。索引构建完成后是只读的，所以「异步构建 → 交回主线程使用」安全 |
| 副作用 | 无。不写日志、不改全局状态、不注册事件 |

### 硬性等价要求

对同一 `pool`、同一 `query`、同一 `Profile`：

```
matcher(pool, p).searchIndices(query) == { i | matches(pool[i], query, p) == true }
```

- `Engine.LOOP`（**默认**）：等价性由构造保证。
- `Engine.SIMPLE / TREE / CACHED`：PinIn 的索引匹配与即时匹配是否语义一致**未经验证**；
  本仓库的 `MatcherEquivalenceTest` 在 vendored PinIn 1.6.0 上实测**四个引擎全部等价**，
  但索引引擎仍属**显式选择**的优化，谁用谁承担前提。

---

## 4. 性能与内存（把账算清楚）

PinIn 官方实测（37k 词条 / 约 400k 字符 / 约 900KB 样本）：

| 匹配方式 | 构建耗时 | 搜索耗时 | 内存 |
|---|---|---|---|
| `TreeSearcher`（索引） | 210 ms | 0.19 ms | 9.50 MB |
| `SimpleSearcher`（索引） | 27 ms | 9.1 ms | 1.84 MB |
| `CachedSearcher`（索引） | 28 ms（+预热 16 ms） | 0.55 ms | 介于两者之间 |
| 遍历拼音匹配（即时，= 本库 `LOOP`） | — | 23 ms | — |
| 遍历 `contains`（即时，非拼音） | — | 0.53 ms | — |

| 本库路径 | 索引 | 开销 / 建议用法 |
|---|---|---|
| `matches(text, query)` | 无 | 单次微秒级。小列表逐条调用，每敲一个键重算一遍也没问题 |
| `matcher(pool)` + `LOOP`（默认） | 无真实索引 | **安全默认**；10k 条约为此表「遍历拼音匹配」的 1/4 |
| `matcher(pool)` + `SIMPLE / TREE / CACHED` | **同步构建** | 大 pool 且已验收时使用；**不要在渲染 / 输入回调里构建** |
| `Matcher.search*(query)` | 复用已建索引 | 同一份 `Matcher` 反复服务不同 query |

> ⚠️ **`Profile` 是配置描述，不是一次性用品！**
> `PinIn` 的构造会解析整份拼音字典（重操作，`TreeSearcher` 量级约 **9.5MB**/实例），
> 本库按 `Profile` 缓存引擎实例。因此请**在初始化时构造少数几个 `Profile` 并长期复用**，
> **不要在热路径上反复 `build()`**。
> 缓存有容量上限（16 个），超出时淘汰最久未使用的引擎（最坏退化为重新构造）。

---

## 5. 已知限制（实测结论）

以下都是**在 vendored PinIn 1.6.0 上真实测出来的行为**，不是猜测；对应的断言就在 `KnownBehaviorTest` 里。

| 限制 | 实测结论 |
|---|---|
| **上下文多音字** | 单字的**每个读音**都会参与匹配，所以「重庆」用 `chongqing` 和 `zhongqing` **都能搜到**；但**没有词级读音控制**（无法表达「只在『重庆』里读 chong」）。v1.0 不解决，v1.1 计划 `Profile.readingOverride` |
| **字形辅助码** | 不存在，PinIn 明确「不（也不会）支持」，本库**永不做** |
| **简繁互搜** | ❌ **不互通**：`钻石剑` 搜不到 `鑽石劍`，反之亦然。但**繁体文本本身可以用拼音搜**（`鑽石劍` + `zsj` ✅）。本库不做繁简转换 |
| **双拼 / 注音** | 功能可用（`中国` = `vsgo` / `5j/eji`），但 PinIn 自述双拼在测试阶段，标注**实验性** |
| **纯数字 query** | `铁砧` + `1` → 不命中，也不会误当声调串。**声调数字是被校验的**：`铁砧` + `tie3` ✅、+ `tie1` ❌ |
| **声调数字的位置** | 必须跟在**完整音节**后：`zhong1国` ✅、`zhen1` ✅；跟在裸声母后（`zh1国`）❌ |
| **英文子串匹配** | ✅ 可用（大小写不敏感、任意位置包含）：`diamond` / `word` → `Diamond Sword`；但 `diamonds` ❌（是「包含」不是前缀/模糊） |
| **英文词首字母** | ❌ `Diamond Sword` 搜不到 `DS` |
| 索引引擎语义 | 默认 `LOOP` 规避；索引引擎须自行过等价性验收 |
| 分词 | 不做 |

### ⚠️ 与开发文档表格的两处偏差（以实测为准）

开发文档 §8.1 的默认 Profile 表里有两行与 PinIn 实际行为不符，本库按**实测**实现并锁定断言：

| 用例 | 文档写的 | 实测 | 原因 |
|---|---|---|---|
| `中国` + `z国` | ❌ | ✅ | `z` 本来就是 `zhong` 的**合法简拼首字母**（和 `zsj` 能搜「钻石剑」是同一机制），**不是**模糊音 `zh → z`。真正的模糊音对照组 `zong国` 实测确实是 ❌ |
| `中国` + `zh1国` | ✅ | ❌ | 声调数字必须跟在完整音节后；裸声母 `zh` 后 PinIn 不识别声调 |

本库**不发明拼音逻辑**，所以不做「补丁式对齐」，而是如实暴露并写进文档。

---

## 6. 接入方式

### 6.1 软依赖（玩家自己装）

见 §1。适合：不想让玩家无感、愿意接受「装了才有拼音搜索」。
优点：全服共用一份、行为统一、接入方 jar 不变大。

### 6.2 硬依赖 + jarJar（玩家无感）

```gradle
dependencies {
    compileOnly 'com.github.2779789119:pinyinsearch:1.1.0'   // 编译期 API
    jarJar      'com.github.2779789119:pinyinsearch:1.1.0'   // 嵌进自己的 jar
}
```

```toml
# 依赖方 mods.toml
[[dependencies.yourmod]]
modId = "pinyin_search"
mandatory = false          # 已被 jarJar 嵌入，这里只声明版本关系
versionRange = "[1.0.0,2.0.0)"
ordering = "NONE"
side = "BOTH"
```

优点：玩家不用额外装东西。代价：每个依赖方各带一份 ~204KB。

**首次接入时请验完这四项**（同一件事的四个面）：

1. 只装内嵌本库的那一个模组 → 拼音搜索可用；
2. 两个都内嵌本库的模组同时加载 → 能正常启动；
3. 玩家**既单独装本库、又装了内嵌本库的模组** → 是否报重复 modId（视结果二选一）；
4. 与 JECh 同时装 → 无类冲突。

> 本库**无状态**（`Profile` 不可变、无全局注册表），因此即使真的加载了两份也不会互相污染 ——
> 最坏只是多占一份内存。

### 6.3 Maven 坐标

JitPack 的标准格式是 `com.github.User:Repo:Tag`（artifactId 默认就是仓库名）：

```gradle
compileOnly 'com.github.2779789119:pinyinsearch:1.1.0'            // ✅
// compileOnly 'com.github.2779789119.pinyinsearch:pinyin_search:1.1.0'  // ❌ 初稿写错过的形式
```

> 本仓库使用 `com.pinyinsearch` 作为包名、`pinyin_search` 作为 modid、`pinyinsearch` 作为仓库名
> （JitPack 的 artifactId 就是仓库名，**区分大小写**，务必写小写）。
> fork / 改名后请同步修改 `gradle.properties`、`mods.toml` 与本文档的坐标示例。

---

## 7. 与 JustEnoughCharacters (JECh) 共存

| 方面 | 结论 |
|---|---|
| 类冲突 | **无**。内置 PinIn 已重定位到 `com.pinyinsearch.shaded.pinin`，jar 内**不存在** `me/towdium/pinin/**` |
| 行为差异 | **会有**：JECh 自己的配置（模糊音、拼法等，由玩家改配置文件）与本库 `Profile` 相互独立 → 同一文本在两个搜索框可能结果不同 |
| 是否读 JECh 配置 | **不读**。一读就把自己耦合到别人的内部实现上 |
| 定位差异 | JECh 用 coremod 给**别人的**模组打补丁（未收录的模组只能提 issue 等版本）；本库给**愿意主动适配的**模组一条路径。路线相反，不竞争 |

---

## 8. 本项目内置的 PinIn（合规与 vendor 说明）

- 上游：<https://github.com/Towdium/PinIn>（Towdium，**MIT**），本仓库 vendor 的是 **1.6.0**（默认分支 `master`）。
- vendor **全量 16 个源文件，不裁剪任何类**（PinIn 内部可能有反射 / 动态加载，裁剪后可能「编译通过、运行期才炸」）。
- **唯一的改动是包名重定位**：`me.towdium.pinin` → `com.pinyinsearch.shaded.pinin`，
  以及拼音表资源路径随之移动为 `com/pinyinsearch/shaded/pinin/data.txt`
  （`DictLoader.Default` 用的是 `PinIn.class.getResourceAsStream("data.txt")`，包相对路径自动跟随）。
  每个文件顶部都有注明这一改动的注释，方便以后跟上游 diff。
- **fastutil**：上游声明 `it.unimi.dsi:fastutil:8.3.0`；MC 1.20.1 自带 **8.5.9**（`gradlew dependencies` 实测）。
  两者主版本一致（都是 8）→ **直接复用 MC 自带的 fastutil，不额外打包**。
  本仓库运行期冒烟测试已覆盖（`gradlew test` 会真实解析字典并匹配）。
- 合规：仓库根与 jar 内均有 `LICENSE`（MIT）与 `LICENSE-Pinin.txt`（PinIn 的 MIT 原文，原样拷贝）。
  致谢 PinIn —— 拼音引擎完全由它提供，本库不发明任何拼音逻辑。

---

## 9. 构建与测试

```bash
./gradlew build            # 编译 + 跑全部 JUnit 用例 + 打 jar（含 -sources.jar / -javadoc.jar）
./gradlew test             # 只跑测试
./gradlew javadoc          # 只生成 API 文档 → build/docs/javadoc
./gradlew javadocToDocs    # 把 API 文档同步到 docs/apidocs（供离线浏览 / Pages）
```

- 测试是**纯 JUnit 5 表驱动**（匹配逻辑不依赖 MC 运行时），CI 里可直接跑。
- 覆盖：开发文档 §8.1 默认 Profile 用例、自定义 Profile 用例（模糊音 / 双拼 / 注音 / accelerate 一致性）、
  **`Matcher` 与 `matches()` 逐条等价**（4 个引擎 × 3 套方案）、引擎缓存与 `Profile` 隔离、缓存容量上限、
  literal 实现、以及 §5 的全部实测结论。
- CI：`.github/workflows/build.yml` —— push / PR 触发构建 + 测试 + 产出 jar/源码包/文档包；
  `v*` tag 触发发布（供 JitPack 取坐标）；默认分支上的构建还会把 javadoc 发布到 GitHub Pages。

> ⚠️ 本仓库刻意**不给 Gradle 守护进程设置 `-Dfile.encoding=UTF-8`**。
> Gradle 写 `@argfile`（长类路径会走这条路）用的是守护进程默认字符集，而 JDK 17 启动器按平台默认字符集读取；
> 两者不一致时，路径里的中文（`D:\mod\拼音搜索`、`C:\Users\联想`）会乱码，测试 worker 直接报
> `ClassNotFoundException: ...GradleWorkerMain`。源码编码由 `options.encoding = 'UTF-8'` 单独保证。

---

## 10. 版本承诺（1.x 内）

- `api` 包下所有公开类型：**不改 / 不删**已有方法签名与公开字段，只允许**新增**方法或重载。
- `Matcher.searchIndices` / `search` 的命中判定语义保持不变；如需改语义，只能通过新增方法或
  新增 `Profile` 配置项（且新配置项必须有「不改变既有行为」的默认值）实现。
- `Profile` 的配置项只增、不改语义。
- `normalize` 的输入输出行为保持稳定（一旦调用方拿它建过索引，行为变化会让索引失效）；
  如需调整，只能新增 `normalizeV2`。

## 11. 非目标（明确划出来）

- ❌ 不 mixin 进别人的 GUI / `EditBox` / 容器界面
- ❌ 不接管 JEI / REI 的搜索
- ❌ 不处理输入法（IME）
- ❌ 不做繁简转换、分词；❌ 不实现字形辅助码
- ❌ 不提供任何玩家可见的配置界面（配置只在接入方的代码里）
- ❌ 第一期只做 1.20.1 Forge
