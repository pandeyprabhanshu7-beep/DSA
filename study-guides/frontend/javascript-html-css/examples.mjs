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

export async function latestRequestCancellationExample() {
  const pending = new Map();
  const events = [];

  function fakeFetch(url, {signal}) {
    return new Promise((resolve, reject) => {
      const query = new URL(url, 'https://example.test').searchParams.get('q');
      const abort = () => {
        const error = new Error('superseded');
        error.name = 'AbortError';
        events.push(`abort:${query}`);
        reject(error);
      };
      if (signal.aborted) return abort();
      signal.addEventListener('abort', abort, {once: true});
      pending.set(query, value => {
        signal.removeEventListener('abort', abort);
        events.push(`resolve:${query}`);
        resolve({ok: true, status: 200, json: async () => value});
      });
    });
  }

  function createLatestSearch(fetchImpl) {
    let active;
    let generation = 0;
    return async function search(query) {
      active?.abort();
      const controller = new AbortController();
      const mine = ++generation;
      active = controller;
      try {
        const response = await fetchImpl(
          `/api/search?q=${encodeURIComponent(query)}`,
          {signal: controller.signal}
        );
        if (!response.ok) throw new Error(`HTTP ${response.status}`);
        const data = await response.json();
        return mine === generation ? data : undefined;
      } catch (error) {
        if (error.name === 'AbortError') return undefined;
        throw error;
      } finally {
        if (mine === generation) active = undefined;
      }
    };
  }

  const search = createLatestSearch(fakeFetch);
  const first = search('cat');
  const second = search('caterpillar');
  pending.get('caterpillar')(['caterpillar']);

  return {events, results: [await first, await second]};
}

export function delegatedActionExample() {
  function node(name, parent = null, attributes = {}) {
    return {name, parent, attributes};
  }
  function closest(start, attribute) {
    for (let cursor = start; cursor; cursor = cursor.parent) {
      if (Object.hasOwn(cursor.attributes, attribute)) return cursor;
    }
    return null;
  }
  function contains(boundary, candidate) {
    for (let cursor = candidate; cursor; cursor = cursor.parent) {
      if (cursor === boundary) return true;
    }
    return false;
  }
  function delegatedDelete(boundary, target) {
    const button = closest(target, 'data-delete');
    if (!button || !contains(boundary, button)) return undefined;
    const item = closest(button, 'data-item-id');
    return item && contains(boundary, item) ? item.attributes['data-item-id'] : undefined;
  }

  const documentNode = node('document');
  const list = node('list', documentNode);
  const item = node('item', list, {'data-item-id': 'A'});
  const button = node('button', item, {'data-delete': ''});
  const icon = node('icon', button);
  const outsideItem = node('outside-item', documentNode, {'data-item-id': 'B'});
  const outsideButton = node('outside-button', outsideItem, {'data-delete': ''});

  return [delegatedDelete(list, icon), delegatedDelete(list, outsideButton)];
}

export function renderingAndStoragePolicyExample() {
  function forcedLayoutsFor(operations) {
    let geometryDirty = false;
    let forcedLayouts = 0;
    for (const operation of operations) {
      if (operation === 'write') geometryDirty = true;
      if (operation === 'read' && geometryDirty) {
        forcedLayouts += 1;
        geometryDirty = false;
      }
    }
    return forcedLayouts;
  }

  const interleaved = ['write', 'read', 'write', 'read', 'write', 'read', 'write', 'read'];
  const batched = ['read', 'write', 'write', 'write', 'write'];
  const storagePolicy = {
    theme: 'localStorage',
    checkoutDraftForThisTab: 'sessionStorage',
    serverSessionIdentifier: 'Secure HttpOnly SameSite cookie',
    offlineCatalog: 'IndexedDB'
  };
  return {
    forcedLayouts: {
      interleaved: forcedLayoutsFor(interleaved),
      batched: forcedLayoutsFor(batched)
    },
    storagePolicy
  };
}

export function corsAndSecurityPolicyExample() {
  function corsVisibility({mode = 'cors', credentials = false, requestOrigin, allowOrigin, allowCredentials = false}) {
    if (mode === 'no-cors') return 'opaque';
    if (allowOrigin !== requestOrigin && allowOrigin !== '*') return 'blocked';
    if (credentials && (allowOrigin === '*' || !allowCredentials)) return 'blocked';
    return 'readable';
  }

  const visibility = [
    corsVisibility({requestOrigin: 'https://app.example', allowOrigin: 'https://app.example'}),
    corsVisibility({credentials: true, requestOrigin: 'https://app.example', allowOrigin: '*', allowCredentials: true}),
    corsVisibility({credentials: true, requestOrigin: 'https://app.example', allowOrigin: 'https://app.example', allowCredentials: true}),
    corsVisibility({mode: 'no-cors', requestOrigin: 'https://app.example'})
  ];
  const controls = {
    xss: ['contextual output encoding', 'safe sinks', 'sanitization for allowed HTML', 'CSP defense-in-depth'],
    csrf: ['anti-CSRF token', 'SameSite cookie', 'Origin or Fetch Metadata validation', 'authorization']
  };
  return {visibility, controls, corsIsCsrfDefense: false};
}

export function eventActionPolicyExample() {
  function decide({kind, cancelable = true, passive = false, modified = false, sameOrigin = true}) {
    if (kind === 'link' && (!sameOrigin || modified)) {
      return {preventDefault: false, stopPropagation: false, action: 'browser-navigation'};
    }
    if (kind === 'link') {
      return {
        preventDefault: cancelable && !passive,
        stopPropagation: false,
        action: cancelable && !passive ? 'client-router' : 'browser-navigation'
      };
    }
    if (kind === 'invalid-form') {
      return {preventDefault: cancelable && !passive, stopPropagation: false, action: 'show-errors'};
    }
    return {preventDefault: false, stopPropagation: false, action: 'none'};
  }
  return [
    decide({kind: 'link', modified: true}),
    decide({kind: 'link'}),
    decide({kind: 'invalid-form'}),
    decide({kind: 'link', passive: true})
  ];
}

