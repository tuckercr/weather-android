package com.fangjet.weather.ui.navigation

import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.fangjet.weather.analytics.CityAdded
import com.fangjet.weather.analytics.CityAddedSource
import com.fangjet.weather.analytics.OnboardingStepShown
import com.fangjet.weather.model.CityListViewModel
import com.fangjet.weather.model.OnboardingStep
import com.fangjet.weather.model.WelcomeViewModel
import com.fangjet.weather.ui.AddLocationRoute
import com.fangjet.weather.ui.CurrentWeatherRoute
import com.fangjet.weather.ui.LocationsRoute
import com.fangjet.weather.ui.NotificationSettingsRoute
import com.fangjet.weather.ui.OnboardingDogScreen
import com.fangjet.weather.ui.OnboardingLocationScreen
import com.fangjet.weather.ui.OnboardingNotificationScreen
import com.fangjet.weather.ui.SettingsRoute
import com.fangjet.weather.ui.WelcomeScreen

object WeatherDestinations {
    const val LOADING = "loading"
    const val WELCOME = "welcome"
    const val DOG_QUESTION = "dog_question"
    const val NOTIFICATION_PERMISSION = "notification_permission"
    const val LOCATION_PERMISSION = "location_permission"
    const val CURRENT = "current"
    const val SETTINGS = "settings"
    const val NOTIFICATION_SETTINGS = "notification_settings"
    const val LOCATIONS = "locations"
    const val ADD_LOCATION = "add_location"
}

/** The name of a location just added, handed back to Manage Locations to confirm. */
private const val LOCATION_ADDED = "location_added"

