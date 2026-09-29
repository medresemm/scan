package com.nibras.scan

import android.os.Bundle
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity
import com.nibras.scan.databinding.ActivityPrivacyBinding
import com.nibras.scan.util.InsetsUtil
import java.util.Locale

/** Shows the privacy policy bundled in assets (works offline). Azerbaijani for `az`, English otherwise. */
class PrivacyPolicyActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityPrivacyBinding.inflate(layoutInflater)
        setContentView(binding.root)
        InsetsUtil.apply(binding.root, binding.headerPrivacy)

        binding.btnBackPrivacy.setOnClickListener { finish() }

        val file = if (Locale.getDefault().language == "az") "privacy_policy_az.html" else "privacy_policy_en.html"
        val web: WebView = binding.webPrivacy
        web.settings.javaScriptEnabled = false
        web.loadUrl("file:///android_asset/$file")
    }
}
