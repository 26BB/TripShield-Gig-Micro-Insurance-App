package com.example.data

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

enum class TripStatus(val label: String) {
    ACTIVE("Protected"),
    COMPLETED("Completed"),
    FLAGGED("Flagged");

    companion object {
        fun fromRaw(raw: String): TripStatus =
            entries.firstOrNull { it.name == raw } ?: COMPLETED
    }
}

enum class IncidentType(val label: String) {
    ACCIDENT("Road Accident"),
    BREAKDOWN("Vehicle Breakdown"),
    WEATHER_DELAY("Severe Rain / Flood"),
    THEFT("Parcel / Gear Theft");

    companion object {
        fun fromRaw(raw: String): IncidentType =
            entries.firstOrNull { it.name == raw } ?: ACCIDENT
    }
}

enum class ClaimStatus(val label: String) {
    PENDING("Under Review"),
    APPROVED("Approved"),
    PAID("Paid"),
    OUTSIDE_WINDOW("Outside Trip Window");

    companion object {
        fun fromRaw(raw: String): ClaimStatus =
            entries.firstOrNull { it.name == raw } ?: PENDING
    }
}

enum class LedgerType(val label: String) {
    PREMIUM("Trip Premium"),
    PAYOUT("Claim Payout"),
    TOPUP("Wallet Top-Up");

    companion object {
        fun fromRaw(raw: String): LedgerType =
            entries.firstOrNull { it.name == raw } ?: PREMIUM
    }
}

@Keep
@IgnoreExtraProperties
data class RiderProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val email: String = "",
    val riderCode: String = "",
    val vehicleNumber: String = "",
    val partnerFleet: String = "Swiggy",
    val walletBalanceInr: Double = 250.0,
    val voiceAlertsEnabled: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class TripRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val tripCode: String = "",
    val status: String = TripStatus.ACTIVE.name,
    val platformName: String = "Swiggy",
    val routeSummary: String = "",
    val premiumInr: Double = 8.0,
    val coverageLimitInr: Double = 100000.0,
    val startLat: Double = 12.9716,
    val startLng: Double = 77.5946,
    val endLat: Double? = null,
    val endLng: Double? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
    @ServerTimestamp val endedAt: Timestamp? = null
) {
    val tripStatus: TripStatus get() = TripStatus.fromRaw(status)
}

@Keep
@IgnoreExtraProperties
data class ClaimRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val claimCode: String = "",
    val tripId: String = "",
    val tripCode: String = "",
    val incidentType: String = IncidentType.ACCIDENT.name,
    val description: String = "",
    val status: String = ClaimStatus.PENDING.name,
    val requestedInr: Double = 0.0,
    val incidentLat: Double? = null,
    val incidentLng: Double? = null,
    val evidenceAttached: Boolean = false,
    val aiSummary: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val parsedIncidentType: IncidentType get() = IncidentType.fromRaw(incidentType)
    val parsedStatus: ClaimStatus get() = ClaimStatus.fromRaw(status)
}

@Keep
@IgnoreExtraProperties
data class LedgerEntry(
    @DocumentId val id: String = "",
    val userId: String = "",
    val type: String = LedgerType.PREMIUM.name,
    val title: String = "",
    val referenceCode: String = "",
    val amountInr: Double = 0.0,
    val balanceAfterInr: Double = 0.0,
    @ServerTimestamp val createdAt: Timestamp? = null
) {
    val parsedType: LedgerType get() = LedgerType.fromRaw(type)
}

data class PartnerHospital(
    val name: String,
    val area: String,
    val lat: Double,
    val lng: Double,
    val cashlessReady: Boolean = true
)

val BengaluruPartnerHospitals = listOf(
    PartnerHospital("St. John's Emergency Trauma", "Koramangala", 12.9298, 77.6204),
    PartnerHospital("Manipal Hospital Old Airport Rd", "HAL / Indiranagar", 12.9588, 77.6488),
    PartnerHospital("Apollo Clinic & Urgent Care", "Indiranagar 100ft Rd", 12.9784, 77.6408),
    PartnerHospital("Fortis Trauma Desk", "Bannerghatta Rd", 12.8947, 77.5986)
)
