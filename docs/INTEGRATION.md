# 接入模板 · 用 API 给你的模组加上拼音搜索

> 面向**依赖方模组作者**。照抄本文就能完成接入，不需要读实现、不需要装任何前置。
> 本模组只提供 API，**不注入 GUI、不接管 JEI/REI** —— 搜索框是你自己的，匹配逻辑由 API 提供。

**核心结论：一个搜索框只需要 3 个调用。**

| 搜索框需要做什么 | 调哪个 API |
|---|---|
| 逐条判断「这条要不要显示」 | `PinyinSearch.matches(text, query)` |
| 大数据集反复搜不同的 query | `PinyinSearch.matcher(pool).search(query)` |
| 想关掉拼音（走纯原文包含，例如玩家开关） | `Matcher.literal(pool)` |

---

## 0. 三步接入

1. **加一行依赖**（软依赖 / jarJar 二选一）
2. **写一个 Bridge 小类**（防 `NoClassDefFoundError`，见 §2，全文可直接照抄）
3. **把搜索框里的 `contains` 换成 Bridge 的调用**（见 §3）

---

## 1. 加依赖

### 方式 A：软依赖（先做这个）

玩家 / 整合包自己装本模组；没装时你的模组行为**完全不变**。

```gradle
repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
    // 只编译期需要，运行期靠玩家安装；你的 jar 不会变大
    compileOnly 'com.github.2779789119:pinyinsearch:1.1.1'
}
```

### 方式 B：硬依赖 + jarJar（玩家无感）

玩家不用额外装东西，代价是每个依赖方各带一份 ~204KB。

```gradle
dependencies {
    compileOnly 'com.github.2779789119:pinyinsearch:1.1.1'   // 编译期 API
    jarJar      'com.github.2779789119:pinyinsearch:1.1.1'   // 嵌进自己的 jar
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

> 想给 IDE 悬停文档，再加两条（可选）：
> `compileOnly 'com.github.2779789119:pinyinsearch:1.1.1:javadoc'` 与 `:sources`。

---

## 2. Bridge 小类（★ 这一步不能省，直接照抄改包名即可）

**为什么必须单独一个类**：软依赖的前提是「**延迟解析**」。只要把库的类型写进
静态字段 / 常量类型 / 继承关系 / 接口实现，类加载阶段就会抛 `NoClassDefFoundError`，
连「没装就回退」的机会都没有。所以**所有对库的直接引用都必须关在方法体里**，
需要长期持有的实现用 `Object` 装、在方法体内转型。

> 下面这份代码在本仓库里有一份**可编译副本**：
> `src/test/java/com/pinyinsearch/examples/PinyinSearchBridge.java`，
> 构建时会一起编译 —— 万一将来 API 改坏了这份示例，`gradlew build` 会立刻报错，
> 而不是等你复制粘贴时才发现。

```java
package com.example.yourmod.compat;

import net.minecraftforge.fml.ModList;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 拼音搜索桥接：装了 pinyin_search 就走拼音匹配，没装则完全退化为纯原文包含。
 *
 * ★ 关键约定：库的类型（PinyinSearch / Matcher / Profile）只出现在方法体里，
 *   绝不写进字段类型、方法签名、继承或实现 —— 否则类加载阶段就会 NoClassDefFoundError。
 */
public final class PinyinSearchBridge {

    public static final String MOD_ID = "pinyin_search";

    /** 字段类型是 boolean，不是库里的类型，加载安全。 */
    private static final boolean AVAILABLE = ModList.get().isLoaded(MOD_ID);

    private PinyinSearchBridge() {
    }

    public static boolean isAvailable() {
        return AVAILABLE;
    }

    /** 单对单匹配：小列表（几十~几百条）逐条判断用这个。 */
    public static boolean matches(String text, String query) {
        if (AVAILABLE) {
            return com.pinyinsearch.api.PinyinSearch.matches(text, query);
        }
        return rawContains(text, query);
    }

    /**
     * 大数据集：建一次索引，之后用不同 query 反复搜。
     *
     * @param pool 文本池，必须是稳定有序的 List（index 返回的下标语义依赖迭代顺序）
     */
    public static Index index(List<String> pool) {
        return new Index(pool, createImpl(pool, true));
    }

