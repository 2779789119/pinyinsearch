# 通用拼音搜索库模组 · 开发文档

> 面向「**其他模组开发者**」的拼音搜索库模组开发规格说明。目标是一个**新工程**，本文档可直接作为开发依据。
>
> **状态：v8，三轮评审已收敛，可动工。** §4 的 API 清单就是 v1.0 的完整清单，不多一行。

## 修订记录

前几轮的结论已全部并入正文，此处只留结论、不留过程：

| 轮次 | 收敛的关键结论 |
|---|---|
| v1–v4 | API 收敛为「`Matcher` 持有 pool、`search(query)` 取结果」；协议 CC0→**MIT**；JitPack 坐标格式修正（`com.github.User:Repo:Tag`）；按 JECh README / `generate.py` 校准能力面与实现路线；fastutil 改「实测版本比对」 |
| v5–v6 | **范围转向「能力全对齐 JECh」**（引入 `Profile`）；目标澄清：**不追采用率、不做推广** |
| v7 | 确认 **PinIn 配置是实例级**（`Profile` 隔离成立）；**默认引擎改 `LOOP`**（等价性由构造保证）；`readingOverride` 从静态方法移入 `Profile.Builder`；第一版**全量 vendor** |
| v8 | **`readingOverride` 在 v1.0 完全不提供**（三处矛盾表述已统一）；**`matcher` / `literal` 入参 `Collection`→`List`**（下标语义成立）；补**缓存内存账**；定义 **null 语义**；软依赖补 Gradle 配置与「延迟解析」注意；英文用例与伪代码修正 |

---

## 0. 定位

一个**给模组开发者用的拼音搜索库模组**：把 PinIn 的完整拼音匹配能力，包装成「**一行依赖、三行调用**」的形态。

**不做 GUI 注入、不碰 JEI/REI、不提供任何玩家可见界面** —— 它是能力提供方，不是 JECh 的替代品。

### 为什么值得单独做一个

| 现有方案 | 形态 | 为什么不能直接给开发者用 |
|---|---|---|
| JustEnoughCharacters (JECh) | 终端用户模组 | 靠 **coremod 替换各模组的文本匹配调用点**；内部类可反射调用，但**不是公开 API** |
| PinIn (Towdium) | 纯 Java 库 | 只有 library jar、**没有 `mods.toml`**，无法优雅依赖 / jarJar；使用方还得自己处理 fastutil、拼音数据、与 JECh 的类名冲突 |
| 其他「拼音搜索」模组 | 终端用户模组 | 同上，没有面向开发者的接入面 |

**空的是中间那层**：一个「可被模组依赖、也可被玩家单装」的拼音匹配库模组。

> **诚实的定位**：拼音引擎完全由 PinIn 提供，**本库不发明任何拼音逻辑**。价值在于把它 **MC 模组化** ——
> 打包拼音数据与 fastutil、relocate 隔离（不与 JECh 撞类）、提供异常安全且带归一化的傻瓜 API，
> 并**同时支持「玩家单装」与「jarJar 嵌入」两种形态**。别声称自己造了拼音引擎。

### JECh 的两条关键事实（决定了我们做什么、不做什么）

1. **它的路线是「替模组打补丁」**：`generate.py` 硬编码约 130+ 个调用点（`完全限定类名:方法名+描述符`，
   覆盖 RS / AE2 / 汤姆存储 / Quark / REI / JEI / EMI / Cloth Config / ProjectE / Mekanism / Patchouli / Botania …），
   构建期为每条生成 coremod JS 并打进 **JECh 自己的 jar**，启动时用 ASM 改写**那些模组**的类。
   因此**别的模组想做也做不了**：只能等 JECh 收录（未收录 → 游戏内 `/jech profile` 拿报告 → 提 issue → 作者加进
   `generate.py` → 下版本才生效）。它还有两个硬伤：**一个模组 × 一个版本 = 一条手维护清单**；
   被补丁的模组一改方法名或描述符，coremod **静默失配**，玩家只感觉「有时好使有时不好使」。
2. **它只覆盖写死了 `contains` / 正则的调用点，因此永远覆盖不到自建 GUI** ——
   自研搜索框、功能开关、过滤界面都不在清单里，只能靠「你提我加」。
   这正是「JECh 没有可用 API」的由来，也是本库存在的理由。

**两条结论：**

- **能力面 v1.0 全对齐**（全拼 / 声母 / 任意组合 / 声调 / 简拼 / 双拼 / 注音 / 模糊音 / 简繁）——
  这些**全都在 PinIn 里**，本库只负责暴露成可配置的 `Profile`（逐项来源见 §4.1.1）。
  唯一不做的是**字形辅助码**（PinIn 明确「不（也不会）支持」）。
- **路线相反，因此不竞争**：JECh 覆盖「没做适配的模组」，本库覆盖「愿意主动适配的模组」，
  且能让模组**不依赖任何第三方拼音模组**。

---

## 1. 目标与非目标

### 1.1 目标

- 提供**稳定、无副作用**的匹配 API（§4）—— 薄在**方法数量**，不薄在**能力**
- **能力对齐 JECh**：九项全支持并可逐项配置（能力全部来自 PinIn，来源见 §4.1.1）
  - ⚠️ **简繁互搜待实测**：取决于 PinIn 字典（地球拼音 + pinyin-data）的收录情况；
    若不支持，则本库**在这一项上不与 JECh 完全对齐**，将写进 §4.3 与 README
- **内置 PinIn**，不依赖任何第三方模组（装了就能用，零前置）
- **两种接入形态都支持**：玩家单装一份（全服共用）/ 依赖方 jarJar 嵌入（玩家无感）
- 与 JECh **可共存、不冲突**（类名隔离，§3.2）
- 有一个可编译、可打包、可发布的完整工程骨架

### 1.2 非目标（明确划出来，防止范围膨胀）

- ❌ **不** mixin 进别人的 GUI / `EditBox` / 容器界面
- ❌ **不** 接管 JEI / REI 的搜索
- ❌ **不** 处理输入法（IME）
- ❌ **不** 做繁简转换、分词（第一期）；简繁能否互搜**只随字典顺带生效**，不做转换
- ❌ **不** 实现字形辅助码 —— PinIn 明确**不（也不会）支持**，无实现路径
- ❌ **不解决上下文多音字**（v1.0）：「重庆」默认读 `zhongqing`，搜 `chongqing` 搜不到（§4.3）
- ❌ **不** 让英文词首字母可搜（`Diamond Sword` 不支持用 `DS` 搜到，见 §4.3）
- ❌ **不** 做多平台（第一期只做 1.20.1 Forge）
- ❌ **不** 提供任何玩家可见的配置界面（配置只在接入方的代码里）
- ❌ **不** 追求采用率、不做推广 —— 本库只是给其他开发者**一条可选的路径**；
  谁用、用的人多不多**不是本项目目标**，也不该成为任何设计决策的依据

> **红线**：前两条一旦做了，本模组就变成「第二个 JECh」，会正面对撞且风险和体量失控。
> 注意区分：**能力多**是暴露 PinIn 的开关（成本极低、不做白不做），**范围膨胀**是去改别人的 GUI —— §1.2 挡的是后者。

---

## 2. 元信息（默认值，可改）

| 项 | 取值 |
|---|---|
| modid | `pinyin_search` |
| 显示名 | 通用拼音搜索（开发者库） / Pinyin Search API |
| 包名 | `com.<yourid>.pinyinsearch` |
| relocate 目标包 | `com.<yourid>.pinyinsearch.shaded.pinin` |
| 协议 | **MIT**（本模组自身代码）；PinIn 部分保留其 **MIT** 声明 |
| 前置依赖 | 无 |
| MC / 加载器 | 1.20.1 / Forge 47.x |
| 分发 | CurseForge / Modrinth；JitPack 提供 Maven 坐标（写法见 §7.3） |

