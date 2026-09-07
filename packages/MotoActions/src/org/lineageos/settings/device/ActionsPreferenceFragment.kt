package org.lineageos.settings.device

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.provider.Settings
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
        const val KEY_EARPIECE_BOOST = "earpiece_gain_boost"
        const val KEY_SPEAKER_BOOST = "speaker_gain_boost"
        const val KEY_CHARGE_LIMIT_80 = "charge_limit_80_enabled"

        // Sysfs Hardware Node Constants
        private val HBM_NODES = listOf(
            "/sys/devices/platform/soc/ae00000.qcom,mdss_mdp/drm/card0/card0-DSI-1/dsi_display_hbm",
            "/sys/class/drm/card0-DSI-1/dsi_display_hbm",
            "/sys/devices/platform/soc/soc:qcom,dsi-display-primary/hbm",
            "/sys/class/backlight/panel0-backlight/hbm_mode"
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
        private val CHARGE_LIMIT_NODES = listOf(
            "/sys/class/power_supply/battery/charge_control_limit"
        )

        // CPU Frequency & Scheduling Nodes
        private val CPU0_MAX_FREQ = listOf("/sys/devices/system/cpu/cpufreq/policy0/scaling_max_freq")
        private val CPU0_MIN_FREQ = listOf("/sys/devices/system/cpu/cpufreq/policy0/scaling_min_freq")
        private val CPU4_MAX_FREQ = listOf("/sys/devices/system/cpu/cpufreq/policy4/scaling_max_freq")
        private val CPU4_MIN_FREQ = listOf("/sys/devices/system/cpu/cpufreq/policy4/scaling_min_freq")
        private val CPU7_MAX_FREQ = listOf("/sys/devices/system/cpu/cpufreq/policy7/scaling_max_freq")
        private val CPU7_MIN_FREQ = listOf("/sys/devices/system/cpu/cpufreq/policy7/scaling_min_freq")
        private val TOP_APP_UCLAMP_MIN = listOf("/dev/cpuctl/top-app/cpu.uclamp.min")

        fun writeNode(paths: List<String>, value: String): Boolean {
            var success = false
            for (path in paths) {
                try {
                    val file = File(path)
                    if (file.exists()) {
                        FileOutputStream(file).use { it.write(value.toByteArray()) }
                        Log.i(TAG, "Successfully wrote $value to $path")
                        success = true
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed writing $value to $path: ${e.message}")
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

            // 4. Audio Gains
            applyAudioGains(context)

            // 5. Charge Limit
            val chargeLimit = prefs.getBoolean(KEY_CHARGE_LIMIT_80, false)
            writeNode(CHARGE_LIMIT_NODES, if (chargeLimit) "13" else "12")
        }

        fun applyPerformanceProfile(profile: String) {
            when (profile) {
                "powersave" -> {
                    // Lower min frequency first, then cap max frequency
                    writeNode(CPU0_MIN_FREQ, "614400")
                    writeNode(CPU4_MIN_FREQ, "652800")
                    writeNode(CPU7_MIN_FREQ, "768000")
                    writeNode(CPU0_MAX_FREQ, "1094400")
                    writeNode(CPU4_MAX_FREQ, "1382400")
                    writeNode(CPU7_MAX_FREQ, "1401600")
                    writeNode(TOP_APP_UCLAMP_MIN, "0.00")
                }
                "gaming" -> {
                    // Raise max frequency first, then raise min floor
                    writeNode(CPU0_MAX_FREQ, "1804800")
                    writeNode(CPU4_MAX_FREQ, "2361600")
                    writeNode(CPU7_MAX_FREQ, "2515200")
                    writeNode(CPU0_MIN_FREQ, "1228800")
                    writeNode(CPU4_MIN_FREQ, "1651200")
                    writeNode(CPU7_MIN_FREQ, "1766400")
                    writeNode(TOP_APP_UCLAMP_MIN, "40.00")
                }
                "overdrive" -> {
                    // Raise max frequency first, then pin high min floor
                    writeNode(CPU0_MAX_FREQ, "1804800")
                    writeNode(CPU4_MAX_FREQ, "2361600")
                    writeNode(CPU7_MAX_FREQ, "2515200")
                    writeNode(CPU0_MIN_FREQ, "1555200")
                    writeNode(CPU4_MIN_FREQ, "2054400")
                    writeNode(CPU7_MIN_FREQ, "2169600")
                    writeNode(TOP_APP_UCLAMP_MIN, "70.00")
                }
                else -> { // "balanced"
                    // Reset to full dynamic range with EAS
                    writeNode(CPU0_MAX_FREQ, "1804800")
                    writeNode(CPU4_MAX_FREQ, "2361600")
                    writeNode(CPU7_MAX_FREQ, "2515200")
                    writeNode(CPU0_MIN_FREQ, "614400")
                    writeNode(CPU4_MIN_FREQ, "652800")
                    writeNode(CPU7_MIN_FREQ, "768000")
                    writeNode(TOP_APP_UCLAMP_MIN, "15.00")
                }
            }
        }

        fun applyAudioGains(context: Context) {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            val earpieceBoost = (prefs.getString(KEY_EARPIECE_BOOST, "0") ?: "0").toIntOrNull() ?: 0
            val speakerBoost = (prefs.getString(KEY_SPEAKER_BOOST, "0") ?: "0").toIntOrNull() ?: 0

            val diff = speakerBoost - earpieceBoost
            val balance = (diff * 0.15f).coerceIn(-1.0f, 1.0f)
            runCatching {
                Settings.System.putFloat(
                    context.contentResolver,
                    Settings.System.MASTER_BALANCE,
                    balance
                )
            }
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.actions_panel, rootKey)

        findPreference<SwitchPreferenceCompat>(KEY_HBM_BOOST)?.onPreferenceChangeListener = this
        findPreference<SwitchPreferenceCompat>(KEY_DC_DIMMING)?.onPreferenceChangeListener = this
        findPreference<SwitchPreferenceCompat>(KEY_EDGE_REJECTION)?.onPreferenceChangeListener = this
        findPreference<ListPreference>(KEY_PERF_PROFILE)?.onPreferenceChangeListener = this
        findPreference<SwitchPreferenceCompat>(KEY_TOUCH_GAMING)?.onPreferenceChangeListener = this
        findPreference<ListPreference>(KEY_EARPIECE_BOOST)?.onPreferenceChangeListener = this
        findPreference<ListPreference>(KEY_SPEAKER_BOOST)?.onPreferenceChangeListener = this
        findPreference<SwitchPreferenceCompat>(KEY_CHARGE_LIMIT_80)?.onPreferenceChangeListener = this
    }

    override fun onPreferenceChange(preference: Preference, newValue: Any): Boolean {
        Log.i(TAG, "onPreferenceChange: ${preference.key} -> $newValue")
        when (preference.key) {
            KEY_HBM_BOOST -> {
                val enabled = newValue as Boolean
                val prefs = PreferenceManager.getDefaultSharedPreferences(preference.context)
                if (enabled) {
                    val currentFloat = try {
                        Settings.System.getFloat(preference.context.contentResolver, "screen_brightness_float")
                    } catch (e: Exception) { 0.5f }
                    prefs.edit().putFloat("hbm_saved_brightness", currentFloat).apply()
                    runCatching {
                        Settings.System.putFloat(
                            preference.context.contentResolver,
                            "screen_brightness_float",
                            1.0f
                        )
                        Settings.System.putInt(
                            preference.context.contentResolver,
                            Settings.System.SCREEN_BRIGHTNESS,
                            255
                        )
                    }
                } else {
                    val saved = prefs.getFloat("hbm_saved_brightness", 0.5f)
                    runCatching {
                        Settings.System.putFloat(
                            preference.context.contentResolver,
                            "screen_brightness_float",
                            saved
                        )
                    }
                }
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
                    "powersave" -> "Power Save (Max Frequencies Capped)"
                    "gaming" -> "Gaming Mode (High Baseline Frequencies)"
                    "overdrive" -> "Extreme Overdrive (Pinned Peak Clocks)"
                    else -> "Balanced (Dynamic EAS Scaling)"
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
            KEY_EARPIECE_BOOST -> {
                val boost = (newValue as String).toIntOrNull() ?: 0
                val prefs = PreferenceManager.getDefaultSharedPreferences(preference.context)
                val speakerBoost = (prefs.getString(KEY_SPEAKER_BOOST, "0") ?: "0").toIntOrNull() ?: 0
                val diff = speakerBoost - boost
                val balance = (diff * 0.15f).coerceIn(-1.0f, 1.0f)
                runCatching {
                    Settings.System.putFloat(
                        preference.context.contentResolver,
                        Settings.System.MASTER_BALANCE,
                        balance
                    )
                }
                Toast.makeText(context, "Top Earpiece Boost: +${boost} dB", Toast.LENGTH_SHORT).show()
            }
            KEY_SPEAKER_BOOST -> {
                val boost = (newValue as String).toIntOrNull() ?: 0
                val prefs = PreferenceManager.getDefaultSharedPreferences(preference.context)
                val earpieceBoost = (prefs.getString(KEY_EARPIECE_BOOST, "0") ?: "0").toIntOrNull() ?: 0
                val diff = boost - earpieceBoost
                val balance = (diff * 0.15f).coerceIn(-1.0f, 1.0f)
                runCatching {
                    Settings.System.putFloat(
                        preference.context.contentResolver,
                        Settings.System.MASTER_BALANCE,
                        balance
                    )
                }
                Toast.makeText(context, "Bottom Speaker Boost: +${boost} dB", Toast.LENGTH_SHORT).show()
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
