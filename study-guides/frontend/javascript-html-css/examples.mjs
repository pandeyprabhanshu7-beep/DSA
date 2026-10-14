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

assert.deepEqual(constantBindingExample(), {name: 'Grace', skills: ['JS', 'HTML']});
assert.deepEqual(hoistingExample(), [undefined, 7]);
assert.equal(temporalDeadZoneErrorName(), 'ReferenceError');
assert.deepEqual(lexicalScopeExample(), ['global', 'outer']);
assert.deepEqual(await loopCaptureExample(), [3, 3, 3, 0, 1, 2]);

console.log('JavaScript execution-model examples: 5 assertions passed');
