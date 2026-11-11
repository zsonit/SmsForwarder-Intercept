package cn.ppps.forwarder.fragment

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import cn.ppps.forwarder.R
import cn.ppps.forwarder.adapter.SmsInterceptedAdapter
import cn.ppps.forwarder.core.BaseFragment
import cn.ppps.forwarder.core.Core
import cn.ppps.forwarder.databinding.FragmentSmsInterceptedBinding
import cn.ppps.forwarder.utils.XToastUtils
import com.xuexiang.xaop.annotation.SingleClick
import com.xuexiang.xpage.annotation.Page
import com.xuexiang.xui.utils.ThemeUtils
import com.xuexiang.xui.widget.actionbar.TitleBar
import com.xuexiang.xui.widget.dialog.materialdialog.DialogAction
import com.xuexiang.xui.widget.dialog.materialdialog.MaterialDialog
import kotlinx.coroutines.launch

//已拦截短信列表（二次开发新增）：查看命中拦截规则的短信存档，可复制内容、删除记录、一键清空
@Page(name = "已拦截短信")
class SmsInterceptedFragment : BaseFragment<FragmentSmsInterceptedBinding?>() {

    private var titleBar: TitleBar? = null
    private val adapter = SmsInterceptedAdapter(mutableListOf()) { position ->
        deleteRecord(position)
    }

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
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onResume() {
        super.onResume()
        loadRecords()
    }

    private fun loadRecords() {
        lifecycleScope.launch {
            val records = Core.smsIntercepted.getAll()
            adapter.setData(records)
            binding!!.tvEmpty.visibility = if (records.isEmpty()) View.VISIBLE else View.GONE
        }
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
                    XToastUtils.info(getString(R.string.sms_intercepted_deleted_toast))
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
                    loadRecords()
                    XToastUtils.info(getString(R.string.sms_intercepted_cleared_toast))
                }
            }
            .show()
    }

}
