package dev.thyra.android

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data object ConnectionRoute : NavKey
@Serializable data object AuthenticationRoute : NavKey
@Serializable data object AgentsRoute : NavKey
@Serializable data object SessionsRoute : NavKey
@Serializable data object ChatRoute : NavKey