    /**
     * 建索引，并显式指定是否启用拼音（装了库但玩家关掉拼音开关时传 false）。
     *
     * @param pool          文本池
     * @param pinyinEnabled 是否启用拼音
     */
    public static Index index(List<String> pool, boolean pinyinEnabled) {
        return new Index(pool, createImpl(pool, pinyinEnabled));
    }

    /**
     * 实现对象。返回类型故意写成 Object：
     * 库没装时返回 null，调用方走纯原文分支，绝不触碰库的类型。
     */
    private static Object createImpl(List<String> pool, boolean pinyinEnabled) {
        if (!AVAILABLE) {
            return null;
        }
        // 装了库：Matcher.literal 是「关掉拼音但仍按同一套归一化规则做原文包含」
        return pinyinEnabled
                ? com.pinyinsearch.api.PinyinSearch.matcher(pool)
                : com.pinyinsearch.api.Matcher.literal(pool);
    }

    private static boolean rawContains(String text, String query) {
        if (text == null || query == null) {
            return true;
        }
        String q = query.trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) {
            return true;
        }
        return text.toLowerCase(Locale.ROOT).contains(q);
    }

    /**
     * 索引句柄：对外只暴露 List<Integer> / List<String>，库的类型被封装在内部。
     * 本类可安全加载（库没装时 impl == null）。
     */
    public static final class Index {
        private final List<String> pool;
        private final Object impl;   // 实际类型是 Matcher；库没装时为 null

        private Index(List<String> pool, Object impl) {
            this.pool = pool == null ? List.of() : new ArrayList<>(pool);
            this.impl = impl;
        }

        /** 命中下标，升序；query 为 null / 空 / 全空白 → 返回全部下标。 */
        public List<Integer> indices(String query) {
            if (impl != null) {
                return ((com.pinyinsearch.api.Matcher) impl).searchIndices(query);
            }
            List<Integer> ret = new ArrayList<>();
            for (int i = 0; i < pool.size(); i++) {
                if (rawContains(pool.get(i), query)) {
                    ret.add(i);
                }
            }
            return ret;
        }

        /** 命中的文本，保留 pool 顺序与重复项。 */
        public List<String> search(String query) {
            if (impl != null) {
                return ((com.pinyinsearch.api.Matcher) impl).search(query);
            }
            List<String> ret = new ArrayList<>();
            for (Integer i : indices(query)) {
                ret.add(pool.get(i));
            }
            return ret;
        }
    }
}
```

> 观察最后两处的 `(Matcher) impl`：类型转换指令只在 `impl != null`（即库存在）时才会执行，
> 所以库没装时这段代码永远不会被解析 —— 这正是「延迟解析」的实现方式。

---

## 3. 把搜索框接上

### 3.1 形态一：小列表逐条判断（改动最小）

```java
// 改之前
private boolean matchesQuery(String name, String query) {
    return name.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT));
}

// 改之后：一行换掉
private boolean matchesQuery(String name, String query) {
    return PinyinSearchBridge.matches(name, query);
}
```

收益：`zsj` / `tiezh` / `zhong1国` 这些输入立刻能搜到，玩家不需要装 JECh。

### 3.2 形态二：大列表建一次索引（推荐给物品列表 / 配方列表）

```java
private PinyinSearchBridge.Index index;            // 类型是你自己的 Bridge，不是库里的类型

public void onListChanged(List<String> allNames) { // 打开界面 / 列表变动时重建
    this.index = PinyinSearchBridge.index(allNames);
}

private void refresh(String query) {
    List<String> visible = index.search(query);    // 保留 pool 顺序与重复项
    // 用 visible 渲染列表
}
```

要点：

- `allNames` 必须是**稳定有序的 `List<String>`**（`ArrayList` 之类）。传 `HashSet` 时返回的下标无意义。
- 默认引擎 `LOOP`（逐条匹配），**创建成本极低**，可以在打开界面时建。
  只有当「已自行验收等价性」时才显式换 `Engine.TREE` 等索引引擎，且**不要**在渲染 / 输入回调里建（索引是同步构建的）。
- `Matcher` **只能在客户端主线程用**。索引构建完成后是只读的，所以「异步构建 → 交回主线程使用」安全。
- 玩家自己关掉拼音开关时，用 `PinyinSearchBridge.index(allNames, false)`（走 `Matcher.literal`）。

### 3.3 形态三：给玩家一个「模糊音」开关

```java
// ★ 在初始化时构造一次并长期复用；不要在每次搜索时 build()
private static final Profile FUZZY = ...;   // ← 库类型不能作为字段！见下方说明
```

⚠️ `Profile` 是库里的类型，**不能**存进你的静态字段。把它也交给 Bridge 保管：

```java
// 在 PinyinSearchBridge 内：
private static Object fuzzyProfile;   // 类型是 Object，加载安全

