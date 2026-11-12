package cn.ppps.forwarder.database.repository

import androidx.annotation.WorkerThread
import cn.ppps.forwarder.database.dao.SmsInterceptedDao
import cn.ppps.forwarder.database.entity.SmsIntercepted
import cn.ppps.forwarder.utils.SMS_INTERCEPTED_KEEP_HOURS
import java.util.Date

class SmsInterceptedRepository(private val smsInterceptedDao: SmsInterceptedDao) {

    @WorkerThread
    suspend fun insert(smsIntercepted: SmsIntercepted): Long = smsInterceptedDao.insert(smsIntercepted)

    @WorkerThread
    fun delete(id: Long) = smsInterceptedDao.delete(id)

    @WorkerThread
    fun deleteAll() = smsInterceptedDao.deleteAll()

    /**
     * 清理过期拦截记录（二次开发新增）：默认只保留最近 24 小时，超时自动清空。
     * 调用点：每次写入拦截记录后、以及打开"已拦截"页面时，保证列表不会长期堆积。
     */
    @WorkerThread
    fun deleteExpired(keepHours: Int = SMS_INTERCEPTED_KEEP_HOURS): Int {
        val before = Date(System.currentTimeMillis() - keepHours * 60L * 60L * 1000L)
        return smsInterceptedDao.deleteBefore(before)
    }

    @WorkerThread
    suspend fun getAll(): List<SmsIntercepted> = smsInterceptedDao.getAll()

}