> **为什么是 MIT 而不是 CC0**：CC0 对软件的「放弃版权」在部分司法管辖区有争议，且没有专利授权条款；
> 而**接入摩擦只取决于 API 设计，不取决于协议**（MIT 只要求保留版权声明，对依赖方几乎无感）。
> 反正要带 `LICENSE-Pinin.txt`，多带一个 `LICENSE` 是零成本。

---

## 3. 技术选型与关键约束

### 3.1 匹配引擎：PinIn

仓库 <https://github.com/Towdium/PinIn>（默认分支 `master`）· 协议 **MIT** · **纯 Java**（不是 Kotlin，便于 vendor 源码）·
JitPack 坐标 `com.github.Towdium:PinIn:<version>` · 拼音数据来自「地球拼音」与 pinyin-data（打包在 `data.txt`）。

**能力清单**：`contains` / `begins` / `matches` 三种匹配语义；**7 种模糊音开关**
（`fZh2Z` `fSh2S` `fCh2C` `fAng2An` `fIng2In` `fEng2En` `fU2V`）；`Keyboard`
（全拼 / 双拼自然码·小鹤 / 注音大千）；即时匹配（手写递归 + `IndexSet`）与 `accelerate` 加速模式；
索引匹配 `TreeSearcher` / `SimpleSearcher` / `CachedSearcher`；汉字转换 `getChar` / `getPinyin` / `getPhoneme` 与 `PinyinFormat`。

#### ★ 配置作用域（源码确认 —— 这决定了 `Profile` 隔离能否成立）

读 `PinIn.java` 得到三条事实：

1. **配置字段全部是实例字段**：`keyboard`、`fZh2Z`…`fU2V`、`accelerate`、`format` 都是 `private`（非 `static`）成员；
2. **`config()` 是非静态方法**，返回 `Config` —— 而 `Config` 是**非静态内部类**，构造时从 `PinIn.this` 读当前值，
   其 `commit()` 写回**该实例**；
3. **`Ticket` 同样是非静态内部类**，读写的是它所属 `PinIn` 实例的 `modification`。

> **结论：PinIn 的配置是实例级的，不存在全局单例。**
> 因此「**一个 `Profile` 对应一个独立 `PinIn` 实例**」即可实现隔离，
> 「两个不同 Profile 的 `Matcher` 交叉使用互不干扰」（§8.2 第 5 点）**成立**，不需要降级方案。

由此得到三条**必须照做的实现约束**：

1. **`PinIn` 构造是重操作**（构造函数里就 `DictLoader.load(...)` 解析整份拼音字典）→
   绝不能「每次 `matches()` 都 new 一个」，**必须按 `Profile` 缓存并复用**实例（`impl/EngineCache`）。
2. **`Profile` 必须是不带可变状态的不可变值对象，且正确实现 `equals` / `hashCode`** ——
   否则缓存无法命中，同一个配置会被当成不同 key，字典被反复解析。
3. **★ 缓存必须算内存账**：单个引擎实例的内存**可观**（`TreeSearcher` 量级约 **9.5MB**，见 §4.4），
   而 `Profile` 是**公开可构造**的 —— 调用方可以造出任意多个不同 `Profile`。
   「无淘汰策略的缓存」+「公开可构造的 key」= 每个新 `Profile` 常驻一份引擎：
   被 10 个模组嵌入、每个模组各建 5 个 `Profile`，就是数百 MB 的**静默**占用。
   **v1.0 的处理**：以**文档约束**为主 —— README 与 JavaDoc 必须写明「`Profile` 是**配置描述**，
   请在初始化时构造少数几个并长期复用，不要在热路径上反复 `build()`」；
   实现上给缓存一个**容量上限**（超出时淘汰最久未用的，最坏退化为重新构造），不做主动释放；
   弱引用等更复杂的方案留到确实观察到内存问题再上。

> 落地前通读 `src/main/java/me/towdium/pinin/`；**具体方法签名以源码为准**，不要凭记忆写。

### 3.2 【硬要求】必须 relocate 包名

JECh 已经把它依赖的 PinIn **原样打进了自己的 jar**（`me/towdium/pinin/**` + 拼音表 `data.txt`）。
如果本模组也用 `me.towdium.pinin.*`：同一个 classloader 上会出现**两份同名类**（谁先加载谁赢），
两边的 PinIn 版本还不一定一致 → 玄学 bug（行为差异），且排查成本极高（报错栈看起来完全正常）。

**因此必须把 PinIn 重定位到 `com.<yourid>.pinyinsearch.shaded.pinin`。这条不做，项目不要发。**

### 3.3 fastutil：**必须实测版本比对**，不能只看编译期

PinIn 依赖 `it.unimi.dsi.fastutil`，而 **MC 本身就带 fastutil**。目标做法是只 vendor PinIn 源码、
**复用 MC 自带的 fastutil**（体积最小、不与 MC / 其他模组打架）。

但「缺类编译期就会报错」这个判断**只覆盖了一半**情况：还可能**有该类但方法签名不同**（版本差异）——
vendor 的 PinIn 调用的方法在旧版恰好也存在 → 编译通过，运行期 `NoSuchMethodError` 或行为异常。

**执行步骤（写进 §5.3 的 vendoring 流程）：**

1. 记录 PinIn 上游 `build.gradle` 里声明的 fastutil **版本号**；
2. 查出 MC 1.20.1 实际自带的 fastutil **版本号**（查整合包 `libraries/` 目录或版本清单）；
3. 两者**主版本一致** → 复用 MC 的；**主版本不同** → 直接 relocate 一份 fastutil 进本项目，**别赌**；
4. 无论走哪条，都要跑一次「启动 + 一次完整拼音搜索」的**运行期冒烟测试**，不能只靠编译通过。

**备选方案**：用 PinIn 的 **shadow(fat) 版**作来源时，必须把其中的 fastutil **一起 relocate**，
否则会重复打包 ~200KB 并与 MC 的 fastutil 撞名。

### 3.4 不要把 PinIn 做成「jarJar 嵌库」

Forge 原生 `jarJar` 是**面向模组 jar** 的（按模组坐标识别、去重），把纯库塞进去不靠谱。
**正确用法**：PinIn 源码 vendor 进本模组（§5.3），`jarJar` 只用来把**本模组自己**提供给依赖方。

### 3.5 与 JECh 共存

| 方面 | 结论 |
|---|---|
| 类冲突 | relocate 后**无冲突** |
| 行为差异 | 会有：JECh 自己的配置（模糊音、拼法等，由玩家改配置文件）与本库 `Profile` 相互独立 → 同一文本在两个搜索框可能结果不同 |
| 是否读 JECh 配置 | **不读**。一读就把自己耦合到别人的内部实现上，违背项目初衷 |
| README 需注明 | 「与 JECh 并存无冲突；两者配置独立，匹配结果可能略有差异」 |

---

## 4. 对外 API（稳定面）

**这是整个项目的核心交付物。** 原则：**稳定、无副作用、异常不抛**；
方法数量保持精简（默认重载 + 可选 `Profile` 重载），但**能力不做删减**。

**JavaDoc 硬要求**：`api` 包下的每一个公开类型、每一个公开方法都**必须**有完整 JavaDoc，至少覆盖：

- 参数语义（尤其 `text` / `query` 哪个是"被搜的文本"、哪个是"用户输入"）
- 返回值语义（`matches` 的 `true` 条件、`search*` 的顺序与重复项处理）
- **异常行为**：明确写"本方法不抛异常；内部异常一律退化为原文包含"
- **线程安全**：明确写"必须在客户端主线程使用；实例不保证线程安全"
- **null 与空串行为**：明确写「任一参数为 `null`、或 `query` 为空/全空白 → 返回 `true`」（§4.1）

