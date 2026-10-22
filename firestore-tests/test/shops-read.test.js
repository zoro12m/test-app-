import assert from 'node:assert/strict';

import { assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { collection, doc, getDoc, getDocs, query, where } from 'firebase/firestore';

import { ALICE, ALICE_SHOP, BOB, CAROL, dbAs, seedShops, SHARED_SHOP } from './env.js';

/** The app's shop list query: memberUids array-contains [memberUid]. */
function memberQuery(db, memberUid) {
    return query(collection(db, 'shops'), where('memberUids', 'array-contains', memberUid));
}

describe('shops: read', () => {
    beforeEach(seedShops);

    describe('get', () => {
        it('lets the owner get their shop', async () => {
            const snap = await assertSucceeds(getDoc(doc(dbAs(ALICE), 'shops', ALICE_SHOP)));
            assert.equal(snap.get('ownerUid'), ALICE);
        });

        it('lets a member who is not the owner get the shop', async () => {
            await assertSucceeds(getDoc(doc(dbAs(CAROL), 'shops', SHARED_SHOP)));
        });

        it('denies a non-member', async () => {
            await assertFails(getDoc(doc(dbAs(BOB), 'shops', ALICE_SHOP)));
            await assertFails(getDoc(doc(dbAs(CAROL), 'shops', ALICE_SHOP)));
        });

        it('denies a signed-out user', async () => {
            await assertFails(getDoc(doc(dbAs(null), 'shops', ALICE_SHOP)));
        });
    });

    describe('list', () => {
        it('lets a member list their shops with memberUids array-contains', async () => {
            const snap = await assertSucceeds(getDocs(memberQuery(dbAs(ALICE), ALICE)));
            assert.deepEqual(snap.docs.map((d) => d.id).sort(), [ALICE_SHOP, SHARED_SHOP]);
        });

        it('shows a non-owner member only the shops they belong to', async () => {
            const snap = await assertSucceeds(getDocs(memberQuery(dbAs(CAROL), CAROL)));
            assert.deepEqual(snap.docs.map((d) => d.id), [SHARED_SHOP]);
        });

        it('returns nothing to a user with no shops', async () => {
            const snap = await assertSucceeds(getDocs(memberQuery(dbAs(BOB), BOB)));
            assert.equal(snap.size, 0);
        });

        it("denies a non-member querying someone else's uid", async () => {
            await assertFails(getDocs(memberQuery(dbAs(BOB), ALICE)));
        });

        it('denies a query without the memberUids filter', async () => {
            await assertFails(getDocs(collection(dbAs(ALICE), 'shops')));
        });

        it('denies a query filtered on ownerUid only', async () => {
            await assertFails(getDocs(query(collection(dbAs(ALICE), 'shops'), where('ownerUid', '==', ALICE))));
        });

        it('denies a signed-out user', async () => {
            await assertFails(getDocs(memberQuery(dbAs(null), ALICE)));
        });
    });
});
