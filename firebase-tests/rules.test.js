/** Firebase Rules tests — design §7.4 (D2C F&B, Nhịp 1).
 *
 * Requires a running Firestore emulator:
 *   firebase emulators:start --only firestore --project demo-d2cfood --config firebase.json
 * then: npm test   (from firebase-tests/)
 *
 * Uses @firebase/rules-unit-testing v3, which evaluates the shipped
 * firestore.rules against the emulator. §7.4.5 (Android local
 * notifications) is app behaviour — verified manually in Nhịp 4.
 */
const fs = require('fs');
const path = require('path');
const { initializeTestEnvironment, assertSucceeds, assertFails } = require('@firebase/rules-unit-testing');

const RULES = fs.readFileSync(path.join(__dirname, '..', 'firestore.rules'), 'utf8');
let env;

const baseOrder = (over = {}) => ({
  userId: 'cust1', storeId: 'own1',
  orderStatus: 'PENDING_STORE', shippingStatus: 'NOT_REQUESTED',
  paymentMethod: 'COD', paymentStatus: 'COD_PENDING',
  subtotal: 100000, discountAmount: 0, shippingFee: 10000, total: 110000,
  ...over,
});

before(async () => {
  env = await initializeTestEnvironment({
    projectId: 'demo-d2cfood',
    firestore: { host: '127.0.0.1', port: 8080, rules: RULES },
  });
});

beforeEach(async () => {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore(); // compat Firestore
    await db.collection('users').doc('cust1').set({ uid: 'cust1', roles: ['CUSTOMER'] });
    await db.collection('users').doc('cust2').set({ uid: 'cust2', roles: ['CUSTOMER'] });
    await db.collection('users').doc('own1').set({ uid: 'own1', roles: ['CUSTOMER', 'STORE_OWNER'] });
    await db.collection('stores').doc('own1').set({
      ownerId: 'own1', name: 'Demo', address: 'HN', status: 'OPEN', fallbackPerKmFee: 5000,
    });
    await db.collection('stores/own1/vouchers').doc('v1').set({
      code: 'V1', type: 'PERCENT', value: 10, minOrderAmount: 100000, maxUses: 1, usedCount: 0, active: true,
    });
    await db.collection('stores/own1/vouchers').doc('v2').set({
      code: 'V2', type: 'FIXED_AMOUNT', value: 20000, maxUses: 1, usedCount: 1, active: true,
    });
    const orders = {
      pending:  baseOrder(),
      prep:     baseOrder({ orderStatus: 'PREPARING', paymentMethod: 'PREPAID_MOCK', paymentStatus: 'PAID' }),
      ready:    baseOrder({ orderStatus: 'READY', paymentMethod: 'PREPAID_MOCK', paymentStatus: 'PAID' }),
      delivering: baseOrder({ orderStatus: 'READY', shippingStatus: 'DELIVERING', paymentMethod: 'PREPAID_MOCK', paymentStatus: 'PAID' }),
      failed:   baseOrder({ orderStatus: 'READY', shippingStatus: 'FAILED', paymentMethod: 'PREPAID_MOCK', paymentStatus: 'PAID' }),
      rejectedPaid: baseOrder({ orderStatus: 'REJECTED', rejectReason: 'Hết chả cá', paymentMethod: 'PREPAID_MOCK', paymentStatus: 'PAID' }),
      rejectedCod:  baseOrder({ orderStatus: 'REJECTED', rejectReason: 'Hết chả cá' }),
      completed:  baseOrder({ orderStatus: 'COMPLETED', shippingStatus: 'DELIVERED' }),
    };
    for (const [id, order] of Object.entries(orders)) {
      await db.collection('orders').doc(id).set(order);
    }
  });
});

after(async () => env && env.cleanup());