> 开发者库的交付物就是 API，JavaDoc 不是可选项。

```java
package com.<yourid>.pinyinsearch.api;

public final class PinyinSearch {

    /** 库是否可用（正常加载时恒为 true）。依赖方做能力探测用。 */
    public static boolean isAvailable();

    /**
     * 单对单匹配 —— 最常用，使用默认 Profile（见 §4.1.1）。
     * 小列表（几十~几百条）逐条判断用这个，无需建索引。
     * 内部自行处理归一化，调用方不需要预处理。
     */
    public static boolean matches(CharSequence text, CharSequence query);

    /** 单对单匹配，指定 Profile。 */
    public static boolean matches(CharSequence text, CharSequence query, Profile profile);

    /**
     * 大数据集建索引/包装成 Matcher，之后可反复用不同 query 搜索。使用默认 Profile。
     * <p>
     * ⚠️ `pool` 必须是**稳定有序**的列表 —— `Matcher.searchIndices` 返回的下标语义
     * 依赖「迭代顺序」，传 `Set` 之类无序集合时下标无意义（见 `Matcher` 的接口注释）。
     * ⚠️ 默认引擎为 LOOP（逐条调 matches 等价实现），构建成本见 §4.4。
     */
    public static Matcher matcher(List<String> pool);

    /** 建 Matcher，指定 Profile（含引擎选择）。 */
    public static Matcher matcher(List<String> pool, Profile profile);

    /** 默认 Profile（内容 = §4.1.1 表的「默认」列）。 */
    public static Profile defaultProfile();

    /** 自定义 Profile 的入口；链式配置后 `build()`。 */
    public static Profile.Builder profile();

    /** 归一化（小写 + 全角转半角 + 去首尾空白）。行为稳定性承诺见 §4.2。 */
    public static String normalize(String text);
}
```

```java
package com.<yourid>.pinyinsearch.api;

public interface Matcher {

    /**
     * 命中 pool 中的哪些项 → 返回**下标**，按 `pool` 的迭代顺序升序。
     * <p>
     * ⚠️ 下标只有在 `pool` 是**稳定有序列表**时才有意义 —— 这也是
     * `PinyinSearch.matcher(...)` 的入参类型是 `List<String>` 而不是 `Collection<String>` 的原因：
     * 传 `HashSet` 时迭代顺序不保证稳定，下标无法可靠映射回元素，两次调用之间甚至可能对不上。
     * <p>
     * 大 pool 下 `List<Integer>` 有装箱开销，换来的是 API 简洁；
     * 性能敏感场景请自行用 `matches()` 遍历（见 §4.4）。
     */
    List<Integer> searchIndices(CharSequence query);

    /** 便捷：直接拿到命中的文本（保留 pool 顺序与重复项，不去重）。 */
    List<String> search(CharSequence query);

    /**
     * **不依赖 PinIn 的纯原文包含实现**（只做大小写不敏感的子串包含），接口形状与拼音版一致。
     * <p>
     * 用途：调用方想在代码里**统一按 `Matcher` 抽象**处理搜索（例如自己有「关闭拼音搜索」的开关，
     * 或某个 pool 不值得建索引），就可以直接换成这个实现，不必自己再写一套。
     * <p>
     * ⚠️ **它不能用来兜「本模组整体没装」的情况** —— 那种情况下连 `Matcher` 这个类都加载不了
     * （`NoClassDefFoundError`）。「没装则行为不变」的回退必须由调用方自己写，见 §7.1。
     */
    static Matcher literal(List<String> pool);
}
```

```java
package com.<yourid>.pinyinsearch.api;

public final class Profile {

    /** 输入方式。枚举名自定义，实现时映射到 PinIn 的 `Keyboard`（其常量名以源码为准）。 */
    public enum Scheme {
        QUANPIN,             // 全拼（默认）
        SHUANGPIN_ZIRANMA,   // 双拼 · 自然码
        SHUANGPIN_XIAOHE,    // 双拼 · 小鹤
        ZHUYIN_DACHEN,       // 注音 · 大千
    }

    /** Matcher 的底层实现。只影响 `matcher(...)` 路径，不影响 `matches(...)`。 */
    public enum Engine {
        LOOP,    // 默认：逐条调 PinIn.contains —— 与 matches() 的等价性由构造保证
        SIMPLE,  // PinIn SimpleSearcher：构建快、搜索慢
        TREE,    // PinIn TreeSearcher：构建慢、搜索快
        CACHED,  // PinIn CachedSearcher：介于两者之间
    }

    public Scheme scheme();
    public Engine engine();

    /** 7 种模糊音（一一对应 PinIn 的 fZh2Z/fSh2S/fCh2C/fAng2An/fIng2In/fEng2En/fU2V），默认全关。 */
    public boolean fuzzyZhZ();
    public boolean fuzzyShS();
    public boolean fuzzyChC();
    public boolean fuzzyAngAn();
    public boolean fuzzyIngIn();
    public boolean fuzzyEngEn();
    public boolean fuzzyUV();

    /** 即时匹配加速（PinIn 的 `accelerate`）。默认 `true`，见 §4.4。 */
    public boolean accelerate();

    /** 本类型是不可变值对象，且**必须**正确实现 equals/hashCode（用于引擎实例缓存，见 §3.1）。 */
    @Override public boolean equals(Object o);
    @Override public int hashCode();

    public Builder toBuilder();

    public static final class Builder {
        public Builder scheme(Scheme scheme);
        public Builder engine(Engine engine);
        public Builder fuzzyZhZ(boolean on);
        public Builder fuzzyShS(boolean on);
        public Builder fuzzyChC(boolean on);
        public Builder fuzzyAngAn(boolean on);
        public Builder fuzzyIngIn(boolean on);
        public Builder fuzzyEngEn(boolean on);
        public Builder fuzzyUV(boolean on);
        /** 便捷：一次性开关全部模糊音。 */
        public Builder allFuzzy(boolean on);
        public Builder accelerate(boolean on);
        public Profile build();
        // v1.1 才会新增 readingOverride(String text, String pinyin)。
        // v1.0 **不提供**该方法，也**不提供** no-op 占位 —— 理由见 §4.3。
    }
}
```

**为什么不用 `matcher(query, pool)`**：那个形状每次查询都要带一遍 pool，必然要做「按 pool 身份缓存索引」
的隐式优化，调用方难以预测开销；现在 `matcher(pool)` 一次性建好包装、`search(query)` 反复用，开销在哪儿一目了然。

### 4.1 `matches(text, query)` 的语义（验收标准）

| 情况 | 返回 |
|---|---|
| `text == null` 或 `query == null` | `true`（**按「`query` 为空」处理** —— 与「不抛异常、退化为原文包含」的整体哲学一致，调用方无需自己判空） |
| `query` 为空 / 全空白 | `true` |
| 归一化后原文包含 query | `true` |
| **中文**命中全拼 / 声母 / 简拼 / 任意组合（铁砧 → `tiezhen` / `tz` / `tiezh`；中国 → `zhong国`、`zh国`） | `true` |
| **中文**带声调（中国 → `zhong1国`、`zh1国`） | `true` |
| 默认 Profile 下的模糊音（中国 → `zong国`、`z国`） | **`false`** —— 默认全关；开 `fuzzyZhZ` 后为 `true` |
| 英文命中词首字母（`Diamond Sword` → `DS`） | **不保证**（见 §4.3） |
| 完全无关 | `false` |
| 内部任何异常 | 退化为「原文包含」，**永不抛异常** |

