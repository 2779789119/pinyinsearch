package com.pinyinsearch.api;

import java.util.Objects;

/**
 * 拼音匹配的<b>不可变</b>配置描述对象：拼法（输入法）/ 索引引擎 / 7 种模糊音 / 即时匹配加速。
 *
 * <h2>使用方式</h2>
 * <p>通过 {@link PinyinSearch#profile()} 或 {@link #toBuilder()} 拿到 {@link Builder}，链式配置后 {@link Builder#build()}：</p>
 * <pre>{@code
 * Profile fuzzy = PinyinSearch.profile().allFuzzy(true).build();
 * }</pre>
 *
 * <h2>★ 生命周期建议（重要）</h2>
 * <p>{@link Profile} 是<b>配置描述</b>，不是一次性用品：库内部按 {@code Profile} 缓存拼音引擎实例，
 * 而构造引擎需要解析整份拼音字典（重操作，单实例内存可达 MB 量级）。</p>
 * <p>因此请<b>在初始化时构造少数几个 {@code Profile} 并长期复用</b>，
 * <b>不要在热路径（每帧 / 每次按键 / 每次查询）上反复 {@code build()}</b> ——
 * 每个不同的 {@code Profile} 都会占住一份引擎。库的缓存有容量上限，超出后会淘汰最久未使用的引擎
 * （最坏退化为重新构造）。</p>
 *
 * <h2>不可变性与相等性</h2>
 * <p>本类型不持有任何可变状态；{@link #equals(Object)} / {@link #hashCode()} 覆盖全部配置项，
 * 用于引擎缓存的键。任何配置项不同的两个 {@code Profile} 一定不相等。</p>
 *
 * <h2>线程</h2>
 * <p>本类型自身不可变，可安全跨线程传递；但由它派生出的匹配行为仍<b>必须在客户端主线程</b>执行
 * （见 {@link PinyinSearch#matches(CharSequence, CharSequence, Profile)}）。</p>
 *
 * @see PinyinSearch#defaultProfile()
 */
public final class Profile {

    /**
     * 输入方式（键盘布局）。枚举常量与 PinIn 的 {@code Keyboard} 一一对应。
     *
     * <p>{@link #SHUANGPIN_ZIRANMA} / {@link #SHUANGPIN_XIAOHE} / {@link #ZHUYIN_DACHEN}
     * 会<b>改变 query 的解析方式</b>（用户输入被当成双拼按键串 / 注音串），
     * 属于使用者主动选择的输入法，因此默认<b>关闭</b>。</p>
     */
    public enum Scheme {
        /** 全拼（默认）。 */
        QUANPIN,
        /** 双拼 · 自然码。<b>PinIn 自述双拼仍在测试阶段，属实验性功能。</b> */
        SHUANGPIN_ZIRANMA,
        /** 双拼 · 小鹤。<b>PinIn 自述双拼仍在测试阶段，属实验性功能。</b> */
        SHUANGPIN_XIAOHE,
        /** 注音 · 大千。<b>实验性功能。</b> */
        ZHUYIN_DACHEN,
    }

    /**
     * {@link Matcher} 的底层实现（索引引擎）。
     *
     * <p>只影响 {@link PinyinSearch#matcher(java.util.List, Profile)} 这条路径，
     * <b>不影响</b> {@link PinyinSearch#matches(CharSequence, CharSequence, Profile)}。</p>
     */
    public enum Engine {
        /**
         * 默认。逐条调用即时匹配实现（{@code PinIn.contains}），与
         * {@link PinyinSearch#matches(CharSequence, CharSequence, Profile)} 的等价性<b>由构造保证</b>，
         * 无需额外验证。10k 词条量级仍可接受。
         */
        LOOP,
        /**
         * PinIn {@code SimpleSearcher}：构建快、搜索慢。
         * <p>⚠️ 索引匹配与即时匹配<b>是否逐条等价未经验证</b>，使用前请自行验收。</p>
         */
        SIMPLE,
        /**
         * PinIn {@code TreeSearcher}：构建慢、搜索快、内存占用最大。
         * <p>⚠️ 索引匹配与即时匹配<b>是否逐条等价未经验证</b>，使用前请自行验收。</p>
         */
        TREE,
        /**
         * PinIn {@code CachedSearcher}：介于 {@link #SIMPLE} 与 {@link #TREE} 之间。
         * <p>⚠️ 索引匹配与即时匹配<b>是否逐条等价未经验证</b>，使用前请自行验收。</p>
         */
        CACHED,
    }

