package cn.ppps.forwarder.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import cn.ppps.forwarder.database.entity.SmsIntercepted

@Dao
interface SmsInterceptedDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(smsIntercepted: SmsIntercepted): Long

    @Query("DELETE FROM SmsIntercepted where id=:id")
    fun delete(id: Long)

    @Query("DELETE FROM SmsIntercepted")
    fun deleteAll()

    @Query("SELECT * FROM SmsIntercepted ORDER BY id DESC")
    suspend fun getAll(): List<SmsIntercepted>
}
