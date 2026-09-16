// Synthetic tooling integration only: ephemeral loopback port, fake JWTs, in-memory Orders.
// This does not run the Flash Sale application, Redis, PostgreSQL, Kafka, Stripe or EKS.
import test from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';
import { spawn } from 'node:child_process';
import { mkdir, mkdtemp, writeFile, readFile, readdir, rm } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import { once } from 'node:events';

const repo = fileURLToPath(new URL('../../../', import.meta.url));
const runner = path.join(repo, 'infra/scripts/load/run-flash-sale-to-order-stress.ps1');
const output = path.join(repo, 'load-tests/flash-sale-to-order-stress/results');
const campaignId = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa';
const variantId = 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb';

function run(args) {
  return new Promise((resolve, reject) => {
    const child = spawn('pwsh', ['-NoLogo', '-NoProfile', '-File', runner,
      '-CampaignId', campaignId, '-VariantId', variantId, ...args], {
      cwd: repo, windowsHide: true, env: { ...process.env, K6_HTTP_DEBUG: 'full' },
    });
    let text = '';
    child.stdout.on('data', chunk => { text += chunk; });
    child.stderr.on('data', chunk => { text += chunk; });
    const timer = setTimeout(() => { child.kill(); reject(new Error('fixture runner exceeded 60s')); }, 60000);
    child.on('error', error => { clearTimeout(timer); reject(error); });
    child.on('close', code => { clearTimeout(timer); resolve({ code, text }); });
  });
}

async function fakeGateway(behavior = 'success', allocation = 100) {
  const owners = new Map();
  let admissions = 0, reads = 0, replays = 0;
  const server = http.createServer(async (req, res) => {
    const token = req.headers.authorization?.replace('Bearer ', '');
    const subject = token ? JSON.parse(Buffer.from(token.split('.')[1], 'base64url')).sub : 'anonymous';
    const reply = (status, data) => {
      res.writeHead(status, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify(data));
    };
    const ok = data => reply(200, { success: true, data });
    if (req.method === 'POST' && req.url === `/api/v1/flash-sales/${campaignId}/reservations`) {
      admissions++;
      let raw = ''; for await (const chunk of req) raw += chunk;
      const request = JSON.parse(raw);
      if (request.variantId !== variantId || request.quantity !== 1 || !req.headers['idempotency-key']) {
        return reply(400, { errorCode: 'BAD_TEST_PAYLOAD' });
      }
      if (behavior === 'pending') return reply(503, { errorCode: 'FLASH_SALE_ACCEPTANCE_PENDING' });
      if (!owners.has(subject) && owners.size >= allocation) return reply(409, { errorCode: 'FLASH_SALE_SOLD_OUT' });
      if (owners.has(subject)) replays++;
      else owners.set(subject, { id: `order-${subject}`, purchaseRequestId: `purchase-${subject}`,
        reservationId: `reservation-${subject}`, campaignId, variantId, quantity: 1 });
      return reply(202, { success: true, data: { ...owners.get(subject), status: 'RESERVED',
        expiresAt: new Date(Date.now() + (behavior === 'expiring' ? 500 : 7200000)).toISOString() } });
    }
    if (req.method === 'GET' && req.url === '/api/v1/orders?page=0&size=2') {
      reads++;
      const order = behavior === 'missing' ? null : owners.get(subject);
      const n = order ? (behavior === 'duplicate' ? 2 : 1) : 0;
      return ok({ data: order ? [{ id: order.id }] : [],
        page: { number: 0, size: 2, totalElements: n, totalPages: n ? 1 : 0, hasNext: false } });
    }
    if (req.method === 'GET' && req.url.startsWith('/api/v1/orders/')) {
      reads++;
      const order = owners.get(subject);
      if (!order || req.url !== `/api/v1/orders/${order.id}`) return reply(404, {});
      return ok({ ...order, campaignId: behavior === 'mismatch' ? 'wrong' : campaignId,
        purchaseSource: 'FLASH_SALE', status: 'PENDING_PAYMENT', items: [{ variantId, quantity: 1 }] });
    }
    return reply(404, {});
  });
  server.listen(0, '127.0.0.1'); await once(server, 'listening');
  return { url: `http://127.0.0.1:${server.address().port}`, server,
    stats: () => ({ admissions, reads, replays, orders: owners.size }) };
}

