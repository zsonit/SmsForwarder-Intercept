package cn.ppps.forwarder.utils

import android.content.Context
import android.provider.Telephony
import com.xuexiang.xrouter.utils.TextUtils

//短信拦截规则工具：命中号码黑名单或关键词黑名单的短信不再转发
object SmsInterceptUtils {

    private const val TAG: String = "SmsInterceptUtils"

    //是否命中拦截规则
    fun isIntercepted(from: String, content: String): Boolean {
        if (!SettingUtils.enableSmsIntercept) return false

        //号码黑名单：逗号/分号分隔（兼容中英文），发件人号码以任一拦截号码结尾即命中（便于匹配含+86前缀的号码）
        if (!TextUtils.isEmpty(SettingUtils.smsInterceptPhones)) {
            val phones = SettingUtils.smsInterceptPhones
                .replace(";", ",").replace("；", ",").replace("，", ",").replace("\n", ",")
            for (phone in phones.split(",")) {
                val number = phone.trim()
                if (!TextUtils.isEmpty(number) && from.endsWith(number)) {
                    Log.i(TAG, "命中号码拦截规则：from = $from, interceptPhone = $number")
                    return true
                }
            }
        }

        //关键词黑名单：一行一个关键词，支持正则表达式，命中内容任意一项即返回 true
        if (!TextUtils.isEmpty(SettingUtils.smsInterceptKeywords)) {
            for (line in SettingUtils.smsInterceptKeywords.split("\n")) {
                val keyword = line.trim()
                if (keyword.isEmpty()) continue
                try {
                    val regex = Regex(keyword)
                    if (regex.containsMatchIn(content)) {
                        Log.i(TAG, "命中关键词拦截规则：keyword = $keyword, content = $content")
                        return true
                    }
                } catch (e: Exception) {
                    //正则表达式非法时，降级为普通包含匹配
                    Log.w(TAG, "拦截关键词正则非法，降级为包含匹配：$keyword, ${e.message}")
                    if (content.contains(keyword)) {
                        Log.i(TAG, "命中关键词拦截规则：keyword = $keyword, content = $content")
                        return true
                    }
                }
            }
        }

        return false
    }

    //从系统短信收件箱删除指定短信（安卓4.4+要求本应用为默认短信应用，否则删除失败）
    fun deleteSmsFromInbox(context: Context, from: String, content: String): Int {
        return try {
            val uri = Telephony.Sms.Inbox.CONTENT_URI
            val selection = "${Telephony.Sms.ADDRESS} = ? AND ${Telephony.Sms.BODY} = ?"
            val deletedCount = context.contentResolver.delete(uri, selection, arrayOf(from, content))
            Log.i(TAG, "删除本地短信：from = $from, deletedCount = $deletedCount")
            deletedCount
        } catch (e: Exception) {
            Log.e(TAG, "删除本地短信失败（需将本应用设为默认短信应用）：" + e.message.toString())
            0
        }
    }
}
