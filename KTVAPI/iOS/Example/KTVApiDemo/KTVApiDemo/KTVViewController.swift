//
//  KTVViewController.swift
//  KTVApiDemo
//
//  Created by CP on 2023/8/8.
//

import UIKit
import Foundation
import AgoraRtcKit
import SVProgressHUD
public enum LoadMusicType: Int {
    case mcc //声网歌曲中心
    case local //本地音乐
}

class KTVViewController: UIViewController {

    var role: KTVSingRole = .audience
    var channelName: String = ""
    var type: LoadMusicType = .mcc
    var rtcKit: AgoraRtcEngineKit!
    var rtcDataStreamId: Int = 0
    var ktvApi: KTVApiImpl!
    var rtcToken: String?
    var rtmToken: String?
    var rtcPlayerToken: String?
    var userId: Int = 0
    
    let mainSingerId = 1000
    let coSingerId = 2000
    let audienceId = 3000
    
    let mccSongCode = 6625526605291650
    
    var lyricView: KTVLyricView!
    
    let loadMusicBtn: UIButton = UIButton()
    let cancelMusicBtn: UIButton = UIButton()
    
    let joinChorusBtn: UIButton = UIButton()
    let leaveChorusBtn: UIButton = UIButton()
    
    let oriBtn: UIButton = UIButton()
    let accBtn: UIButton = UIButton()
    let leadBtn: UIButton = UIButton()

    let muteBtn: UIButton = UIButton()
    let unmuteBtn: UIButton = UIButton()
    
    let musicLoadProLabel: UILabel = UILabel()
    let roleLabel: UILabel = UILabel()

    private var loadMusicCallBack:((Bool, String)->Void)?
    
    override func viewDidLoad() {
        super.viewDidLoad()

        self.view.backgroundColor = .white
        self.title = "KTV online"
        
        if role == .leadSinger {
            userId = mainSingerId
        } else if role == .coSinger {
            userId = coSingerId
        } else {
            userId = audienceId
        }
        
        /*
         1.加载RTC
         2.初始化KTV API
         3.切换角色
         4.加载歌曲或者歌词
         */
        
        layoutUI()
        joinRTCChannel()
        loadKTVApi()

    }
    
    override func viewWillDisappear(_ animated: Bool) {
        super.viewWillDisappear(animated)
        leaveChannel()
    }
    
