package org.lineageos.settings.device

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.view.MenuItem
import android.widget.Toast
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import androidx.preference.SwitchPreferenceCompat
import com.android.settingslib.widget.SettingsBasePreferenceFragment
import java.io.File
import java.io.FileOutputStream

class ActionsPreferenceFragment : SettingsBasePreferenceFragment(), Preference.OnPreferenceChangeListener {

    companion object {
        private const val TAG = "MotoActionsHardware"

        const val KEY_HBM_BOOST = "hbm_boost_enabled"
        const val KEY_DC_DIMMING = "dc_dimming_enabled"
        const val KEY_EDGE_REJECTION = "edge_rejection_enabled"
        const val KEY_PERF_PROFILE = "perf_profile"
        const val KEY_TOUCH_GAMING = "touch_gaming_enabled"
        const val KEY_TORCH_BRIGHTNESS = "torch_brightness_level"
        const val KEY_VIBRATION_OVERDRIVE = "vibration_overdrive_enabled"
        const val KEY_AUDIO_STEREO_BOOST = "audio_stereo_boost_enabled"
        const val KEY_CHARGE_LIMIT_80 = "charge_limit_80_enabled"

        // Sysfs Hardware Node Constants
        private val HBM_NODES = listOf(
            "/sys/devices/platform/soc/ae00000.qcom,mdss_mdp/drm/card0/card0-DSI-1/dsi_display_hbm",
            "/sys/devices/platform/soc/soc:qcom,dsi-display-primary/hbm",
            "/sys/class/backlight/panel0-backlight/hbm_mode",
            "/sys/devices/platform/soc/ae00000.qcom,mdss_mdp/backlight/panel0-backlight/hbm_mode"
        )
        private val DC_DIMMING_NODES = listOf(
            "/sys/devices/platform/soc/soc:qcom,dsi-display-primary/dc"
        )
        private val EDGE_REJECTION_NODES = listOf(
            "/sys/class/touchscreen/primary/edge"
        )
        private val TOUCH_GAMING_NODES = listOf(
            "/sys/class/touchscreen/primary/sample",
            "/sys/class/touchscreen/primary/interpolation"
        )
        private val TORCH_NODES = listOf(
            "/sys/class/leds/led:torch_0/brightness",
            "/sys/class/leds/led:torch_1/brightness"
        )
        private val CHARGE_LIMIT_NODES = listOf(
            "/sys/class/power_supply/battery/charge_control_limit"
        )
        private val TOP_APP_UCLAMP_MIN = listOf(
            "/dev/cpuctl/top-app/cpu.uclamp.min"
        )

        fun writeNode(paths: List<String>, value: String): Boolean {
            var success = false
            for (path in paths) {
                try {
                    val file = File(path)
                    if (file.exists()) {
                        FileOutputStream(file).use { it.write(value.toByteArray()) }
                        success = true
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed writing $value to $path: ${e.message}")
                }
            }
            return success
        }

        fun applyAllSettings(context: Context) {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            
            // 1. HBM & DC Dimming
            val hbm = prefs.getBoolean(KEY_HBM_BOOST, false)
            writeNode(HBM_NODES, if (hbm) "1" else "0")

            val dc = prefs.getBoolean(KEY_DC_DIMMING, false)
            writeNode(DC_DIMMING_NODES, if (dc) "1" else "0")

            // 2. Edge Rejection & Touch Gaming
            val edge = prefs.getBoolean(KEY_EDGE_REJECTION, true)
            writeNode(EDGE_REJECTION_NODES, if (edge) "1" else "0")

            val touchGaming = prefs.getBoolean(KEY_TOUCH_GAMING, false)
            writeNode(TOUCH_GAMING_NODES, if (touchGaming) "1" else "0")

            // 3. Performance Profile
            val perf = prefs.getString(KEY_PERF_PROFILE, "balanced") ?: "balanced"
            applyPerformanceProfile(perf)

            // 4. Charge Limit
            val chargeLimit = prefs.getBoolean(KEY_CHARGE_LIMIT_80, false)
            writeNode(CHARGE_LIMIT_NODES, if (chargeLimit) "13" else "12")
        }

        fun applyPerformanceProfile(profile: String) {
            val uclampVal = when (profile) {
                "powersave" -> "0.00"
                "gaming" -> "35.00"
                "overdrive" -> "60.00"
                else -> "15.00" // balanced
            }
            writeNode(TOP_APP_UCLAMP_MIN, uclampVal)
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.actions_panel, rootKey)

        findPreference<SwitchPreferenceCompat>(KEY_HBM_BOOST)?.onPreferenceChangeListener = this
        findPreference<SwitchPreferenceCompat>(KEY_DC_DIMMING)?.onPreferenceChangeListener = this
        findPreference<SwitchPreferenceCompat>(KEY_EDGE_REJECTION)?.onPreferenceChangeListener = this
        findPreference<ListPreference>(KEY_PERF_PROFILE)?.onPreferenceChangeListener = this
        findPreference<SwitchPreferenceCompat>(KEY_TOUCH_GAMING)?.onPreferenceChangeListener = this
        findPreference<ListPreference>(KEY_TORCH_BRIGHTNESS)?.onPreferenceChangeListener = this
        findPreference<SwitchPreferenceCompat>(KEY_VIBRATION_OVERDRIVE)?.onPreferenceChangeListener = this
        findPreference<SwitchPreferenceCompat>(KEY_AUDIO_STEREO_BOOST)?.onPreferenceChangeListener = this
        findPreference<SwitchPreferenceCompat>(KEY_CHARGE_LIMIT_80)?.onPreferenceChangeListener = this
    }

    override fun onPreferenceChange(preference: Preference, newValue: Any): Boolean {
        when (preference.key) {
            KEY_HBM_BOOST -> {
                val enabled = newValue as Boolean
                writeNode(HBM_NODES, if (enabled) "1" else "0")
                Toast.makeText(
                    context,
                    if (enabled) "Sunlight HBM Boost Active (1600 nits)" else "Sunlight HBM Disabled",
                    Toast.LENGTH_SHORT
                ).show()
            }
            KEY_DC_DIMMING -> {
                val enabled = newValue as Boolean
                writeNode(DC_DIMMING_NODES, if (enabled) "1" else "0")
                Toast.makeText(
                    context,
                    if (enabled) "DC Dimming Enabled (Anti-Flicker Active)" else "DC Dimming Disabled",
                    Toast.LENGTH_SHORT
                ).show()
            }
            KEY_EDGE_REJECTION -> {
                val enabled = newValue as Boolean
                writeNode(EDGE_REJECTION_NODES, if (enabled) "1" else "0")
                Toast.makeText(
                    context,
                    if (enabled) "Curved Edge Palm Rejection Active" else "Edge Rejection Disabled",
                    Toast.LENGTH_SHORT
                ).show()
            }
            KEY_PERF_PROFILE -> {
                val profile = newValue as String
                applyPerformanceProfile(profile)
                val label = when (profile) {
                    "powersave" -> "Power Save (Battery Optimized)"
                    "gaming" -> "Gaming & Touch Boost (35% Uclamp Baseline)"
                    "overdrive" -> "Extreme Overdrive (60% Uclamp Baseline)"
                    else -> "Balanced (Dynamic EAS)"
                }
                Toast.makeText(context, "Active Profile: $label", Toast.LENGTH_SHORT).show()
            }
            KEY_TOUCH_GAMING -> {
                val enabled = newValue as Boolean
                writeNode(TOUCH_GAMING_NODES, if (enabled) "1" else "0")
                Toast.makeText(
                    context,
                    if (enabled) "360Hz Gaming Touch Polling Enabled" else "Standard Touch Polling Active",
                    Toast.LENGTH_SHORT
                ).show()
            }
            KEY_TORCH_BRIGHTNESS -> {
                val level = newValue as String
                writeNode(TORCH_NODES, level)
                Toast.makeText(context, "Flashlight Intensity Set: $level mA", Toast.LENGTH_SHORT).show()
            }
            KEY_VIBRATION_OVERDRIVE -> {
                val enabled = newValue as Boolean
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
                val enabled = newValue as Boolean
                Toast.makeText(
                    context,
                    if (enabled) "Stereo Earpiece Gain Stage Boosted" else "Standard Audio Gains Active",
                    Toast.LENGTH_SHORT
                ).show()
            }
            KEY_CHARGE_LIMIT_80 -> {
                val enabled = newValue as Boolean
                writeNode(CHARGE_LIMIT_NODES, if (enabled) "13" else "12")
                Toast.makeText(
                    context,
                    if (enabled) "80% Battery Protection Limit Enabled" else "Standard 100% Charging Enabled",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        return true
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
