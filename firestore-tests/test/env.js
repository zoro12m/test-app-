// Shared Firestore emulator test environment and fixtures.
//
// Loaded once by Mocha (see .mocharc.json "require") as a root hook plugin, and
// imported by the test files for the helpers below. The emulator address comes from
// FIRESTORE_EMULATOR_HOST, which `firebase emulators:exec` sets.

import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

import { initializeTestEnvironment } from '@firebase/rules-unit-testing';
import { doc, serverTimestamp, setDoc, setLogLevel, Timestamp } from 'firebase/firestore';

export const PROJECT_ID = 'demo-shopfinance';

const RULES_PATH = fileURLToPath(new URL('../../firestore.rules', import.meta.url));

/** The shop owner in most tests. */
export const ALICE = 'alice';
/** A signed-in user with no access to Alice's shops. */
export const BOB = 'bob';
/** A member of Alice's shared shop who is not its owner. */
export const CAROL = 'carol';

/** Alice's shop, members: [ALICE]. */
export const ALICE_SHOP = 'shop-alice';
/** A shop owned by Alice with CAROL as an extra member (as later stages may allow). */
export const SHARED_SHOP = 'shop-shared';

/** Every field of a shop document. */
export const SHOP_KEYS = [
    'name',
    'ownerUid',
    'memberUids',
    'currency',
    'createdAt',
    'updatedAt',
    'schemaVersion',
];

let testEnv;

export const mochaHooks = {
    async beforeAll() {
        setLogLevel('error');
        testEnv = await initializeTestEnvironment({
            projectId: PROJECT_ID,
            firestore: { rules: readFileSync(RULES_PATH, 'utf8') },
        });
    },
    async beforeEach() {
        await testEnv.clearFirestore();
    },
    async afterAll() {
        await testEnv?.cleanup();
    },
};

/** A Firestore instance acting as [uid], or as a signed-out user when uid is null. */
export function dbAs(uid) {
    const context = uid == null
        ? testEnv.unauthenticatedContext()
        : testEnv.authenticatedContext(uid);
    return context.firestore();
}

/** Shop data exactly as the app writes it on create, with optional overrides. */
export function newShopData(uid, overrides = {}) {
    return {
        name: 'متجر الأمل',
        ownerUid: uid,
        memberUids: [uid],
        currency: 'USD',
        createdAt: serverTimestamp(),
        updatedAt: serverTimestamp(),
        schemaVersion: 1,
        ...overrides,
    };
}

/** Writes a document while bypassing the rules. */
export async function seed(path, data) {
    await testEnv.withSecurityRulesDisabled(async (context) => {
        await setDoc(doc(context.firestore(), path), data);
    });
}

/** Seeds ALICE_SHOP (members [ALICE]) and SHARED_SHOP (members [ALICE, CAROL]). */
export async function seedShops() {
    const createdAt = Timestamp.fromDate(new Date('2026-01-01T00:00:00Z'));
    const base = {
        ownerUid: ALICE,
        currency: 'USD',
        createdAt,
        updatedAt: createdAt,
        schemaVersion: 1,
    };
    await seed(`shops/${ALICE_SHOP}`, { ...base, name: 'متجر الأمل', memberUids: [ALICE] });
    await seed(`shops/${SHARED_SHOP}`, { ...base, name: 'متجر مشترك', memberUids: [ALICE, CAROL] });
}

/** Every field a new ledger entry may have; categoryId only on expenses. */
export const ENTRY_KEYS = [
    'type',
    'amountMinor',
    'currency',
    'occurredAt',
    'note',
    'createdBy',
    'createdAt',
    'schemaVersion',
];

/** A new ledger entry exactly as the app writes it (a 12.50 USD cash sale), with optional overrides. */
export function newEntryData(uid, overrides = {}) {
    return {
        type: 'sale',
        amountMinor: 1250,
        currency: 'USD',
        occurredAt: Timestamp.fromDate(new Date('2026-10-01T10:00:00Z')),
        note: '',
        createdBy: uid,
        createdAt: serverTimestamp(),
        schemaVersion: 1,
        ...overrides,
    };
}

/** A stored entry as the server keeps it after create (timestamps resolved). */
export function storedEntryData(uid, overrides = {}) {
    return {
        ...newEntryData(uid),
        createdAt: Timestamp.fromDate(new Date('2026-10-01T10:00:05Z')),
        ...overrides,
    };
}
