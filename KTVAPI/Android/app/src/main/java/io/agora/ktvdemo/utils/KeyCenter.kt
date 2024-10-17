package io.agora.ktvdemo.utils

object KeyCenter {

    /*
     * 主唱 uid，每个频道只能有一个主唱所以我们固定了主唱的 uid
     */
    const val LeadSingerUid = 2024

    val songCode: Long  get() = if (isMccEx) 40289835 else 6625526605291650
    val songCode2: Long get() = if (isMccEx) 89488966 else 6654550265524810

    /*
     * 加入的频道名
     */
    var channelId: String = ""

    /*
     * 自己的 uid
     */
    var localUid: Int = 2024

    /*
     * 选择的歌曲类型
     */
    var isMcc: Boolean = true

    /*
     * 体验 KTVAPI 的类型， true为普通合唱、false为大合唱
     */
    var isNormalChorus: Boolean = true

    /*
     * 当前演唱中的身份
     */
    var isBroadcaster: Boolean = false

    /**
     * 是否是 MCC EX
     */
    var isMccEx: Boolean = false
}