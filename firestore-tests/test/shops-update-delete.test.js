import { assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import {
    arrayUnion,
    deleteDoc,
    deleteField,
    doc,
    serverTimestamp,
    setDoc,
    Timestamp,
    updateDoc,
} from 'firebase/firestore';

import { ALICE, ALICE_SHOP, BOB, CAROL, dbAs, newShopData, seedShops, SHARED_SHOP } from './env.js';

function shopRef(uid, shopId = ALICE_SHOP) {
    return doc(dbAs(uid), 'shops', shopId);
}

/** A rename exactly as the app would send it. */
function rename(name) {
    return { name, updatedAt: serverTimestamp() };
}

describe('shops: update', () => {
    beforeEach(seedShops);

    it('lets the owner rename the shop', async () => {
        await assertSucceeds(updateDoc(shopRef(ALICE), rename('متجر النور')));
    });

    it('lets the owner rename a shop that has other members', async () => {
        await assertSucceeds(updateDoc(shopRef(ALICE, SHARED_SHOP), rename('متجر جديد')));
    });

    it('denies a rename by a member who is not the owner', async () => {
        await assertFails(updateDoc(shopRef(CAROL, SHARED_SHOP), rename('متجر كارول')));
    });

    it('denies a rename by a non-member', async () => {
        await assertFails(updateDoc(shopRef(BOB), rename('متجر بوب')));
    });

    it('denies a rename by a signed-out user', async () => {
        await assertFails(updateDoc(shopRef(null), rename('متجر')));
    });

    it('denies a rename that does not set updatedAt to server time', async () => {
        await assertFails(updateDoc(shopRef(ALICE), { name: 'متجر النور' }));
    });

    it('denies a rename with a client updatedAt', async () => {
        await assertFails(updateDoc(shopRef(ALICE), { name: 'متجر النور', updatedAt: Timestamp.now() }));
    });

    for (const [label, name] of [
        ['empty', ''],
        ['whitespace-only', '   '],
        ['61 characters', 'a'.repeat(61)],
        ['untrimmed', ' متجر '],
        ['not a string', 7],
    ]) {
        it(`denies a rename to a name that is ${label}`, async () => {
            await assertFails(updateDoc(shopRef(ALICE), rename(name)));
        });
    }

    it('denies changing ownerUid', async () => {
        await assertFails(updateDoc(shopRef(ALICE), { ...rename('متجر'), ownerUid: BOB }));
        await assertFails(updateDoc(shopRef(ALICE), { ownerUid: BOB }));
    });

    it('denies changing memberUids', async () => {
        await assertFails(updateDoc(shopRef(ALICE), { ...rename('متجر'), memberUids: arrayUnion(BOB) }));
        await assertFails(updateDoc(shopRef(ALICE), { memberUids: [ALICE, BOB] }));
    });

    it('denies a non-member adding themselves to memberUids', async () => {
        await assertFails(updateDoc(shopRef(BOB), { memberUids: arrayUnion(BOB) }));
    });

    it('denies changing currency', async () => {
        await assertFails(updateDoc(shopRef(ALICE), { ...rename('متجر'), currency: 'LBP' }));
        await assertFails(updateDoc(shopRef(ALICE), { currency: 'LBP' }));
    });

    it('denies changing createdAt', async () => {
        await assertFails(updateDoc(shopRef(ALICE), { ...rename('متجر'), createdAt: serverTimestamp() }));
    });

    it('denies changing schemaVersion', async () => {
        await assertFails(updateDoc(shopRef(ALICE), { ...rename('متجر'), schemaVersion: 2 }));
    });

    it('denies adding a field', async () => {
        await assertFails(updateDoc(shopRef(ALICE), { ...rename('متجر'), balance: 100 }));
    });

    it('denies removing a field', async () => {
        await assertFails(updateDoc(shopRef(ALICE), { ...rename('متجر'), currency: deleteField() }));
    });

    it('denies overwriting the shop with fresh create data', async () => {
        await assertFails(setDoc(shopRef(ALICE), newShopData(ALICE, { name: 'متجر النور' })));
    });
});

describe('shops: delete', () => {
    beforeEach(seedShops);

    it('denies the owner', async () => {
        await assertFails(deleteDoc(shopRef(ALICE)));
    });

    it('denies a member who is not the owner', async () => {
        await assertFails(deleteDoc(shopRef(CAROL, SHARED_SHOP)));
    });

    it('denies a non-member', async () => {
        await assertFails(deleteDoc(shopRef(BOB)));
    });

    it('denies a signed-out user', async () => {
        await assertFails(deleteDoc(shopRef(null)));
    });
});
