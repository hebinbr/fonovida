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
        patientReminderEnabled: true,
        patientReminderLeadMinutes: 1440,
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

test("SlotBlocks: therapist can block/unblock hours; invalid dateKey & cross-user fail", async () => {
  const anaDb = testEnv.authenticatedContext(THERAPIST_A).firestore();
  const brunaDb = testEnv.authenticatedContext(THERAPIST_B).firestore();
  const created = pastDate(5000);

  await assertSucceeds(
    anaDb
      .collection("users")
      .doc(THERAPIST_A)
      .collection("slotBlocks")
      .doc("2026-11-24_14")
      .set({
        userId: THERAPIST_A,
        dateKey: "2026-11-24",
        hour: 14,
        minute: 0,
        durationMinutes: 60,
        reason: "Horário ocupado - estudo de caso",
        createdAt: created,
        updatedAt: created,
      })
  );

  // List works for owner
  await assertSucceeds(
    anaDb.collection("users").doc(THERAPIST_A).collection("slotBlocks").get()
  );

  // Invalid dateKey rejected
  await assertFails(
    anaDb
      .collection("users")
      .doc(THERAPIST_A)
      .collection("slotBlocks")
      .doc("bad_slot")
      .set({
        userId: THERAPIST_A,
        dateKey: "invalid",
        hour: 14,
        minute: 0,
        durationMinutes: 60,
        createdAt: created,
        updatedAt: created,
      })
  );

  // Cross-user delete rejected
  await assertFails(
    brunaDb
      .collection("users")
      .doc(THERAPIST_A)
      .collection("slotBlocks")
      .doc("2026-11-24_14")
      .delete()
  );

  // Owner can mark slot available again
  await assertSucceeds(
    anaDb
      .collection("users")
      .doc(THERAPIST_A)
      .collection("slotBlocks")
      .doc("2026-11-24_14")
      .delete()
  );
});

test("ExerciseLists: therapist can create, update, list & delete personalized lists; empty items or cross-user fail", async () => {
  await seedPatient(THERAPIST_A, "pat_1");
  const anaDb = testEnv.authenticatedContext(THERAPIST_A).firestore();
  const brunaDb = testEnv.authenticatedContext(THERAPIST_B).firestore();
  const created = pastDate(5000);

  // Reusable clinic protocol (patientId == "")
  await assertSucceeds(
    anaDb
      .collection("users")
      .doc(THERAPIST_A)
      .collection("exerciseLists")
      .doc("list_1")
      .set({
        userId: THERAPIST_A,
        title: "Aquecimento Vocal Matinal",
        clinicalObjective: "Reduzir tensão laríngea antes das aulas",
        patientId: "",
        patientName: "",
        items: [
          {
            title: "Vibração de Lábios /br/",
            category: "AQUECIMENTO",
            instructions: "3 séries de 8 emissões suaves.",
            durationMinutes: 10,
            frequencyPerWeek: 5,
          },
        ],
        createdAt: created,
        updatedAt: created,
      })
  );

  // Patient-linked exercise list succeeds
  await assertSucceeds(
    anaDb
      .collection("users")
      .doc(THERAPIST_A)
      .collection("exerciseLists")
      .doc("list_2")
      .set({
        userId: THERAPIST_A,
        title: "Plano Disfonia Lucas",
        clinicalObjective: "Elevar TMF e conforto vocal",
        patientId: "pat_1",
        patientName: "Lucas Ferreira",
        items: [
          {
            title: "Som Nasal /m/",
            category: "RESSONANCIA",
            instructions: "Mastigar suavemente emitindo /m/.",
            durationMinutes: 12,
            frequencyPerWeek: 5,
          },
        ],
        createdAt: created,
        updatedAt: created,
      })
  );

  // Empty items array rejected
  await assertFails(
    anaDb
      .collection("users")
      .doc(THERAPIST_A)
      .collection("exerciseLists")
      .doc("list_empty")
      .set({
        userId: THERAPIST_A,
        title: "Lista Vazia",
        items: [],
        createdAt: created,
        updatedAt: created,
      })
  );

  // Nonexistent patientId rejected
  await assertFails(
    anaDb
      .collection("users")
      .doc(THERAPIST_A)
      .collection("exerciseLists")
      .doc("list_bad_pat")
      .set({
        userId: THERAPIST_A,
        title: "Lista Orfã",
        patientId: "does_not_exist",
        items: [
          {
            title: "Teste",
            category: "AQUECIMENTO",
            instructions: "Instrução",
            durationMinutes: 10,
            frequencyPerWeek: 5,
          },
        ],
        createdAt: created,
        updatedAt: created,
      })
  );

  // List & update as owner
  await assertSucceeds(
    anaDb.collection("users").doc(THERAPIST_A).collection("exerciseLists").get()
  );
  await assertSucceeds(
    anaDb
      .collection("users")
      .doc(THERAPIST_A)
      .collection("exerciseLists")
      .doc("list_1")
      .update({
        title: "Aquecimento Vocal Atualizado",
        updatedAt: pastDate(1000),
      })
  );

  // Cross-user read & delete denied
  await assertFails(
    brunaDb.collection("users").doc(THERAPIST_A).collection("exerciseLists").get()
  );
  await assertFails(
    brunaDb
      .collection("users")
      .doc(THERAPIST_A)
      .collection("exerciseLists")
      .doc("list_1")
      .delete()
  );

  // Owner delete succeeds
  await assertSucceeds(
    anaDb
      .collection("users")
      .doc(THERAPIST_A)
      .collection("exerciseLists")
      .doc("list_1")
      .delete()
  );
});