**★ 硬性等价要求（不通过不发版）**：对同一 `pool`、同一 `query`、同一 `Profile`，
`matcher(pool, p).searchIndices(query)` 的结果，必须与「`pool` 中所有满足 `matches(item, query, p) == true`
的下标集合」**完全相等**。

- `Engine.LOOP`（**默认**）：等价性由**构造**保证（它就是逐条调同一个 `matches` 实现）→ **必过**。
- `Engine.SIMPLE / TREE / CACHED`：PinIn 的索引匹配与即时匹配**是否语义一致未经验证**，必须按 §8.1 实测；
  若在声调 / 模糊音 / 双拼解析等边界上不一致，则**把差异写进 §4.3，并把该引擎从默认候选中排除**。
- 把默认引擎设成 `LOOP` 正是为了让「逐条能搜到、建索引后搜不到」这类诡异行为**在默认路径上不可能发生**；
  索引引擎是**显式选择**的优化，谁用谁承担已验证过的前提。

### 4.1.1 能力矩阵（JECh ↔ PinIn ↔ 本库）与默认 Profile

**关键结论：下表每一项都是 PinIn 现成的能力，本库只做「暴露 + 默认值选择」** ——
换言之，实现 JECh 的全部功能**几乎不用写匹配逻辑，只需暴露配置**。

| 能力 | PinIn 里的来源（源码级确认） | 本库暴露方式 / 默认 | 例 |
|---|---|---|---|
| 原文包含 | 匹配算法自带 | 恒开，不可关 | `钻石` → 钻石剑 |
| 全拼 | `Keyboard.QUANPIN`（默认） | `Scheme.QUANPIN`，✅ | `zuanshijian` → 钻石剑 |
| 声母 / 简拼 / 任意组合 | `Matcher.contains` 递归 + `IndexSet` | 恒开，不可关 | `zsj`、`zuanjian`、`tiezh` |
| 声调（可带可忽略） | 内置（`PinyinFormat.NUMBER`） | 恒开，不可关 | `zhong1国`、`zh1国` |
| 双拼 · 自然码 / 小鹤 | `Keyboard` 的另两档 | `Scheme.SHUANGPIN_*`，❌ 关 · **实验性** | 开了以后 query 按双拼解析 |
| 注音 · 大千 | `Keyboard` 的另一档 | `Scheme.ZHUYIN_DACHEN`，❌ 关 · **实验性** | 同上 |
| 模糊音 ×7 | `fZh2Z` … `fU2V` | `Builder.fuzzyXxx(...)` ×7 + `allFuzzy(boolean)`，❌ 全关 | — |
| 简体 / 繁体 | `data.txt`（地球拼音 + pinyin-data） | 随数据生效，**需实测** | 不对齐风险见 §1.1 |
| 即时 / 索引两种匹配 | `contains` / `TreeSearcher`·`SimpleSearcher`·`CachedSearcher` | `matches(...)` / `Profile.Engine`，默认 `LOOP` | — |
| 实时切换配置 | `Config.commit()` + `Ticket.renew()`（均为**实例级**，§3.1） | 换成**不可变 `Profile`**：换 Profile 就是换一个 `PinIn` 实例，天然隔离 | — |
| 即时匹配加速 | `accelerate`（ThreadLocal） | `Profile.accelerate()`，✅ **默认开**（§4.4） | — |
| 诊断（`/jech profile`） | 不适用 | 不做（我们没有需要 patch 的调用点） | — |
| **字形辅助码** | **不存在** | ❌ 不做（§4.3） | — |
| 词级读音覆盖（多音字） | 需自建（`DictLoader` 是**字级**的，不够用） | ❌ **v1.0 无**；v1.1 形态见 §4.3 | — |
| 英文 | 原文包含 | 只做大小写不敏感子串包含，不做词首字母 | `diamond` **需实测**（§4.3） |

**默认值的选择理由：**

- **声母 / 简拼 / 声调默认开**：这些不会带来误命中，纯增益，且 JECh 默认也是这套。
- **模糊音 / 双拼 / 注音默认关**：前者是"放宽"（开了 `z国` 也命中，误命中上升）；
  后两者会**改变 query 的解析方式**，属于使用者主动选择的输入法，不该默认改变所有人的搜索行为。
  想看全开就 `allFuzzy(true)`。
- **`accelerate` 默认开**：本 API 的主力场景（一次 query × 多次 text）正是 PinIn 注释里说的稳定调用。
- **引擎默认 `LOOP`**：让等价性由构造保证（§4.1）。

### 4.2 设计约束

- **归一化由库吃掉**：小写、全角→半角、去首尾空白。这是调用方最容易踩的坑。
  （双拼 / 注音模式下归一化不改变按键串，只做大小写与全角处理。）
- **无副作用**：不写日志（除 debug 开关）、不改全局状态、不注册事件。
- **不抛异常**：任何 `Throwable` 一律 capture 后退化。
- **无状态**：`api` 包下的公开类型**不持有任何可变全局状态**；`Profile` 是不可变值对象，
  `PinyinSearch` 只有内部私有的引擎缓存。这样被多个模组 jarJar 嵌入也完全安全（同 §7.2）。
- **版本承诺（1.x 内）**：
  - **`api` 包下所有公开类型**：不改 / 不删已有方法签名与公开字段，只允许**新增**方法或重载；
  - **`Matcher.searchIndices` / `search` 的命中判定语义保持不变**；如需改变匹配语义，
    只能通过**新增方法**或**新增 `Profile` 配置项**（且新配置项必须有「不改变既有行为」的默认值）实现；
  - **`Profile` 的配置项只增不改语义**；
  - **`normalize` 的输入输出行为保持稳定** —— 一旦调用方拿它建过索引，行为变化会让调用方的索引失效；
    如需调整，只能新增 `normalizeV2`，不得改动 `normalize` 的行为。

### 4.3 已知限制与预留扩展点

| 限制 | 说明 | 处理 |
|---|---|---|
| **上下文多音字** | 「重庆」默认 `zhongqing`，搜 `chongqing` 搜不到；PinIn 不做上下文判断 | v1.0 不解决；**README 必须写明**；v1.1 用 `Profile.readingOverride` |
| **字形辅助码** | 无实现路径（PinIn 明确不支持，也不会支持） | 永久不做；README 写明 |
| 简繁互搜 | 本库不做繁简转换；简繁能否互搜取决于 PinIn 字典的收录情况 | **需实测**后写进 README，并据此修正 §1.1 的「能力对齐」表述 |
| 双拼稳定性 | PinIn 自述「双拼输入尚在测试阶段」 | 功能提供，但文档与 README 标注**实验性** |
| **纯数字 query** | 声调写成数字（`zhong1国`）与「原文里本来就有数字」共用同一字符空间，PinIn 如何取舍**未验证** | **需实测**（§8.1）；若存在误解析，考虑加归一化规则：query 全为数字时直接走原文包含 |
| 索引引擎语义 | 索引匹配与即时匹配是否逐条等价**未验证** | 默认用 `LOOP` 规避；索引引擎须过 §8.1 等价性验收 |
| 英文子串匹配 | 「`diamond` 命中 `Diamond Sword`」依赖「归一化小写 + 原文包含」这条路径，逻辑上应成立，但 **PinIn 是否干扰纯拉丁文本的匹配路径从未验证** | **需实测**（§8.1）；通过后转为正式用例，否则写进 README |
| 英文词首字母 | `Diamond Sword` **不支持**用 `DS` 搜到 | 与 §1.2 一致；README 写明 |
| 分词 | 不做 | 第一期不做 |

**v1.1 的多音字扩展点（v1.0 完全不提供）**

