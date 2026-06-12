package com.vitpool.app

import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

internal fun FirebaseAppCheck.installVitPoolAppCheckProvider() {
    installAppCheckProviderFactory(PlayIntegrityAppCheckProviderFactory.getInstance())
}