test("PatientAccessCodes: therapist generates/revokes code, patient redeems valid code, unauthorized/tampering fails", async () => {
  await seedPatient(THERAPIST_A, "pat_1");
  const anaDb = testEnv.authenticatedContext(THERAPIST_A).firestore();
  const patientDb = testEnv.authenticatedContext("patient_uid_1").firestore();
  const strangerDb = testEnv.authenticatedContext("stranger_uid_2").firestore();
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const created = pastDate(5000);

  // Therapist creates a valid access code linked to pat_1
  await assertSucceeds(
    anaDb.collection("patientAccessCodes").doc("FV8K4M2P").set({
      code: "FV8K4M2P",
      therapistId: THERAPIST_A,
      therapistName: "Dra. Ana Paula",
      clinicName: "Clínica Voz & Fala",
      patientId: "pat_1",
      patientName: "Lucas Ferreira",
      clinicalFocus: "Disfonia Funcional",
      weeklyGoalMinutes: 90,
      active: true,
      redeemedByUid: "",
      redeemedAtMillis: 0,
      exercisesSnapshot: [
        {
          title: "Vibração de Lábios /br/",
          category: "AQUECIMENTO",
          instructions: "3 séries de 8 emissões suaves.",
          durationMinutes: 10,
          frequencyPerWeek: 5,
          sourceLabel: "Plano Ativo",
        },
      ],
      nextAppointmentSummary: "25/11/2026 às 14:30 • Presencial",
      recentProgressSummary: "Última clareza vocal: 84%",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Unauthenticated cannot read
  await assertFails(unauthDb.collection("patientAccessCodes").doc("FV8K4M2P").get());

  // Unscoped list denied; therapist-scoped list succeeds
  await assertFails(patientDb.collection("patientAccessCodes").get());
  await assertSucceeds(
    anaDb.collection("patientAccessCodes").where("therapistId", "==", THERAPIST_A).get()
  );

  // Wrong code fails
  await assertFails(patientDb.collection("patientAccessCodes").doc("WRONGCODE").get());

  // Patient gets valid unredeemed code and redeems it
  await assertSucceeds(patientDb.collection("patientAccessCodes").doc("FV8K4M2P").get());
  await assertSucceeds(
    patientDb.collection("patientAccessCodes").doc("FV8K4M2P").update({
      redeemedByUid: "patient_uid_1",
      redeemedAtMillis: Date.now(),
      updatedAt: pastDate(1000),
    })
  );

  // Patient cannot tamper with exercisesSnapshot or therapistId
  await assertFails(
    patientDb.collection("patientAccessCodes").doc("FV8K4M2P").update({
      exercisesSnapshot: [],
      updatedAt: pastDate(500),
    })
  );

  // Stranger cannot read or steal an already-redeemed code
  await assertFails(strangerDb.collection("patientAccessCodes").doc("FV8K4M2P").get());
  await assertFails(
    strangerDb.collection("patientAccessCodes").doc("FV8K4M2P").update({
      redeemedByUid: "stranger_uid_2",
      redeemedAtMillis: Date.now(),
      updatedAt: pastDate(500),
    })
  );

  // Therapist revokes access code -> patient immediately loses read access
  await assertSucceeds(
    anaDb.collection("patientAccessCodes").doc("FV8K4M2P").update({
      active: false,
      updatedAt: pastDate(200),
    })
  );
  await assertFails(patientDb.collection("patientAccessCodes").doc("FV8K4M2P").get());

  // Therapist deletes code
  await assertSucceeds(anaDb.collection("patientAccessCodes").doc("FV8K4M2P").delete());
});


