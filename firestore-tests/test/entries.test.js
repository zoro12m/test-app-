import { assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import {
    collection,
    deleteDoc,
    deleteField,
    doc,
    getDoc,
    getDocs,
    orderBy,
    query,
    serverTimestamp,
    setDoc,
    Timestamp,
    updateDoc,
} from 'firebase/firestore';

import {
    ALICE,
    ALICE_SHOP,
    BOB,
    CAROL,
    dbAs,
    ENTRY_KEYS,
    newEntryData,
    seed,
    seedShops,
    SHARED_SHOP,
    storedEntryData,
} from './env.js';

function entryRef(uid, shopId, entryId = 'entry-new') {
    return doc(dbAs(uid), 'shops', shopId, 'entries', entryId);
}

function createAs(uid, data, shopId = ALICE_SHOP) {
    return setDoc(entryRef(uid, shopId), data);
}

function voidAs(uid, fields, shopId = ALICE_SHOP, entryId = 'entry-1') {
    return updateDoc(entryRef(uid, shopId, entryId), fields);
}

function voidFields(uid, overrides = {}) {
    return { voidedAt: serverTimestamp(), voidReason: 'سُجّل مرتين', voidedBy: uid, ...overrides };
}

describe('entries: create', () => {
    beforeEach(seedShops);

    for (const type of ['sale', 'capital_in', 'capital_out']) {
        it(`lets the owner record a ${type}`, async () => {
            await assertSucceeds(createAs(ALICE, newEntryData(ALICE, { type })));
        });
    }

    it('lets the owner record an expense with a category', async () => {
        await assertSucceeds(createAs(ALICE, newEntryData(ALICE, { type: 'expense', categoryId: 'electricity' })));
    });

    it('accepts every expense category', async () => {
        for (const categoryId of ['goods', 'rent', 'electricity', 'salaries', 'transport', 'maintenance', 'other']) {
            const ref = entryRef(ALICE, ALICE_SHOP, `expense-${categoryId}`);
            await assertSucceeds(setDoc(ref, newEntryData(ALICE, { type: 'expense', categoryId })));
        }
    });

    it('lets another member of a shared shop record an entry', async () => {
        await assertSucceeds(createAs(CAROL, newEntryData(CAROL), SHARED_SHOP));
    });

    it('denies a non-member', async () => {
        await assertFails(createAs(BOB, newEntryData(BOB)));
    });

    it('denies a signed-out user', async () => {
        await assertFails(createAs(null, newEntryData(ALICE)));
    });

    it('denies an entry in a shop that does not exist', async () => {
        await assertFails(createAs(ALICE, newEntryData(ALICE), 'no-such-shop'));
    });

    it('denies createdBy other than the caller', async () => {
        await assertFails(createAs(ALICE, newEntryData(ALICE, { createdBy: BOB })));
    });

    describe('type and category', () => {
        for (const type of ['debt_given', 'debt_payment', 'liability_in', 'loan_received', 'liability_payment', 'refund', '']) {
            it(`denies type "${type}"`, async () => {
                await assertFails(createAs(ALICE, newEntryData(ALICE, { type })));
            });
        }

        it('denies an expense without a category', async () => {
            await assertFails(createAs(ALICE, newEntryData(ALICE, { type: 'expense' })));
        });

        it('denies an unknown expense category', async () => {
            await assertFails(createAs(ALICE, newEntryData(ALICE, { type: 'expense', categoryId: 'fun' })));
        });

        it('denies a category on a sale', async () => {
            await assertFails(createAs(ALICE, newEntryData(ALICE, { categoryId: 'goods' })));
        });

        it('denies a null category on a sale', async () => {
            await assertFails(createAs(ALICE, newEntryData(ALICE, { categoryId: null })));
        });
    });

    describe('amount and currency', () => {
        it('accepts 1 cent and 100,000,000.00 USD', async () => {
            await assertSucceeds(setDoc(entryRef(ALICE, ALICE_SHOP, 'min'), newEntryData(ALICE, { amountMinor: 1 })));
            await assertSucceeds(setDoc(entryRef(ALICE, ALICE_SHOP, 'max'), newEntryData(ALICE, { amountMinor: 10000000000 })));
        });

        for (const [label, amountMinor] of [
            ['zero', 0],
            ['negative', -100],
            ['above the maximum', 10000000001],
            ['a fraction of a cent', 12.5],
            ['a string', '1250'],
        ]) {
            it(`denies an amount that is ${label}`, async () => {
                await assertFails(createAs(ALICE, newEntryData(ALICE, { amountMinor })));
            });
        }

        for (const currency of ['LBP', 'usd', 'EUR']) {
            it(`denies currency ${currency}`, async () => {
                await assertFails(createAs(ALICE, newEntryData(ALICE, { currency })));
            });
        }
    });

    describe('dates', () => {
        it('accepts an entry dated now', async () => {
            await assertSucceeds(createAs(ALICE, newEntryData(ALICE, { occurredAt: Timestamp.now() })));
        });

        it('accepts an entry dated up to a day ahead (clock and time-zone slack)', async () => {
            const soon = Timestamp.fromMillis(Date.now() + 20 * 60 * 60 * 1000);
            await assertSucceeds(createAs(ALICE, newEntryData(ALICE, { occurredAt: soon })));
        });

        it('denies an entry dated more than a day ahead', async () => {
            const later = Timestamp.fromMillis(Date.now() + 2 * 24 * 60 * 60 * 1000);
            await assertFails(createAs(ALICE, newEntryData(ALICE, { occurredAt: later })));
        });

        it('denies an entry dated before 2000', async () => {
            const old = Timestamp.fromDate(new Date('1999-12-31T00:00:00Z'));
            await assertFails(createAs(ALICE, newEntryData(ALICE, { occurredAt: old })));
        });

        it('denies occurredAt that is not a timestamp', async () => {
            await assertFails(createAs(ALICE, newEntryData(ALICE, { occurredAt: '2026-10-01' })));
        });

        it('denies a client-chosen createdAt', async () => {
            await assertFails(createAs(ALICE, newEntryData(ALICE, { createdAt: Timestamp.now() })));
        });
    });

    describe('fields', () => {
        it('accepts a note of 200 characters', async () => {
            await assertSucceeds(createAs(ALICE, newEntryData(ALICE, { note: 'ن'.repeat(200) })));
        });

        it('denies a note of 201 characters', async () => {
            await assertFails(createAs(ALICE, newEntryData(ALICE, { note: 'ن'.repeat(201) })));
        });

        it('denies a note that is not a string', async () => {
            await assertFails(createAs(ALICE, newEntryData(ALICE, { note: null })));
        });

        for (const key of ENTRY_KEYS) {
            it(`denies an entry without ${key}`, async () => {
                const data = newEntryData(ALICE);
                delete data[key];
                await assertFails(createAs(ALICE, data));
            });
        }

        for (const [key, value] of [
            ['partyId', 'customer-1'],
            ['voidedAt', null],
            ['voidReason', 'x'],
            ['balanceMinor', 0],
        ]) {
            it(`denies an extra field ${key}`, async () => {
                await assertFails(createAs(ALICE, newEntryData(ALICE, { [key]: value })));
            });
        }

        it('denies schemaVersion other than the integer 1', async () => {
            await assertFails(createAs(ALICE, newEntryData(ALICE, { schemaVersion: 2 })));
            await assertFails(createAs(ALICE, newEntryData(ALICE, { schemaVersion: '1' })));
        });
    });
});

describe('entries: read', () => {
    beforeEach(async () => {
        await seedShops();
        await seed(`shops/${ALICE_SHOP}/entries/entry-1`, storedEntryData(ALICE));
        await seed(`shops/${SHARED_SHOP}/entries/entry-1`, storedEntryData(ALICE));
    });

    it('lets the owner get and list entries, newest first', async () => {
        await assertSucceeds(getDoc(entryRef(ALICE, ALICE_SHOP, 'entry-1')));
        const entries = collection(dbAs(ALICE), 'shops', ALICE_SHOP, 'entries');
        await assertSucceeds(getDocs(query(entries, orderBy('occurredAt', 'desc'))));
    });

    it('lets another member read a shared shop', async () => {
        await assertSucceeds(getDocs(collection(dbAs(CAROL), 'shops', SHARED_SHOP, 'entries')));
    });

    it('denies a non-member get and list', async () => {
        await assertFails(getDoc(entryRef(BOB, ALICE_SHOP, 'entry-1')));
        await assertFails(getDocs(collection(dbAs(BOB), 'shops', ALICE_SHOP, 'entries')));
    });

    it('denies a member of one shop reading another shop', async () => {
        await assertFails(getDocs(collection(dbAs(CAROL), 'shops', ALICE_SHOP, 'entries')));
    });

    it('denies a signed-out read', async () => {
        await assertFails(getDocs(collection(dbAs(null), 'shops', ALICE_SHOP, 'entries')));
    });
});

describe('entries: void, update and delete', () => {
    beforeEach(async () => {
        await seedShops();
        await seed(`shops/${ALICE_SHOP}/entries/entry-1`, storedEntryData(ALICE));
        await seed(`shops/${SHARED_SHOP}/entries/entry-1`, storedEntryData(ALICE));
    });

    it('lets the owner void an entry once, with a reason', async () => {
        await assertSucceeds(voidAs(ALICE, voidFields(ALICE)));
    });

    it('lets another member void an entry in a shared shop', async () => {
        await assertSucceeds(voidAs(CAROL, voidFields(CAROL), SHARED_SHOP));
    });

    it('denies voiding twice', async () => {
        await assertSucceeds(voidAs(ALICE, voidFields(ALICE)));
        await assertFails(voidAs(ALICE, voidFields(ALICE, { voidReason: 'مرة ثانية' })));
    });

    it('denies a non-member voiding', async () => {
        await assertFails(voidAs(BOB, voidFields(BOB)));
    });

    it('denies voidedBy other than the caller', async () => {
        await assertFails(voidAs(ALICE, voidFields(BOB)));
    });

    it('denies a client-chosen voidedAt', async () => {
        await assertFails(voidAs(ALICE, voidFields(ALICE, { voidedAt: Timestamp.now() })));
    });

    for (const [label, voidReason] of [
        ['empty', ''],
        ['whitespace only', '   '],
        ['untrimmed', ' سُجّل مرتين'],
        ['201 characters', 'س'.repeat(201)],
        ['not a string', 42],
    ]) {
        it(`denies a void reason that is ${label}`, async () => {
            await assertFails(voidAs(ALICE, voidFields(ALICE, { voidReason })));
        });
    }

    it('accepts a void reason of 200 characters', async () => {
        await assertSucceeds(voidAs(ALICE, voidFields(ALICE, { voidReason: 'س'.repeat(200) })));
    });

    it('denies a void without a reason', async () => {
        await assertFails(voidAs(ALICE, { voidedAt: serverTimestamp(), voidedBy: ALICE }));
    });

    for (const [key, value] of [
        ['amountMinor', 999],
        ['type', 'expense'],
        ['occurredAt', Timestamp.now()],
        ['note', 'تعديل'],
        ['currency', 'LBP'],
        ['createdBy', BOB],
    ]) {
        it(`denies changing ${key}`, async () => {
            await assertFails(voidAs(ALICE, { [key]: value }));
        });

        it(`denies changing ${key} while voiding`, async () => {
            await assertFails(voidAs(ALICE, { ...voidFields(ALICE), [key]: value }));
        });
    }

    it('denies removing a field', async () => {
        await assertFails(voidAs(ALICE, { note: deleteField() }));
    });

    it('denies un-voiding', async () => {
        await assertSucceeds(voidAs(ALICE, voidFields(ALICE)));
        await assertFails(voidAs(ALICE, { voidedAt: deleteField(), voidReason: deleteField(), voidedBy: deleteField() }));
    });

    it('denies replacing an existing entry with a new one', async () => {
        await assertFails(setDoc(entryRef(ALICE, ALICE_SHOP, 'entry-1'), newEntryData(ALICE, { amountMinor: 1 })));
    });

    it('denies deleting an entry, even for the owner', async () => {
        await assertFails(deleteDoc(entryRef(ALICE, ALICE_SHOP, 'entry-1')));
    });
});
