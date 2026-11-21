package com.example.data

import android.content.Context
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlin.math.abs
import kotlin.random.Random

class TripShieldRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(expectedUid: String? = null): String {
        val uid = auth.currentUser?.uid
            ?: throw IllegalStateException("Please sign in with Google to continue.")
        if (expectedUid != null) {
            check(uid == expectedUid) { "Active account changed; refusing cross-user write." }
        }
        return uid
    }

    fun observeRiderProfile(userId: String): Flow<RiderProfile?> = flow {
        val uid = requireUserId(userId)
        val path = "users/$uid"
        emitAll(
            db.collection("users").document(uid)
                .snapshots()
                .map { snap ->
                    if (snap.exists()) {
                        snap.toObject(
                            RiderProfile::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    } else null
                }
                .catch { error ->
                    handleFlowError(error, OperationType.GET, path, uid)
                    throw error
                }
        )
    }

    fun observeTrips(userId: String): Flow<List<TripRecord>> = flow {
        val uid = requireUserId(userId)
        val path = "users/$uid/trips"
        emitAll(
            db.collection("users").document(uid).collection("trips")
                .snapshots()
                .map { snap ->
                    snap.toObjects(
                        TripRecord::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    handleFlowError(error, OperationType.LIST, path, uid)
                    throw error
                }
        )
    }

    fun observeClaims(userId: String): Flow<List<ClaimRecord>> = flow {
        val uid = requireUserId(userId)
        val path = "users/$uid/claims"
        emitAll(
            db.collection("users").document(uid).collection("claims")
                .snapshots()
                .map { snap ->
                    snap.toObjects(
                        ClaimRecord::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    handleFlowError(error, OperationType.LIST, path, uid)
                    throw error
                }
        )
    }

    fun observeLedger(userId: String): Flow<List<LedgerEntry>> = flow {
        val uid = requireUserId(userId)
        val path = "users/$uid/ledger"
        emitAll(
            db.collection("users").document(uid).collection("ledger")
                .snapshots()
                .map { snap ->
                    snap.toObjects(
                        LedgerEntry::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    handleFlowError(error, OperationType.LIST, path, uid)
                    throw error
                }
        )
    }

    suspend fun ensureRiderProfile(
        userId: String,
        displayName: String?,
        email: String?
    ): Result<Unit> {
        val path = "users/$userId"
        return try {
            val uid = requireUserId(userId)
            val docRef = db.collection("users").document(uid)
            val existing = docRef.get().await()
            if (!existing.exists()) {
                val safeName = displayName?.trim()?.take(80).takeUnless { it.isNullOrBlank() }
                    ?: "TripShield Rider"
                val safeEmail = email?.trim()?.take(120).orEmpty()
                val suffix = (abs(uid.hashCode()) % 9000) + 1000
                val riderCode = "TS-RDR-$suffix"

                val profilePayload = mapOf(
                    "userId" to uid,
                    "displayName" to safeName,
                    "email" to safeEmail,
                    "riderCode" to riderCode,
                    "vehicleNumber" to "KA-01-EQ-4821",
                    "partnerFleet" to "Swiggy",
                    "walletBalanceInr" to 250.0,
                    "voiceAlertsEnabled" to true,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                val openingLedgerRef = docRef.collection("ledger").document()
                val openingLedgerPayload = mapOf(
                    "userId" to uid,
                    "type" to LedgerType.TOPUP.name,
                    "title" to "Welcome Cover Credit",
                    "referenceCode" to riderCode,
                    "amountInr" to 250.0,
                    "balanceAfterInr" to 250.0,
                    "createdAt" to FieldValue.serverTimestamp()
                )

                val batch = db.batch()
                batch.set(docRef, profilePayload)
                batch.set(openingLedgerRef, openingLedgerPayload)
                batch.commit().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun activateTripCover(
        userId: String,
        platformName: String,
        routeSummary: String,
        startLat: Double,
        startLng: Double,
        currentWalletBalance: Double,
        premiumInr: Double = 8.0,
        coverageLimitInr: Double = 100000.0
    ): Result<String> {
        val path = "users/$userId/trips"
        return try {
            val uid = requireUserId(userId)
            require(currentWalletBalance >= premiumInr) {
                "Insufficient wallet balance. Add ₹200 in Ledger to activate cover."
            }
            val safePlatform = platformName.trim().ifBlank { "Swiggy" }.take(40)
            val safeRoute = routeSummary.trim().ifBlank { "Active Delivery Zone" }.take(120)
            val tripCode = "TRIP-${Random.nextInt(1000, 9999)}"
            val newBalance = (currentWalletBalance - premiumInr).coerceAtLeast(0.0)

            val userRef = db.collection("users").document(uid)
            val tripRef = userRef.collection("trips").document()
            val ledgerRef = userRef.collection("ledger").document()

            val tripPayload = mapOf(
                "userId" to uid,
                "tripCode" to tripCode,
                "status" to TripStatus.ACTIVE.name,
                "platformName" to safePlatform,
                "routeSummary" to safeRoute,
                "premiumInr" to premiumInr,
                "coverageLimitInr" to coverageLimitInr,
                "startLat" to startLat.coerceIn(-90.0, 90.0),
                "startLng" to startLng.coerceIn(-180.0, 180.0),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )

            val ledgerPayload = mapOf(
                "userId" to uid,
                "type" to LedgerType.PREMIUM.name,
                "title" to "$safePlatform Trip Cover",
                "referenceCode" to tripCode,
                "amountInr" to -premiumInr,
                "balanceAfterInr" to newBalance,
                "createdAt" to FieldValue.serverTimestamp()
            )

            val batch = db.batch()
            batch.set(tripRef, tripPayload)
            batch.set(ledgerRef, ledgerPayload)
            batch.update(
                userRef,
                mapOf(
                    "walletBalanceInr" to newBalance,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(tripCode)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun endActiveTrip(
        userId: String,
        tripId: String,
        endLat: Double,
        endLng: Double,
        updatedRouteSummary: String? = null
    ): Result<Unit> {
        val path = "users/$userId/trips/$tripId"
        return try {
            val uid = requireUserId(userId)
            require(tripId.isNotBlank()) { "Trip ID cannot be blank." }
            val updates = mutableMapOf<String, Any>(
                "status" to TripStatus.COMPLETED.name,
                "endLat" to endLat.coerceIn(-90.0, 90.0),
                "endLng" to endLng.coerceIn(-180.0, 180.0),
                "endedAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (!updatedRouteSummary.isNullOrBlank()) {
                updates["routeSummary"] = updatedRouteSummary.trim().take(120)
            }
            db.collection("users").document(uid)
                .collection("trips").document(tripId)
                .update(updates)
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun fileClaim(
        userId: String,
        tripId: String,
        tripCode: String,
        incidentType: IncidentType,
        description: String,
        requestedInr: Double,
        incidentLat: Double?,
        incidentLng: Double?,
        evidenceAttached: Boolean,
        outsideWindow: Boolean = false,
        aiSummary: String = ""
    ): Result<String> {
        val path = "users/$userId/claims"
        return try {
            val uid = requireUserId(userId)
            val safeDesc = description.trim()
            require(safeDesc.length in 5..600) {
                "Please describe the incident in 5 to 600 characters."
            }
            require(requestedInr in 1.0..500000.0) {
                "Claim amount must be between ₹1 and ₹5,00,000."
            }
            val claimCode = "CLM-${Random.nextInt(1000, 9999)}"
            val status = if (outsideWindow) ClaimStatus.OUTSIDE_WINDOW.name else ClaimStatus.PENDING.name
            val claimRef = db.collection("users").document(uid).collection("claims").document()

            val payload = mapOf(
                "userId" to uid,
                "claimCode" to claimCode,
                "tripId" to tripId.ifBlank { "OUTSIDE_TRIP" }.take(128),
                "tripCode" to tripCode.ifBlank { "OUTSIDE_TRIP" }.take(32),
                "incidentType" to incidentType.name,
                "description" to safeDesc,
                "status" to status,
                "requestedInr" to requestedInr,
                "incidentLat" to incidentLat?.coerceIn(-90.0, 90.0),
                "incidentLng" to incidentLng?.coerceIn(-180.0, 180.0),
                "evidenceAttached" to evidenceAttached,
                "aiSummary" to aiSummary.trim().take(1200),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null } as Map<String, Any>

            claimRef.set(payload).await()
            Result.success(claimCode)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun attachAiSummaryToClaim(
        userId: String,
        claimId: String,
        aiSummary: String
    ): Result<Unit> {
        val path = "users/$userId/claims/$claimId"
        return try {
            val uid = requireUserId(userId)
            require(claimId.isNotBlank()) { "Claim ID cannot be blank." }
            db.collection("users").document(uid)
                .collection("claims").document(claimId)
                .update(
                    mapOf(
                        "aiSummary" to aiSummary.trim().take(1200),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun withdrawPendingClaim(userId: String, claimId: String): Result<Unit> {
        val path = "users/$userId/claims/$claimId"
        return try {
            val uid = requireUserId(userId)
            require(claimId.isNotBlank()) { "Claim ID cannot be blank." }
            db.collection("users").document(uid)
                .collection("claims").document(claimId)
                .delete()
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun topUpWallet(
        userId: String,
        currentWalletBalance: Double,
        amountInr: Double = 200.0
    ): Result<Double> {
        val path = "users/$userId/ledger"
        return try {
            val uid = requireUserId(userId)
            require(amountInr in 10.0..50000.0) { "Top-up amount must be between ₹10 and ₹50,000." }
            val newBalance = (currentWalletBalance + amountInr).coerceAtMost(1000000.0)
            val refCode = "UPI-${Random.nextInt(10000, 99999)}"
            val userRef = db.collection("users").document(uid)
            val ledgerRef = userRef.collection("ledger").document()

            val batch = db.batch()
            batch.set(
                ledgerRef,
                mapOf(
                    "userId" to uid,
                    "type" to LedgerType.TOPUP.name,
                    "title" to "UPI Wallet Top-Up",
                    "referenceCode" to refCode,
                    "amountInr" to amountInr,
                    "balanceAfterInr" to newBalance,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.update(
                userRef,
                mapOf(
                    "walletBalanceInr" to newBalance,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(newBalance)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun updateRiderSettings(
        userId: String,
        displayName: String,
        vehicleNumber: String,
        partnerFleet: String,
        voiceAlertsEnabled: Boolean
    ): Result<Unit> {
        val path = "users/$userId"
        return try {
            val uid = requireUserId(userId)
            val safeName = displayName.trim().ifBlank { "TripShield Rider" }.take(80)
            val safeVehicle = vehicleNumber.trim().uppercase().take(24)
            val safeFleet = partnerFleet.trim().ifBlank { "Swiggy" }.take(60)

            db.collection("users").document(uid)
                .update(
                    mapOf(
                        "displayName" to safeName,
                        "vehicleNumber" to safeVehicle,
                        "partnerFleet" to safeFleet,
                        "voiceAlertsEnabled" to voiceAlertsEnabled,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    private fun handleFlowError(
        error: Throwable,
        operationType: OperationType,
        path: String,
        expectedUid: String
    ) {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser?.uid == expectedUid) {
            handleFirestoreError(exceptionToLog, operationType, path)
        }
    }
}