初稿曾把它设计成静态方法 `PinyinSearch.registerReadingOverride(text, pinyin)`。
这与 §4.2 的「无状态」承诺**直接冲突**：静态注册表是全局可变状态，被多个模组 jarJar 嵌入时会互相污染，
也破坏了 §7.2 的嵌入安全性。因此改为**挂在 `Profile` 上**：

```java
// v1.1
Profile p = PinyinSearch.profile()
        .readingOverride("重庆", "chongqing")
        .readingOverride("银行", "yinhang")
        .build();
```

语义：当**被匹配的文本**中出现该子串时，该子串按指定读音参与匹配（**词级优先于单字的默认读音**）。

- **v1.0 完全不提供**：`Profile` 里**没有** `readingOverrides()` 字段，`Profile.Builder` 里**没有**
  `readingOverride(...)` 方法 —— 既没有实现，也**没有 no-op 占位**。
  理由：静默 no-op 会让调用方误以为生效，是更差的体验；而「给 `Builder` 新增方法」本身就是向后兼容的变更，
  v1.1 新增即可，没有必要提前占位。
  **换句话说：§4 的 API 清单就是 v1.0 的完整清单，不多一行。**
- ⚠️ **实现机制注意**：PinIn 的 `DictLoader` 是**字级**的（`Char` codepoint → 读音列表），
  用它来做「重庆 → chongqing」会连带把「重要」的「重」也改掉，**不可接受**。
  因此 v1.1 需要的是**词级**方案（例如匹配前对 haystack 做分段 / 标注，让指定子串走固定读音）。
  形态先记在这里，避免 v1.1 引入破坏性变更。

### 4.4 性能与线程

PinIn 官方实测（37k 词条 / 约 400k 字符 / 约 900KB 样本，**可直接引用进我们的 README**）：

| 匹配方式 | 构建耗时 | 搜索耗时 | 内存 |
|---|---|---|---|
| `TreeSearcher`（索引） | 210 ms | 0.19 ms | 9.50 MB |
| `SimpleSearcher`（索引） | 27 ms | 9.1 ms | 1.84 MB |
| `CachedSearcher`（索引） | 28 ms（+预热 16 ms） | 0.55 ms | 介于两者之间 |
| 遍历拼音匹配（即时，= 我们的 `LOOP`） | — | 23 ms | — |
| 遍历 `contains`（即时，非拼音） | — | 0.53 ms | — |

（前缀匹配另有数据，数量级相近。）

**对本库 API 的含义：**

| 路径 | 索引 | 开销 / 建议用法 |
|---|---|---|
| `matches(text, query)` | 无 | 单次微秒级。小列表逐条调用，每敲一个键重算一遍也没问题 |
| `matcher(pool)` + `Engine.LOOP`（**默认**） | 无真实索引，内部就是逐条 `contains` | **安全默认**，等价性由构造保证；10k 条约为此表「遍历拼音匹配」的 1/4，量级仍可接受 |
| `matcher(pool)` + `SIMPLE / TREE / CACHED` | **同步构建** | 大 pool 且已通过等价性验收时使用；**不要在渲染 / 输入回调里构建** |
| `Matcher.search*(query)` | 复用已建索引 | 同一份 `Matcher` 反复服务不同 query |

- **引擎实例缓存（必做）= 一笔内存账**：`PinIn` 构造要解析整份字典（§3.1），单实例内存可观
  （上表 `TreeSearcher` 量级约 **9.5MB**），因此 `PinyinSearch` 内部按 `Profile` 缓存实例。
  **`Profile` 公开可构造 —— 请构造少数几个并长期复用**（详见 §3.1 第 3 条）。
- **缓存为何用 `ConcurrentHashMap`**：本库的使用约束是「客户端主线程」，按此用普通 `HashMap` 就够；
  选并发容器是**防御性**的 —— `matches()` 是静态入口，挡不住没读过文档的调用方从别的线程（比如异步预处理）调它，
  一旦并发就会同时写引擎缓存。代价可忽略。
  但这**不代表并发被支持**：引擎内部（PinIn 的 `Cache`）与 `Matcher` 仍要求主线程使用。
- **`accelerate` 默认开**：PinIn 源码注释明确 —— 以**不同的 `s1`、相同的 `s2`** 连续调用约 100 次属于稳定调用，
  此时加速模式**显著提速**；反之（`s2` 频繁变化）缓存管理反而拖慢。搜索框正是前者。
  若调用方是「query 频繁变、text 固定」的少见场景，可 `Profile.accelerate(false)`。
- **`Matcher` 不保证线程安全，并且必须在客户端主线程使用**：
  PinIn 内部的 `Cache`（`pinyins` / `phonemes`）会随查询惰性增长，并发访问不安全。
- 索引构建完成后是**只读**的，因此「异步构建 → 构建完成后交回主线程使用」是安全的用法。
- `searchIndices` 返回 `List<Integer>` 有装箱开销（大 pool 下可见）。**保留该形态以换 API 简洁**，
  性能敏感的调用方用 `matches()` 自行遍历即可。

### 4.5 关于能力映射的两处常见误读

> ⚠️ **「双拼下字形辅助码不可用」是误读**：JECh README 这么写，读起来像"只在双拼下不可用"。
> 但 PinIn README 的原话是「双拼输入尚在测试阶段，并且**不（也不会）支持字形码**」——
> 即**从来没有、也不会**支持字形码，与是否双拼无关。

> ⚠️ **为什么用不可变 `Profile` 而不是 PinIn 的 `Config`**：危险不在于「全局单例」（它不是，见 §3.1），
> 而在于 `Config.commit()` 是**就地修改所属 `PinIn` 实例** —— 多个调用方共享同一个实例时，
> A 改了配置 B 就被改，而且是静默的。本库的做法是「**一个 `Profile` 一个实例 + 换配置即换实例**」，
> 让共享只发生在**配置完全相同**的情况下，因此不存在互相污染。

---

## 5. 工程结构

### 5.1 目录

```
src/main/java/com/<yourid>/pinyinsearch/
├── PinyinSearchMod.java            # @Mod 入口（只为让它是个「真模组」）
├── api/
│   ├── PinyinSearch.java           # ★ 门面：唯一对外入口（含内部引擎缓存）
│   ├── Profile.java                # ★ 不可变配置（拼法 / 引擎 / 模糊音 / 加速）+ Builder
│   └── Matcher.java                # ★ 接口 + literal(pool) 退化实现
├── impl/
│   ├── PininMatcher.java           # Matcher 的拼音实现（LOOP / 索引两种引擎）
│   ├── EngineCache.java            # 按 Profile 缓存 PinIn 实例（§3.1 要求）
│   └── Normalizer.java             # 归一化
└── shaded/pinin/**                 # vendored 的 PinIn（已改包名，见 §5.3）

src/main/resources/
├── META-INF/mods.toml
├── pack.mcmeta
├── LICENSE                         # MIT 全文
├── LICENSE-Pinin.txt               # ★ PinIn 的 MIT 原文（合规必需）
└── com/<yourid>/pinyinsearch/shaded/pinin/data.txt   # 拼音表（路径随包名）
```

### 5.2 `mods.toml` 要点

```toml
modLoader = "javafml"
loaderVersion = "[47,)"
license = "MIT"              # ← 必须放在 header，不能放进 [[mods]]
issueTrackerURL = "..."

[[mods]]
modId = "pinyin_search"
version = "${mod_version}"
displayName = "Pinyin Search API"
authors = "..."
description = '''
A pinyin search library for mod developers. No GUI injection, no JEI/REI takeover.
'''

[[dependencies.pinyin_search]]
modId = "forge"
mandatory = true
versionRange = "[47,)"
ordering = "NONE"
side = "BOTH"

[[dependencies.pinyin_search]]
modId = "minecraft"
mandatory = true
versionRange = "[1.20.1,1.21)"
ordering = "NONE"
side = "BOTH"
```

