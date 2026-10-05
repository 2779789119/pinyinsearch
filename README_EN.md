# Pinyin Search API

**A pinyin search library *for mod developers*.** One dependency line, three lines of code.
No GUI injection, no JEI/REI takeover, no player-facing UI — it is a *capability provider*,
not a replacement for JustEnoughCharacters.

**License: MIT** (this project) · bundled PinIn keeps its own MIT notice.

| | |
|---|---|
| modid | `pinyin_search` |
| Loader / MC | Forge 47.x / 1.20.1 |
| Java package | `com.pinyinsearch` |
| Dependencies | **none** |
| Size | ~204 KB (pinyin table included, 302 KB compressed) |

[中文说明](README.md)

---

## 📖 Documentation

| What you want | Where |
|---|---|
| **Integrate it into your mod** (dependency + bridge class + search-box recipes, copy-paste ready) | [`docs/INTEGRATION.md`](docs/INTEGRATION.md) (Chinese) |
| **Read the API end to end** (parameters / return values / null / thread / exception semantics + snippets) | [`docs/API.md`](docs/API.md) (Chinese) |
| **Offline HTML** (double-click `index.html`, or zip the folder and hand it to someone) | [`docs/apidocs/index.html`](docs/apidocs/index.html) |
| **Online HTML** | `https://2779789119.github.io/pinyinsearch/` (published by CI once Pages is enabled) |
| Hover documentation inside your IDE | JitPack `:javadoc` / `:sources` artifacts (see §7) |

---

## 1. One dependency line, three lines of code

```gradle
repositories { maven { url 'https://jitpack.io' } }

dependencies {
    // soft dependency: compile-time only, players/packs provide it at runtime
    compileOnly 'com.github.2779789119:pinyinsearch:1.1.3'
}
```

```java
if (ModList.get().isLoaded("pinyin_search")) {
    return PinyinSearch.matches(text, query);
}
// Not installed: behaviour is completely unchanged
return text.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT));
```

**Without this library your mod behaves exactly as before.** That is what makes it safe to adopt.

> ⚠️ The snippet above is only safe because `PinyinSearch` is resolved *lazily*, inside the branch.
> **Never** put `PinyinSearch` in the type of a static field/constant and never extend/implement it —
> that would throw `NoClassDefFoundError` during class loading, leaving no chance to fall back.
> For extra safety, wrap the call in a tiny bridge class, see [`docs/INTEGRATION.md`](docs/INTEGRATION.md) §2.

### Common API

```java
PinyinSearch.matches(text, query);               // single pair (small lists, no index needed)
PinyinSearch.matches(text, query, fuzzyProfile); // relaxed matching (fuzzy sounds → more false positives)

Matcher m = PinyinSearch.matcher(pool);          // large data set: build once, query many times
List<Integer> idx = m.searchIndices("zsj");      // ascending indices
List<String>  hit = m.search("zsj");             // matched strings, pool order + duplicates kept

PinyinSearch.normalize(raw);                     // lower-case + full-width→half-width + strip
```

Full signatures and examples → [`docs/API.md`](docs/API.md)

---

## 2. Capability matrix

**Every capability comes from the bundled PinIn; this library only exposes it and picks defaults.
It invents no pinyin logic.**

| Capability | Supported | How / default | Example |
|---|---|---|---|
| Literal substring | ✅ always on | built into the matcher | `钻石` → 钻石剑 |
| Full pinyin | ✅ default | `Scheme.QUANPIN` | `zuanshijian` → 钻石剑 |
| Initials / abbreviations / any mix | ✅ always on | recursive matcher | `zsj`, `zuanjian`, `tiezh` |
| Tones (optional) | ✅ always on | `PinyinFormat.NUMBER` | `zhong1国`, `zhen1` |
| Shuangpin (Ziranma / Xiaohe) | ✅ **experimental**, off | `Scheme.SHUANGPIN_*` | 中国 = `vsgo` |
| Zhuyin (Daqian) | ✅ **experimental**, off | `Scheme.ZHUYIN_DACHEN` | 中国 = `5j/eji` |
| Fuzzy sounds ×7 | ✅ all off | `Builder.fuzzyXxx(...)` ×7, `allFuzzy(b)` | with `fuzzyZhZ`, `zong国` matches 中国 |
| Simplified ↔ Traditional | ⚠️ **no cross-match** | follows the dictionary | traditional text itself is searchable by pinyin |
| Immediate / indexed matching | ✅ | `matches(...)` / `Profile.Engine` (default `LOOP`) | — |
| `accelerate` | ✅ on | `Profile.accelerate()` | — |
| Glyph/auxiliary codes | ❌ never | — | PinIn does not support them |
| Word-level polyphone override | ❌ not in v1.0 | `Profile.readingOverride` planned for v1.1 | see [`docs/API.md`](docs/API.md) §7 |
| English substring (case-insensitive) | ✅ | literal path | `diamond` → `Diamond Sword` |
| English initials | ❌ | — | `DS` does **not** match `Diamond Sword` |

**Why these defaults:** initials/abbreviations/tones are pure gains (no extra false positives), so they are on;
fuzzy sounds *widen* matching and shuangpin/zhuyin *change how the query is parsed*, so they are off and must be
opted into; `accelerate` is on because the main use case is one query × many texts; the default engine is `LOOP`,
so "found by brute force but not by the index" can never happen on the default path.

---

## 3. Performance (two sentences to remember)

