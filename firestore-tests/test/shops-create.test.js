import { assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { doc, setDoc, Timestamp } from 'firebase/firestore';

import { ALICE, ALICE_SHOP, BOB, dbAs, newShopData, seedShops, SHOP_KEYS } from './env.js';

const NEW_SHOP = 'shop-new';

function createAs(uid, data) {
    return setDoc(doc(dbAs(uid), 'shops', NEW_SHOP), data);
}

describe('shops: create', () => {
    it('lets a signed-in user create a valid USD shop they own', async () => {
        await assertSucceeds(createAs(ALICE, newShopData(ALICE)));
    });

    it('accepts names of 1 and 60 characters, Arabic included', async () => {
        await assertSucceeds(setDoc(doc(dbAs(ALICE), 'shops', 'one'), newShopData(ALICE, { name: 'م' })));
        await assertSucceeds(setDoc(doc(dbAs(ALICE), 'shops', 'sixty'), newShopData(ALICE, { name: 'a'.repeat(60) })));
        await assertSucceeds(setDoc(doc(dbAs(ALICE), 'shops', 'arabic'), newShopData(ALICE, { name: 'م'.repeat(60) })));
    });

    it('accepts a name with single inner spaces', async () => {
        await assertSucceeds(createAs(ALICE, newShopData(ALICE, { name: 'متجر أبو علي' })));
    });

    // The app's ShopNameRules counts String.length (UTF-16 code units); so do the rules.
    it('counts name length in UTF-16 code units', async () => {
        await assertSucceeds(createAs(ALICE, newShopData(ALICE, { name: '😀'.repeat(30) })));
        await assertFails(setDoc(doc(dbAs(ALICE), 'shops', 'emoji-31'), newShopData(ALICE, { name: '😀'.repeat(31) })));
    });

    it('denies a signed-out user', async () => {
        await assertFails(createAs(null, newShopData(ALICE)));
    });

    describe('ownership and members', () => {
        it('denies an ownerUid other than the caller', async () => {
            await assertFails(createAs(ALICE, newShopData(ALICE, { ownerUid: BOB })));
        });

        it('denies extra members', async () => {
            await assertFails(createAs(ALICE, newShopData(ALICE, { memberUids: [ALICE, BOB] })));
        });

        it('denies empty members', async () => {
            await assertFails(createAs(ALICE, newShopData(ALICE, { memberUids: [] })));
        });

        it('denies members that leave out the owner', async () => {
            await assertFails(createAs(ALICE, newShopData(ALICE, { memberUids: [BOB] })));
        });

        it('denies members that are not a list', async () => {
            await assertFails(createAs(ALICE, newShopData(ALICE, { memberUids: ALICE })));
        });

        it('denies taking over an existing shop id', async () => {
            await seedShops();
            await assertFails(setDoc(doc(dbAs(BOB), 'shops', ALICE_SHOP), newShopData(BOB)));
        });
    });

    describe('keys', () => {
        it('denies an extra key', async () => {
            await assertFails(createAs(ALICE, newShopData(ALICE, { balance: 0 })));
        });

        for (const key of SHOP_KEYS) {
            it(`denies a missing ${key}`, async () => {
                const data = newShopData(ALICE);
                delete data[key];
                await assertFails(createAs(ALICE, data));
            });
        }
    });

    describe('currency', () => {
        it('denies LBP', async () => {
            await assertFails(createAs(ALICE, newShopData(ALICE, { currency: 'LBP' })));
        });

        it('denies lower-case usd', async () => {
            await assertFails(createAs(ALICE, newShopData(ALICE, { currency: 'usd' })));
        });
    });

    describe('name', () => {
        const invalidNames = [
            ['empty', ''],
            ['whitespace-only', '   '],
            ['61 characters', 'a'.repeat(61)],
            ['61 Arabic characters', 'م'.repeat(61)],
            ['a leading space', ' متجر'],
            ['a trailing space', 'متجر '],
            ['a leading tab', '\tمتجر'],
            ['a trailing newline', 'متجر\n'],
            // ShopNameRules.normalize in the app strips these because trim() here does.
            ['a leading control character U+0001', '\u0001متجر'],
            ['a trailing control character U+001B', 'متجر\u001B'],
            ['a number', 42],
            ['null', null],
        ];
        for (const [label, name] of invalidNames) {
            it(`denies a name that is ${label}`, async () => {
                await assertFails(createAs(ALICE, newShopData(ALICE, { name })));
            });
        }
    });

    describe('timestamps', () => {
        it('denies a client createdAt instead of server time', async () => {
            await assertFails(createAs(ALICE, newShopData(ALICE, { createdAt: Timestamp.now() })));
        });

        it('denies a client updatedAt instead of server time', async () => {
            await assertFails(createAs(ALICE, newShopData(ALICE, { updatedAt: Timestamp.now() })));
        });

        it('denies client timestamps for both fields', async () => {
            const now = Timestamp.now();
            await assertFails(createAs(ALICE, newShopData(ALICE, { createdAt: now, updatedAt: now })));
        });
    });

    describe('schemaVersion', () => {
        for (const schemaVersion of [0, 2, 1.5, '1']) {
            it(`denies schemaVersion ${JSON.stringify(schemaVersion)}`, async () => {
                await assertFails(createAs(ALICE, newShopData(ALICE, { schemaVersion })));
            });
        }
    });
});
