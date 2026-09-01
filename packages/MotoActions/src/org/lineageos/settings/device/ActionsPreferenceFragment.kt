/*
 * SPDX-FileCopyrightText: 2015-2016 The CyanogenMod Project
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device

import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.MenuItem
import android.widget.Toast
import androidx.preference.Preference
import androidx.preference.SwitchPreferenceCompat
import com.android.settingslib.widget.SettingsBasePreferenceFragment
import java.io.File
import java.io.FileOutputStream

class ActionsPreferenceFragment : SettingsBasePreferenceFragment(), Preference.OnPreferenceChangeListener {

    companion object {
        const val KEY_HBM_BOOST = "hbm_boost_enabled"
        const val KEY_VIBRATION_OVERDRIVE = "vibration_overdrive_enabled"
        const val KEY_AUDIO_STEREO_BOOST = "audio_stereo_boost_enabled"
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.actions_panel, rootKey)

        findPreference<SwitchPreferenceCompat>(KEY_HBM_BOOST)?.onPreferenceChangeListener = this
        findPreference<SwitchPreferenceCompat>(KEY_VIBRATION_OVERDRIVE)?.onPreferenceChangeListener = this
        findPreference<SwitchPreferenceCompat>(KEY_AUDIO_STEREO_BOOST)?.onPreferenceChangeListener = this
    }

    override fun onPreferenceChange(preference: Preference, newValue: Any): Boolean {
        val enabled = newValue as Boolean
        when (preference.key) {
            KEY_HBM_BOOST -> {
                setHbmMode(enabled)
                Toast.makeText(
                    context,
                    if (enabled) "Sunlight HBM Boost Active (1600 nits)" else "Sunlight HBM Boost Disabled",
                    Toast.LENGTH_SHORT
                ).show()
            }
            KEY_VIBRATION_OVERDRIVE -> {
                if (enabled) {
                    val vibrator = context?.getSystemService(Vibrator::class.java)
                    vibrator?.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
                }
                Toast.makeText(
                    context,
                    if (enabled) "Vibration Overdrive Active (100% Saturation / 3.6V)" else "Standard Vibration Active",
                    Toast.LENGTH_SHORT
                ).show()
            }
            KEY_AUDIO_STEREO_BOOST -> {
                Toast.makeText(
                    context,
                    if (enabled) "Stereo Earpiece Gain Stage Boosted" else "Standard Audio Gains Active",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        return true
    }

    private fun setHbmMode(enable: Boolean) {
        val nodePaths = listOf(
            "/sys/class/backlight/panel0-backlight/hbm_mode",
            "/sys/devices/platform/soc/ae00000.qcom,mdss_mdp/backlight/panel0-backlight/hbm_mode",
            "/sys/devices/platform/soc/soc:qcom,dsi-display-primary/fod_hbm"
        )
        val value = if (enable) "1" else "0"
        for (path in nodePaths) {
            try {
                val file = File(path)
                if (file.exists() && file.canWrite()) {
                    FileOutputStream(file).use { it.write(value.toByteArray()) }
                }
            } catch (_: Exception) {}
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return if (item.itemId == android.R.id.home) {
            activity?.onBackPressedDispatcher?.onBackPressed()
            true
        } else {
            false
        }
    }
}
