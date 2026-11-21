const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "rider_alice_101";
const BOB_UID = "rider_bob_202";

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

test("Unauthenticated users cannot read or write any TripShield collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const now = new Date(Date.now() - 1000);

  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("trips").get()
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("claims").get()
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("ledger").get()
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Unauthorized",
      email: "x@example.com",
      riderCode: "TS-RDR-0001",
      vehicleNumber: "KA-01-AA-1111",
      partnerFleet: "Swiggy",
      walletBalanceInr: 250,
      voiceAlertsEnabled: true,
      createdAt: now,
      updatedAt: now,
    })
  );
});

test("Rider profile: create, valid partial updates, and security blocks", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = new Date(Date.now() - 5000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Aarav Sharma",
      email: "aarav@example.com",
      riderCode: "TS-RDR-4092",
      vehicleNumber: "KA-01-EQ-4821",
      partnerFleet: "Swiggy",
      walletBalanceInr: 250.0,
      voiceAlertsEnabled: true,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Profile settings partial update succeeds
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      vehicleNumber: "MH-12-TR-9087",
      voiceAlertsEnabled: false,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Wallet balance partial update succeeds
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      walletBalanceInr: 450.0,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Shadow update with unexpected field fails
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isAdmin: true,
      updatedAt: new Date(Date.now() - 200),
    })
  );

  // Mutating immutable riderCode fails
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      riderCode: "TS-HACK-9999",
      updatedAt: new Date(Date.now() - 200),
    })
  );

  // Cross-user read & write fails
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).update({
      walletBalanceInr: 0,
      updatedAt: new Date(Date.now() - 200),
    })
  );
});

test("Trips subcollection: create, complete, terminal state locking, and isolation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const started = new Date(Date.now() - 5000);

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("trips")
      .doc("trip_8821")
      .set({
        userId: ALICE_UID,
        tripCode: "TRIP-8821",
        status: "ACTIVE",
        platformName: "Swiggy",
        routeSummary: "Indiranagar 100ft Rd -> Koramangala",
        premiumInr: 8.0,
        coverageLimitInr: 100000.0,
        startLat: 12.9716,
        startLng: 77.5946,
        createdAt: started,
        updatedAt: started,
      })
  );

  // List query matching Kotlin repository succeeds
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("trips").get()
  );

  // End active trip succeeds
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("trips")
      .doc("trip_8821")
      .update({
        status: "COMPLETED",
        endLat: 12.9352,
        endLng: 77.6245,
        endedAt: new Date(Date.now() - 1000),
        updatedAt: new Date(Date.now() - 1000),
      })
  );

  // Terminal state lock: updating completed trip fails
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("trips")
      .doc("trip_8821")
      .update({
        status: "ACTIVE",
        updatedAt: new Date(Date.now() - 500),
      })
  );

  // Extra @DocumentId 'id' field rejected on create
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("trips")
      .doc("trip_bad")
      .set({
        id: "trip_bad",
        userId: ALICE_UID,
        tripCode: "TRIP-9999",
        status: "ACTIVE",
        platformName: "Blinkit",
        routeSummary: "Test",
        premiumInr: 8.0,
        coverageLimitInr: 100000.0,
        startLat: 12.97,
        startLng: 77.59,
        createdAt: started,
        updatedAt: started,
      })
  );

  // Future timestamp rejected
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("trips")
      .doc("trip_future")
      .set({
        userId: ALICE_UID,
        tripCode: "TRIP-8822",
        status: "ACTIVE",
        platformName: "Zomato",
        routeSummary: "Future test",
        premiumInr: 8.0,
        coverageLimitInr: 100000.0,
        startLat: 12.97,
        startLng: 77.59,
        createdAt: new Date(Date.now() + 60000),
        updatedAt: new Date(Date.now() + 60000),
      })
  );

  // Cross-user access denied
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("trips").get()
  );
});

test("Claims subcollection: create, AI summary update, prevent self-approval, withdraw", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = new Date(Date.now() - 5000);

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("claims")
      .doc("clm_3041")
      .set({
        userId: ALICE_UID,
        claimCode: "CLM-3041",
        tripId: "trip_8821",
        tripCode: "TRIP-8821",
        incidentType: "ACCIDENT",
        description: "Minor skid near wet junction during active order drop.",
        status: "PENDING",
        requestedInr: 1850.0,
        incidentLat: 12.9611,
        incidentLng: 77.6387,
        evidenceAttached: true,
        aiSummary: "",
        createdAt: created,
        updatedAt: created,
      })
  );

  // Updating AI summary on pending claim succeeds
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("claims")
      .doc("clm_3041")
      .update({
        aiSummary: "Eligible under active trip medical cover; attach clinic bill.",
        updatedAt: new Date(Date.now() - 1000),
      })
  );

  // Self-approving claim to PAID fails
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("claims")
      .doc("clm_3041")
      .update({
        status: "PAID",
        updatedAt: new Date(Date.now() - 500),
      })
  );

  // Creating a pre-approved claim directly fails
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("claims")
      .doc("clm_fraud")
      .set({
        userId: ALICE_UID,
        claimCode: "CLM-9999",
        tripId: "trip_8821",
        tripCode: "TRIP-8821",
        incidentType: "THEFT",
        description: "Attempting direct PAID creation.",
        status: "PAID",
        requestedInr: 5000.0,
        evidenceAttached: false,
        aiSummary: "",
        createdAt: created,
        updatedAt: created,
      })
  );

  // Cross-user read denied
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("claims").get()
  );

  // Withdrawing pending claim succeeds
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("claims")
      .doc("clm_3041")
      .delete()
  );
});

test("Ledger subcollection: append-only financial integrity", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 2000);

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("ledger")
      .doc("led_01")
      .set({
        userId: ALICE_UID,
        type: "PREMIUM",
        title: "Trip Cover Premium",
        referenceCode: "TRIP-8821",
        amountInr: -8.0,
        balanceAfterInr: 242.0,
        createdAt: created,
      })
  );

  // List query succeeds
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("ledger").get()
  );

  // Ledger entries are immutable (update & delete denied)
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("ledger")
      .doc("led_01")
      .update({ amountInr: 5000.0 })
  );
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("ledger")
      .doc("led_01")
      .delete()
  );
});