export function scriptSchedulingExample() {
  // An application teaching model, not a browser implementation.
  function schedule({parseFinishedAt, scripts}) {
    const output = [];
    for (const script of scripts.filter(item => item.mode === 'async')) {
      output.push({name: script.name, at: script.readyAt});
    }
    let deferredAt = parseFinishedAt;
    for (const script of scripts.filter(item => item.mode === 'defer')) {
      deferredAt = Math.max(deferredAt, script.readyAt);
      output.push({name: script.name, at: deferredAt});
    }
    return output.sort((a, b) => a.at - b.at);
  }
  return schedule({
    parseFinishedAt: 50,
    scripts: [
      {name: 'analytics', mode: 'async', readyAt: 20},
      {name: 'ads', mode: 'async', readyAt: 35},
      {name: 'config', mode: 'defer', readyAt: 10},
      {name: 'application', mode: 'defer', readyAt: 45}
    ]
  });
}

export function accessibleFormPolicyExample() {
  function inspect(field) {
    const issues = [];
    if (!field.visibleLabel || field.labelFor !== field.id) issues.push('missing-programmatic-label');
    if (field.required && !field.requiredText) issues.push('required-state-not-visible');
    if (field.invalid && (!field.errorId || !field.describedBy.includes(field.errorId))) {
      issues.push('error-not-associated');
    }
    return {
      issues,
      ariaInvalid: field.invalid ? 'true' : undefined,
      focusTarget: field.invalid ? field.id : undefined
    };
  }
  return {
    validField: inspect({
      id: 'email', visibleLabel: true, labelFor: 'email', required: true,
      requiredText: true, invalid: true, errorId: 'email-error',
      describedBy: ['email-hint', 'email-error']
    }),
    placeholderOnly: inspect({
      id: 'name', visibleLabel: false, labelFor: '', required: true,
      requiredText: false, invalid: true, errorId: 'name-error', describedBy: []
    })
  };
}

export function responsiveImagePolicyExample() {
  function illustrativeCandidate(slotCssPixels, devicePixelRatio, candidates) {
    const target = slotCssPixels * devicePixelRatio;
    return candidates.find(width => width >= target) ?? candidates.at(-1);
  }
  return {
    expectedSourcePixels: 800,
    illustrativeCandidate: illustrativeCandidate(400, 2, [480, 800, 1600]),
    browserMakesFinalChoice: true,
    reservedAspectRatio: '16:9'
  };
}

export function visibilityPolicyExample() {
  const mechanisms = {
    hidden: {layout: false, painted: false, accessibilityTree: false, focusableDescendants: false},
    displayNone: {layout: false, painted: false, accessibilityTree: false, focusableDescendants: false},
    visibilityHidden: {layout: true, painted: false, accessibilityTree: false, focusableDescendants: false},
    ariaHidden: {layout: true, painted: true, accessibilityTree: false, focusableDescendants: 'must-not-exist'}
  };
  function disclosureState(open) {
    return {hidden: !open, ariaExpanded: String(open)};
  }
  return {mechanisms, closed: disclosureState(false), open: disclosureState(true)};
}

export function lazyLoadingPolicyExample() {
  function plan(resource) {
    if (resource.initialViewport || resource.lcpCandidate) {
      return {loading: 'eager', priority: resource.lcpCandidate ? 'high' : 'auto'};
    }
    return {loading: 'lazy', priority: 'auto'};
  }
  return {
    hero: plan({initialViewport: true, lcpCandidate: true}),
    gallery: plan({initialViewport: false, lcpCandidate: false}),
    map: plan({initialViewport: false, lcpCandidate: false}),
    invariants: ['reserve-dimensions', 'retain-alt-or-title', 'browser-controls-threshold']
  };
}

export function documentQualityPolicyExample() {
  function inspect(page) {
    const issues = [];
    if (!page.lang) issues.push('missing-language');
    if (!page.title || page.title === page.siteName) issues.push('non-descriptive-title');
    if (page.h1Count !== 1) issues.push('unclear-primary-heading');
    if (page.links.some(link => !link.href || !link.text.trim() || link.text === 'click here')) {
      issues.push('non-descriptive-or-non-crawlable-link');
    }
    if (page.images.some(image => image.meaningful && !image.alt.trim())) issues.push('missing-image-alternative');
    if (!page.structuredDataMatchesVisibleContent) issues.push('misleading-structured-data');
    return issues;
  }
  return {
    article: inspect({
      lang: 'en', title: 'Cache Stampede Guide | Acme', siteName: 'Acme', h1Count: 1,
      links: [{href: '/guides/stale-while-revalidate', text: 'stale-while-revalidate trade-offs'}],
      images: [{meaningful: true, alt: 'Concurrent misses join one refresh'}],
      structuredDataMatchesVisibleContent: true
    }),
    brokenLandingPage: inspect({
      lang: '', title: 'Acme', siteName: 'Acme', h1Count: 0,
      links: [{href: '', text: 'click here'}], images: [{meaningful: true, alt: ''}],
      structuredDataMatchesVisibleContent: false
    })
  };
}

