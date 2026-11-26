package com.firestarterpro.templartv.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.firestarterpro.templartv.FeatureFlags
import com.firestarterpro.templartv.Flag
import com.firestarterpro.templartv.google.GoogleHomeHandoff
import com.firestarterpro.templartv.home.BigBoxHome
import com.firestarterpro.templartv.ui.VistaSignals
import com.firestarterpro.templartv.ui.apps.AppsScreen
import com.firestarterpro.templartv.ui.components.AppContextMenu
import com.firestarterpro.templartv.ui.content.ContentGrid
import com.firestarterpro.templartv.ui.content.LibraryScreen
import com.firestarterpro.templartv.ui.content.SearchScreen

@Composable
fun HomeScreen(
    onOpenQuickSettings: () -> Unit = {},
    viewModel: HomeViewModel = viewModel(),
) {
    val apps by viewModel.apps.collectAsStateWithLifecycle()
    val sections by viewModel.sections.collectAsStateWithLifecycle()
    val favouritesSet by viewModel.favourites.collectAsStateWithLifecycle()
    val movies by viewModel.movies.collectAsStateWithLifecycle()
    val shows by viewModel.shows.collectAsStateWithLifecycle()
    val continueWatching by viewModel.continueWatching.collectAsStateWithLifecycle()
    val allContent by viewModel.allContent.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val flags by remember { FeatureFlags.observe(context) }.collectAsStateWithLifecycle(initialValue = emptyMap())
    var bigBoxFailed by remember { mutableStateOf(false) }
    val bigBox = (flags[Flag.BIG_BOX_HOME] ?: Flag.BIG_BOX_HOME.defaultEnabled) && !bigBoxFailed
    val openGoogleHome = {
        if (!GoogleHomeHandoff.openGoogleHome(context)) {
            Toast.makeText(context, "Hold the Home button on your remote to open the Google Home panel.", Toast.LENGTH_LONG).show()
        }
    }

    var tab by remember { mutableStateOf(VistaTab.FOR_YOU) }
    var searchOpen by remember { mutableStateOf(false) }
    var menuApp by remember { mutableStateOf<com.firestarterpro.templartv.domain.AppEntry?>(null) }
    val topBarFocus = remember { FocusRequester() }
    val contentFocus = remember { FocusRequester() }
    val resetSignal by VistaSignals.reset.collectAsStateWithLifecycle()

    LaunchedEffect(resetSignal) {
        if (resetSignal > 0) {
            tab = VistaTab.FOR_YOU
            searchOpen = false
        }
    }

    val favouriteApps = remember(apps, favouritesSet) { apps.filter { it.packageName in favouritesSet } }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        when (tab) {
            VistaTab.FOR_YOU -> if (bigBox) {
                BigBoxHome(
                    apps = apps,
                    cards = allContent,
                    continueCards = continueWatching,
                    favouriteApps = favouritesSet,
                    onLaunchApp = viewModel::launch,
                    onLaunchCard = viewModel::launchContent,
                    onAppLongPress = { menuApp = it },
                    onOpenSearch = { searchOpen = true },
                    onFailed = { bigBoxFailed = true },
                    topBarFocus = topBarFocus,
                    contentFocus = contentFocus,
                    resetSignal = resetSignal,
                )
            } else if (sections.isEmpty()) Loading() else HomeTab(
                    sections = sections,
                    favourites = favouritesSet,
                    onLaunch = viewModel::launch,
                    onLaunchContent = viewModel::launchContent,
                    onAppLongPress = { menuApp = it },
                    topBarFocus = topBarFocus,
                    contentFocus = contentFocus,
                    resetSignal = resetSignal,
                )

            VistaTab.MOVIES -> ContentGrid("Movies", movies, viewModel::launchContent, contentFocus = contentFocus)
            VistaTab.SHOWS -> ContentGrid("Shows", shows, viewModel::launchContent, contentFocus = contentFocus)
            VistaTab.APPS -> if (apps.isEmpty()) Loading() else AppsScreen(
                apps = apps,
                onLaunch = viewModel::launch,
                onLongPress = { menuApp = it },
                contentFocus = contentFocus,
            )
            VistaTab.LIBRARY -> LibraryScreen(
                favourites = favouriteApps,
                continueWatching = continueWatching,
                onLaunchApp = viewModel::launch,
                onLaunchContent = viewModel::launchContent,
                contentFocus = contentFocus,
            )
        }

        VistaTopBar(
            selected = tab,
            onSelect = { tab = it },
            onOpenQuickSettings = onOpenQuickSettings,
            onOpenSearch = { searchOpen = true },
            onOpenGoogleHome = openGoogleHome,
            modifier = Modifier.align(Alignment.TopStart),
            barFocusRequester = topBarFocus,
            contentFocus = contentFocus,
        )

        if (searchOpen) {
            SearchScreen(
                apps = apps,
                content = allContent,
                onLaunchApp = viewModel::launch,
                onLaunchContent = viewModel::launchContent,
                onDismiss = { searchOpen = false },
            )
        }

        AppContextMenu(
            app = menuApp,
            isFavourite = menuApp?.packageName in favouritesSet,
            onDismiss = { menuApp = null },
            onOpen = viewModel::launch,
            onToggleFavourite = viewModel::toggleFavourite,
            onAppInfo = viewModel::openAppInfo,
            onUninstall = viewModel::uninstall,
        )
    }
}

@Composable
private fun Loading() {
    Box(Modifier.fillMaxSize()) {
        Text(
            text = "Loading…",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}
