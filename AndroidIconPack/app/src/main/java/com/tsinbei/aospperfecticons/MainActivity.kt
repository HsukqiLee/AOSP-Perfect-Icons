package com.tsinbei.aospperfecticons

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.core.widget.doOnTextChanged
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.json.JSONArray

class MainActivity : AppCompatActivity() {

    private lateinit var allEntries: List<IconEntry>
    private lateinit var visibleEntries: MutableList<IconEntry>
    private lateinit var iconAdapter: IconAdapter
    private lateinit var subtitleView: TextView
    private var installedLauncherPackages: Set<String> = emptySet()

    private var currentQuery: String = ""
    private var lastBackPressedAt: Long = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_main)

        val rootView: View = findViewById(R.id.rootContainer)
        val recyclerView: RecyclerView = findViewById(R.id.iconsRecycler)
        subtitleView = findViewById(R.id.subtitleText)
        val searchInput: EditText = findViewById(R.id.searchInput)

        val layoutHome: View = findViewById(R.id.layoutHome)
        val layoutAbout: View = findViewById(R.id.layoutAbout)
        val bottomNavigation: BottomNavigationView = findViewById(R.id.bottomNavigation)
        val versionText: TextView = findViewById(R.id.versionText)
        val btnLanguageSettings: View = findViewById(R.id.btnLanguageSettings)
        val currentLanguageText: TextView = findViewById(R.id.currentLanguageText)
        val btnGithubLink: View = findViewById(R.id.btnGithubLink)
        val btnOriginalGithubLink: View = findViewById(R.id.btnOriginalGithubLink)

        val baseRootPaddingLeft = rootView.paddingLeft
        val baseRootPaddingTop = rootView.paddingTop
        val baseRootPaddingRight = rootView.paddingRight
        val baseRootPaddingBottom = rootView.paddingBottom
        val baseRecyclerPaddingLeft = recyclerView.paddingLeft
        val baseRecyclerPaddingTop = recyclerView.paddingTop
        val baseRecyclerPaddingRight = recyclerView.paddingRight
        val baseRecyclerPaddingBottom = recyclerView.paddingBottom

        // Dynamic version name
        val packageInfo = try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                packageManager.getPackageInfo(packageName, 0)
            }
        } catch (e: Exception) {
            null
        }
        versionText.text = "Version ${packageInfo?.versionName ?: "1.0.0"}"

        // Navigation Setup
        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_home -> {
                    layoutHome.visibility = View.VISIBLE
                    layoutAbout.visibility = View.GONE
                    true
                }
                R.id.navigation_about -> {
                    layoutHome.visibility = View.GONE
                    layoutAbout.visibility = View.VISIBLE
                    updateCurrentLanguageText(currentLanguageText)
                    true
                }
                else -> false
            }
        }

        // Restore tab layout visibility state after recreation
        val activeTabId = savedInstanceState?.getInt("active_tab_id") ?: R.id.navigation_home
        bottomNavigation.selectedItemId = activeTabId
        if (activeTabId == R.id.navigation_about) {
            layoutHome.visibility = View.GONE
            layoutAbout.visibility = View.VISIBLE
            updateCurrentLanguageText(currentLanguageText)
        } else {
            layoutHome.visibility = View.VISIBLE
            layoutAbout.visibility = View.GONE
        }

        // Language setting details
        updateCurrentLanguageText(currentLanguageText)
        btnLanguageSettings.setOnClickListener {
            showLanguageSelectorDialog(currentLanguageText)
        }

        // GitHub link
        btnGithubLink.setOnClickListener {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/HsukqiLee/Pixel-Launcher-Icons"))
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "Could not open browser", Toast.LENGTH_SHORT).show()
            }
        }

        // Original GitHub link
        btnOriginalGithubLink.setOnClickListener {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/elliana-wt/Pixel-Launcher-Icons"))
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "Could not open browser", Toast.LENGTH_SHORT).show()
            }
        }

        rootView.isFocusableInTouchMode = true
        rootView.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                clearSearchFocus(searchInput, rootView)
            }
            false
        }

        recyclerView.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                clearSearchFocus(searchInput, rootView)
            }
            false
        }

        searchInput.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                hideKeyboard(searchInput)
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (searchInput.hasFocus()) {
                    clearSearchFocus(searchInput, rootView)
                    return
                }

                if (bottomNavigation.selectedItemId != R.id.navigation_home) {
                    bottomNavigation.selectedItemId = R.id.navigation_home
                    return
                }

                val now = System.currentTimeMillis()
                if (now - lastBackPressedAt <= 2000L) {
                    finish()
                } else {
                    lastBackPressedAt = now
                    Toast.makeText(
                        this@MainActivity,
                        getString(R.string.press_back_again_to_exit),
                        Toast.LENGTH_SHORT
                     ).show()
                }
            }
        })

        ViewCompat.setOnApplyWindowInsetsListener(rootView) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            rootView.updatePadding(
                left = baseRootPaddingLeft + bars.left,
                top = baseRootPaddingTop + bars.top,
                right = baseRootPaddingRight + bars.right,
                bottom = baseRootPaddingBottom
            )
            // No need to add bars.bottom to recyclerView because BottomNavigationView is below it.
            // But we pad bottomNavigation for system navigation bar insets.
            bottomNavigation.updatePadding(
                bottom = bars.bottom
            )
            insets
        }

        allEntries = loadIconEntries()
        refreshInstalledLauncherPackages()
        visibleEntries = allEntries.toMutableList()
        updateSubtitle()

        val cellMinDp = 92f
        val cellMinPx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            cellMinDp,
            resources.displayMetrics
        )
        val spanCount = (resources.displayMetrics.widthPixels / cellMinPx).toInt().coerceAtLeast(2)

        val layoutManager = GridLayoutManager(this, spanCount)
        recyclerView.layoutManager = layoutManager
        iconAdapter = IconAdapter(
            rows = buildDisplayRows(visibleEntries).toMutableList(),
            resolveDrawableId = { drawableName ->
                resources.getIdentifier(drawableName, "drawable", packageName)
            },
            onItemClicked = { entry ->
                showIconDetails(entry)
            }
        )
        layoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                return if (iconAdapter.isHeader(position)) spanCount else 1
            }
        }
        recyclerView.adapter = iconAdapter

        searchInput.doOnTextChanged { text, _, _, _ ->
            currentQuery = text?.toString().orEmpty()
            applyFilter()
        }

        ViewCompat.requestApplyInsets(rootView)
    }

    override fun onResume() {
        super.onResume()
        refreshInstalledLauncherPackages()
        iconAdapter.submitRows(buildDisplayRows(visibleEntries))
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val bottomNavigation: BottomNavigationView? = findViewById(R.id.bottomNavigation)
        if (bottomNavigation != null) {
            outState.putInt("active_tab_id", bottomNavigation.selectedItemId)
        }
    }

    private fun updateCurrentLanguageText(textView: TextView) {
        val currentLocales = AppCompatDelegate.getApplicationLocales()
        val currentText = if (currentLocales.isEmpty) {
            getString(R.string.about_language_system)
        } else {
            val locale = currentLocales.get(0)
            val language = locale?.language
            val script = locale?.script
            val country = locale?.country
            when (language) {
                "en" -> getString(R.string.about_language_en)
                "zh" -> {
                    if (script == "Hant" || country == "TW" || country == "HK") {
                        getString(R.string.about_language_zh_tw)
                    } else {
                        getString(R.string.about_language_zh_cn)
                    }
                }
                else -> getString(R.string.about_language_system)
            }
        }
        textView.text = currentText
    }

    private fun showLanguageSelectorDialog(currentLanguageText: TextView) {
        val languages = arrayOf(
            getString(R.string.about_language_system),
            getString(R.string.about_language_en),
            getString(R.string.about_language_zh_cn),
            getString(R.string.about_language_zh_tw)
        )

        val currentLocales = AppCompatDelegate.getApplicationLocales()
        val checkedItem = if (currentLocales.isEmpty) {
            0
        } else {
            val locale = currentLocales.get(0)
            val language = locale?.language
            val script = locale?.script
            val country = locale?.country
            when (language) {
                "en" -> 1
                "zh" -> {
                    if (script == "Hant" || country == "TW" || country == "HK") 3 else 2
                }
                else -> 0
            }
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.about_language_title)
            .setSingleChoiceItems(languages, checkedItem) { dialog, which ->
                val localeTag = when (which) {
                    1 -> "en"
                    2 -> "zh-Hans"
                    3 -> "zh-Hant"
                    else -> ""
                }
                val locales = if (localeTag.isEmpty()) {
                    LocaleListCompat.getEmptyLocaleList()
                } else {
                    LocaleListCompat.forLanguageTags(localeTag)
                }
                AppCompatDelegate.setApplicationLocales(locales)
                updateCurrentLanguageText(currentLanguageText)
                dialog.dismiss()
            }
            .show()
    }

    private fun loadIconEntries(): List<IconEntry> {
        val text = assets.open("icon_pack_index.json").bufferedReader().use { it.readText() }
        val array = JSONArray(text)
        val list = ArrayList<IconEntry>(array.length())
        for (i in 0 until array.length()) {
            val item = array.getJSONObject(i)
            list += IconEntry(
                name = item.getString("name"),
                drawable = item.getString("drawable"),
                components = buildList {
                    if (item.has("components")) {
                        val componentsArray = item.getJSONArray("components")
                        for (componentIndex in 0 until componentsArray.length()) {
                            add(componentsArray.getString(componentIndex))
                        }
                    }
                }
            )
        }
        return list
    }

    private fun applyFilter() {
        val query = currentQuery.trim()
        if (query.isBlank()) {
            visibleEntries.clear()
            visibleEntries.addAll(allEntries)
        } else {
            val lowerQuery = query.lowercase()
            val filtered = allEntries.filter { entry ->
                entry.matchesQuery(lowerQuery)
            }
            visibleEntries.clear()
            visibleEntries.addAll(filtered)
        }
        iconAdapter.submitRows(buildDisplayRows(visibleEntries))
        updateSubtitle()
    }

    private fun buildDisplayRows(entries: List<IconEntry>): List<IconListRow> {
        val mapped = entries.filter { it.components.isNotEmpty() }
        val unmapped = entries.filter { it.components.isEmpty() }
        val mappedInstalled = mapped.filter { isEntryInstalled(it) }
        val mappedNotInstalled = mapped.filterNot { isEntryInstalled(it) }

        val rows = mutableListOf<IconListRow>()
        if (mapped.isNotEmpty()) {
            rows += IconListRow.Header(getString(R.string.group_mapped_title, mapped.size))
            rows += mappedInstalled.map {
                IconListRow.Item(entry = it, isMapped = true, isInstalled = true)
            }
            rows += mappedNotInstalled.map {
                IconListRow.Item(entry = it, isMapped = true, isInstalled = false)
            }
        }
        if (unmapped.isNotEmpty()) {
            rows += IconListRow.Header(getString(R.string.group_unmapped_title, unmapped.size))
            rows += unmapped.map {
                IconListRow.Item(entry = it, isMapped = false, isInstalled = false)
            }
        }
        return rows
    }

    private fun updateSubtitle() {
        subtitleView.text = when {
            currentQuery.isBlank() -> getString(R.string.icon_count_text, visibleEntries.size)
            else -> getString(R.string.search_result_text, visibleEntries.size, allEntries.size)
        }
    }

    private fun showIconDetails(entry: IconEntry) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_icon_detail, null, false)
        val iconView: ImageView = dialogView.findViewById(R.id.detailIcon)
        val nameView: TextView = dialogView.findViewById(R.id.detailName)
        val packageView: TextView = dialogView.findViewById(R.id.detailPackage)
        val activityView: TextView = dialogView.findViewById(R.id.detailActivity)
        val launchButton: Button = dialogView.findViewById(R.id.launchButton)

        val drawableId = resources.getIdentifier(entry.drawable, "drawable", packageName)
        if (drawableId != 0) {
            iconView.setImageResource(drawableId)
        } else {
            iconView.setImageResource(android.R.drawable.sym_def_app_icon)
        }

        val resolvedComponent = entry.firstLaunchableComponent()
        val packageNameText = entry.firstMappedPackage() ?: getString(R.string.detail_unknown)
        val activityNameText = resolvedComponent?.shortClassName() ?: getString(R.string.detail_unknown)
        val launchable = isEntryInstalled(entry)

        nameView.text = entry.name
        packageView.text = getString(R.string.detail_package_text, packageNameText)
        activityView.text = getString(R.string.detail_activity_text, activityNameText)
        launchButton.isEnabled = launchable
        launchButton.alpha = if (launchable) 1f else 0.5f

        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .create()

        launchButton.setOnClickListener {
            launchMappedApp(entry)
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun launchMappedApp(entry: IconEntry) {
        for (componentText in entry.components) {
            val componentName = parseComponentName(componentText) ?: continue

            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                component = componentName
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            try {
                startActivity(intent)
                return
            } catch (_: android.content.ActivityNotFoundException) {
                // Mapping may point to an old activity in a newer app version.
                continue
            } catch (_: SecurityException) {
                continue
            }
        }

        entry.components
            .asSequence()
            .mapNotNull { parsePackageNameFromComponent(it) }
            .distinct()
            .forEach { packageName ->
                val fallbackIntent = packageManager.getLaunchIntentForPackage(packageName)
                if (fallbackIntent != null) {
                    fallbackIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(fallbackIntent)
                    return
                }
            }

        Toast.makeText(this, getString(R.string.launch_failed), Toast.LENGTH_SHORT).show()
    }

    private fun refreshInstalledLauncherPackages() {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        installedLauncherPackages = packageManager
            .queryIntentActivities(intent, 0)
            .mapNotNull { it.activityInfo?.packageName }
            .toSet()
    }

    private fun isEntryInstalled(entry: IconEntry): Boolean {
        return entry.components.any { componentText ->
            val packageName = parsePackageNameFromComponent(componentText) ?: return@any false
            installedLauncherPackages.contains(packageName)
        }
    }

    private fun parsePackageNameFromComponent(componentText: String): String? {
        val text = componentText.trim()
        if (text.startsWith("PackageInfo{") && text.endsWith("}")) {
            return text.substringAfter("PackageInfo{").substringBeforeLast("}").trim().ifBlank { null }
        }

        val start = componentText.indexOf('{')
        val slash = componentText.indexOf('/')
        val end = componentText.indexOf('}')
        if (start < 0 || slash < 0 || end < 0 || slash <= start + 1) {
            return null
        }
        return componentText.substring(start + 1, slash).trim().ifBlank { null }
    }

    private fun parseComponentName(componentText: String): ComponentName? {
        val start = componentText.indexOf('{')
        val slash = componentText.indexOf('/')
        val end = componentText.indexOf('}')
        if (start < 0 || slash < 0 || end < 0 || slash <= start + 1 || end <= slash + 1) {
            return null
        }

        val packageName = componentText.substring(start + 1, slash)
        val className = componentText.substring(slash + 1, end)
        return ComponentName(packageName, className)
    }

    private fun clearSearchFocus(searchInput: EditText, rootView: View) {
        if (searchInput.hasFocus()) {
            searchInput.clearFocus()
        }
        rootView.requestFocus()
        hideKeyboard(searchInput)
    }

    private fun hideKeyboard(view: View) {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
        imm?.hideSoftInputFromWindow(view.windowToken, 0)
    }
}