export function cascadePolicyExample() {
  function compareSpecificity(left, right) {
    for (let index = 0; index < left.length; index += 1) {
      if (left[index] !== right[index]) return left[index] - right[index];
    }
    return 0;
  }
  function chooseNormalAuthorDeclaration(declarations) {
    return declarations.toSorted((left, right) => {
      const leftLayer = left.layerOrder ?? Number.POSITIVE_INFINITY;
      const rightLayer = right.layerOrder ?? Number.POSITIVE_INFINITY;
      if (leftLayer !== rightLayer) return leftLayer - rightLayer;
      const specificity = compareSpecificity(left.specificity, right.specificity);
      if (specificity !== 0) return specificity;
      if (left.scopeHops !== right.scopeHops) return right.scopeHops - left.scopeHops;
      return left.sourceOrder - right.sourceOrder;
    }).at(-1);
  }
  const components = [
    {value: 'navy', layerOrder: 2, specificity: [0, 1, 0], scopeHops: 4, sourceOrder: 1},
    {value: 'teal', layerOrder: 2, specificity: [0, 2, 0], scopeHops: 4, sourceOrder: 2}
  ];
  const allMatches = [
    ...components,
    {value: 'purple', layerOrder: 3, specificity: [0, 1, 0], scopeHops: 4, sourceOrder: 3},
    {value: 'darkgreen', layerOrder: null, specificity: [0, 1, 0], scopeHops: 4, sourceOrder: 4}
  ];
  return {
    sameLayerWinner: chooseNormalAuthorDeclaration(components).value,
    finalWinner: chooseNormalAuthorDeclaration(allMatches).value,
    comparisonOrder: ['layer', 'specificity', 'scope-proximity', 'source-order'],
    limitedTo: 'matching normal author style rules in one encapsulation context'
  };
}

export function boxModelPolicyExample() {
  function measure({declaredWidth, paddingInline, borderInline, marginInline, boxSizing}) {
    const borderBox = boxSizing === 'border-box'
      ? declaredWidth
      : declaredWidth + paddingInline + borderInline;
    const content = boxSizing === 'border-box'
      ? declaredWidth - paddingInline - borderInline
      : declaredWidth;
    return {content, borderBox, outer: borderBox + marginInline};
  }
  const input = {declaredWidth: 240, paddingInline: 32, borderInline: 4, marginInline: 24};
  return {
    contentBox: measure({...input, boxSizing: 'content-box'}),
    borderBox: measure({...input, boxSizing: 'border-box'})
  };
}

export function layoutSizingPolicyExample() {
  function chooseLayout({onePrimaryAxis, sharedRowsAndColumns}) {
    if (sharedRowsAndColumns) return 'grid';
    if (onePrimaryAxis) return 'flex';
    return 'normal-flow';
  }
  function ellipsisReachable({contentBasedAutoMinimum, minInlineSize}) {
    return !contentBasedAutoMinimum || minInlineSize === 0;
  }
  return {
    dashboard: chooseLayout({onePrimaryAxis: false, sharedRowsAndColumns: true}),
    toolbar: chooseLayout({onePrimaryAxis: true, sharedRowsAndColumns: false}),
    longTitleBefore: ellipsisReachable({contentBasedAutoMinimum: true, minInlineSize: 'auto'}),
    longTitleAfter: ellipsisReachable({contentBasedAutoMinimum: true, minInlineSize: 0}),
    gridTrackFloor: 'minmax(0, 1fr)'
  };
}

export function positioningAndStackingPolicyExample() {
  function containingBlock({position, positionedAncestor, transformedAncestor}) {
    if (position === 'absolute') return positionedAncestor ?? transformedAncestor ?? 'initial-containing-block';
    if (position === 'fixed') return transformedAncestor ?? 'viewport';
    return 'normal-flow-containing-block';
  }
  return {
    absoluteBadge: containingBlock({position: 'absolute', positionedAncestor: 'card'}),
    fixedInsideTransform: containingBlock({position: 'fixed', transformedAncestor: 'app-shell'}),
    sticky: {retainsFlowSpace: true, requiresInset: true, constrainedByScrollContainer: true},
    tooltipCanEscapeParentContext: false,
    topLayerParentContext: 'root-stacking-context'
  };
}

export function centeringAndUnitPolicyExample() {
  function centering({context, target}) {
    if (context === 'grid') return 'place-items:center';
    if (context === 'flex') return 'justify-content:center + align-items:center';
    if (target === 'block') return 'constrained-inline-size + margin-inline:auto';
    if (target === 'inline-content') return 'text-align:center';
    return 'define-layout-context-first';
  }
  function remToCssPixels(rem, rootFontSize) {
    return rem * rootFontSize;
  }
  function emToCssPixels(em, elementFontSize) {
    return em * elementFontSize;
  }
  return {
    grid: centering({context: 'grid', target: 'item'}),
    article: centering({context: 'normal-flow', target: 'block'}),
    title: centering({context: 'normal-flow', target: 'inline-content'}),
    twoRemAt20: remToCssPixels(2, 20),
    oneEmAt18: emToCssPixels(1, 18),
    viewportTextPolicy: 'clamp(rem, rem + vw, rem)'
  };
}

export function containerQueryPolicyExample() {
  function productCardLayout(containerInlineSizeRem) {
    return containerInlineSizeRem >= 32 ? 'image-and-details' : 'stacked';
  }
  return {
    sameViewport: 90,
    rail: productCardLayout(24),
    catalog: productCardLayout(48),
    environmentPolicy: 'media-query',
    allocationPolicy: 'container-query'
  };
}

export function customPropertyPolicyExample() {
  function chooseToken({declared, registered, initialValue, fallback}) {
    if (!declared) return fallback;
    if (registered && declared === 'invalid') return initialValue;
    return declared;
  }
  return {
    missingUsesFallback: chooseToken({declared: '', fallback: 'royalblue'}),
    unregisteredInvalidSubstitutes: chooseToken({declared: 'invalid', registered: false, fallback: '1rem'}),
    registeredInvalidUsesInitial: chooseToken({declared: 'invalid', registered: true, initialValue: 0, fallback: 1}),
    sassLifetime: 'build-time',
    customPropertyLifetime: 'cascade-and-runtime'
  };
}

