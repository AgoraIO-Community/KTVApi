package cn.soulapp.android.lib.media.agroa;

import static io.agora.mediaplayer.Constants.MediaPlayerState.PLAYER_STATE_FAILED;
import static io.agora.mediaplayer.Constants.MediaPlayerState.PLAYER_STATE_IDLE;
import static io.agora.mediaplayer.Constants.MediaPlayerState.PLAYER_STATE_OPEN_COMPLETED;
import static io.agora.mediaplayer.Constants.MediaPlayerState.PLAYER_STATE_PAUSED;
import static io.agora.mediaplayer.Constants.MediaPlayerState.PLAYER_STATE_PLAYBACK_ALL_LOOPS_COMPLETED;
import static io.agora.mediaplayer.Constants.MediaPlayerState.PLAYER_STATE_PLAYBACK_COMPLETED;
import static io.agora.mediaplayer.Constants.MediaPlayerState.PLAYER_STATE_PLAYING;
import static io.agora.mediaplayer.Constants.MediaPlayerState.PLAYER_STATE_STOPPED;
import static io.agora.rtc2.Constants.AUDIO_FILE_RECORDING_PLAYBACK;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import cn.soulapp.android.lib.media.Const;
import cn.soulapp.android.lib.media.agoraup.interfaces.IAgoraKTVSyncProcess;
import cn.soulapp.android.lib.media.agoraup.interfaces.IAgoraRtcBridge;
import cn.soulapp.android.lib.media.agoraup.interfaces.IAgoraRtcBridgeEventHandler;
import cn.soulapp.android.lib.media.agroa.AgoraKTVConfig;
import cn.soulapp.android.lib.media.agroa.AgoraKTVStreamMessage;
import cn.soulapp.android.lib.media.agroa.AgoraKTVSyncProcessNTPHelper;
import cn.soulapp.android.lib.media.zego.utils.GsonUtils;
import io.agora.mediaplayer.IMediaPlayer;
import io.agora.mediaplayer.IMediaPlayerCustomDataProvider;
import io.agora.mediaplayer.IMediaPlayerObserver;
import io.agora.mediaplayer.data.PlayerUpdatedInfo;
import io.agora.mediaplayer.data.SrcInfo;
import io.agora.rtc2.ChannelMediaOptions;
import io.agora.rtc2.Constants;
import io.agora.rtc2.DataStreamConfig;
import io.agora.rtc2.IRtcEngineEventHandler;
import io.agora.rtc2.RtcConnection;
import io.agora.rtc2.RtcEngineEx;
import io.agora.rtc2.internal.AudioRecordingConfiguration;

public class AgoraRtcBridge implements IAgoraRtcBridge, IMediaPlayerObserver {
    private static final String TAG = "AgoraRtcBridge";

    /**
     * 观众
     */
    public final static int KTV_ROLE_AUDIENCE = 2;
    /**
     * 主唱
     */
    public final static int KTV_ROLE_LEADER_SINGER = 3;
    /**
     * 副唱
     */
    public final static int KTV_ROLE_ACCOMPANY_SINGER = 4;

    // =================== 实时合唱场景 ===================
    private final RtcEngineEx mRtcEngine;
    private IMediaPlayer mMediaPlayer;
    private AgoraKTVConfig mKtvConfig;
    private int mRole;
    private final int localUid;
    private String mChorusToken; //主唱人声流token
    private volatile boolean mHasJoinChannelEx;
    private RtcConnection mRtcConnectionEx;
    private boolean mHasOnStart;
    private int mLeaderUid;
    private boolean mEnableMic = true;
    private volatile boolean mHasPlay;
    private int syncSteamId;
    private int steamId;
    private IAgoraKTVSyncProcess syncProcessHelper;//KTV合唱同步
    private boolean isAccompanyDelayPositionChange = false;
    private String audioUniId;
    private boolean isDelayLocalSendPosition = true;
    private long audioDuration = 0;//
    private io.agora.mediaplayer.Constants.MediaPlayerState mMediaPlayerState = PLAYER_STATE_IDLE;

    // TODO Add variables
    private int audioDelay;
    private long currentAudioPosition;

    // TODO Add variables
    private IAgoraRtcBridgeEventHandler eventHandler;
    private String audioUrl;
    private int audioTrack = -1;
    private boolean haveAdjustVolum;
    private int playoutVolume;
    private int publishSignalVolume;
    private boolean isNewLeadSinger = false;
    private boolean isOldLeadSinger = false;
    private final Handler mHandler = new Handler(Looper.getMainLooper());

    public AgoraRtcBridge(RtcEngineEx rtcEngine, int uid) {
        this.mRtcEngine = rtcEngine;
        this.localUid = uid;
    }

    @Override
    public void setEventHandler(IAgoraRtcBridgeEventHandler handler) {
        this.eventHandler = handler;
    }

    // ======================= 实时合唱 =======================
    private void initAudioMediaPlayer() {
        if (mMediaPlayer == null && mRtcEngine != null) {
            DataStreamConfig config = new DataStreamConfig();
            config.syncWithAudio = true;
            steamId = mRtcEngine.createDataStream(config);
            syncSteamId = mRtcEngine.createDataStream(config);
            mMediaPlayer = mRtcEngine.createMediaPlayer();
            mMediaPlayer.registerPlayerObserver(this);
            mMediaPlayer.setPlayerOption("play_pos_change_callback", 100);
            mMediaPlayer.setLoopCount(0);

            if (haveAdjustVolum) {
                if (playoutVolume >= 0 && publishSignalVolume >= 0) {
                    setVideoVolume(playoutVolume, publishSignalVolume);
                }
            }
        }
    }