test('offline plan requires no token file, k6 process, HTTP request or output directory', async () => {
  const gateway = await fakeGateway();
  try {
    const result = await run(['-ExpectedAllocation', '100', '-GatewayBaseUrl', gateway.url,
      '-ShopperTokenFile', 'does-not-exist.json']);
    assert.equal(result.code, 0, result.text);
    assert.match(result.text, /VALIDATION_ONLY=PASS/);
    assert.deepEqual(gateway.stats(), { admissions: 0, reads: 0, replays: 0, orders: 0 });
  } finally { gateway.server.close(); }
});

test('invalid rates, insufficient allocation and remote opt-in fail before traffic', async () => {
  for (const args of [['-RatesCsv', '5,1'], ['-ExpectedAllocation', '1'],
    ['-GatewayBaseUrl', 'https://example.com'], ['-GatewayBaseUrl', 'http://example.com', '-AllowRemote']]) {
    const all = args[0] === '-ExpectedAllocation' ? args : ['-ExpectedAllocation', '100', ...args];
    assert.notEqual((await run(all)).code, 0);
  }
});

for (const [scenario, behavior, shouldPass, allocation] of [
  ['NewOrders', 'success', true, 100], ['Replay', 'success', true, 100],
  ['SoldOut', 'success', true, 1], ['NewOrders', 'mismatch', false, 100],
  ['NewOrders', 'duplicate', false, 100], ['NewOrders', 'missing', false, 100],
  ['NewOrders', 'pending', false, 100],
  ['NewOrders', 'expiring', false, 100],
  ['NewOrders', 'pending-ladder', false, 100],
  ['NewOrders', 'success-ladder', true, 100],
]) {
  test(`real k6 against synthetic Gateway: ${scenario}/${behavior} must ${shouldPass ? 'pass' : 'fail'}`, async () => {
    await mkdir(output, { recursive: true });
    const temp = await mkdtemp(path.join(output, 'tooling-test-'));
    const tokenFile = path.join(temp, 'tokens.json');
    const tokens = Array.from({ length: 15 }, (_, i) => {
      const claims = { sub: `00000000-0000-4000-8000-${String(i + 1).padStart(12, '0')}`,
        exp: Math.floor(Date.now() / 1000) + 7200 };
      return `${Buffer.from('{"alg":"none"}').toString('base64url')}.${Buffer.from(JSON.stringify(claims)).toString('base64url')}.fake-signature`;
    });
    await writeFile(tokenFile, JSON.stringify(tokens));
    const gateway = await fakeGateway(behavior.replace('-ladder', ''), allocation);
    const before = new Set(await readdir(output));
    try {
      const result = await run(['-ExpectedAllocation', String(allocation), '-Scenario', scenario,
        '-RatesCsv', behavior.endsWith('-ladder') ? '2,3' : '2', '-StageSeconds', '2', '-WarmupSeconds', '0', '-CooldownSeconds', '0',
        '-OrderTimeoutSeconds', '2', '-PollIntervalMs', '100', '-RequestTimeoutSeconds', '1',
        '-VirtualUsers', '10', '-TimeoutSeconds', '120', '-AdmissionP95LimitMs', '5000',
        '-AdmissionP99LimitMs', '6000', '-OrderP95LimitMs', '2000',
        '-GatewayBaseUrl', gateway.url, '-ShopperTokenFile', tokenFile, '-Run']);
      assert.equal(result.code === 0, shouldPass, result.text);
      for (const token of tokens) assert.ok(!result.text.includes(token));
      const created = (await readdir(output)).filter(dir => !before.has(dir));
      assert.equal(created.length, 1, result.text);
      const report = JSON.parse(await readFile(path.join(output, created[0], 'report.json'), 'utf8'));
      assert.equal(report.operatorTokenFilePreserved, true);
      assert.equal(report.stages.length, behavior === 'success-ladder' ? 2 : 1, result.text);
      const actual = gateway.stats();
      if (shouldPass) {
        const sum = name => report.stages.reduce((n, item) => n + item[name], 0);
        assert.equal(sum('observedOrders'), actual.orders);
        assert.equal(sum('firstReservationRequests') + sum('replayRequests'), actual.admissions);
        assert.equal(sum('observerReadRequests'), actual.reads);
        assert.equal(sum('verifiedReplays'), actual.replays);
        assert.equal(report.outcome, 'passed_tested_rates_not_capacity_limit');
      } else assert.notEqual(report.outcome, 'passed_tested_rates_not_capacity_limit');
      for (const token of tokens) assert.ok(!JSON.stringify(report).includes(token));
      assert.equal(await readFile(tokenFile, 'utf8'), JSON.stringify(tokens));
    } finally {
      gateway.server.close();
      // Only remove the exact mkdtemp-created fake credential directory; preserve sanitized reports.
      await rm(temp, { recursive: true });
    }
  });
}
