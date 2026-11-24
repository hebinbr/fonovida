# Security Summary

**Reviewed Files**: 22
**Issues Resolved**: 6

### Issues Resolved
| Area | File | Line | What Was Happening | Impact | Status | How It Was Fixed |
|---|---|---|---|---|---|---|
| Exported Activity Intent Spoofing | `app/src/main/java/com/example/MainActivity.kt` | 87–107 | `MainActivity` is exported as the launcher activity and previously accepted any `EXTRA_WHATSAPP_URI` and `EXTRA_WHATSAPP_APPT_ID` strings from incoming intents without origin authentication or URI host allowlisting, allowing third-party apps to open arbitrary URIs or mark appointments as reminded. | High | Fixed | Enforced strict `https://wa.me/...` URI allowlisting, validated document ID format, and required a constant-time `HmacSHA256` internal dispatch token generated in `ReminderScheduler` before forwarding or acting on the intent. |
| Unvalidated External Link Launch | `app/src/main/java/com/example/notifications/ReminderScheduler.kt` | 121–135 | WhatsApp dispatch helpers did not verify scheme, host, userinfo, port, or path structure on the parsed `URI` prior to `Intent.ACTION_VIEW`. | Medium | Fixed | Added `isAllowedWhatsAppUri` enforcing `https`, exact `wa.me` host, empty userinfo, default port, digits-only path, and no whitespace or control characters. |
| Sensitive Log & PII Exposure | `app/src/main/java/com/example/data/firebase/FirestoreErrorHandler.kt` | 34–57 | `handleFirestoreError` wrote the user's email address, UID, full document path, and raw exception strings to system logcat, while `AuthHelper` logged full exception objects. | Medium | Fixed | Redacted email addresses, UIDs, full paths, and raw throwable text from logcat output across `FirestoreErrorHandler` and `AuthHelper`, keeping only sanitized error codes, operation types, top-level collections, and authentication booleans. |
| Unbounded Document ID Path Segments | `app/src/main/java/com/example/data/firebase/FonoRepository.kt` | 49–59 | Repository document helpers interpolated caller-supplied IDs into Firestore document references without rejecting path separators (`/`) or traversal segments (`..`). | Medium | Fixed | Added `requireValidDocId` (`^[a-zA-Z0-9_\-]{1,128}$`) across user, patient, exercise, session, appointment, block, list, and access-code operations. |
| Missing Schema Bounds on Code Redemption | `firestore.rules` | 497–514 | Patient redemption updates on `patientAccessCodes/{codeId}` omitted `isValidPatientAccessCode`, document ID validation (`isValidId(codeId)`), and an upper bound on `redeemedAtMillis`. | Medium | Fixed | Added `isValidId(codeId)`, full `isValidPatientAccessCode(incoming(), codeId)` validation on redemption updates, and bounded `redeemedAtMillis <= request.time.toMillis() + 300000`. |
| Android Auto Backup & Stale PDF Cache | `app/src/main/AndroidManifest.xml` | 17 | `android:allowBackup="true"` permitted device/cloud backups of app-private `SharedPreferences` (session role and access code preferences) and local exports, and temporary PDF reports accumulated in cache. | Low | Fixed | Set `android:allowBackup="false"`, excluded `sharedpref`, `database`, and `file` domains in `backup_rules.xml` and `data_extraction_rules.xml`, and added 24-hour pruning (`pruneStaleReports`) in `ClinicalPdfExporter`. |

### Safeguards Verified
| Area | File | How It's Protected |
|---|---|---|
| Google Sign-In & Credential Handling | `app/src/main/java/com/example/auth/AuthHelper.kt` | Uses Jetpack `CredentialManager` with `GetSignInWithGoogleOption` / `GetGoogleIdOption` and Firebase Authentication; no anonymous auth or hardcoded credentials. |
| Cloud Database Ownership Rules | `firestore.rules` | Every `/users/{userId}/...` document and subcollection requires `request.auth != null`, `request.auth.uid == userId`, strict field allowlists (`hasOnly` / `affectedKeys().hasOnly`), server timestamps, and numeric bounds. |
| App-Private Session & Token Storage | `app/src/main/java/com/example/data/AccessSessionStore.kt` | Stores patient/therapist session metadata and HMAC signing key strictly in `Context.MODE_PRIVATE` `SharedPreferences` with backups disabled. |
| PDF File Sharing Boundaries | `app/src/main/res/xml/file_paths.xml` | `FileProvider` is non-exported (`android:exported="false"`) and exposes only cache subdirectories (`reports/` and `exercise_lists/`), using short-lived read URI grants. |
| Secrets & Key Management | `app/build.gradle.kts` | No hardcoded third-party API keys or credentials exist in Kotlin sources; Firebase configuration uses generated project resources. |

