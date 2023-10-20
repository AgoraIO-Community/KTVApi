//
//  AudioSettings.swift
//  AgoraEntScenarios
//
//  Created by CP on 2023/10/20.
//

import Foundation
import AgoraRtcKit

/**
 
 场景类型
 @param KTV K歌房
 @param ChatRoom 语聊房 */
 enum SceneType: Int {
     case KTV = 0
     case ChatRoom = 1
 }
/**
 
 虚拟声卡类型
 
 @param NanShengCiXing 男生磁性
 
 @param NvShengCiXing 女生磁性
 
 @param NanShengTianMei 男生甜美 延迟大(60ms) 男声甜美在k歌场景下会被映射到男生唱歌
 
 @param NvShengTianMei 女生甜美 延迟大(60ms) 女生甜美在k歌场景下会被映射到女生唱歌
 
 @param NanShengChangGe 男生唱歌
 
 @param NvShengChangGe 女生唱歌
 
 @param Close 关
 */
enum SoundCardType {
    case NanShengCiXing
    case NvShengCiXing
    case NanShengTianMei
    case NvShengTianMei
    case NanShengChangGe
    case NvShengChangGe
    case Close
    
    var gainValue: Float {
        switch self {
        case .NanShengCiXing, .NvShengCiXing, .NanShengTianMei, .NvShengTianMei, .NanShengChangGe, .NvShengChangGe:
            return 1.0
        case .Close:
            return -1.0
        }
    }
    
    var presetValue: Int {
        switch self {
        case .NanShengCiXing, .NvShengCiXing, .NanShengTianMei, .NvShengTianMei, .NanShengChangGe, .NvShengChangGe:
            return 4
        case .Close:
            return -1
        }
    }
    
    var gender: Int {
        switch self {
        case .NanShengCiXing, .NanShengTianMei, .NanShengChangGe:
            return 0
        case .NvShengCiXing, .NvShengTianMei, .NvShengChangGe:
            return 1
        case .Close:
            return -1
        }
    }
    
    var effect: Int {
        switch self {
        case .NanShengTianMei, .NvShengTianMei:
            return 1
        case .NanShengChangGe, .NvShengChangGe:
            return 2
        case .NanShengCiXing, .NvShengCiXing, .Close:
            return 0
        }
    }
}

/**
 
 插入耳机的类型
 
 @param EQ0 针对小米系列有线耳机
 
 @param EQ1 针对Sony系列有线耳机
 
 @param EQ2 针对JBL系列有线耳机
 
 @param EQ3 针对华为系列有线耳机
 
 @param EQ4 针对iphone系列有线耳机(默认)
 */
enum EarPhoneType {
    case EQ0
    case EQ1
    case EQ2
    case EQ3
    case EQ4
    
    var presetValue: Int {
        switch self {
        case .EQ0:
            return 0
        case .EQ1:
            return 1
        case .EQ2:
            return 2
        case .EQ3:
            return 3
        case .EQ4:
            return 4
        }
    }
}

class AudioSettings {
    static var enabled: Bool = false
    
    private let rtcEngine: AgoraRtcEngineKit
    
    init(rtcEngine: AgoraRtcEngineKit) {
        self.rtcEngine = rtcEngine
    }
    
    func enableVirtualSoundCard(sceneType: SceneType, soundCardType: SoundCardType, earPhoneType: EarPhoneType?) {
        AudioSettings.enabled = soundCardType != .Close
        setVirtualSoundCardType(sceneType: sceneType, soundCardType: soundCardType, earPhoneType: earPhoneType)
        if sceneType == .KTV {
            rtcEngine.setParameters("{\"che.audio.agc.enable\": \(!AudioSettings.enabled)}")
        }
    }
    
    func enableAGC(enable: Bool) {
        rtcEngine.setParameters("{\"che.audio.agc.enable\": \(enable)}")
    }
    
    private func setVirtualSoundCardType(sceneType: SceneType, soundCardType: SoundCardType, earPhoneType: EarPhoneType?) {
        if sceneType == .KTV && soundCardType == .NanShengTianMei {
            setVirtualSoundCardType(sceneType: .ChatRoom, soundCardType: .NanShengChangGe, earPhoneType: earPhoneType)
            return
        }
        
        if sceneType == .KTV && soundCardType == .NvShengTianMei {
            setVirtualSoundCardType(sceneType: .ChatRoom, soundCardType: .NvShengChangGe, earPhoneType: earPhoneType)
            return
        }
        
        if let earPhoneType = earPhoneType {
            if soundCardType != .Close {
                rtcEngine.setParameters("{\"che.audio.virtual_soundcard\":{\"preset\":\(earPhoneType.presetValue),\"gain\":\(soundCardType.gainValue),\"gender\":\(soundCardType.gender),\"effect\":\(soundCardType.effect)}}")
                return
            }
        }
        
        rtcEngine.setParameters("{\"che.audio.virtual_soundcard\":{\"preset\":\(soundCardType.presetValue),\"gain\":\(soundCardType.gainValue),\"gender\":\(soundCardType.gender),\"effect\":\(soundCardType.effect)}}")
    }
}
