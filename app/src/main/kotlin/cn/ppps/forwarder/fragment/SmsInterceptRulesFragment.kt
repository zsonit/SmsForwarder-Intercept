package cn.ppps.forwarder.fragment

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import cn.ppps.forwarder.R
import cn.ppps.forwarder.core.BaseFragment
import cn.ppps.forwarder.databinding.FragmentSmsInterceptRulesBinding
import cn.ppps.forwarder.utils.SettingUtils
import cn.ppps.forwarder.utils.XToastUtils
import com.xuexiang.xpage.annotation.Page
import com.xuexiang.xui.widget.actionbar.TitleBar

//自定义拦截规则（二次开发新增）：二级页面管理拦截号码与拦截关键词
@Page(name = "自定义拦截规则")
class SmsInterceptRulesFragment : BaseFragment<FragmentSmsInterceptRulesBinding?>() {

    override fun viewBindingInflate(
        inflater: LayoutInflater,
        container: ViewGroup,
    ): FragmentSmsInterceptRulesBinding {
        return FragmentSmsInterceptRulesBinding.inflate(inflater, container, false)
    }

    override fun initTitle(): TitleBar? {
        return super.initTitle()!!.setImmersive(false).setTitle(R.string.menu_sms_intercept_rules)
    }

    override fun initViews() {
        //拦截号码：修改即保存
        binding!!.etInterceptPhones.setText(SettingUtils.smsInterceptPhones)
        binding!!.etInterceptPhones.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable) {
                SettingUtils.smsInterceptPhones = s.toString().trim()
            }
        })

        //拦截关键词：逐条添加式管理
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
        binding!!.etInterceptPhones.setText(SettingUtils.smsInterceptPhones)
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
