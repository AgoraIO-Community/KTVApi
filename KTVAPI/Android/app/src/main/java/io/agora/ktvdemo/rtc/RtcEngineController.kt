package io.agora.ktvdemo.rtc

import android.util.Log
import io.agora.ktvdemo.BuildConfig
import io.agora.ktvdemo.MyApplication
import io.agora.rtc2.*

data class IChannelEventListener constructor(
    var onChannelJoined: ((channel: String, uid: Int) -> Unit)? = null,
    var onUserJoined: ((uid: Int) -> Unit)? = null,
    var onUserOffline: ((uid: Int) -> Unit)? = null,
)

object RtcEngineController {

    private var innerRtcEngine: RtcEngineEx? = null

    var eventListener: IChannelEventListener? = null
        set(value) {
            field = value
        }

    val rtcEngine: RtcEngineEx
        get() {
            if (innerRtcEngine == null) {
                val config = RtcEngineConfig()
                config.mContext = MyApplication.app()
                config.mAppId = BuildConfig.AGORA_APP_ID
                config.mEventHandler = object : IRtcEngineEventHandler() {
                    override fun onError(err: Int) {
                        super.onError(err)
                        Log.d(
                            "RtcEngineController",
                            "Rtc Error code:$err, msg:" + RtcEngine.getErrorDescription(err)
                        )
                    }

                    override fun onJoinChannelSuccess(channel: String, uid: Int, elapsed: Int) {
                        super.onJoinChannelSuccess(channel, uid, elapsed)
                        eventListener?.onChannelJoined?.invoke(channel, uid)
                    }
                }
                innerRtcEngine = (RtcEngine.create(config) as RtcEngineEx).apply {
                    setAudioProfile(
                        Constants.AUDIO_PROFILE_MUSIC_HIGH_QUALITY,
                        Constants.AUDIO_SCENARIO_GAME_STREAMING
                    )
                }
            }
            return innerRtcEngine!!
        }

    var audienceChannelToken: String = ""
    var chorusChannelToken: String = ""
    var musicStreamToken = ""
    var rtmToken = ""
}