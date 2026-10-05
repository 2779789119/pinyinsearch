package com.pinyinsearch.impl;

import com.pinyinsearch.api.Profile;
import com.pinyinsearch.shaded.pinin.Keyboard;
import com.pinyinsearch.shaded.pinin.PinIn;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 按 {@link Profile} 缓存 PinIn 引擎实例。
 *
 * <h2>为什么必须缓存</h2>
 * <p>{@code new PinIn()} 的构造函数里就会解析整份拼音字典，是<b>重操作</b>
 * （单实例内存可达 MB 量级，{@code TreeSearcher} 量级约 9.5MB），
 * 绝不能「每次 {@code matches()} 都 new 一个」。</p>
 *
 * <h2>为什么必须算内存账</h2>
 * <p>{@link Profile} 是公开可构造的 —— 调用方可以造出任意多个不同 {@code Profile}。
 * 「无淘汰策略的缓存」+「公开可构造的 key」= 每个新 {@code Profile} 常驻一份引擎：
 * 被 10 个模组嵌入、每个模组各建 5 个 {@code Profile}，就是数百 MB 的静默占用。
 * 因此这里给缓存一个容量上限（{@link #MAX_ENGINES}），超出时淘汰<b>最久未使用</b>的引擎
 * （最坏退化为重新构造），不做主动释放。</p>
 *
 * <h2>并发</h2>
 * <p>本库的使用约束是「客户端主线程」，按此用普通 {@code HashMap} 就够；
 * 选并发容器是<b>防御性</b>的 —— {@code matches()} 是静态入口，挡不住没读过文档的调用方
 * 从别的线程（比如异步预处理）调它，一旦并发就会同时写引擎缓存。代价可忽略。</p>
 * <p>但这<b>不代表并发被支持</b>：PinIn 引擎内部的缓存与 {@code Matcher} 仍要求主线程使用。</p>
 *
 * <p>非公开 API（{@code impl} 包不对外承诺兼容性）。</p>
 */
public final class EngineCache {

    /** 引擎缓存的容量上限：超出后淘汰最久未使用的引擎。 */
    public static final int MAX_ENGINES = 16;

    private static final ConcurrentHashMap<Profile, Holder> CACHE = new ConcurrentHashMap<>();
    /** 逻辑访问时钟：只用于 LRU 排序，不需要严格单调。 */
    private static final AtomicLong CLOCK = new AtomicLong();
    /** 引擎构造计数：用于「同一个 Profile 只构造一次」的运行期验证（§8.2 第 4 点）。 */
    private static final AtomicInteger CONSTRUCTIONS = new AtomicInteger();

    private EngineCache() {
    }

    private static final class Holder {
        final PinIn engine;
        volatile long lastAccess;

        Holder(PinIn engine, long lastAccess) {
            this.engine = engine;
            this.lastAccess = lastAccess;
        }
    }

    /**
     * 取得（必要时构造）该 {@link Profile} 对应的 PinIn 引擎。
     *
     * @param profile 非 {@code null} 的配置（调用方负责兜底 {@code null}）
     * @return 可复用的引擎实例
     */
    public static PinIn engine(Profile profile) {
        long now = CLOCK.incrementAndGet();
        Holder holder = CACHE.get(profile);
        if (holder == null) {
            PinIn created = create(profile);
            Holder fresh = new Holder(created, now);
            Holder raced = CACHE.putIfAbsent(profile, fresh);
            if (raced == null) {
                holder = fresh;
                CONSTRUCTIONS.incrementAndGet();
                evictIfNeeded(profile);
            } else {
                // 并发下已经有人放进去了：丢掉自己这份，用先到的那份（只多花一次构造）
                holder = raced;
            }
        }
        holder.lastAccess = now;
        return holder.engine;
    }

    private static PinIn create(Profile profile) {
        PinIn engine = new PinIn();
        engine.config()
                .keyboard(keyboard(profile.scheme()))
                .fZh2Z(profile.fuzzyZhZ())
                .fSh2S(profile.fuzzyShS())
                .fCh2C(profile.fuzzyChC())
                .fAng2An(profile.fuzzyAngAn())
                .fIng2In(profile.fuzzyIngIn())
                .fEng2En(profile.fuzzyEngEn())
                .fU2V(profile.fuzzyUV())
                .accelerate(profile.accelerate())
                .commit();
        return engine;
    }

    private static Keyboard keyboard(Profile.Scheme scheme) {
        switch (scheme) {
            case SHUANGPIN_ZIRANMA:
                return Keyboard.ZIRANMA;
            case SHUANGPIN_XIAOHE:
                return Keyboard.XIAOHE;
            case ZHUYIN_DACHEN:
                return Keyboard.DAQIAN;
            case QUANPIN:
            default:
                return Keyboard.QUANPIN;
        }
    }

    private static void evictIfNeeded(Profile keep) {
        while (CACHE.size() > MAX_ENGINES) {
            Profile victim = null;
            long oldest = Long.MAX_VALUE;
            for (Map.Entry<Profile, Holder> e : CACHE.entrySet()) {
                if (e.getKey().equals(keep)) {
                    continue;
                }
                long access = e.getValue().lastAccess;
                if (access < oldest) {
                    oldest = access;
                    victim = e.getKey();
                }
            }
            if (victim == null) {
                return;
            }
            Holder victimHolder = CACHE.get(victim);
            if (victimHolder == null || !CACHE.remove(victim, victimHolder)) {
                // 并发下已被别人移除：重试一轮即可，避免无进展的死循环
                return;
            }
        }
    }

    /**
     * 引擎构造次数（自进程启动起累计）。用于验证「同一个 {@code Profile} 只构造一次 PinIn」。
     *
     * @return 构造次数
     */
    public static int constructionCount() {
        return CONSTRUCTIONS.get();
    }

    /** 当前缓存的引擎数量。 */
    public static int size() {
        return CACHE.size();
    }

    /**
     * 清空引擎缓存（仅用于测试 / 排障；正常业务不需要调用）。
     * <p>注意：已交给调用方的 {@code Matcher} 仍持有自己的引擎引用。</p>
     */
    public static void clear() {
        CACHE.clear();
        CONSTRUCTIONS.set(0);
    }
}