    @Override
    public IMediaPlayer getMediaPlayer() {
        return mMediaPlayer;
    }

    @Override
    public void leaveKtvRoom() {
        IAgoraKTVSyncProcess syncProcessHelper = this.syncProcessHelper;
        if (syncProcessHelper != null) {
            syncProcessHelper.stop();
            this.syncProcessHelper = null;
        }
        if (mMediaPlayer != null) {
            mMediaPlayer.unRegisterPlayerObserver(this);
            mMediaPlayer.destroy();
            mMediaPlayer = null;
        }
        if (audioCardMediaPlayer != null) {
            audioCardMediaPlayer.destroy();
            audioCardMediaPlayer = null;
        }
        audioDuration = 0;
        haveAdjustVolum = false;
    }

    public void switchSingerRole(int role) {
        if (mRtcEngine == null) return;
        if (mRole == KTV_ROLE_AUDIENCE && role == KTV_ROLE_LEADER_SINGER) {
            startPlay();
        } else if (mRole == KTV_ROLE_AUDIENCE && role == KTV_ROLE_ACCOMPANY_SINGER) {
            startPlay();
        } else if (mRole == KTV_ROLE_LEADER_SINGER && role == KTV_ROLE_AUDIENCE) {
            stopPlay();
        } else if (mRole == KTV_ROLE_ACCOMPANY_SINGER && role == KTV_ROLE_AUDIENCE) {
            stopPlay();
        } else if (mRole == KTV_ROLE_ACCOMPANY_SINGER && role == KTV_ROLE_LEADER_SINGER) {
            if (syncProcessHelper != null) {
                syncProcessHelper.stop();
            }
            //把背景音乐发布到主频道
            ChannelMediaOptions options = new ChannelMediaOptions();
            options.publishMediaPlayerAudioTrack = true;
            options.publishMediaPlayerId = mMediaPlayer.getMediaPlayerId();
            options.clientRoleType = Constants.CLIENT_ROLE_BROADCASTER;
            mRtcEngine.updateChannelMediaOptions(options);

            //加入频道2（人声）
            options = new ChannelMediaOptions();
            options.autoSubscribeAudio = false;
            options.autoSubscribeVideo = false;
            options.publishMicrophoneTrack = false;
            options.enableAudioRecordingOrPlayout = false;
            options.clientRoleType = Constants.CLIENT_ROLE_BROADCASTER;
            options.publishDirectCustomAudioTrack = true;
            mRtcEngine.updateChannelMediaOptionsEx(options, mRtcConnectionEx);
            mRtcEngine.muteRemoteAudioStreamEx(mLeaderUid, false, mRtcConnectionEx);

            mRtcEngine.setParameters("{\"che.audio.custom_bitrate\":80000}");
        }
        mRole = role;
    }

    @Override
    public void playKTVEncryptAudio(
            String url, // TODO Change from PlayKTVParams
            int role,   // TODO Change from PlayKTVParams
            long startPos,
            String chorusToken, // TODO Change from PlayKTVParams
            String chorusChannelId, // TODO Change from PlayKTVParams
            String curSingerUid,    // TODO Change from PlayKTVParams
            int playbackSignalVolume,  // TODO Change from PlayKTVParams
            String audioUniId,
            Boolean isAccompanyDelayPositionChange,
            IMediaPlayerCustomDataProvider customDataProvider // TODO null if not encryption
    ) {
        this.audioUniId = audioUniId;
        this.isAccompanyDelayPositionChange = isAccompanyDelayPositionChange;
        isDelayLocalSendPosition = true;
        mRole = role;
        initAudioMediaPlayer();
        if (!haveAdjustVolum) {
            setLocalVolume(100);
            if (role == KTV_ROLE_ACCOMPANY_SINGER) {
                //伴唱
                mMediaPlayer.adjustPlayoutVolume(25);
            } else {
                setVideoVolume(50);
            }
        }

        audioDuration = 0;
        boolean changeSource =
                !url.equals(audioUrl);
        if (mMediaPlayer.getState() == io.agora.mediaplayer.Constants.MediaPlayerState.PLAYER_STATE_PLAYING && changeSource) {
            mMediaPlayer.stop();
        }
        audioUrl = url;

        if (mRtcEngine == null) {
            Log.e(TAG, "playKTVEncryptAudio rtcEngine==null");
            return;
        }

        AgoraKTVConfig ktvConfig = new AgoraKTVConfig();
        ktvConfig.rtcEngine = mRtcEngine;
        ktvConfig.mediaPlayer = mMediaPlayer;
        ktvConfig.role = role;
        ktvConfig.chorusToken = chorusToken;
        ktvConfig.chorusChannelId = chorusChannelId;
        ktvConfig.leaderUid = Integer.parseInt(curSingerUid);
        ktvConfig.syncSteamId = syncSteamId;
        ktvConfig.playbackSignalVolume = playbackSignalVolume;
        this.mKtvConfig = ktvConfig;
        this.mRole = ktvConfig.role;
        this.mChorusToken = ktvConfig.chorusToken;
        this.mLeaderUid = ktvConfig.leaderUid;
        Log.i(TAG, "new AgoraKTVChorusHelper :mChorusToken="+ mChorusToken
                + " ,chorusChannelId="+ ktvConfig.chorusChannelId
                + " ,mLeaderUid="+mLeaderUid + " ,mRole="+mRole);

        stopPlay();
        enableMic(mEnableMic);
        startPlay();

        if (syncProcessHelper != null) {
            syncProcessHelper.stop();
        }
        syncProcessHelper = new AgoraKTVSyncProcessNTPHelper(ktvConfig);
        if (role == KTV_ROLE_ACCOMPANY_SINGER) {
            syncProcessHelper.startSyncProcess();
        }

        if (mMediaPlayer.getState() != io.agora.mediaplayer.Constants.MediaPlayerState.PLAYER_STATE_PLAYING || changeSource) {
            if (mMediaPlayer.getState() != io.agora.mediaplayer.Constants.MediaPlayerState.PLAYER_STATE_PAUSED) {
                if (null != customDataProvider) {
                    if (startPos > 0) {
                        mMediaPlayer.openWithCustomSource(startPos, customDataProvider);
                    } else {
                        mMediaPlayer.openWithCustomSource(0, customDataProvider);
                    }
                } else {
                    if (startPos > 0) {
                        mMediaPlayer.open(url, startPos);
                    } else {
                        mMediaPlayer.open(url, 0);
                    }
                }
            }
        }
    }