describe('§7.4.1 Authentication & Authorization', () => {
  it('customer reads own profile', async () => {
    const db = env.authenticatedContext('cust1').firestore();
    await assertSucceeds(db.collection('users').doc('cust1').get());
  });

  it('customer cannot self-promote to STORE_OWNER', async () => {
    const db = env.authenticatedContext('cust1').firestore();
    await assertFails(db.collection('users').doc('cust1').update({ roles: ['CUSTOMER', 'STORE_OWNER'] }));
  });

  it('customer cannot create a store', async () => {
    const db = env.authenticatedContext('cust1').firestore();
    await assertFails(db.collection('stores').doc('cust1').set({ ownerId: 'cust1', name: 'Quán tôi', address: 'HN' }));
  });

  it('admin-seeded STORE_OWNER re-creates store with ID = UID', async () => {
    const db = env.authenticatedContext('own1').firestore();
    await assertSucceeds(db.collection('stores').doc('own1').set({
      ownerId: 'own1', name: 'Demo', address: 'HN', status: 'OPEN', fallbackPerKmFee: 5000,
    }));
  });

  it('owner cannot change ownerId', async () => {
    const db = env.authenticatedContext('own1').firestore();
    await assertFails(db.collection('stores').doc('own1').update({ ownerId: 'cust2' }));
  });
});

describe('§7.4.2 Order & Security Rules', () => {
  it('customer creates order + line in one transaction', async () => {
    const db = env.authenticatedContext('cust1').firestore();
    await assertSucceeds(db.runTransaction(async (tx) => {
      tx.set(db.collection('orders').doc('o-tx'), baseOrder());
      tx.set(db.collection('orders/o-tx/lines').doc('l1'), { orderId: 'o-tx', itemName: 'Bún chả cá', quantity: 1, linePrice: 45000 });
    }));
  });

  it('customer cannot read another customer\'s order', async () => {
    const db = env.authenticatedContext('cust2').firestore();
    await assertFails(db.collection('orders').doc('pending').get());
  });

  it('store owner can read their store\'s orders', async () => {
    const db = env.authenticatedContext('own1').firestore();
    await assertSucceeds(db.collection('orders').doc('pending').get());
  });

  it('store cannot modify money fields after create', async () => {
    const db = env.authenticatedContext('own1').firestore();
    await assertFails(db.collection('orders').doc('pending').update({ total: 1 }));
  });

  it('rejects invalid payment pair COD + PAID', async () => {
    const db = env.authenticatedContext('cust1').firestore();
    await assertFails(db.collection('orders').doc('o-bad1').set(baseOrder({ paymentStatus: 'PAID' })));
  });

  it('rejects invalid payment pair PREPAID_MOCK + COD_PENDING', async () => {
    const db = env.authenticatedContext('cust1').firestore();
    await assertFails(db.collection('orders').doc('o-bad2').set(baseOrder({ paymentMethod: 'PREPAID_MOCK', paymentStatus: 'COD_PENDING' })));
  });

  it('customer cannot create a line under someone else\'s order', async () => {
    const db = env.authenticatedContext('cust2').firestore();
    await assertFails(db.collection('orders/pending/lines').doc('x').set({ orderId: 'pending', linePrice: 10 }));
  });
});

describe('§7.4.3 Voucher & Concurrency', () => {
  it('valid voucher redemption (order + redemption + counter in one transaction)', async () => {
    const db = env.authenticatedContext('cust1').firestore();
    await assertSucceeds(db.runTransaction(async (tx) => {
      tx.set(db.collection('orders').doc('o-v1'), baseOrder({ voucherId: 'v1', discountAmount: 10000, total: 100000 }));
      tx.set(db.collection('stores/own1/vouchers/v1/redemptions').doc('o-v1'), {
        userId: 'cust1', voucherId: 'v1', orderId: 'o-v1', storeId: 'own1',
      });
      tx.update(db.collection('stores/own1/vouchers').doc('v1'), { usedCount: 1, lastRedemptionOrderId: 'o-v1' });
    }));
  });

  it('redemption doc id must equal its orderId', async () => {
    const db = env.authenticatedContext('cust1').firestore();
    await assertFails(
      db.collection('stores/own1/vouchers/v1/redemptions').doc('o-mismatch').set({
        userId: 'cust1', voucherId: 'v1', orderId: 'o-other', storeId: 'own1',
      }),
    );
  });

  it('voucher exhausted (usedCount == maxUses) rejects a second customer', async () => {
    const db = env.authenticatedContext('cust2').firestore();
    await assertFails(
      db.collection('stores/own1/vouchers').doc('v2').update({ usedCount: 2, lastRedemptionOrderId: 'o-x' }),
    );
  });

  it('owner cannot hand-edit usedCount', async () => {
    const db = env.authenticatedContext('own1').firestore();
    await assertFails(db.collection('stores/own1/vouchers').doc('v1').update({ usedCount: 99 }));
  });
});

