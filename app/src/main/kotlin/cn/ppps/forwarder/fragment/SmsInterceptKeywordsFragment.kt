package cn.ppps.forwarder.fragment

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import cn.ppps.forwarder.R
import cn.ppps.forwarder.core.BaseFragment
import cn.ppps.forwarder.databinding.FragmentSmsInterceptKeywordsBinding
import cn.ppps.forwarder.utils.SettingUtils
import cn.ppps.forwarder.utils.XToastUtils
import com.xuexiang.xpage.annotation.Page
import com.xuexiang.xui.widget.actionbar.TitleBar

//拦截关键词（二次开发新增）：二级页面逐条添加/删除拦截关键词，支持正则表达式
@Page(name = "拦截关键词")
class SmsInterceptKeywordsFragment : BaseFragment<FragmentSmsInterceptKeywordsBinding?>() {

    override fun viewBindingInflate(
        inflater: LayoutInflater,
        container: ViewGroup,
    ): FragmentSmsInterceptKeywordsBinding {
        return FragmentSmsInterceptKeywordsBinding.inflate(inflater, container, false)
    }

    override fun initTitle(): TitleBar? {
        return super.initTitle()!!.setImmersive(false).setTitle(R.string.menu_sms_intercept_keywords)
    }

    override fun initViews() {
        binding!!.btnInterceptKeywordAdd.setOnClickListener {
            val input = binding!!.etInterceptKeywordInput.text?.toString()?.trim() ?: ""
            if (input.isEmpty()) {
                XToastUtils.warning(getString(R.string.sms_intercept_keyword_empty))
                return@setOnClickListener
            }
            val current = SettingUtils.smsInterceptKeywords
            if (current.split("\n").any { it.trim() == input }) {
                XToastUtils.warning(getString(R.string.sms_intercept_keyword_duplicate))
                binding!!.etInterceptKeywordInput.setText("")
                return@setOnClickListener
            }
            SettingUtils.smsInterceptKeywords = if (current.isEmpty()) input else "$current\n$input"
            binding!!.etInterceptKeywordInput.setText("")
            refreshKeywordsList()
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onResume() {
        super.onResume()
        refreshKeywordsList()
    }

    //刷新关键词逐条列表
    private fun refreshKeywordsList() {
        val container = binding!!.layoutInterceptKeywordsList
        container.removeAllViews()
        val keywords = SettingUtils.smsInterceptKeywords.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        binding!!.tvInterceptKeywordsCount.text = getString(R.string.sms_intercept_keywords_count, keywords.size)
        if (keywords.isEmpty()) return
        val inflater = LayoutInflater.from(requireContext())
        for (keyword in keywords) {
            val row = inflater.inflate(R.layout.item_sms_intercept_keyword, container, false) as ViewGroup
            row.findViewById<TextView>(R.id.tv_keyword).text = keyword
            row.findViewById<ImageView>(R.id.iv_delete).setOnClickListener {
                val remain = SettingUtils.smsInterceptKeywords.split("\n").map { it.trim() }
                    .filter { it.isNotEmpty() && it != keyword }
                SettingUtils.smsInterceptKeywords = remain.joinToString("\n")
                refreshKeywordsList()
            }
            container.addView(row)
        }
    }

}