@Composable
fun WeatherNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    val activity = LocalActivity.current as ComponentActivity
    val welcomeViewModel = hiltViewModel<WelcomeViewModel>(viewModelStoreOwner = activity)
    val cityListViewModel = hiltViewModel<CityListViewModel>(viewModelStoreOwner = activity)
    val step by welcomeViewModel.step.collectAsStateWithLifecycle()
    NavHost(
        navController = navController,
        startDestination = WeatherDestinations.LOADING,
        modifier = modifier,
    ) {
        composable(WeatherDestinations.LOADING) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            LaunchedEffect(step) {
                val next = when (step ?: return@LaunchedEffect) {
                    OnboardingStep.WELCOME -> WeatherDestinations.WELCOME
                    OnboardingStep.NOTIFICATIONS -> WeatherDestinations.NOTIFICATION_PERMISSION
                    OnboardingStep.LOCATION -> WeatherDestinations.LOCATION_PERMISSION
                    OnboardingStep.DONE -> WeatherDestinations.CURRENT
                }
                navController.navigate(next) {
                    popUpTo(WeatherDestinations.LOADING) { inclusive = true }
                    launchSingleTop = true
                }
            }
        }

        composable(WeatherDestinations.WELCOME) {
            LaunchedEffect(Unit) { welcomeViewModel.stepShown(OnboardingStepShown.WELCOME) }
            WelcomeScreen(
                onGetStarted = {
                    welcomeViewModel.completeWelcome()
                    navController.navigate(WeatherDestinations.DOG_QUESTION) {
                        popUpTo(WeatherDestinations.WELCOME) { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )
        }

        // Part of a new user's first run only: not resumed on a later launch, and existing users
        // aren't asked after an update (they have the switch in Settings).
        composable(WeatherDestinations.DOG_QUESTION) {
            LaunchedEffect(Unit) { welcomeViewModel.stepShown(OnboardingStepShown.DOG) }
            OnboardingDogScreen(
                onAnswered = { hasDog ->
                    welcomeViewModel.answerHasDog(hasDog)
                    navController.navigate(WeatherDestinations.NOTIFICATION_PERMISSION) {
                        popUpTo(WeatherDestinations.DOG_QUESTION) { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )
        }

        composable(WeatherDestinations.NOTIFICATION_PERMISSION) {
            LaunchedEffect(Unit) { welcomeViewModel.stepShown(OnboardingStepShown.NOTIFICATIONS) }
            val promise by welcomeViewModel.notificationPromise.collectAsStateWithLifecycle()
            OnboardingNotificationScreen(
                hasDog = promise.hasDog,
                walkTimeEnabled = promise.walkTimeEnabled,
                rainEnabled = promise.rainEnabled,
                onDone = { asked ->
                    welcomeViewModel.completeNotificationOnboarding(asked)
                    navController.navigate(WeatherDestinations.LOCATION_PERMISSION) {
                        popUpTo(WeatherDestinations.NOTIFICATION_PERMISSION) { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )
        }

        composable(WeatherDestinations.LOCATION_PERMISSION) {
            LaunchedEffect(Unit) { welcomeViewModel.stepShown(OnboardingStepShown.LOCATION) }
            OnboardingLocationScreen(
                onLocationResolved = { location ->
                    cityListViewModel.addLocation(location, CityAdded(CityAddedSource.MY_LOCATION))
                    welcomeViewModel.completeLocationOnboarding()
                    navController.navigate(WeatherDestinations.CURRENT) {
                        popUpTo(WeatherDestinations.LOCATION_PERMISSION) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onSkip = { refused ->
                    welcomeViewModel.completeLocationOnboarding(asked = refused)
                    navController.navigate(WeatherDestinations.CURRENT) {
                        popUpTo(WeatherDestinations.LOCATION_PERMISSION) { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )
        }

        composable(WeatherDestinations.CURRENT) {
            CurrentWeatherRoute(
                onOpenSettings = {
                    navController.navigate(WeatherDestinations.SETTINGS)
                },
                onAddLocation = {
                    navController.navigate(WeatherDestinations.ADD_LOCATION) { launchSingleTop = true }
                },
            )
        }

        composable(WeatherDestinations.SETTINGS) {
            SettingsRoute(
                onNavigateBack = { navController.popBackStack() },
                onOpenNotifications = {
                    navController.navigate(WeatherDestinations.NOTIFICATION_SETTINGS)
                },
                onOpenLocations = {
                    navController.navigate(WeatherDestinations.LOCATIONS)
                },
            )
        }

        composable(WeatherDestinations.NOTIFICATION_SETTINGS) {
            NotificationSettingsRoute(onNavigateBack = { navController.popBackStack() })
        }

        composable(WeatherDestinations.LOCATIONS) { entry ->
            val locationAdded by entry.savedStateHandle
                .getStateFlow<String?>(LOCATION_ADDED, null)
                .collectAsStateWithLifecycle()
            LocationsRoute(
                locations = cityListViewModel.locations.collectAsStateWithLifecycle().value,
                onReorder = cityListViewModel::reorderLocations,
                onRemove = cityListViewModel::removeLocation,
                onNavigateBack = { navController.popBackStack() },
                onAddLocation = {
                    navController.navigate(WeatherDestinations.ADD_LOCATION) { launchSingleTop = true }
                },
                locationAdded = locationAdded,
                onLocationAddedShown = { entry.savedStateHandle[LOCATION_ADDED] = null },
            )
        }

        // A screen of its own, closed with its X. Adding goes back to where it was opened from;
        // Go to (a place already saved) goes to the weather screen showing it.
        composable(WeatherDestinations.ADD_LOCATION) {
            // Only while it's the screen showing, so a second tap doesn't pop what's under it.
            fun showing() = navController.currentDestination?.route == WeatherDestinations.ADD_LOCATION
            AddLocationRoute(
                locations = cityListViewModel.locations.collectAsStateWithLifecycle().value,
                onAdd = { location, name, added ->
                    if (showing()) {
                        cityListViewModel.addLocation(location, added)
                        navController.previousBackStackEntry?.savedStateHandle?.set(LOCATION_ADDED, name)
                        navController.popBackStack()
                    }
                },
                onGoTo = { index ->
                    if (showing()) {
                        cityListViewModel.setActiveIndex(index)
                        navController.popBackStack(WeatherDestinations.CURRENT, inclusive = false)
                    }
                },
                onClose = { if (showing()) navController.popBackStack() },
            )
        }
    }
}
