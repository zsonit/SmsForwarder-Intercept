package cn.ppps.forwarder.database.repository

import androidx.annotation.WorkerThread
import cn.ppps.forwarder.database.dao.SmsInterceptedDao
import cn.ppps.forwarder.database.entity.SmsIntercepted

class SmsInterceptedRepository(private val smsInterceptedDao: SmsInterceptedDao) {

    @WorkerThread
    suspend fun insert(smsIntercepted: SmsIntercepted): Long = smsInterceptedDao.insert(smsIntercepted)

    @WorkerThread
    fun delete(id: Long) = smsInterceptedDao.delete(id)

    @WorkerThread
    fun deleteAll() = smsInterceptedDao.deleteAll()

    @WorkerThread
    suspend fun getAll(): List<SmsIntercepted> = smsInterceptedDao.getAll()

}