export function layoutStabilityAndNamingPolicyExample() {
  function reservedAspectRatio(width, height) {
    return `${width}:${height}`;
  }
  function bem({block, element, modifier}) {
    if (element && modifier) return `${block}__${element}--${modifier}`;
    if (element) return `${block}__${element}`;
    if (modifier) return `${block}--${modifier}`;
    return block;
  }
  return {
    imageReservation: reservedAspectRatio(800, 450),
    slowImageMustShift: false,
    stableInsertionPolicy: 'reserve-space-before-render',
    block: bem({block: 'product-card'}),
    element: bem({block: 'product-card', element: 'title'}),
    modifier: bem({block: 'product-card', modifier: 'featured'}),
    bemRequired: false
  };
}

function toSafeLength(value) {
  const number = Number(value);
  if (Number.isNaN(number) || number <= 0) return 0;
  if (number === Infinity) return Number.MAX_SAFE_INTEGER;
  return Math.min(Math.floor(number), Number.MAX_SAFE_INTEGER);
}

export function mapLike(arrayLike, callback, thisArg) {
  if (arrayLike == null) throw new TypeError('mapLike receiver is null or undefined');
  if (typeof callback !== 'function') throw new TypeError('callback must be callable');
  const source = Object(arrayLike);
  const length = toSafeLength(source.length);
  const output = new Array(length);
  for (let index = 0; index < length; index += 1) {
    if (index in source) {
      output[index] = callback.call(thisArg, source[index], index, source);
    }
  }
  return output;
}

export function reduceLike(arrayLike, callback, ...initial) {
  if (arrayLike == null) throw new TypeError('reduceLike receiver is null or undefined');
  if (typeof callback !== 'function') throw new TypeError('callback must be callable');
  const source = Object(arrayLike);
  const length = toSafeLength(source.length);
  let index = 0;
  let accumulator;

  if (initial.length > 0) {
    [accumulator] = initial;
  } else {
    while (index < length && !(index in source)) index += 1;
    if (index === length) throw new TypeError('reduce of empty or all-hole input with no initial value');
    accumulator = source[index];
    index += 1;
  }

  for (; index < length; index += 1) {
    if (index in source) {
      accumulator = callback(accumulator, source[index], index, source);
    }
  }
  return accumulator;
}

export function mapAndReducePolicyExample() {
  const sparse = [, 2, , 4];
  const visited = [];
  const context = {factor: 3};
  const mapped = mapLike(sparse, function multiply(value, index, source) {
    visited.push([index, source === sparse]);
    return value * this.factor;
  }, context);
  const seedlessTotal = reduceLike(sparse, (sum, value) => sum + value);
  const explicitUndefined = reduceLike([1], (accumulator, value) => [accumulator, value], undefined);
  return {
    mapped,
    mappedKeys: Object.keys(mapped),
    visited,
    seedlessTotal,
    explicitUndefined
  };
}

export function flattenArray(source, depth = Infinity) {
  if (!Array.isArray(source)) throw new TypeError('flattenArray expects an array');
  const numericDepth = Number(depth);
  const normalizedDepth = Number.isNaN(numericDepth)
    ? 0
    : Math.max(0, Math.trunc(numericDepth));
  const output = [];
  const stack = [{array: source, index: 0, depth: normalizedDepth}];

  while (stack.length > 0) {
    const frame = stack.at(-1);
    if (frame.index >= frame.array.length) {
      stack.pop();
      continue;
    }
    const index = frame.index;
    frame.index += 1;
    if (!(index in frame.array)) continue;
    const value = frame.array[index];
    if (Array.isArray(value) && frame.depth > 0) {
      stack.push({array: value, index: 0, depth: frame.depth - 1});
    } else {
      output.push(value);
    }
  }
  return output;
}

export function flattenPolicyExample() {
  const nested = [1, [2, , [3, [4]]], 5];
  return {
    oneLevel: flattenArray(nested, 1),
    twoLevels: flattenArray(nested, 2),
    allLevels: flattenArray(nested)
  };
}

export function groupByToMap(items, keySelector) {
  if (typeof keySelector !== 'function') throw new TypeError('keySelector must be callable');
  const groups = new Map();
  let index = 0;
  for (const item of items) {
    const key = keySelector(item, index);
    index += 1;
    const bucket = groups.get(key);
    if (bucket === undefined && !groups.has(key)) groups.set(key, [item]);
    else bucket.push(item);
  }
  return groups;
}

export function groupByPolicyExample() {
  const north = {region: 'north'};
  const south = {region: 'south'};
  const rows = [
    {id: 'A', owner: north},
    {id: 'B', owner: south},
    {id: 'C', owner: north}
  ];
  const byOwner = groupByToMap(rows, row => row.owner);
  const byParity = groupByToMap(new Set([3, 4, 5]), (value, index) => `${index}:${value % 2}`);
  return {
    northIds: byOwner.get(north).map(row => row.id),
    southIds: byOwner.get(south).map(row => row.id),
    identityKeyCount: byOwner.size,
    parityKeys: [...byParity.keys()]
  };
}

export function promiseAllLike(iterable) {
  return new Promise((resolve, reject) => {
    const output = [];
    let remaining = 1;
    let index = 0;
    try {
      for (const value of iterable) {
        const currentIndex = index;
        index += 1;
        output[currentIndex] = undefined;
        remaining += 1;
        Promise.resolve(value).then(
          resolved => {
            output[currentIndex] = resolved;
            remaining -= 1;
            if (remaining === 0) resolve(output);
          },
          reject
        );
      }
      remaining -= 1;
      if (remaining === 0) resolve(output);
    } catch (error) {
      reject(error);
    }
  });
}

