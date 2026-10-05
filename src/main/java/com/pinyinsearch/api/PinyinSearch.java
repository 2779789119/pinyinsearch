package com.pinyinsearch.api;

import com.pinyinsearch.impl.EngineCache;
import com.pinyinsearch.impl.Normalizer;
import com.pinyinsearch.impl.PininMatcher;
import com.pinyinsearch.shaded.pinin.PinIn;

import java.util.List;

/**
 * 拼音搜索库的<b>唯一对外入口</b>。
 *
 * <h2>能力</h2>
 * <p>全拼 / 声母 / 简拼 / 任意组合 / 声调 / 双拼（自然码·小鹤）/ 注音（大千）/ 7 种模糊音 / 简繁（随字典）——
 * 全部由内置的 PinIn（MIT，已重定位包名）提供；本库负责 MC 模组化包装、配置隔离与异常安全。</p>
 *
 * <h2>约定（所有方法共同遵守）</h2>
 * <ul>
 *   <li><b>不抛异常</b>：任何内部 {@link Throwable} 一律 capture，退化为「原文包含」语义。</li>
 *   <li><b>归一化由库吃掉</b>：小写 + 全角转半角 + 去首尾空白，调用方无需预处理。</li>
 *   <li><b>无副作用</b>：不写日志（除外部 debug 开关）、不改全局状态、不注册任何事件。</li>
 *   <li><b>无状态</b>：本类不持有任何可变全局状态（只有一份私有的、有容量上限的引擎缓存），
 *       因此被多个模组 jarJar 嵌入也完全安全。</li>
 *   <li><b>线程</b>：必须在<b>客户端主线程</b>使用。静态入口对并发做了防御性处理，
 *       但这不代表并发被支持（底层引擎与 {@link Matcher} 仍要求主线程）。</li>
 * </ul>
 *
 * <h2>能力探测</h2>
 * <p>{@link #isAvailable()} 只检查「类可加载 + 拼音表资源存在」，<b>不会</b>解析字典，可以在启动早期调用。</p>
 *
 * @see Profile
 * @see Matcher
 */
public final class PinyinSearch {

    private static final Profile DEFAULT_PROFILE = new Profile.Builder().build();
    private static final boolean AVAILABLE = probe();

    private PinyinSearch() {
    }

