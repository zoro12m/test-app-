import { assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import {
    collection,
    deleteDoc,
    doc,
    getDoc,
    getDocs,
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
    dayFromToday,
    DEBT_ID,
    DEBT_KEYS,
    DEBTOR_ID,
    DEBTOR_KEYS,
    dbAs,
    newDebtData,
    newDebtorData,
    newPaymentData,
    PAYMENT_KEYS,
    seed,
    seedDebt,
    seedShops,
    SHARED_SHOP,
    stored,
} from './env.js';

function ref(uid, shopId, collectionName, id) {
    return doc(dbAs(uid), 'shops', shopId, collectionName, id);
}

function addDebtor(uid, data, shopId = ALICE_SHOP, id = 'debtor-new') {
    return setDoc(ref(uid, shopId, 'debtors', id), data);
}

function addDebt(uid, data, shopId = ALICE_SHOP, id = 'debt-new') {
    return setDoc(ref(uid, shopId, 'debts', id), data);
}

function addPayment(uid, data, shopId = ALICE_SHOP, id = 'payment-new') {
    return setDoc(ref(uid, shopId, 'debtPayments', id), data);
}

function deleteFields(uid, overrides = {}) {
    return { deletedAt: serverTimestamp(), deletedBy: uid, ...overrides };
}

describe('debtors: create', () => {
    beforeEach(seedShops);

    it('lets the owner add a debtor', async () => {
        await assertSucceeds(addDebtor(ALICE, newDebtorData(ALICE)));
    });

    it('lets the owner add a debtor without a phone', async () => {
        const data = newDebtorData(ALICE);
        delete data.phone;
        await assertSucceeds(addDebtor(ALICE, data));
    });

    it('lets another member of a shared shop add a debtor', async () => {
        await assertSucceeds(addDebtor(CAROL, newDebtorData(CAROL), SHARED_SHOP));
    });

    it('denies a non-member', async () => {
        await assertFails(addDebtor(BOB, newDebtorData(BOB)));
    });

    it('denies a signed-out user', async () => {
        await assertFails(addDebtor(null, newDebtorData(ALICE)));
    });

    it('denies createdBy other than the caller', async () => {
        await assertFails(addDebtor(ALICE, newDebtorData(ALICE, { createdBy: BOB })));
    });

    describe('name', () => {
        it('accepts 60 characters', async () => {
            await assertSucceeds(addDebtor(ALICE, newDebtorData(ALICE, { name: 'ر'.repeat(60) })));
        });

        for (const [label, name] of [
            ['empty', ''],
            ['61 characters', 'ر'.repeat(61)],
            ['not trimmed', ' رامي'],
            ['not a string', 7],
        ]) {
            it(`denies a name that is ${label}`, async () => {
                await assertFails(addDebtor(ALICE, newDebtorData(ALICE, { name })));
            });
        }
    });

    describe('phone', () => {
        for (const phone of ['03123456', '+961 3 123 456', '03-123-456', '1']) {
            it(`accepts "${phone}"`, async () => {
                await assertSucceeds(addDebtor(ALICE, newDebtorData(ALICE, { phone })));
            });
        }

        for (const [label, phone] of [
            ['empty', ''],
            ['21 characters', '1'.repeat(21)],
            ['not trimmed', ' 03123456'],
            ['letters', '03 call me'],
            ['Arabic digits (the app stores Western digits)', '٠٣١٢٣٤٥٦'],
            ['without a digit', '+ -'],
            ['null', null],
        ]) {
            it(`denies a phone that is ${label}`, async () => {
                await assertFails(addDebtor(ALICE, newDebtorData(ALICE, { phone })));
            });
        }
    });

    describe('fields', () => {
        it('accepts a note of 200 characters and denies 201', async () => {
            await assertSucceeds(addDebtor(ALICE, newDebtorData(ALICE, { note: 'ن'.repeat(200) }), ALICE_SHOP, 'a'));
            await assertFails(addDebtor(ALICE, newDebtorData(ALICE, { note: 'ن'.repeat(201) }), ALICE_SHOP, 'b'));
        });

        for (const key of DEBTOR_KEYS) {
            it(`denies a debtor without ${key}`, async () => {
                const data = newDebtorData(ALICE);
                delete data[key];
                await assertFails(addDebtor(ALICE, data));
            });
        }

        for (const [key, value] of [['remainingMinor', 0], ['balanceMinor', 0], ['deletedAt', null]]) {
            it(`denies an extra field ${key}`, async () => {
                await assertFails(addDebtor(ALICE, newDebtorData(ALICE, { [key]: value })));
            });
        }

        it('denies schemaVersion other than the integer 1 and a client-chosen createdAt', async () => {
            await assertFails(addDebtor(ALICE, newDebtorData(ALICE, { schemaVersion: 2 })));
            await assertFails(addDebtor(ALICE, newDebtorData(ALICE, { createdAt: Timestamp.now() })));
        });
    });
});

describe('debtors: read, change, delete', () => {
    beforeEach(async () => {
        await seedShops();
        await seedDebt(ALICE_SHOP, null);
    });

    it('lets the owner read and list debtors', async () => {
        await assertSucceeds(getDoc(ref(ALICE, ALICE_SHOP, 'debtors', DEBTOR_ID)));
        await assertSucceeds(getDocs(collection(dbAs(ALICE), 'shops', ALICE_SHOP, 'debtors')));
    });

    it('denies a non-member reading or listing', async () => {
        await assertFails(getDoc(ref(BOB, ALICE_SHOP, 'debtors', DEBTOR_ID)));
        await assertFails(getDocs(collection(dbAs(BOB), 'shops', ALICE_SHOP, 'debtors')));
    });

    it('denies changing or deleting a debtor', async () => {
        await assertFails(updateDoc(ref(ALICE, ALICE_SHOP, 'debtors', DEBTOR_ID), { name: 'سامي' }));
        await assertFails(updateDoc(ref(ALICE, ALICE_SHOP, 'debtors', DEBTOR_ID), deleteFields(ALICE)));
        await assertFails(deleteDoc(ref(ALICE, ALICE_SHOP, 'debtors', DEBTOR_ID)));
    });
});

describe('debts: create', () => {
    beforeEach(async () => {
        await seedShops();
        await seedDebt(ALICE_SHOP, null);
    });

    it('lets the owner record a USD debt', async () => {
        await assertSucceeds(addDebt(ALICE, newDebtData(ALICE)));
    });

    it('lets the owner record an LBP debt in whole lira', async () => {
        await assertSucceeds(addDebt(ALICE, newDebtData(ALICE, { currency: 'LBP', amountMinor: 500000 })));
    });

    it('lets another member of a shared shop record a debt', async () => {
        await seedDebt(SHARED_SHOP, null);
        await assertSucceeds(addDebt(CAROL, newDebtData(CAROL), SHARED_SHOP));
    });

    it('denies a non-member', async () => {
        await assertFails(addDebt(BOB, newDebtData(BOB)));
    });

    it('denies createdBy other than the caller', async () => {
        await assertFails(addDebt(ALICE, newDebtData(ALICE, { createdBy: BOB })));
    });

    describe('debtor', () => {
        it('denies a debtor that does not exist', async () => {
            await assertFails(addDebt(ALICE, newDebtData(ALICE, { debtorId: 'nobody' })));
        });

        it("denies a debtor of another shop", async () => {
            await seed(`shops/${SHARED_SHOP}/debtors/shared-only`, stored(newDebtorData(ALICE)));
            await assertFails(addDebt(ALICE, newDebtData(ALICE, { debtorId: 'shared-only' })));
        });

        for (const debtorId of ['', 'a/b', 7]) {
            it(`denies debtorId ${JSON.stringify(debtorId)}`, async () => {
                await assertFails(addDebt(ALICE, newDebtData(ALICE, { debtorId })));
            });
        }
    });

    describe('amount and currency', () => {
        it('accepts 1 and 10,000,000,000 in either currency', async () => {
            for (const currency of ['USD', 'LBP']) {
                await assertSucceeds(addDebt(ALICE, newDebtData(ALICE, { currency, amountMinor: 1 }), ALICE_SHOP, `${currency}-min`));
                await assertSucceeds(addDebt(ALICE, newDebtData(ALICE, { currency, amountMinor: 10000000000 }), ALICE_SHOP, `${currency}-max`));
            }
        });

        for (const [label, amountMinor] of [
            ['zero', 0],
            ['negative', -100],
            ['above the maximum', 10000000001],
            ['a fraction', 12.5],
            ['a string', '10000'],
        ]) {
            it(`denies an amount that is ${label}`, async () => {
                await assertFails(addDebt(ALICE, newDebtData(ALICE, { amountMinor })));
            });
        }

        for (const currency of ['EUR', 'usd', 'lbp', '']) {
            it(`denies currency "${currency}"`, async () => {
                await assertFails(addDebt(ALICE, newDebtData(ALICE, { currency })));
            });
        }
    });

    describe('debt date', () => {
        it('accepts today, tomorrow (time-zone slack) and 2000-01-01', async () => {
            await assertSucceeds(addDebt(ALICE, newDebtData(ALICE, { debtDate: dayFromToday(0) }), ALICE_SHOP, 'today'));
            await assertSucceeds(addDebt(ALICE, newDebtData(ALICE, { debtDate: dayFromToday(1) }), ALICE_SHOP, 'tomorrow'));
            await assertSucceeds(addDebt(ALICE, newDebtData(ALICE, { debtDate: '2000-01-01' }), ALICE_SHOP, 'old'));
        });

        for (const [label, debtDate] of [
            ['two days ahead', dayFromToday(2)],
            ['before 2000', '1999-12-31'],
            ['without zero padding', '2026-10-1'],
            ['in another format', '01/10/2026'],
            ['an impossible month', '2026-13-01'],
            ['a timestamp', Timestamp.fromDate(new Date('2026-10-01T00:00:00Z'))],
            ['empty', ''],
        ]) {
            it(`denies a debt date that is ${label}`, async () => {
                await assertFails(addDebt(ALICE, newDebtData(ALICE, { debtDate })));
            });
        }
    });

    describe('fields', () => {
        it('accepts a note of 200 characters and denies 201', async () => {
            await assertSucceeds(addDebt(ALICE, newDebtData(ALICE, { note: 'ن'.repeat(200) }), ALICE_SHOP, 'a'));
            await assertFails(addDebt(ALICE, newDebtData(ALICE, { note: 'ن'.repeat(201) }), ALICE_SHOP, 'b'));
        });

        for (const key of DEBT_KEYS) {
            it(`denies a debt without ${key}`, async () => {
                const data = newDebtData(ALICE);
                delete data[key];
                await assertFails(addDebt(ALICE, data));
            });
        }

        // Remaining and status are always worked out from the payments, never stored.
        for (const [key, value] of [
            ['remainingMinor', 10000],
            ['paidMinor', 0],
            ['status', 'open'],
            ['deletedAt', null],
            ['deletedBy', ALICE],
        ]) {
            it(`denies an extra field ${key}`, async () => {
                await assertFails(addDebt(ALICE, newDebtData(ALICE, { [key]: value })));
            });
        }

        it('denies schemaVersion other than the integer 1 and a client-chosen createdAt', async () => {
            await assertFails(addDebt(ALICE, newDebtData(ALICE, { schemaVersion: '1' })));
            await assertFails(addDebt(ALICE, newDebtData(ALICE, { createdAt: Timestamp.now() })));
        });
    });
});

describe('debts: read, change, delete', () => {
    const debtRef = (uid) => ref(uid, ALICE_SHOP, 'debts', DEBT_ID);

    beforeEach(async () => {
        await seedShops();
        await seedDebt();
    });

    it('lets the owner read and list debts', async () => {
        await assertSucceeds(getDoc(debtRef(ALICE)));
        await assertSucceeds(getDocs(collection(dbAs(ALICE), 'shops', ALICE_SHOP, 'debts')));
    });

    it('denies a non-member reading or listing', async () => {
        await assertFails(getDoc(debtRef(BOB)));
        await assertFails(getDocs(collection(dbAs(BOB), 'shops', ALICE_SHOP, 'debts')));
    });

    it('lets the owner mark a debt deleted once', async () => {
        await assertSucceeds(updateDoc(debtRef(ALICE), deleteFields(ALICE)));
        await assertFails(updateDoc(debtRef(ALICE), deleteFields(ALICE)));
    });

    it('denies a non-member marking a debt deleted', async () => {
        await assertFails(updateDoc(debtRef(BOB), deleteFields(BOB)));
    });

    it('denies a delete mark with the wrong person or a client time', async () => {
        await assertFails(updateDoc(debtRef(ALICE), deleteFields(ALICE, { deletedBy: BOB })));
        await assertFails(updateDoc(debtRef(ALICE), deleteFields(ALICE, { deletedAt: Timestamp.now() })));
        await assertFails(updateDoc(debtRef(ALICE), { deletedAt: serverTimestamp() }));
    });

    it('denies changing a saved debt', async () => {
        await assertFails(updateDoc(debtRef(ALICE), { amountMinor: 5000 }));
        await assertFails(updateDoc(debtRef(ALICE), { debtDate: '2026-09-01' }));
        await assertFails(updateDoc(debtRef(ALICE), { ...deleteFields(ALICE), amountMinor: 5000 }));
    });

    it('denies removing a debt from the database', async () => {
        await assertFails(deleteDoc(debtRef(ALICE)));
    });
});

describe('debtPayments: create', () => {
    beforeEach(async () => {
        await seedShops();
        await seedDebt();
    });

    it('lets the owner record a part payment', async () => {
        await assertSucceeds(addPayment(ALICE, newPaymentData(ALICE)));
    });

    it('lets the owner pay the whole debt at once', async () => {
        await assertSucceeds(addPayment(ALICE, newPaymentData(ALICE, { amountMinor: 10000 })));
    });

    it('lets the owner pay an LBP debt in whole lira', async () => {
        await seed(`shops/${ALICE_SHOP}/debts/lbp-debt`, stored(newDebtData(ALICE, { currency: 'LBP', amountMinor: 500000 })));
        await assertSucceeds(addPayment(ALICE, newPaymentData(ALICE, { debtId: 'lbp-debt', amountMinor: 150000 })));
    });

    it('lets another member of a shared shop record a payment', async () => {
        await seedDebt(SHARED_SHOP);
        await assertSucceeds(addPayment(CAROL, newPaymentData(CAROL), SHARED_SHOP));
    });

    it('denies a non-member', async () => {
        await assertFails(addPayment(BOB, newPaymentData(BOB)));
    });

    it('denies createdBy other than the caller', async () => {
        await assertFails(addPayment(ALICE, newPaymentData(ALICE, { createdBy: BOB })));
    });

    describe('debt', () => {
        it('denies a debt that does not exist', async () => {
            await assertFails(addPayment(ALICE, newPaymentData(ALICE, { debtId: 'no-such-debt' })));
        });

        it('denies a debt of another shop', async () => {
            await seed(`shops/${SHARED_SHOP}/debts/shared-only`, stored(newDebtData(ALICE)));
            await assertFails(addPayment(ALICE, newPaymentData(ALICE, { debtId: 'shared-only' })));
        });

        it('denies a deleted debt', async () => {
            await seed(`shops/${ALICE_SHOP}/debts/${DEBT_ID}`, stored(newDebtData(ALICE, {
                deletedAt: Timestamp.now(),
                deletedBy: ALICE,
            })));
            await assertFails(addPayment(ALICE, newPaymentData(ALICE)));
        });

        for (const debtId of ['', 'a/b', null]) {
            it(`denies debtId ${JSON.stringify(debtId)}`, async () => {
                await assertFails(addPayment(ALICE, newPaymentData(ALICE, { debtId })));
            });
        }
    });

    describe('amount', () => {
        for (const [label, amountMinor] of [
            ['zero', 0],
            ['negative', -100],
            ['more than the debt', 10001],
            ['a fraction', 12.5],
            ['a string', '3000'],
        ]) {
            it(`denies an amount that is ${label}`, async () => {
                await assertFails(addPayment(ALICE, newPaymentData(ALICE, { amountMinor })));
            });
        }

        // Rules cannot add up earlier payments: the server accepts 80 after 30 on a 100
        // debt, and the app is what refuses it (DebtAccount.checkPayment).
        it('leaves "not more than the remaining" to the app', async () => {
            await seed(`shops/${ALICE_SHOP}/debtPayments/p1`, stored(newPaymentData(ALICE)));
            await assertSucceeds(addPayment(ALICE, newPaymentData(ALICE, { amountMinor: 8000 })));
        });

        it('denies a currency field (a payment is always in its debt currency)', async () => {
            await assertFails(addPayment(ALICE, newPaymentData(ALICE, { currency: 'USD' })));
        });
    });

    describe('payment date', () => {
        it('accepts the debt date itself, today and tomorrow', async () => {
            await assertSucceeds(addPayment(ALICE, newPaymentData(ALICE, { paymentDate: '2026-10-01' }), ALICE_SHOP, 'a'));
            await assertSucceeds(addPayment(ALICE, newPaymentData(ALICE, { paymentDate: dayFromToday(0) }), ALICE_SHOP, 'b'));
            await assertSucceeds(addPayment(ALICE, newPaymentData(ALICE, { paymentDate: dayFromToday(1) }), ALICE_SHOP, 'c'));
        });

        for (const [label, paymentDate] of [
            ['before the debt date', '2026-09-30'],
            ['two days ahead', dayFromToday(2)],
            ['in another format', '5/10/2026'],
            ['a timestamp', Timestamp.now()],
        ]) {
            it(`denies a payment date that is ${label}`, async () => {
                await assertFails(addPayment(ALICE, newPaymentData(ALICE, { paymentDate })));
            });
        }
    });

    describe('fields', () => {
        it('accepts a note of 200 characters and denies 201', async () => {
            await assertSucceeds(addPayment(ALICE, newPaymentData(ALICE, { note: 'ن'.repeat(200) }), ALICE_SHOP, 'a'));
            await assertFails(addPayment(ALICE, newPaymentData(ALICE, { note: 'ن'.repeat(201) }), ALICE_SHOP, 'b'));
        });

        for (const key of PAYMENT_KEYS) {
            it(`denies a payment without ${key}`, async () => {
                const data = newPaymentData(ALICE);
                delete data[key];
                await assertFails(addPayment(ALICE, data));
            });
        }

        for (const [key, value] of [['debtorId', DEBTOR_ID], ['remainingMinor', 7000], ['deletedAt', null]]) {
            it(`denies an extra field ${key}`, async () => {
                await assertFails(addPayment(ALICE, newPaymentData(ALICE, { [key]: value })));
            });
        }

        it('denies schemaVersion other than the integer 1 and a client-chosen createdAt', async () => {
            await assertFails(addPayment(ALICE, newPaymentData(ALICE, { schemaVersion: 2 })));
            await assertFails(addPayment(ALICE, newPaymentData(ALICE, { createdAt: Timestamp.now() })));
        });
    });
});

describe('debtPayments: read, change, delete', () => {
    const paymentRef = (uid) => ref(uid, ALICE_SHOP, 'debtPayments', 'p1');

    beforeEach(async () => {
        await seedShops();
        await seedDebt();
        await seed(`shops/${ALICE_SHOP}/debtPayments/p1`, stored(newPaymentData(ALICE)));
    });

    it('lets the owner read and list payments', async () => {
        await assertSucceeds(getDoc(paymentRef(ALICE)));
        await assertSucceeds(getDocs(collection(dbAs(ALICE), 'shops', ALICE_SHOP, 'debtPayments')));
    });

    it('denies a non-member reading or listing', async () => {
        await assertFails(getDoc(paymentRef(BOB)));
        await assertFails(getDocs(collection(dbAs(BOB), 'shops', ALICE_SHOP, 'debtPayments')));
    });

    it('lets the owner mark a payment deleted once', async () => {
        await assertSucceeds(updateDoc(paymentRef(ALICE), deleteFields(ALICE)));
        await assertFails(updateDoc(paymentRef(ALICE), deleteFields(ALICE)));
    });

    it('denies changing a saved payment', async () => {
        await assertFails(updateDoc(paymentRef(ALICE), { amountMinor: 1000 }));
        await assertFails(updateDoc(paymentRef(ALICE), { ...deleteFields(ALICE), amountMinor: 1000 }));
    });

    it('denies removing a payment from the database', async () => {
        await assertFails(deleteDoc(paymentRef(ALICE)));
    });

    it('denies anything nested under a debt or payment', async () => {
        await assertFails(setDoc(doc(dbAs(ALICE), 'shops', ALICE_SHOP, 'debts', DEBT_ID, 'notes', 'n1'), { text: 'x' }));
        await assertFails(setDoc(doc(dbAs(ALICE), 'shops', ALICE_SHOP, 'debtPayments', 'p1', 'notes', 'n1'), { text: 'x' }));
    });
});
