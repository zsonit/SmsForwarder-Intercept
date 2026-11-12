package cn.ppps.forwarder.workers

import android.annotation.SuppressLint
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.google.gson.Gson
import cn.ppps.forwarder.R
import cn.ppps.forwarder.core.Core
import cn.ppps.forwarder.database.entity.Logs
import cn.ppps.forwarder.database.entity.Msg
import cn.ppps.forwarder.database.entity.Rule
import cn.ppps.forwarder.database.entity.SmsIntercepted
import cn.ppps.forwarder.entity.MsgInfo
import cn.ppps.forwarder.entity.TaskSetting
import cn.ppps.forwarder.utils.CHECK_SIM_SLOT_ALL
import cn.ppps.forwarder.utils.DataProvider
import cn.ppps.forwarder.utils.HistoryUtils
import cn.ppps.forwarder.utils.Log
import cn.ppps.forwarder.utils.SendUtils
import cn.ppps.forwarder.utils.SettingUtils
import cn.ppps.forwarder.utils.SmsInterceptUtils
import cn.ppps.forwarder.utils.TASK_CONDITION_APP
import cn.ppps.forwarder.utils.TASK_CONDITION_CALL
import cn.ppps.forwarder.utils.TASK_CONDITION_SMS
import cn.ppps.forwarder.utils.TaskWorker
import cn.ppps.forwarder.utils.Worker
import cn.ppps.forwarder.utils.task.ConditionUtils
import com.xuexiang.xutil.resource.ResUtils
import com.xuexiang.xutil.security.CipherUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Suppress("PrivatePropertyName", "DEPRECATION")
class SendWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    private val TAG: String = SendWorker::class.java.simpleName

    @SuppressLint("SimpleDateFormat")
    override suspend fun doWork(): Result {

        return withContext(Dispatchers.IO) {
            try {
                val msgInfoJson = inputData.getString(Worker.SEND_MSG_INFO)
                if (msgInfoJson.isNullOrBlank()) {
                    return@withContext Result.failure(workDataOf("send" to "msgInfoJson is null"))
                }

                val msgInfo = Gson().fromJson(msgInfoJson, MsgInfo::class.java)
                Log.i(TAG, "开始处理：type=${msgInfo.type}, from=${msgInfo.from}, simSlot=${msgInfo.simSlot}")
                //【注意】卡槽id：-1=获取失败、0=卡槽1、1=卡槽2，但是 Rule 表里存的是 SIM1/SIM2
                val simSlot = "SIM" + (msgInfo.simSlot + 1)

                //短信拦截规则：命中拦截号码或拦截关键词的短信不再转发（不影响自动任务以外的任何流程）
                if (msgInfo.type == "sms" && SmsInterceptUtils.isIntercepted(msgInfo.from, msgInfo.content, msgInfo.simSlot)) {
                    Log.e(TAG, "短信命中拦截规则，不转发：from = ${msgInfo.from}")
                    var interceptResponse = ResUtils.getString(R.string.sms_intercepted)
                    //同时删除手机上的该条短信
                    if (SettingUtils.smsInterceptDelete) {
                        val deletedCount = SmsInterceptUtils.deleteSmsFromInbox(applicationContext, msgInfo.from, msgInfo.content)
                        Log.e(TAG, "删除本地短信：deletedCount = $deletedCount")
                        if (deletedCount > 0) interceptResponse = ResUtils.getString(R.string.sms_intercept_deleted)
                    }
                    if (SettingUtils.enableSmsInterceptLogs) {
                        val msg = Msg(0, msgInfo.type, msgInfo.from, msgInfo.content, msgInfo.simSlot, msgInfo.simInfo, msgInfo.subId, msgInfo.callType)
                        val msgId = Core.msg.insert(msg)
                        val log = Logs(0, msgInfo.type, msgId, 0, 0, 0, interceptResponse)
                        Core.logs.insert(log)
                    }
                    //已拦截短信存档：无论是否记录转发日志，均存入"已拦截"列表便于查看
                    try {
                        Core.smsIntercepted.insert(
                            SmsIntercepted(0, msgInfo.from, msgInfo.content, msgInfo.simInfo, msgInfo.simSlot)
                        )
                        //仅保留最近 24 小时的拦截记录，超时自动清空
                        Core.smsIntercepted.deleteExpired()
                    } catch (e: Exception) {
                        Log.e(TAG, "已拦截短信存档失败：" + e.message.toString())
                    }
                    return@withContext Result.failure(workDataOf("send" to "intercepted"))
                }

                //自动任务处理逻辑
                autoTaskProcess(msgInfo, msgInfoJson, simSlot)

                // 免打扰(禁用转发)时间段
                var isSilentPeriod = false
                Log.d(TAG, "silentPeriodStart = ${SettingUtils.silentPeriodStart}, silentPeriodEnd = ${SettingUtils.silentPeriodEnd}")
                if (SettingUtils.silentPeriodStart != SettingUtils.silentPeriodEnd) {
                    isSilentPeriod = DataProvider.isCurrentTimeInPeriod(SettingUtils.silentPeriodStart, SettingUtils.silentPeriodEnd)
                    Log.d(TAG, "isSilentPeriod = $isSilentPeriod, enableSilentPeriodLogs = ${SettingUtils.enableSilentPeriodLogs}")
                    if (isSilentPeriod && !SettingUtils.enableSilentPeriodLogs) {
                        Log.e(TAG, "免打扰(禁用转发)时间段")
                        return@withContext Result.failure(workDataOf("send" to "failed"))
                    }
                }

                // 过滤重复消息机制
                val duplicateMessagesLimits = SettingUtils.duplicateMessagesLimits * 1000L
                if (duplicateMessagesLimits > 0L) {
                    val key = CipherUtils.md5(msgInfo.type + msgInfo.from + msgInfo.content)
                    val timestamp: Long = System.currentTimeMillis()
                    var timestampPrev: Long by HistoryUtils(key, timestamp)
                    Log.d(TAG, "duplicateMessagesLimits=$duplicateMessagesLimits, timestamp=$timestamp, timestampPrev=$timestampPrev, msgInfo=$msgInfo")
                    if (timestampPrev != timestamp && timestamp - timestampPrev <= duplicateMessagesLimits) {
                        Log.e(TAG, "过滤重复消息机制")
                        timestampPrev = timestamp
                        return@withContext Result.failure(workDataOf("send" to "failed"))
                    }
                    timestampPrev = timestamp
                }

                val ruleList: List<Rule> = Core.rule.getRuleList(msgInfo.type, 1, simSlot)
                if (ruleList.isEmpty()) {
                    Log.e(TAG, "没有启用中的转发规则：type=${msgInfo.type}, simSlot=$simSlot（规则未启用/未勾选短信条件/卡槽不匹配）")
                    return@withContext Result.failure(workDataOf("send" to "failed"))
                }
                Log.i(TAG, "匹配到启用中的规则数量=${ruleList.size}, type=${msgInfo.type}, simSlot=$simSlot, from=${msgInfo.from}")

                val ruleListMatched: MutableList<Rule> = mutableListOf()
                for (rule in ruleList) {
                    Log.d(TAG, rule.toString())
                    if (rule.checkMsg(msgInfo)) ruleListMatched.add(rule)
                }
                if (ruleListMatched.isEmpty()) {
                    Log.e(TAG, "规则条件均未匹配该短信（检查规则里的号码/关键字/卡槽条件）：from=${msgInfo.from}, content=${msgInfo.content}")
                    return@withContext Result.failure(workDataOf("send" to "failed"))
                }
                Log.i(TAG, "命中规则数量=${ruleListMatched.size}，开始发送")

                val msg = Msg(0, msgInfo.type, msgInfo.from, msgInfo.content, msgInfo.simSlot, msgInfo.simInfo, msgInfo.subId, msgInfo.callType)
                val msgId = Core.msg.insert(msg)
                for (rule in ruleListMatched) {
                    val sender = rule.senderList[0]
                    if (isSilentPeriod) {
                        val log = Logs(0, msgInfo.type, msgId, rule.id, sender.id, 0, ResUtils.getString(R.string.silent_time_period))
                        Core.logs.insert(log)
                        continue
                    }
                    val log = Logs(0, msgInfo.type, msgId, rule.id, sender.id)
                    val logId = Core.logs.insert(log)
                    SendUtils.sendMsgSender(msgInfo, rule, 0, logId, msgId)
                }

            } catch (e: Exception) {
                e.printStackTrace()
                Log.e(TAG, "SendWorker error: ${e.message}")
                return@withContext Result.failure(workDataOf("send" to e.message.toString()))
            }

            return@withContext Result.success()
        }
    }

    private fun autoTaskProcess(msgInfo: MsgInfo, msgInfoJson: String, simSlot: String) {
        val conditionType = when (msgInfo.type) {
            "app" -> TASK_CONDITION_APP
            "call" -> TASK_CONDITION_CALL
            else -> TASK_CONDITION_SMS
        }

        val taskList = Core.task.getByType(conditionType)
        for (task in taskList) {
            Log.d(TAG, "task = $task")

            // 根据任务信息执行相应操作
            val conditionList = Gson().fromJson(task.conditions, Array<TaskSetting>::class.java).toMutableList()
            if (conditionList.isEmpty()) {
                Log.d(TAG, "TASK-${task.id}：conditionList is empty")
                continue
            }
            val firstCondition = conditionList.firstOrNull()
            if (firstCondition == null) {
                Log.d(TAG, "TASK-${task.id}：firstCondition is null")
                continue
            }

            val ruleSetting = Gson().fromJson(firstCondition.setting, Rule::class.java)
            if (ruleSetting == null) {
                Log.d(TAG, "TASK-${task.id}：ruleSetting is null")
                continue
            }

            if (ruleSetting.simSlot != CHECK_SIM_SLOT_ALL && simSlot != ruleSetting.simSlot) {
                Log.d(TAG, "TASK-${task.id}：simSlot is not matched, simSlot = $simSlot, ruleSetting = $ruleSetting")
                continue
            }

            if (!ruleSetting.checkMsg(msgInfo)) {
                Log.d(TAG, "TASK-${task.id}：ruleSetting is not matched, msgInfo = $msgInfo, ruleSetting = $ruleSetting")
                continue
            }

            //TODO：判断其他条件是否满足
            if (!ConditionUtils.checkCondition(task.id, conditionList)) {
                Log.d(TAG, "TASK-${task.id}：other condition is not satisfied")
                continue
            }

            //TODO: 组装消息体 && 执行具体任务
            val actionData = Data.Builder()
                .putLong(TaskWorker.TASK_ID, task.id)
                .putString(TaskWorker.TASK_ACTIONS, task.actions)
                .putString(TaskWorker.MSG_INFO, msgInfoJson)
                .build()
            val actionRequest = OneTimeWorkRequestBuilder<ActionWorker>().setInputData(actionData).build()
            WorkManager.getInstance().enqueue(actionRequest)
        }
    }

}