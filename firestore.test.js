const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_emp_123";
const BOB_UID = "bob_emp_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validAttendancePayload(uid, overrides = {}) {
  const ts = new Date(Date.now() - 2000);
  return {
    userId: uid,
    employeeId: "EMP-2026-001",
    employeeName: "Budi Santoso",
    status: "CHECK_IN",
    latitude: -6.208763,
    longitude: 106.845599,
    accuracyMeters: 12.5,
    distanceMeters: 24.3,
    withinRadius: true,
    mapsUrl: "https://maps.google.com/?q=-6.208763,106.845599",
    capturedTimeText: "19 Nov 2026 08:15:30",
    photoBase64: "/9j/4AAQSkZJRgABAQAAAQABAAD/2wBDAA==",
    syncedToSheets: false,
    createdAt: ts,
    updatedAt: ts,
    ...overrides,
  };
}

function validSettingsPayload(uid, overrides = {}) {
  const ts = new Date(Date.now() - 2000);
  return {
    userId: uid,
    defaultEmployeeId: "EMP-2026-001",
    defaultEmployeeName: "Budi Santoso",
    officeName: "Kantor Pusat Jakarta",
    officeLatitude: -6.2088,
    officeLongitude: 106.8456,
    maxRadiusMeters: 100.0,
    geofenceEnabled: true,
    sheetsWebhookUrl: "https://script.google.com/macros/s/demo/exec",
    createdAt: ts,
    updatedAt: ts,
    ...overrides,
  };
}

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

test("Unauthenticated user cannot read or write attendance or settings", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();

  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("attendance").get()
  );
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("attendance")
      .doc("rec_1")
      .set(validAttendancePayload(ALICE_UID))
  );
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("settings")
      .doc("workplace")
      .get()
  );
});

test("Authenticated owner can create, list, update sync flag, and delete own attendance", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const colRef = aliceDb.collection("users").doc(ALICE_UID).collection("attendance");

  // Empty collection list succeeds (no existing() reference on list)
  await assertSucceeds(colRef.orderBy("createdAt", "desc").get());

  // Valid create matching Kotlin AttendanceRepository
  await assertSucceeds(colRef.doc("rec_1").set(validAttendancePayload(ALICE_UID)));

  // List & get succeed
  await assertSucceeds(colRef.orderBy("createdAt", "desc").get());
  await assertSucceeds(colRef.doc("rec_1").get());

  // Allowed partial update: syncedToSheets + updatedAt
  await assertSucceeds(
    colRef.doc("rec_1").update({
      syncedToSheets: true,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Tampering with attendance facts (status, employeeId, createdAt) MUST fail
  await assertFails(
    colRef.doc("rec_1").update({
      status: "CHECK_OUT",
      updatedAt: new Date(Date.now() - 200),
    })
  );
  await assertFails(
    colRef.doc("rec_1").update({
      createdAt: new Date(Date.now() - 200),
      updatedAt: new Date(Date.now() - 200),
    })
  );

  // Delete own record succeeds
  await assertSucceeds(colRef.doc("rec_1").delete());
});

test("Attendance schema validation rejects shadow fields, invalid status, future timestamp, and @DocumentId key", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const colRef = aliceDb.collection("users").doc(ALICE_UID).collection("attendance");

  // Reject @DocumentId `id` field inside body
  await assertFails(
    colRef.doc("rec_bad_id").set(validAttendancePayload(ALICE_UID, { id: "rec_bad_id" }))
  );

  // Reject ghost/shadow field
  await assertFails(
    colRef.doc("rec_ghost").set(validAttendancePayload(ALICE_UID, { isVerified: true }))
  );

  // Reject invalid status enum
  await assertFails(
    colRef.doc("rec_status").set(validAttendancePayload(ALICE_UID, { status: "MASUK" }))
  );

  // Reject future timestamp
  await assertFails(
    colRef.doc("rec_future").set(
      validAttendancePayload(ALICE_UID, { createdAt: new Date(Date.now() + 60000) })
    )
  );

  // Reject spoofing userId
  await assertFails(
    colRef.doc("rec_spoof").set(validAttendancePayload(BOB_UID))
  );
});

test("Cross-user isolation prevents Bob from accessing or modifying Alice's attendance or settings", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db
      .collection("users")
      .doc(ALICE_UID)
      .collection("attendance")
      .doc("alice_rec")
      .set(validAttendancePayload(ALICE_UID));
    await db
      .collection("users")
      .doc(ALICE_UID)
      .collection("settings")
      .doc("workplace")
      .set(validSettingsPayload(ALICE_UID));
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("attendance").get()
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("attendance").doc("alice_rec").get()
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("attendance").doc("alice_rec").delete()
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("settings").doc("workplace").get()
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("settings")
      .doc("workplace")
      .update({ officeName: "Hacked", updatedAt: new Date(Date.now() - 500) })
  );
});

test("Authenticated owner can create and update workplace settings with strict bounds", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const settingsRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("settings")
    .doc("workplace");

  // Create valid workplace settings
  await assertSucceeds(settingsRef.set(validSettingsPayload(ALICE_UID)));

  // Partial update preserves immutable createdAt & userId
  await assertSucceeds(
    settingsRef.update({
      officeName: "Cabang Surabaya",
      maxRadiusMeters: 150.0,
      geofenceEnabled: false,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject invalid radius (< 10m)
  await assertFails(
    settingsRef.update({
      maxRadiusMeters: 2.0,
      updatedAt: new Date(Date.now() - 300),
    })
  );

  // Reject arbitrary setting doc ID
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("settings")
      .doc("other_doc")
      .set(validSettingsPayload(ALICE_UID))
  );
});