- **side 用 `BOTH`**：库本身无副作用，写成 BOTH 最省事，也避免依赖方 jarJar 后在服务端「缺模组」的困惑。
  （虽然实际只在客户端用，但没必要为它引入 side 限制。）
- **不要**声明任何前置依赖。

### 5.3 Vendoring PinIn 的步骤

1. 从 <https://github.com/Towdium/PinIn>（默认分支 `master`）取 `src/main/java/me/towdium/pinin/**`（**纯 Java 源码**）。
2. 全局替换包名：`me.towdium.pinin` → `com.<yourid>.pinyinsearch.shaded.pinin`
   （含 `import`、字符串常量、资源路径常量）。
3. 把拼音表 `data.txt` 放到 `src/main/resources/com/<yourid>/pinyinsearch/shaded/pinin/data.txt`，
   并确认 `DictLoader` 里读资源的路径常量已同步改掉。
4. **记录 PinIn 上游声明的 fastutil 版本号，与 MC 1.20.1 自带版本比对**（§3.3）：
   主版本不同就走备选方案（relocate 一份 fastutil），别赌编译通过。
5. **★ 第一版全量 vendor，不裁剪任何类**：
   PinIn 内部可能存在反射 / 动态加载 / 间接依赖，裁剪后可能"编译通过、运行期才炸"，而这种 bug 极难定位。
   本模组预期体积 ~200KB，**不值得为省几十 KB 冒这个险**。
   体积优化留到稳定之后，且必须先有完整的运行期冒烟测试兜底。
6. 尽量**不动逻辑**，只改包名与资源路径；改动处加注释，方便以后跟上游 diff。
7. 保留 `LICENSE-Pinin.txt`（原样拷贝仓库的 LICENSE）。
8. `compileJava` 跑一遍补缺类，然后**必须再跑一次运行期冒烟测试**（§3.3 第 4 点）。

> **体积预期**：JECh 整个 jar 约 260KB，其中 PinIn（含 `data.txt`）占大头。
> 本模组最终 jar 预期在 **200KB 量级** —— 这个量级对模组完全无感。

### 5.4 `build.gradle` 骨架（要点）

```gradle
java.toolchain.languageVersion = JavaLanguageVersion.of(17)

minecraft {
    mappings channel: 'official', version: '1.20.1'
    // runs 按需配置
}

dependencies {
    minecraft "net.minecraftforge:forge:1.20.1-47.3.0"
    // ★ 不引入 PinIn 依赖：源码已 vendor 进 src/main/java
    // ★ 不需要 Shadow / relocation 插件
}

jar {
    manifest { attributes([...]) }
}
jar.finalizedBy('reobfJar')
```

### 5.5 CI/CD 要求

- **GitHub Actions**：push / PR 触发 `gradlew build`（含 `compileJava` 与 §8.1 的用例测试）。
- **测试必须能在无 MC / 无客户端的环境下跑**：§8.1 的用例表应做成**纯 JUnit 表驱动**
  （匹配逻辑本身不依赖 MC 运行时），`gameTest` 只用于 §8.2 的集成项。
  否则 CI 里跑测试的成本高到没人愿意配 —— 一个"配了但没人跑"的 CI 等于没有。
- **打 tag 自动发布**：`v*` tag 触发构建并把产物发布出去，让 **JitPack 能按 tag 取到坐标**（§7.3）。
- **tag 即版本**：§4.2 的版本承诺以 tag 为准，不鼓励用 `-SNAPSHOT` 之外的不确定引用。

---

## 6. 合规清单

- **仓库根**：`LICENSE`（MIT 全文），README 顶部写明「MIT」。
- **发布物（jar）内**：`LICENSE`（MIT）+ `LICENSE-Pinin.txt`（PinIn 的 MIT 原文，**原样**）。
- **README / 页面**：致谢 PinIn（附链接与协议）；说明「本模组内置了 PinIn（MIT），已重定位包名以避免
  与 JustEnoughCharacters 冲突」；**明确写出 §4.3 的限制**，尤其「不解决上下文多音字」「不支持字形码」
  「英文不支持词首字母」，以及简繁互搜的实测结论。

> MIT 的核心义务就是**保留版权声明与许可全文**。只要 jar 里有 `LICENSE-Pinin.txt`、README 有致谢，就满足。

---

## 7. 依赖方接入方式（README 首页必须写清）

### 7.1 方式一：软依赖（玩家自己装）

**依赖方 `build.gradle`：**

```gradle
repositories {
    maven { url 'https://jitpack.io' }   // JitPack 坐标格式见 §7.3
}

dependencies {
    // 只编译期需要；运行期靠玩家自己装（或整合包统一安装，见 §7.2）
    compileOnly 'com.github.2779789119:pinyinsearch:1.0.0'
}
```

**调用代码（完整片段，可直接复制）：**

```java
import com.<yourid>.pinyinsearch.api.PinyinSearch;
import net.minecraftforge.fml.ModList;
import java.util.Locale;

public static boolean matchesQuery(String text, String query) {
    if (ModList.get().isLoaded("pinyin_search")) {
        return PinyinSearch.matches(text, query);
    }
    // 没装：行为完全不变
    return text.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT));
}
```

- 适合：不想让玩家无感、愿意接受「装了才有拼音搜索」。
- 优点：全服共用一份、行为统一、接入方 jar 不变大。
- 必须配一句：「**没装本库时你的模组行为完全不变**」——这是别人敢接的前提。
- ⚠️ 这条回退**只能由调用方自己写**：库没装时连 `PinyinSearch` / `Matcher` 都加载不了，
  `Matcher.literal` 帮不上忙（§4 接口注释已说明）。
- ⚠️ **软依赖的前提是"延迟解析"**：上面那段之所以安全，是因为 `PinyinSearch` 只在**真正进入分支后**
  才会被解析。**不要把 `PinyinSearch` 写进静态字段/常量的类型、也不要继承/实现它** ——
  那会在类加载阶段就抛 `NoClassDefFoundError`，连"没装就回退"的机会都没有。
  更稳的做法是把调用封进一个独立的小类（如 `PinyinSearchBridge`），只在分支内引用它。

### 7.2 方式二：硬依赖 + jarJar（玩家无感）

**依赖方 `build.gradle`：**

```gradle
repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
    // 编译期 API
    compileOnly 'com.github.2779789119:pinyinsearch:1.0.0'
    // 嵌进自己的 jar，玩家无需另装
    jarJar 'com.github.2779789119:pinyinsearch:1.0.0'
}

jarJar.enable()          // ← ForgeGradle 6 的正确调用方式需实测确认
```

**依赖方 `mods.toml`：**

```toml
[[dependencies.yourmod]]
modId = "pinyin_search"
mandatory = false          # 已被 jarJar 嵌入，这里只声明版本关系
versionRange = "[1.0.0,2.0.0)"
ordering = "NONE"
side = "BOTH"
```

- 优点：玩家不用额外装东西，开箱即用。
- 代价：每个依赖方各带一份 ~200KB。
- **首次接入时的验收清单**（下面几项一次验完，它们是同一件事的四个面，不是四件工作）：
  1. 只装内嵌本库的那一个模组 → 拼音搜索可用；
  2. 两个都内嵌本库的模组同时加载 → 能正常启动（Forge 的 JarJar 是否按坐标去重，**实测确认**）；
  3. 玩家**既单独装本库、又装了内嵌本库的模组** → 是否报重复 modId（**实测确认**；
     README 视结果写清「二选一」）；
  4. 与 JECh 同时装 → 无类冲突。
