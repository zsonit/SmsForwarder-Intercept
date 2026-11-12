package cn.ppps.forwarder.fragment

import android.annotation.SuppressLint
import android.text.InputType
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioGroup
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView.RecycledViewPool
import com.alibaba.android.vlayout.VirtualLayoutManager
import cn.ppps.forwarder.App.Companion.FORWARD_STATUS_MAP
import cn.ppps.forwarder.R
import cn.ppps.forwarder.activity.MainActivity
import cn.ppps.forwarder.adapter.MsgPagingAdapter
import cn.ppps.forwarder.adapter.SmsInterceptedAdapter
import cn.ppps.forwarder.core.BaseFragment
import cn.ppps.forwarder.core.Core
import cn.ppps.forwarder.database.entity.LogsDetail
import cn.ppps.forwarder.database.entity.MsgAndLogs
import cn.ppps.forwarder.database.entity.Rule
import cn.ppps.forwarder.database.entity.SmsIntercepted
import cn.ppps.forwarder.database.viewmodel.BaseViewModelFactory
import cn.ppps.forwarder.database.viewmodel.MsgViewModel
import cn.ppps.forwarder.databinding.FragmentLogsBinding
import cn.ppps.forwarder.utils.Log
import cn.ppps.forwarder.utils.SendUtils
import cn.ppps.forwarder.utils.XToastUtils
import com.scwang.smartrefresh.layout.api.RefreshLayout
import com.xuexiang.xaop.annotation.SingleClick
import com.xuexiang.xpage.annotation.Page
import com.xuexiang.xui.widget.actionbar.TitleBar
import com.xuexiang.xui.widget.button.SmoothCheckBox
import com.xuexiang.xui.widget.dialog.materialdialog.DialogAction
import com.xuexiang.xui.widget.dialog.materialdialog.MaterialDialog
import com.xuexiang.xui.widget.picker.widget.TimePickerView
import com.xuexiang.xui.widget.picker.widget.builder.TimePickerBuilder
import com.xuexiang.xui.widget.picker.widget.configure.TimePickerType
import com.xuexiang.xutil.data.DateUtils
import com.xuexiang.xutil.resource.ResUtils.getColors
import com.xuexiang.xutil.resource.ResUtils.getStringArray
import com.xuexiang.xutil.system.ClipboardUtils
import com.xuexiang.xutil.tip.ToastUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale


@Suppress("PrivatePropertyName")
@Page(name = "转发日志")
class LogsFragment : BaseFragment<FragmentLogsBinding?>(), MsgPagingAdapter.OnItemClickListener {

    companion object {
        //第 4 个分类「已拦截」（对应 arrays.type_param_option，二次开发新增）
        private const val TAB_INTERCEPTED = 3
    }

    private val TAG: String = LogsFragment::class.java.simpleName
    private var titleBar: TitleBar? = null
    private var adapter = MsgPagingAdapter(this)
    private val viewModel by viewModels<MsgViewModel> { BaseViewModelFactory(context) }
    private var currentType: String = "sms"

    //日志筛选
    private var currentFilter: MutableMap<String, Any> = mutableMapOf()
    private var logsFilterPopup: MaterialDialog? = null
    private var timePicker: TimePickerView? = null

    //已拦截短信记录（二次开发新增：由设置页并入转发日志页第 4 个分类）
    private val interceptedAdapter = SmsInterceptedAdapter(
        mutableListOf(),
        itemClickListener = { position -> showInterceptedDetail(position) },
        deleteClickListener = { position -> deleteInterceptedRecord(position) },
    )
    private var interceptedRecords: List<SmsIntercepted> = mutableListOf()
    private var interceptedFilter: String? = null
    private val interceptedDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    private var isInterceptedTab = false

    override fun viewBindingInflate(
        inflater: LayoutInflater,
        container: ViewGroup,
    ): FragmentLogsBinding {
        return FragmentLogsBinding.inflate(inflater, container, false)
    }

    override fun initTitle(): TitleBar? {
        titleBar = super.initTitle()!!.setImmersive(false)
        titleBar!!.setLeftImageResource(R.drawable.ic_action_menu)
        titleBar!!.setTitle(R.string.menu_logs)
        titleBar!!.setLeftClickListener { getContainer()?.openMenu() }
        //日志页标题栏动作（拦截记录页会临时移除，避免误清空/误筛选）
        addLogsActions()
        return titleBar
    }