    @Override
    public void stopAudio() {
        audioUniId = "";
        if (mMediaPlayer != null) {
            mMediaPlayer.stop();
            audioDuration = 0;
            ChannelMediaOptions options = new ChannelMediaOptions();
            options.publishMediaPlayerAudioTrack = false;
            options.publishMediaPlayerVideoTrack = false;
            mRtcEngine.updateChannelMediaOptions(options);
        }

        if (syncProcessHelper != null) {
            syncProcessHelper.stop();
            syncProcessHelper = null;
        }
        stopPlay();
        mRole = KTV_ROLE_AUDIENCE;
    }

    /**
     * 设置新的主唱
     * @param uid 新主唱uid
     */
    @Override
    public void setNewLeadSinger(
            int uid,
            String url, // TODO Change from PlayKTVParams
            long startPos,
            String chorusToken, // TODO Change from PlayKTVParams
            String chorusChannelId, // TODO Change from PlayKTVParams
            String curSingerUid,    // TODO Change from PlayKTVParams
            int playbackSignalVolume,  // TODO Change from PlayKTVParams,
            String audioUniId,
            Boolean isAccompanyDelayPositionChange,
            IMediaPlayerCustomDataProvider customDataProvider) {
        if (mRole == KTV_ROLE_LEADER_SINGER && localUid != uid) {
            isOldLeadSinger = true;
            mLeaderUid = uid;
        } else if (mRole == KTV_ROLE_AUDIENCE && localUid == uid) {
            isNewLeadSinger = true;
            switchSingerRole(KTV_ROLE_ACCOMPANY_SINGER);
            playKTVEncryptAudio(url, KTV_ROLE_ACCOMPANY_SINGER, startPos, chorusToken, chorusChannelId, curSingerUid, playbackSignalVolume, audioUniId, isAccompanyDelayPositionChange, customDataProvider);
        }
    }

    @Override
    public void pauseAudio() {
        if (mMediaPlayer != null) {
            mMediaPlayer.pause();
        }
    }

    @Override
    public void resumeAudio() {
        if (mMediaPlayer != null) {
            mMediaPlayer.resume();
        }
    }

    @Override
    public void selectAudioTrack(int audioTrack) {
        if (mMediaPlayer != null) {
            mMediaPlayer.selectAudioTrack(audioTrack);
        }
        this.audioTrack = audioTrack;
    }

    @Override
    public boolean isAudioPlaying() {
        return mMediaPlayer != null && mMediaPlayer.getState() == io.agora.mediaplayer.Constants.MediaPlayerState.PLAYER_STATE_PLAYING;
    }

    @Override
    public long getAudioDuration() {
        if (mMediaPlayer != null) {
            mMediaPlayer.getDuration();
        }
        return 0;
    }

    @Override
    public long getAudioCurrentPosition() {
        return currentAudioPosition;
    }

    @Override
    public void audioSeekTo(long l) {
        if (mMediaPlayer != null) {
            mMediaPlayer.seek(l);
        }
    }

    @Override
    public io.agora.mediaplayer.Constants.MediaPlayerState getAudioPlayerState() {
        if (mMediaPlayer != null) {
            return mMediaPlayer.getState();
        }
        return PLAYER_STATE_IDLE;
    }

    @Override
    public void setAudioDelay(int audioDelay) {
        this.audioDelay = audioDelay;
        if (syncProcessHelper != null) {
            syncProcessHelper.setAudioDelay(audioDelay);
        }
        Log.d("RoomChatEngineAgora", "audioPlayoutDelay="+audioDelay);
    }

