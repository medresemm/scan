package com.nibras.scan

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.nibras.scan.databinding.ActivitySettingsBinding
import com.nibras.scan.model.FilterType
import com.nibras.scan.repo.ScanSession
import com.nibras.scan.util.AppConfig
import com.nibras.scan.util.FileUtils
import com.nibras.scan.util.InsetsUtil
import com.nibras.scan.util.SettingsManager
import com.nibras.scan.util.SettingsManager.OcrLanguage
import com.nibras.scan.util.SettingsManager.PdfQuality

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        InsetsUtil.apply(binding.root, binding.headerSettings)

        binding.btnBackSettings.setOnClickListener { finish() }
        binding.rowLanguage.setOnClickListener { chooseLanguage() }
        binding.rowFilter.setOnClickListener { chooseFilter() }
        binding.rowQuality.setOnClickListener { chooseQuality() }
        binding.rowOcrLang.setOnClickListener { chooseOcrLanguage() }
        binding.rowDeleteData.setOnClickListener { confirmDeleteAll() }
        binding.rowPrivacy.setOnClickListener {
            startActivity(Intent(this, PrivacyPolicyActivity::class.java))
        }
        binding.rowWebsite.setOnClickListener { openWebsite() }
        binding.rowShare.setOnClickListener { shareApp() }
        binding.rowRate.setOnClickListener { rateApp() }
        binding.rowContact.setOnClickListener { contactSupport() }

        binding.tvAppNameValue.text = getString(R.string.app_name)

        val versionName = try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "1.0"
        } catch (e: Exception) { "1.0" }
        binding.tvVersionValue.text = versionName

        refreshValues()
    }

    private fun filterLabel(f: FilterType) = when (f) {
        FilterType.ORIGINAL -> getString(R.string.filter_original)
        FilterType.AUTO -> getString(R.string.filter_auto)
        FilterType.BLACK_WHITE -> getString(R.string.filter_bw)
        FilterType.GRAYSCALE -> getString(R.string.filter_grayscale)
    }

    private fun qualityLabel(q: PdfQuality) = when (q) {
        PdfQuality.LOW -> getString(R.string.quality_low)
        PdfQuality.MEDIUM -> getString(R.string.quality_medium)
        PdfQuality.HIGH -> getString(R.string.quality_high)
    }

    private fun langLabel(l: OcrLanguage) = when (l) {
        OcrLanguage.LATIN -> getString(R.string.ocr_lang_latin)
        OcrLanguage.ARABIC -> getString(R.string.ocr_lang_arabic)
    }

    private fun refreshValues() {
        binding.tvLanguageValue.text = languageLabel(currentLanguageTag())
        binding.tvFilterValue.text = filterLabel(SettingsManager.getDefaultFilter(this))
        binding.tvQualityValue.text = qualityLabel(SettingsManager.getPdfQuality(this))
        binding.tvOcrLangValue.text = langLabel(SettingsManager.getOcrLanguage(this))
    }

    // null = follow the phone's system language
    private val languageTags = listOf(null, "az", "en", "tr", "ru", "ar")

    private fun languageLabel(tag: String?) = when (tag) {
        null -> getString(R.string.lang_system)
        "az" -> getString(R.string.lang_az)
        "en" -> getString(R.string.lang_en)
        "tr" -> getString(R.string.lang_tr)
        "ru" -> getString(R.string.lang_ru)
        "ar" -> getString(R.string.lang_ar)
        else -> tag
    }

    private fun currentLanguageTag(): String? {
        val locales = AppCompatDelegate.getApplicationLocales()
        return if (locales.isEmpty) null else locales[0]?.language
    }

    private fun chooseLanguage() {
        val currentIndex = languageTags.indexOf(currentLanguageTag()).let { if (it == -1) 0 else it }
        AlertDialog.Builder(this)
            .setTitle(R.string.setting_app_language)
            .setSingleChoiceItems(languageTags.map { languageLabel(it) }.toTypedArray(), currentIndex) { d, which ->
                val tag = languageTags[which]
                val locales = if (tag == null) {
                    LocaleListCompat.getEmptyLocaleList()
                } else {
                    LocaleListCompat.forLanguageTags(tag)
                }
                // Applies immediately and persists automatically; recreates this (and other
                // running) activities so every screen picks up the new language right away.
                AppCompatDelegate.setApplicationLocales(locales)
                d.dismiss()
            }
            .setNegativeButton(R.string.cancel, null).show()
    }

    private fun openWebsite() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(AppConfig.WEBSITE_URL)))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, R.string.no_browser_app, Toast.LENGTH_SHORT).show()
        }
    }

    private fun chooseFilter() {
        val all = FilterType.values()
        val current = all.indexOf(SettingsManager.getDefaultFilter(this))
        AlertDialog.Builder(this)
            .setTitle(R.string.setting_default_filter)
            .setSingleChoiceItems(all.map { filterLabel(it) }.toTypedArray(), current) { d, which ->
                SettingsManager.setDefaultFilter(this, all[which])
                refreshValues(); d.dismiss()
            }
            .setNegativeButton(R.string.cancel, null).show()
    }

    private fun chooseQuality() {
        val all = PdfQuality.values()
        val current = all.indexOf(SettingsManager.getPdfQuality(this))
        AlertDialog.Builder(this)
            .setTitle(R.string.setting_pdf_quality)
            .setSingleChoiceItems(all.map { qualityLabel(it) }.toTypedArray(), current) { d, which ->
                SettingsManager.setPdfQuality(this, all[which])
                refreshValues(); d.dismiss()
            }
            .setNegativeButton(R.string.cancel, null).show()
    }

    private fun chooseOcrLanguage() {
        val all = OcrLanguage.values()
        val current = all.indexOf(SettingsManager.getOcrLanguage(this))
        AlertDialog.Builder(this)
            .setTitle(R.string.setting_ocr_language)
            .setSingleChoiceItems(all.map { langLabel(it) }.toTypedArray(), current) { d, which ->
                SettingsManager.setOcrLanguage(this, all[which])
                refreshValues(); d.dismiss()
            }
            .setNegativeButton(R.string.cancel, null).show()
    }

    private fun confirmDeleteAll() {
        AlertDialog.Builder(this)
            .setMessage(R.string.delete_all_confirm)
            .setPositiveButton(R.string.delete) { _, _ ->
                FileUtils.documentsDir(this).listFiles()?.forEach { it.delete() }
                FileUtils.clearPagesDir(this)
                cacheDir.listFiles()?.forEach { it.delete() }
                ScanSession.clear()
                Toast.makeText(this, R.string.all_data_deleted, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.cancel, null).show()
    }

    private fun shareApp() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, getString(R.string.share_app_text) + "\n" + AppConfig.PLAY_STORE_URL)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.share_app)))
    }

    private fun rateApp() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(AppConfig.PLAY_MARKET_URI)))
        } catch (e: ActivityNotFoundException) {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(AppConfig.PLAY_STORE_URL)))
        }
    }

    private fun contactSupport() {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:${AppConfig.SUPPORT_EMAIL}")
            putExtra(Intent.EXTRA_SUBJECT, "Nibras Scan")
        }
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, R.string.no_email_app, Toast.LENGTH_SHORT).show()
        }
    }
}
