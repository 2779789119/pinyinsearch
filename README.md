# 通用拼音搜索（开发者库） · Pinyin Search API

**给模组开发者用的拼音搜索库模组。** 把完整的拼音匹配能力包装成「一行依赖、三行调用」。
不做 GUI 注入、不碰 JEI/REI、不提供任何玩家可见界面 —— 它是**能力提供方**。

**协议：MIT**（本模组自身代码）· 内置 PinIn 保留其 MIT 声明。

| 项 | 值 |
|---|---|
| modid | `pinyin_search` |
| MC / 加载器 | 1.20.1 / Forge 47.x |
| Java 包名 | `com.pinyinsearch` |
| 前置依赖 | **无** |
| 体积 | ~204 KB（内含拼音表，压缩后 302 KB） |

[English README](README_EN.md)

---

## 📖 文档导航

| 想做什么 | 去哪儿看 |
|---|---|
| **照着接进自己的模组**（依赖 + Bridge 类 + 搜索框改法，可整份照抄） | [`docs/INTEGRATION.md`](docs/INTEGRATION.md) |
| **通读 API**（参数 / 返回 / null / 线程 / 异常语义 + 复制粘贴示例） | [`docs/API.md`](docs/API.md) |
| **离线网页版**（双击 `index.html` 即看，可整目录打包分发） | [`docs/apidocs/index.html`](docs/apidocs/index.html) |
| **在线网页版** | `https://2779789119.github.io/pinyinsearch/`（仓库开启 Pages 后由 CI 发布） |
| 写代码时悬停看中文 JavaDoc | JitPack 的 `:javadoc` / `:sources` 产物（见 §7） |

---

## 1. 一行依赖 + 三行调用

```gradle
repositories { maven { url 'https://jitpack.io' } }

dependencies {
    // 软依赖：只编译期需要，运行期靠玩家 / 整合包安装
    compileOnly 'com.github.2779789119:pinyinsearch:1.1.3'
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
> 别把它写进静态字段 / 常量的类型，也别继承 / 实现它 —— 那会在类加载阶段就抛
> `NoClassDefFoundError`，连「没装就回退」的机会都没有。
> 更稳的做法是封进一个独立的桥接小类，见 [`docs/INTEGRATION.md`](docs/INTEGRATION.md) §2。

### 常用 API

```java
PinyinSearch.matches(text, query);               // 单对单匹配（小列表逐条判断，无需建索引）
PinyinSearch.matches(text, query, fuzzyProfile); // 放宽匹配（模糊音，误命中会变多）

Matcher m = PinyinSearch.matcher(pool);          // 大数据集：建一次，反复用不同 query 搜索
List<Integer> idx = m.searchIndices("zsj");      // 命中下标，升序
List<String>  hit = m.search("zsj");             // 命中文本，保留 pool 顺序与重复项