    /**
     * 主唱or伴唱开始播放
     */
    private void startPlay() {
        if (mHasPlay || mRtcEngine == null) {
            return;
        }
        mHasPlay = true;
        if (mRole == KTV_ROLE_LEADER_SINGER){
            leaderPlay();
            mRtcEngine.setParameters("{\"che.audio.custom_bitrate\":80000}");
        }else if (mRole == KTV_ROLE_ACCOMPANY_SINGER){
            chorusPlay();
            mRtcEngine.setParameters("{\"che.audio.custom_bitrate\":48000}");
        }

        mRtcEngine.setParameters("{\"rtc.video.enable_sync_render_ntp_broadcast_dynamic\":false}");//主播关闭多端同步，动态设置
    }

    /**
     * 主唱or伴唱停止播放
     */
    private void stopPlay() {
        if (!mHasPlay || mRtcEngine == null) {
            return;
        }
        mHasPlay = false;
        if (mRole == KTV_ROLE_LEADER_SINGER){
            leaderLeave();
        }else if (mRole == KTV_ROLE_ACCOMPANY_SINGER){
            chorusLeave();
        }
        mRtcEngine.adjustPlaybackSignalVolume(100);//恢复收流音量
        mRtcEngine.setParameters("{\"rtc.video.enable_sync_render_ntp_broadcast_dynamic\":true}");//主播启用多端同步，动态设置
        mRtcEngine.setParameters("{\"che.audio.custom_bitrate\":48000}");//
    }

    /**
     * 主唱推送人声流
     */
    @Override
    public void onRecordAudioFrame(ByteBuffer buffer, long renderTimeMs) {
        if (mEnableMic && mRole == KTV_ROLE_LEADER_SINGER && mHasJoinChannelEx && mRtcEngine != null) {
            //推送人声流
            mRtcEngine.pushDirectAudioFrame(buffer, renderTimeMs, 48000, 1);
        }
    }

    /**
     * 开始播放
     */
    private void onStartPlay() {
        if (mHasOnStart || mRtcEngine == null) {
            return;
        }
        mHasOnStart = true;
        if (mRole == KTV_ROLE_ACCOMPANY_SINGER){
            //伴唱
            // mute主频道
            mRtcEngine.muteRemoteAudioStream(mLeaderUid, true);
            //取消mute主唱人声
            mRtcEngine.muteRemoteAudioStreamEx(mLeaderUid, false, mRtcConnectionEx);
            //设置收流音量
            playbackVolume = getPlaybackSignalVolume();
            mRtcEngine.adjustPlaybackSignalVolume(getPlaybackSignalVolume());
        }

        Log.i(TAG, "onStartPlay");
    }

    /**
     * 麦克风是否禁用
     */
    @Override
    public void enableMic(boolean enable) {
        mEnableMic = enable;
    }

    @Override
    public String getChorusToken() {
        return mChorusToken;
    }

    /***
     * 主唱开始播放
     */
    private void leaderPlay() {
        if (mRtcEngine == null) {
            return;
        }

        //把背景音乐发布到主频道
        ChannelMediaOptions options = new ChannelMediaOptions();
        options.publishMediaPlayerAudioTrack = true;
        options.publishMediaPlayerId = mMediaPlayer.getMediaPlayerId();
        options.clientRoleType = Constants.CLIENT_ROLE_BROADCASTER;
        mRtcEngine.updateChannelMediaOptions(options);


        //加入频道2（人声）
        options = new ChannelMediaOptions();
        options.autoSubscribeAudio = false;
        options.autoSubscribeVideo = false;
        options.publishMicrophoneTrack = false;
        options.enableAudioRecordingOrPlayout = false;
        options.clientRoleType = Constants.CLIENT_ROLE_BROADCASTER;
        options.publishDirectCustomAudioTrack = true;
        IRtcEngineEventHandler handler = new IRtcEngineEventHandler() {
            @Override
            public void onJoinChannelSuccess(String channel, int uid, int elapsed)
            {
                Log.i(TAG, "主唱 ex 频道 onJoinChannelSuccess");
                if (mRtcEngine == null) {
                    return;
                }
                mHasJoinChannelEx = true;
                mRtcEngine.enableAudioVolumeIndicationEx(1500, 3, false, mRtcConnectionEx);
            }
            @Override
            public void onLeaveChannel(RtcStats stats) {
                Log.i(TAG, "主唱 ex 频道 onLeaveChannel");
                mHasJoinChannelEx = false;
            }

            @Override
            public void onAudioVolumeIndication(AudioVolumeInfo[] speakers, int totalVolume) {
                super.onAudioVolumeIndication(speakers, totalVolume);
                if (eventHandler != null) {
                    // TODO 转调出去 eventHandler.rtcEventHandler.onAudioVolumeIndication
                    eventHandler.onAudioVolumeIndication(speakers, totalVolume);
                }
//                AgroaEngineEventHandler eventHandler = getEventHandler();
//                if (eventHandler != null) {
//                    eventHandler.rtcEventHandler.onAudioVolumeIndication(speakers, totalVolume);
//                }
            }

            // TODO 接唱暂时不用该方案
//            @Override
//            public void onUserJoined(int uid, int elapsed) {
//                super.onUserJoined(uid, elapsed);
//                // 接唱人员加入
//                if (uid == mLeaderUid && isOldLeadSinger) {
//                    isOldLeadSinger = false;
//                    mHandler.postDelayed(() -> {
//                        switchSingerRole(KTV_ROLE_AUDIENCE);
//                    }, 3000);
//                }
//            }
//
//            @Override
//            public void onUserOffline(int uid, int reason) {
//                super.onUserOffline(uid, reason);
//                if (uid == mLeaderUid && isNewLeadSinger) {
//                    isNewLeadSinger = false;
//                    mHandler.post(() -> {
//                        switchSingerRole(KTV_ROLE_LEADER_SINGER);
//                        mLeaderUid = localUid;
//                    });
//                }
//            }
        };
        leaderLeave();
        mRtcEngine.setAudioScenario(Constants.AUDIO_SCENARIO_CHORUS);
        mRtcConnectionEx = new RtcConnection();
        mRtcConnectionEx.channelId = mKtvConfig.chorusChannelId;
        mRtcConnectionEx.localUid = localUid;
        mRtcEngine.joinChannelEx(mChorusToken, mRtcConnectionEx,
                options, handler);

        //主唱在有人加入合唱后才需要设置收流音量
//        mRtcEngine.adjustPlaybackSignalVolume(getPlaybackSignalVolume());
        Log.i(TAG, "leaderPlay");
    }

