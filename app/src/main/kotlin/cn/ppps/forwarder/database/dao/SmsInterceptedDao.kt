package cn.ppps.forwarder.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import cn.ppps.forwarder.database.entity.SmsIntercepted
import java.util.Date

@Dao
interface SmsInterceptedDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(smsIntercepted: SmsIntercepted): Long

    @Query("DELETE FROM SmsIntercepted where id=:id")
    fun delete(id: Long)

    @Query("DELETE FROM SmsIntercepted")
    fun deleteAll()

    //删除指定时间之前的记录（二次开发新增：已拦截记录只保留最近 24 小时）
    @Query("DELETE FROM SmsIntercepted WHERE time < :before")
    fun deleteBefore(before: Date): Int

    @Query("SELECT * FROM SmsIntercepted ORDER BY id DESC")
    suspend fun getAll(): List<SmsIntercepted>
}
