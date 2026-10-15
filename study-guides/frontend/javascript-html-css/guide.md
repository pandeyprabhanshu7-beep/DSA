---
title: JavaScript, HTML and CSS Interview Guide
description: Incremental, source-reconciled frontend interview guide with executable JavaScript examples.
---

# JavaScript, HTML and CSS Interview Guide

This is the canonical incremental guide for the paired JavaScript/HTML/CSS workbooks. It currently reconciles **Q1–Q56**: execution fundamentals, value semantics, the function/object model, asynchronous JavaScript, browser events and rendering, storage, web security, semantic HTML, script delivery, accessible forms, responsive media, visibility semantics, native lazy loading, accessible search-friendly markup, the CSS cascade and box model, Flexbox/Grid sizing, positioning, stacking contexts, responsive units, component queries, runtime design tokens, layout stability, CSS naming, and interview implementations for collection transforms and promise coordination. Later questions remain queued; this is not a claim that the complete workbook has been promoted.

## Module map

- [Q1 — `var`, `let`, and `const`](#q1)
- [Q2 — Hoisting and the temporal dead zone](#q2)
- [Q3 — Lexical scope](#q3)
- [Q4 — Loop capture with `var` and `let`](#q4)
- [Q5 — Primitive values and object references](#q5)
- [Q6 — `==` and `===`](#q6)
- [Q7 — `null`, `undefined`, undeclared names, and `NaN`](#q7)
- [Q8 — Shallow and deep copies](#q8)
- [Q9 — `map`, `filter`, and `reduce`](#q9)
- [Q10 — Optional chaining and nullish coalescing](#q10)
- [Q11 — Closures](#q11)
- [Q12 — How `this` is determined](#q12)
- [Q13 — `call`, `apply`, and `bind`](#q13)
- [Q14 — Prototype chains](#q14)
- [Q15 — Classes and prototypes](#q15)
- [Q16 — Function forms](#q16)
- [Q17 — Currying and partial application](#q17)
- [Q18 — Memoization](#q18)
- [Q19 — Event-loop ordering](#q19)
- [Q20 — `async` and `await`](#q20)
- [Q21 — Promise combinators](#q21)
- [Q22 — Async error boundaries](#q22)
- [Q23 — Debounce](#q23)
- [Q24 — Throttle](#q24)
- [Q25 — Cancelling `fetch`](#q25)
- [Q26 — Event propagation and delegation](#q26)
- [Q27 — Layout and paint costs](#q27)
- [Q28 — Browser storage](#q28)
- [Q29 — Same-origin policy and CORS](#q29)
- [Q30 — XSS and CSRF](#q30)
- [Q31 — Default actions and propagation](#q31)
- [Q32 — Semantic HTML](#q32)
- [Q33 — `async` and `defer`](#q33)
- [Q34 — Accessible forms](#q34)
- [Q35 — The viewport meta element](#q35)
- [Q36 — Responsive images](#q36)
- [Q37 — Visibility and accessibility](#q37)
- [Q38 — Native lazy loading](#q38)
- [Q39 — SEO without accessibility regressions](#q39)
- [Q40 — Cascade and specificity](#q40)
- [Q41 — The CSS box model](#q41)
- [Q42 — Flexbox versus Grid](#q42)
- [Q43 — Automatic minimum sizes and overflow](#q43)
- [Q44 — CSS positioning schemes](#q44)
- [Q45 — Stacking contexts and `z-index`](#q45)
- [Q46 — Centering by layout context](#q46)
- [Q47 — CSS length units](#q47)
- [Q48 — Container queries](#q48)
- [Q49 — CSS custom properties and Sass variables](#q49)
- [Q50 — Preventing layout shift](#q50)
- [Q51 — BEM and naming ownership](#q51)
- [Q52 — Implement `map`](#q52)
- [Q53 — Implement `reduce`](#q53)
- [Q54 — Flatten a nested array](#q54)
- [Q55 — Implement `groupBy`](#q55)
- [Q56 — Implement `Promise.all`](#q56)
- [Source provenance and verification](#sources)
- [Next bounded section](#next)

## The model before the syntax

An interview answer is stronger when it separates four questions:

1. **Where is the binding stored?** Function/global variable environment or a lexical block environment?
2. **When is the binding created?** Before statements execute, during declaration instantiation.
3. **When is the binding initialized?** `var` starts as `undefined`; a lexical binding cannot be read until its declaration initializes it.
4. **Which binding does a function close over?** The one found through the lexical environment chain where the function was created.

```mermaid
flowchart TD
    Enter[Enter scope] --> Create[Create environment records]
    Create --> Var[var binding initialized to undefined]
    Create --> Lex[let or const binding uninitialized]
    Var --> Run[Evaluate statements]
    Lex --> Run
    Run --> Init[Declaration initializes lexical binding]
    Run --> Lookup[Function resolves names through outer environments]
```

The phrase “JavaScript moves declarations to the top” is only a mnemonic. No source text moves. The specification describes environment creation, binding creation, initialization, and later statement evaluation.

<a id="q1"></a>

## Q1. What is the difference between `var`, `let`, and `const`?

### What the interviewer is asking

Given a declaration, identify its scope, redeclaration rules, initialization timing, and whether the **binding** may be assigned again. Do not confuse a constant binding with an immutable object.

| Declaration | Scope | Before declaration executes | Same-scope redeclaration | Reassignment |
|---|---|---|---|---|
| `var` | nearest function or script/global variable environment | readable as `undefined` | generally allowed for another `var` | allowed |
| `let` | nearest block | binding exists but is uninitialized | syntax error | allowed after initialization |
| `const` | nearest block | binding exists but is uninitialized | syntax error | forbidden after initialization |

### Example 1 — the binding is constant, not the object

```javascript
const profile = { name: "Ada", skills: ["JS"] };
profile.name = "Grace";          // allowed: mutate the object
profile.skills.push("HTML");     // allowed
// profile = {};                 // TypeError: assignment to a constant

console.log(profile);
// { name: "Grace", skills: ["JS", "HTML"] }
```

Trace:

| Step | Binding `profile` | Referenced object |
|---:|---|---|
| declaration | initialized once | `{ name: "Ada", skills: ["JS"] }` |
| property write | unchanged | `name` becomes `"Grace"` |
| `push` | unchanged | array gains `"HTML"` |

Use `Object.freeze()` when shallow runtime mutation prevention is the actual requirement; it solves a different problem from `const`.

<a id="q2"></a>

## Q2. Explain hoisting and the Temporal Dead Zone with output

### Requirement

Predict the output in two phases: first declaration instantiation, then statement evaluation. A lexical binding’s temporal dead zone (TDZ) lasts from entering its scope until its declaration initializes it.

### Example 2 — distinguish created from initialized

```javascript
console.log(total); // undefined
printReady();       // ready

var total = 7;
function printReady() {
  console.log("ready");
}

try {
  console.log(status); // ReferenceError
} catch (error) {
  console.log(error.name);
}
let status = "done";
```

Expected output:

```text
undefined
ready
ReferenceError
```

Dry run:

| Phase | `total` | `printReady` | `status` |
|---|---|---|---|
| declaration instantiation | initialized to `undefined` | initialized to function object | created, uninitialized |
| first log | prints `undefined` | — | — |
| call | — | prints `ready` | — |
| `var` initializer | becomes `7` | — | — |
| attempted lexical read | — | — | throws `ReferenceError` |
| lexical declaration | — | — | initialized to `"done"` |

Interview trap: `typeof missingName` returns `"undefined"`, but `typeof tdzName` can throw when `tdzName` is an uninitialized lexical binding in the current scope.

<a id="q3"></a>

## Q3. What is lexical scope?

### Requirement

Resolve an identifier from the function’s **definition environment**, not from whichever function happens to call it. The environment chain is determined by source nesting.

### Example 3 — call site does not replace the definition environment

```javascript
const label = "global";

function makeReader() {
  const label = "outer";
  return function readLabel() {
    return label;
  };
}

function invokeWithAnotherLabel(fn) {
  const label = "caller";
  return fn();
}

const read = makeReader();
console.log(invokeWithAnotherLabel(read)); // outer
```

Lookup trace for `label` inside `readLabel`:

```mermaid
flowchart LR
    Read[readLabel environment] --> Outer[makeReader environment: label = outer]
    Outer --> Global[global environment: label = global]
```

The lookup stops at `makeReader`’s binding. `invokeWithAnotherLabel` is on the call stack, but it is not on `readLabel`’s lexical environment chain.

<a id="q4"></a>

## Q4. Why does a `var` loop print `3,3,3`, while a `let` loop prints `0,1,2`?

### Requirement

Count captured **bindings**, not captured values. A `var` loop reuses one function-scoped binding. A `for` loop with a lexical declaration creates a fresh per-iteration environment.

### Example 4 — callbacks read their bindings after the loop

```javascript
for (var i = 0; i < 3; i += 1) {
  setTimeout(() => console.log(i), 0);
}
// 3, 3, 3

for (let j = 0; j < 3; j += 1) {
  setTimeout(() => console.log(j), 0);
}
// 0, 1, 2
```

Dry run:

| Registration | `var` callback closes over | `let` callback closes over |
|---:|---|---|
| 1 | shared `i` | iteration binding `j = 0` |
| 2 | shared `i` | iteration binding `j = 1` |
| 3 | shared `i` | iteration binding `j = 2` |
| after both loops | shared `i` is `3` | three `j` bindings remain `0`, `1`, `2` |

The zero-millisecond delay does not mean “run now.” Each timer callback is queued for later processing by the host. The runnable [examples module](examples.mjs) checks the outputs without relying on console inspection.

### ES5-era repair and why `let` is clearer

Before per-iteration lexical bindings were available, an immediately invoked function could create a fresh parameter binding:

```javascript
for (var i = 0; i < 3; i += 1) {
  ((captured) => setTimeout(() => console.log(captured), 0))(i);
}
// 0, 1, 2
```

Prefer `let` for this case: it states the lifetime rule directly and removes the extra wrapper function.

<a id="q5"></a>

## Q5. Are objects “passed by reference” in JavaScript?

### Requirement

Say precisely what is copied. Every assignment and argument pass copies a value. A primitive value is the data itself; an object value is a reference to an object. Two copied references can therefore reach the same mutable object, but assigning a new reference to one variable never redirects the other variable.

```mermaid
flowchart LR
    A["account value: reference R"] --> O["object { balance: 10 }"]
    B["alias value: copied reference R"] --> O
    P["parameter value: copied reference R"] --> O
    P -. "parameter reassignment only" .-> N["new object"]
```

### Example 5 — distinguish mutation from reassignment

```javascript
const account = { balance: 10 };
const alias = account;

function update(local) {
  local.balance += 5;      // mutates the shared object
  local = { balance: 0 };  // reassigns only the local parameter
  return local;
}

const replacement = update(account);
console.log(account.balance);     // 15
console.log(alias.balance);       // 15
console.log(replacement.balance); // 0
console.log(account === alias);   // true
console.log(account === replacement); // false
```

Trace:

| Step | `account` / `alias` | local parameter | Visible result |
|---:|---|---|---|
| call | reference R | copy of R | all three reach the original object |
| property update | R | R | original object becomes `{ balance: 15 }` |
| parameter assignment | R | new reference S | caller bindings still hold R |
| return | R | S | `replacement` receives S |

For primitives, the same copy rule is easier to see because there is no separately mutable object: changing a copied number or string only changes that binding.

<a id="q6"></a>

## Q6. What is the difference between `==` and `===`?

### Requirement

Use strict equality by default and explain the result from the specification rule, not from “truthiness.” `===` returns false for different types and otherwise compares without coercion. `==` follows the abstract equality algorithm: it has an explicit `null`/`undefined` match and may convert booleans, strings, numbers, BigInts, or objects before retrying the comparison.

### Example 6 — classify before evaluating

```javascript
console.log(0 === false);          // false: different types
console.log(0 == false);           // true: false -> 0
console.log("" == 0);             // true: "" -> 0
console.log(null == undefined);    // true: explicit loose-equality rule
console.log(null == 0);            // false: null is not converted to 0 here
console.log([5] == 5);             // true: [5] -> "5" -> 5
console.log(Number.NaN === Number.NaN); // false
```

Decision trace:

| Expression | First decisive rule | Result |
|---|---|---:|
| `0 === false` | strict equality rejects different types | `false` |
| `0 == false` | boolean converts with `ToNumber(false)` | `true` |
| `null == undefined` | dedicated abstract-equality case | `true` |
| `null == 0` | no applicable conversion makes them equal | `false` |
| `[5] == 5` | object converts to primitive, then string to number | `true` |

Loose equality is not the same as comparing `Boolean(left)` and `Boolean(right)`. For example, both `"0"` and `[]` are truthy, but the actual `==` algorithm follows type-directed conversions. In application code, explicit normalization such as `Number(input) === expected` makes the contract reviewable.

<a id="q7"></a>

## Q7. How do `null`, `undefined`, undeclared names, and `NaN` differ?

### Requirement

Separate four states instead of calling all of them “empty”:

| State | Meaning | Safe test |
|---|---|---|
| `undefined` | a value often produced by an uninitialized variable, missing property, or absent return | `value === undefined` |
| `null` | an explicit null value chosen by code or an API | `value === null` |
| undeclared identifier | no resolvable binding exists | `typeof maybeMissing === "undefined"` only when probing is intentional |
| `NaN` | a Number value representing a failed/undefined numeric result | `Number.isNaN(value)` |

### Example 7 — preserve the distinction at an input boundary

```javascript
function classify(value) {
  if (value === null) return "explicit-null";
  if (value === undefined) return "missing";
  if (Number.isNaN(value)) return "invalid-number";
  return "present";
}

const record = { nickname: null };
console.log(classify(record.nickname)); // explicit-null
console.log(classify(record.age));      // missing
console.log(classify(Number("oops"))); // invalid-number
console.log(classify(0));               // present
console.log(typeof neverDeclared);      // undefined
// console.log(neverDeclared);          // ReferenceError
```

Important edges:

- `typeof null` is the historical string `"object"`; it is not a reliable null test.
- `NaN` is the only ECMAScript value that is not strictly equal to itself. `Object.is(NaN, NaN)` and `Number.isNaN(NaN)` are both true.
- Global `isNaN("oops")` coerces first and returns true; `Number.isNaN("oops")` returns false. Prefer the non-coercing method when validating typed data.
- A declared lexical binding in its TDZ is different from an undeclared name: even `typeof` can throw before that binding is initialized.

<a id="q8"></a>

## Q8. What is a shallow copy versus a deep copy?

### Requirement

Define the object-graph boundary. A shallow copy creates a new outer container and copies its property values; nested object values are still references to the original nested objects. A deep copy recursively produces separate supported graph nodes. “Deep” does not mean “copies every possible JavaScript value.”

### Example 8 — choose the copy that matches the update boundary

```javascript
const original = {
  owner: { name: "Ada" },
  tags: ["js"],
  created: new Date("2026-01-02T00:00:00Z")
};

const shallow = { ...original };
shallow.owner.name = "Grace";
console.log(original.owner.name); // Grace: nested owner is shared

const isolated = structuredClone(original);
isolated.owner.name = "Lin";
isolated.tags.push("css");
console.log(original.owner.name); // Grace
console.log(original.tags);       // ["js"]
console.log(isolated.created instanceof Date); // true
```

Graph trace:

| Copy | Outer object | `owner` | `tags` | `Date` |
|---|---|---|---|---|
| spread | new | shared | shared | shared |
| `structuredClone` | new | cloned | cloned | cloned as a `Date` |

`structuredClone()` supports cycles and many built-in data types, and can transfer eligible buffers. It throws `DataCloneError` for non-serializable values such as functions. JSON stringify/parse is a text-serialization round trip, not a general clone: it rejects cycles, loses `undefined` properties, and converts dates to strings. Often the clearest immutable update is to copy only the path being changed rather than clone an entire graph.

<a id="q9"></a>

## Q9. What do `map`, `filter`, and `reduce` communicate?

### Requirement

Choose the operation from the output shape. `map` preserves the number of array slots while transforming values, `filter` keeps a subset, and `reduce` folds visited elements into an accumulator. The callbacks are invoked in ascending index order for assigned elements; these methods do not mutate the source array by themselves, although a callback still can.

```mermaid
flowchart TD
    Goal{Desired output?}
    Goal -->|one result per input| Map[map]
    Goal -->|subset of inputs| Filter[filter]
    Goal -->|one accumulated result| Reduce[reduce]
    Goal -->|early exit or side effect| Other[find, some, every, or a loop]
```

### Example 9 — a readable transformation pipeline

```javascript
const orders = [
  { id: "A", paid: true, cents: 1250 },
  { id: "B", paid: false, cents: 900 },
  { id: "C", paid: true, cents: 750 }
];

const paidIds = orders
  .filter(order => order.paid)
  .map(order => order.id);

const paidCents = orders.reduce(
  (sum, order) => order.paid ? sum + order.cents : sum,
  0
);

console.log(paidIds);   // ["A", "C"]
console.log(paidCents); // 2000
```

Dry run of the reducer:

| Order | Accumulator before | Action | Accumulator after |
|---|---:|---|---:|
| A, paid | 0 | add 1250 | 1250 |
| B, unpaid | 1250 | keep | 1250 |
| C, paid | 1250 | add 750 | 2000 |

Each method is linear in the array length for a full pass. The `filter().map()` pipeline is expressive but allocates an intermediate array and performs two passes. A single loop or reducer may be appropriate on a measured hot path. Always supply an explicit initial accumulator unless the empty-array behavior and first-element semantics are deliberately part of the contract.

<a id="q10"></a>

## Q10. How do optional chaining and nullish coalescing work?

### Requirement

Use `?.` to stop one continuous property, element, or call chain when the value immediately to its left is `null` or `undefined`. Use `??` to default only those two nullish values. Neither operator treats `0`, `false`, or `""` as missing.

### Example 10 — default missing configuration without erasing valid falsy values

```javascript
function readOptions(config) {
  return {
    retries: config?.network?.retries ?? 3,
    label: config?.ui?.label ?? "default",
    onReadyResult: config?.hooks?.onReady?.() ?? "not-run"
  };
}

console.log(readOptions({
  network: { retries: 0 },
  ui: { label: "" },
  hooks: { onReady: () => false }
}));
// { retries: 0, label: "", onReadyResult: false }

console.log(readOptions(null));
// { retries: 3, label: "default", onReadyResult: "not-run" }
```

Trace for `config?.network?.retries ?? 3`:

| Value reached | Optional chain result | `?? 3` result |
|---|---|---:|
| `config` is `null` | `undefined` | `3` |
| `network` is missing | `undefined` | `3` |
| `retries` is `0` | `0` | `0` |
| `retries` is `false` | `false` | `false` |

Grouping can end a chain: `(config?.network).retries` may still throw because `.retries` is outside the optional chain. Optional chaining also cannot be used as an assignment target. When mixing `??` with `&&` or `||`, add parentheses; the grammar intentionally rejects an unparenthesized mixture.

<a id="q11"></a>

## Q11. What is a closure, and why is it useful?

### Requirement

A closure is a function together with access to the lexical environment where that function was created. The important fact is not that one function is textually inside another; it is that a reachable function retains the bindings it needs even after the outer call has returned.

### Example 11 — independent factories retain independent bindings

```javascript
function makeCounter(start = 0) {
  let count = start;
  return {
    next() { count += 1; return count; },
    value() { return count; }
  };
}

const first = makeCounter(0);
const second = makeCounter(10);

console.log(first.next());  // 1
console.log(first.next());  // 2
console.log(second.next()); // 11
console.log(first.value()); // 2
```

Trace:

| Event | `first` environment | `second` environment |
|---|---:|---:|
| factory calls return | `count = 0` | `count = 10` |
| `first.next()` twice | `count = 2` | `count = 10` |
| `second.next()` | `count = 2` | `count = 11` |

This pattern enables factories, callbacks, module-private state, currying, and memoization. It also creates a lifetime obligation: if a long-lived closure captures a large object or DOM subtree, that object stays reachable. Capture the smallest state needed, and unsubscribe or discard callbacks when their owner is finished.

<a id="q12"></a>

## Q12. How is `this` determined in JavaScript?

### Requirement

For a normal function, inspect the call expression. For an arrow function, inspect where it was created because arrows use lexical `this`. A practical precedence model is:

```mermaid
flowchart TD
    Call{How is a normal function called?}
    Call -->|new F| New[New instance is this]
    Call -->|bound function| Bound[Bound this wins]
    Call -->|call or apply| Explicit[Explicit thisArg]
    Call -->|obj.method| Receiver[Object before dot is this]
    Call -->|plain F in strict mode| Plain[undefined]
    Call -->|arrow function| Lexical[Use enclosing this]
```

`new` has higher precedence than a bound `this`: when a constructable bound function is invoked with `new`, the newly created instance becomes `this`; the bound arguments still participate.

### Example 12 — call shape, not storage location, chooses the receiver

```javascript
"use strict";

const account = {
  owner: "Ada",
  readOwner() { return this?.owner; },
  makeArrow() { return () => this.owner; }
};

const detached = account.readOwner;
const arrow = account.makeArrow();

console.log(account.readOwner());          // Ada: method call
console.log(detached());                   // undefined: plain strict call
console.log(detached.call({ owner: "Lin" })); // Lin: explicit receiver
console.log(arrow.call({ owner: "Ignored" })); // Ada: lexical this
```

| Expression | Rule | Effective `this` |
|---|---|---|
| `account.readOwner()` | implicit receiver | `account` |
| `detached()` | strict plain call | `undefined` |
| `detached.call(other)` | explicit binding | `other` |
| `arrow.call(other)` | arrow ignores rebinding | `account` captured by `makeArrow()` |

Avoid arrow properties for methods that need a dynamic receiver. Conversely, an arrow created inside a method is useful for a callback that must keep the method's receiver.

<a id="q13"></a>

## Q13. How do `call`, `apply`, and `bind` differ?

### Requirement

- `fn.call(thisArg, a, b)` invokes immediately with positional arguments.
- `fn.apply(thisArg, [a, b])` invokes immediately with an array-like argument list.
- `fn.bind(thisArg, a)` returns a new function for later calls and may pre-fill leading arguments.

```javascript
function format(prefix, suffix) {
  return `${prefix}${this.name}${suffix}`;
}

const user = { name: "Sam" };
console.log(format.call(user, "[", "]"));      // [Sam]
console.log(format.apply(user, ["<", ">"])); // <Sam>

const greetSam = format.bind(user, "Hello ");
console.log(greetSam("!"));                    // Hello Sam!
```

Spread syntax often replaces `apply` for ordinary argument forwarding: `fn(...args)`. Binding an arrow cannot replace its lexical `this`, and rebinding an already-bound function does not replace its original bound receiver.

<a id="q14"></a>

## Q14. How does the prototype chain resolve a property?

### Requirement

Property lookup starts with the object itself. If the property is absent, lookup follows the object's internal `[[Prototype]]` link until it finds the first matching property or reaches `null`. An own property can shadow an inherited property without changing the prototype.

```mermaid
flowchart LR
    Instance["employee\nown: name"] --> Proto["Employee.prototype\nown: greet"]
    Proto --> Base["Person.prototype\nown: describe"]
    Base --> Object["Object.prototype"]
    Object --> Nil[null]
```

### Example 13 — lookup, shadowing, and deletion

```javascript
const personMethods = {
  describe() { return `person:${this.name}`; }
};

const employeeMethods = Object.create(personMethods);
employeeMethods.describe = function () {
  return `employee:${this.name}`;
};

const employee = Object.create(employeeMethods);
employee.name = "Mia";

console.log(employee.describe()); // employee:Mia
delete employeeMethods.describe;
console.log(employee.describe()); // person:Mia
console.log(Object.hasOwn(employee, "describe")); // false
```

Lookup trace:

| State | Instance | `employeeMethods` | `personMethods` | Result |
|---|---|---|---|---|
| before delete | absent | own `describe` | own `describe` | first inherited method wins |
| after delete | absent | absent | own `describe` | lookup continues to base |

`Constructor.prototype` is the object assigned as the prototype of instances created by `new Constructor()`. It is not the internal prototype of the constructor function itself.

<a id="q15"></a>

## Q15. Are classes fundamentally different from prototypes?

### Requirement

Class syntax adds declarative constructors, `extends`, `super`, private elements, strict semantics, and a prohibition on calling a class without `new`. Instance method lookup still uses prototype links; methods declared in the class body live on the class's `.prototype`, not as a separate function copy on every instance.

```javascript
class Person {
  constructor(name) { this.name = name; }
  describe() { return `person:${this.name}`; }
}

class Employee extends Person {
  describe() { return `employee:${this.name}`; }
}

const employee = new Employee("Mia");
console.log(employee.describe()); // employee:Mia
console.log(Object.getPrototypeOf(employee) === Employee.prototype); // true
console.log(Object.getPrototypeOf(Employee.prototype) === Person.prototype); // true
console.log(Object.hasOwn(employee, "describe")); // false
```

Do not reduce classes to “only syntactic sugar” in an interview. Their method delegation is prototype-based, but class declarations also impose observable semantics such as strict mode, non-enumerable prototype methods, TDZ behavior, and `new`-only construction.

<a id="q16"></a>

## Q16. When should you choose a declaration, expression, or arrow function?

### Requirement

Choose by semantics:

| Form | Availability before definition | Own `this` / `arguments` | Constructable with `new` | Typical use |
|---|---|---|---|---|
| function declaration | initialized during declaration instantiation | yes | normally yes | named reusable operation |
| function expression assigned to `const` | follows the lexical binding's TDZ | yes | normally yes | value selected/created conditionally |
| arrow function | follows its variable's declaration rule | no; resolves lexically | no | callback that inherits surrounding context |

```javascript
console.log(declared(2)); // 4
function declared(x) { return x * 2; }

const expressed = function square(x) { return x * x; };
const doubled = [1, 2, 3].map(x => x * 2);

console.log(expressed(3)); // 9
console.log(doubled);      // [2, 4, 6]
// new (() => {});        // TypeError: arrow is not a constructor
```

A named function expression improves stack traces and permits internal recursion without leaking that name to the outer scope.

<a id="q17"></a>

## Q17. How does currying differ from partial application?

### Requirement

Currying changes a function of multiple parameters into a chain that accepts one parameter at each step. Partial application fixes any subset—commonly a leading prefix—of a function's arguments and returns a function that accepts the remainder. Both use closures, but they are different transformations.

### Example 14 — compare the resulting call shapes

```javascript
const curryPrice = taxRate => discount => amount =>
  amount * (1 - discount) * (1 + taxRate);

function price(taxRate, discount, amount) {
  return amount * (1 - discount) * (1 + taxRate);
}

const georgiaPrice = curryPrice(0.08);     // curried: still one arg at a time
const salePrice = price.bind(null, 0.08, 0.10); // partial: two args fixed

console.log(georgiaPrice(0.10)(100)); // 97.2
console.log(salePrice(100));          // 97.2
```

Currying helps composition and reusable staged configuration. Partial application is often the more direct choice when an existing multi-parameter API should be specialized.

<a id="q18"></a>

## Q18. What makes memoization correct—and dangerous?

### Requirement

Memoization reuses a previous result for an equivalent input. Correctness requires a stable equivalence/key strategy and a result that does not silently depend on changing external state. Production design additionally needs a memory/freshness policy: size bounds, TTL, explicit invalidation, weak keys, or an owner lifetime.

```javascript
function memoizeUnary(fn, { maxEntries = 2 } = {}) {
  const cache = new Map();
  return function memoized(input) {
    if (cache.has(input)) {
      const value = cache.get(input);
      cache.delete(input);
      cache.set(input, value); // refresh recency
      return value;
    }
    const value = fn(input);
    cache.set(input, value);
    if (cache.size > maxEntries) {
      cache.delete(cache.keys().next().value);
    }
    return value;
  };
}

let computations = 0;
const square = memoizeUnary(x => { computations += 1; return x * x; });
console.log(square(4), square(4), computations); // 16 16 1
```

The `has` check is essential because cached results may legitimately be `0`, `false`, or `undefined`. A `Map` uses object identity for object keys; structurally equal object literals are different keys. JSON stringification is not a universal structural key because order, unsupported values, cycles, and serialization cost matter. Memoizing time-, user-, locale-, permission-, or database-dependent work without including those dependencies and an invalidation policy returns stale or unsafe results.

## Asynchronous JavaScript: separate scheduling from execution

JavaScript promises do not create a second JavaScript thread. The host runs tasks such as timer callbacks; ECMAScript promise reactions run as jobs, which browsers integrate with the microtask queue. A useful browser model is:

```mermaid
flowchart TD
    Task[Run one task to completion] --> Checkpoint[Perform microtask checkpoint]
    Checkpoint --> Empty{Microtask queue empty?}
    Empty -->|no| Microtask[Run oldest microtask]
    Microtask --> Empty
    Empty -->|yes| Render[Possible rendering update]
    Render --> Next[Choose another runnable task]
```

This is a scheduling model, not a promise that every task source is globally FIFO. A zero-millisecond timer means “eligible after the minimum delay,” not “execute immediately.” Microtasks queued by other microtasks are drained in the same checkpoint; an unbounded microtask chain can therefore delay rendering and later tasks.

<a id="q19"></a>

## Q19. How do you predict event-loop output?

### Requirement

First run the current script to completion. Record promise reactions and `queueMicrotask` callbacks in registration order. At the checkpoint, keep draining microtasks—including newly queued ones—before a later timer task can run.

### Example 15 — a nested microtask joins the same checkpoint

```javascript
const output = [];

output.push("A");
setTimeout(() => output.push("timer"), 0);
Promise.resolve().then(() => {
  output.push("promise-1");
  queueMicrotask(() => output.push("nested"));
});
queueMicrotask(() => output.push("microtask-1"));
output.push("B");

// after the timer: A, B, promise-1, microtask-1, nested, timer
```

Trace:

| Step | Running work | Microtask queue after step | Output |
|---:|---|---|---|
| 1 | current script | — | `A` |
| 2 | register timer task | — | `A` |
| 3 | register promise reaction | `promise-1` | `A` |
| 4 | `queueMicrotask` | `promise-1`, `microtask-1` | `A` |
| 5 | finish script | unchanged | `A, B` |
| 6 | run promise reaction | `microtask-1`, `nested` | `A, B, promise-1` |
| 7 | drain remaining microtasks | empty | `…, microtask-1, nested` |
| 8 | timer task becomes selected | empty | `…, timer` |

Do not say “promises always beat timers” without scope. A promise reaction registered inside a later timer obviously cannot run before the code that registers it. Analyze one task and its following checkpoint at a time.

<a id="q20"></a>

## Q20. What do `async` and `await` actually do?

### Requirement

Calling an `async` function returns a promise. `await expression` obtains a promise from the expression, suspends only that async function, and schedules its continuation after settlement. The JavaScript thread is free to run other work. Even an already-fulfilled promise does not make the continuation run inline with the current stack.

### Example 16 — start independent work before awaiting it

```javascript
async function loadDashboard(loadUser, loadOrders) {
  const userPromise = loadUser();     // starts now
  const ordersPromise = loadOrders(); // starts now
  const [user, orders] = await Promise.all([userPromise, ordersPromise]);
  return { user, orders };
}
```

Compare the timelines:

| Shape | Timeline | Approximate wall time for 80 ms + 120 ms jobs |
|---|---|---:|
| serial | start user → await → start orders → await | 200 ms |
| concurrent | start both → await both | 120 ms |

Concurrency is correct only when the operations are independent and the resource budget permits it. If the second operation needs the first result, serial `await` expresses the real dependency. For a large input collection, use a concurrency limit rather than launching every operation at once.

<a id="q21"></a>

## Q21. When should you use `Promise.all`, `allSettled`, `race`, or `any`?

### Requirement

Choose the success condition before choosing the API. Each method accepts an iterable and returns a new promise; none of them cancels losing or rejected input operations.

| Combinator | Returned promise fulfills when | Rejects when | Output ordering / reason |
|---|---|---|---|
| `Promise.all` | every input fulfills | first observed rejection | values stay in input order |
| `Promise.allSettled` | every input settles | not because an input rejects | result records stay in input order |
| `Promise.race` | first input settles successfully | first input settles by rejection | first settlement wins |
| `Promise.any` | first input fulfills | every input rejects | rejects with `AggregateError` |

### Example 17 — select semantics, then handle the non-winners

```javascript
const audit = await Promise.allSettled([
  Promise.resolve({ region: "east", latency: 42 }),
  Promise.reject(new Error("west unavailable"))
]);
// [{ status: "fulfilled", value: ... }, { status: "rejected", reason: ... }]

const firstHealthy = await Promise.any([
  Promise.reject(new Error("east unavailable")),
  Promise.resolve({ region: "west", latency: 55 })
]);
// { region: "west", latency: 55 }
```

Important edge cases:

- `Promise.all([])` and `Promise.allSettled([])` fulfill with `[]`.
- `Promise.any([])` rejects with an `AggregateError` because no input can fulfill.
- `Promise.race([])` remains pending because no input can settle it.
- “Fail fast” does not mean “stop the other jobs.” Connect explicit cancellation when wasted work or stale effects matter.

<a id="q22"></a>

## Q22. Where should asynchronous errors be handled?

### Requirement

Await or return every promise so its failure remains in the caller's chain. Catch an error only where the code can add useful context, recover, translate it into a domain result, or perform cleanup. Otherwise let it propagate to the operation's owner.

```javascript
async function loadUser(fetchImpl, id) {
  try {
    const response = await fetchImpl(`/api/users/${encodeURIComponent(id)}`);
    if (!response.ok) {
      throw new Error(`HTTP ${response.status}`);
    }
    return await response.json();
  } catch (error) {
    throw new Error(`Could not load user ${id}`, { cause: error });
  }
}
```

`fetch()` rejects for failures such as a network error or abort, but an HTTP 404 or 500 normally arrives as a `Response`; checking `response.ok` creates the application-level failure. Preserve the original error as `cause` rather than replacing all diagnostic information.

One subtle boundary:

```javascript
async function notCaught() {
  try {
    return failingOperation(); // rejection occurs after this try block returns
  } catch {
    return "fallback";
  }
}

async function caughtHere() {
  try {
    return await failingOperation(); // rejection is thrown at this await
  } catch {
    return "fallback";
  }
}
```

Both functions return promises; the difference is where the rejection is observed. Use `finally` for cleanup that must run for either outcome, but do not return from `finally` because that can replace the original result or error.

<a id="q23"></a>

## Q23. How do you implement debounce without losing the latest call?

### Requirement

Debounce is the “after silence” policy: every call replaces the pending timer, and one trailing invocation uses the latest arguments and receiver. This is appropriate when intermediate events are disposable, such as autocomplete text or resize recomputation.

```javascript
function debounce(fn, wait) {
  let timerId;
  return function debounced(...args) {
    const receiver = this;
    clearTimeout(timerId);
    timerId = setTimeout(() => {
      timerId = undefined;
      fn.apply(receiver, args);
    }, wait);
  };
}
```

Dry run for calls at 0, 40, and 90 ms with `wait = 100`:

| Time | Event | Pending deadline | Invocation |
|---:|---|---:|---|
| 0 | call `"a"` | 100 | — |
| 40 | call `"ab"`; cancel prior timer | 140 | — |
| 90 | call `"abc"`; cancel prior timer | 190 | — |
| 190 | quiet period completes | none | `fn("abc")` |

Production wrappers may also expose `cancel()` and `flush()`. Define leading/trailing behavior explicitly instead of combining options until calls can fire twice at one boundary.

<a id="q24"></a>

## Q24. How does throttle differ from debounce?

### Requirement

Throttle is the “at most once per interval” policy. It suits continuous signals such as scroll sampling when periodic progress matters. A leading-plus-trailing version invokes immediately, then remembers the latest suppressed call for the end of the window.

```mermaid
flowchart LR
    Need{Are intermediate updates useful?}
    Need -->|no; wait for quiet| Debounce[Debounce trailing call]
    Need -->|yes; cap frequency| Throttle[Throttle leading or trailing]
    Debounce --> Search[Search or resize result]
    Throttle --> Scroll[Scroll or pointer sampling]
```

```javascript
function throttle(fn, wait) {
  let lastInvoke = -Infinity;
  let timerId;
  let pending;

  return function throttled(...args) {
    const now = Date.now();
    pending = { receiver: this, args };
    const remaining = wait - (now - lastInvoke);

    const invoke = () => {
      lastInvoke = Date.now();
      timerId = undefined;
      const call = pending;
      pending = undefined;
      fn.apply(call.receiver, call.args);
    };

    if (remaining <= 0) {
      if (timerId !== undefined) clearTimeout(timerId);
      invoke();
    } else if (timerId === undefined) {
      timerId = setTimeout(invoke, remaining);
    }
  };
}
```

| Policy | Burst at 0, 40, 90 ms; wait 100 ms | Best fit |
|---|---|---|
| trailing debounce | only latest call at 190 ms | final value matters |
| leading + trailing throttle | first call at 0 ms, latest suppressed call near 100 ms | periodic progress matters |

Timers are host scheduling requests, not hard real-time deadlines. Long tasks can delay either wrapper. For visual updates, `requestAnimationFrame` may be a better pacing mechanism because it aligns work with rendering rather than an arbitrary millisecond interval.

<a id="q25"></a>

## Q25. How do you cancel a `fetch()` request, and what problem does cancellation solve?

### Requirement

Create an `AbortController`, pass its `signal` to `fetch()`, and call `abort()` from the operation that owns the request. Cancellation saves work and gives an unmounted view, superseded search, timeout, or explicit user action a defined cleanup path. It does **not** by itself prove that an older server operation was rolled back, so idempotency and stale-result guards may still matter.

### Example 18 — latest search owns the active request

```javascript
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
        { signal: controller.signal }
      );
      if (!response.ok) throw new Error(`HTTP ${response.status}`);
      const data = await response.json();
      return mine === generation ? data : undefined;
    } catch (error) {
      if (error.name === "AbortError") return undefined;
      throw error;
    } finally {
      if (mine === generation) active = undefined;
    }
  };
}
```

```mermaid
sequenceDiagram
    participant UI
    participant Owner as Request owner
    participant Net as Fetch
    UI->>Owner: search("cat")
    Owner->>Net: request A + signal A
    UI->>Owner: search("caterpillar")
    Owner-->>Net: abort A
    Owner->>Net: request B + signal B
    Net-->>Owner: B succeeds
    Owner-->>UI: publish B only
```

Trace:

| Step | Generation | Active signal | Observable result |
|---:|---:|---|---|
| search `"cat"` | 1 | A | A pending |
| search `"caterpillar"` | 2 | B | A rejects with `AbortError` |
| B completes | 2 | none after cleanup | B may update the UI |

An abort is an expected control-flow outcome, not a generic network failure. Do not show an alarming error toast for every `AbortError`. Conversely, do not swallow real HTTP, parsing, or network failures. In current runtimes, `AbortSignal.timeout()` and `AbortSignal.any()` can express timeouts and combined ownership, but explicit controllers remain the portable mental model.

<a id="q26"></a>

## Q26. Explain event capturing, bubbling, and delegation

### Requirement

An event path is computed from the target through its ancestors. Capture listeners run toward the target, target listeners run at the target, and bubbling listeners run back toward the root when the event bubbles. `event.target` is where dispatch began; `event.currentTarget` is the node whose listener is currently running.

Delegation attaches one listener to a stable ancestor and derives the intended descendant action from the event. It reduces listener churn and also handles children added later.

```mermaid
flowchart TD
    Doc[document capture] --> List[ul capture]
    List --> Button[button target]
    Button --> ListBubble[ul bubble]
    ListBubble --> DocBubble[document bubble]
```

### Example 19 — delegate at a boundary, not merely at a selector

```javascript
list.addEventListener("click", event => {
  const button = event.target.closest("[data-delete]");
  if (!button || !list.contains(button)) return;

  const item = button.closest("[data-item-id]");
  if (!item || !list.contains(item)) return;
  removeItem(item.dataset.itemId);
});
```

The containment checks prevent a selector match outside the component boundary from being accepted. `closest()` also handles clicks on an icon nested inside the button. For Shadow DOM, retargeting can change `target`; `event.composedPath()` is the explicit path tool when an event is composed across a shadow boundary.

| API/property | Meaning | Does not mean |
|---|---|---|
| `target` | original/retargeted dispatch target visible here | node whose listener is running |
| `currentTarget` | node for the listener currently executing | deepest clicked node |
| `stopPropagation()` | stop later traversal to other nodes | cancel link/form default behavior |
| `preventDefault()` | cancel a cancelable default action | stop propagation |

<a id="q27"></a>

## Q27. What are layout/reflow and repaint, and how do you avoid layout thrashing?

### Requirement

Style calculation determines computed styles; layout determines geometry; paint records visual drawing; compositing assembles layers. A change need not trigger every stage: changing geometry can require layout and paint, while a compositor-friendly transform may avoid layout and paint for that frame. Treat those as browser-dependent optimization opportunities, not guarantees.

```mermaid
flowchart LR
    JS[DOM or style change] --> Style[Style calculation]
    Style --> Layout[Layout geometry]
    Layout --> Paint[Paint records]
    Paint --> Composite[Composite layers]
    Style -. transform or opacity may skip .-> Composite
```

### Example 20 — batch reads, then writes

```javascript
// Bad: each write can invalidate geometry before the next read.
for (const row of rows) {
  row.style.width = `${container.offsetWidth / 2}px`;
}

// Better: read once, then perform the writes together.
const width = container.offsetWidth / 2;
requestAnimationFrame(() => {
  for (const row of rows) row.style.width = `${width}px`;
});
```

The problematic pattern is an alternating sequence of layout-invalidating **write → geometry read → write → geometry read**. A geometry API such as `offsetWidth` or `getBoundingClientRect()` may force the browser to make pending layout information current. Read the necessary measurements together, compute in ordinary JavaScript, then write together. `requestAnimationFrame()` aligns a write batch with a rendering opportunity; it does not make expensive work free, and a callback can still miss the frame budget.

Dry run for four rows:

| Plan | Geometry reads | DOM writes | Potential forced layout boundaries |
|---|---:|---:|---:|
| interleaved loop | 4 | 4 | up to 4 |
| batched read then writes | 1 | 4 | 1 measurement boundary |

Measure actual behavior with browser performance tooling because engines can coalesce work and layer promotion consumes memory.

<a id="q28"></a>

## Q28. Compare `localStorage`, `sessionStorage`, cookies, and IndexedDB

### Requirement

Choose storage by data shape, lifetime, access pattern, and security—not by memorized capacity numbers. Browser quotas and eviction policies vary, and none of these APIs is a safe vault for secrets exposed to script.

| Mechanism | API/model | Lifetime/scope | Network behavior | Good fit / principal warning |
|---|---|---|---|---|
| `localStorage` | synchronous string key/value | origin; persists until cleared/evicted | not sent automatically | tiny preferences; blocks the main thread and needs serialization |
| `sessionStorage` | synchronous string key/value | origin plus top-level browsing context; tab session | not sent automatically | per-tab workflow state; not cross-tab persistence |
| cookie | small string name/value plus attributes | domain/path and expiry rules | matching cookies are attached to requests | server sessions and request state; use `Secure`, `HttpOnly`, and deliberate `SameSite` |
| IndexedDB | asynchronous object stores, indexes, transactions | origin; persists subject to policy | not sent automatically | larger structured/offline data; schema/version and transaction discipline required |

### Example 21 — make the policy explicit

```javascript
const storagePolicy = {
  theme: "localStorage",
  checkoutDraftForThisTab: "sessionStorage",
  serverSessionIdentifier: "Secure HttpOnly SameSite cookie",
  offlineCatalog: "IndexedDB"
};
```

Store the minimum data needed. XSS can read script-accessible Web Storage and IndexedDB. An `HttpOnly` cookie blocks direct JavaScript reads, but the browser may still attach it to requests, so CSRF defenses and server-side authorization remain necessary. Client storage is a cache or persistence mechanism, not the source of truth for permissions.

<a id="q29"></a>

## Q29. What are the Same-Origin Policy and CORS?

### Requirement

An origin is the tuple **scheme + host + port**. The Same-Origin Policy restricts how a document or script from one origin can read/interact with resources from another. Cross-origin requests can still be sent in several cases; the important default restriction is often whether script may read the response.

CORS is the Fetch protocol by which a server opts selected origins into cross-origin response sharing. A non-simple request can trigger a preflight `OPTIONS` request before the actual request. CORS is not authentication, authorization, or a general CSRF defense.

```mermaid
sequenceDiagram
    participant App as https://app.example
    participant API as https://api.example
    App->>API: OPTIONS with Origin and requested method/headers
    API-->>App: allowed origin, method, headers
    App->>API: actual request with Origin
    API-->>App: response + matching allow-origin
    Note over App: Browser exposes response only if checks pass
```

For a credentialed browser request, the client must opt in (for example, `credentials: "include"`), the server must return a specific allowed origin rather than `*`, and it must allow credentials. `mode: "no-cors"` is not a bypass: it deliberately produces an opaque response that JavaScript cannot inspect.

| Situation | Request may reach server? | JavaScript can read response? |
|---|---|---|
| same origin | normally yes | normally yes |
| allowed CORS response | yes | yes, within exposed-header rules |
| disallowed cross-origin response | may do so | no |
| failed preflight | actual request is not sent | no |
| `no-cors` request | limited request may be sent | opaque response; no body/status access |

Validate authorization on the server for every request. A successful CORS check only says the browser may share the response with that origin.

<a id="q30"></a>

## Q30. How do XSS and CSRF differ, and how are they prevented?

### Requirement

**Cross-site scripting (XSS)** gets attacker-controlled content interpreted in a trusted page's execution context. **Cross-site request forgery (CSRF)** causes a victim's browser to send an unwanted state-changing request using ambient authority such as cookies. They have different primary controls, and an XSS flaw can often defeat in-page CSRF defenses.

| Threat | Broken boundary | Primary controls | Common mistake |
|---|---|---|---|
| XSS | untrusted data becomes executable markup, script, URL, CSS, or DOM | context-appropriate output encoding; safe DOM sinks; sanitization for intentionally allowed HTML; CSP as defense-in-depth | one generic escaping function for every context |
| CSRF | server accepts a cross-site state change backed by ambient credentials | anti-CSRF token; `SameSite` cookies; Origin/Referer or Fetch Metadata validation; no state changes through safe methods | assuming CORS alone blocks forged requests |

Safer DOM construction keeps data as text:

```javascript
const message = document.createElement("p");
message.textContent = untrustedMessage; // data, not HTML source
container.replaceChildren(message);
```

If product requirements intentionally accept HTML, use a maintained sanitizer configured for that exact context; do not repair it with regular expressions. On the server, protect state-changing routes with a framework-supported token pattern, intentional cookie attributes, origin signals, and authorization. `SameSite` is valuable defense-in-depth but compatibility, sibling domains, and application flows still require a complete policy.

Security trace:

1. The browser attaches a session cookie to an eligible request.
2. The server authenticates the session **and** verifies authorization.
3. For a state change, the server also verifies the anti-CSRF/origin policy.
4. Returned untrusted data is encoded for its output context; it is never concatenated into an executable sink.
5. CSP limits impact if a markup injection bug remains, but does not replace correct data handling.

<a id="q31"></a>

## Q31. What is the difference between `preventDefault()` and `stopPropagation()`?

### Requirement

They control independent parts of event dispatch:

- `event.preventDefault()` requests cancellation of the event's **default action**, such as following a link or submitting a form. It does not stop listeners on ancestors.
- `event.stopPropagation()` stops further capture/bubble traversal, but does not cancel the default action and does not stop other listeners already registered on the current target.
- `event.stopImmediatePropagation()` also prevents later listeners on the current target from running.

Cancellation works only when `event.cancelable` is true. A listener registered with `{ passive: true }` promises not to cancel, so its `preventDefault()` call has no effect. When later code needs to know whether cancellation won, inspect `event.defaultPrevented`. Returning `false` is not a generic substitute in `addEventListener()`.

### Example 22 — intercept only an ordinary same-origin navigation

```javascript
document.addEventListener("click", event => {
  const link = event.target.closest("a[href]");
  if (!link || !document.contains(link)) return;

  const destination = new URL(link.href, location.href);
  const modified = event.button !== 0 || event.metaKey || event.ctrlKey ||
    event.shiftKey || event.altKey;

  if (modified || link.target || link.hasAttribute("download") ||
      destination.origin !== location.origin) return;

  event.preventDefault();
  router.navigate(destination.pathname + destination.search + destination.hash);
  // Deliberately allow bubbling: an ancestor may record the navigation intent.
});
```

Decision trace:

```mermaid
flowchart TD
    Click[Link click] --> Native{Modified, external,<br/>download, or target?}
    Native -- yes --> Browser[Keep browser default]
    Native -- no --> Cancel[Cancel navigation default]
    Cancel --> Route[Route in the application]
    Route --> Bubble[Allow propagation unless isolation is required]
```

Do not stop propagation merely to avoid duplicate handling; a narrow selector, component-boundary check, or `defaultPrevented` convention is usually easier to compose.

<a id="q32"></a>

## Q32. What is semantic HTML, and why does it matter?

### Requirement

Semantic HTML chooses elements for their meaning and behavior, not their default appearance. Native landmarks, headings, lists, links, buttons, tables, and form controls expose useful structure to browsers and assistive technology while providing tested keyboard/focus behavior. CSS can change presentation without changing that contract.

Use a link for navigation and a button for an action. Prefer a real `<button>` over a clickable `<div role="button">`: the latter still needs keyboard activation, focus behavior, disabled-state handling, and other semantics implemented correctly. Semantics can also help search and maintenance, but they are not an automatic ranking trick.

### Example 23 — a small page with native contracts

```html
<header>
  <a href="/" aria-label="Acme home">Acme</a>
  <nav aria-label="Primary">
    <a href="/docs">Documentation</a>
  </nav>
</header>
<main>
  <article>
    <h1>Deployment checklist</h1>
    <p>Verify the release before promotion.</p>
    <button type="button" id="run-checks">Run checks</button>
  </article>
</main>
<footer><small>Last reviewed 2026-10-15</small></footer>
```

The document has one main content region, a named navigation region, a meaningful heading, a navigation link, and an action button. Do not add redundant ARIA roles to elements that already have the needed native semantics; add names only where the visible context is insufficient.

```mermaid
flowchart TD
    Page[Document] --> Header[Banner]
    Header --> Nav[Primary navigation]
    Page --> Main[Main]
    Main --> Article[Article: Deployment checklist]
    Page --> Footer[Content information]
```

<a id="q33"></a>

## Q33. What is the difference between `async` and `defer` on scripts?

### Requirement

For parser-inserted classic external scripts, both attributes let fetching proceed while HTML parsing continues, but execution differs.

| Script | Fetch | Execution | Relative order | `DOMContentLoaded` |
|---|---|---|---|---|
| classic, no attribute | blocks parsing while fetched and run | immediately at the parser position | document order | parsing continues afterward |
| classic `async` | parallel with parsing | as soon as the script is ready | not guaranteed | does not wait for it unless it happens to run first |
| classic `defer` | parallel with parsing | after parsing, before the event | document order | waits for deferred scripts |
| module | module graph fetched in parallel | deferred by default after parsing | dependency graph controls readiness | waits for default-deferred modules |

An inline classic script does not gain deferred behavior from `defer`. Adding `async` to a module opts that module graph into async-style execution when ready. Dynamically inserted scripts have separate rules, so do not infer their ordering from this table.

### Example 24 — choose ordering from dependencies

```html
<script async src="/analytics.js"></script>
<script defer src="/config.js"></script>
<script defer src="/application.js"></script>
<script type="module" src="/enhance.js"></script>
```

Use `async` for independent code whose timing relative to the page is unimportant. Use `defer` when classic scripts depend on document order or a parsed DOM. Modules should express JavaScript dependencies with `import`, not parallel `<script>` tags whose network completion order is accidental.

```mermaid
sequenceDiagram
    participant P as HTML parser
    participant N as Network
    participant J as JavaScript
    P->>N: request async and deferred scripts
    P->>P: continue parsing
    N-->>J: async script ready
    J->>J: run async script when ready
    P->>P: finish parsing
    N-->>J: deferred scripts ready
    J->>J: run deferred scripts in document order
    J->>P: DOMContentLoaded may fire
```

The diagram expresses ordering constraints, not guaranteed download durations.

<a id="q34"></a>

## Q34. How do you build an accessible form?

### Requirement

Start with native controls and visible labels. Associate each label with its control, group related choices with `<fieldset>` and `<legend>`, choose the correct input type and autocomplete token, and explain formats or requirements before submission. Do not use a placeholder as the only label.

On failure, identify the field in text, set `aria-invalid="true"`, associate its message with `aria-describedby`, and move focus to an error summary or the first invalid control according to a documented policy. Do not rely on color alone. Server validation remains authoritative even when client validation improves feedback.

### Example 25 — a form and profile image that keep their contracts

```html
<form id="signup" novalidate>
  <h2>Create your profile</h2>
  <p id="required-note">Fields marked “required” must be completed.</p>

  <div>
    <label for="email">Work email (required)</label>
    <input id="email" name="email" type="email" autocomplete="email"
           required aria-describedby="email-hint email-error">
    <p id="email-hint">Use the address where release alerts should arrive.</p>
    <p id="email-error" role="alert" hidden>Enter a valid email address.</p>
  </div>

  <fieldset>
    <legend>Alert frequency</legend>
    <label><input type="radio" name="frequency" value="daily" required> Daily</label>
    <label><input type="radio" name="frequency" value="weekly"> Weekly</label>
  </fieldset>
  <button type="submit">Create profile</button>
</form>
```

If validation fails, reveal the error, set `email.setAttribute("aria-invalid", "true")`, and focus the chosen error destination. When the value becomes valid, remove the invalid state and stale message. Test the completed flow with a keyboard, browser accessibility inspection, and representative assistive technology rather than treating markup review as sufficient.

<a id="q35"></a>

## Q35. What does the viewport meta element do?

### Requirement

Mobile browsers may otherwise use a wider default layout viewport and scale the page down. This element asks for a layout viewport that follows the device width and establishes a 1:1 initial CSS-pixel scale:

```html
<meta name="viewport" content="width=device-width, initial-scale=1">
```

It enables responsive CSS to operate against the intended viewport; it does not make a fixed-width layout responsive by itself. The **layout viewport** is the coordinate space used for layout, while the **visual viewport** is the portion currently visible and can change with zoom or an on-screen keyboard.

Avoid `user-scalable=no` and restrictive `maximum-scale` values: zoom is an important accessibility mechanism, and browsers may ignore such restrictions. Prefer fluid sizing, wrapping, media/container queries, and testing at zoomed and narrow viewports.

<a id="q36"></a>

## Q36. How do `srcset`, `sizes`, and `<picture>` support responsive images?

### Requirement

Use resolution switching when the same composition works at different pixel densities or layout widths. With width descriptors, `srcset` states each candidate's intrinsic width and `sizes` states the image's expected rendered slot width. The browser combines that information with device pixel ratio and other conditions to choose a candidate; application code should not hard-code the choice.

Use `<picture>` when the source itself should change, such as a tighter crop at narrow widths or a preferred format with fallback. Keep an `<img>` as the final fallback and as the owner of the alternative text. Supplying intrinsic `width` and `height` gives the browser an aspect ratio before download and helps prevent layout shift.

```html
<picture>
  <source media="(max-width: 40rem)"
          srcset="/team-close-480.webp 480w, /team-close-960.webp 960w"
          sizes="92vw" type="image/webp">
  <source srcset="/team-800.webp 800w, /team-1600.webp 1600w"
          sizes="(max-width: 70rem) 92vw, 70rem" type="image/webp">
  <img src="/team-800.jpg"
       srcset="/team-800.jpg 800w, /team-1600.jpg 1600w"
       sizes="(max-width: 70rem) 92vw, 70rem"
       width="1600" height="900"
       alt="The release team reviewing a deployment dashboard">
</picture>
```

Trace for a 400 CSS-pixel slot at device-pixel ratio 2:

1. The teaching estimate is `400 × 2 = 800` source pixels.
2. A compatible source list is chosen by `<picture>` media/type conditions.
3. The browser may choose an 800w candidate, but cache, network, zoom, and implementation policy can justify another candidate.
4. The selected image is decoded into the reserved 16:9 box.

Do not mix `w` and `x` descriptors in one `srcset`. Use `1x`/`2x` descriptors for a fixed CSS-size asset; use `w` plus an accurate `sizes` list for a fluid layout. `sizes` describes the rendered slot, not which file the author wants.

<a id="q37"></a>

## Q37. How do `hidden`, `aria-hidden`, `display: none`, and `visibility: hidden` differ?

### Requirement

Choose along three separate axes: whether the content generates layout boxes, whether it is painted/interactive, and whether it is exposed to accessibility APIs. “Invisible” is not one state.

| Mechanism | Layout space | Visual/interactable | Accessibility exposure | Appropriate intent |
|---|---|---|---|---|
| HTML `hidden` | normally none | no | normally removed | content is not currently relevant to any user |
| CSS `display: none` | none; no boxes generated | no | normally removed | presentation rule removes a subtree from layout/rendering |
| CSS `visibility: hidden` | retained | not painted or interactive | not rendered to speech/navigation under the CSS model | temporarily hide while preserving layout geometry |
| `aria-hidden="true"` | unchanged | unchanged | subtree is hidden from assistive technology | remove redundant/decorative content from the accessibility tree only |

`aria-hidden` does not visually hide anything. Its state is inherited by descendants, and `aria-hidden="false"` on a child cannot restore a child whose ancestor is hidden. Never place `aria-hidden="true"` on a focusable element or an ancestor containing focusable controls: keyboard focus could reach an element that assistive technology cannot perceive.

The HTML state `hidden="until-found"` is a distinct current feature. Its content remains discoverable by find-in-page and fragment navigation; the browser reveals the ancestor before scrolling to a match. It is not interchangeable with an ordinary collapsed application panel.

### Example 26 — one source of truth for a disclosure

```html
<button id="filters-toggle" type="button"
        aria-expanded="false" aria-controls="filters-panel">
  <svg aria-hidden="true" focusable="false" viewBox="0 0 16 16">
    <path d="M2 4h12M4 8h8M6 12h4"></path>
  </svg>
  Filters
</button>
<section id="filters-panel" hidden>
  <h2>Filter results</h2>
  <label><input type="checkbox" name="remote"> Remote</label>
</section>
```

```javascript
const toggle = document.querySelector("#filters-toggle");
const panel = document.querySelector("#filters-panel");

toggle.addEventListener("click", () => {
  const open = toggle.getAttribute("aria-expanded") === "true";
  toggle.setAttribute("aria-expanded", String(!open));
  panel.hidden = open;
});
```

The panel is either relevant, visible, focusable, and exposed—or absent from all of those modes. Only the redundant icon is `aria-hidden`. Do not add `aria-hidden` to the panel as a second, independently mutable visibility state.

```mermaid
flowchart TD
    State{Panel relevant?}
    State -- no --> Hidden[hidden: no layout, paint, focus, or AT exposure]
    State -- yes --> Shown[render panel and expose native controls]
    Shown --> Decor[aria-hidden only on redundant icon]
```

<a id="q38"></a>

## Q38. What is native lazy loading in HTML?

### Requirement

`loading="lazy"` is a browser hint that eligible offscreen images or iframes may be deferred until they approach the viewport. This can reduce initial requests, transfer, decoding, and embedded-document work. The browser chooses the distance threshold and may still load a resource earlier than expected; do not use lazy loading as an authorization, privacy, or exact scheduling boundary.

Use it for below-the-fold media—not for the likely Largest Contentful Paint hero. Reserve image dimensions to prevent layout shift, keep responsive `srcset`/`sizes` when needed, supply useful image `alt` text, and give iframes a descriptive `title`. `decoding="async"` controls decode scheduling, not network laziness.

### Example 27 — prioritize the hero, defer the gallery

```html
<img src="release-hero-1280.webp"
     srcset="release-hero-640.webp 640w, release-hero-1280.webp 1280w"
     sizes="100vw" width="1280" height="720"
     loading="eager" fetchpriority="high"
     alt="Engineers reviewing release health on a dashboard">

<img src="trace-800.webp"
     srcset="trace-400.webp 400w, trace-800.webp 800w"
     sizes="(max-width: 50rem) 92vw, 40rem"
     width="800" height="500" loading="lazy" decoding="async"
     alt="Request trace showing gateway, service, and database spans">

<iframe src="/examples/cache-simulator" loading="lazy"
        width="800" height="450"
        title="Interactive cache hit-rate simulator"></iframe>
```

Loading trace:

```mermaid
flowchart TD
    Resource[Image or iframe] --> Critical{Likely initial-viewport or LCP content?}
    Critical -- yes --> Eager[Load eagerly and prioritize deliberately]
    Critical -- no --> Lazy[Allow native lazy loading]
    Eager --> Stable[Reserve dimensions and provide semantics]
    Lazy --> Stable
    Stable --> Measure[Verify LCP, CLS, requests, and scrolling behavior]
```

Test with production-like pages and throttled browser tooling. A gallery can still overload the network if many lazy candidates sit near the threshold, and lazy iframes can postpone code the user expects immediately.

<a id="q39"></a>

## Q39. Which HTML choices improve SEO without hurting accessibility?

### Requirement

Build one understandable document for people, assistive technology, browsers, and crawlers. Useful overlap includes:

- a concise, unique `<title>` and a truthful description of the page;
- the correct document language, meaningful landmarks, and descriptive headings that reflect the content hierarchy;
- ordinary crawlable `<a href>` links with anchor text that makes sense out of context;
- text alternatives for meaningful images and empty `alt=""` for truly decorative images;
- helpful, visible, people-first content using the same words readers use;
- structured data only when it accurately describes visible page content.

Good heading order helps navigation for screen-reader users, but there is no magic heading count or semantic-markup score that guarantees search ranking. A description can influence a search-result snippet but is not a promise of ranking or even of the exact snippet shown. Avoid keyword stuffing, hidden text/links, misleading structured data, generic “click here” links, and visual-only reading order.

### Example 28 — one article, consistent signals

```html
<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Spring Boot Cache Stampede Guide | Acme Engineering</title>
  <meta name="description"
        content="Learn request coalescing, jitter, stale-while-revalidate, and load testing for Spring Boot caches.">
  <link rel="canonical" href="https://example.com/guides/cache-stampede">
</head>
<body>
  <header><a href="/">Acme Engineering</a></header>
  <main>
    <article>
      <h1>Preventing cache stampedes in Spring Boot</h1>
      <p>A cache stampede occurs when many requests rebuild the same expired value.</p>
      <figure>
        <img src="coalescing-flow.svg" width="960" height="540"
             alt="Concurrent cache misses join one database refresh">
        <figcaption>Request coalescing allows one refresh owner.</figcaption>
      </figure>
      <h2>Choose a refresh policy</h2>
      <p>Compare <a href="/guides/stale-while-revalidate">stale-while-revalidate trade-offs</a>.</p>
    </article>
  </main>
</body>
</html>
```

Before publishing, verify that the title/description match the visible article, every link works without click handlers, the heading outline is understandable, image alternatives convey purpose, canonical/robots directives are intentional, and any structured data matches the page. Then test keyboard navigation, accessibility structure, crawlability, rendered content, and relevant search tooling.

```mermaid
flowchart TD
    Content[Visible people-first content] --> Semantic[Semantic document and links]
    Semantic --> Human[Readable and keyboard usable]
    Semantic --> AT[Understandable accessibility structure]
    Semantic --> Crawl[Crawlable, descriptive search signals]
    Human --> Verify[Accessibility and search validation]
    AT --> Verify
    Crawl --> Verify
```

<a id="q40"></a>

## Q40. Explain the cascade and specificity

### Requirement

Given several declarations for one property on one element, eliminate rules that do not apply and then compare the survivors in cascade order. Specificity is only one late tie-breaker; it cannot jump across an origin, importance level, encapsulation context, or cascade layer.

For an interview, use this debugging ladder:

1. **Relevance and match:** does the stylesheet apply, and does the selector match this element?
2. **Origin and importance:** transitions, important declarations, animations, and normal author/user/UA declarations have defined precedence.
3. **Encapsulation, style attributes, and layers:** Shadow DOM context and inline style declarations have special positions; within an origin, layer order precedes selector specificity. Normal unlayered author rules outrank normal layered rules. For important layered rules, layer order reverses.
4. **Specificity:** compare selector weights only among declarations still tied in the earlier stages.
5. **Scope proximity:** for competing scoped rules, the closer scoping root wins.
6. **Order of appearance:** the later declaration wins the remaining tie.

`!important` does not merely add a huge specificity score. It changes the importance band, also reverses origin and layer precedence where the cascade defines that reversal, and should normally protect contracts such as user overrides—not compensate for uncontrolled selectors.

### Example 29 — trace a normal author declaration

```css
@layer reset, components, overrides;

@layer components {
  .card { color: navy; }             /* layer 2, specificity 0-1-0 */
  .card.featured { color: teal; }    /* layer 2, specificity 0-2-0 */
}

@layer overrides {
  .card { color: purple; }           /* later layer 3 */
}

.card { color: darkgreen; }          /* unlayered normal author rule */
```

For `<article class="card featured">`, all four selectors match:

| Comparison | Survivor | Why |
|---|---|---|
| within `components` | `teal` | same layer; `0-2-0` beats `0-1-0` |
| `components` versus `overrides` | `purple` | layer order is compared before specificity |
| layered versus unlayered normal author rule | `darkgreen` | normal unlayered declarations come after all named layers |

The computed color is `darkgreen`. Adding more classes or an ID inside `components` would not cross the layer boundary.

```mermaid
flowchart TD
    Match[Applicable matching declarations] --> Origin[Origin and importance]
    Origin --> Context[Encapsulation, style attribute, and layer]
    Context --> Specificity[Specificity]
    Specificity --> Scope[Scope proximity]
    Scope --> Order[Order of appearance]
    Order --> Winner[One cascaded value]
```

<a id="q41"></a>

## Q41. What is the CSS box model?

### Requirement

Every generated box has a content area, padding around the content, a border around the padding, and margin outside the border. For an ordinary rectangular box, horizontal outer size is:

```text
margin-left + border-left + padding-left + content width
+ padding-right + border-right + margin-right
```

With `box-sizing: content-box` (the initial value), declared `width` controls the content box. With `box-sizing: border-box`, declared `width` controls the border box; padding and border are taken from that available width. Margins always remain outside the declared width. Min/max constraints, intrinsic sizes, replaced elements, and overflow can still affect the final used geometry.

### Example 30 — calculate both sizing modes

```css
.panel {
  width: 240px;
  padding-inline: 16px;
  border: 2px solid;
  margin-inline: 12px;
}
```

| Mode | Content width | Border-box width | Outer width including margins |
|---|---:|---:|---:|
| `content-box` | 240px | `240 + 32 + 4 = 276px` | `276 + 24 = 300px` |
| `border-box` | `240 - 32 - 4 = 204px` | 240px | `240 + 24 = 264px` |

A common baseline makes component widths easier to reason about:

```css
*, *::before, *::after { box-sizing: border-box; }
```

It does not eliminate the need to budget padding, borders, minimum content sizes, or margins.

```mermaid
flowchart TD
    Margin[Margin box] --> Border[Border box]
    Border --> Padding[Padding box]
    Padding --> Content[Content box]
    Declared{box-sizing?} --> CB[content-box: width sets content]
    Declared --> BB[border-box: width sets border box]
```

<a id="q42"></a>

## Q42. Flexbox versus Grid: when do you choose each?

### Requirement

Choose from the relationships the layout must preserve:

- **Flexbox** lays items out and distributes free space along one main axis at a time. It is a strong fit for toolbars, navigation, button groups, and components whose items form one row or column and may wrap.
- **Grid** defines rows and columns together. It is a strong fit when cards, regions, or form fields must align to shared tracks in two dimensions.
- They compose naturally: Grid can place page regions while Flexbox aligns controls inside one region.

Do not use visual reordering to repair an incorrect document order. `order` and grid placement can change visual position without creating a better keyboard or reading sequence.

### Example 31 — Grid outside, Flexbox inside

```html
<main class="dashboard">
  <article class="card">
    <header class="card__toolbar">
      <h2>Orders</h2><button type="button">Refresh</button>
    </header>
    <p class="card__summary">12 orders require review</p>
  </article>
  <article class="card">Inventory</article>
  <article class="card">Latency</article>
</main>
```

```css
.dashboard {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(18rem, 100%), 1fr));
  gap: 1rem;
}

.card__toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: .75rem;
}
```

```mermaid
flowchart TD
    Need[Layout relationship] --> Axis{One primary axis?}
    Axis -- yes --> Flex[Flexbox distributes items along row or column]
    Axis -- no --> Tracks{Shared rows and columns?}
    Tracks -- yes --> Grid[Grid defines two-dimensional tracks]
    Tracks -- mixed --> Compose[Grid regions with Flexbox internals]
```

<a id="q43"></a>

## Q43. Why does `min-width: 0` fix overflowing Flexbox or Grid children?

### Requirement

The initial `min-width: auto` is not always zero. A non-scrollable flex item can receive a content-based automatic minimum in its main axis. Grid items also receive content-based automatic minimums under defined track and span conditions. A long unbreakable token, intrinsic image width, or nested component can therefore establish a floor larger than the available track.

`flex-shrink: 1` distributes negative free space only until an item reaches its minimum. Setting `min-inline-size: 0` (or physical `min-width: 0` in a horizontal writing mode) explicitly permits further shrinking. For Grid, `minmax(0, 1fr)` also removes the track's implicit content floor when that is the actual constraint.

### Example 32 — make ellipsis reachable

```html
<div class="result-row">
  <img src="avatar.webp" width="40" height="40" alt="">
  <div class="result-row__body">
    <strong class="result-row__title">customer-export-2026-10-15-final-reviewed.csv</strong>
    <span>Ready for download</span>
  </div>
</div>
```

```css
.result-row { display: flex; gap: .75rem; max-inline-size: 22rem; }
.result-row__body { flex: 1 1 auto; min-inline-size: 0; }
.result-row__title {
  display: block;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
```

Shrink trace:

| Step | Without explicit minimum | With `min-inline-size: 0` |
|---|---|---|
| available width drops | negative free space is calculated | same |
| item reaches automatic minimum | stops near its min-content width | may shrink below min-content width |
| title overflow | row can overflow | title box becomes narrower |
| ellipsis | may never activate usefully | overflow is clipped and ellipsis can paint |

<a id="q44"></a>

## Q44. Explain `static`, `relative`, `absolute`, `fixed`, and `sticky` positioning

### Requirement

| Position | Normal-flow space retained? | Offset/containing-block model | Common use |
|---|---|---|---|
| `static` | yes | normal layout; inset properties do not position it | ordinary document flow |
| `relative` | yes | laid out normally, then visually offset; establishes the containing block for positioned descendants | local badge/overlay anchor |
| `absolute` | no | positioned against its containing block, commonly the nearest positioned ancestor | component-local overlay |
| `fixed` | no | fixed-position containing block, usually the viewport; transforms and other properties can create an ancestor containing block | viewport action/control |
| `sticky` | yes | participates in flow, then is shifted within its scrollport/containing block after crossing an inset | section/table headers |

Sticky positioning needs an inset such as `inset-block-start: 0` and enough scroll range. Inspect every ancestor that establishes scrolling or clipping; the relevant scroll container may not be the viewport.

### Example 33 — local badge and sticky heading

```css
.card { position: relative; }
.card__badge {
  position: absolute;
  inset-block-start: .5rem;
  inset-inline-end: .5rem;
}

.results__heading {
  position: sticky;
  inset-block-start: 0;
  z-index: 1;
  background: Canvas;
}
```

Debugging trace: first ask whether the element remains in flow, then identify its containing block, then identify its scroll container, and only then inspect inset values and clipping.

<a id="q45"></a>

## Q45. What creates a stacking context, and why does `z-index` sometimes fail?

### Requirement

A stacking context is an atomic painting group. Descendants are ordered inside their context; after that context is painted as one unit, no descendant `z-index` can interleave with a sibling outside it. A child at `z-index: 9999` can therefore remain behind a sibling whose parent context is higher.

Common context creators include the root element; positioned elements with a non-`auto` `z-index`; fixed and sticky positioned boxes; flex/grid items with non-`auto` `z-index`; `opacity` below 1; transforms, filters, and some masking/compositing properties; `isolation: isolate`; and relevant containment or `will-change` values. Check the specification for a property's exact effect instead of memorizing one frozen list.

### Example 34 — the trapped tooltip

```html
<section class="workspace">
  <article class="card"><button>Open tooltip</button><aside class="tooltip">Details</aside></article>
  <aside class="sidebar">Navigation</aside>
</section>
```

```css
.card { position: relative; z-index: 1; }
.tooltip { position: absolute; z-index: 9999; }
.sidebar { position: relative; z-index: 2; }
```

The tooltip cannot outrank `.sidebar` because its entire parent context (`1`) paints below the sidebar context (`2`). Fix the ownership: render the overlay in an appropriate shared overlay layer, remove the accidental parent context, or use a top-layer primitive such as a modal `<dialog>` or the Popover API when its semantics fit. Do not start an arbitrary `z-index` arms race.

```mermaid
flowchart TD
    Root[Root stacking context] --> Card[Card context: z 1]
    Root --> Sidebar[Sidebar context: z 2]
    Card --> Tooltip[Tooltip: z 9999 inside card]
    Card --> Atomic[Card and tooltip paint as one unit]
    Atomic --> Behind[Whole card context remains behind sidebar]
```

<a id="q46"></a>

## Q46. How would you center an element?

### Requirement

First identify the axis, the formatting context, and whether the parent has usable free space. Centering is not one magic declaration:

- In **Grid**, `place-items: center` centers each item in its grid area on both axes.
- In **Flexbox**, `justify-content: center` acts on the main axis and `align-items: center` on the cross axis. Those physical directions change with `flex-direction` and writing mode.
- For one block with a definite or constrained inline size, `margin-inline: auto` consumes equal free space on both sides.
- For text or inline content inside a block, `text-align: center` centers the inline content, not the block itself.
- For an overlay, absolute positioning plus `inset: 50% auto auto 50%` and `translate: -50% -50%` can be appropriate, but it removes the box from normal flow and should not be the default layout technique.

### Example 35 — choose from the relationship

```css
.empty-state {
  display: grid;
  min-block-size: 12rem;
  place-items: center;
}

.dialog-actions {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: .75rem;
}

.article { max-inline-size: 70ch; margin-inline: auto; }
.article__title { text-align: center; }
```

```mermaid
flowchart TD
    Need[What must be centered?] --> Context{Existing layout context?}
    Context -- Grid --> Grid[place-items or place-self]
    Context -- Flex --> Flex[main axis plus cross axis]
    Context -- Normal flow --> Kind{Box or inline content?}
    Kind -- Box --> Margin[Constrain size and use margin-inline auto]
    Kind -- Inline --> Text[text-align center]
```

### Trace

For a row flex container, main-axis centering is usually horizontal and cross-axis centering vertical. Change it to `flex-direction: column` and those roles swap. Say the axis names in an interview; “horizontal equals `justify-content`” is not a reliable rule.

<a id="q47"></a>

## Q47. `rem` versus `em` versus `px` versus `%` versus `vw`/`vh`

### Requirement

Choose the reference quantity deliberately:

| Unit | Reference | Strong use | Common trap |
|---|---|---|---|
| `rem` | root element's font size | page-level spacing and type scale that follows user font settings | assuming it is always exactly 16px |
| `em` | element's computed font size; on `font-size`, the inherited font size | a component whose padding/radius scales with its own type | compounding sizes through deep nesting |
| `px` | CSS reference pixel, not necessarily one device pixel | borders, icons, and exact CSS-space constraints | treating it as immune to zoom or display density |
| `%` | property-specific reference, often a containing block dimension | fluid track/box relationships | assuming every vertical percentage uses parent height |
| `vw`/`vh` | 1% of the large viewport size in current CSS Values | viewport-coupled art or bounded fluid sizing | mobile browser UI and unbounded text scaling |

Small, large, and dynamic viewport variants (`svh`, `lvh`, `dvh`, and their width/inline/block counterparts) make the intended mobile viewport behavior explicit. Bound fluid values with `min()`, `max()`, or `clamp()` and keep readable text sensitive to user scaling.

### Example 36 — preserve the dependency you mean

```css
:root { font-size: 100%; }

.prose {
  inline-size: min(70ch, 100% - 2rem);
  margin-inline: auto;
  font-size: clamp(1rem, .94rem + .3vw, 1.25rem);
}

.button {
  padding: .6em 1em;
  border: 1px solid;
}

.full-height-panel { min-block-size: 100dvh; }
```

With a 20px root font size, `2rem` is 40 CSS px. A `.button` at 18px has horizontal padding of 18 CSS px. The references are different on purpose.

<a id="q48"></a>

## Q48. What are container queries, and when are they better than media queries?

### Requirement

A media query asks about the viewport or environment; a container query lets descendants respond to an eligible ancestor's size, style, or scroll state. Use a size container query when one reusable component can appear in a narrow sidebar and a wide main region at the same viewport width. Keep media queries for page-wide/environment policy such as print, reduced motion, or top-level navigation.

For inline-size queries, establish a query container with `container-type: inline-size`; optionally name it. The `@container` rule styles descendants of the matching container, not the container itself. Container query length units such as `cqi` are relative to the selected query container.

### Example 37 — one card, two hosts

```html
<section class="rail"><article class="product-card">…</article></section>
<main class="catalog"><article class="product-card">…</article></main>
```

```css
.rail, .catalog { container: product-host / inline-size; }
.product-card { display: grid; gap: 1rem; }

@container product-host (inline-size >= 32rem) {
  .product-card { grid-template-columns: 10rem 1fr; }
}

@media (prefers-reduced-motion: reduce) {
  .product-card { scroll-behavior: auto; }
}
```

```mermaid
flowchart TD
    Component[Same product card] --> Rail[Narrow rail container]
    Component --> Main[Wide catalog container]
    Rail --> Stack[Single-column card]
    Main --> Split[Image plus details]
    Env[Reduced-motion environment] --> Both[Policy applies to both hosts]
```

The viewport may be 1440px in both cases. A media breakpoint cannot distinguish those two card allocations without coupling the component to page placement; the container query can.

<a id="q49"></a>

## Q49. How do CSS custom properties differ from Sass variables?

### Requirement

Sass variables are resolved by a preprocessor and disappear from the emitted stylesheet. CSS custom properties are real properties: they cascade, normally inherit, remain in the computed style, can vary by selector or query, and can be updated at runtime. `var(--token, fallback)` substitutes a fallback only when the referenced custom property is missing or guaranteed-invalid—not whenever the eventual property value happens to be undesirable.

Use Sass variables for compile-time composition and generation. Use custom properties for design tokens whose value must participate in themes, component inheritance, queries, animation, or runtime changes. `@property` can register a custom property with syntax, inheritance behavior, and an initial value, allowing earlier validation and typed interpolation.

### Example 38 — compile-time structure, runtime token

```scss
$button-selector: '.button';

:root { --brand-hue: 220; }
[data-theme='warm'] { --brand-hue: 18; }

@property --button-progress {
  syntax: '<number>';
  inherits: false;
  initial-value: 0;
}

#{$button-selector} {
  --button-bg: hsl(var(--brand-hue) 70% 45%);
  background: var(--button-bg, royalblue);
  transform: scaleX(var(--button-progress));
}
```

```mermaid
flowchart TD
    Sass[Sass variables and loops] --> Build[Build emits CSS]
    Build --> Cascade[Selectors and cascade choose custom-property values]
    Theme[Theme, query, inline style, or script] --> Cascade
    Cascade --> Var[var substitution at computed-value time]
    Var --> Used[Used component property]
```

### Failure mode

```css
.card { --gap: 12bogus; gap: var(--gap, 1rem); }
```

`--gap` exists, so the `var()` fallback is not selected. After substitution the `gap` value is invalid. Registering the token with `@property` or supplying a valid token prevents that invalid-at-computed-value-time surprise.

<a id="q50"></a>

## Q50. How would you avoid layout shift from images and components?

### Requirement

Layout shift occurs when visible content changes position between rendered frames without the expected user-driven exclusion. Prevention is primarily a space-reservation problem:

- give images and video correct intrinsic `width` and `height` attributes; responsive CSS can still use `max-inline-size: 100%; block-size: auto`;
- reserve component slots with a stable size, `aspect-ratio`, or a skeleton that matches the final geometry;
- do not insert banners, consent UI, ads, or errors above already-rendered content unless space was reserved;
- keep fallback and final font metrics compatible and use an intentional loading strategy;
- animate `transform` and `opacity` when motion need not affect layout;
- diagnose actual shifted elements with `PerformanceObserver` rather than guessing from a single screenshot.

### Example 39 — dimensions establish the ratio before bytes arrive

```html
<img src="team-800.webp"
     srcset="team-400.webp 400w, team-800.webp 800w"
     sizes="(max-width: 40rem) 100vw, 40rem"
     width="800" height="450"
     alt="The engineering team in the release room">
```

```css
img { display: block; max-inline-size: 100%; block-size: auto; }
.recommendations { min-block-size: 18rem; }
.toast { transform: translateY(var(--offset)); }
```

```mermaid
flowchart TD
    Discover[HTML discovered] --> Reserve[Intrinsic ratio or stable slot reserves space]
    Reserve --> Load[Media, font, or component loads]
    Load --> Same{Final geometry fits reservation?}
    Same -- yes --> Stable[No displacement from this resource]
    Same -- no --> Shift[Visible content may shift]
```

CLS is an aggregate score derived from layout-shift entries, not “the number of slow images.” A resource can load slowly without shifting anything if its geometry was reserved.

<a id="q51"></a>

## Q51. What is BEM, and do you have to use it?

### Requirement

BEM names ownership explicitly:

- **Block:** independent component, such as `.card`.
- **Element:** a part that belongs to that block, such as `.card__title`.
- **Modifier:** a variation or state, such as `.card--featured` or `.card__title--muted`.

It is a convention, not a browser feature and not a requirement. Its value is predictable flat selectors and recognizable ownership in a global namespace. CSS Modules, scoped styles, utility classes, cascade layers, and component frameworks can provide other scoping or composition mechanisms. Choose one coherent ownership model; do not mix conventions randomly.

### Example 40 — name the component contract

```html
<article class="product-card product-card--featured">
  <h2 class="product-card__title">Noise-cancelling headphones</h2>
  <p class="product-card__price">$199</p>
</article>
```

```css
.product-card { padding: 1rem; border: 1px solid; }
.product-card__title { margin-block: 0 .5rem; }
.product-card__price { font-weight: 700; }
.product-card--featured { border-color: var(--brand); }
```

Avoid selectors such as `.page .sidebar article.product-card h2.title`: they encode page placement and DOM depth, raise specificity, and make the component harder to move. Also avoid inventing “elements of elements” such as `.card__body__title`; the element belongs to the block, so `.card__title` is enough.

<a id="q52"></a>

## Q52. Implement `Array.prototype.map` behavior without using `map`

### Requirement

An interview implementation should state its contract before writing the loop. `map` snapshots the source length, visits integer indices in ascending order, calls the callback only for properties that exist, and leaves corresponding holes in the result. The callback receives `(value, index, source)` and an optional `thisArg`. Because the existence check is dynamic, deleting an unvisited property can make it disappear from the visit, while appending beyond the original length does not extend the pass.

The helper below deliberately covers those observable interview semantics and generic array-like receivers. A production polyfill must additionally reproduce realm and species behavior, exact abstract coercions, and property-creation failure semantics; do not advertise a teaching helper as a drop-in specification implementation.

### Example 41 — sparse `map` and seed-aware `reduce`

```javascript
function toSafeLength(value) {
  const number = Number(value);
  if (Number.isNaN(number) || number <= 0) return 0;
  if (number === Infinity) return Number.MAX_SAFE_INTEGER;
  return Math.min(Math.floor(number), Number.MAX_SAFE_INTEGER);
}

function mapLike(arrayLike, callback, thisArg) {
  if (arrayLike == null) throw new TypeError("receiver is null or undefined");
  if (typeof callback !== "function") throw new TypeError("callback must be callable");

  const source = Object(arrayLike);
  const length = toSafeLength(source.length); // snapshot once
  const output = new Array(length);
  for (let index = 0; index < length; index += 1) {
    if (index in source) {
      output[index] = callback.call(thisArg, source[index], index, source);
    }
  }
  return output;
}

const sparse = [, 2, , 4];
const tripled = mapLike(sparse, function (value) {
  return value * this.factor;
}, {factor: 3});

console.log(tripled.length);       // 4
console.log(Object.keys(tripled)); // ["1", "3"]
console.log(tripled[1], tripled[3]); // 6 12
```

```mermaid
flowchart TD
    Start[Snapshot length 4] --> I0[Index 0 absent: leave hole]
    I0 --> I1[Index 1 present: callback gives 6]
    I1 --> I2[Index 2 absent: leave hole]
    I2 --> I3[Index 3 present: callback gives 12]
    I3 --> Result[Result length 4 with keys 1 and 3]
```

Complexity is `O(length)` property checks and `O(length)` result capacity; callback work is additional. The output's sparse shape is part of correctness, not an optimization detail.

<a id="q53"></a>

## Q53. Implement `reduce`

### Requirement

`reduce` also snapshots length and skips missing properties. The hard branch is accumulator initialization:

- if an initial value was supplied—even explicit `undefined`—use it and start searching from index `0`;
- otherwise, scan for the first present property, use its value as the accumulator, and begin callback execution at the following index;
- if no initial value exists and no present property is found, throw `TypeError`.

Checking `arguments.length` in a fixed-parameter helper can distinguish omitted from explicit `undefined`; a rest parameter makes that distinction clearer. Do not seed blindly from `source[0]`, and do not call the reducer for holes.

```javascript
function reduceLike(arrayLike, callback, ...initial) {
  if (arrayLike == null) throw new TypeError("receiver is null or undefined");
  if (typeof callback !== "function") throw new TypeError("callback must be callable");

  const source = Object(arrayLike);
  const length = toSafeLength(source.length);
  let index = 0;
  let accumulator;

  if (initial.length > 0) {
    [accumulator] = initial;
  } else {
    while (index < length && !(index in source)) index += 1;
    if (index === length) throw new TypeError("no value can seed the reduction");
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

console.log(reduceLike([, 2, , 4], (sum, value) => sum + value)); // 6
console.log(reduceLike([1], (acc, value) => [acc, value], undefined));
// [undefined, 1] — explicit undefined is still an initial value
```

Seed trace for `[, , 5, , 7]` without an initial value:

| Scan/index | Present? | Accumulator/action |
|---:|---|---|
| 0 | no | keep searching |
| 1 | no | keep searching |
| 2 | yes | seed with `5`; do not call callback |
| 3 | no | skip |
| 4 | yes | call callback with `(5, 7, 4, source)` |

```mermaid
flowchart TD
    Begin[Reduction begins] --> Initial{Initial argument supplied?}
    Initial -- yes --> Seed[Use it, even undefined]
    Initial -- no --> Search[Find first present property]
    Search --> Found{Found one?}
    Found -- no --> Throw[Throw TypeError]
    Found -- yes --> First[Use first value as seed]
    Seed --> Fold[Visit remaining present properties]
    First --> Fold
```

The loop performs at most `length` existence checks: `O(length)` time and `O(1)` auxiliary space, excluding callback-owned allocations.

<a id="q54"></a>

## Q54. Flatten a nested array

### Requirement

Clarify whether the desired depth is one level or all levels. Native `flat()` defaults to depth `1`, flattens only actual arrays, and removes holes in levels it traverses. A recursive depth-first solution is concise, but adversarial nesting can exhaust the call stack. The iterative version below stores traversal frames explicitly, preserves left-to-right order, supports a finite depth or `Infinity`, and skips holes.

### Example 42 — explicit stack instead of recursive call depth

```javascript
function flattenArray(source, depth = Infinity) {
  if (!Array.isArray(source)) throw new TypeError("expected an array");
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

const nested = [1, [2, , [3, [4]]], 5];
console.log(flattenArray(nested, 1)); // [1, 2, [3, [4]], 5]
console.log(flattenArray(nested, 2)); // [1, 2, 3, [4], 5]
console.log(flattenArray(nested));    // [1, 2, 3, 4, 5]
```

```mermaid
flowchart TD
    Inspect[Inspect next present value] --> Nested{Array and depth remains?}
    Nested -- yes --> Push[Push child frame]
    Nested -- no --> Emit[Append value]
    Push --> Inspect
    Emit --> More{Current frame finished?}
    More -- no --> Inspect
    More -- yes --> Pop[Pop frame and resume parent]
```

For `n` visited slots and maximum active nesting `h`, time is `O(n)` and explicit traversal space is `O(h)` plus the output. Cyclic arrays are outside the native `flat` contract's practical safe-use boundary too; add cycle detection only if the application contract requires it, because that changes behavior and memory cost.

<a id="q55"></a>

## Q55. Implement `groupBy`

### Requirement

Grouping is a single-pass partition: derive one key for each item, create a bucket on its first occurrence, then append while preserving input order inside each bucket. Choose the result type by key semantics:

- `Map.groupBy(items, keySelector)` retains object and primitive key identity using `Map` key semantics.
- `Object.groupBy(items, keySelector)` converts each key to a property key and returns a null-prototype object.

A plain `{}` silently string-coerces keys and exposes prototype names such as `__proto__`; it is the wrong default for arbitrary keys. This helper intentionally models the `Map` form and accepts any iterable.

### Example 43 — preserve object identity

```javascript
function groupByToMap(items, keySelector) {
  if (typeof keySelector !== "function") throw new TypeError("selector must be callable");
  const groups = new Map();
  let index = 0;
  for (const item of items) {
    const key = keySelector(item, index++);
    if (!groups.has(key)) groups.set(key, []);
    groups.get(key).push(item);
  }
  return groups;
}

const north = {region: "north"};
const south = {region: "south"};
const rows = [
  {id: "A", owner: north},
  {id: "B", owner: south},
  {id: "C", owner: north}
];
const byOwner = groupByToMap(rows, row => row.owner);
console.log(byOwner.get(north).map(row => row.id)); // ["A", "C"]
console.log(byOwner.get(south).map(row => row.id)); // ["B"]
```

```mermaid
flowchart TD
    Item[Read iterable item and index] --> Key[Derive key once]
    Key --> Exists{Bucket exists?}
    Exists -- no --> Create[Create empty bucket]
    Exists -- yes --> Reuse[Reuse existing bucket]
    Create --> Append[Append item]
    Reuse --> Append
    Append --> Item
```

For `n` items, expected time is `O(n)` assuming expected constant-time `Map` operations; storage is `O(n + k)` for `k` groups and all bucket references. Selector cost remains additive.

<a id="q56"></a>

## Q56. Implement a simple `Promise.all`

### Requirement

`Promise.all` consumes an iterable, assimilates values and thenables through `Promise.resolve`, and fulfills only after every input fulfills. The result array follows **iteration order**, not settlement order. The first observed rejection rejects the aggregate, but already-started work is not cancelled. An empty iterable produces a fulfilled promise; attached reactions still run as promise jobs rather than inline with the current stack.

The implementation below is intentionally a base-`Promise` teaching version, not a subclass/species-compatible replacement. The sentinel count (`remaining = 1`) prevents an empty iterable and synchronously iterated inputs from racing the end-of-iteration decision. `Promise.resolve` guarantees that each installed fulfillment handler runs at most once, even for hostile thenables.

### Example 44 — settlement order differs from result order

```javascript
function promiseAllLike(iterable) {
  return new Promise((resolve, reject) => {
    const output = [];
    let remaining = 1; // iteration sentinel
    let index = 0;
    try {
      for (const value of iterable) {
        const currentIndex = index++;
        output[currentIndex] = undefined;
        remaining += 1;
        Promise.resolve(value).then(resolved => {
          output[currentIndex] = resolved;
          remaining -= 1;
          if (remaining === 0) resolve(output);
        }, reject);
      }
      remaining -= 1;
      if (remaining === 0) resolve(output);
    } catch (error) {
      reject(error); // iterator creation/advancement can throw
    }
  });
}

const wait = (label, ms) => new Promise(resolve => setTimeout(resolve, ms, label));
console.log(await promiseAllLike([wait("slow", 20), 7, wait("fast", 0)]));
// ["slow", 7, "fast"]

await promiseAllLike(new Set([Promise.resolve("ok"), Promise.reject("boom")]))
  .catch(reason => console.log(reason)); // boom
```

| Moment | Settled input | Stored state | Aggregate |
|---|---|---|---|
| after iteration | none required | three reserved indexes | pending |
| first | index 1 (`7`) | `[empty, 7, empty]` | pending |
| second | index 2 (`"fast"`) | `[empty, 7, "fast"]` | pending |
| third | index 0 (`"slow"`) | `["slow", 7, "fast"]` | fulfilled in input order |

```mermaid
flowchart TD
    Iterate[Iterate and reserve stable indexes] --> Adopt[Promise.resolve each value]
    Adopt --> Settle{Input settles}
    Settle -- reject --> Reject[Reject aggregate; no implicit cancellation]
    Settle -- fulfill --> Store[Store at original index]
    Store --> Remaining{All inputs fulfilled?}
    Remaining -- no --> Settle
    Remaining -- yes --> Resolve[Resolve ordered result array]
```

Iteration and storage are `O(n)`. The elapsed time to fulfillment is governed by the slowest fulfilling input, while rejection latency is governed by the first rejection that reaches the aggregate. This is coordination, not parallel execution: the underlying operations start when their promises/tasks are created.

## Decision checklist

- Use `const` by default when the binding should not change; use `let` when reassignment is part of the model.
- Treat `var` as function-scoped legacy syntax whose initialization and redeclaration rules matter when reading older code.
- For an output question, write the environment state before simulating statements.
- For a closure question, draw the source nesting and follow outer-environment links.
- For a loop/callback question, ask whether callbacks share one binding or receive one per iteration.
- For mutation questions, draw bindings separately from the object graph and mark exactly which reference is copied.
- For equality questions, classify operand types before following a coercion rule; do not substitute truthiness.
- For copying, state which nested nodes must be isolated and which values the chosen mechanism supports.
- For collection transforms, choose from the desired output shape before optimizing pass count.
- For defaults, use `??` when `0`, `false`, or an empty string is valid data.
- For closures, identify the exact captured bindings and their intended owner lifetime.
- For normal-function `this`, read the call expression; for arrow `this`, read the creation environment.
- For prototype questions, separate own properties, inherited properties, and the `.prototype` property on constructors.
- For memoization, define input equivalence, falsy-result handling, eviction, freshness, and invalidation before discussing speed.
- For event-loop output, finish the current task, drain the entire microtask checkpoint, then consider a later task.
- For `await`, distinguish dependency ordering from independent work that can be started together.
- For promise combinators, name the required success condition and remember that settlement does not cancel sibling work.
- For async errors, keep the promise in the caller's chain and catch only at a meaningful ownership boundary.
- Choose debounce when only the final value matters; choose throttle when bounded periodic progress matters.
- Give each request an owner; abort superseded work and distinguish cancellation from failure.
- Delegate events at a stable ancestor, but validate the matched node stays within the component boundary.
- Batch geometry reads before DOM writes, then verify the result in browser performance tooling.
- Choose browser storage by shape, lifetime, access, and threat model; never treat client storage as authorization state.
- Treat CORS as controlled response sharing, not as authentication or CSRF protection.
- For XSS, keep untrusted data out of executable contexts; for CSRF, validate state-changing requests beyond ambient cookies.
- Decide default-action cancellation separately from propagation; check cancelability and passive-listener constraints.
- Choose native elements for their contracts: links navigate, buttons act, labels name controls, and headings describe structure.
- Use `async` only for independent scripts; use `defer` or modules when parse completion and ordering matter.
- Make form names, instructions, required state, and errors programmatically available and keyboard-test the completed flow.
- Treat the viewport element as layout configuration, not a substitute for fluid responsive CSS or user zoom.
- Use `srcset`/`sizes` for resolution switching and `<picture>` for art direction; leave final selection to the browser.
- Keep visual, layout, focus, and accessibility-tree visibility as separate decisions; never hide reachable focus with `aria-hidden`.
- Lazy-load eligible below-the-fold media, not the likely LCP resource, and reserve dimensions before it arrives.
- Prefer one honest semantic document for readers, assistive technology, and crawlers; verify rather than chase markup myths.
- Debug the cascade from applicability and origin through layers, specificity, scope proximity, and source order; never start with a specificity arms race.
- Calculate content, padding, border, and margin separately; `border-box` changes the declared sizing box, not the physical existence of those edges.
- Use Flexbox for one-axis distribution, Grid for shared two-axis tracks, and preserve a logical source order in either layout.
- When flex/grid content overflows, identify the automatic minimum before adding clipping; use `min-inline-size: 0` or a zero track minimum deliberately.
- For positioned elements, identify flow participation, containing block, scroll container, insets, and clipping in that order.
- Treat each stacking context as an atomic group; fix overlay ownership or use the top layer instead of escalating `z-index` numbers.
- Center according to the formatting context and named axes; do not memorize one physical-direction recipe.
- Choose CSS units by the quantity that should control the value, and bound viewport-driven type or spacing.
- Use container queries for reusable component allocation and media queries for viewport or environment policy.
- Use CSS custom properties when values must cascade or change at runtime; use Sass variables for build-time composition.
- Prevent layout shift by reserving final geometry before asynchronous content arrives, then measure actual shift entries.
- Treat BEM as one ownership convention, not a requirement; prefer flat predictable selectors over DOM-coupled chains.
- For `map` and `reduce`, snapshot length, distinguish missing properties from `undefined`, and state how the initial accumulator is chosen.
- For flattening, agree on depth and hole behavior before choosing recursion or an explicit traversal stack.
- Choose `Map` grouping when key identity matters; choose object grouping only when property-key coercion is intentional.
- For promise aggregation, reserve stable indexes, adopt every input, count fulfillment, and remember that rejection does not cancel source work.

<a id="sources"></a>

## Source provenance and verification

Paired source comparison performed through 2026-10-15:

- `JavaScript_HTML_CSS_Interview_Mermaid_Workbook.md`, SHA-256 `742ef1042828659004807171e384cfa22decd4589dbbb384d59910df6217fdd0`, lines 166–1487. The newly processed Q52–Q56 range is lines 1356–1487, bounded by Q57 at line 1488.
- `JavaScript_HTML_CSS_Interview_Workbook.docx`, SHA-256 `790f96df8880d825180a99171978d4fd301eeb7f3e1490c570a42028d5778bfb`, rendered lines 927–1034 for Q52–Q56, bounded by Q57 at rendered line 1035. It agrees on all five topics but is not promoted as a competing guide.
- The repository edition corrects sparse-array reduction, distinguishes omitted and explicit seeds, adds stack-safe depth-controlled flattening, preserves arbitrary grouping-key identity, accepts promise iterables, and adds four connected executable examples, four traces/diagrams, deterministic assertions, and current primary links.

Primary references checked through 2026-10-15:

- [ECMAScript 2026 — Environment Records](https://tc39.es/ecma262/2026/multipage/executable-code-and-execution-contexts.html#sec-environment-records)
- [ECMAScript 2026 — `let`, `const`, and `var` declarations](https://tc39.es/ecma262/2026/multipage/ecmascript-language-statements-and-declarations.html#sec-let-and-const-declarations)
- [ECMAScript 2026 — `for` loop per-iteration environments](https://tc39.es/ecma262/2026/multipage/ecmascript-language-statements-and-declarations.html#sec-createperiterationenvironment)
- [HTML Living Standard — timers](https://html.spec.whatwg.org/multipage/timers-and-user-prompts.html#timers)
- [ECMAScript — language types](https://tc39.es/ecma262/multipage/ecmascript-data-types-and-values.html)
- [ECMAScript — strict and abstract equality](https://tc39.es/ecma262/multipage/abstract-operations.html#sec-islooselyequal)
- [ECMAScript — `Number.isNaN`](https://tc39.es/ecma262/multipage/numbers-and-dates.html#sec-number.isnan)
- [HTML Living Standard — structured cloning API](https://html.spec.whatwg.org/multipage/structured-data.html#structured-cloning-api)
- [ECMAScript — indexed collection methods](https://tc39.es/ecma262/multipage/indexed-collections.html#sec-properties-of-the-array-prototype-object)
- [ECMAScript — optional chains](https://tc39.es/ecma262/multipage/ecmascript-language-expressions.html#sec-optional-chains)
- [ECMAScript — nullish coalescing](https://tc39.es/ecma262/multipage/ecmascript-language-expressions.html#sec-coalesce-expression)
- [ECMAScript — ECMAScript function objects and captured environments](https://tc39.es/ecma262/multipage/ordinary-and-exotic-objects-behaviours.html#sec-ecmascript-function-objects)
- [ECMAScript — ordinary function calls and `this`](https://tc39.es/ecma262/multipage/ordinary-and-exotic-objects-behaviours.html#sec-ordinarycallbindthis)
- [ECMAScript — `Function.prototype.call`, `apply`, and `bind`](https://tc39.es/ecma262/multipage/fundamental-objects.html#sec-properties-of-the-function-prototype-object)
- [ECMAScript — ordinary property lookup](https://tc39.es/ecma262/multipage/ordinary-and-exotic-objects-behaviours.html#sec-ordinary-object-internal-methods-and-internal-slots-get-p-receiver)
- [ECMAScript — class definitions](https://tc39.es/ecma262/multipage/ecmascript-language-functions-and-classes.html#sec-class-definitions)
- [ECMAScript — arrow function definitions](https://tc39.es/ecma262/multipage/ecmascript-language-functions-and-classes.html#sec-arrow-function-definitions)
- [HTML Living Standard — event loops and microtask checkpoints](https://html.spec.whatwg.org/multipage/webappapis.html#event-loops)
- [ECMAScript 2026 — async functions](https://tc39.es/ecma262/2026/multipage/ecmascript-language-functions-and-classes.html#sec-async-function-definitions)
- [ECMAScript 2026 — promise combinators and reactions](https://tc39.es/ecma262/2026/multipage/control-abstraction-objects.html#sec-promise-objects)
- [ECMAScript 2026 — error cause](https://tc39.es/ecma262/2026/multipage/fundamental-objects.html#sec-installerrorcause)
- [Fetch Standard — fetch and network errors](https://fetch.spec.whatwg.org/#fetch-method)
- [HTML Living Standard — timers](https://html.spec.whatwg.org/multipage/timers-and-user-prompts.html#timers)
- [DOM Standard — event dispatch](https://dom.spec.whatwg.org/#dispatching-events)
- [Fetch Standard — CORS protocol](https://fetch.spec.whatwg.org/#http-cors-protocol)
- [HTML Living Standard — Web Storage](https://html.spec.whatwg.org/multipage/webstorage.html)
- [Indexed Database API](https://w3c.github.io/IndexedDB/)
- [Content Security Policy Level 3](https://www.w3.org/TR/CSP3/)
- [OWASP Cross Site Scripting Prevention Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Cross_Site_Scripting_Prevention_Cheat_Sheet.html)
- [OWASP Cross-Site Request Forgery Prevention Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Cross-Site_Request_Forgery_Prevention_Cheat_Sheet.html)
- [HTML Living Standard — semantics](https://html.spec.whatwg.org/multipage/dom.html#semantics-2)
- [HTML Living Standard — scripting](https://html.spec.whatwg.org/multipage/scripting.html)
- [HTML Living Standard — forms](https://html.spec.whatwg.org/multipage/forms.html)
- [WAI Forms Tutorial — labeling controls](https://www.w3.org/WAI/tutorials/forms/labels/)
- [CSS Viewport Module Level 1](https://drafts.csswg.org/css-viewport/)
- [HTML Living Standard — responsive images](https://html.spec.whatwg.org/multipage/images.html#responsive-images)
- [HTML Living Standard — the `hidden` attribute](https://html.spec.whatwg.org/multipage/interaction.html#the-hidden-attribute)
- [ARIA in HTML — author requirements](https://www.w3.org/TR/html-aria/)
- [CSS Display — invisibility and box generation](https://drafts.csswg.org/css-display-4/#visibility)
- [HTML Living Standard — lazy loading](https://html.spec.whatwg.org/multipage/urls-and-fetching.html#lazy-loading-attributes)
- [Google Search Essentials](https://developers.google.com/search/docs/essentials)
- [Google SEO Starter Guide](https://developers.google.com/search/docs/fundamentals/seo-starter-guide)
- [Google link best practices](https://developers.google.com/search/docs/crawling-indexing/links-crawlable)
- [CSS Cascading and Inheritance Level 6 — cascade sorting order](https://drafts.csswg.org/css-cascade-6/#cascade-sort)
- [CSS Box Model Level 3](https://drafts.csswg.org/css-box-3/)
- [CSS Box Sizing Level 3](https://drafts.csswg.org/css-sizing-3/)
- [CSS Flexible Box Layout Level 2 — automatic minimum size](https://drafts.csswg.org/css-flexbox-2/#min-size-auto)
- [CSS Grid Layout Level 2 — automatic minimum size](https://drafts.csswg.org/css-grid/#min-size-auto)
- [CSS Positioned Layout Level 3](https://drafts.csswg.org/css-position-3/)
- [CSS Positioned Layout Level 4 — stacking and the top layer](https://drafts.csswg.org/css-position-4/)
- [CSS Box Alignment Level 3](https://drafts.csswg.org/css-align/)
- [CSS Values and Units Level 4](https://drafts.csswg.org/css-values-4/)
- [CSS Conditional Rules Level 5 — container queries](https://drafts.csswg.org/css-conditional-5/)
- [CSS Containment Level 3](https://drafts.csswg.org/css-contain-3/)
- [CSS Custom Properties for Cascading Variables](https://drafts.csswg.org/css-variables/)
- [CSS Properties and Values API Level 1](https://drafts.css-houdini.org/css-properties-values-api-1/)
- [Layout Instability API](https://wicg.github.io/layout-instability/)
- [HTML Living Standard — responsive images and intrinsic dimensions](https://html.spec.whatwg.org/multipage/images.html)
- [BEM methodology — naming](https://getbem.com/naming/)
- [ECMAScript — `Array.prototype.map`](https://tc39.es/ecma262/multipage/indexed-collections.html#sec-array.prototype.map)
- [ECMAScript — `Array.prototype.reduce`](https://tc39.es/ecma262/multipage/indexed-collections.html#sec-array.prototype.reduce)
- [ECMAScript — `Array.prototype.flat` and `FlattenIntoArray`](https://tc39.es/ecma262/multipage/indexed-collections.html#sec-array.prototype.flat)
- [ECMAScript — `Object.groupBy`](https://tc39.es/ecma262/multipage/fundamental-objects.html#sec-object.groupby)
- [ECMAScript — `Map.groupBy`](https://tc39.es/ecma262/multipage/keyed-collections.html#sec-map.groupby)
- [ECMAScript — `Promise.all`](https://tc39.es/ecma262/multipage/control-abstraction-objects.html#sec-promise.all)

<a id="next"></a>

## Next bounded section

Continue with source Q57–Q61: `once`, a basic event emitter, a promise-task concurrency limiter, nested event-loop ordering, and closure-retention risks. The Markdown range begins at line 1488 and is bounded by Q62 at line 1641; the DOCX range begins at rendered line 1035 and is bounded by Q62 at rendered line 1167. Re-read both exact ranges before editing.