    /**
     * 主唱离开合唱，退出EX频道
     */
    private void leaderLeave() {
        if (mRtcConnectionEx == null || mRtcEngine == null) {
            return;
        }
        mHasJoinChannelEx = false;
        ChannelMediaOptions options = new ChannelMediaOptions();
        options.publishDirectCustomAudioTrack = false;
        mRtcEngine.updateChannelMediaOptionsEx(options,
                mRtcConnectionEx);
        mRtcEngine.leaveChannelEx(mRtcConnectionEx);
        mRtcEngine.setAudioScenario(Constants.AUDIO_SCENARIO_GAME_STREAMING);
        mRtcConnectionEx = null;
        Log.i(TAG, "leaderLeave");
    }


    /**
     * 伴唱开始播放
     */
    private void chorusPlay() {
        if (mRtcEngine == null) {
            return;
        }

        //加入频道2（主唱人声）
        ChannelMediaOptions options = new ChannelMediaOptions();
        options.autoSubscribeAudio = true;
        options.autoSubscribeVideo = false;
        options.publishMicrophoneTrack = false;
        options.clientRoleType = Constants.CLIENT_ROLE_BROADCASTER;
        IRtcEngineEventHandler handler = new IRtcEngineEventHandler() {
            @Override
            public void onJoinChannelSuccess(String channel, int uid, int
                    elapsed) {
                Log.i(TAG, "伴唱 ex 频道 onJoinChannelSuccess");
            }
            @Override
            public void onLeaveChannel(RtcStats stats) {
                Log.i(TAG, "伴唱 ex 频道 onLeaveChannel");
            }
        };
        chorusLeave();
        mRtcEngine.setAudioScenario(Constants.AUDIO_SCENARIO_CHORUS);
        mRtcConnectionEx = new RtcConnection();
        mRtcConnectionEx.channelId = mKtvConfig.chorusChannelId;
        mRtcConnectionEx.localUid = localUid;
        mRtcEngine.joinChannelEx(mChorusToken, mRtcConnectionEx,
                options, handler);

        //先mute主唱人声；等播放开始后，mute主频道，放开人声频道
        mRtcEngine.muteRemoteAudioStreamEx(mLeaderUid, true, mRtcConnectionEx);
        Log.i(TAG, "chorusPlay");
    }

    private void chorusLeave() {
        if (mRtcConnectionEx == null || mRtcEngine == null) {
            return;
        }
        mRtcEngine.leaveChannelEx(mRtcConnectionEx);
        mRtcEngine.setAudioScenario(Constants.AUDIO_SCENARIO_GAME_STREAMING);
        //取消mute主唱bgm+人声频道
        mRtcEngine.muteRemoteAudioStream(mLeaderUid, false);
        mRtcConnectionEx = null;
        Log.i(TAG, "chorusLeave");
    }

    private int getPlaybackSignalVolume(){
        return mKtvConfig.playbackSignalVolume;
    }

    private void setLocalVolume(int volume) {
        if (mRtcEngine == null) return;
        haveAdjustVolum = true;
        mRtcEngine.adjustRecordingSignalVolume(volume * 2);//本地需要发布的人声
        mRtcEngine.setInEarMonitoringVolume(volume);//本地耳返音量
    }

    @Override
    public void setVideoVolume(int volume) {
        if (mMediaPlayer != null) {
            mMediaPlayer.adjustPlayoutVolume(playoutVolume);
            mMediaPlayer.adjustPublishSignalVolume(publishSignalVolume);
        }
    }

    @Override
    public void setVideoVolume(int playoutVolume, int publishSignalVolume) {
        if (mMediaPlayer != null) {
            mMediaPlayer.adjustPlayoutVolume(playoutVolume);
            mMediaPlayer.adjustPublishSignalVolume(publishSignalVolume);
        }
        this.playoutVolume = playoutVolume;
        this.publishSignalVolume = publishSignalVolume;
        haveAdjustVolum = true;
    }

