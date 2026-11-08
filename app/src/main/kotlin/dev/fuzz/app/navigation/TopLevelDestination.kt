package dev.fuzz.app.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.ui.graphics.vector.ImageVector
import dev.fuzz.app.R
import dev.fuzz.feature.home.HomeRoute
import dev.fuzz.feature.library.LibraryRoute
import dev.fuzz.feature.search.SearchRoute
import dev.fuzz.feature.settings.ProfileRoute
import kotlin.reflect.KClass

/** Четыре вкладки нижней панели (раздел 6 ТЗ). */
enum class TopLevelDestination(
    val route: Any,
    val routeClass: KClass<*>,
    @param:StringRes val label: Int,
    val selectedIcon: ImageVector,
    val icon: ImageVector,
) {
    Home(HomeRoute, HomeRoute::class, R.string.tab_home, Icons.Rounded.Home, Icons.Outlined.Home),
    Search(SearchRoute, SearchRoute::class, R.string.tab_search, Icons.Rounded.Search, Icons.Outlined.Search),
    Library(
        LibraryRoute,
        LibraryRoute::class,
        R.string.tab_library,
        Icons.Rounded.LibraryMusic,
        Icons.Outlined.LibraryMusic,
    ),
    Profile(ProfileRoute, ProfileRoute::class, R.string.tab_profile, Icons.Rounded.Person, Icons.Outlined.Person),
}
