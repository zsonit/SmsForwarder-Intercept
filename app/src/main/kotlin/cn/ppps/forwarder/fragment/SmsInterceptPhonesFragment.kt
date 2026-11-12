package cn.ppps.forwarder.fragment

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import cn.ppps.forwarder.R
import cn.ppps.forwarder.core.BaseFragment
import cn.ppps.forwarder.databinding.FragmentSmsInterceptPhonesBinding
import cn.ppps.forwarder.utils.SettingUtils
import cn.ppps.forwarder.utils.XToastUtils
import com.xuexiang.xpage.annotation.Page
import com.xuexiang.xui.widget.actionbar.TitleBar

//拦截号码（二次开发新增）：二级页面逐条添加/删除要拦截的号码
@Page(name = "拦截号码")
class SmsInterceptPhonesFragment : BaseFragment<FragmentSmsInterceptPhonesBinding?>() {

    override fun viewBindingInflate(
        inflater: LayoutInflater,
        container: ViewGroup,
    ): FragmentSmsInterceptPhonesBinding {
        return FragmentSmsInterceptPhonesBinding.inflate(inflater, container, false)
    }

    override fun initTitle(): TitleBar? {
        return super.initTitle()!!.setImmersive(false).setTitle(R.string.menu_sms_intercept_phones)
    }

    override fun initViews() {
        binding!!.btnInterceptPhoneAdd.setOnClickListener {
            val input = binding!!.etInterceptPhoneInput.text?.toString()?.trim() ?: ""
            if (input.isEmpty()) {
                XToastUtils.warning(getString(R.string.sms_intercept_phone_empty))
                return@setOnClickListener
            }
            val currentList = parsePhones()
            if (currentList.contains(input)) {
                XToastUtils.warning(getString(R.string.sms_intercept_phone_duplicate))
                binding!!.etInterceptPhoneInput.setText("")
                return@setOnClickListener
            }
            currentList.add(input)
            SettingUtils.smsInterceptPhones = currentList.joinToString("\n")
            binding!!.etInterceptPhoneInput.setText("")
            refreshPhonesList()
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onResume() {
        super.onResume()
        refreshPhonesList()
    }

    //解析已存号码（兼容逗号/分号/换行分隔的旧数据）
    private fun parsePhones(): MutableList<String> {
        return SettingUtils.smsInterceptPhones
            .replace(";", "\n").replace("；", "\n").replace("，", "\n").replace(",", "\n")
            .split("\n").map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
    }

    //刷新号码逐条列表
    private fun refreshPhonesList() {
        val container = binding!!.layoutInterceptPhonesList
        container.removeAllViews()
        val phones = parsePhones()
        binding!!.tvInterceptPhonesCount.text = getString(R.string.sms_intercept_phones_count, phones.size)
        if (phones.isEmpty()) return
        val inflater = LayoutInflater.from(requireContext())
        for (phone in phones) {
            val row = inflater.inflate(R.layout.item_sms_intercept_keyword, container, false) as ViewGroup
            row.findViewById<TextView>(R.id.tv_keyword).text = phone
            row.findViewById<ImageView>(R.id.iv_delete).setOnClickListener {
                val remain = parsePhones().filter { it != phone }
                SettingUtils.smsInterceptPhones = remain.joinToString("\n")
                refreshPhonesList()
            }
            container.addView(row)
        }
    }

}