    // ================== IMediaPlayerObserver ==================
    @Override
    public void onPlayerStateChanged(io.agora.mediaplayer.Constants.MediaPlayerState mediaPlayerState, io.agora.mediaplayer.Constants.MediaPlayerError mediaPlayerError) {
        if (audioUrl != null) {
            if (mediaPlayerState.equals(PLAYER_STATE_OPEN_COMPLETED)) {
                audioDuration = mMediaPlayer.getDuration();
                if (mRole != KTV_ROLE_ACCOMPANY_SINGER) {
                    mMediaPlayer.play();
                }
                if (audioTrack >= 0) {
                    selectAudioTrack(audioTrack);
                }
//                if (iRoomCallback != null) {
//                    iRoomCallback.onLocalAudioStateChanged(SLMediaPlayerState.PLAYER_STATE_START);
//                }
            } else if (mediaPlayerState.equals(PLAYER_STATE_PLAYBACK_COMPLETED) || mediaPlayerState.equals(PLAYER_STATE_PLAYBACK_ALL_LOOPS_COMPLETED)) {
                audioUrl = null;
//                if (iRoomCallback != null) {
//                    iRoomCallback.onLocalAudioStateChanged(SLMediaPlayerState.PLAYER_STATE_COMPLETE);
//                }
            } else if (mediaPlayerState.equals(PLAYER_STATE_FAILED)) {
                audioUrl = null;
//                if (iRoomCallback != null) {
//                    iRoomCallback.onLocalAudioStateChanged(SLMediaPlayerState.PLAYER_STATE_FAILED);
//                }
            } else if (mediaPlayerState.equals(PLAYER_STATE_IDLE)) {
//                if (iRoomCallback != null) {
//                    iRoomCallback.onLocalAudioStateChanged(SLMediaPlayerState.PLAYER_STATE_STOP);
//                }
            } else if (mediaPlayerState.equals(PLAYER_STATE_PAUSED)) {
//                if (iRoomCallback != null) {
//                    iRoomCallback.onLocalAudioStateChanged(SLMediaPlayerState.PLAYER_STATE_PAUSED);
//                }
            } else if (mediaPlayerState.equals(PLAYER_STATE_PLAYING)) {
//                if (iRoomCallback != null) {
//                    iRoomCallback.onLocalAudioStateChanged(SLMediaPlayerState.PLAYER_STATE_PLAYING);
//                }
            }
        }

        if (eventHandler != null) {
            // TODO 转调出去 iRoomCallback.onLocalAudioStateChanged
            eventHandler.onPlayerStateChanged(mediaPlayerState, mediaPlayerError);
        }

        if (mediaPlayerState.equals(PLAYER_STATE_PLAYING)) {
            //多线程NPE避免
            IAgoraKTVSyncProcess syncProcessHelper = this.syncProcessHelper;
            if (syncProcessHelper != null) {
                syncProcessHelper.onStartPlay();
            }
            onStartPlay();
        } else if (mediaPlayerState.equals(PLAYER_STATE_STOPPED) || mediaPlayerState.equals(PLAYER_STATE_OPEN_COMPLETED)) {
            mHasOnStart = false;
        }

        //多线程NPE避免
        IAgoraKTVSyncProcess syncProcessHelper = this.syncProcessHelper;

        if (mRtcEngine != null && mRole == KTV_ROLE_LEADER_SINGER && mMediaPlayer != null) {
            AgoraKTVStreamMessage streamMessage = new AgoraKTVStreamMessage();
            streamMessage.type = AgoraKTVStreamMessage.TYPE_PLAY_STATUS;
            if (syncProcessHelper != null) {
                streamMessage.currentTimeStamp = String.valueOf(syncProcessHelper.getNtpTime());
            }
            streamMessage.currentDuration = String.valueOf(mMediaPlayer.getPlayPosition());
            if (audioDuration <= 0) {
                audioDuration = mMediaPlayer.getDuration();
            }
            streamMessage.audioDelay = String.valueOf(audioDelay);
            streamMessage.audioUniId = audioUniId;
            streamMessage.duration = String.valueOf(audioDuration);
            streamMessage.playerState = String.valueOf(io.agora.mediaplayer.Constants.MediaPlayerState.getValue(mediaPlayerState));
            // TODO 把streamMessage 转成jsonString
            String data = GsonUtils.entityToJson(streamMessage);
            if (data != null) {
                mRtcEngine.sendStreamMessage(steamId, data.getBytes(StandardCharsets.UTF_8));
            }
        }

        this.mMediaPlayerState = mediaPlayerState;
        if (syncProcessHelper != null){
            syncProcessHelper.onPlayerStateChanged(mediaPlayerState, mediaPlayerError);
        }

    }