    //添加日志页的标题栏动作：清空日志、筛选日志
    private fun addLogsActions() {
        titleBar!!.addAction(object : TitleBar.ImageAction(R.drawable.ic_delete) {
            @SingleClick
            override fun performAction(view: View) {
                MaterialDialog.Builder(requireContext())
                    .content(if (currentFilter.isEmpty()) R.string.delete_type_log_tips else R.string.delete_filter_log_tips)
                    .positiveText(R.string.lab_yes)
                    .negativeText(R.string.lab_no)
                    .onPositive { _: MaterialDialog?, _: DialogAction? ->
                        try {
                            Log.d(TAG, "deleteAll, currentType:$currentType, currentFilter:$currentFilter")
                            viewModel.setType(currentType).setFilter(currentFilter).deleteAll()
                            reloadData()
                            XToastUtils.success(if (currentFilter.isEmpty()) R.string.delete_type_log_toast else R.string.delete_filter_log_toast)
                        } catch (e: Exception) {
                            e.message?.let { XToastUtils.error(it) }
                        }
                    }
                    .show()
            }
        })
        titleBar!!.addAction(object : TitleBar.ImageAction(R.drawable.ic_filter) {
            @SingleClick
            override fun performAction(view: View) {
                initLogsFilterDialog()
                logsFilterPopup?.show()
            }
        })
    }

    private fun getContainer(): MainActivity? {
        return activity as MainActivity?
    }

    /**
     * 初始化控件
     */
    override fun initViews() {
        val virtualLayoutManager = VirtualLayoutManager(requireContext())
        binding!!.recyclerView.layoutManager = virtualLayoutManager
        val viewPool = RecycledViewPool()
        binding!!.recyclerView.setRecycledViewPool(viewPool)
        viewPool.setMaxRecycledViews(0, 10)
        binding!!.recyclerView.isFocusableInTouchMode = false

        binding!!.tabBar.setTabTitles(getStringArray(R.array.type_param_option))
        binding!!.tabBar.setOnTabClickListener { _, position ->
            if (position == TAB_INTERCEPTED) {
                switchToInterceptedTab()
                return@setOnTabClickListener
            }
            //XToastUtils.toast("点击了$title--$position")
            currentType = when (position) {
                1 -> "call"
                2 -> "app"
                else -> "sms"
            }
            switchToLogsTab()
            initLogsFilterDialog(true)
            reloadData()
        }

        //已拦截短信列表（二次开发新增）
        binding!!.rvIntercepted.layoutManager = LinearLayoutManager(requireContext())
        binding!!.rvIntercepted.adapter = interceptedAdapter
        binding!!.btnInterceptedFilter.setOnClickListener { showInterceptedFilterDialog() }
        binding!!.btnInterceptedClear.setOnClickListener { clearInterceptedRecords() }
    }

    //切换到"已拦截"分类：隐藏日志列表，显示拦截记录
    private fun switchToInterceptedTab() {
        isInterceptedTab = true
        binding!!.refreshLayout.visibility = View.GONE
        binding!!.layoutIntercepted.visibility = View.VISIBLE
        //拦截记录页由页内按钮操作，移除日志页的删除/筛选动作，避免误清空日志
        titleBar!!.removeAllActions()
        loadInterceptedRecords()
    }

    //切回日志分类：恢复日志列表与标题栏动作
    private fun switchToLogsTab() {
        isInterceptedTab = false
        binding!!.layoutIntercepted.visibility = View.GONE
        binding!!.refreshLayout.visibility = View.VISIBLE
        titleBar!!.removeAllActions()
        addLogsActions()
    }

    override fun onResume() {
        super.onResume()
        //在"已拦截"分类时返回页面，刷新一次记录（含 24 小时过期清理）
        if (isInterceptedTab) loadInterceptedRecords()
    }