- 本库**无状态**（`Profile` 不可变、无全局注册表，§4.2），因此即使真的加载了两份也不会互相污染
  —— 这既是「无状态」约束的另一个理由，也意味着上面第 2、3 项**最坏只是多占一份内存**。

### 7.3 提供 Maven 坐标（否则没人接）

JitPack 的标准格式是 **`com.github.User:Repo:Tag`**（artifactId 默认就是仓库名，不要写成 `com.github.User.Repo`）：

```gradle
repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
    // ✅ 正确：com.github.<你的用户名>:<仓库名>:<tag>
    compileOnly 'com.github.2779789119:pinyinsearch:1.0.0'

    // ❌ 错误（初稿写错过的形式，会让别人复制粘贴后直接编译失败）：
    // compileOnly 'com.github.2779789119.pinyinsearch:pinyin_search:1.0.0'
}
```

- 最省事：JitPack（推 GitHub tag 即用，配合 §5.5 的 CI）。
- README 里必须有：**一行依赖 + 三行调用**的完整示例。
- 仓库名以实际为准：本仓库为 **`pinyinsearch`**（JitPack 用仓库名作 artifactId，**区分大小写**）。

### 7.4 样例：首次接入（无限加点）

现有实现是 `com.infinitestats.compat.JechCompat`（反射 JECh 内部类 + 回退 `contains`）。
本库可用后：

```java
// 替换 JechCompat.matches(input, query)
if (ModList.get().isLoaded("pinyin_search")) {
    return PinyinSearch.matches(input, query);
}
return input.contains(query);
```

好处：不依赖任何模组的内部类，行为固定、可预期。本库只提供这条路径，
**是否采用、由谁采用完全由调用方自己决定**，本项目不做任何推动。

### 7.5 需要上模糊音 / 双拼的调用方

```java
// 放宽匹配（z/zh 不分等）—— 会让误命中变多，慎用
Profile fuzzy = PinyinSearch.profile().allFuzzy(true).build();
PinyinSearch.matches(text, query, fuzzy);

// 使用者主动选了双拼输入法
Profile shuangpin = PinyinSearch.profile()
        .scheme(Profile.Scheme.SHUANGPIN_XIAOHE)
        .build();
Matcher m = PinyinSearch.matcher(pool, shuangpin);

// 大 pool 且已确认索引引擎与即时匹配等价时，才显式换引擎
Profile big = PinyinSearch.profile().engine(Profile.Engine.TREE).build();
```

---

## 8. 测试计划

### 8.1 自动化用例（做成纯 JUnit 表驱动，见 §5.5）

**默认 Profile**：

| 文本 | 查询 | 期望 | 覆盖点 |
|---|---|---|---|
| 铁砧 | `tiezhen` | ✓ | 全拼 |
| 铁砧 | `tz` | ✓ | 声母 |
| 铁砧 | `tiezh` | ✓ | 简拼 |
| 铁砧 | `zuanjian` | ✗ | 不误命中 |
| 钻石剑 | `zuanshijian` | ✓ | 全拼 |
| 钻石剑 | `zsj` | ✓ | 声母 |
| 钻石剑 | `钻石` | ✓ | 原文包含 |
| 钻石剑 | `Diamond Sword` | ✗ | 不误命中 |
| 中国 | `zhongguo` / `中guo` / `zhong国` / `zh国` | ✓ | 任意组合（PinIn README 示例） |
| 中国 | `zhong1国` / `zh1国` | ✓ | 声调 |
| 中国 | `zong国` / `z国` | ✗ | **默认模糊音全关** |
| Ｄｉａｍｏｎｄ（全角） | `diamond` | ✓ | 全角归一化 |
| 铁砧 | `""` / `"  "` | ✓ | 空查询 |
| 铁砧 | `null`（文本或查询） | ✓ | null 语义（§4.1） |
| 铁砧 | `!!!` | ✗（不抛异常） | 异常/无匹配退化 |

**自定义 Profile**：

| Profile | 文本 | 查询 | 期望 | 覆盖点 |
|---|---|---|---|---|
| `allFuzzy(true)` | 中国 | `zong国` / `z国` | ✓ | 模糊音开启后与默认相反 |
| `allFuzzy(true)` | 中国 | `zhongguo` | ✓ | 开模糊音不改既有命中 |
| `SHUANGPIN_*` | 待定 | 双拼按键串 | **待实测** | 双拼（PinIn 自述实验性） |
| `ZHUYIN_DACHEN` | 待定 | 注音串 | **待实测** | 注音 |
| `accelerate(true)` vs `(false)` | 任意 | 任意 | **结果必须完全一致** | 加速模式只影响性能，不影响语义 |

**★ 硬性验收：`Matcher` 与 `matches()` 逐条等价（§4.1）**

对每个被验的引擎、同一 `pool`、同一 `query`、同一 `Profile`：

```java
// pool 需为稳定有序列表（与 matcher 的入参类型一致）
List<String> list = List.copyOf(pool);
List<Integer> expected = IntStream.range(0, list.size())
        .filter(i -> PinyinSearch.matches(list.get(i), query, profile))
        .boxed()
        .toList();
List<Integer> actual = PinyinSearch.matcher(list, profile).searchIndices(query);
assertEquals(expected, actual, "引擎 " + profile.engine() + " 与逐条 matches 不等价");
```

- `Engine.LOOP`（默认）：**必须通过**（等价性由构造保证）。
- `Engine.SIMPLE / TREE / CACHED`：**逐个引擎单独验**；任何不一致都必须记录到 §4.3，
  并把该引擎从默认候选中排除。重点盯：声调（数字）、模糊音开启时、双拼解析。

> ⚠️ **待实测，不要预先写死期望值**：
> - `Diamond Sword` → `DS`：英文词首字母**不是 PinIn 的能力**，预期 ✗。第一期不支持，别写进断言。
> - **英文子串匹配**：`Diamond Sword` → `diamond`（大小写不敏感）逻辑上应命中，
>   但 **PinIn 是否干扰纯拉丁文本的匹配路径未验证**。实测通过后再把它转成正式断言用例（§4.3）。
> - 多音字：`重庆` → `chongqing` / `zhongqing` 分别是什么结果，实测后写进 README 的「已知限制」。
> - 简繁互搜：`钻石剑` 用繁体字能否搜到，实测后写进 README，并据此修正 §1.1 的「能力对齐」表述。
> - **纯数字 query**：文本「铁砧」+ 查询 `1` / `3` 是什么行为；查询 `tie1` 是否被当作「tie + 声调 1」。
>   若发现数字被误当声调，考虑加归一化规则：**query 全为数字时直接走原文包含**。

### 8.2 手动 / 集成测试

1. **单独加载**：进游戏、开一个带搜索框的测试界面，拼音可搜、无 crash。
2. **与 JECh 同时加载**：确认无 duplicate class / 无 crash；两边的搜索框都还能用。
3. **被 jarJar 嵌入**：在无限加点里 jarJar 本库，单独测试「只装无限加点」的整合包能否拼音搜索
   （完整验收清单见 §7.2）。
4. **引擎实例缓存与 Profile 隔离**：用同一个 `Profile` **连续调用 `matches()` 与 `matcher()`
   （两条路径都要覆盖）**，确认 **`PinIn` 只被构造一次**（打印构造日志 / 打点计数）；
   换一个新 `Profile` 才重新构造。⚠️ 只测 `matcher()` 会漏掉 `matches()` 这条路径，它同样走引擎缓存。
   另用两个不同 Profile 各建一个 `Matcher`，交叉搜索结果互不影响（防「共享实例 + 就地 commit」式串扰）。
