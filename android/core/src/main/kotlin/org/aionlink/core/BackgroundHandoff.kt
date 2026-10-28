package org.aionlink.core

/** Keep only a user-started, active handoff while briefly outside the activity. */
fun preserveBackgroundHandoff(bleActive: Boolean, networkConnectingOrConnected: Boolean): Boolean =
    bleActive || networkConnectingOrConnected