    private final Scheme scheme;
    private final Engine engine;
    private final boolean fuzzyZhZ;
    private final boolean fuzzyShS;
    private final boolean fuzzyChC;
    private final boolean fuzzyAngAn;
    private final boolean fuzzyIngIn;
    private final boolean fuzzyEngEn;
    private final boolean fuzzyUV;
    private final boolean accelerate;

    private Profile(Builder builder) {
        this.scheme = builder.scheme;
        this.engine = builder.engine;
        this.fuzzyZhZ = builder.fuzzyZhZ;
        this.fuzzyShS = builder.fuzzyShS;
        this.fuzzyChC = builder.fuzzyChC;
        this.fuzzyAngAn = builder.fuzzyAngAn;
        this.fuzzyIngIn = builder.fuzzyIngIn;
        this.fuzzyEngEn = builder.fuzzyEngEn;
        this.fuzzyUV = builder.fuzzyUV;
        this.accelerate = builder.accelerate;
    }

    /** @return 输入方式，默认 {@link Scheme#QUANPIN} */
    public Scheme scheme() {
        return scheme;
    }

    /** @return 索引引擎，默认 {@link Engine#LOOP} */
    public Engine engine() {
        return engine;
    }

    /** @return 模糊音 {@code zh <-> z} 是否开启，默认 {@code false} */
    public boolean fuzzyZhZ() {
        return fuzzyZhZ;
    }

    /** @return 模糊音 {@code sh <-> s} 是否开启，默认 {@code false} */
    public boolean fuzzyShS() {
        return fuzzyShS;
    }

    /** @return 模糊音 {@code ch <-> c} 是否开启，默认 {@code false} */
    public boolean fuzzyChC() {
        return fuzzyChC;
    }

    /** @return 模糊音 {@code ang <-> an} 是否开启，默认 {@code false} */
    public boolean fuzzyAngAn() {
        return fuzzyAngAn;
    }

    /** @return 模糊音 {@code ing <-> in} 是否开启，默认 {@code false} */
    public boolean fuzzyIngIn() {
        return fuzzyIngIn;
    }

    /** @return 模糊音 {@code eng <-> en} 是否开启，默认 {@code false} */
    public boolean fuzzyEngEn() {
        return fuzzyEngEn;
    }

    /** @return 模糊音 {@code u <-> v}（ü）是否开启，默认 {@code false} */
    public boolean fuzzyUV() {
        return fuzzyUV;
    }

    /**
     * @return 即时匹配加速（PinIn 的 {@code accelerate}）是否开启，默认 {@code true}。
     *         <p>适用场景是「同一个 query × 多个 text」的稳定调用（搜索框正是如此）；
     *         若调用方是「text 固定、query 频繁变」的少见场景，可关掉。</p>
     */
    public boolean accelerate() {
        return accelerate;
    }