    @Override
    public void onPositionChanged(long l) {
        //多线程NPE避免
        IAgoraKTVSyncProcess syncProcessHelper = this.syncProcessHelper;
        if (syncProcessHelper != null){
            syncProcessHelper.onPositionChanged(l);
        }
        if (!isDelayLocalSendPosition || mRole == KTV_ROLE_LEADER_SINGER
                || (mRole == KTV_ROLE_ACCOMPANY_SINGER
                && (!isAccompanyDelayPositionChange || Math.abs(l - currentAudioPosition) < 1000))) {
            isDelayLocalSendPosition = false;
            currentAudioPosition = l;
            if (null != eventHandler) {
                // TODO 外部设置这个转调
                eventHandler.onPositionChanged(l, audioUniId);
                // audioPlayerCallBack.onAudioPositionChanged("", currentAudioPosition, audioUniId, false);
                //audioPlayerCallBack.onAudioPositionChanged(uid, currentAudioPosition, audioUniId, false);
            }
            if (mRtcEngine != null && mRole == KTV_ROLE_LEADER_SINGER) {
                AgoraKTVStreamMessage streamMessage = new AgoraKTVStreamMessage();
                streamMessage.type = AgoraKTVStreamMessage.TYPE_PLAY_STATUS;
                if (syncProcessHelper != null) {
                    streamMessage.currentTimeStamp = String.valueOf(syncProcessHelper.getNtpTime());
                }
                streamMessage.currentDuration = String.valueOf(l - audioDelay);
                streamMessage.audioDelay = String.valueOf(audioDelay);
                streamMessage.audioUniId = audioUniId;
                streamMessage.duration = String.valueOf(audioDuration);
                streamMessage.playerState = String.valueOf(io.agora.mediaplayer.Constants.MediaPlayerState.getValue(mMediaPlayerState));
                String data = GsonUtils.entityToJson(streamMessage);
                if (data != null)
                    mRtcEngine.sendStreamMessage(syncSteamId, data.getBytes(StandardCharsets.UTF_8));
            }
        }

    }

    @Override
    public void onPlayerEvent(io.agora.mediaplayer.Constants.MediaPlayerEvent mediaPlayerEvent, long elapsedTime, String message) {

    }


    @Override
    public void onMetaData(io.agora.mediaplayer.Constants.MediaPlayerMetadataType mediaPlayerMetadataType, byte[] bytes) {

    }

    @Override
    public void onPlayBufferUpdated(long l) {

    }

    @Override
    public void onPreloadEvent(String src, io.agora.mediaplayer.Constants.MediaPlayerPreloadEvent event) {

    }

    @Override
    public void onCompleted() {

    }

    @Override
    public void onAgoraCDNTokenWillExpire() {

    }

    @Override
    public void onPlayerSrcInfoChanged(SrcInfo from, SrcInfo to) {

    }

    @Override
    public void onPlayerInfoUpdated(PlayerUpdatedInfo info) {

    }

    @Override
    public void onAudioVolumeIndication(int volume) {

    }

    // ================== onStreamMessage ==================
    public boolean syncChorusProcess(int uid, int streamId, AgoraKTVStreamMessage msg) {
        //多线程NPE避免
        IAgoraKTVSyncProcess syncProcessHelper = this.syncProcessHelper;
        if (syncProcessHelper != null) {
            return syncProcessHelper.onStreamMessage(uid, streamId, msg);
        }
        return false;
    }

    // ================== 声音卡片 ===================
    private IMediaPlayer audioCardMediaPlayer;

    private int audioMixingVolume = 100;
    private int effectVolume = 100;
    private int playbackVolume = 100;

    @Override
    public void adjustAudioMixingVolume(int volume) {
        if (mRtcEngine == null) return;
        this.audioMixingVolume = volume;
        mRtcEngine.adjustAudioMixingVolume(volume);
    }

    @Override
    public void setEffectVolume(int soundID, int volume) {
        if (mRtcEngine == null) return;
        this.effectVolume = volume;
        mRtcEngine.setVolumeOfEffect(soundID, volume);
    }

    @Override
    public void setPlaybackSignalVolume(int playbackSignalVolume) {
        if (mRtcEngine == null) {
            return;
        }
        if (playbackSignalVolume >= 100) {
            mRtcEngine.adjustPlaybackSignalVolume(100);
            playbackVolume = 100;
        } else {
            mRtcEngine.adjustPlaybackSignalVolume(playbackSignalVolume);
            playbackVolume = playbackSignalVolume;
        }
    }

    @Override
    public int getPlaybackVolume() {
        return playbackVolume;
    }

    public boolean isRecording = false;

    // 开始录制声音卡片
    @Override
    public void startAudioCardRecording(String filePath) {
        if (mRtcEngine == null) return;
        isRecording = true;
        mRtcEngine.muteRecordingSignal(false); // 打开录制声音
        mRtcEngine.enableLocalAudio(false); //关闭本地音频
        mRtcEngine.muteAllRemoteAudioStreams(true); // 不订阅远端音频

        // TODO
        if (mMediaPlayer != null) {
            mMediaPlayer.adjustPlayoutVolume(0); // mpk本地播放音量调为0
        }
        if (audioCardMediaPlayer != null) {
            audioCardMediaPlayer.adjustPlayoutVolume(0); // mpk本地播放音量调为0
        }

        mRtcEngine.startRecordingDeviceTest(100); //打开本地录制mic，此时音频裸数据有回调

        AudioRecordingConfiguration config = new AudioRecordingConfiguration();
        config.filePath = filePath;
        config.codec = true;
        config.sampleRate = 48000;
        config.fileRecordOption = AUDIO_FILE_RECORDING_PLAYBACK;  //注意此处需要设置为AUDIO_FILE_RECORDING_PLAYBACK 确保录制的是mic声音
        mRtcEngine.startAudioRecording(config); //客户app进行录制， 录制参数参数如上
    }

