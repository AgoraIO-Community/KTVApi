package io.agora.ktvdemo.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.res.ResourcesCompat
import androidx.core.widget.doAfterTextChanged
import androidx.navigation.fragment.findNavController
import io.agora.ktvdemo.rtc.IChannelEventListener
import io.agora.ktvdemo.R
import io.agora.ktvdemo.rtc.RtcEngineController
import io.agora.ktvdemo.databinding.FragmentMainBinding
import io.agora.ktvdemo.utils.KeyCenter
import io.agora.ktvdemo.utils.TokenGenerator

class MainFragment : BaseFragment<FragmentMainBinding>() {

    override fun getViewBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentMainBinding {
        return FragmentMainBinding.inflate(inflater)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding?.apply {
            resetRoleView()
            KeyCenter.localUid = KeyCenter.LeadSingerUid
            setRoleView()
            etChannelId.doAfterTextChanged {
                KeyCenter.channelId = it?.trim().toString()
            }
            btnLeadSinger.setOnClickListener {
                resetRoleView()
                KeyCenter.localUid = KeyCenter.LeadSingerUid
                setRoleView()
            }
            btnCoSinger.setOnClickListener {
                resetRoleView()
                KeyCenter.localUid = KeyCenter.CoSingerUid
                setRoleView()
            }
            btnAudience.setOnClickListener {
                resetRoleView()
                KeyCenter.localUid = KeyCenter.AudienceUid
                setRoleView()
            }
            groupSongType.setOnCheckedChangeListener { p0, checkedId -> KeyCenter.isMcc = checkedId == R.id.rbtMccSong }
            btnStartChorus.setOnClickListener {
                if (KeyCenter.channelId.isEmpty()){
                    toast("请输入频道号")
                    return@setOnClickListener
                }
                RtcEngineController.eventListener = IChannelEventListener()
                TokenGenerator.generateTokens(KeyCenter.channelId,
                    KeyCenter.localUid.toString(),
                    TokenGenerator.TokenGeneratorType.token006,
                    arrayOf(
                        TokenGenerator.AgoraTokenType.rtc,
                        TokenGenerator.AgoraTokenType.rtm
                    ),
                    success = { ret ->
                        val rtcToken = ret[TokenGenerator.AgoraTokenType.rtc] ?: ""
                        val rtmToken = ret[TokenGenerator.AgoraTokenType.rtm] ?: ""
                        TokenGenerator.generateToken("${KeyCenter.channelId}_ad", KeyCenter.localUid.toString(),
                            TokenGenerator.TokenGeneratorType.token007, TokenGenerator.AgoraTokenType.rtc,
                            success = { exToken ->

                                TokenGenerator.generateToken(KeyCenter.channelId, "2023",
                                    TokenGenerator.TokenGeneratorType.token007, TokenGenerator.AgoraTokenType.rtc,
                                    success = { musicToken ->
                                        RtcEngineController.chorusChannelToken = rtcToken
                                        RtcEngineController.rtmToken = rtmToken
                                        RtcEngineController.audienceChannelToken = exToken
                                        RtcEngineController.musicStreamToken = musicToken
                                        findNavController().navigate(R.id.action_mainFragment_to_livingFragment)
                                    },
                                    failure = {
                                        toast("获取 token 异常1")
                                    }
                                )
                            },
                            failure = {
                                toast("获取 token 异常2")
                            }
                        )
                    },
                    failure = {
                        toast("获取 token 异常3")
                    }
                )
            }
        }
    }

    private fun resetRoleView() {
        binding?.apply {
            btnLeadSinger.setBackgroundColor(ResourcesCompat.getColor(resources, R.color.lighter_gray, null))
            btnCoSinger.setBackgroundColor(ResourcesCompat.getColor(resources, R.color.lighter_gray, null))
            btnAudience.setBackgroundColor(ResourcesCompat.getColor(resources, R.color.lighter_gray, null))
        }
    }

    private fun setRoleView() {
        binding?.apply {
            if (KeyCenter.isLeadSinger()) {
                btnLeadSinger.setBackgroundColor(ResourcesCompat.getColor(resources, R.color.darker_gray, null))
            } else if (KeyCenter.isCoSinger()) {
                btnCoSinger.setBackgroundColor(ResourcesCompat.getColor(resources, R.color.darker_gray, null))
            } else {
                btnAudience.setBackgroundColor(ResourcesCompat.getColor(resources, R.color.darker_gray, null))
            }
        }
    }
}