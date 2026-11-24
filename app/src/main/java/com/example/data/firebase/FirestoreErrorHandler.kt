package com.example.data.firebase

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject

enum class OperationType(val value: String) {
    CREATE("create"),
    UPDATE("update"),
    DELETE("delete"),
    LIST("list"),
    GET("get"),
    WRITE("write"),
}

fun handleFirestoreError(
    exception: Throwable,
    operationType: OperationType,
    path: String?
): String {
    val causes = generateSequence(exception) { it.cause }
    val rootError = causes.firstOrNull { it is FirebaseFirestoreException }
        ?: causes.firstOrNull { it !is CancellationException }
        ?: exception

    val currentUser = FirebaseAuth.getInstance().currentUser
    val providerIds = currentUser?.providerData
        ?.mapNotNull { it.providerId.takeIf { id -> id.isNotBlank() } }
        .orEmpty()

    val errorCode = (rootError as? FirebaseFirestoreException)?.code?.name
        ?: rootError::class.java.simpleName

    val sanitizedCollection = path
        ?.substringBefore('/')
        ?.take(64)
        .orEmpty()

    val authInfoJson = JSONObject().apply {
        put("authenticated", currentUser != null)
        put("emailVerified", currentUser?.isEmailVerified ?: false)
        put("providers", JSONArray(providerIds))
    }

    val errorInfoJson = JSONObject().apply {
        put("errorCode", errorCode)
        put("operationType", operationType.value)
        put("collection", sanitizedCollection)
        put("authInfo", authInfoJson)
    }

    val jsonString = errorInfoJson.toString()
    Log.e("FirestoreError", "Firestore operation failed: $jsonString")
    return jsonString
}
