package com.pinyinsearch;

import net.minecraftforge.fml.common.Mod;

/**
 * 模组入口。
 *
 * <p>本模组是<b>库</b>：不做 GUI 注入、不接管 JEI/REI、不注册任何事件、不写任何全局状态。
 * 这个 {@code @Mod} 入口存在的唯一意义是让它成为一个「可以被正常安装 / 被 jarJar 嵌入 / 能被
 * {@code ModList.isLoaded()} 探测到」的真模组。</p>
 *
 * <p>所有能力都在 {@code com.pinyinsearch.api} 包里。</p>
 */
@Mod(PinyinSearchMod.MOD_ID)
public final class PinyinSearchMod {

    /** 模组 ID。依赖方用它做软依赖探测（{@code ModList.get().isLoaded(MOD_ID)}）。 */
    public static final String MOD_ID = "pinyin_search";

    public PinyinSearchMod() {
        // 无副作用：不注册事件、不读写配置、不打日志
    }
}