PinyinSearch.normalize(raw);                     // 小写 + 全角转半角 + 去首尾空白
```

完整签名与示例 → [`docs/API.md`](docs/API.md)

---

## 2. 能力矩阵

**每一项能力都由内置 PinIn 提供，本库只做「暴露 + 默认值选择」。本库不发明任何拼音逻辑。**

| 能力 | 是否支持 | 本库暴露方式 / 默认 | 例 |
|---|---|---|---|
| 原文包含 | ✅ 恒开，不可关 | 匹配算法自带 | `钻石` → 钻石剑 |
| 全拼 | ✅ 默认 | `Scheme.QUANPIN` | `zuanshijian` → 钻石剑 |
| 声母 / 简拼 / 任意组合 | ✅ 恒开，不可关 | 递归匹配 | `zsj`、`zuanjian`、`tiezh` |
| 声调（可带可忽略） | ✅ 恒开，不可关 | 内置（`PinyinFormat.NUMBER`） | `zhong1国`、`zhen1` |
| 双拼 · 自然码 / 小鹤 | ✅ **实验性**，默认关 | `Scheme.SHUANGPIN_*` | 中国 = `vsgo` |
| 注音 · 大千 | ✅ **实验性**，默认关 | `Scheme.ZHUYIN_DACHEN` | 中国 = `5j/eji` |
| 模糊音 ×7 | ✅ 默认全关 | `Builder.fuzzyXxx(...)` ×7 + `allFuzzy(b)` | 开 `fuzzyZhZ` 后 `zong国` 命中「中国」 |
| 简体 / 繁体 | ⚠️ **不互通** | 随字典生效 | 繁体文本自身可拼音搜 |
| 即时 / 索引两种匹配 | ✅ | `matches(...)` / `Profile.Engine`（默认 `LOOP`） | — |
| 即时匹配加速 | ✅ 默认开 | `Profile.accelerate()` | — |
| 字形辅助码 | ❌ **永不做** | — | PinIn 明确不支持 |
| 上下文多音字（词级读音覆盖） | ❌ v1.0 不提供 | v1.1 的 `Profile.readingOverride` | 见 [`docs/API.md`](docs/API.md) §7 |
| 英文子串（大小写不敏感） | ✅ | 原文包含路径 | `diamond` → `Diamond Sword` |
| 英文词首字母 | ❌ | — | `DS` **搜不到** `Diamond Sword` |

**默认值的选择理由**：声母 / 简拼 / 声调不带来误命中，是纯增益 → 默认开；
模糊音是「放宽」（误命中上升）、双拼 / 注音会**改变 query 的解析方式**（属使用者主动选择的输入法）→ 默认关；
`accelerate` 默认开（本 API 的主力场景就是「一次 query × 多次 text」）；
引擎默认 `LOOP`，让「逐条能搜到、建索引后搜不到」这类诡异行为**在默认路径上不可能发生**。

---

## 3. 性能（先把这两句话记住）

- **小列表**：直接 `PinyinSearch.matches(...)`，单次微秒级 —— 每敲一个键重算一遍也没问题。
- **大列表**：`PinyinSearch.matcher(pool)` + 默认 `LOOP`（无真实索引）是**安全默认**；10k 条约为此表「遍历拼音匹配」的 1/4。
- **索引引擎**（`SIMPLE / TREE / CACHED`）：创建 `Matcher` 时**同步构建**，大 pool 且已验收等价性才用，**不要在渲染 / 输入回调里构建**。

> ⚠️ **`Profile` 是配置描述，不是一次性用品！**
> 构造 `PinIn` 会解析整份拼音字典（`TreeSearcher` 量级约 **9.5 MB** / 实例），本库按 `Profile` 缓存引擎
> （容量上限 16 + LRU 淘汰）。请在初始化时构造**少数几个** `Profile` 并长期复用，
> **不要在热路径上反复 `build()`**。

PinIn 官方实测数据（37k 词条 / ~400k 字符 / ~900KB 样本）与逐项开销 → [`docs/API.md`](docs/API.md)

---

## 4. 最容易踩的 5 个坑

1. `text` / `query` 为 `null` → `matches` 返回 `true`（按「query 为空」处理），调用方无需自己判空。
2. 内部任何异常都被捕获，退化为「原文包含」，**永不抛异常**。
3. `Matcher` 入参是 `List<String>`（不是 `Collection`）—— `searchIndices` 的下标只有在**稳定有序列表**上才有意义。
4. 线程：**必须在客户端主线程使用**；索引构建完成后是只读的，所以「异步构建 → 交回主线程使用」安全。
5. 简繁**不互通**：`钻石剑` 搜不到 `鑽石劍`（但繁体文本本身可用拼音搜）。

其余实测结论（多音字、声调位置、纯数字 query、英文子串、索引引擎等价性…）→
[`docs/API.md`](docs/API.md) §7；对应的回归断言在 `KnownBehaviorTest` 里。

---

## 5. 与 JustEnoughCharacters (JECh) 共存

| 方面 | 结论 |
|---|---|
| 类冲突 | **无**。内置 PinIn 已重定位到 `com.pinyinsearch.shaded.pinin`，jar 内**不存在** `me/towdium/pinin/**` |
| 行为差异 | **会有**：JECh 自己的配置（模糊音、拼法等）与本库 `Profile` 相互独立 → 同一文本在两个搜索框可能结果不同 |
| 是否读 JECh 配置 | **不读**。一读就把自己耦合到别人的内部实现上 |
| 定位差异 | JECh 用 coremod 给**别人的**模组打补丁；本库给**愿意主动适配的**模组一条路径。路线相反，不竞争 |

---

## 6. 内置的 PinIn（合规说明）

- 上游 [Towdium/PinIn](https://github.com/Towdium/PinIn)（MIT），vendor 的是 **1.6.0**（默认分支 `master`）。
- vendor **全量 16 个源文件，不裁剪任何类**（PinIn 内部可能有反射 / 动态加载，裁剪后可能「编译通过、运行期才炸」）。
- **唯一的改动是包名重定位**：`me.towdium.pinin` → `com.pinyinsearch.shaded.pinin`，拼音表资源路径随之移动为
  `com/pinyinsearch/shaded/pinin/data.txt`（`DictLoader.Default` 用的是 `PinIn.class.getResourceAsStream("data.txt")`，
  包相对路径自动跟随）。每个文件顶部都注明了这一改动，方便以后跟上游 diff。
- **fastutil**：上游声明 `8.3.0`，MC 1.20.1 自带 **8.5.9**（主版本一致）→ **复用 MC 自带的，不额外打包**。
- 合规：仓库根与 jar 内均有 `LICENSE`（MIT）与 `LICENSE-Pinin.txt`（PinIn 的 MIT 原文，原样拷贝）。
- **拼音引擎完全由 PinIn 提供，本库不发明任何拼音逻辑。**

---

## 7. 接入方式与坐标

- **软依赖**（见 §1）：玩家自己装，全服共用一份、行为统一、接入方 jar 不变大。
- **硬依赖 + jarJar**（玩家无感）：`jarJar` 嵌进自己的 jar，代价是每个依赖方各带一份 ~204KB。

```gradle
compileOnly 'com.github.2779789119:pinyinsearch:1.1.3'            // ✅
compileOnly 'com.github.2779789119:pinyinsearch:1.1.3:javadoc'    // IDE 悬停看中文 JavaDoc
compileOnly 'com.github.2779789119:pinyinsearch:1.1.3:sources'
// compileOnly 'com.github.2779789119.pinyinsearch:pinyin_search:1.1.2'  // ❌ 不要写成 com.github.User.Repo
```

坐标是 JitPack 的 `com.github.User:Repo:Tag` —— artifactId 就是仓库名，**区分大小写**，务必写小写 `pinyinsearch`。

**首次接入时请验完这四项**：① 只装内嵌本库的那一个模组 → 拼音搜索可用；② 两个都内嵌本库的模组同时加载 → 能正常启动；
③ 玩家既单独装本库、又装了内嵌本库的模组 → 是否报重复 modId；④ 与 JECh 同时装 → 无类冲突。
本库**无状态**，即使真加载了两份也不会互相污染 —— 最坏只是多占一份内存。

完整接入模板（含 Bridge 类与三种搜索框改法）→ [`docs/INTEGRATION.md`](docs/INTEGRATION.md)

---

## 8. 构建与测试

```bash
./gradlew build            # 编译 + 跑全部 JUnit 用例 + 打 jar（含 -sources.jar / -javadoc.jar）
./gradlew test             # 只跑测试
./gradlew javadocToDocs    # 生成 API 文档并同步到 docs/apidocs
```

测试是**纯 JUnit 5 表驱动**（匹配逻辑不依赖 MC 运行时，CI 里可直接跑）；CI 见 `.github/workflows/build.yml`。

---

## 9. 版本承诺与非目标

- **1.x 内的承诺**：`api` 包下所有公开类型**不改 / 不删**已有方法签名与公开字段，只允许**新增**。详见 [`docs/API.md`](docs/API.md) §6。
- **非目标**：❌ 不 mixin 进别人的 GUI / `EditBox` / 容器界面；❌ 不接管 JEI / REI 的搜索；
  ❌ 不处理输入法（IME）、不做繁简转换、不做分词、不实现字形辅助码；❌ 不提供玩家可见的配置界面；
  ❌ 第一期只做 1.20.1 Forge。
