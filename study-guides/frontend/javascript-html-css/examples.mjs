import assert from 'node:assert/strict';

export function constantBindingExample() {
  const profile = {name: 'Ada', skills: ['JS']};
  profile.name = 'Grace';
  profile.skills.push('HTML');
  return profile;
}

export function hoistingExample() {
  return (() => {
    const beforeInitialization = total;
    var total = 7;
    return [beforeInitialization, total];
  })();
}

export function temporalDeadZoneErrorName() {
  try {
    return status;
  } catch (error) {
    return error.name;
  }
  let status = 'done';
}

export function lexicalScopeExample() {
  const label = 'global';
  function makeReader() {
    const label = 'outer';
    return () => label;
  }
  function invokeWithAnotherLabel(fn) {
    const label = 'caller';
    assert.equal(label, 'caller');
    return fn();
  }
  return [label, invokeWithAnotherLabel(makeReader())];
}

export async function loopCaptureExample() {
  const output = [];
  await new Promise((resolve) => {
    for (var i = 0; i < 3; i += 1) {
      setTimeout(() => output.push(i), 0);
    }
    for (let j = 0; j < 3; j += 1) {
      setTimeout(() => output.push(j), 0);
    }
    setTimeout(resolve, 15);
  });
  return output;
}

export function referenceCopyExample() {
  const account = {balance: 10};
  const alias = account;
  function update(local) {
    local.balance += 5;
    local = {balance: 0};
    return local;
  }
  const replacement = update(account);
  return {account, alias, replacement};
}

export function equalityExample() {
  return [
    0 === false,
    0 == false, // Intentional: verifies the abstract-equality teaching case.
    '' == 0, // Intentional coercion example.
    null == undefined, // Intentional dedicated loose-equality case.
    null == 0,
    [5] == 5 // Intentional object-to-primitive example.
  ];
}

export function classifyMissingValue(value) {
  if (value === null) return 'explicit-null';
  if (value === undefined) return 'missing';
  if (Number.isNaN(value)) return 'invalid-number';
  return 'present';
}

export function copyBoundaryExample() {
  const original = {
    owner: {name: 'Ada'},
    tags: ['js'],
    created: new Date('2026-01-02T00:00:00Z')
  };
  const shallow = {...original};
  shallow.owner.name = 'Grace';
  const isolated = structuredClone(original);
  isolated.owner.name = 'Lin';
  isolated.tags.push('css');
  return {original, shallow, isolated};
}

export function arrayTransformExample() {
  const orders = [
    {id: 'A', paid: true, cents: 1250},
    {id: 'B', paid: false, cents: 900},
    {id: 'C', paid: true, cents: 750}
  ];
  const paidIds = orders.filter(order => order.paid).map(order => order.id);
  const paidCents = orders.reduce(
    (sum, order) => order.paid ? sum + order.cents : sum,
    0
  );
  return {paidIds, paidCents};
}

export function nullSafeOptionsExample(config) {
  return {
    retries: config?.network?.retries ?? 3,
    label: config?.ui?.label ?? 'default',
    onReadyResult: config?.hooks?.onReady?.() ?? 'not-run'
  };
}

assert.deepEqual(constantBindingExample(), {name: 'Grace', skills: ['JS', 'HTML']});
assert.deepEqual(hoistingExample(), [undefined, 7]);
assert.equal(temporalDeadZoneErrorName(), 'ReferenceError');
assert.deepEqual(lexicalScopeExample(), ['global', 'outer']);
assert.deepEqual(await loopCaptureExample(), [3, 3, 3, 0, 1, 2]);

const references = referenceCopyExample();
assert.equal(references.account, references.alias);
assert.notEqual(references.account, references.replacement);
assert.deepEqual(references.account, {balance: 15});
assert.deepEqual(references.replacement, {balance: 0});
assert.deepEqual(equalityExample(), [false, true, true, true, false, true]);
assert.deepEqual(
  [classifyMissingValue(null), classifyMissingValue(undefined), classifyMissingValue(Number('oops')), classifyMissingValue(0)],
  ['explicit-null', 'missing', 'invalid-number', 'present']
);

const copies = copyBoundaryExample();
assert.equal(copies.original.owner, copies.shallow.owner);
assert.notEqual(copies.original.owner, copies.isolated.owner);
assert.deepEqual(copies.original.tags, ['js']);
assert.deepEqual(copies.isolated.tags, ['js', 'css']);
assert.equal(copies.isolated.created instanceof Date, true);
assert.deepEqual(arrayTransformExample(), {paidIds: ['A', 'C'], paidCents: 2000});
assert.deepEqual(nullSafeOptionsExample({
  network: {retries: 0},
  ui: {label: ''},
  hooks: {onReady: () => false}
}), {retries: 0, label: '', onReadyResult: false});
assert.deepEqual(nullSafeOptionsExample(null), {
  retries: 3,
  label: 'default',
  onReadyResult: 'not-run'
});

console.log('JavaScript fundamentals examples: 11 scenario groups passed');
