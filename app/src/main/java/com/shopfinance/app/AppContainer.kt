package com.shopfinance.app

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings
import com.shopfinance.app.data.auth.AuthRepository
import com.shopfinance.app.data.auth.FirebaseAuthRepository
import com.shopfinance.app.data.debts.DebtRepository
import com.shopfinance.app.data.debts.FirestoreDebtRepository
import com.shopfinance.app.data.ledger.FirestoreLedgerRepository
import com.shopfinance.app.data.ledger.LedgerRepository
import com.shopfinance.app.data.shop.FirestoreShopRepository
import com.shopfinance.app.data.shop.ShopRepository

/**
 * The app's long-lived objects, built once per process by [ShopFinanceApplication]
 * (manual dependency injection, no framework).
 *
 * Firebase is only initialised when the build had app/google-services.json. Check
 * [firebaseConfigured] first: without it, reading [authRepository], [shopRepository],
 * [ledgerRepository] or [debtRepository] throws [IllegalStateException].
 */
class AppContainer(context: Context) {

    /** True when Firebase was initialised from google-services.json at startup. */
    val firebaseConfigured: Boolean = FirebaseApp.getApps(context.applicationContext).isNotEmpty()

    /**
     * The Firestore instance, with its settings applied exactly once, before its first use.
     * Firestore refuses settings changes after it has started.
     */
    private val firestore: FirebaseFirestore by lazy {
        requireFirebase()
        FirebaseFirestore.getInstance().apply {
            firestoreSettings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(
                    PersistentCacheSettings.newBuilder()
                        .setSizeBytes(CACHE_SIZE_BYTES)
                        .build(),
                )
                .build()
        }
    }

    val authRepository: AuthRepository by lazy {
        requireFirebase()
        FirebaseAuthRepository(FirebaseAuth.getInstance())
    }

    val shopRepository: ShopRepository by lazy {
        FirestoreShopRepository(firestore)
    }

    val ledgerRepository: LedgerRepository by lazy {
        FirestoreLedgerRepository(firestore)
    }

    val debtRepository: DebtRepository by lazy {
        FirestoreDebtRepository(firestore)
    }

    private fun requireFirebase() {
        check(firebaseConfigured) {
            "Firebase is not configured: add app/google-services.json and rebuild"
        }
    }

    private companion object {
        /** Offline cache size on the device. */
        const val CACHE_SIZE_BYTES = 100L * 1024 * 1024
    }
}
