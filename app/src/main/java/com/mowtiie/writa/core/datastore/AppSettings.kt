package com.mowtiie.writa.core.datastore

import com.mowtiie.writa.core.ui.theme.Contrast
import com.mowtiie.writa.feature.notes.Sort

enum class Theme(
    val key: String,
) {
    System("system"),
    Battery("battery"),
    Light("light"),
    Dark("dark"),
    ;

    /** Whether the app is dark, given what the system says and whether battery saver is on. */
    fun isDark(
        systemDark: Boolean,
        batterySaver: Boolean,
    ): Boolean =
        when (this) {
            System -> systemDark
            Battery -> batterySaver
            Light -> false
            Dark -> true
        }
}

data class AppSettings(
    val theme: Theme = Theme.System,
    val contrast: Contrast = Contrast.Low,
    val dynamicColor: Boolean = false,
    val screenPrivacy: Boolean = false,
    val sort: Sort = Sort.NEWEST,
)
