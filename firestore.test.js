const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const THERAPIST_A = "therapist_ana_123";
const THERAPIST_B = "therapist_bruna_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

function pastDate(offsetMs = 5000) {
  return new Date(Date.now() - offsetMs);
}

async function seedPatient(uid, patientId) {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db
      .collection("users")
      .doc(uid)
      .collection("patients")
      .doc(patientId)
      .set({
        userId: uid,
        fullName: "Lucas Ferreira",
        ageYears: 8,
        clinicalFocus: "Disfonia Funcional",
        guardianName: "Mariana Ferreira",
        guardianPhone: "11999990000",
        status: "ATIVO",
        weeklyGoalMinutes: 90,
        dailyExerciseReminderEnabled: true,
        reminderHour: 18,
        reminderMinute: 0,
        createdAt: pastDate(20000),
        updatedAt: pastDate(20000),
      });
  });
}

test("Unauthenticated user: cannot read or write any FonoVida collection", async () => {
  const unauth = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauth.collection("users").doc(THERAPIST_A).get());
  await assertFails(
    unauth.collection("users").doc(THERAPIST_A).collection("patients").get()
  );
  await assertFails(
    unauth
      .collection("users")
      .doc(THERAPIST_A)
      .collection("patients")
      .doc("p1")
      .set({
        userId: THERAPIST_A,
        fullName: "Teste",
        ageYears: 10,
        clinicalFocus: "Voz",
        guardianName: "Mae",
        status: "ATIVO",
        weeklyGoalMinutes: 60,
        dailyExerciseReminderEnabled: false,
        reminderHour: 9,
        reminderMinute: 0,
        createdAt: pastDate(),
        updatedAt: pastDate(),
      })
  );
});

