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

export function closureFactoryExample() {
  function makeCounter(start = 0) {
    let count = start;
    return {
      next() { count += 1; return count; },
      value() { return count; }
    };
  }
  const first = makeCounter(0);
  const second = makeCounter(10);
  return [first.next(), first.next(), second.next(), first.value()];
}

export function thisBindingExample() {
  const account = {
    owner: 'Ada',
    readOwner() { return this?.owner; },
    makeArrow() { return () => this.owner; }
  };
  const detached = account.readOwner;
  const arrow = account.makeArrow();
  return [
    account.readOwner(),
    detached(),
    detached.call({owner: 'Lin'}),
    arrow.call({owner: 'Ignored'})
  ];
}

export function explicitBindingExample() {
  function format(prefix, suffix) {
    return `${prefix}${this.name}${suffix}`;
  }
  const user = {name: 'Sam'};
  const greetSam = format.bind(user, 'Hello ');
  return [
    format.call(user, '[', ']'),
    format.apply(user, ['<', '>']),
    greetSam('!')
  ];
}

export function prototypeLookupExample() {
  const personMethods = {
    describe() { return `person:${this.name}`; }
  };
  const employeeMethods = Object.create(personMethods);
  employeeMethods.describe = function describeEmployee() {
    return `employee:${this.name}`;
  };
  const employee = Object.create(employeeMethods);
  employee.name = 'Mia';
  const shadowed = employee.describe();
  delete employeeMethods.describe;
  return [shadowed, employee.describe(), Object.hasOwn(employee, 'describe')];
}

export function classPrototypeExample() {
  class Person {
    constructor(name) { this.name = name; }
    describe() { return `person:${this.name}`; }
  }
  class Employee extends Person {
    describe() { return `employee:${this.name}`; }
  }
  const employee = new Employee('Mia');
  return [
    employee.describe(),
    Object.getPrototypeOf(employee) === Employee.prototype,
    Object.getPrototypeOf(Employee.prototype) === Person.prototype,
    Object.hasOwn(employee, 'describe')
  ];
}

export function functionFormsExample() {
  function declared(x) { return x * 2; }
  const expressed = function square(x) { return x * x; };
  const doubled = [1, 2, 3].map(x => x * 2);
  let arrowConstructError;
  try {
    const Arrow = () => {};
    new Arrow();
  } catch (error) {
    arrowConstructError = error.name;
  }
  return [declared(2), expressed(3), doubled, arrowConstructError];
}

export function curryPartialExample() {
  const curryPrice = taxRate => discount => amount =>
    amount * (1 - discount) * (1 + taxRate);
  function price(taxRate, discount, amount) {
    return amount * (1 - discount) * (1 + taxRate);
  }
  const georgiaPrice = curryPrice(0.08);
  const salePrice = price.bind(null, 0.08, 0.10);
  return [georgiaPrice(0.10)(100), salePrice(100)];
}

export function memoizationExample() {
  function memoizeUnary(fn, {maxEntries = 2} = {}) {
    const cache = new Map();
    return function memoized(input) {
      if (cache.has(input)) {
        const value = cache.get(input);
        cache.delete(input);
        cache.set(input, value);
        return value;
      }
      const value = fn(input);
      cache.set(input, value);
      if (cache.size > maxEntries) cache.delete(cache.keys().next().value);
      return value;
    };
  }
  let computations = 0;
  const square = memoizeUnary(x => { computations += 1; return x * x; });
  const zero = memoizeUnary(() => { computations += 1; return 0; });
  const results = [square(4), square(4), square(5), square(6), square(4)];
  const zeroResults = [zero('key'), zero('key')];
  return {results, zeroResults, computations};
}

export async function eventLoopOrderingExample() {
  const output = [];
  return new Promise((resolve) => {
    output.push('A');
    setTimeout(() => {
      output.push('timer');
      resolve(output);
    }, 0);
    Promise.resolve().then(() => {
      output.push('promise-1');
      queueMicrotask(() => output.push('nested'));
    });
    queueMicrotask(() => output.push('microtask-1'));
    output.push('B');
  });
}

export async function asyncAwaitConcurrencyExample() {
  const started = [];
  let resolveUser;
  let resolveOrders;
  const userPromise = new Promise(resolve => { resolveUser = resolve; });
  const ordersPromise = new Promise(resolve => { resolveOrders = resolve; });

  async function loadDashboard(loadUser, loadOrders) {
    const pendingUser = loadUser();
    const pendingOrders = loadOrders();
    const [user, orders] = await Promise.all([pendingUser, pendingOrders]);
    return {user, orders};
  }

  const pendingDashboard = loadDashboard(
    () => { started.push('user'); return userPromise; },
    () => { started.push('orders'); return ordersPromise; }
  );
  assert.deepEqual(started, ['user', 'orders']);
  resolveOrders([{id: 'O1'}]);
  resolveUser({id: 'U1'});
  return {started, dashboard: await pendingDashboard};
}