    /** {@inheritDoc} 覆盖全部配置项。 */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Profile)) {
            return false;
        }
        Profile other = (Profile) o;
        return fuzzyZhZ == other.fuzzyZhZ
                && fuzzyShS == other.fuzzyShS
                && fuzzyChC == other.fuzzyChC
                && fuzzyAngAn == other.fuzzyAngAn
                && fuzzyIngIn == other.fuzzyIngIn
                && fuzzyEngEn == other.fuzzyEngEn
                && fuzzyUV == other.fuzzyUV
                && accelerate == other.accelerate
                && scheme == other.scheme
                && engine == other.engine;
    }

    /** {@inheritDoc} 与 {@link #equals(Object)} 一致。 */
    @Override
    public int hashCode() {
        return Objects.hash(scheme, engine, fuzzyZhZ, fuzzyShS, fuzzyChC, fuzzyAngAn,
                fuzzyIngIn, fuzzyEngEn, fuzzyUV, accelerate);
    }

    /** @return 便于日志排查的字符串形式（不含任何运行时状态）。 */
    @Override
    public String toString() {
        return "Profile{scheme=" + scheme
                + ", engine=" + engine
                + ", fuzzy=[zhZ=" + fuzzyZhZ + ", shS=" + fuzzyShS + ", chC=" + fuzzyChC
                + ", angAn=" + fuzzyAngAn + ", ingIn=" + fuzzyIngIn + ", engEn=" + fuzzyEngEn
                + ", uV=" + fuzzyUV + "]"
                + ", accelerate=" + accelerate + '}';
    }

    /**
     * @return 基于本对象当前配置的 {@link Builder}，用于派生出一个新 {@code Profile}。
     */
    public Builder toBuilder() {
        return new Builder(this);
    }

    /**
     * {@link Profile} 的链式构建器。
     *
     * <p>默认值（与 {@link PinyinSearch#defaultProfile()} 一致）：
     * {@link Scheme#QUANPIN}、{@link Engine#LOOP}、7 种模糊音全关、{@code accelerate = true}。</p>
     *
     * <p>本类型不是线程安全的；请配置完成后立即 {@link #build()}，
     * 不要在多个线程间共享同一个 {@code Builder}。</p>
     */
    public static final class Builder {
        private Scheme scheme = Scheme.QUANPIN;
        private Engine engine = Engine.LOOP;
        private boolean fuzzyZhZ;
        private boolean fuzzyShS;
        private boolean fuzzyChC;
        private boolean fuzzyAngAn;
        private boolean fuzzyIngIn;
        private boolean fuzzyEngEn;
        private boolean fuzzyUV;
        private boolean accelerate = true;

        Builder() {
        }

        private Builder(Profile profile) {
            this.scheme = profile.scheme;
            this.engine = profile.engine;
            this.fuzzyZhZ = profile.fuzzyZhZ;
            this.fuzzyShS = profile.fuzzyShS;
            this.fuzzyChC = profile.fuzzyChC;
            this.fuzzyAngAn = profile.fuzzyAngAn;
            this.fuzzyIngIn = profile.fuzzyIngIn;
            this.fuzzyEngEn = profile.fuzzyEngEn;
            this.fuzzyUV = profile.fuzzyUV;
            this.accelerate = profile.accelerate;
        }

        /**
         * 设置输入方式（拼法）。
         *
         * @param scheme 非 {@code null}；传 {@code null} 时保留当前值（不抛异常）
         * @return {@code this}
         */
        public Builder scheme(Scheme scheme) {
            if (scheme != null) {
                this.scheme = scheme;
            }
            return this;
        }

        /**
         * 设置 {@link Matcher} 的底层索引引擎。
         *
         * @param engine 非 {@code null}；传 {@code null} 时保留当前值（不抛异常）
         * @return {@code this}
         */
        public Builder engine(Engine engine) {
            if (engine != null) {
                this.engine = engine;
            }
            return this;
        }

        /** @param on 是否开启模糊音 {@code zh <-> z}；@return {@code this} */
        public Builder fuzzyZhZ(boolean on) {
            this.fuzzyZhZ = on;
            return this;
        }

        /** @param on 是否开启模糊音 {@code sh <-> s}；@return {@code this} */
        public Builder fuzzyShS(boolean on) {
            this.fuzzyShS = on;
            return this;
        }

        /** @param on 是否开启模糊音 {@code ch <-> c}；@return {@code this} */
        public Builder fuzzyChC(boolean on) {
            this.fuzzyChC = on;
            return this;
        }

        /** @param on 是否开启模糊音 {@code ang <-> an}；@return {@code this} */
        public Builder fuzzyAngAn(boolean on) {
            this.fuzzyAngAn = on;
            return this;
        }

        /** @param on 是否开启模糊音 {@code ing <-> in}；@return {@code this} */
        public Builder fuzzyIngIn(boolean on) {
            this.fuzzyIngIn = on;
            return this;
        }

        /** @param on 是否开启模糊音 {@code eng <-> en}；@return {@code this} */
        public Builder fuzzyEngEn(boolean on) {
            this.fuzzyEngEn = on;
            return this;
        }

        /** @param on 是否开启模糊音 {@code u <-> v}（ü）；@return {@code this} */
        public Builder fuzzyUV(boolean on) {
            this.fuzzyUV = on;
            return this;
        }

        /**
         * 便捷方法：一次性开关全部 7 种模糊音（等价于逐个调用 {@code fuzzyXxx(on)}）。
         *
         * <p>⚠️ 开启模糊音属于「放宽」匹配，会让误命中变多（例如 {@code z国} 也能命中「中国」），
         * 请确认这是调用方想要的语义。</p>
         *
         * @param on 是否全部开启
         * @return {@code this}
         */
        public Builder allFuzzy(boolean on) {
            return fuzzyZhZ(on).fuzzyShS(on).fuzzyChC(on).fuzzyAngAn(on)
                    .fuzzyIngIn(on).fuzzyEngEn(on).fuzzyUV(on);
        }

        /** @param on 是否开启即时匹配加速（默认 {@code true}）；@return {@code this} */
        public Builder accelerate(boolean on) {
            this.accelerate = on;
            return this;
        }

        /**
         * 构建出不可变的 {@link Profile}。
         *
         * @return 新的 {@code Profile} 实例；本方法不抛异常
         */
        public Profile build() {
            return new Profile(this);
        }
    }
}
