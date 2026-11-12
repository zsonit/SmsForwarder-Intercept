package cn.ppps.forwarder.fragment

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import cn.ppps.forwarder.R
import cn.ppps.forwarder.core.BaseFragment
import cn.ppps.forwarder.databinding.FragmentSmsInterceptWhitelistBinding
import cn.ppps.forwarder.utils.SettingUtils
import cn.ppps.forwarder.utils.XToastUtils
import com.xuexiang.xpage.annotation.Page
import com.xuexiang.xui.widget.actionbar.TitleBar

//拦截白名单（二次开发新增）：号码或内容命中白名单即放行，优先于拦截规则
@Page(name = "拦截白名单")
class SmsInterceptWhitelistFragment : BaseFragment<FragmentSmsInterceptWhitelistBinding?>() {

    override fun viewBindingInflate(
        inflater: LayoutInflater,
        container: ViewGroup,
    ): FragmentSmsInterceptWhitelistBinding {
        return FragmentSmsInterceptWhitelistBinding.inflate(inflater, container, false)
    }

    override fun initTitle(): TitleBar? {
        return super.initTitle()!!.setImmersive(false).setTitle(R.string.menu_sms_intercept_whitelist)
    }

    override fun initViews() {
        binding!!.btnWhitelistAdd.setOnClickListener {
            val input = binding!!.etWhitelistInput.text?.toString()?.trim() ?: ""
            if (input.isEmpty()) {
                XToastUtils.warning(getString(R.string.sms_intercept_whitelist_empty))
                return@setOnClickListener
            }
            val currentList = parseWhitelist()
            if (currentList.contains(input)) {
                XToastUtils.warning(getString(R.string.sms_intercept_whitelist_duplicate))
                binding!!.etWhitelistInput.setText("")
                return@setOnClickListener
            }
            currentList.add(input)
            SettingUtils.smsInterceptWhitelist = currentList.joinToString("\n")
            binding!!.etWhitelistInput.setText("")
            refreshWhitelist()
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onResume() {
        super.onResume()
        refreshWhitelist()
    }

    //解析已存白名单（兼容逗号/分号/换行分隔的旧数据）
    private fun parseWhitelist(): MutableList<String> {
        return SettingUtils.smsInterceptWhitelist
            .replace(";", "\n").replace("；", "\n").replace("，", "\n").replace(",", "\n")
            .split("\n").map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
    }

    //刷新白名单逐条列表
    private fun refreshWhitelist() {
        val container = binding!!.layoutWhitelistList
        container.removeAllViews()
        val items = parseWhitelist()
        binding!!.tvWhitelistCount.text = getString(R.string.sms_intercept_whitelist_count, items.size)
        if (items.isEmpty()) return
        val inflater = LayoutInflater.from(requireContext())
        for (item in items) {
            val row = inflater.inflate(R.layout.item_sms_intercept_keyword, container, false) as ViewGroup
            row.findViewById<TextView>(R.id.tv_keyword).text = item
            row.findViewById<ImageView>(R.id.iv_delete).setOnClickListener {
                val remain = parseWhitelist().filter { it != item }
                SettingUtils.smsInterceptWhitelist = remain.joinToString("\n")
                refreshWhitelist()
            }
            container.addView(row)
        }
    }

}