    private static boolean probe() {
        try {
            return PinIn.class.getResourceAsStream("data.txt") != null;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * 库是否可用（正常情况下恒为 {@code true}）。依赖方做能力探测用。
     *
     * <p>探测内容仅为「PinIn 类可加载 + 拼音表 {@code data.txt} 资源可定位」，
     * <b>不会解析字典</b>，因此开销极低，可在模组构造阶段调用。</p>
     *
     * @return 类与资源均正常时为 {@code true}；重定位 / 打包出错时为 {@code false}
     */
    public static boolean isAvailable() {
        return AVAILABLE;
    }

    /**
     * 单对单匹配 —— 最常用，使用 {@linkplain #defaultProfile() 默认 Profile}。
     *
     * <p>小列表（几十~几百条）逐条判断用这个，无需建索引。</p>
     *
     * <p>语义详见 {@link #matches(CharSequence, CharSequence, Profile)}。</p>
     *
     * @param text  被搜索的文本（例如物品显示名）；{@code null} 视为匹配成功
     * @param query 用户输入；{@code null} / 空串 / 全空白视为匹配成功
     * @return 是否命中
     */
    public static boolean matches(CharSequence text, CharSequence query) {
        return matches(text, query, DEFAULT_PROFILE);
    }

    /**
     * 单对单匹配，指定 {@link Profile}。
     *
     * <h3>返回 {@code true} 的条件</h3>
     * <ul>
     *   <li>{@code text} 或 {@code query} 为 {@code null}（按「{@code query} 为空」处理，调用方无需自己判空）；</li>
     *   <li>{@code query} 归一化后为空串 / 全空白；</li>
     *   <li>归一化后 {@code text} 原文包含 {@code query}；</li>
     *   <li>拼音命中：全拼 / 声母 / 简拼 / 任意组合 / 带声调数字；
     *       {@code Profile} 开启模糊音后，模糊音变体同样命中。</li>
     * </ul>
     *
     * <h3>返回 {@code false}</h3>
     * <p>其余情况，包括：完全无关的 query、默认 Profile 下未开启模糊音的变体（例如「中国」+ {@code z国}）、
     * 以及内部异常时退化为「原文包含」后仍未命中。</p>
     *
     * <p>本方法<b>不抛异常</b>；实例内部异常一律退化为「原文包含」。</p>
     *
     * @param text    被搜索的文本（haystack）；{@code null} → 返回 {@code true}
     * @param query   用户输入（needle）；{@code null} / 空串 / 全空白 → 返回 {@code true}
     * @param profile 匹配配置；{@code null} → 使用 {@link #defaultProfile()}（不抛异常）
     * @return 是否命中
     */
    public static boolean matches(CharSequence text, CharSequence query, Profile profile) {
        try {
            if (text == null || query == null) {
                return true;
            }
            String q = Normalizer.normalize(query.toString());
            if (q.isEmpty()) {
                return true;
            }
            return EngineCache.engine(profile == null ? DEFAULT_PROFILE : profile)
                    .contains(Normalizer.normalize(text.toString()), q);
        } catch (Throwable t) {
            return literalContains(text, query);
        }
    }

    private static boolean literalContains(CharSequence text, CharSequence query) {
        try {
            if (text == null || query == null) {
                return true;
            }
            String q = Normalizer.normalize(query.toString());
            if (q.isEmpty()) {
                return true;
            }
            return Normalizer.normalize(text.toString()).contains(q);
        } catch (Throwable ignored) {
            // 连原文包含都做不了（例如 OOM）：退化为「不命中」，绝不向上抛
            return false;
        }
    }

    /**
     * 把大数据集包装成 {@link Matcher}，之后可反复用不同 query 搜索。使用 {@linkplain #defaultProfile() 默认 Profile}。
     *
     * <p>⚠️ {@code pool} 必须是<b>稳定有序</b>的列表 —— {@link Matcher#searchIndices(CharSequence)}
     * 返回的下标语义依赖迭代顺序，传 {@code Set} 之类无序集合时下标无意义。</p>
     *
     * <p>⚠️ 默认引擎为 {@link Profile.Engine#LOOP}（逐条调用 {@code matches} 的等价实现），
     * 构建成本极低；选择索引引擎会在<b>创建 Matcher 时同步构建索引</b>，
     * 大文本池不要在渲染 / 输入回调里构建。</p>
     *
     * @param pool 文本池；{@code null} 视为空列表。内部的 {@code null} 元素按「空串」处理
     * @return 新的 {@code Matcher}；本方法不抛异常
     */
    public static Matcher matcher(List<String> pool) {
        return matcher(pool, DEFAULT_PROFILE);
    }

    /**
     * 把大数据集包装成 {@link Matcher}，指定 {@link Profile}（含引擎选择）。
     *
     * <p>语义与限制见 {@link #matcher(List)}。</p>
     *
     * @param pool    文本池；{@code null} 视为空列表
     * @param profile 匹配配置；{@code null} → 使用 {@link #defaultProfile()}
     * @return 新的 {@code Matcher}；本方法不抛异常
     */
    public static Matcher matcher(List<String> pool, Profile profile) {
        return new PininMatcher(pool, profile == null ? DEFAULT_PROFILE : profile);
    }

    /**
     * 默认 {@link Profile}：全拼、{@link Profile.Engine#LOOP}、7 种模糊音全关、即时匹配加速开启。
     *
     * <p>返回的是<b>同一个不可变实例</b>，可以安全地长期持有 / 反复比较。</p>
     *
     * @return 默认 Profile；恒不为 {@code null}
     */
    public static Profile defaultProfile() {
        return DEFAULT_PROFILE;
    }

    /**
     * 自定义 {@link Profile} 的入口；链式配置后 {@link Profile.Builder#build()}。
     *
     * <pre>{@code
     * Profile fuzzy = PinyinSearch.profile().allFuzzy(true).build();
     * }</pre>
     *
     * @return 全新的 {@link Profile.Builder}；恒不为 {@code null}
     */
    public static Profile.Builder profile() {
        return new Profile.Builder();
    }

    /**
     * 归一化：小写 + 全角转半角 + 去首尾空白。
     *
     * <p>调用方<b>不需要</b>自己归一化（{@link #matches} / {@link Matcher} 内部都会做），
     * 本方法的用途是你自己想建索引或缓存文本时保持一致。</p>
     *
     * <p><b>行为稳定性承诺</b>：1.x 内本方法的输入输出行为保持不变；如需调整，
     * 只会新增 {@code normalizeV2}，不会改动本方法。</p>
     *
     * @param text 待归一化文本；{@code null} → 返回空串（{@code ""}），不抛异常
     * @return 归一化后的文本；恒不为 {@code null}
     */
    public static String normalize(String text) {
        return Normalizer.normalize(text);
    }
}
