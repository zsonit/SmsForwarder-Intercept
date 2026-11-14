package cn.ppps.forwarder.utils.sender

import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import cn.ppps.forwarder.App
import cn.ppps.forwarder.R
import cn.ppps.forwarder.database.entity.Rule
import cn.ppps.forwarder.entity.MsgInfo
import cn.ppps.forwarder.entity.setting.SmsSetting
import cn.ppps.forwarder.utils.Log
import cn.ppps.forwarder.utils.PhoneUtils
import cn.ppps.forwarder.utils.SendUtils
import cn.ppps.forwarder.utils.SettingUtils
import com.xuexiang.xutil.XUtil
import com.xuexiang.xutil.net.NetworkUtils
import com.xuexiang.xutil.resource.ResUtils.getString

class SmsUtils {
    companion object {

        private val TAG: String = SmsUtils::class.java.simpleName

        //启用时机：0=始终启用、1=仅当无网络时启用、2=自动降级
        const val FALLBACK_ALWAYS = 0
        const val FALLBACK_ONLY_NO_NETWORK = 1
        const val FALLBACK_AUTO = 2

        /**
         * 判断当前是否真实可用网络（有网络连接 且 能 ping 通外网）
         */
        fun isNetworkReallyAvailable(): Boolean {
            return try {
                NetworkUtils.isHaveInternet() && NetworkUtils.isAvailableByPing()
            } catch (e: Exception) {
                Log.e(TAG, "isNetworkReallyAvailable error: ${e.message}")
                false
            }
        }

        fun sendMsg(
            setting: SmsSetting,
            msgInfo: MsgInfo,
            rule: Rule? = null,
            senderIndex: Int = 0,
            logId: Long = 0L,
            msgId: Long = 0L,
            forceSend: Boolean = false
        ) {
            when (setting.getEffectiveFallbackMode()) {
                //仅当无网络时启用：有真实可用网络就跳过本通道
                FALLBACK_ONLY_NO_NETWORK -> if (isNetworkReallyAvailable()) {
                    Log.d(TAG, "仅当无网络时启用：当前有网络，跳过短信通道")
                    SendUtils.updateLogs(logId, 0, getString(R.string.OnlyNoNetwork))
                    SendUtils.senderLogic(0, msgInfo, rule, senderIndex, msgId)
                    return
                }

                //自动降级：有网络时先不发，待命；由 SendWorker 在其他通道全部失败后强制补发
                FALLBACK_AUTO -> if (isNetworkReallyAvailable() && !forceSend) {
                    Log.d(TAG, "自动降级：当前有网络，短信通道待命（logId=$logId）")
                    SendUtils.updateLogs(logId, 1, getString(R.string.SmsAutoStandby))
                    SendUtils.senderLogic(0, msgInfo, rule, senderIndex, msgId)
                    return
                }
            }

            if (ActivityCompat.checkSelfPermission(XUtil.getContext(), Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
                SendUtils.updateLogs(logId, 0, getString(R.string.no_sms_sending_permission))
                SendUtils.senderLogic(0, msgInfo, rule, senderIndex, msgId)
                return
            }

            val content: String = if (rule != null) {
                msgInfo.getContentForSend(rule.smsTemplate, rule.regexReplace, rule.title)
            } else {
                msgInfo.getContentForSend(SettingUtils.smsTemplate)
            }

            //【注意】判断卡槽配置：0=原进原出、1=卡槽1、2=卡槽2
            val simSlotIndex = if (setting.simSlot == 0) msgInfo.simSlot else setting.simSlot - 1

            //获取卡槽信息
            if (App.SimInfoList.isEmpty()) {
                App.SimInfoList = PhoneUtils.getSimMultiInfo()
            }
            Log.d(TAG, App.SimInfoList.toString())

            //替换 {{来源号码}} 标签
            val mobiles = setting.mobiles.replace(getString(R.string.tag_from), msgInfo.from)

            //TODO：取不到卡槽信息时，采用默认卡槽发送
            val mSubscriptionId: Int = App.SimInfoList[simSlotIndex]?.mSubscriptionId ?: -1
            val res: String? = PhoneUtils.sendSms(mSubscriptionId, mobiles, content)
            if (res == null) {
                SendUtils.updateLogs(logId, 2, getString(R.string.request_succeeded))
                SendUtils.senderLogic(2, msgInfo, rule, senderIndex, msgId)
            } else {
                SendUtils.updateLogs(logId, 0, res)
                SendUtils.senderLogic(0, msgInfo, rule, senderIndex, msgId)
            }
        }

    }
}