5. **性能（把实测值回填 §4.4）**：对 1 万条文本分别测 `LOOP` 与 `SIMPLE / TREE / CACHED` 的构建与搜索耗时；
   构建后连续切 query（含单字符）确认 `search(query)` 无卡顿；对比「逐条 `matches()`」在 1 万条下的全表耗时，
   并在 `accelerate(true/false)` 下各测一次，据此决定 README 里推荐哪种用法。
6. **fastutil 冒烟**：按 §3.3 第 4 点，启动 + 完整搜索一次，专门盯 `NoSuchMethodError` / `NoClassDefFoundError`。

---

## 9. 路线图

| 版本 | 内容 |
|---|---|
| **v1.0** | 1.20.1 Forge；§4 的 API（含 `Profile`：全拼 / 双拼 / 注音 / 7 种模糊音 / 加速 / 引擎）；vendored PinIn（**全量，不裁剪**）；MIT + PinIn 声明；JitPack 坐标 + CI；中英 README（含能力矩阵与 §4.3 限制） |
| v1.1 | `Profile.readingOverride(text, pinyin)` 处理多音字（**词级**方案，见 §4.3）；`filter(...)` 便捷方法；可选返回匹配区间（供高亮） |
| v1.2 | debug 开关 / 性能统计（仅日志，不改变语义）；按实测结果决定索引引擎能否成为默认 |
| v2.0（可选） | NeoForge / Fabric 多平台（Architectury） |
| **永不做** | GUI 注入、JEI/REI 接管、IME、字形辅助码 |

---

## 10. 风险清单

| 风险 | 影响 | 对策 |
|---|---|---|
| **限制没写清，使用者踩坑后才发现** | 调用方上线后才撞上多音字 / 字形码 / 英文首字母这些边界 | README 把 §4.3 的限制放在显眼位置；v1.1 提供 `readingOverride` |
| **索引引擎与即时匹配语义不一致** | 「逐条能搜到、建索引后搜不到」的诡异行为 | 默认 `Engine.LOOP` 规避；索引引擎须过 §8.1 等价性验收 |
| **`PinIn` 实例被反复构造 / 缓存无上限** | 反复解析字典 → 严重卡顿；或每个新 `Profile` 常驻 ~10MB | 按 `Profile` 缓存（`equals`/`hashCode` 必须正确）+ **缓存容量上限** + 文档要求复用 `Profile`（§3.1、§8.2 第 4 点） |
| **某处引入可变全局状态** | 破坏 jarJar 多份嵌入的安全性，多模组互相污染 | §4.2「无状态」约束；`readingOverride` 已从静态方法改为 `Profile` 配置项 |
| **默认 Profile 选错** | 误命中（模糊音）或行为突变（双拼/注音）引发困惑 | 默认只开「纯增益」项（声母/简拼/声调/加速），模糊音与输入法默认关（§4.1.1） |
| **能力多 = 测试面大** | 双拼/注音路径缺乏真实样本，容易藏 bug | 双拼/注音标注**实验性**；未验证项写「待实测」而非写死期望 |
| **vendored PinIn 处理不当** | 裁剪引发运行期 `NoClassDefFoundError` / 反射失败；或上游更新后难以合并 | v1.0 **全量 vendor**、只改包名与资源路径、改动处加注释（§5.3） |
| fastutil 版本不一致 | 编译过、运行期 `NoSuchMethodError` | **版本实测比对**（§3.3），主版本不同就 relocate 一份；跑运行期冒烟 |
| 与 JECh 撞类 | 玄学 bug，极难排查 | **relocate（硬要求，§3.2）** |
| 漏掉 MIT 声明 | 合规问题 | jar 内保留 `LICENSE-Pinin.txt` + README 致谢（§6） |
| 范围膨胀（有人要求「顺便支持 JEI」） | 变成第二个 JECh | 用 §1.2 的非目标清单挡回去 |

---

## 附录 A：PinIn 关键类（速查，**以源码为准**）

| 类 | 作用 |
|---|---|
| `PinIn` | 主入口。`contains(s1, s2)` / `begins` / `matches`；`config()` 返回**非静态内部类** `Config`，`commit()` 写回**本实例**；`ticket(Runnable)` + `Ticket.renew()` 为本实例的配置变更通知。**构造即解析整份字典，是重操作** |
| `PinIn.Config` | `keyboard` / 7 个模糊音布尔 / `accelerate` / `format`（全部为实例级） |
| `Keyboard` | 全拼 / 双拼（自然码、小鹤）/ 注音（大千）—— **常量名以源码为准** |
| `PinyinFormat` | `NUMBER` / `UNICODE` / `PHONETIC`（注音符号）等 |
| `Searcher` / `TreeSearcher` / `SimpleSearcher` / `CachedSearcher` | 索引匹配（对应 `Profile.Engine`） |
| `DictLoader`（`DictLoader.Default`） | 从 classpath 读 `data.txt`；**字级**（codepoint → 读音），**改包名后必须同步改资源路径** |
| `Char` / `Pinyin` / `Phoneme` / `Element` / `IndexSet` | 字 / 拼音 / 音素 / 匹配元素 |

> 本表来自 PinIn 源码与 README 的实际阅读，但仍**请在实现时以源码为准**；不要凭记忆写方法签名。

## 附录 B：验收清单（发版前逐条打勾）

**打包与合规**

- [ ] 包名已 relocate，jar 内**不存在** `me/towdium/pinin`
- [ ] jar 内存在 `LICENSE` 与 `LICENSE-Pinin.txt`；`mods.toml` 的 `license` 在 header 部分（不在 `[[mods]]` 里）
- [ ] vendored PinIn 为**全量**（未裁剪类）
- [ ] PinIn 与 MC 的 fastutil 版本已比对并记录，运行期冒烟通过
- [ ] 与 JECh 同时加载无 crash

**API 形态**

- [ ] `api` 包下所有公开类型与方法都有完整 JavaDoc（含「不抛异常」「主线程」「null 行为」）
- [ ] `api` 包里**不存在**任何 v1.0 未提供的方法（尤其 `readingOverride`：既无实现，也无 no-op 占位）
- [ ] **`matcher` / `Matcher.literal` 的入参类型是 `List<String>`**（不是 `Collection`），
      且 JavaDoc 已写明「下标只对稳定有序列表有意义」

**语义与性能**

- [ ] `matches()` 对空查询返回 `true`；**任一参数为 `null` 也返回 `true` 且不抛 `NullPointerException`**（§4.1）
- [ ] 对任意异常不抛（内部一律退化为原文包含）
- [ ] **`Matcher` 与 `matches()` 逐条等价**已按 §8.1 验收（`LOOP` 必过；其它引擎逐个验并记录结论）
- [ ] 默认 Profile 的 §8.1 用例全部通过；模糊音用例「默认关」与「开关后」两个方向都验过
- [ ] `accelerate(true)` 与 `(false)` 的匹配结果完全一致
- [ ] **`matches()` 与 `matcher()` 两条路径都只构造一次 `PinIn`**（同一 `Profile`）；
      两个不同 `Profile` 的 `Matcher` 交叉使用互不干扰
- [ ] 引擎缓存有**容量上限**；README 已写明「`Profile` 是配置描述，请构造少数几个并长期复用」
- [ ] `LOOP` 与各索引引擎在 1 万条下的耗时已实测并回填 §4.4

**文档与发布**

- [ ] §8.1 中「待实测」项（英文首字母 / **英文子串大小写不敏感** / 多音字 / 简繁互搜 / 双拼 / 注音 /
      **纯数字 query**）已实测并写入 README
- [ ] README 中英双语，含「一行依赖 + 三行调用」示例、能力矩阵、「没装则行为不变」声明、以及 §4.3 的已知限制
- [ ] CI 已配置构建 + 测试，`v*` tag 能触发发布且 JitPack 坐标可用
