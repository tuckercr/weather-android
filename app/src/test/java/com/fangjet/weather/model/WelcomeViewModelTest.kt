package com.fangjet.weather.model

import com.fangjet.weather.analytics.OnboardingPermissionResult
import com.fangjet.weather.analytics.OnboardingStepShown
import com.fangjet.weather.worker.NotificationKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class WelcomeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `initial state is null before prefs read`() =
        runTest {
            val viewModel = viewModel(
                hasSeenWelcomeOnce = { true },
                locationOnboardingDoneOnce = { false },
            )

            assertNull(viewModel.onboardingState.value)
        }

    @Test
    fun `initial state reflects stored flags`() =
        runTest {
            val viewModel = viewModel(
                hasSeenWelcomeOnce = { true },
                locationOnboardingDoneOnce = { true },
            )

            mainDispatcherRule.testDispatcher.scheduler.runCurrent()

            assertEquals(
                OnboardingState(
                    hasSeenWelcome = true,
                    notificationOnboardingDone = false,
                    locationOnboardingDone = true,
                ),
                viewModel.onboardingState.value,
            )
        }

    @Test
    fun `complete welcome marks state done and persists flag`() =
        runTest {
            val persistedValues = mutableListOf<Boolean>()
            val viewModel = viewModel(
                hasSeenWelcomeOnce = { false },
                setHasSeenWelcome = { persistedValues += it },
            )
            mainDispatcherRule.testDispatcher.scheduler.runCurrent()
            assertEquals(false, viewModel.onboardingState.value?.hasSeenWelcome)

            viewModel.completeWelcome()
            mainDispatcherRule.testDispatcher.scheduler.runCurrent()

            assertEquals(true, viewModel.onboardingState.value?.hasSeenWelcome)
            assertEquals(listOf(true), persistedValues)
        }

    @Test
    fun `complete notification onboarding marks state done and persists`() =
        runTest {
            val persisted = mutableListOf<Unit>()
            val viewModel = viewModel(
                notificationOnboardingDoneOnce = { false },
                setNotificationOnboardingDone = { persisted += Unit },
            )
            mainDispatcherRule.testDispatcher.scheduler.runCurrent()
            assertEquals(false, viewModel.onboardingState.value?.notificationOnboardingDone)

            viewModel.completeNotificationOnboarding()
            mainDispatcherRule.testDispatcher.scheduler.runCurrent()

            assertEquals(true, viewModel.onboardingState.value?.notificationOnboardingDone)
            assertEquals(1, persisted.size)
        }

    @Test
    fun `complete location onboarding marks state done and persists`() =
        runTest {
            val persisted = mutableListOf<Unit>()
            val viewModel = viewModel(
                locationOnboardingDoneOnce = { false },
                setLocationOnboardingDone = { persisted += Unit },
            )
            mainDispatcherRule.testDispatcher.scheduler.runCurrent()
            assertEquals(false, viewModel.onboardingState.value?.locationOnboardingDone)

            viewModel.completeLocationOnboarding()
            mainDispatcherRule.testDispatcher.scheduler.runCurrent()

            assertEquals(true, viewModel.onboardingState.value?.locationOnboardingDone)
            assertEquals(1, persisted.size)
        }

    @Test
    fun `answering no dog turns the walk card off, and yes turns it on`() =
        runTest {
            val saved = mutableListOf<Boolean>()
            val vm = viewModel(setShowWalkCard = { saved += it })

            vm.answerHasDog(hasDog = false)
            vm.answerHasDog(hasDog = true)
            mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(listOf(false, true), saved)
        }

    @Test
    fun `the first step is wherever onboarding was left`() =
        runTest {
            suspend fun stepFor(
                welcome: Boolean,
                notifications: Boolean,
                location: Boolean,
            ): OnboardingStep? {
                val vm = viewModel(
                    hasSeenWelcomeOnce = { welcome },
                    notificationOnboardingDoneOnce = { notifications },
                    locationOnboardingDoneOnce = { location },
                )
                mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()
                return vm.step.value
            }

            assertEquals(OnboardingStep.WELCOME, stepFor(welcome = false, notifications = false, location = false))
            assertEquals(OnboardingStep.NOTIFICATIONS, stepFor(welcome = true, notifications = false, location = false))
            assertEquals(OnboardingStep.LOCATION, stepFor(welcome = true, notifications = true, location = false))
            assertEquals(OnboardingStep.DONE, stepFor(welcome = true, notifications = true, location = true))
        }

    @Test
    fun `the notification promise follows the dog answer and Remote Config`() =
        runTest {
            val vm = viewModel(hasDogOnce = { true }, remotelyEnabled = { it != NotificationKind.RAIN })
            mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()
            assertEquals(NotificationPromise(hasDog = true, walkTimeEnabled = true, rainEnabled = false), vm.notificationPromise.value)

            vm.answerHasDog(hasDog = false)
            assertEquals(false, vm.notificationPromise.value.hasDog)
        }

    @Test
    fun `a Remote Config activation during onboarding updates the notification promise`() =
        runTest {
            // A settable Remote Config: rain alerts off, then on once a fetch is activated.
            var rainOn = false
            val activations = MutableStateFlow(0)
            val vm = viewModel(
                remotelyEnabled = { it != NotificationKind.RAIN || rainOn },
                remoteConfigActivations = activations,
            )
            mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()
            assertEquals(false, vm.notificationPromise.value.rainEnabled)

            rainOn = true
            activations.value = 1
            mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()
            assertEquals(true, vm.notificationPromise.value.rainEnabled)
        }

    // --- Analytics ---

    @Test
    fun `each step shown is logged once, however often its screen recomposes`() {
        val shown = mutableListOf<OnboardingStepShown>()
        val vm = viewModel(onStepShown = { shown += it })

        vm.stepShown(OnboardingStepShown.WELCOME)
        vm.stepShown(OnboardingStepShown.WELCOME)
        vm.stepShown(OnboardingStepShown.DOG)
        vm.stepShown(OnboardingStepShown.NOTIFICATIONS)
        vm.stepShown(OnboardingStepShown.NOTIFICATIONS)
        vm.stepShown(OnboardingStepShown.LOCATION)

        assertEquals(OnboardingStepShown.entries, shown)
    }

    private data class Completion(
        val hasDog: Boolean,
        val notifications: OnboardingPermissionResult,
        val location: OnboardingPermissionResult,
    )

    /** Onboarding's end, as logged, after [steps] with notifications and location allowed or not. */
    private fun completion(
        notificationsHeld: Boolean = false,
        locationHeld: Boolean = false,
        steps: WelcomeViewModel.() -> Unit,
    ): Completion? {
        var completion: Completion? = null
        val vm = viewModel(
            hasDogOnce = { true },
            onOnboardingComplete = { dog, notifications, location -> completion = Completion(dog, notifications, location) },
            notificationsHeld = { notificationsHeld },
            locationHeld = { locationHeld },
        )
        mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()
        vm.steps()
        mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()
        return completion
    }

    @Test
    fun `onboarding complete says what was answered`() {
        // Both allowed when asked, with a dog.
        assertEquals(
            Completion(true, OnboardingPermissionResult.GRANTED, OnboardingPermissionResult.GRANTED),
            completion(notificationsHeld = true, locationHeld = true) {
                completeNotificationOnboarding(asked = true)
                completeLocationOnboarding()
            },
        )
        // Both refused when asked, and no dog.
        assertEquals(
            Completion(false, OnboardingPermissionResult.DENIED, OnboardingPermissionResult.DENIED),
            completion {
                answerHasDog(false)
                completeNotificationOnboarding(asked = true)
                completeLocationOnboarding(asked = true)
            },
        )
        // "Not now" and "Enter a town or city manually".
        assertEquals(
            Completion(true, OnboardingPermissionResult.SKIPPED, OnboardingPermissionResult.SKIPPED),
            completion {
                completeNotificationOnboarding(asked = false)
                completeLocationOnboarding(asked = false)
            },
        )
    }

    @Test
    fun `onboarding resumed after a restart reports what's allowed, or skipped`() {
        // The notification step was done before the restart: its answer is unknown.
        assertEquals(
            Completion(true, OnboardingPermissionResult.GRANTED, OnboardingPermissionResult.SKIPPED),
            completion(notificationsHeld = true) { completeLocationOnboarding() },
        )
        assertEquals(
            Completion(true, OnboardingPermissionResult.SKIPPED, OnboardingPermissionResult.SKIPPED),
            completion { completeLocationOnboarding() },
        )
    }

    private fun viewModel(
        hasSeenWelcomeOnce: suspend () -> Boolean = { false },
        notificationOnboardingDoneOnce: suspend () -> Boolean = { false },
        locationOnboardingDoneOnce: suspend () -> Boolean = { false },
        setHasSeenWelcome: suspend (Boolean) -> Unit = {},
        setNotificationOnboardingDone: suspend () -> Unit = {},
        setLocationOnboardingDone: suspend () -> Unit = {},
        setShowWalkCard: suspend (Boolean) -> Unit = {},
        hasDogOnce: suspend () -> Boolean = { true },
        remotelyEnabled: (NotificationKind) -> Boolean = { true },
        remoteConfigActivations: Flow<Int> = flowOf(0),
        onOnboardingComplete: (Boolean, OnboardingPermissionResult, OnboardingPermissionResult) -> Unit = { _, _, _ -> },
        onStepShown: (OnboardingStepShown) -> Unit = {},
        notificationsHeld: () -> Boolean = { false },
        locationHeld: () -> Boolean = { false },
    ) = WelcomeViewModel(
        hasSeenWelcomeOnce = hasSeenWelcomeOnce,
        notificationOnboardingDoneOnce = notificationOnboardingDoneOnce,
        locationOnboardingDoneOnce = locationOnboardingDoneOnce,
        setHasSeenWelcome = setHasSeenWelcome,
        setNotificationOnboardingDone = setNotificationOnboardingDone,
        setLocationOnboardingDone = setLocationOnboardingDone,
        setShowWalkCard = setShowWalkCard,
        hasDogOnce = hasDogOnce,
        remotelyEnabled = remotelyEnabled,
        remoteConfigActivations = remoteConfigActivations,
        onOnboardingComplete = onOnboardingComplete,
        onStepShown = onStepShown,
        notificationsHeld = notificationsHeld,
        locationHeld = locationHeld,
    )

    class MainDispatcherRule(
        val testDispatcher: TestDispatcher = StandardTestDispatcher(),
    ) : TestWatcher() {
        override fun starting(description: Description) {
            Dispatchers.setMain(testDispatcher)
        }

        override fun finished(description: Description) {
            Dispatchers.resetMain()
        }
    }
}
