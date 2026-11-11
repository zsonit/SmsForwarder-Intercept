package cn.ppps.forwarder.adapter

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import cn.ppps.forwarder.R
import cn.ppps.forwarder.database.entity.SmsIntercepted
import java.text.SimpleDateFormat
import java.util.Locale

//已拦截短信列表适配器（二次开发新增）
@Suppress("DEPRECATION")
class SmsInterceptedAdapter(
    var itemList: MutableList<SmsIntercepted>,
    private val deleteClickListener: ((Int) -> Unit)? = null,
) : RecyclerView.Adapter<SmsInterceptedAdapter.ViewHolder>() {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.adapter_sms_intercepted_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(itemList[position])
    }

    override fun getItemCount(): Int = itemList.size

    @SuppressLint("NotifyDataSetChanged")
    fun setData(list: List<SmsIntercepted>) {
        itemList.clear()
        itemList.addAll(list)
        notifyDataSetChanged()
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvFrom: TextView = itemView.findViewById(R.id.tv_from)
        private val tvTime: TextView = itemView.findViewById(R.id.tv_time)
        private val tvContent: TextView = itemView.findViewById(R.id.tv_content)
        private val ivDelete: ImageView = itemView.findViewById(R.id.iv_delete)

        fun bind(item: SmsIntercepted) {
            tvFrom.text = item.from
            tvTime.text = dateFormat.format(item.time)
            tvContent.text = item.content
            ivDelete.setOnClickListener {
                deleteClickListener?.invoke(bindingAdapterPosition)
            }
        }
    }
}
