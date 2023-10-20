package io.agora.ktvapi

import io.agora.rtc2.RtcEngine

/**
 * 场景类型
 * @param KTV K歌房
 * @param ChatRoom 语聊房
 */
enum class SceneType(val value: Int) {
    KTV(0),
    ChatRoom(1)
}

/**
 * 虚拟声卡类型
 * @param NanShengCiXing 男生磁性
 * @param NvShengCiXing 女生磁性
 * @param NanShengTianMei 男生甜美 延迟大(60ms) 男声甜美在k歌场景下会被映射到男生唱歌
 * @param NvShengTianMei 女生甜美 延迟大(60ms) 女生甜美在k歌场景下会被映射到女生唱歌
 * @param NanShengChangGe 男生唱歌
 * @param NvShengChangGe 女生唱歌
 * @param Close 关
 */
enum class SoundCardType constructor(
    val gainValue: Float,
    val presetValue: Int,
    val gender: Int,
    val effect: Int,
) {
    NanShengCiXing(gainValue = 1.0f, presetValue = 4, gender = 0, effect = 0),
    NvShengCiXing(gainValue = 1.0f, presetValue = 4, gender = 1, effect = 0),
    NanShengTianMei(gainValue = 1.0f, presetValue = 4, gender = 0, effect = 1),
    NvShengTianMei(gainValue = 1.0f, presetValue = 4, gender = 1, effect = 1),
    NanShengChangGe(gainValue = 1.0f, presetValue = 4, gender = 0, effect = 2),
    NvShengChangGe(gainValue = 1.0f, presetValue = 4, gender = 1, effect = 2),
    Close(gainValue = -1.0f, presetValue = -1, gender = -1, effect = -1)
}

/**
 * 插入耳机的类型
 * @param EQ0 针对小米系列有线耳机
 * @param EQ1 针对Sony系列有线耳机
 * @param EQ2 针对JBL系列有线耳机
 * @param EQ3 针对华为系列有线耳机
 * @param EQ4 针对iphone系列有线耳机(默认)
 */
enum class EarPhoneType constructor(val presetValue: Int) {
    EQ0(0),
    EQ1(1),
    EQ2(2),
    EQ3(3),
    EQ4(4),
}

class AudioSettings constructor(private val mRtcEngine: RtcEngine) {
    companion object {
        var enabled: Boolean = false
    }

    fun enableVirtualSoundCard(sceneType: SceneType, soundCardType: SoundCardType, earPhoneType: EarPhoneType?) {
        enabled = soundCardType != SoundCardType.Close
        setVirtualSoundCardType(sceneType, soundCardType, earPhoneType)
        if (sceneType == SceneType.KTV) {
            mRtcEngine.setParameters("{\"che.audio.agc.enable\": ${!enabled}}")
        }
    }

    fun enableAGC(enable: Boolean) {
        mRtcEngine.setParameters("{\"che.audio.agc.enable\": $enable}")
    }

    private fun setVirtualSoundCardType(sceneType: SceneType, soundCardType: SoundCardType, earPhoneType: EarPhoneType?) {
        // 延迟大(60ms)建议男声甜美在k歌场景下 映射到唱歌私参
        if (sceneType == SceneType.KTV && soundCardType == SoundCardType.NanShengTianMei) {
            setVirtualSoundCardType(SceneType.ChatRoom, SoundCardType.NanShengChangGe, earPhoneType)
            return
        }
        // 延迟大(60ms)建议女生甜美在k歌场景下 映射到唱歌私参
        if (sceneType == SceneType.KTV && soundCardType == SoundCardType.NvShengTianMei) {
            setVirtualSoundCardType(SceneType.ChatRoom, SoundCardType.NvShengChangGe, earPhoneType)
            return
        }

        // 有预设的耳机类型
        earPhoneType?.let {
            if (soundCardType != SoundCardType.Close) {
                mRtcEngine.setParameters("{\"che.audio.virtual_soundcard\":{\"preset\":${earPhoneType.presetValue},\"gain\":${soundCardType.gainValue},\"gender\":${soundCardType.gender},\"effect\":${soundCardType.effect}}}")
                return
            }
        }

        // 默认耳机类型
        mRtcEngine.setParameters("{\"che.audio.virtual_soundcard\":{\"preset\":${soundCardType.presetValue},\"gain\":${soundCardType.gainValue},\"gender\":${soundCardType.gender},\"effect\":${soundCardType.effect}}}")
    }
}