    //加载已拦截记录：先清理超过 24 小时的记录，再做统计与发件人筛选
    private fun loadInterceptedRecords() {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    Core.smsIntercepted.deleteExpired()
                } catch (e: Exception) {
                    Log.e(TAG, "清理过期拦截记录失败：" + e.message.toString())
                }
            }
            interceptedRecords = Core.smsIntercepted.getAll()

            val todayStart = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            val todayCount = interceptedRecords.count { it.time.time >= todayStart }
            binding!!.tvInterceptedStats.text =
                String.format(getString(R.string.sms_intercepted_stats), interceptedRecords.size, todayCount)

            val displayList = if (interceptedFilter == null) {
                interceptedRecords
            } else {
                interceptedRecords.filter { it.from == interceptedFilter }
            }
            interceptedAdapter.setData(displayList)
            binding!!.tvInterceptedEmpty.visibility = if (displayList.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    //按发件人筛选拦截记录
    private fun showInterceptedFilterDialog() {
        if (interceptedRecords.isEmpty()) {
            XToastUtils.info(getString(R.string.sms_intercepted_empty))
            return
        }
        val senders = interceptedRecords.map { it.from }.distinct()
        val options = mutableListOf(getString(R.string.sms_intercepted_filter_all))
        options.addAll(senders)
        val selectedIndex = if (interceptedFilter == null) 0 else options.indexOf(interceptedFilter).coerceAtLeast(0)

        MaterialDialog.Builder(requireContext())
            .title(R.string.sms_intercepted_filter)
            .items(options)
            .itemsCallbackSingleChoice(selectedIndex) { _: MaterialDialog?, _: View?, _: Int, text: CharSequence ->
                interceptedFilter =
                    if (text == getString(R.string.sms_intercepted_filter_all)) null else text.toString()
                loadInterceptedRecords()
                true
            }
            .negativeText(R.string.cancel)
            .show()
    }

    //查看拦截记录详情，支持复制短信内容
    private fun showInterceptedDetail(position: Int) {
        if (position < 0 || position >= interceptedAdapter.itemCount) return
        val record = interceptedAdapter.itemList[position]
        val simText = when (record.simSlot) {
            0 -> getString(R.string.sim1)
            1 -> getString(R.string.sim2)
            else -> "-"
        }
        val detail = getString(R.string.sms_intercepted_detail_time) + ": " + interceptedDateFormat.format(record.time) +
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

    //删除单条拦截记录
    private fun deleteInterceptedRecord(position: Int) {
        if (position < 0 || position >= interceptedAdapter.itemCount) return
        val record = interceptedAdapter.itemList[position]
        MaterialDialog.Builder(requireContext())
            .title(R.string.sms_intercepted_record_delete)
            .content(record.from + "\n" + record.content)
            .positiveText(R.string.confirm)
            .negativeText(R.string.cancel)
            .onPositive { _: MaterialDialog?, _: DialogAction? ->
                lifecycleScope.launch {
                    Core.smsIntercepted.delete(record.id)
                    loadInterceptedRecords()
                    XToastUtils.info(getString(R.string.sms_intercepted_deleted_toast))
                }
            }
            .show()
    }

    //清空全部拦截记录
    private fun clearInterceptedRecords() {
        MaterialDialog.Builder(requireContext())
            .title(R.string.sms_intercepted_clear)
            .content(R.string.sms_intercepted_clear_confirm)
            .positiveText(R.string.confirm)
            .negativeText(R.string.cancel)
            .onPositive { _: MaterialDialog?, _: DialogAction? ->
                lifecycleScope.launch {
                    Core.smsIntercepted.deleteAll()
                    interceptedFilter = null
                    loadInterceptedRecords()
                    XToastUtils.info(getString(R.string.sms_intercepted_cleared_toast))
                }
            }
            .show()
    }

    override fun initListeners() {
        binding!!.recyclerView.adapter = adapter

        //下拉刷新
        binding!!.refreshLayout.setOnRefreshListener { refreshLayout: RefreshLayout ->
            //adapter.refresh()
            lifecycleScope.launch {
                viewModel.setType(currentType).setFilter(currentFilter).allMsg.collectLatest { adapter.submitData(it) }
            }
            refreshLayout.finishRefresh()
        }

        binding!!.refreshLayout.autoRefresh()
    }

    override fun onItemClicked(view: View?, item: MsgAndLogs) {
        Log.d(TAG, "item: $item")

        val detailStr = StringBuilder()
        detailStr.append(getString(R.string.from)).append(item.msg.from).append("\n\n")
        if (!TextUtils.isEmpty(item.msg.simInfo)) {
            if (item.msg.type == "app") {
                detailStr.append(getString(R.string.title)).append(item.msg.simInfo).append("\n\n")
                detailStr.append(getString(R.string.msg)).append(item.msg.content).append("\n\n")
            } else {
                detailStr.append(getString(R.string.msg)).append(item.msg.content).append("\n\n")
                detailStr.append(getString(R.string.slot)).append(item.msg.simInfo).append("\n\n")
            }
        }
        @SuppressLint("SimpleDateFormat") val utcFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        detailStr.append(getString(R.string.time)).append(DateUtils.date2String(item.msg.time, utcFormatter))

        MaterialDialog.Builder(requireContext())
            .iconRes(item.msg.simImageId)
            .title(R.string.details)
            .content(detailStr.toString())
            .cancelable(true)
            .positiveText(R.string.del)
            .onPositive { _: MaterialDialog?, _: DialogAction? ->
                viewModel.delete(item.msg.id)
                XToastUtils.success(R.string.delete_log_toast)
            }
            .neutralText(R.string.rematch)
            .neutralColor(getColors(R.color.red))
            .onNeutral { _: MaterialDialog?, _: DialogAction? ->
                XToastUtils.toast(R.string.rematch_toast)
                SendUtils.rematchSendMsg(item)
            }
            .show()
    }

    override fun onLogsClicked(view: View?, item: LogsDetail) {
        Log.d(TAG, "item: $item")
        val ruleStr = StringBuilder()
        ruleStr.append(Rule.getRuleMatch(item.type, item.ruleFiled, item.ruleCheck, item.ruleValue, item.ruleSimSlot)).append(item.senderName)
        val detailStr = StringBuilder()
        detailStr.append(getString(R.string.rule)).append(ruleStr.toString()).append("\n\n")
        @SuppressLint("SimpleDateFormat") val utcFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        detailStr.append(getString(R.string.time)).append(DateUtils.date2String(item.time, utcFormatter)).append("\n\n")
        detailStr.append(getString(R.string.result)).append(FORWARD_STATUS_MAP[item.forwardStatus]).append("\n--------------------\n").append(item.forwardResponse)

        MaterialDialog.Builder(requireContext())
            .title(R.string.details)
            .content(detailStr.toString())
            .cancelable(true)
            .negativeText(R.string.resend)
            .onNegative { _: MaterialDialog?, _: DialogAction? ->
                XToastUtils.toast(R.string.resend_toast)
                SendUtils.retrySendMsg(item.id)
            }
            .show()
    }

    override fun onItemRemove(view: View?, id: Int) {}

    private fun reloadData() {
        viewModel.setType(currentType).setFilter(currentFilter)
        adapter.refresh()
        binding!!.recyclerView.scrollToPosition(0)
    }

    @Suppress("SameParameterValue")
    private fun initLogsFilterDialog(needInit: Boolean = false) {
        if (logsFilterPopup == null || needInit) {
            currentFilter = mutableMapOf()

            val logsFilterDialog = View.inflate(requireContext(), R.layout.dialog_logs_filter, null)
            val layoutTitle = logsFilterDialog.findViewById<LinearLayout>(R.id.layout_title)
            val layoutSimSlot = logsFilterDialog.findViewById<LinearLayout>(R.id.layout_sim_slot)
            val layoutCallType = logsFilterDialog.findViewById<LinearLayout>(R.id.layout_call_type)
            when (currentType) {
                "app" -> {
                    layoutTitle.visibility = View.VISIBLE
                    layoutSimSlot.visibility = View.GONE
                    layoutCallType.visibility = View.GONE
                }

                "call" -> {
                    layoutTitle.visibility = View.GONE
                    layoutSimSlot.visibility = View.VISIBLE
                    layoutCallType.visibility = View.VISIBLE
                }

                else -> {
                    layoutTitle.visibility = View.GONE
                    layoutSimSlot.visibility = View.VISIBLE
                    layoutCallType.visibility = View.GONE
                }
            }

            val scbCallType1 = logsFilterDialog.findViewById<SmoothCheckBox>(R.id.scb_call_type1)
            val scbCallType2 = logsFilterDialog.findViewById<SmoothCheckBox>(R.id.scb_call_type2)
            val scbCallType3 = logsFilterDialog.findViewById<SmoothCheckBox>(R.id.scb_call_type3)
            val scbCallType4 = logsFilterDialog.findViewById<SmoothCheckBox>(R.id.scb_call_type4)
            val scbCallType5 = logsFilterDialog.findViewById<SmoothCheckBox>(R.id.scb_call_type5)
            val scbCallType6 = logsFilterDialog.findViewById<SmoothCheckBox>(R.id.scb_call_type6)
            val etFrom = logsFilterDialog.findViewById<EditText>(R.id.et_from)
            val etContent = logsFilterDialog.findViewById<EditText>(R.id.et_content)
            val etTitle = logsFilterDialog.findViewById<EditText>(R.id.et_title)
            val rgSimSlot = logsFilterDialog.findViewById<RadioGroup>(R.id.rg_sim_slot)
            val etStartTime = logsFilterDialog.findViewById<EditText>(R.id.et_start_time)
            val scbForwardStatus0 = logsFilterDialog.findViewById<SmoothCheckBox>(R.id.scb_forward_status_0)
            val scbForwardStatus1 = logsFilterDialog.findViewById<SmoothCheckBox>(R.id.scb_forward_status_1)
            val scbForwardStatus2 = logsFilterDialog.findViewById<SmoothCheckBox>(R.id.scb_forward_status_2)
            etStartTime.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    showTimePicker(etStartTime.text.toString().trim(), getString(R.string.start_time), etStartTime)
                } else {
                    timePicker?.dismiss()
                }
            }
            val etEndTime = logsFilterDialog.findViewById<EditText>(R.id.et_end_time)
            etEndTime.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    showTimePicker(etEndTime.text.toString().trim(), getString(R.string.end_time), etEndTime)
                } else {
                    timePicker?.dismiss()
                }
            }

            logsFilterPopup = MaterialDialog.Builder(requireContext())
                .iconRes(android.R.drawable.ic_menu_search)
                .title(R.string.menu_logs)
                .customView(logsFilterDialog, true)
                .cancelable(false)
                .autoDismiss(false)
                .neutralText(R.string.reset)
                .neutralColor(getColors(R.color.darkGrey))
                .onNeutral { dialog: MaterialDialog?, _: DialogAction? ->
                    dialog?.dismiss()
                    currentFilter = mutableMapOf()
                    logsFilterPopup = null
                    reloadData()
                }.positiveText(R.string.search).onPositive { dialog: MaterialDialog?, _: DialogAction? ->
                    currentFilter = mutableMapOf()
                    currentFilter["from"] = etFrom.text.toString().trim()
                    currentFilter["content"] = etContent.text.toString().trim()
                    currentFilter["title"] = etTitle.text.toString().trim()
                    currentFilter["start_time"] = etStartTime.text.toString().trim()
                    currentFilter["end_time"] = etEndTime.text.toString().trim()
                    currentFilter["sim_slot"] = if (currentType == "app") -1 else when (rgSimSlot.checkedRadioButtonId) {
                        R.id.rb_sim_slot_1 -> 0
                        R.id.rb_sim_slot_2 -> 1
                        else -> -1
                    }
                    if (currentType == "call") {
                        currentFilter["call_type"] = mutableListOf<Int>().apply {
                            if (scbCallType1.isChecked) add(1)
                            if (scbCallType2.isChecked) add(2)
                            if (scbCallType3.isChecked) add(3)
                            if (scbCallType4.isChecked) add(4)
                            if (scbCallType5.isChecked) add(5)
                            if (scbCallType6.isChecked) add(6)
                        }
                    }
                    currentFilter["forward_status"] = mutableListOf<Int>().apply {
                        if (scbForwardStatus0.isChecked) add(0)
                        if (scbForwardStatus1.isChecked) add(1)
                        if (scbForwardStatus2.isChecked) add(2)
                    }
                    reloadData()
                    dialog?.dismiss()
                }.build()
        }
    }

    private fun showTimePicker(time: String, title: String, et: EditText) {
        et.inputType = InputType.TYPE_NULL
        val calendar: Calendar = Calendar.getInstance()
        calendar.time = try {
            if (time.isEmpty()) Date() else DateUtils.string2Date(time, DateUtils.yyyyMMddHHmmss.get())
        } catch (e: Exception) {
            Date()
        }
        timePicker = TimePickerBuilder(context) { date, _ ->
            ToastUtils.toast(DateUtils.date2String(date, DateUtils.yyyyMMddHHmmss.get()))
            et.setText(DateUtils.date2String(date, DateUtils.yyyyMMddHHmmss.get()))
        }
            .setTimeSelectChangeListener { date ->
                Log.i("pvTime", "onTimeSelectChanged")
                et.setText(DateUtils.date2String(date, DateUtils.yyyyMMddHHmmss.get()))
            }
            .setType(TimePickerType.ALL)
            .setTitleText(title)
            .isDialog(true)
            .setOutSideCancelable(false)
            .setDate(calendar)
            .build()
        timePicker?.show(false)
    }
}