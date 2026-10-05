# Pinyin Search API

**A pinyin search library *for mod developers*.** One dependency line, three lines of code.
No GUI injection, no JEI/REI takeover, no player-facing UI — it is a *capability provider*, not a
replacement for JustEnoughCharacters.

**License: MIT** (this project) · bundled PinIn keeps its own MIT notice.

| | |
|---|---|
| modid | `pinyin_search` |
| Loader / MC | Forge 47.x / 1.20.1 |
| Dependencies | **none** |
| Size | ~204 KB |
| Java package | `com.pinyinsearch` |

[中文说明](README.md)

---

## 📖 API documentation

| What you want | Where |
|---|---|
| Read the API end to end (parameters, return values, null/thread/exception semantics, copy-paste snippets) | **[`docs/API.md`](docs/API.md)** (Chinese) |
| Open offline HTML (double-click, or zip the folder and hand it to someone) | [`docs/apidocs/index.html`](docs/apidocs/index.html) |
| Hover documentation inside your IDE (easiest) | JitPack `-javadoc` / `-sources` artifacts |

```gradle
compileOnly 'com.github.2779789119:pinyinsearch:1.1.0'
compileOnly 'com.github.2779789119:pinyinsearch:1.1.0:javadoc'
compileOnly 'com.github.2779789119:pinyinsearch:1.1.0:sources'
```

```bash
./gradlew javadoc          # -> build/docs/javadoc
./gradlew javadocToDocs    # sync into docs/apidocs
```

---

## 1. One dependency line, three lines of code

```gradle
repositories { maven { url 'https://jitpack.io' } }

dependencies {
    compileOnly 'com.github.2779789119:pinyinsearch:1.1.0'   // soft dependency
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
> For extra safety, wrap the call in a tiny class (e.g. `PinyinSearchBridge`).

### API surface

```java
PinyinSearch.matches(text, query);                       // single pair, default Profile
PinyinSearch.matches(text, query, profile);              // single pair, custom Profile

Matcher m = PinyinSearch.matcher(pool);                  // pool: stable, ordered List<String>
m.searchIndices("zsj");                                  // ascending indices
m.search("zsj");                                         // matched strings, order + duplicates kept

Matcher raw = Matcher.literal(pool);                     // plain contains, no pinyin

Profile fuzzy = PinyinSearch.profile().allFuzzy(true).build();
Profile sp    = PinyinSearch.profile().scheme(Profile.Scheme.SHUANGPIN_XIAOHE).build();
Profile big   = PinyinSearch.profile().engine(Profile.Engine.TREE).build();

