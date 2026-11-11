package cn.ppps.forwarder.database.entity

import android.os.Parcelable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize
import java.util.Date

//已拦截短信（二次开发新增）：命中拦截规则的短信存档，可在"已拦截"页面查看/删除记录
@Parcelize
@Entity(
    tableName = "SmsIntercepted",
    indices = [
        Index(value = ["id"], unique = true)
    ]
)
data class SmsIntercepted(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id") var id: Long,
    @ColumnInfo(name = "from", defaultValue = "") var from: String,
    @ColumnInfo(name = "content", defaultValue = "") var content: String,
    @ColumnInfo(name = "sim_info", defaultValue = "") var simInfo: String = "",
    @ColumnInfo(name = "sim_slot", defaultValue = "-1") var simSlot: Int = -1,
    @ColumnInfo(name = "time") var time: Date = Date(),
) : Parcelable