    // 结束录制声音卡片
    @Override
    public void stopAudioCardRecording() {
        if (mRtcEngine == null) return;

        mRtcEngine.stopAudioRecording(); //客户app停止录制
        mRtcEngine.stopRecordingDeviceTest(); //关闭本地录制mic

        mRtcEngine.muteRecordingSignal(mEnableMic);
        mRtcEngine.enableLocalAudio(true);

        mRtcEngine.muteAllRemoteAudioStreams(false); //  订阅远端音频

        if (mMediaPlayer != null) {
            mMediaPlayer.adjustPlayoutVolume(playoutVolume); // 恢复mpk本地播放音量调 ？？ Bob: 是不是
        }
        if (audioCardMediaPlayer != null) {
            audioCardMediaPlayer.adjustPlayoutVolume(100);
        }
        isRecording = false;
    }

    // 播放录制
    @Override
    public void startAudioCardPlayback(String filePath) {
        if (mRtcEngine == null) return;
        if (audioCardMediaPlayer == null) {
            audioCardMediaPlayer = mRtcEngine.createMediaPlayer(); // 创建 mpk instance
            audioCardMediaPlayer.registerPlayerObserver(new IMediaPlayerObserver() {
                @Override
                public void onPlayerStateChanged(io.agora.mediaplayer.Constants.MediaPlayerState state, io.agora.mediaplayer.Constants.MediaPlayerError error) {
                    if (state == io.agora.mediaplayer.Constants.MediaPlayerState.PLAYER_STATE_OPEN_COMPLETED) {
                        audioCardMediaPlayer.play();
                    }
                }

                @Override
                public void onPositionChanged(long position_ms) {

                }

                @Override
                public void onPlayerEvent(io.agora.mediaplayer.Constants.MediaPlayerEvent eventCode, long elapsedTime, String message) {

                }

                @Override
                public void onMetaData(io.agora.mediaplayer.Constants.MediaPlayerMetadataType type, byte[] data) {

                }

                @Override
                public void onPlayBufferUpdated(long playCachedBuffer) {

                }

                @Override
                public void onPreloadEvent(String src, io.agora.mediaplayer.Constants.MediaPlayerPreloadEvent event) {

                }

                @Override
                public void onCompleted() {

                }

                @Override
                public void onAgoraCDNTokenWillExpire() {

                }

                @Override
                public void onPlayerSrcInfoChanged(SrcInfo from, SrcInfo to) {

                }

                @Override
                public void onPlayerInfoUpdated(PlayerUpdatedInfo info) {

                }

                @Override
                public void onAudioVolumeIndication(int volume) {

                }
            }); // 注册回调
        }
        audioCardMediaPlayer.open(filePath, 0); // 打开录制的文件

        mRtcEngine.adjustPlaybackSignalVolume(20);  // 使用封装层接口 设置远端的播放音量，降低20%
        mRtcEngine.adjustAudioMixingPlayoutVolume(20);  // 如果startAudioMixing场景，可以调整音量
        mRtcEngine.setEffectsVolume(20); // 如果有playEffect场景，可以调整音量

        if (mMediaPlayer != null) {
            mMediaPlayer.adjustPlayoutVolume(20); // 如果有其他mpk场景，可以调整音量。 预期bridge以外，没有其他mpk了
        }
    }

    // 暂停播放
    @Override
    public void pauseAudioCardPlayback() {
        if (mRtcEngine == null) return;
        if (audioCardMediaPlayer != null) {
            audioCardMediaPlayer.pause();
        }
        mRtcEngine.adjustPlaybackSignalVolume(playbackVolume);  // 恢复远端用户播放音量
        mRtcEngine.adjustAudioMixingPlayoutVolume(audioMixingVolume);      // 如果startAudioMixing场景，可以调整音量
        mRtcEngine.setEffectsVolume(effectVolume);  // 如果有playEffect场景，可以调整音量

        if (mMediaPlayer != null) {
            mMediaPlayer.adjustPlayoutVolume(playoutVolume); // 如果有其他mpk场景，可以调整音量，预期bridge以外，没有其他mpk了
        }
    }

    // 恢复播放
    @Override
    public void resumeAudioCardPlayback() {
        if (mRtcEngine == null) return;
        if (audioCardMediaPlayer != null) {
            audioCardMediaPlayer.resume();
        }

        mRtcEngine.adjustPlaybackSignalVolume(20);  // 使用封装层接口 设置远端的播放音量，降低20%
        mRtcEngine.adjustAudioMixingPlayoutVolume(20);  // 如果startAudioMixing场景，可以调整音量
        mRtcEngine.setEffectsVolume(20); // 如果有playEffect场景，可以调整音量

        if (mMediaPlayer != null) {
            mMediaPlayer.adjustPlayoutVolume(20); // 如果有其他mpk场景，可以调整音量。 预期bridge以外，没有其他mpk了
        }
    }

    // 停止播放
    @Override
    public void stopAudioCardPlayback() {
        if (mRtcEngine == null) return;
        if (audioCardMediaPlayer != null) {
            audioCardMediaPlayer.stop();
        }

        mRtcEngine.adjustPlaybackSignalVolume(playbackVolume);  // 恢复远端用户播放音量

        mRtcEngine.adjustAudioMixingPlayoutVolume(audioMixingVolume);      // 如果startAudioMixing场景，可以调整音量

        mRtcEngine.setEffectsVolume(effectVolume);  // 如果有playEffect场景，可以调整音量

        if (mMediaPlayer != null) {
            mMediaPlayer.adjustPlayoutVolume(playoutVolume); // 如果有其他mpk场景，可以调整音量，预期bridge以外，没有其他mpk了
        }
    }
}