export async function promiseAllPolicyExample() {
  const completion = [];
  const later = (label, delay) => new Promise(resolve => {
    setTimeout(() => {
      completion.push(label);
      resolve(label);
    }, delay);
  });
  const ordered = await promiseAllLike([
    later('slow', 8),
    7,
    later('fast', 0)
  ]);
  let rejection;
  try {
    await promiseAllLike(new Set([Promise.resolve('ok'), Promise.reject(new Error('boom'))]));
  } catch (error) {
    rejection = error.message;
  }
  return {completion, ordered, rejection, empty: await promiseAllLike([])};
}

export function onceStable(fn) {
  if (typeof fn !== 'function') throw new TypeError('fn must be callable');
  let state = 'idle';
  let value;
  let error;
  return function (...args) {
    if (state === 'fulfilled') return value;
    if (state === 'rejected') throw error;
    if (state === 'running') throw new Error('reentrant once invocation');
    state = 'running';
    try {
      value = fn.apply(this, args);
      state = 'fulfilled';
      return value;
    } catch (cause) {
      error = cause;
      state = 'rejected';
      throw cause;
    }
  };
}

export function oncePolicyExample() {
  const receiver = {
    total: 0,
    add: onceStable(function (amount) {
      this.total += amount;
      return this.total;
    })
  };
  const values = [receiver.add(3), receiver.add(100)];
  const expected = new Error('first-call-failed');
  const failsOnce = onceStable(() => { throw expected; });
  const errors = [];
  for (let attempt = 0; attempt < 2; attempt += 1) {
    try { failsOnce(); } catch (error) { errors.push(error); }
  }
  let recursive;
  recursive = onceStable(() => recursive());
  let reentrantMessage;
  try { recursive(); } catch (error) { reentrantMessage = error.message; }
  return {
    values,
    total: receiver.total,
    sameErrorReplayed: errors[0] === expected && errors[1] === expected,
    reentrantMessage
  };
}

export class StudyEventEmitter {
  #events = new Map();

  on(event, listener) {
    if (typeof listener !== 'function') throw new TypeError('listener must be callable');
    const listeners = this.#events.get(event) ?? new Set();
    listeners.add(listener);
    this.#events.set(event, listeners);
    let active = true;
    return () => {
      if (!active) return false;
      active = false;
      return this.off(event, listener);
    };
  }

  once(event, listener) {
    let unsubscribe;
    const wrapper = (...args) => {
      unsubscribe();
      return listener.apply(this, args);
    };
    unsubscribe = this.on(event, wrapper);
    return unsubscribe;
  }

  off(event, listener) {
    const listeners = this.#events.get(event);
    if (!listeners) return false;
    const removed = listeners.delete(listener);
    if (listeners.size === 0) this.#events.delete(event);
    return removed;
  }