- **Small lists**: call `PinyinSearch.matches(...)` directly — microseconds per call, recomputing on every keystroke is fine.
- **Large lists**: `PinyinSearch.matcher(pool)` + the default `LOOP` (no real index) is the **safe default**; 10k entries cost about 1/4 of "brute-force pinyin matching".
- **Index engines** (`SIMPLE / TREE / CACHED`): build **synchronously** when you create the `Matcher` — use them for large pools with verified equivalence, and **never inside a render or input callback**.

> ⚠️ **A `Profile` is a description, not a disposable object.**
> Constructing `PinIn` parses the whole dictionary (`TreeSearcher` is about **9.5 MB** per instance).
> Engines are cached per `Profile` (capacity 16, LRU eviction). Build a *few* profiles at init and reuse them —
> do **not** call `build()` on a hot path.

PinIn's own benchmark (37k entries / ~400k chars / ~900KB sample) and per-path costs → [`docs/API.md`](docs/API.md)

---

## 4. The five easiest ways to get bitten

1. `text` / `query` being `null` → `matches` returns `true` (treated as an empty query); no need to null-check.
2. Any internal error is captured and degrades to literal `contains` — it **never throws**.
3. `Matcher` takes a `List<String>` (not a `Collection`) — returned **indices** only make sense on a stable ordered list.
4. Threads: **client main thread only**; a built index is read-only, so "build async → use on the main thread" is safe.
5. Simplified ↔ Traditional **do not cross-match**: `钻石剑` does not find `鑽石劍` (but traditional text itself is pinyin-searchable).

Other measured findings (polyphones, tone position, pure numeric queries, English substrings, index-engine
equivalence…) → [`docs/API.md`](docs/API.md) §7; the regression assertions live in `KnownBehaviorTest`.

---

## 5. Coexistence with JustEnoughCharacters (JECh)

| Aspect | Verdict |
|---|---|
| Class conflict | **None.** PinIn is relocated to `com.pinyinsearch.shaded.pinin`; the jar contains **no** `me/towdium/pinin/**` |
| Behaviour | **May differ**: JECh's own config (fuzzy sounds, scheme) is independent from this library's `Profile`, so the same text can match differently in two search boxes |
| Reads JECh config? | **No.** Reading it would couple this library to someone else's internals |
| Positioning | JECh patches *other* mods via coremod call sites; this library serves mods that *choose* to integrate. Opposite approaches, so they do not compete |

---

## 6. Bundled PinIn (compliance)

- Upstream [Towdium/PinIn](https://github.com/Towdium/PinIn) (MIT), version **1.6.0** (default branch `master`).
- All **16 source files vendored, nothing stripped** (PinIn may use reflection / dynamic loading; trimming could
  compile fine and only explode at runtime).
- **The only modification is the package relocation**: `me.towdium.pinin` → `com.pinyinsearch.shaded.pinin`, with the
  pinyin table resource moved to `com/pinyinsearch/shaded/pinin/data.txt` (`DictLoader.Default` uses
  `PinIn.class.getResourceAsStream("data.txt")`, so the package-relative path follows automatically).
  Every file carries a header comment describing the change, to ease future diffs against upstream.
- **fastutil**: upstream declares `8.3.0`, Minecraft 1.20.1 ships **8.5.9** (same major) → Minecraft's fastutil is
  reused and **nothing extra is bundled**.
- Compliance: `LICENSE` (MIT) and `LICENSE-Pinin.txt` (PinIn's original MIT text) ship at the repo root *and* inside the jar.
- **All pinyin logic comes from PinIn; this library invents none of it.**

---

## 7. Integration and coordinates

- **Soft dependency** (see §1): install once for the whole pack, consistent behaviour, your jar stays small.
- **Hard dependency + jarJar** (transparent to players): embed it in your own jar; costs ~204KB per embedding mod.

```gradle
compileOnly 'com.github.2779789119:pinyinsearch:1.1.3'            // ✅
compileOnly 'com.github.2779789119:pinyinsearch:1.1.3:javadoc'    // Chinese JavaDoc on hover
compileOnly 'com.github.2779789119:pinyinsearch:1.1.3:sources'
// compileOnly 'com.github.2779789119.pinyinsearch:pinyin_search:1.1.2'  // ❌ do not write com.github.User.Repo
```

Coordinates follow JitPack's `com.github.User:Repo:Tag` — the artifactId is the repository name, which is
**case-sensitive**: always write the lowercase `pinyinsearch`.

**Verify these four things once, when you first integrate:** (1) a pack with only your embedding mod works;
(2) two mods both embedding the library start fine; (3) a player installing both the standalone library *and* an
embedding mod (duplicate modId?); (4) loading together with JECh causes no class conflict.
The library is **stateless**, so even two copies cannot corrupt each other — worst case is one extra copy in memory.

Full integration template (bridge class + three search-box recipes) → [`docs/INTEGRATION.md`](docs/INTEGRATION.md)

---

## 8. Build & test

```bash
./gradlew build            # compile + all JUnit tests + jars (incl. -sources.jar / -javadoc.jar)
./gradlew test             # tests only
./gradlew javadocToDocs    # generate API docs and sync into docs/apidocs
```

Tests are plain **JUnit 5, table-driven**, and do not need a Minecraft runtime. CI: `.github/workflows/build.yml`.

---

## 9. Compatibility promise and non-goals

- **Within 1.x**: every public type in the `api` package keeps its existing method signatures and public fields —
  nothing is changed or removed, only **additions** are allowed. See [`docs/API.md`](docs/API.md) §6.
- **Non-goals**: ❌ no mixin into other mods' GUIs / `EditBox` / container screens; ❌ no JEI / REI search takeover;
  ❌ no IME handling, no simplified↔traditional conversion, no word segmentation, no glyph/auxiliary codes;
  ❌ no player-facing config UI; ❌ 1.20.1 Forge only for now.