test("TherapistProfile: owner create/update succeeds; cross-user & ghost fields fail", async () => {
  const anaDb = testEnv.authenticatedContext(THERAPIST_A).firestore();
  const brunaDb = testEnv.authenticatedContext(THERAPIST_B).firestore();
  const created = pastDate(10000);

  await assertSucceeds(
    anaDb.collection("users").doc(THERAPIST_A).set({
      userId: THERAPIST_A,
      displayName: "Dra. Ana Paula",
      email: "ana@fono.br",
      crfa: "CRFa 2-19402",
      clinicName: "Clínica Voz & Fala",
      defaultReminderMinutes: 60,
      autoRemindersEnabled: true,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Partial update
  await assertSucceeds(
    anaDb.collection("users").doc(THERAPIST_A).update({
      crfa: "CRFa 2-19403",
      updatedAt: pastDate(1000),
    })
  );

  // Ghost field rejected
  await assertFails(
    anaDb.collection("users").doc(THERAPIST_A).update({
      isAdmin: true,
      updatedAt: pastDate(500),
    })
  );

  // Cross-user read & listing users rejected
  await assertFails(brunaDb.collection("users").doc(THERAPIST_A).get());
  await assertFails(brunaDb.collection("users").get());
});

test("Patients: owner create, partial update, list succeed; cross-user & @DocumentId 'id' fail", async () => {
  const anaDb = testEnv.authenticatedContext(THERAPIST_A).firestore();
  const brunaDb = testEnv.authenticatedContext(THERAPIST_B).firestore();
  const created = pastDate(10000);

  await assertSucceeds(
    anaDb
      .collection("users")
      .doc(THERAPIST_A)
      .collection("patients")
      .doc("pat_1")
      .set({
        userId: THERAPIST_A,
        fullName: "Sofia Mendes",
        ageYears: 7,
        clinicalFocus: "Nódulos Vocais",
        guardianName: "Carlos Mendes",
        guardianPhone: "11988887777",
        status: "ATIVO",
        weeklyGoalMinutes: 75,
        initialNotes: "Rouquidão vespertina",
        dailyExerciseReminderEnabled: true,
        reminderHour: 17,
        reminderMinute: 30,
        createdAt: created,
        updatedAt: created,
      })
  );

  // List subcollection as owner succeeds (even on empty or populated collection)
  await assertSucceeds(
    anaDb.collection("users").doc(THERAPIST_A).collection("patients").get()
  );

  // Partial update preserving createdAt
  await assertSucceeds(
    anaDb
      .collection("users")
      .doc(THERAPIST_A)
      .collection("patients")
      .doc("pat_1")
      .update({
        status: "ALTA",
        weeklyGoalMinutes: 60,
        updatedAt: pastDate(1000),
      })
  );

  // Reject including @DocumentId 'id' inside document body
  await assertFails(
    anaDb
      .collection("users")
      .doc(THERAPIST_A)
      .collection("patients")
      .doc("pat_2")
      .set({
        id: "pat_2",
        userId: THERAPIST_A,
        fullName: "Joao Silva",
        ageYears: 12,
        clinicalFocus: "Disfonia",
        guardianName: "Maria Silva",
        status: "ATIVO",
        weeklyGoalMinutes: 60,
        dailyExerciseReminderEnabled: false,
        reminderHour: 10,
        reminderMinute: 0,
        createdAt: created,
        updatedAt: created,
      })
  );

  // Reject future timestamp
  await assertFails(
    anaDb
      .collection("users")
      .doc(THERAPIST_A)
      .collection("patients")
      .doc("pat_future")
      .set({
        userId: THERAPIST_A,
        fullName: "Futuro",
        ageYears: 12,
        clinicalFocus: "Disfonia",
        guardianName: "Maria",
        status: "ATIVO",
        weeklyGoalMinutes: 60,
        dailyExerciseReminderEnabled: false,
        reminderHour: 10,
        reminderMinute: 0,
        createdAt: new Date(Date.now() + 60000),
        updatedAt: created,
      })
  );

  // Cross-user access denied
  await assertFails(
    brunaDb.collection("users").doc(THERAPIST_A).collection("patients").get()
  );
  await assertFails(
    brunaDb
      .collection("users")
      .doc(THERAPIST_A)
      .collection("patients")
      .doc("pat_1")
      .update({ fullName: "Hijack", updatedAt: pastDate(500) })
  );
});

test("VoiceExercises & EvolutionSessions: require existing patient and valid metrics", async () => {
  await seedPatient(THERAPIST_A, "pat_1");
  const anaDb = testEnv.authenticatedContext(THERAPIST_A).firestore();
  const created = pastDate(10000);

  // Create exercise linked to pat_1
  await assertSucceeds(
    anaDb
      .collection("users")
      .doc(THERAPIST_A)
      .collection("exercises")
      .doc("ex_1")
      .set({
        userId: THERAPIST_A,
        patientId: "pat_1",
        title: "Vibração de Lábios /br/",
        category: "AQUECIMENTO",
        instructions: "Realizar 3 séries de 10 repetições com fluxo contínuo.",
        targetDurationMinutes: 10,
        frequencyPerWeek: 5,
        activeFlag: true,
        createdAt: created,
        updatedAt: created,
      })
  );

  // Orphan exercise (nonexistent patient) fails
  await assertFails(
    anaDb
      .collection("users")
      .doc(THERAPIST_A)
      .collection("exercises")
      .doc("ex_orphan")
      .set({
        userId: THERAPIST_A,
        patientId: "nonexistent_pat",
        title: "Orphan",
        category: "AQUECIMENTO",
        instructions: "Instruções.",
        targetDurationMinutes: 10,
        frequencyPerWeek: 5,
        activeFlag: true,
        createdAt: created,
        updatedAt: created,
      })
  );

  // Create evolution session linked to pat_1
  await assertSucceeds(
    anaDb
      .collection("users")
      .doc(THERAPIST_A)
      .collection("sessions")
      .doc("sess_1")
      .set({
        userId: THERAPIST_A,
        patientId: "pat_1",
        patientName: "Lucas Ferreira",
        exerciseTitle: "Vibração de Lábios /br/",
        category: "AQUECIMENTO",
        durationMinutes: 15,
        vocalClarityScore: 84,
        stabilityScore: 79,
        comfortScore: 90,
        tmfSeconds: 14.5,
        clinicalNotes: "Boa projeção sem esforço cervical.",
        homeGuidance: "Manter hidratação antes dos exercícios.",
        sessionDateMillis: Date.now() - 3600000,
        createdAt: created,
        updatedAt: created,
      })
  );

  // Invalid score (>100) rejected
  await assertFails(
    anaDb
      .collection("users")
      .doc(THERAPIST_A)
      .collection("sessions")
      .doc("sess_bad")
      .set({
        userId: THERAPIST_A,
        patientId: "pat_1",
        patientName: "Lucas Ferreira",
        exerciseTitle: "Teste",
        category: "AQUECIMENTO",
        durationMinutes: 15,
        vocalClarityScore: 150,
        stabilityScore: 79,
        comfortScore: 90,
        tmfSeconds: 14.5,
        sessionDateMillis: Date.now() - 3600000,
        createdAt: created,
        updatedAt: created,
      })
  );
});

test("Appointments: scheduling, reminder tracking & terminal state lock", async () => {
  await seedPatient(THERAPIST_A, "pat_1");
  const anaDb = testEnv.authenticatedContext(THERAPIST_A).firestore();
  const created = pastDate(10000);

  await assertSucceeds(
    anaDb
      .collection("users")
      .doc(THERAPIST_A)
      .collection("appointments")
      .doc("appt_1")
      .set({
        userId: THERAPIST_A,
        patientId: "pat_1",
        patientName: "Lucas Ferreira",
        guardianName: "Mariana Ferreira",
        guardianPhone: "11999990000",
        scheduledAtMillis: Date.now() + 86400000,
        durationMinutes: 45,
        sessionType: "PRESENCIAL",
        status: "AGENDADA",
        notes: "Reavaliação de TMF",
        reminderEnabled: true,
        reminderLeadMinutes: 60,
        reminderSentMillis: 0,
        createdAt: created,
        updatedAt: created,
      })
  );

  // Confirm appointment & record reminder sent
  await assertSucceeds(
    anaDb
      .collection("users")
      .doc(THERAPIST_A)
      .collection("appointments")
      .doc("appt_1")
      .update({
        status: "CONFIRMADA",
        updatedAt: pastDate(2000),
      })
  );

  await assertSucceeds(
    anaDb
      .collection("users")
      .doc(THERAPIST_A)
      .collection("appointments")
      .doc("appt_1")
      .update({
        reminderSentMillis: Date.now(),
        updatedAt: pastDate(1500),
      })
  );

  // Transition to terminal state CONCLUIDA
  await assertSucceeds(
    anaDb
      .collection("users")
      .doc(THERAPIST_A)
      .collection("appointments")
      .doc("appt_1")
      .update({
        status: "CONCLUIDA",
        updatedAt: pastDate(1000),
      })
  );

  // Terminal state lock: cannot reopen status after CONCLUIDA
  await assertFails(
    anaDb
      .collection("users")
      .doc(THERAPIST_A)
      .collection("appointments")
      .doc("appt_1")
      .update({
        status: "AGENDADA",
        updatedAt: pastDate(500),
      })
  );

  // Terminal state still allows updating clinical notes
  await assertSucceeds(
    anaDb
      .collection("users")
      .doc(THERAPIST_A)
      .collection("appointments")
      .doc("appt_1")
      .update({
        notes: "Paciente compareceu e evoluiu bem.",
        updatedAt: pastDate(200),
      })
  );
});
