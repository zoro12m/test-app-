import { assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { collection, deleteDoc, doc, getDoc, getDocs, setDoc } from 'firebase/firestore';

import { ALICE, ALICE_SHOP, BOB, CAROL, dbAs, seed, seedShops, SHARED_SHOP } from './env.js';

describe('shops subcollections (Stage 3 placeholders)', () => {
    beforeEach(async () => {
        await seedShops();
        await seed(`shops/${ALICE_SHOP}/ledger/entry-1`, { amountMinor: 1000 });
        await seed(`shops/${SHARED_SHOP}/ledger/entry-1`, { amountMinor: 1000 });
    });

    for (const sub of ['ledger', 'parties', 'dues']) {
        it(`denies the owner writing ${sub}`, async () => {
            await assertFails(setDoc(doc(dbAs(ALICE), 'shops', ALICE_SHOP, sub, 'x'), { amountMinor: 1 }));
        });
    }

    it('denies the owner reading an existing subcollection document', async () => {
        await assertFails(getDoc(doc(dbAs(ALICE), 'shops', ALICE_SHOP, 'ledger', 'entry-1')));
    });

    it('denies the owner listing a subcollection', async () => {
        await assertFails(getDocs(collection(dbAs(ALICE), 'shops', ALICE_SHOP, 'ledger')));
    });

    it('denies the owner deleting a subcollection document', async () => {
        await assertFails(deleteDoc(doc(dbAs(ALICE), 'shops', ALICE_SHOP, 'ledger', 'entry-1')));
    });

    it('denies a member reading or writing a subcollection', async () => {
        await assertFails(getDoc(doc(dbAs(CAROL), 'shops', SHARED_SHOP, 'ledger', 'entry-1')));
        await assertFails(setDoc(doc(dbAs(CAROL), 'shops', SHARED_SHOP, 'ledger', 'x'), { amountMinor: 1 }));
    });

    it('denies deeper nesting', async () => {
        const deep = doc(dbAs(ALICE), 'shops', ALICE_SHOP, 'ledger', 'entry-1', 'notes', 'n1');
        await assertFails(setDoc(deep, { text: 'x' }));
        await assertFails(getDoc(deep));
    });
});

describe('users/{uid}', () => {
    it('lets a user write, read and delete their own document', async () => {
        const ref = doc(dbAs(ALICE), 'users', ALICE);
        await assertSucceeds(setDoc(ref, { displayName: 'Alice' }));
        await assertSucceeds(getDoc(ref));
        await assertSucceeds(deleteDoc(ref));
    });

    it("denies reading another user's document", async () => {
        await seed(`users/${ALICE}`, { displayName: 'Alice' });
        await assertFails(getDoc(doc(dbAs(BOB), 'users', ALICE)));
    });

    it("denies writing another user's document", async () => {
        await assertFails(setDoc(doc(dbAs(BOB), 'users', ALICE), { displayName: 'Bob' }));
    });

    it('denies listing users', async () => {
        await assertFails(getDocs(collection(dbAs(ALICE), 'users')));
    });

    it('denies a signed-out user', async () => {
        await seed(`users/${ALICE}`, { displayName: 'Alice' });
        await assertFails(getDoc(doc(dbAs(null), 'users', ALICE)));
        await assertFails(setDoc(doc(dbAs(null), 'users', ALICE), { displayName: 'Alice' }));
    });
});

describe('default deny', () => {
    it('denies unmatched top-level collections', async () => {
        await seed('ledger/entry-1', { amountMinor: 1 });
        await assertFails(getDoc(doc(dbAs(ALICE), 'ledger', 'entry-1')));
        await assertFails(setDoc(doc(dbAs(ALICE), 'ledger', 'entry-2'), { amountMinor: 1 }));
        await assertFails(getDocs(collection(dbAs(ALICE), 'ledger')));
    });

    it('denies subcollections under users', async () => {
        await assertFails(setDoc(doc(dbAs(ALICE), 'users', ALICE, 'private', 'x'), { a: 1 }));
    });
});