---

## PoC Verification

### 1. Exported Activity Intent Spoofing & Arbitrary URI Redirection

#### Summary
| Field | Value |
|---|---|
| Issue Type | Improper Exported Intent Trust & Unvalidated External URI |
| Impact | High |
| Affected File | `app/src/main/java/com/example/MainActivity.kt:87` |
| Test Scenario | External app sends `Intent` to `MainActivity` with `extra_whatsapp_uri = "https://wa.me@evil.com/phish"` or `"javascript:alert(1)"` and `extra_whatsapp_appt_id = "../otherUser/appointments/1"` without a valid internal HMAC token. |

#### How It Was Fixed
`MainActivity.handleReminderWhatsAppIntent` clears extras after reading, validates the URI via `ReminderScheduler.isAllowedWhatsAppUri`, validates optional appointment IDs via `ReminderScheduler.isValidSafeDocumentId`, and verifies an app-private `HmacSHA256` signature (`EXTRA_WHATSAPP_TOKEN`) with constant-time comparison (`MessageDigest.isEqual`) before emitting to `pendingWhatsAppFlow`.

#### Step-by-Step Trace
| Step | What Happens in the Code | Outcome |
|---|---|---|
| 1 | Malicious `Intent` arrives at `MainActivity.onCreate` / `onNewIntent` | `handleReminderWhatsAppIntent(intent)` reads and immediately removes all three extras (`MainActivity.kt:88-93`) |
| 2 | URI check runs at `MainActivity.kt:95` -> `ReminderScheduler.isAllowedWhatsAppUri` (`ReminderScheduler.kt:121-135`) | `https://wa.me@evil.com/phish` fails `userInfo == null` and host `wa.me` checks; `javascript:alert(1)` fails `https` scheme check |
| 3 | Document ID check runs at `MainActivity.kt:98` -> `ReminderScheduler.isValidSafeDocumentId` (`ReminderScheduler.kt:118-119`) | `"../otherUser/appointments/1"` fails `^[a-zA-Z0-9_\-]{1,128}$` |
| 4 | Even with a syntactically valid `wa.me` URL and appointment ID, `ReminderScheduler.verifyIntentDispatchToken` (`MainActivity.kt:103`) checks the HMAC against the 256-bit secret stored in `MODE_PRIVATE` `fonovida_internal_secrets` | Missing or forged token returns `false`; `pendingWhatsAppFlow` is never updated |

#### Conclusion
**Fix Verified** - Untrusted external intents cannot trigger arbitrary URI launches or forge `markAppointmentReminderSent` Firestore writes.

---

### 2. Sensitive Information & PII Exposure in System Logs

#### Summary
| Field | Value |
|---|---|
| Issue Type | Sensitive Data Logged to Logcat |
| Impact | Medium |
| Affected File | `app/src/main/java/com/example/data/firebase/FirestoreErrorHandler.kt:34` |
| Test Scenario | A Firestore `PERMISSION_DENIED` exception or Google Sign-In failure occurs while signed in as `dra.ana@clinica.com`, passing `"users/<uid>/patients/<id>"` to `handleFirestoreError`. |

#### How It Was Fixed
Removed `userId`, `email`, `providerEmails`, full `path`, and `exception.localizedMessage` from the structured log payload in `FirestoreErrorHandler.kt`, emitting only `errorCode`, `operationType`, `collection` (`path.substringBefore('/')`), and boolean `authenticated`/`emailVerified` flags. `AuthHelper.kt` was similarly restricted to exception class names.

#### Step-by-Step Trace
| Step | What Happens in the Code | Outcome |
|---|---|---|
| 1 | Repository catch block invokes `handleFirestoreError(e, OperationType.UPDATE, "users/uid123/patients/pat456")` | Control enters `FirestoreErrorHandler.kt:19` |
| 2 | `sanitizedCollection` extracts `path?.substringBefore('/')` (`FirestoreErrorHandler.kt:37-40`) | Only `"users"` is retained; UID and patient ID are discarded |
| 3 | `authInfoJson` populates only `authenticated`, `emailVerified`, and `providers` (`FirestoreErrorHandler.kt:42-46`) | No email address, UID, or exception stack trace is serialized to `Log.e` |

#### Conclusion
**Fix Verified** - Patient and therapist identifiers, emails, and document paths are no longer written to device logs.

---

### 3. Firestore Document Path Segment Validation