    private func layoutUI() {
        lyricView = KTVLyricView(frame: CGRect(x: 50, y: 60, width: UIScreen.main.bounds.size.width - 100, height: 200))
        view.addSubview(lyricView)
        
        //加载歌曲
        loadMusicBtn.frame = CGRect(x: 10, y: 270, width: 80, height: 40)
        loadMusicBtn.backgroundColor = .gray
        loadMusicBtn.setTitle("加载歌曲", for: .normal)
        loadMusicBtn.titleLabel?.font = UIFont.systemFont(ofSize: 13)
        loadMusicBtn.addTarget(self, action: #selector(loadSong), for: .touchUpInside)
        view.addSubview(loadMusicBtn)
        
        //溢出歌曲
        cancelMusicBtn.frame = CGRect(x: 100, y: 270, width: 80, height: 40)
        cancelMusicBtn.backgroundColor = .gray
        cancelMusicBtn.setTitle("移除歌曲", for: .normal)
        cancelMusicBtn.titleLabel?.font = UIFont.systemFont(ofSize: 13)
        cancelMusicBtn.addTarget(self, action: #selector(cancelSong), for: .touchUpInside)
        view.addSubview(cancelMusicBtn)
        
        musicLoadProLabel.frame = CGRect(x: 200, y: 270, width: 60, height: 40)
        musicLoadProLabel.backgroundColor = .gray
        musicLoadProLabel.font = UIFont.systemFont(ofSize: 13)
        musicLoadProLabel.textColor = .white
        musicLoadProLabel.textAlignment = .center
        view.addSubview(musicLoadProLabel)
        musicLoadProLabel.isHidden = true
        
        //加入合唱
        joinChorusBtn.frame = CGRect(x: 10, y: 320, width: 80, height: 40)
        joinChorusBtn.backgroundColor = .gray
        joinChorusBtn.setTitle("加入合唱", for: .normal)
        joinChorusBtn.titleLabel?.font = UIFont.systemFont(ofSize: 13)
        joinChorusBtn.addTarget(self, action: #selector(joinChorus), for: .touchUpInside)
        view.addSubview(joinChorusBtn)
        
        //离开合唱
        leaveChorusBtn.frame = CGRect(x: 100, y: 320, width: 80, height: 40)
        leaveChorusBtn.backgroundColor = .gray
        leaveChorusBtn.setTitle("离开合唱", for: .normal)
        leaveChorusBtn.titleLabel?.font = UIFont.systemFont(ofSize: 13)
        leaveChorusBtn.addTarget(self, action: #selector(leaveChorus), for: .touchUpInside)
        view.addSubview(leaveChorusBtn)
        
        roleLabel.frame = CGRect(x: 200, y: 320, width: 60, height: 40)
        roleLabel.backgroundColor = .gray
        roleLabel.textColor = .white
        roleLabel.textAlignment = .center
        roleLabel.font = UIFont.systemFont(ofSize: 13)
        view.addSubview(roleLabel)
        roleLabel.text = getRoleName(with: role)
        
        //原唱
        oriBtn.frame = CGRect(x: 10, y: 370, width: 80, height: 40)
        oriBtn.backgroundColor = .gray
        oriBtn.setTitle("原唱", for: .normal)
        oriBtn.addTarget(self, action: #selector(oriSing), for: .touchUpInside)
        oriBtn.titleLabel?.font = UIFont.systemFont(ofSize: 13)
        view.addSubview(oriBtn)
        
        //伴奏
        accBtn.frame = CGRect(x: 100, y: 370, width: 80, height: 40)
        accBtn.backgroundColor = .gray
        accBtn.setTitle("伴奏", for: .normal)
        accBtn.addTarget(self, action: #selector(accSing), for: .touchUpInside)
        accBtn.titleLabel?.font = UIFont.systemFont(ofSize: 13)
        view.addSubview(accBtn)
        
        //开麦
        muteBtn.frame = CGRect(x: 10, y: 420, width: 80, height: 40)
        muteBtn.backgroundColor = .gray
        muteBtn.setTitle("开麦", for: .normal)
        muteBtn.addTarget(self, action: #selector(mute), for: .touchUpInside)
        muteBtn.titleLabel?.font = UIFont.systemFont(ofSize: 13)
        view.addSubview(muteBtn)
        
        //关麦
        unmuteBtn.frame = CGRect(x: 100, y: 420, width: 80, height: 40)
        unmuteBtn.backgroundColor = .gray
        unmuteBtn.setTitle("关麦", for: .normal)
        unmuteBtn.addTarget(self, action: #selector(unmute), for: .touchUpInside)
        unmuteBtn.titleLabel?.font = UIFont.systemFont(ofSize: 13)
        view.addSubview(unmuteBtn)

    }
    
    private func joinRTCChannel() {
        rtcKit = AgoraRtcEngineKit.sharedEngine(withAppId: KeyCenter.AppId, delegate: self)
        rtcKit.setAudioProfile(.musicHighQuality)
        rtcKit.setAudioScenario(.gameStreaming)
        rtcKit.setChannelProfile(.liveBroadcasting)
        rtcKit.enableAudioVolumeIndication(50, smooth: 10, reportVad: true)
        rtcKit.enableAudio()
        rtcKit.setEnableSpeakerphone(true)
        
        let config = AgoraDataStreamConfig.init()
        config.ordered = false
        config.syncWithAudio = false
        rtcKit.createDataStream(&rtcDataStreamId, config: config)
        rtcKit.setClientRole(role == .audience ? .audience : .broadcaster)

    }
    
    private func loadKTVApi() {
        getMccData(with: "\(userId)") {[weak self] rtcToken, rtmToken, rtcPlayerToken in
            guard let self = self else {return}
            self.rtcToken = rtcToken
            self.rtmToken = rtmToken
            self.rtcPlayerToken = rtcPlayerToken
            
            let apiConfig = KTVApiConfig(appId: KeyCenter.AppId, rtmToken: self.type == .mcc ? (self.rtmToken ?? "") : "", engine: self.rtcKit, channelName: self.channelName, localUid: self.userId, chorusChannelName: "\(self.channelName)_ex", chorusChannelToken: self.rtcPlayerToken ?? "", type: .normal, maxCacheSize: 10, musicType: self.type == .mcc ? .mcc : .local, isDebugMode: false)
            self.ktvApi = KTVApiImpl(config: apiConfig)
            self.ktvApi.renewInnerDataStreamId()
            self.ktvApi.setLrcView(view: self.lyricView)
            self.ktvApi.addEventHandler(ktvApiEventHandler: self)
            
            self.rtcKit.joinChannel(byToken: KeyCenter.Token, channelId: self.channelName, uid: UInt(self.userId), mediaOptions: self.mediaOptions())
            self.loadMusic()
        }
    }
    
    private func mediaOptions() -> AgoraRtcChannelMediaOptions {
        let options = AgoraRtcChannelMediaOptions()
        options.clientRoleType = role != .audience ? .broadcaster : .audience
        options.publishMicrophoneTrack = role != .audience ? true : false
        options.publishCustomAudioTrack = false
        options.channelProfile = .liveBroadcasting
        options.autoSubscribeAudio = true
        if type == .mcc {
            options.publishMediaPlayerId = Int(ktvApi.getMusicPlayer()?.getMediaPlayerId() ?? 0)
        }
        options.enableAudioRecordingOrPlayout = true
        return options
    }
    
    
    
    private func switchRole() {
        ktvApi.switchSingerRole(newRole: role) {[weak self] state, failReason in
            guard let self = self else {return}
            DispatchQueue.main.async {
                self.roleLabel.text = self.getRoleName(with: self.role)
            }
        }
    }
    
    private func getRoleName(with role: KTVSingRole) -> String {
        if role == .leadSinger || role == .soloSinger {
            return "主唱"
        } else if role == .coSinger {
            return "合唱"
        } else {
            return "观众"
        }
    }
    
    private func loadMusic() {
        if type == .local {
            let mUrl = Bundle.main.path(forResource: "成都", ofType: "mp3")!
            let lUrl = Bundle.main.path(forResource: "成都", ofType: "xml")!
            let songConfig = KTVSongConfiguration()
            songConfig.autoPlay = (role == .leadSinger || role == .soloSinger) ? true : false
            songConfig.mode = role == .audience ? .loadNone : .loadMusicOnly
            songConfig.mainSingerUid = mainSingerId
            songConfig.songIdentifier = "chengdu"
            ktvApi.loadMusic(config: songConfig, url: mUrl)
            self.lyricView.resetLrcData(with: lUrl)
            switchRole()
        } else {
            let songConfig = KTVSongConfiguration()
            songConfig.autoPlay = (role == .leadSinger || role == .soloSinger) ? true : false
            songConfig.mode = role == .audience ? .loadLrcOnly : .loadMusicAndLrc
            songConfig.mainSingerUid = mainSingerId
            songConfig.songIdentifier = "\(mccSongCode)"
            ktvApi.loadMusic(songCode: mccSongCode, config: songConfig, onMusicLoadStateListener: self)
            
            self.loadMusicCallBack = {[weak self] flag, songCode in
                guard let self = self else {return}
                switchRole()
            }
        }
    }
    
    private func leaveChannel() {
        ktvApi.cleanCache()
        rtcKit.leaveChannel()
    }
    
    private func getMccData(with userId: String, completion:@escaping ((String, String, String)->Void)) {
        var tokenMap1:[Int: String] = [:], tokenMap2:[Int: String] = [:]
        
        let dispatchGroup = DispatchGroup()
        dispatchGroup.enter()
        NetworkManager.shared.generateTokens(channelName: channelName,
                                             uid: userId,
                                             tokenGeneratorType: .token006,
                                             tokenTypes: [.rtc, .rtm]) { tokenMap in
            tokenMap1 = tokenMap
            dispatchGroup.leave()
        }
        
        dispatchGroup.enter()
        NetworkManager.shared.generateTokens(channelName: "\(channelName)_ex",
                                             uid: userId,
                                             tokenGeneratorType: .token006,
                                             tokenTypes: [.rtc]) { tokenMap in
            tokenMap2 = tokenMap
            dispatchGroup.leave()
        }
        
        dispatchGroup.notify(queue: .main){
           if let rtcToken = tokenMap1[NetworkManager.AgoraTokenType.rtc.rawValue],
            let rtmToken = tokenMap1[NetworkManager.AgoraTokenType.rtm.rawValue],
              let rtcPlayerToken = tokenMap2[NetworkManager.AgoraTokenType.rtc.rawValue] {
               completion(rtcToken, rtmToken, rtcPlayerToken)
           } else {
               print("获取MCC信息失败")
           }
        }
    }
    
}

extension KTVViewController {
    @objc private func loadSong() {
        loadMusic()
    }
    
    @objc private func cancelSong() {
        if role == .audience {
            //观众不能取消歌曲下载
            SVProgressHUD.showInfo(withStatus: "当前身份不支持该操作")
            
        } else if type == .local {
            //本地歌曲不能取消歌曲下载
            SVProgressHUD.showInfo(withStatus: "当前场景不支持该操作")
        } else {
            self.ktvApi.removeMusic(songCode: mccSongCode)
        }
    }
    
    @objc private func joinChorus() {
        if role == .audience {
            role = .coSinger
            loadMusic()
            switchRole()
        } else if role == .leadSinger || role == .soloSinger || role == .coSinger {
            //主唱合唱不能加入
            SVProgressHUD.showInfo(withStatus: "当前身份不支持该操作")
        }
    }

    @objc private func mute() {
        self.ktvApi.muteMic(muteStatus: true)
    }
    
    @objc private func unmute() {
        self.ktvApi.muteMic(muteStatus: false)
    }

}

extension KTVViewController: AgoraRtcEngineDelegate {
    func rtcEngine(_ engine: AgoraRtcEngineKit, didJoinChannel channel: String, withUid uid: UInt, elapsed: Int) {

    }
    
    @objc private func leaveChorus() {
        if role == .coSinger {
            //合唱者才能离开合唱
            self.ktvApi.switchSingerRole(newRole: .audience) { state, reason in
                self.role = .audience
            }
        } else {
            SVProgressHUD.showInfo(withStatus: "当前身份不支持该操作")
        }
    }
    
    @objc private func oriSing() {
        if role == .soloSinger || role == .leadSinger || role == .coSinger {
            //主唱合唱才能原唱
            if role == .soloSinger || role == .leadSinger {
                self.ktvApi.getMusicPlayer()?.selectMultiAudioTrack(0, publishTrackIndex: 0)
            } else {
                self.ktvApi.getMusicPlayer()?.selectAudioTrack(0)
            }
        } else {
            SVProgressHUD.showInfo(withStatus: "当前身份不支持该操作")
        }
    }
    
    @objc private func accSing() {
        if role == .leadSinger || role == .leadSinger || role == .coSinger {
            //主唱合唱才能原唱
            if role == .soloSinger || role == .leadSinger {
                self.ktvApi.getMusicPlayer()?.selectMultiAudioTrack(1, publishTrackIndex: 1)
            } else {
                self.ktvApi.getMusicPlayer()?.selectAudioTrack(1)
            }
        } else {
            SVProgressHUD.showInfo(withStatus: "当前身份不支持该操作")
        }
    }

}

extension KTVViewController: IMusicLoadStateListener {
    func onMusicLoadProgress(songCode: Int, percent: Int, status: AgoraMusicContentCenterPreloadStatus, msg: String?, lyricUrl: String?) {
        //歌曲加载进度
        print("歌曲加载进度:\(percent)%")
        DispatchQueue.main.async {
            self.musicLoadProLabel.text = "\(percent)%"
            self.musicLoadProLabel.isHidden = false
        }
    }
    
    func onMusicLoadSuccess(songCode: Int, lyricUrl: String) {
        if let loadMusicCallBack = self.loadMusicCallBack {
            loadMusicCallBack(true, "\(songCode)")
            self.loadMusicCallBack = nil
        }
        DispatchQueue.main.async {
            self.musicLoadProLabel.isHidden = true
        }
    }
    
    func onMusicLoadFail(songCode: Int, reason: KTVLoadSongFailReason) {
        //歌曲加载失败
        if let loadMusicCallBack = self.loadMusicCallBack {
            loadMusicCallBack(false, "\(songCode)")
            self.loadMusicCallBack = nil
        }
        DispatchQueue.main.async {
            self.musicLoadProLabel.isHidden = true
        }
    }
}

extension KTVViewController: KTVApiEventHandlerDelegate {
    func onMusicPlayerStateChanged(state: AgoraMediaPlayerState, error: AgoraMediaPlayerError, isLocal: Bool) {
        
    }
    
    func onSingingScoreResult(score: Float) {
        
    }
    
    func onSingerRoleChanged(oldRole: KTVSingRole, newRole: KTVSingRole) {
        role = newRole
        DispatchQueue.main.async {
            self.roleLabel.text = self.getRoleName(with: self.role)
        }
    }
    
    func onTokenPrivilegeWillExpire() {
        
    }
    
    func onChorusChannelAudioVolumeIndication(speakers: [AgoraRtcAudioVolumeInfo], totalVolume: Int) {
        
    }
    
    func onMusicPlayerProgressChanged(with progress: Int) {
        
    }
}