export async function promiseAndErrorBoundaryExample() {
  const all = await Promise.all([Promise.resolve('a'), Promise.resolve('b')]);
  const settled = await Promise.allSettled([
    Promise.resolve('east'),
    Promise.reject(new Error('west unavailable'))
  ]);
  const race = await Promise.race([Promise.resolve('first'), Promise.resolve('second')]);
  const any = await Promise.any([
    Promise.reject(new Error('east unavailable')),
    Promise.resolve('west')
  ]);

  async function loadUser(fetchImpl, id) {
    try {
      const response = await fetchImpl(`/api/users/${encodeURIComponent(id)}`);
      if (!response.ok) throw new Error(`HTTP ${response.status}`);
      return await response.json();
    } catch (error) {
      throw new Error(`Could not load user ${id}`, {cause: error});
    }
  }

  const user = await loadUser(async () => ({
    ok: true,
    status: 200,
    json: async () => ({id: 'U1'})
  }), 'U1');

  let failure;
  try {
    await loadUser(async () => ({
      ok: false,
      status: 503,
      json: async () => ({})
    }), 'U2');
  } catch (error) {
    failure = error;
  }

  return {
    all,
    settledStatuses: settled.map(result => result.status),
    race,
    any,
    user,
    failureMessage: failure.message,
    causeMessage: failure.cause.message
  };
}

function createFakeScheduler() {
  let time = 0;
  let nextId = 1;
  const timers = new Map();
  return {
    now: () => time,
    setTimeout(callback, delay) {
      const id = nextId++;
      timers.set(id, {callback, due: time + delay, id});
      return id;
    },
    clearTimeout(id) {
      timers.delete(id);
    },
    tick(milliseconds) {
      const target = time + milliseconds;
      while (true) {
        const next = [...timers.values()]
          .filter(timer => timer.due <= target)
          .sort((a, b) => a.due - b.due || a.id - b.id)[0];
        if (!next) break;
        time = next.due;
        timers.delete(next.id);
        next.callback();
      }
      time = target;
    }
  };
}

export function rateLimitingExample() {
  function debounce(fn, wait, scheduler) {
    let timerId;
    return function debounced(...args) {
      const receiver = this;
      scheduler.clearTimeout(timerId);
      timerId = scheduler.setTimeout(() => {
        timerId = undefined;
        fn.apply(receiver, args);
      }, wait);
    };
  }

  function throttle(fn, wait, scheduler) {
    let lastInvoke = -Infinity;
    let timerId;
    let pending;
    return function throttled(...args) {
      const now = scheduler.now();
      pending = {receiver: this, args};
      const remaining = wait - (now - lastInvoke);
      const invoke = () => {
        lastInvoke = scheduler.now();
        timerId = undefined;
        const call = pending;
        pending = undefined;
        fn.apply(call.receiver, call.args);
      };
      if (remaining <= 0) {
        if (timerId !== undefined) scheduler.clearTimeout(timerId);
        invoke();
      } else if (timerId === undefined) {
        timerId = scheduler.setTimeout(invoke, remaining);
      }
    };
  }

  const debounceScheduler = createFakeScheduler();
  const debounceCalls = [];
  const search = {
    label: 'search',
    run: debounce(function run(value) {
      debounceCalls.push(`${this.label}:${value}@${debounceScheduler.now()}`);
    }, 100, debounceScheduler)
  };
  search.run('a');
  debounceScheduler.tick(40);
  search.run('ab');
  debounceScheduler.tick(50);
  search.run('abc');
  debounceScheduler.tick(100);

  const throttleScheduler = createFakeScheduler();
  const throttleCalls = [];
  const viewport = {
    label: 'scroll',
    run: throttle(function run(value) {
      throttleCalls.push(`${this.label}:${value}@${throttleScheduler.now()}`);
    }, 100, throttleScheduler)
  };
  viewport.run('a');
  throttleScheduler.tick(40);
  viewport.run('ab');
  throttleScheduler.tick(50);
  viewport.run('abc');
  throttleScheduler.tick(10);
  throttleScheduler.tick(10);
  viewport.run('d');
  throttleScheduler.tick(90);

  return {debounceCalls, throttleCalls};
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
assert.deepEqual(closureFactoryExample(), [1, 2, 11, 2]);
assert.deepEqual(thisBindingExample(), ['Ada', undefined, 'Lin', 'Ada']);
assert.deepEqual(explicitBindingExample(), ['[Sam]', '<Sam>', 'Hello Sam!']);
assert.deepEqual(prototypeLookupExample(), ['employee:Mia', 'person:Mia', false]);
assert.deepEqual(classPrototypeExample(), ['employee:Mia', true, true, false]);
assert.deepEqual(functionFormsExample(), [4, 9, [2, 4, 6], 'TypeError']);
assert.deepEqual(curryPartialExample(), [97.2, 97.2]);
assert.deepEqual(memoizationExample(), {
  results: [16, 16, 25, 36, 16],
  zeroResults: [0, 0],
  computations: 5
});
assert.deepEqual(await eventLoopOrderingExample(), [
  'A', 'B', 'promise-1', 'microtask-1', 'nested', 'timer'
]);
assert.deepEqual(await asyncAwaitConcurrencyExample(), {
  started: ['user', 'orders'],
  dashboard: {user: {id: 'U1'}, orders: [{id: 'O1'}]}
});
assert.deepEqual(await promiseAndErrorBoundaryExample(), {
  all: ['a', 'b'],
  settledStatuses: ['fulfilled', 'rejected'],
  race: 'first',
  any: 'west',
  user: {id: 'U1'},
  failureMessage: 'Could not load user U2',
  causeMessage: 'HTTP 503'
});
assert.deepEqual(rateLimitingExample(), {
  debounceCalls: ['search:abc@190'],
  throttleCalls: ['scroll:a@0', 'scroll:abc@100', 'scroll:d@200']
});

console.log('JavaScript fundamentals examples: 23 scenario groups passed');
