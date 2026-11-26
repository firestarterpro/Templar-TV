package com.firestarterpro.templartv.home

import android.content.Context
import com.firestarterpro.templartv.domain.AppEntry

/**
 * The owner's own order and hidden list for the Apps row. Kept in plain preferences on the device,
 * so it needs no database change and is fully reversible ("Show hidden apps" in Row Manager).
 */
class AppLayoutStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("templar_app_layout", Context.MODE_PRIVATE)

    val order: List<String> get() = prefs.getString(KEY_ORDER, "").orEmpty().split('\n').filter { it.isNotEmpty() }
    val hidden: Set<String> get() = prefs.getStringSet(KEY_HIDDEN, emptySet()).orEmpty().toSet()

    fun setOrder(packages: List<String>) {
        prefs.edit().putString(KEY_ORDER, packages.joinToString("\n")).apply()
    }

    fun hide(packageName: String) {
        prefs.edit().putStringSet(KEY_HIDDEN, hidden + packageName).apply()
    }

    fun showAll() {
        prefs.edit().putStringSet(KEY_HIDDEN, emptySet()).apply()
    }

    companion object {
        private const val KEY_ORDER = "order"
        private const val KEY_HIDDEN = "hidden"

        /**
         * Apps as the Apps row shows them: hidden ones dropped; favourites first unless the owner
         * has saved an order, in which case that order wins and new apps follow it.
         */
        fun arrange(apps: List<AppEntry>, favourites: Set<String>, order: List<String>, hidden: Set<String>): List<AppEntry> {
            val (fav, rest) = apps.filter { it.packageName !in hidden }.partition { it.packageName in favourites }
            val base = fav + rest
            if (order.isEmpty()) return base
            val byPkg = base.associateBy { it.packageName }
            val listed = order.mapNotNull { byPkg[it] }
            val listedSet = listed.map { it.packageName }.toSet()
            return listed + base.filter { it.packageName !in listedSet }
        }
    }
}