#### Summary
| Field | Value |
|---|---|
| Issue Type | Unvalidated Path Segment Interpolation |
| Impact | Medium |
| Affected File | `app/src/main/java/com/example/data/firebase/FonoRepository.kt:49` |
| Test Scenario | Caller supplies `"../otherCollection/doc"` or `"pat1/subcol/doc"` as `patientId`, `exerciseId`, `appointmentId`, or `code` to repository read/write methods. |

#### How It Was Fixed
Added `requireValidDocId` in `FonoRepository.kt` matching `^[a-zA-Z0-9_\-]{1,128}$` and enforced it on every user ID, document ID, and patient ID before passing identifiers to `.document(...)` or batch queries.

#### Step-by-Step Trace
| Step | What Happens in the Code | Outcome |
|---|---|---|
| 1 | Malformed ID `"../otherCollection/doc"` enters `updatePatientWeeklyFrequencyGoal` or `deletePatient` (`FonoRepository.kt:390, 446`) | Method invokes `requireValidDocId(patientId)` (`FonoRepository.kt:49-53`) |
| 2 | Regex `^[a-zA-Z0-9_\-]{1,128}$` evaluates `"../otherCollection/doc"` | Does not match; throws `IllegalArgumentException` immediately |
| 3 | Catch block maps failure to `Result.failure` cleanly | Firestore `.document(...)` is never reached |

#### Conclusion
**Fix Verified** - Path separators and traversal sequences cannot escape intended Firestore collection boundaries.

---

### 4. Patient Access Code Redemption Validation & Timestamp Bounds

#### Summary
| Field | Value |
|---|---|
| Issue Type | Missing Rule Validation & Unbounded Timestamp on Update |
| Impact | Medium |
| Affected File | `firestore.rules:497` |
| Test Scenario | Authenticated user attempts to redeem a code with `redeemedAtMillis = Date.now() + 10_000_000_000` (far future) or malformed `codeId` / document structure. |

#### How It Was Fixed
Updated `patientAccessCodes/{codeId}` rules in `firestore.rules` to enforce `isValidId(codeId)` on all operations and enforce `isValidPatientAccessCode(incoming(), codeId)` plus `incoming().redeemedAtMillis <= request.time.toMillis() + 300000` during patient redemption updates.

#### Step-by-Step Trace
| Step | What Happens in the Code | Outcome |
|---|---|---|
| 1 | Signed-in patient submits an update on `/patientAccessCodes/FV8K4M2P` with tampered `redeemedAtMillis` in the far future | Request enters `match /patientAccessCodes/{codeId}` `allow update` (`firestore.rules:479`) |
| 2 | Rule checks `isValidId(codeId)` (`line 480`), `isValidPatientAccessCode(incoming(), codeId)` (`line 500`), and `incoming().redeemedAtMillis <= request.time.toMillis() + 300000` (`line 509`) | Timestamp check evaluates to `false` |
| 3 | Transaction rejected by Firestore backend | **PERMISSION_DENIED** (verified in `firestore.test.js`) |

#### Conclusion
**Fix Verified** - Forged future timestamps and schema-violating updates are rejected by Firestore Security Rules.

---

### 5. Local Backup Extraction & Cached Clinical PDF Retention

#### Summary
| Field | Value |
|---|---|
| Issue Type | Unencrypted Device Backup Exposure & Unbounded Cache Retention |
| Impact | Low |
| Affected File | `app/src/main/AndroidManifest.xml:17` |
| Test Scenario | Device backup (`adb backup` or cloud backup) attempts to extract `fonovida_access_prefs` / `fonovida_internal_secrets`, or repeated PDF exports leave stale clinical reports on disk. |

#### How It Was Fixed
Disabled Android backups in `AndroidManifest.xml` (`android:allowBackup="false"`), added explicit `<exclude>` directives for `sharedpref`, `database`, and `file` domains in `backup_rules.xml` and `data_extraction_rules.xml`, and added `pruneStaleReports(reportsDir)` in `ClinicalPdfExporter.kt` to remove cached PDFs older than 24 hours.

#### Step-by-Step Trace
| Step | What Happens in the Code | Outcome |
|---|---|---|
| 1 | Backup agent inspects `AndroidManifest.xml:17-19` and backup XML rules | `allowBackup="false"` and explicit domain exclusions prevent archiving app preferences or cached files |
| 2 | Clinician generates or shares a new PDF report (`ClinicalPdfExporter.kt:116-117`) | `pruneStaleReports(reportsDir)` deletes any cached `.pdf` older than 24 hours before writing |

#### Conclusion
**Fix Verified** - Local session preferences, HMAC keys, and clinical PDF exports are protected from backup extraction and unbounded disk retention.
