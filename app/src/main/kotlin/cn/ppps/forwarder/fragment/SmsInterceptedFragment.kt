package cn.ppps.forwarder.fragment

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import cn.ppps.forwarder.R
import cn.ppps.forwarder.adapter.SmsInterceptedAdapter
import cn.ppps.forwarder.core.BaseFragment
import cn.ppps.forwarder.core.Core
import cn.ppps.forwarder.database.entity.SmsIntercepted
import cn.ppps.forwarder.databinding.FragmentSmsInterceptedBinding
import cn.ppps.forwarder.utils.XToastUtils
import com.xuexiang.xaop.annotation.SingleClick
import com.xuexiang.xpage.annotation.Page
import com.xuexiang.xui.utils.ThemeUtils
import com.xuexiang.xui.widget.actionbar.TitleBar
import com.xuexiang.xui.widget.dialog.materialdialog.DialogAction
import com.xuexiang.xui.widget.dialog.materialdialog.MaterialDialog
import com.xuexiang.xutil.system.ClipboardUtils
import com.xuexiang.xutil.tip.ToastUtils
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

//已拦截短信列表（二次开发新增）：查看命中拦截规则的短信存档，支持统计、筛选、复制内容、删除记录、清空
@Page(name = "已拦截短信")
class SmsInterceptedFragment : BaseFragment<FragmentSmsInterceptedBinding?>() {

    private var titleBar: TitleBar? = null
    private var allRecords: List<SmsIntercepted> = mutableListOf()
    private var currentFilter: String? = null //null=全部发件人
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    private val adapter = SmsInterceptedAdapter(
        mutableListOf(),
        itemClickListener = { position -> showRecordDetail(position) },
        deleteClickListener = { position -> deleteRecord(position) },
    )

    override fun viewBindingInflate(
        inflater: LayoutInflater,
        container: ViewGroup,
    ): FragmentSmsInterceptedBinding {
        return FragmentSmsInterceptedBinding.inflate(inflater, container, false)
    }

    override fun initTitle(): TitleBar? {
        titleBar = super.initTitle()!!.setImmersive(false)
        titleBar!!.setTitle(R.string.menu_sms_intercepted)
        titleBar!!.setActionTextColor(ThemeUtils.resolveColor(context, R.attr.colorAccent))
        titleBar!!.addAction(object : TitleBar.ImageAction(R.drawable.ic_delete) {
            @SingleClick
            override fun performAction(view: View) {
                clearAllRecords()
            }
        })
        return titleBar
    }

    override fun initViews() {
        binding!!.recyclerView.layoutManager = LinearLayoutManager(context)
        binding!!.recyclerView.adapter = adapter
        binding!!.btnSmsInterceptedFilter.setOnClickListener { showFilterDialog() }
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onResume() {
        super.onResume()
        loadRecords()
    }

    private fun loadRecords() {
        lifecycleScope.launch {
            allRecords = Core.smsIntercepted.getAll()

            //统计：累计拦截条数 + 今日拦截条数
            val todayStart = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            val todayCount = allRecords.count { it.time.time >= todayStart }
            binding!!.tvStats.text = String.format(getString(R.string.sms_intercepted_stats), allRecords.size, todayCount)

            //应用发件人筛选
            val displayList = if (currentFilter == null) allRecords else allRecords.filter { it.from == currentFilter }
            adapter.setData(displayList)
            binding!!.tvEmpty.visibility = if (displayList.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    //按发件人筛选
    private fun showFilterDialog() {
        if (allRecords.isEmpty()) {
            XXToastUtils.info(getString(R.string.sms_intercepted_empty))
            return
        }
        val senders = allRecords.map { it.from }.distinct()
        val options = mutableListOf(getString(R.string.sms_intercepted_filter_all))
        options.addAll(senders)
        val selectedIndex = if (currentFilter == null) 0 else options.indexOf(currentFilter).coerceAtLeast(0)

        MaterialDialog.Builder(requireContext())
            .title(R.string.sms_intercepted_filter)
            .items(options)
            .itemsCallbackSingleChoice(selectedIndex) { _: MaterialDialog?, _: View?, _: Int, text: CharSequence ->
                currentFilter = if (text == getString(R.string.sms_intercepted_filter_all)) null else text.toString()
                loadRecords()
                true
            }
            .negativeText(R.string.cancel)
            .show()
    }

    //查看记录详情并支持复制内容
    private fun showRecordDetail(position: Int) {
        if (position < 0 || position >= adapter.itemCount) return
        val record = adapter.itemList[position]
        val simText = when (record.simSlot) {
            0 -> getString(R.string.sim1)
            1 -> getString(R.string.sim2)
            else -> "-"
        }
        val detail = getString(R.string.sms_intercepted_detail_time) + ": " + dateFormat.format(record.time) +
            "\n" + getString(R.string.sms_intercept_sim_slot) + ": " + simText +
            "\n\n" + record.content

        MaterialDialog.Builder(requireContext())
            .title(record.from)
            .content(detail)
            .positiveText(R.string.sms_intercepted_copy)
            .onPositive { _: MaterialDialog?, _: DialogAction? ->
                ClipboardUtils.copyText(record.content)
                XToastUtils.info(getString(R.string.sms_intercepted_copied_toast))
            }
            .negativeText(R.string.cancel)
            .show()
    }

    private fun deleteRecord(position: Int) {
        if (position < 0 || position >= adapter.itemCount) return
        val record = adapter.itemList[position]
        MaterialDialog.Builder(requireContext())
            .title(R.string.sms_intercepted_record_delete)
            .content(record.from + "\n" + record.content)
            .positiveText(R.string.confirm)
            .negativeText(R.string.cancel)
            .onPositive { _: MaterialDialog?, _: DialogAction? ->
                lifecycleScope.launch {
                    Core.smsIntercepted.delete(record.id)
                    loadRecords()
                    XXToastUtils.info(getString(R.string.sms_intercepted_deleted_toast))
                }
            }
            .show()
    }

    private fun clearAllRecords() {
        MaterialDialog.Builder(requireContext())
            .title(R.string.sms_intercepted_clear)
            .content(R.string.sms_intercepted_clear_confirm)
            .positiveText(R.string.confirm)
            .negativeText(R.string.cancel)
            .onPositive { _: MaterialDialog?, _: DialogAction? ->
                lifecycleScope.launch {
                    Core.smsIntercepted.deleteAll()
                    currentFilter = null
                    loadRecords()
                    XXToastUtils.info(getString(R.string.sms_intercepted_cleared_toast))
                }
            }
            .show()
    }

}
