package cn.ppps.forwarder.entity.setting

import cn.ppps.forwarder.R
import java.io.Serializable

data class SmsSetting(
    var simSlot: Int = 0,
    var mobiles: String = "",
    var onlyNoNetwork: Boolean = false,
    // 启用时机：0=始终启用（默认）、1=仅当无网络时启用、2=自动降级（有网络时网络通道优先，失败或无网络才用短信）
    var fallbackMode: Int = 0,
) : Serializable {

    /**
     * 兼容旧版本数据：老版本只有 onlyNoNetwork 开关，没有 fallbackMode 字段。
     * 旧数据 onlyNoNetwork=true 等价于 fallbackMode=1。
     */
    fun getEffectiveFallbackMode(): Int = when {
        fallbackMode == 2 -> 2
        fallbackMode == 1 -> 1
        fallbackMode == 0 && onlyNoNetwork -> 1
        else -> 0
    }

    fun getSmsSimSlotCheckId(): Int {
        return when (simSlot) {
            1 -> R.id.rb_sim_slot_1
            2 -> R.id.rb_sim_slot_2
            else -> R.id.rb_sim_slot_org
        }
    }

    fun getSmsFallbackModeCheckId(): Int {
        return when (getEffectiveFallbackMode()) {
            1 -> R.id.rb_fallback_no_network
            2 -> R.id.rb_fallback_auto
            else -> R.id.rb_fallback_always
        }
    }
}
