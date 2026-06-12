package com.vitpool.app

import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

internal fun FirebaseAppCheck.installVitPoolAppCheckProvider() {
    installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance())
}