private static Object fuzzy() {
    if (fuzzyProfile == null) {
        fuzzyProfile = com.pinyinsearch.api.PinyinSearch.profile()
                .allFuzzy(true).build();          // ★ 只建这一次，长期复用
    }
    return fuzzyProfile;
}

public static boolean matchesFuzzy(String text, String query) {
    if (AVAILABLE) {
        return com.pinyinsearch.api.PinyinSearch
                .matches(text, query, (com.pinyinsearch.api.Profile) fuzzy());
    }
    return rawContains(text, query);
}
```

> 为什么非要这样：每个不同的 `Profile` 都会让库常驻一份拼音引擎（单实例可达 MB 级）。
> 在热路径上反复 `build()` 是**性能事故**。建少数几个、放进 Bridge 的静态字段里长期复用。

---

## 4. 一个完整例子：把 `JechCompat` 换成这个 API

原来（反射 JECh 内部类 + 回退 `contains`）：

```java
if (JechCompat.isLoaded()) {
    return JechCompat.matches(input, query);
}
return input.contains(query);
```

改后（不依赖任何模组的内部类，行为固定、可预期）：

```java
if (PinyinSearchBridge.isAvailable()) {
    return PinyinSearchBridge.matches(input, query);
}
return input.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT));
```

好处：不反射、不等 JECh 收录、不与 JECh 撞类（本模组内置的 PinIn 已重定位）。

---

## 5. 六个必须知道的坑

| 坑 | 说明 |
|---|---|
| 把库类型写进字段 / 继承 / 方法签名 | 类加载阶段直接 `NoClassDefFoundError`，「没装就回退」失效。**引用必须关在方法体里**（需要长期持有就用 `Object`） |
| 忘记写回退 | 软依赖时必须自己写「没装则行为不变」。`Matcher.literal` **不能**用于这个回退（那时连 `Matcher` 类都加载不了），它只用于「装了库但要关掉拼音」 |
| 热路径上反复 `build()` `Profile` | 每个不同的 `Profile` 都常驻一份引擎（可达 MB 级）。**建少数几个，长期复用** |
| `pool` 传了无序集合 | 下标语义失效。必须 `List<String>` |
| 在渲染 / 输入回调里建索引 | `SIMPLE / TREE / CACHED` 是**同步构建**的，大 pool 会卡帧 |
| 跨线程用 `Matcher` | 必须在客户端主线程；只有「构建后只读」的用法可以异步构建 |

**期望管理（也写进你自己的说明里）**：`Diamond Sword` 搜不到 `DS`；简繁**不互搜**；
上下文多音字（词级读音）本 API v1.0 不提供。完整清单见 [README §5](../README.md#5-已知限制实测结论)。

---

## 6. 接入验收清单

- [ ] 只装你的模组（软依赖：**不装** `pinyin_search`）→ 行为与接入前**完全一致**，无 crash
- [ ] 只装你的模组 + `pinyin_search` → `zsj` 能搜到「钻石剑」
- [ ] jarJar 场景：只装内嵌本库的那一个模组 → 拼音搜索可用（玩家无需另装）
- [ ] 两个都内嵌本库的模组同时加载 → 能正常启动
- [ ] 玩家既单独装本库、又装内嵌本库的模组 → 确认是否报重复 modId（视结果在说明里写清「二选一」）
- [ ] 与 JustEnoughCharacters 同时装 → 无 duplicate class、无 crash
- [ ] 检查代码里**没有**把 `PinyinSearch` / `Matcher` / `Profile` 写进字段类型或继承关系

---

## 7. 延伸阅读

- 完整方法语义（参数 / 返回 / null / 线程 / 异常）：[`docs/API.md`](API.md)
- 能力矩阵、实测结论、性能与内存账：[`README.md`](../README.md)
- IDE 悬停文档：JitPack 的 `-javadoc` 产物；离线 HTML：[`docs/apidocs/index.html`](apidocs/index.html)