  emit(event, ...args) {
    const snapshot = [...(this.#events.get(event) ?? [])];
    for (const listener of snapshot) listener.apply(this, args);
    return snapshot.length;
  }

  listenerCount(event) {
    return this.#events.get(event)?.size ?? 0;
  }
}

export function eventEmitterPolicyExample() {
  const emitter = new StudyEventEmitter();
  const calls = [];
  const listenerC = value => calls.push(`C:${value}`);
  let unsubscribeB;
  emitter.on('tick', value => {
    calls.push(`A:${value}`);
    unsubscribeB();
    emitter.on('tick', listenerC);
  });
  unsubscribeB = emitter.on('tick', value => calls.push(`B:${value}`));
  emitter.once('tick', value => calls.push(`once:${value}`));
  const delivered = [emitter.emit('tick', 1), emitter.emit('tick', 2)];
  return {
    calls,
    delivered,
    remaining: emitter.listenerCount('tick'),
    secondUnsubscribeWasNoop: unsubscribeB() === false
  };
}

export async function runLimited(tasks, limit, {settle = false} = {}) {
  if (!Array.isArray(tasks) || tasks.some(task => typeof task !== 'function')) {
    throw new TypeError('tasks must be an array of functions');
  }
  if (!Number.isInteger(limit) || limit < 1) {
    throw new RangeError('limit must be a positive integer');
  }
  const results = new Array(tasks.length);
  let nextIndex = 0;
  let failed = false;
  async function worker() {
    while (true) {
      if (!settle && failed) return;
      const index = nextIndex;
      nextIndex += 1;
      if (index >= tasks.length) return;
      try {
        const value = await tasks[index]();
        results[index] = settle ? {status: 'fulfilled', value} : value;
      } catch (reason) {
        if (!settle) {
          failed = true;
          throw reason;
        }
        results[index] = {status: 'rejected', reason};
      }
    }
  }
  const workerCount = Math.min(limit, tasks.length);
  await Promise.all(Array.from({length: workerCount}, () => worker()));
  return results;
}

export async function concurrencyLimiterPolicyExample() {
  let active = 0;
  let maxActive = 0;
  const completion = [];
  const task = (label, delay, shouldReject = false) => async () => {
    active += 1;
    maxActive = Math.max(maxActive, active);
    try {
      await new Promise(resolve => setTimeout(resolve, delay));
      completion.push(label);
      if (shouldReject) throw new Error(label);
      return label;
    } finally {
      active -= 1;
    }
  };
  const ordered = await runLimited([
    task('A', 20), task('B', 2), task('C', 3), task('D', 0)
  ], 2);
  const settled = await runLimited([
    async () => 'ok', async () => { throw new Error('bad'); }
  ], 1, {settle: true});
  const failFastStarts = [];
  let failFastMessage;
  try {
    await runLimited([
      async () => {
        failFastStarts.push(0);
        await new Promise(resolve => setTimeout(resolve, 1));
        throw new Error('stop');
      },
      async () => {
        failFastStarts.push(1);
        await new Promise(resolve => setTimeout(resolve, 8));
      },
      async () => { failFastStarts.push(2); },
      async () => { failFastStarts.push(3); }
    ], 2);
  } catch (error) {
    failFastMessage = error.message;
  }
  await new Promise(resolve => setTimeout(resolve, 10));
  return {
    ordered,
    completion,
    maxActive,
    settled: settled.map(result => result.status),
    rejection: settled[1].reason.message,
    failFastStarts,
    failFastMessage
  };
}

export async function eventLoopAndLifetimePolicyExample() {
  const output = [];
  output.push('1');
  async function run() {
    output.push('2');
    await Promise.resolve();
    output.push('3');
    queueMicrotask(() => output.push('7'));
  }
  run();
  Promise.resolve().then(() => {
    output.push('4');
    queueMicrotask(() => output.push('8'));
  });
  output.push('6');
  await new Promise(resolve => setTimeout(() => {
    output.push('5');
    resolve();
  }, 0));

  const emitter = new StudyEventEmitter();
  const selected = [];
  const attachSelection = hugeModel => {
    const id = hugeModel.id;
    return emitter.on('select', () => selected.push(id));
  };
  const dispose = attachSelection({id: 'M1', payload: new Array(1000).fill('x')});
  const beforeDispose = emitter.listenerCount('select');
  emitter.emit('select');
  const firstDispose = dispose();
  const secondDispose = dispose();
  emitter.emit('select');
  return {
    output,
    selected,
    beforeDispose,
    afterDispose: emitter.listenerCount('select'),
    firstDispose,
    secondDispose
  };
}

export function propertyOwnershipPolicyExample() {
  const defaults = {theme: 'system', retries: 3};
  const settings = Object.create(defaults);
  settings.theme = 'dark';
  const dictionary = Object.create(null);
  dictionary.hasOwnProperty = 'data, not a method';
  dictionary.region = 'us-east-1';

  const inspect = (object, key) => ({
    key,
    exists: key in object,
    own: Object.hasOwn(object, key),
    value: object[key]
  });
  return {
    prototypeIsDefaults: Object.getPrototypeOf(settings) === defaults,
    checks: [inspect(settings, 'theme'), inspect(settings, 'retries'), inspect(settings, 'missing')],
    nullPrototypeOwnRegion: Object.hasOwn(dictionary, 'region'),
    shadowedMethodIsSafe: Object.hasOwn(dictionary, 'hasOwnProperty')
  };
}

export function simpleBind(fn, thisArg, ...boundArgs) {
  if (typeof fn !== 'function') throw new TypeError('fn must be callable');
  return function simplifiedBound(...laterArgs) {
    return Reflect.apply(fn, thisArg, [...boundArgs, ...laterArgs]);
  };
}

export function simpleBindPolicyExample() {
  function format(prefix, suffix) {
    return `${prefix}${this.name}${suffix}`;
  }
  const receiver = {name: 'Ada'};
  const greet = simpleBind(format, receiver, 'Hello ');

  function Person(name) { this.name = name; }
  const nativePerson = Person.bind({name: 'ignored'}, 'Lin');
  const simplifiedReceiver = {name: 'before'};
  const simplifiedPerson = simpleBind(Person, simplifiedReceiver, 'Grace');
  const nativeInstance = new nativePerson();
  const simplifiedInstance = new simplifiedPerson();

  return {
    callResult: greet('!'),
    callOverrideIgnored: greet.call({name: 'Other'}, '?'),
    nativeConstructedName: nativeInstance.name,
    simplifiedConstructedOwnName: Object.hasOwn(simplifiedInstance, 'name'),
    simplifiedReceiverName: simplifiedReceiver.name
  };
}

export async function asyncIteratorPolicyExample() {
  const events = [];
  async function* pages() {
    try {
      for (let page = 1; page <= 3; page += 1) {
        events.push(`fetch:${page}`);
        await Promise.resolve();
        yield [`item-${page}`];
      }
    } finally {
      events.push('closed');
    }
  }

  const received = [];
  for await (const page of pages()) {
    events.push(`consume:${page[0]}`);
    received.push(...page);
    if (received.length === 2) break;
  }
  return {received, events, thirdPageRequested: events.includes('fetch:3')};
}

export function parseRetryAfter(value, now = Date.now()) {
  if (value == null) return undefined;
  const seconds = Number(value);
  if (Number.isFinite(seconds) && seconds >= 0) return seconds * 1000;
  const timestamp = Date.parse(value);
  return Number.isFinite(timestamp) ? Math.max(0, timestamp - now) : undefined;
}

export function abortableDelay(ms, signal) {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(resolve, ms);
    signal?.addEventListener('abort', () => {
      clearTimeout(timer);
      reject(signal.reason ?? new DOMException('Aborted', 'AbortError'));
    }, {once: true});
  });
}

export async function retryIdempotent(operation, {
  method = 'GET', idempotencyKey, attempts = 3, baseDelayMs = 100,
  maxDelayMs = 5000, signal, random = Math.random,
  sleep = abortableDelay,
  shouldRetry = error => error?.retryable === true
} = {}) {
  if (!Number.isInteger(attempts) || attempts < 1) throw new RangeError('attempts must be positive');
  const idempotentMethods = new Set(['GET', 'HEAD', 'PUT', 'DELETE', 'OPTIONS', 'TRACE']);
  const safeToRepeat = idempotentMethods.has(method.toUpperCase()) || Boolean(idempotencyKey);
  if (!safeToRepeat && attempts > 1) throw new Error('automatic retry requires idempotent semantics');

  for (let attempt = 1; attempt <= attempts; attempt += 1) {
    signal?.throwIfAborted();
    try {
      return await operation(attempt);
    } catch (error) {
      if (attempt === attempts || !shouldRetry(error)) throw error;
      const cap = Math.min(maxDelayMs, baseDelayMs * (2 ** (attempt - 1)));
      const jittered = Math.floor(random() * cap);
      const delay = Number.isFinite(error.retryAfterMs)
        ? Math.min(maxDelayMs, Math.max(0, error.retryAfterMs))
        : jittered;
      await sleep(delay, signal);
    }
  }
}

