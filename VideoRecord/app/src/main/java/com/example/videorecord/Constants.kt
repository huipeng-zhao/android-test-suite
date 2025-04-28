package ai.looki.companion.devo_main

import android.os.Environment
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.File

@Suppress("unused")
object PreferencesKeys {
    val MATE_PHONE_MAC = stringPreferencesKey("mate_phone_bt_mac")
    val USER_LOCAL_DEVICE_NAME = stringPreferencesKey("local_device_name_by_user")
    val USER_ID = stringPreferencesKey("user_id")
    // version contains target upgrade version, non-empty means upgraded and not notify the phone yet.
    // failure contains the reason, empty failure means success.
    val APK_UPGRADE_VERSION = stringPreferencesKey("apk_upgrade_version")
    val APK_UPGRADE_FAILURE = stringPreferencesKey("apk_upgrade_result")
    val SYS_UPGRADE_VERSION = stringPreferencesKey("sys_upgrade_version")
    val SYS_UPGRADE_FAILURE = stringPreferencesKey("sys_upgrade_result")
    val DEVICE_SECRET = stringPreferencesKey("device_secret")
}

const val PRODUCT_NAME = "DEVO"
const val BRAND_NAME = "Looki"
const val DEFAULT_LOCAL_DEVICE_NAME = "$BRAND_NAME $PRODUCT_NAME"

val DIR_PUBLIC_ROOT = Environment.getExternalStorageDirectory()!!
val DIR_BASE = File(DIR_PUBLIC_ROOT, "DCIM")
val DIR_IMAGE_USER_IMAGE = File(DIR_BASE, "Image/User/Image")
val DIR_IMAGE_USER_THUMB = File(DIR_BASE, "Image/User/Thumb")
val DIR_IMAGE_AUTO_IMAGE = File(DIR_BASE, "Image/Auto/Image")
val DIR_IMAGE_AUTO_THUMB = File(DIR_BASE, "Image/Auto/Thumb")
val DIR_VIDEO_USER_VIDEO = File(DIR_BASE, "Video/User/Video")
val DIR_VIDEO_USER_THUMB = File(DIR_BASE, "Video/User/Thumb")
val DIR_VIDEO_USER_AUDIO = File(DIR_BASE, "Video/User/Audio")
val DIR_VIDEO_USER_IMU   = File(DIR_BASE, "Video/User/Imu")
val DIR_VIDEO_USER_TIME  = File(DIR_BASE, "Video/User/Time")
val DIR_VIDEO_AUTO_VIDEO = File(DIR_BASE, "Video/Auto/Video")
val DIR_VIDEO_AUTO_THUMB = File(DIR_BASE, "Video/Auto/Thumb")
val DIR_VIDEO_AUTO_AUDIO = File(DIR_BASE, "Video/Auto/Audio")
val DIR_VIDEO_AUTO_IMU   = File(DIR_BASE, "Video/Auto/Imu")
val DIR_VIDEO_AUTO_TIME  = File(DIR_BASE, "Video/Auto/Time")
val DIR_AUDIO_USER       = File(DIR_BASE, "Audio/User")
val DIR_UPGRADE          = File(DIR_BASE, "Upgrade")
val DIR_LOG              = File(DIR_BASE, ".Debug/Log")
val SHARED_DIRS = setOf(
    DIR_IMAGE_USER_IMAGE,
    DIR_IMAGE_USER_THUMB,
    DIR_IMAGE_AUTO_IMAGE,
    DIR_IMAGE_AUTO_THUMB,
    DIR_VIDEO_USER_VIDEO,
    DIR_VIDEO_USER_THUMB,
    DIR_VIDEO_USER_AUDIO,
    DIR_VIDEO_USER_IMU,
    DIR_VIDEO_AUTO_VIDEO,
    DIR_VIDEO_AUTO_THUMB,
    DIR_VIDEO_AUTO_AUDIO,
    DIR_VIDEO_AUTO_IMU,
    DIR_AUDIO_USER,
    DIR_UPGRADE,
    DIR_LOG,
)
val ALL_DIRS = SHARED_DIRS

const val DEBUG_USE_FIXED_FILE_SERVER = false

// control need to auth device first.
const val NEED_AUTH_DEVICE_ONOFF = false


enum class ShellColor(val value: Char) {
    Black('B'), Green('G'), White('W'), Unknown(Char(0));

    companion object {
        fun fromChar(value: Char?): ShellColor {
            return when (value) {
                'B' -> Black
                'G' -> Green
                'W' -> White
                else -> Unknown
            }
        }
    }
}