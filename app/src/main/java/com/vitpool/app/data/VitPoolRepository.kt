package com.vitpool.app.data

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.Date
import java.util.concurrent.ConcurrentHashMap

internal fun normalizeEmailInput(email: String): String = email.trim().lowercase()

class AuthRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
) {
    val currentUser: FirebaseUser?
        get() = auth.currentUser

    fun authState(): Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.IdTokenListener { trySend(it.currentUser) }
        auth.addIdTokenListener(listener)
        trySend(auth.currentUser)
        awaitClose { auth.removeIdTokenListener(listener) }
    }

    suspend fun signIn(email: String, password: String) {
        auth.signInWithEmailAndPassword(normalizeEmailInput(email), password).await()
        reloadCurrentUserAndRefreshVerifiedToken()
    }

    suspend fun register(email: String, password: String) {
        val normalizedEmail = normalizeEmailInput(email)
        require(normalizedEmail.endsWith("@vit.edu")) {
            "Use your @vit.edu email address."
        }
        auth.createUserWithEmailAndPassword(normalizedEmail, password).await()
        auth.currentUser?.sendEmailVerification()?.await()
    }

    suspend fun resendVerification() {
        auth.currentUser?.sendEmailVerification()?.await()
    }

    suspend fun refreshUser(): FirebaseUser? {
        return reloadCurrentUserAndRefreshVerifiedToken()
    }

    fun signOut() {
        auth.signOut()
    }

    private suspend fun reloadCurrentUserAndRefreshVerifiedToken(): FirebaseUser? {
        val user = auth.currentUser ?: return null
        user.reload().await()
        val refreshedUser = auth.currentUser ?: user
        if (refreshedUser.isEmailVerified) {
            refreshedUser.getIdToken(true).await()
        }
        return auth.currentUser ?: refreshedUser
    }
}

class VitPoolRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val messaging: FirebaseMessaging = FirebaseMessaging.getInstance(),
) {
    private val _notices = MutableSharedFlow<String>(extraBufferCapacity = 4)
    private val reportedListenerErrors = ConcurrentHashMap.newKeySet<String>()

    val notices: SharedFlow<String> = _notices.asSharedFlow()

    private val uid: String
        get() = auth.currentUser?.uid ?: error("Not signed in")

    fun profile(): Flow<UserProfile?> = callbackFlow {
        val registration = db.collection("users").document(uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    reportListenerError("profile", error)
                    trySend(null)
                    return@addSnapshotListener
                }
                trySend(snapshot?.toUserProfile())
            }
        awaitClose { registration.remove() }
    }

    fun matchSuggestion(): Flow<MatchSuggestion> = callbackFlow {
        val registration = db.collection("matchSuggestions").document(uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    reportListenerError("match suggestions", error)
                    trySend(MatchSuggestion())
                    return@addSnapshotListener
                }
                trySend(snapshot?.toMatchSuggestion() ?: MatchSuggestion())
            }
        awaitClose { registration.remove() }
    }

    fun activePool(): Flow<RidePool?> = callbackFlow {
        val registration = db.collection("ridePools")
            .whereArrayContains("memberIds", uid)
            .orderBy("updatedAt", Query.Direction.DESCENDING)
            .limit(1)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    reportListenerError("active pool", error)
                    trySend(null)
                    return@addSnapshotListener
                }
                trySend(snapshot?.documents?.firstOrNull()?.toRidePool())
        }
        awaitClose { registration.remove() }
    }

    fun poolMemberSummaries(memberIds: List<String>): Flow<List<RiderSummary>> = callbackFlow {
        val orderedMemberIds = memberIds.distinct()
        if (orderedMemberIds.isEmpty()) {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }

        val registration = db.collection("users")
            .whereIn(FieldPath.documentId(), orderedMemberIds)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    reportListenerError("pool members", error)
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val membersByUid = snapshot?.documents
                    .orEmpty()
                    .mapNotNull { it.toUserProfile()?.toRiderSummary() }
                    .associateBy { it.uid }
                trySend(orderedMemberIds.mapNotNull { membersByUid[it] })
            }
        awaitClose { registration.remove() }
    }

    fun messages(chatId: String): Flow<List<ChatMessage>> = callbackFlow {
        val registration = db.collection("chats").document(chatId).collection("messages")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .limit(100)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    reportListenerError("messages", error)
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val messages = snapshot?.documents.orEmpty().mapNotNull { it.toChatMessage() }
                trySend(messages)
            }
        awaitClose { registration.remove() }
    }

    suspend fun completeProfile(draft: ProfileDraft) {
        val email = auth.currentUser?.email?.lowercase().orEmpty()
        require(email.endsWith("@vit.edu")) { "Use your @vit.edu email address." }
        val profileRef = db.collection("users").document(uid)
        val payload = draft.toCallablePayload().toMutableMap()
        payload["uid"] = uid
        payload["email"] = email
        payload["updatedAt"] = FieldValue.serverTimestamp()
        if (!profileRef.get().await().exists()) {
            payload["createdAt"] = FieldValue.serverTimestamp()
        }
        profileRef.set(payload, SetOptions.merge()).await()
        registerFcmToken()
    }

    suspend fun requestRide(direction: TripDirection) {
        require(activePoolSnapshot() == null) { "Finish your current pool first." }
        val user = currentProfile()
        val now = Timestamp.now()
        db.collection("rideRequests").document(uid).set(
            mapOf(
                "uid" to uid,
                "tripDirection" to direction.raw,
                "campus" to user.campus.raw,
                "status" to "pending",
                "predictedDepartureAt" to now,
                "createdAt" to now,
                "updatedAt" to now,
            ),
            SetOptions.merge(),
        ).await()
        refreshMatches()
    }

    suspend fun refreshMatches() {
        val base = currentPendingRide(uid) ?: throw IllegalStateException("Create a ride request first.")
        val pendingDocs = db.collection("rideRequests")
            .whereEqualTo("status", "pending")
            .whereEqualTo("campus", base.user.campus.raw)
            .whereEqualTo("tripDirection", base.tripDirection.raw)
            .get()
            .await()
            .documents
        val pending = pendingDocs.mapNotNull { doc -> doc.toPendingRide(loadProfile(doc.id)) }
        val candidates = findCompatibleCandidates(base, pending)
        val suggestedPoolUserIds = buildSuggestedPool(uid, candidates)
        db.collection("matchSuggestions").document(uid).set(
            mapOf(
                "candidateCount" to candidates.size,
                "suggestedPoolUserIds" to suggestedPoolUserIds,
                "candidates" to candidates.map { it.toMap() },
                "summary" to if (candidates.isEmpty()) {
                    "0 compatible riders nearby"
                } else {
                    "${candidates.size} compatible rider${if (candidates.size == 1) "" else "s"} nearby"
                },
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
        ).await()
    }

    suspend fun confirmPool(memberIds: List<String>) {
        val selectedIds = (listOf(uid) + memberIds).distinct().take(4)
        require(selectedIds.size >= 2) { "Choose at least one compatible rider." }
        val rides = selectedIds.map { id ->
            currentPendingRide(id) ?: throw IllegalStateException("One or more riders no longer have active matching.")
        }
        val base = rides.first { it.uid == uid }
        require(compatibleGroup(base, rides)) { "Selected riders are no longer compatible." }
        val poolRef = db.collection("ridePools").document()
        val departureMillis = rides.map { it.predictedDepartureMillis }.average().toLong()
        db.runTransaction { transaction ->
            selectedIds.forEach { id ->
                val requestRef = db.collection("rideRequests").document(id)
                val request = transaction.get(requestRef)
                if (request.getString("status") != "pending") {
                    throw IllegalStateException("One or more riders no longer have active matching.")
                }
            }
            transaction.set(
                poolRef,
                mapOf(
                    "status" to "pending_confirmation",
                    "memberIds" to selectedIds,
                    "confirmedMemberIds" to listOf(uid),
                    "pendingMemberIds" to selectedIds.filter { it != uid },
                    "departureAt" to Timestamp(Date(departureMillis)),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
            )
            selectedIds.forEach { id ->
                transaction.update(
                    db.collection("rideRequests").document(id),
                    mapOf(
                        "status" to "matched",
                        "poolId" to poolRef.id,
                        "updatedAt" to FieldValue.serverTimestamp(),
                    ),
                )
            }
            null
        }.await()
    }

    suspend fun confirmPendingPool() {
        val pool = activePoolSnapshot("pending_confirmation") ?: throw IllegalStateException("No pending pool found.")
        db.runTransaction { transaction ->
            val snapshot = transaction.get(pool.reference)
            val memberIds = snapshot.stringList("memberIds")
            val confirmed = (snapshot.stringList("confirmedMemberIds") + uid).distinct()
            val pending = memberIds.filter { it !in confirmed }
            val update = mutableMapOf<String, Any>(
                "confirmedMemberIds" to confirmed,
                "pendingMemberIds" to pending,
                "updatedAt" to FieldValue.serverTimestamp(),
            )
            if (pending.isEmpty()) {
                update["status"] = "confirmed"
                memberIds.forEach { id ->
                    transaction.update(
                        db.collection("rideRequests").document(id),
                        mapOf("status" to "confirmed", "updatedAt" to FieldValue.serverTimestamp()),
                    )
                }
            }
            transaction.update(pool.reference, update)
            null
        }.await()
    }

    suspend fun rejectPool() {
        val pool = activePoolSnapshot("pending_confirmation") ?: throw IllegalStateException("No pending pool found.")
        val memberIds = pool.stringList("memberIds")
        db.runTransaction { transaction ->
            transaction.delete(pool.reference)
            memberIds.forEach { id ->
                transaction.update(
                    db.collection("rideRequests").document(id),
                    mapOf(
                        "status" to "pending",
                        "poolId" to FieldValue.delete(),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    ),
                )
            }
            null
        }.await()
    }

    suspend fun leavePool() {
        val pool = activePoolSnapshot("confirmed") ?: throw IllegalStateException("No confirmed pool found.")
        val memberIds = pool.stringList("memberIds")
        val remaining = memberIds.filter { it != uid }
        db.runTransaction { transaction ->
            transaction.update(
                db.collection("rideRequests").document(uid),
                mapOf("status" to "cancelled", "updatedAt" to FieldValue.serverTimestamp()),
            )
            if (remaining.size < 2) {
                transaction.delete(pool.reference)
                remaining.forEach { id ->
                    transaction.update(
                        db.collection("rideRequests").document(id),
                        mapOf(
                            "status" to "pending",
                            "poolId" to FieldValue.delete(),
                            "updatedAt" to FieldValue.serverTimestamp(),
                        ),
                    )
                }
            } else {
                transaction.update(
                    pool.reference,
                    mapOf(
                        "memberIds" to remaining,
                        "confirmedMemberIds" to pool.stringList("confirmedMemberIds").filter { it != uid },
                        "pendingMemberIds" to pool.stringList("pendingMemberIds").filter { it != uid },
                        "updatedAt" to FieldValue.serverTimestamp(),
                    ),
                )
            }
            null
        }.await()
    }

    suspend fun cancelPool() {
        val pool = activePoolSnapshot("confirmed") ?: throw IllegalStateException("No confirmed pool found.")
        val memberIds = pool.stringList("memberIds")
        db.runTransaction { transaction ->
            transaction.delete(pool.reference)
            memberIds.forEach { id ->
                transaction.update(
                    db.collection("rideRequests").document(id),
                    mapOf(
                        "status" to if (id == uid) "cancelled" else "pending",
                        "poolId" to FieldValue.delete(),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    ),
                )
            }
            null
        }.await()
    }

    suspend fun ensureChat(chatId: String, participantIds: List<String>) {
        val participants = participantIds
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
        require(chatId.isNotBlank()) { "Choose a rider before opening chat." }
        require(participants.size >= 2 && uid in participants) { "Invalid chat participants." }

        db.collection("chats").document(chatId).set(
            mapOf(
                "participantIds" to participants,
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
            SetOptions.merge(),
        ).await()
    }

    suspend fun sendMessage(chatId: String, content: String, participantIds: List<String>) {
        val profile = currentProfile()
        val text = content.trim()
        require(text.isNotEmpty()) { "Message cannot be empty." }
        ensureChat(chatId, participantIds)
        val chatRef = db.collection("chats").document(chatId)
        chatRef.collection("messages").add(
            mapOf(
                "chatId" to chatId,
                "senderId" to uid,
                "senderName" to profile.name,
                "content" to text,
                "createdAt" to FieldValue.serverTimestamp(),
            ),
        ).await()
    }

    suspend fun registerFcmToken() {
        val token = messaging.token.await()
        db.collection("users").document(uid).collection("fcmTokens").document(token).set(
            mapOf("token" to token, "updatedAt" to FieldValue.serverTimestamp()),
        ).await()
    }

    suspend fun deleteAccount() {
        db.collection("users").document(uid).delete().await()
        db.collection("rideRequests").document(uid).delete().await()
        db.collection("matchSuggestions").document(uid).delete().await()
        auth.currentUser?.delete()?.await()
    }

    private suspend fun currentProfile(): UserProfile = loadProfile(uid)
        ?: throw IllegalStateException("Complete your profile before continuing.")

    private suspend fun loadProfile(id: String): UserProfile? =
        db.collection("users").document(id).get().await().toUserProfile()

    private suspend fun currentPendingRide(id: String): PendingRide? {
        val profile = loadProfile(id) ?: return null
        val doc = db.collection("rideRequests").document(id).get().await()
        if (doc.getString("status") != "pending") return null
        return doc.toPendingRide(profile)
    }

    private fun DocumentSnapshot.toPendingRide(profile: UserProfile?): PendingRide? {
        if (profile == null || !exists()) return null
        return PendingRide(
            uid = id,
            tripDirection = TripDirection.from(getString("tripDirection")),
            predictedDepartureMillis = timestampMillis("predictedDepartureAt").takeIf { it > 0 } ?: System.currentTimeMillis(),
            user = profile,
        )
    }

    private suspend fun activePoolSnapshot(status: String? = null): DocumentSnapshot? {
        var query = db.collection("ridePools")
            .whereArrayContains("memberIds", uid)
            .limit(1)
        if (status != null) query = query.whereEqualTo("status", status)
        return query.get().await().documents.firstOrNull()
    }

    private fun MatchCandidate.toMap(): Map<String, Any?> = mapOf(
        "user" to mapOf(
            "uid" to user.uid,
            "name" to user.name,
            "branch" to user.branch,
            "division" to user.division,
            "campus" to user.campus.raw,
            "stayHomeLabel" to user.stayHomeLabel,
            "notes" to user.notes,
        ),
        "departureGapMinutes" to departureGapMinutes,
        "proximityLabel" to proximityLabel,
    )

    private fun reportListenerError(source: String, error: FirebaseFirestoreException) {
        FirebaseCrashlytics.getInstance().recordException(error)
        val message = if (error.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
            "Firestore access is still syncing. Refresh verification or sign in again."
        } else {
            "Live $source updates are temporarily unavailable."
        }
        val key = "$source:${error.code}"
        if (reportedListenerErrors.add(key)) {
            _notices.tryEmit(message)
        }
    }
}