PinyinSearch.normalize(raw);                             // lower-case + full-width→half-width + strip
PinyinSearch.isAvailable();                              // capability probe, cheap
```

## 2. Capability matrix

Every capability comes from the bundled PinIn; this library only exposes it and picks defaults.

| Capability | Supported | How / default | Example |
|---|---|---|---|
| Literal substring | ✅ always on | built into the matcher | `钻石` → 钻石剑 |
| Full pinyin | ✅ default | `Scheme.QUANPIN` | `zuanshijian` → 钻石剑 |
| Initials / abbreviations / any mix | ✅ always on | recursive matcher | `zsj`, `zuanjian`, `tiezh` |
| Tones (optional) | ✅ always on | `PinyinFormat.NUMBER` | `zhong1国`, `zhen1` |
| Shuangpin (Ziranma / Xiaohe) | ✅ **experimental**, off | `Scheme.SHUANGPIN_*` | 中国 = `vsgo` |
| Zhuyin (Daqian) | ✅ **experimental**, off | `Scheme.ZHUYIN_DACHEN` | 中国 = `5j/eji` |
| Fuzzy sounds ×7 | ✅ all off | `Builder.fuzzyXxx(...)`, `allFuzzy(b)` | with `fuzzyZhZ`, `zong国` matches 中国 |
| Simplified ↔ Traditional | ⚠️ **no cross-match** (see §5) | follows the dictionary | traditional text itself is searchable by pinyin |
| Immediate / indexed matching | ✅ | `matches(...)` / `Profile.Engine` (default `LOOP`) | — |
| `accelerate` | ✅ on | `Profile.accelerate()` | — |
| Glyph/auxiliary codes | ❌ never | — | PinIn does not support them |
| Word-level polyphone override | ❌ not in v1.0 | `Profile.readingOverride` planned for v1.1 | see §5 |
| English substring (case-insensitive) | ✅ | literal path | `diamond` → `Diamond Sword` |
| English initials | ❌ | — | `DS` does **not** match `Diamond Sword` |

**Why these defaults:** initials/abbreviation/tones are strict supersets (no extra false positives), so they
are on; fuzzy sounds *widen* matching and shuangpin/zhuyin *change how the query is parsed*, so they are off
and must be opted into. `accelerate` is on because the main use case is one query × many texts.
The default engine is `LOOP`, so "found by brute force but not by the index" can never happen on the default path.

## 3. Semantics

| Case | Behaviour |
|---|---|
| `text == null` or `query == null` | `matches` → `true` (treated as empty query) |
| empty / blank query | `matches` → `true`; `Matcher.search*` → every index |
| any internal error | captured, degrades to literal `contains`, **never throws** |
| normalization | lower-case + full-width→half-width + strip; done by the library |
| `Matcher` input type | `List<String>` (not `Collection`) — returned indices only make sense on a stable ordered list |
| `null` elements in `pool` | indexed as `""`, but always match (because `matches(null, q) == true`) and are returned as `null` |
| threads | **client main thread only**; a built index is read-only, so "build async → use on main thread" is fine |
| side effects | none — no logging, no global state, no event registration |

**Equivalence guarantee.** For the same pool/query/Profile,
`matcher(pool, p).searchIndices(query)` equals the set of indices where `matches(pool[i], query, p)` is true.
That holds *by construction* for `Engine.LOOP` (the default). `SIMPLE / TREE / CACHED` are opt-in optimizations
(they passed the equivalence test with PinIn 1.6.0 in this repo, but you own that premise).

## 4. Performance & memory

PinIn's own benchmark (37k entries / ~400k chars / ~900KB sample):
`TreeSearcher` 210 ms build, 0.19 ms search, 9.50 MB; `SimpleSearcher` 27 ms / 9.1 ms / 1.84 MB;
`CachedSearcher` 28 ms (+16 ms warm-up) / 0.55 ms; brute-force pinyin matched (= our `LOOP`) 23 ms.

> ⚠️ **A `Profile` is a description, not a disposable object.**
> Constructing `PinIn` parses the whole dictionary. Engines are cached per `Profile`
> (cache capacity: 16, LRU eviction). Build a *few* profiles at init and reuse them —
> do **not** call `build()` on a hot path.

`Engine.SIMPLE/TREE/CACHED` build their index **synchronously** when you create the `Matcher` —
never do that inside a render or input callback.

## 5. Known limitations (measured, not guessed)

Assertions for all of these live in `KnownBehaviorTest`.

| Limitation | Measured behaviour |
|---|---|
| Context-sensitive polyphones | Every reading of a character participates, so 重庆 matches both `chongqing` and `zhongqing`; there is **no word-level control** (v1.0). `Profile.readingOverride` is planned for v1.1 |
| Glyph/auxiliary codes | Do not exist and will never be added |
| Simplified ↔ Traditional | ❌ no cross-match (`钻石剑` ✗ `鑽石劍`), but traditional text *is* pinyin-searchable (`鑽石劍` + `zsj` ✅) |
| Shuangpin / Zhuyin | Work (`中国` = `vsgo` / `5j/eji`) but are **experimental** |
| Pure numeric query | `铁砧` + `1` → no match, no misparse. Tones *are* validated: `tie3` ✅ / `tie1` ❌ |
| Tone position | Must follow a **complete syllable**: `zhong1国` ✅, `zhen1` ✅, `zh1国` ❌ |
| English substring | ✅ `diamond` / `word` → `Diamond Sword` (case-insensitive, anywhere), but `diamonds` ❌ |
| English initials | ❌ `DS` does not match `Diamond Sword` |

### ⚠️ Two places where the original design doc's table was wrong

| Case | Doc says | Measured | Reason |
|---|---|---|---|
| `中国` + `z国` | ❌ | ✅ | `z` is a *legitimate* abbreviation of `zhong` (same mechanism as `zsj` → 钻石剑); it is not the fuzzy sound `zh → z`. The real fuzzy case, `zong国`, is indeed ❌ |
| `中国` + `zh1国` | ✅ | ❌ | Tone digits must follow a complete syllable; PinIn does not accept them after a bare initial |

This library invents no pinyin logic, so it reports the real behaviour instead of patching around it.

## 6. Integration

**Soft dependency:** see §1 — install once for the whole pack, your jar stays small.

**Hard dependency + jarJar (transparent to players):**

```gradle
dependencies {
    compileOnly 'com.github.2779789119:pinyinsearch:1.1.0'
    jarJar      'com.github.2779789119:pinyinsearch:1.1.0'
}
```

```toml
[[dependencies.yourmod]]
modId = "pinyin_search"
mandatory = false
versionRange = "[1.0.0,2.0.0)"
ordering = "NONE"
side = "BOTH"
```

Verify these four things once, when you first integrate: (1) a pack with only your embedded mod works;
(2) two mods both embedding the library start fine; (3) player installs both the standalone library *and* an
embedding mod (duplicate modId?) — document which one to choose; (4) loading together with JECh causes no
class conflict. The library is stateless, so even two copies cannot corrupt each other — worst case is one
extra copy in memory.

**Maven coordinates** (JitPack format is `com.github.User:Repo:Tag`):

```gradle
compileOnly 'com.github.2779789119:pinyinsearch:1.1.0'
```

## 7. Coexistence with JustEnoughCharacters

No class conflict: PinIn is relocated to `com.pinyinsearch.shaded.pinin` and the jar contains
**no** `me/towdium/pinin/**`. Behaviour may differ: JECh's own config (fuzzy sounds, scheme) is independent
from this library's `Profile`, and this library deliberately **does not read** JECh's config.

JECh patches *other* mods via coremod call sites; this library serves mods that *choose* to integrate.
Opposite approaches, so they do not compete.

## 8. Bundled PinIn

Vendored from <https://github.com/Towdium/PinIn> (MIT), version **1.6.0**, all 16 source files, **nothing
stripped**. The only modification is the package relocation
(`me.towdium.pinin` → `com.pinyinsearch.shaded.pinin`) and the matching resource path
(`com/pinyinsearch/shaded/pinin/data.txt`). Each file carries a header comment describing it.

`fastutil`: upstream declares 8.3.0, Minecraft 1.20.1 ships **8.5.9** (same major) → the library reuses
Minecraft's fastutil and bundles nothing extra. Compliance: `LICENSE` (MIT) and `LICENSE-Pinin.txt` are at
the repo root *and* inside the jar.

## 9. Build & test

```bash
./gradlew build   # compile + all JUnit tests + jar
./gradlew test    # tests only
```

Tests are plain JUnit 5, table-driven, and do not need a Minecraft runtime. CI
(`.github/workflows/build.yml`) runs build + tests on push/PR and publishes on `v*` tags for JitPack.