private fun IconEntry.firstLaunchableComponent(): ComponentName? {
    return components.asSequence().mapNotNull { componentText ->
        val start = componentText.indexOf('{')
        val slash = componentText.indexOf('/')
        val end = componentText.indexOf('}')
        if (start < 0 || slash < 0 || end < 0 || slash <= start + 1 || end <= slash + 1) {
            null
        } else {
            val packageName = componentText.substring(start + 1, slash)
            val className = componentText.substring(slash + 1, end)
            ComponentName(packageName, className)
        }
    }.firstOrNull()
}

private fun IconEntry.firstMappedPackage(): String? {
    return components.asSequence().mapNotNull { componentText ->
        val text = componentText.trim()
        if (text.startsWith("PackageInfo{") && text.endsWith("}")) {
            text.substringAfter("PackageInfo{").substringBeforeLast("}").trim().ifBlank { null }
        } else {
            val start = text.indexOf('{')
            val slash = text.indexOf('/')
            if (start < 0 || slash <= start + 1) {
                null
            } else {
                text.substring(start + 1, slash).trim().ifBlank { null }
            }
        }
    }.firstOrNull()
}

private fun ComponentName.shortClassName(): String {
    return className.substringAfterLast('.')
}

private fun IconEntry.matchesQuery(query: String): Boolean {
    val componentText = components.joinToString(" ")
    val component = firstLaunchableComponent()
    val packageName = component?.packageName.orEmpty()
    val activityName = component?.className.orEmpty()

    return name.contains(query, ignoreCase = true) ||
        drawable.contains(query, ignoreCase = true) ||
        componentText.contains(query, ignoreCase = true) ||
        packageName.contains(query, ignoreCase = true) ||
        activityName.contains(query, ignoreCase = true)
}