export function chooseExecutionSite({estimatedTaskMs, needsDom = false, networkBound = false, transferable = false}) {
  if (needsDom) return {site: 'main-thread', reason: 'DOM ownership'};
  if (networkBound) return {site: 'main-thread-async', reason: 'waiting is not CPU contention'};
  if (estimatedTaskMs >= 50) {
    return {site: 'worker-candidate', reason: transferable ? 'CPU work with transferable input' : 'profile clone cost first'};
  }
  return {site: 'main-thread', reason: 'small task; worker overhead may dominate'};
}

export async function retryAndWorkerPolicyExample() {
  const delays = [];
  const attempts = [];
  const value = await retryIdempotent(async attempt => {
    attempts.push(attempt);
    if (attempt < 3) {
      const error = new Error('temporary');
      error.retryable = true;
      if (attempt === 1) error.retryAfterMs = 700;
      throw error;
    }
    return 'ok';
  }, {attempts: 3, baseDelayMs: 200, random: () => 0.5, sleep: async ms => delays.push(ms)});

  return {
    value,
    attempts,
    delays,
    retryAfterSeconds: parseRetryAfter('2', 0),
    cpuChoice: chooseExecutionSite({estimatedTaskMs: 80, transferable: true}),
    domChoice: chooseExecutionSite({estimatedTaskMs: 80, needsDom: true}),
    networkChoice: chooseExecutionSite({estimatedTaskMs: 80, networkBound: true})
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

assert.deepEqual(await latestRequestCancellationExample(), {
  events: ['abort:cat', 'resolve:caterpillar'],
  results: [undefined, ['caterpillar']]
});
assert.deepEqual(delegatedActionExample(), ['A', undefined]);
assert.deepEqual(renderingAndStoragePolicyExample(), {
  forcedLayouts: {interleaved: 4, batched: 0},
  storagePolicy: {
    theme: 'localStorage',
    checkoutDraftForThisTab: 'sessionStorage',
    serverSessionIdentifier: 'Secure HttpOnly SameSite cookie',
    offlineCatalog: 'IndexedDB'
  }
});
assert.deepEqual(corsAndSecurityPolicyExample(), {
  visibility: ['readable', 'blocked', 'readable', 'opaque'],
  controls: {
    xss: ['contextual output encoding', 'safe sinks', 'sanitization for allowed HTML', 'CSP defense-in-depth'],
    csrf: ['anti-CSRF token', 'SameSite cookie', 'Origin or Fetch Metadata validation', 'authorization']
  },
  corsIsCsrfDefense: false
});

assert.deepEqual(eventActionPolicyExample(), [
  {preventDefault: false, stopPropagation: false, action: 'browser-navigation'},
  {preventDefault: true, stopPropagation: false, action: 'client-router'},
  {preventDefault: true, stopPropagation: false, action: 'show-errors'},
  {preventDefault: false, stopPropagation: false, action: 'browser-navigation'}
]);
assert.deepEqual(scriptSchedulingExample(), [
  {name: 'analytics', at: 20},
  {name: 'ads', at: 35},
  {name: 'config', at: 50},
  {name: 'application', at: 50}
]);
assert.deepEqual(accessibleFormPolicyExample(), {
  validField: {issues: [], ariaInvalid: 'true', focusTarget: 'email'},
  placeholderOnly: {
    issues: ['missing-programmatic-label', 'required-state-not-visible', 'error-not-associated'],
    ariaInvalid: 'true',
    focusTarget: 'name'
  }
});
assert.deepEqual(responsiveImagePolicyExample(), {
  expectedSourcePixels: 800,
  illustrativeCandidate: 800,
  browserMakesFinalChoice: true,
  reservedAspectRatio: '16:9'
});

assert.deepEqual(visibilityPolicyExample(), {
  mechanisms: {
    hidden: {layout: false, painted: false, accessibilityTree: false, focusableDescendants: false},
    displayNone: {layout: false, painted: false, accessibilityTree: false, focusableDescendants: false},
    visibilityHidden: {layout: true, painted: false, accessibilityTree: false, focusableDescendants: false},
    ariaHidden: {layout: true, painted: true, accessibilityTree: false, focusableDescendants: 'must-not-exist'}
  },
  closed: {hidden: true, ariaExpanded: 'false'},
  open: {hidden: false, ariaExpanded: 'true'}
});
assert.deepEqual(lazyLoadingPolicyExample(), {
  hero: {loading: 'eager', priority: 'high'},
  gallery: {loading: 'lazy', priority: 'auto'},
  map: {loading: 'lazy', priority: 'auto'},
  invariants: ['reserve-dimensions', 'retain-alt-or-title', 'browser-controls-threshold']
});
assert.deepEqual(documentQualityPolicyExample(), {
  article: [],
  brokenLandingPage: [
    'missing-language',
    'non-descriptive-title',
    'unclear-primary-heading',
    'non-descriptive-or-non-crawlable-link',
    'missing-image-alternative',
    'misleading-structured-data'
  ]
});

assert.deepEqual(cascadePolicyExample(), {
  sameLayerWinner: 'teal',
  finalWinner: 'darkgreen',
  comparisonOrder: ['layer', 'specificity', 'scope-proximity', 'source-order'],
  limitedTo: 'matching normal author style rules in one encapsulation context'
});
assert.deepEqual(boxModelPolicyExample(), {
  contentBox: {content: 240, borderBox: 276, outer: 300},
  borderBox: {content: 204, borderBox: 240, outer: 264}
});
assert.deepEqual(layoutSizingPolicyExample(), {
  dashboard: 'grid',
  toolbar: 'flex',
  longTitleBefore: false,
  longTitleAfter: true,
  gridTrackFloor: 'minmax(0, 1fr)'
});
assert.deepEqual(positioningAndStackingPolicyExample(), {
  absoluteBadge: 'card',
  fixedInsideTransform: 'app-shell',
  sticky: {retainsFlowSpace: true, requiresInset: true, constrainedByScrollContainer: true},
  tooltipCanEscapeParentContext: false,
  topLayerParentContext: 'root-stacking-context'
});

assert.deepEqual(centeringAndUnitPolicyExample(), {
  grid: 'place-items:center',
  article: 'constrained-inline-size + margin-inline:auto',
  title: 'text-align:center',
  twoRemAt20: 40,
  oneEmAt18: 18,
  viewportTextPolicy: 'clamp(rem, rem + vw, rem)'
});
assert.deepEqual(containerQueryPolicyExample(), {
  sameViewport: 90,
  rail: 'stacked',
  catalog: 'image-and-details',
  environmentPolicy: 'media-query',
  allocationPolicy: 'container-query'
});
assert.deepEqual(customPropertyPolicyExample(), {
  missingUsesFallback: 'royalblue',
  unregisteredInvalidSubstitutes: 'invalid',
  registeredInvalidUsesInitial: 0,
  sassLifetime: 'build-time',
  customPropertyLifetime: 'cascade-and-runtime'
});
assert.deepEqual(layoutStabilityAndNamingPolicyExample(), {
  imageReservation: '800:450',
  slowImageMustShift: false,
  stableInsertionPolicy: 'reserve-space-before-render',
  block: 'product-card',
  element: 'product-card__title',
  modifier: 'product-card--featured',
  bemRequired: false
});

const collectionTransforms = mapAndReducePolicyExample();
assert.equal(collectionTransforms.mapped.length, 4);
assert.equal(0 in collectionTransforms.mapped, false);
assert.equal(2 in collectionTransforms.mapped, false);
assert.deepEqual(collectionTransforms.mappedKeys, ['1', '3']);
assert.deepEqual(collectionTransforms.visited, [[1, true], [3, true]]);
assert.equal(collectionTransforms.mapped[1], 6);
assert.equal(collectionTransforms.mapped[3], 12);
assert.equal(collectionTransforms.seedlessTotal, 6);
assert.deepEqual(collectionTransforms.explicitUndefined, [undefined, 1]);
assert.throws(() => reduceLike(new Array(3), () => 0), TypeError);
assert.deepEqual(flattenPolicyExample(), {
  oneLevel: [1, 2, [3, [4]], 5],
  twoLevels: [1, 2, 3, [4], 5],
  allLevels: [1, 2, 3, 4, 5]
});
assert.deepEqual(groupByPolicyExample(), {
  northIds: ['A', 'C'],
  southIds: ['B'],
  identityKeyCount: 2,
  parityKeys: ['0:1', '1:0', '2:1']
});
assert.deepEqual(await promiseAllPolicyExample(), {
  completion: ['fast', 'slow'],
  ordered: ['slow', 7, 'fast'],
  rejection: 'boom',
  empty: []
});

assert.deepEqual(oncePolicyExample(), {
  values: [3, 3],
  total: 3,
  sameErrorReplayed: true,
  reentrantMessage: 'reentrant once invocation'
});
assert.deepEqual(eventEmitterPolicyExample(), {
  calls: ['A:1', 'B:1', 'once:1', 'A:2', 'C:2'],
  delivered: [3, 2],
  remaining: 2,
  secondUnsubscribeWasNoop: true
});
assert.deepEqual(await concurrencyLimiterPolicyExample(), {
  ordered: ['A', 'B', 'C', 'D'],
  completion: ['B', 'C', 'D', 'A'],
  maxActive: 2,
  settled: ['fulfilled', 'rejected'],
  rejection: 'bad',
  failFastStarts: [0, 1],
  failFastMessage: 'stop'
});
assert.deepEqual(await eventLoopAndLifetimePolicyExample(), {
  output: ['1', '2', '6', '3', '4', '7', '8', '5'],
  selected: ['M1'],
  beforeDispose: 1,
  afterDispose: 0,
  firstDispose: true,
  secondDispose: false
});

assert.deepEqual(propertyOwnershipPolicyExample(), {
  prototypeIsDefaults: true,
  checks: [
    {key: 'theme', exists: true, own: true, value: 'dark'},
    {key: 'retries', exists: true, own: false, value: 3},
    {key: 'missing', exists: false, own: false, value: undefined}
  ],
  nullPrototypeOwnRegion: true,
  shadowedMethodIsSafe: true
});
assert.deepEqual(simpleBindPolicyExample(), {
  callResult: 'Hello Ada!',
  callOverrideIgnored: 'Hello Ada?',
  nativeConstructedName: 'Lin',
  simplifiedConstructedOwnName: false,
  simplifiedReceiverName: 'Grace'
});
assert.deepEqual(await asyncIteratorPolicyExample(), {
  received: ['item-1', 'item-2'],
  events: ['fetch:1', 'consume:item-1', 'fetch:2', 'consume:item-2', 'closed'],
  thirdPageRequested: false
});
assert.deepEqual(await retryAndWorkerPolicyExample(), {
  value: 'ok', attempts: [1, 2, 3], delays: [700, 200], retryAfterSeconds: 2000,
  cpuChoice: {site: 'worker-candidate', reason: 'CPU work with transferable input'},
  domChoice: {site: 'main-thread', reason: 'DOM ownership'},
  networkChoice: {site: 'main-thread-async', reason: 'waiting is not CPU contention'}
});
await assert.rejects(
  retryIdempotent(async () => 'no', {method: 'POST', attempts: 2}),
  /idempotent semantics/
);

console.log('JavaScript fundamentals examples: 54 scenario groups passed');