describe('§7.4.4 Order State & Payment', () => {
  const own = () => env.authenticatedContext('own1').firestore();

  it('PENDING_STORE -> PREPARING (owner confirms)', async () => {
    await assertSucceeds(own().collection('orders').doc('pending').update({ orderStatus: 'PREPARING' }));
  });

  it('PREPARING -> READY (kitchen done)', async () => {
    await assertSucceeds(own().collection('orders').doc('prep').update({ orderStatus: 'READY' }));
  });

  it('out-of-sequence PENDING_STORE -> READY is rejected', async () => {
    await assertFails(own().collection('orders').doc('pending').update({ orderStatus: 'READY' }));
  });

  it('DELIVERING -> DELIVERED + READY -> COMPLETED in one write', async () => {
    await assertSucceeds(own().collection('orders').doc('delivering').update({
      shippingStatus: 'DELIVERED', orderStatus: 'COMPLETED',
    }));
  });

  it('SEARCHING_DRIVER -> FAILED -> SEARCHING_DRIVER (retry)', async () => {
    const db = own();
    await assertSucceeds(db.collection('orders').doc('ready').update({ shippingStatus: 'SEARCHING_DRIVER' }));
    await assertSucceeds(db.collection('orders').doc('ready').update({ shippingStatus: 'FAILED' }));
    await assertSucceeds(db.collection('orders').doc('ready').update({ shippingStatus: 'SEARCHING_DRIVER' }));
  });

  it('FAILED + READY -> REJECTED + NOT_REQUESTED (give up, reject order)', async () => {
    await assertSucceeds(own().collection('orders').doc('failed').update({
      orderStatus: 'REJECTED', shippingStatus: 'NOT_REQUESTED', rejectReason: 'Không có tài xế',
    }));
  });

  it('PAID -> REFUNDED only for rejected PREPAID_MOCK order', async () => {
    await assertSucceeds(own().collection('orders').doc('rejectedPaid').update({ paymentStatus: 'REFUNDED' }));
  });

  it('COD rejected order cannot be refunded', async () => {
    await assertFails(own().collection('orders').doc('rejectedCod').update({ paymentStatus: 'REFUNDED' }));
  });

  it('customer cannot drive order state', async () => {
    const cust = env.authenticatedContext('cust1').firestore();
    await assertFails(cust.collection('orders').doc('pending').update({ orderStatus: 'PREPARING' }));
  });
});

describe('Ratings (P1-4)', () => {
  it('completed-order owner creates exactly one rating', async () => {
    const db = env.authenticatedContext('cust1').firestore();
    await assertSucceeds(db.collection('ratings').doc('completed').set({
      userId: 'cust1', stars: 5, comment: 'Ngon, giao nhanh',
    }));
    // second create for the same orderId must fail (doc already exists)
    await assertFails(db.collection('ratings').doc('completed').set({ userId: 'cust1', stars: 4 }));
  });

  it('non-owner cannot rate the order', async () => {
    const db = env.authenticatedContext('cust2').firestore();
    await assertFails(db.collection('ratings').doc('completed').set({ userId: 'cust2', stars: 5 }));
  });

  it('cannot rate a non-completed order', async () => {
    const db = env.authenticatedContext('cust1').firestore();
    await assertFails(db.collection('ratings').doc('pending').set({ userId: 'cust1', stars: 5 }));
  });

  it('stars out of 1..5 range are rejected', async () => {
    const db = env.authenticatedContext('cust1').firestore();
    await assertFails(db.collection('ratings').doc('completed').set({ userId: 'cust1', stars: 9 }));
  });
});
