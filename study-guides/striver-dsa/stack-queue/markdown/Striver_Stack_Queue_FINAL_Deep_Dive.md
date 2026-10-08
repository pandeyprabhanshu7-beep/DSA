# Striver / Take U Forward — Stack & Queue Deep Study Guide

## Navigation and Index

> Use this as the home page for the book. Every card ends with a return link.

| ID | Topic | Jump |
|---|---|---|
| **S1** | Stack Using Array | [Jump](#s1-stack-using-array) |
| **S2** | Queue Using Circular Array | [Jump](#s2-queue-using-circular-array) |
| **S3** | Stack Using One Queue | [Jump](#s3-stack-using-one-queue) |
| **S4** | Queue Using Two Stacks | [Jump](#s4-queue-using-two-stacks) |
| **S5** | Stack Using Linked List | [Jump](#s5-stack-using-linked-list) |
| **S6** | Queue Using Linked List | [Jump](#s6-queue-using-linked-list) |
| **S7** | Balanced Parentheses | [Jump](#s7-balanced-parentheses) |
| **S8** | Min Stack | [Jump](#s8-min-stack) |
| **S9** | Infix to Postfix | [Jump](#s9-infix-to-postfix) |
| **S10** | Prefix to Infix | [Jump](#s10-prefix-to-infix) |
| **S11** | Prefix to Postfix | [Jump](#s11-prefix-to-postfix) |
| **S12** | Postfix to Prefix | [Jump](#s12-postfix-to-prefix) |
| **S13** | Postfix to Infix | [Jump](#s13-postfix-to-infix) |
| **S14** | Infix to Prefix | [Jump](#s14-infix-to-prefix) |
| **S15** | Next Greater Element | [Jump](#s15-next-greater-element) |
| **S16** | Next Greater Element II — Circular | [Jump](#s16-next-greater-element-ii-circular) |
| **S17** | Next Smaller Element | [Jump](#s17-next-smaller-element) |
| **S18** | Count Greater Elements to the Right for Queries | [Jump](#s18-count-greater-elements-to-the-right-for-queries) |
| **S19** | Trapping Rainwater | [Jump](#s19-trapping-rainwater) |
| **S20** | Sum of Subarray Minimums | [Jump](#s20-sum-of-subarray-minimums) |
| **S21** | Asteroid Collision | [Jump](#s21-asteroid-collision) |
| **S22** | Sum of Subarray Ranges | [Jump](#s22-sum-of-subarray-ranges) |
| **S23** | Remove K Digits | [Jump](#s23-remove-k-digits) |
| **S24** | Largest Rectangle in Histogram | [Jump](#s24-largest-rectangle-in-histogram) |
| **S25** | Maximal Rectangle | [Jump](#s25-maximal-rectangle) |
| **S26** | Sliding Window Maximum | [Jump](#s26-sliding-window-maximum) |
| **S27** | Stock Span | [Jump](#s27-stock-span) |
| **S28** | Celebrity Problem | [Jump](#s28-celebrity-problem) |
| **S29** | LRU Cache | [Jump](#s29-lru-cache) |
| **S30** | LFU Cache | [Jump](#s30-lfu-cache) |

## How to study this book

Stacks and queues encode which unresolved state should be revisited next. LIFO models nested/recent state, FIFO models layers/time, monotonic structures discard dominated candidates, and cache designs compose multiple structures because one container cannot satisfy every O(1) requirement.

**Required reading order:** requirement → examples → data picture → brute force → repeated-work diagnosis → invariant → optimized plan → dry run → Java → correctness → boundaries → complexity → memory trick → variations.

> **Source transparency:** topic ordering follows the retained Striver/Take U Forward study structure from this project. Extra examples, memory tricks, research notes and explanations are study-guide additions unless explicitly marked as verified from a creator/source.

## Pattern recognition cheat sheet

| Clue | Think about first |
|---|---|
| next/previous greater/smaller | monotonic stack |
| fixed-size best window | deque / sliding window |
| shortest unweighted path | BFS |
| nonnegative weighted shortest path | Dijkstra |
| negative edges | Bellman-Ford |
| dependencies | topological sort |
| repeated component merges | DSU |
| critical edge/vertex | low-link DFS |
| linked-list middle/cycle | slow/fast pointers |
| tree child summaries | postorder DP |
| repeated lookup/count | HashMap / HashSet |
| subarray sum/count with negatives | prefix state + HashMap |

<a id="s1-stack-using-array"></a>
## S1. Stack Using Array

[Index](#navigation-and-index) · [S2 →](#s2-queue-using-circular-array)

### Exact problem and API contract

Implement an integer **LIFO stack** backed by a fixed-capacity array. Constructor capacity may be zero but not negative. Implement **push(int)**, **pop()**, **peek()**, **size()**, **isEmpty()** without a built-in stack. Push on full throws **IllegalStateException**; pop or peek on empty throws **NoSuchElementException**. These exception choices are part of this guide's API, not universal online-judge rules.

### Four concrete input/output examples

| # | Operations | Output / behavior |
|---:|---|---|
| 1 | capacity 3; push(10), push(20), push(30), peek(), pop(), pop(), push(-7), pop(), pop() | peek=30; pops **30,20,-7,10** |
| 2 | capacity 0; isEmpty(), push(5), pop() | true; push throws **IllegalStateException**; pop throws **NoSuchElementException** |
| 3 | capacity 2; push(4), push(4), push(5), size() | third push throws; size remains **2** |
| 4 | capacity 1; push(-2147483648), peek(), pop(), isEmpty() | peek/pop = **-2147483648**, then true |

### Recognize pattern; baseline vs optimized approach

**Clue:** newest insertion is removed first. A naive array can place newest values at index 0, shifting existing elements on every push and pop (**O(n)**). Instead use the **end of the occupied array prefix** as top; then no element shifts. **Invariant:** 0 ≤ size ≤ capacity; live stack values occupy **data[0..size-1]** in bottom-to-top order; when nonempty the top is **data[size-1]**. Values beyond size are irrelevant.

### Mermaid state diagram

~~~mermaid
flowchart LR
  A["data: [10, 20, 30, _]; size=3"] -->|"pop: --size"| B["live: [10,20]; size=2; returned 30"]
  B -->|"push(40): data[size++]=40"| C["live: [10,20,40]; size=3"]
~~~

### Detailed dry run, capacity 3

| Step | Operation | Live prefix, bottom → top | size | Result |
|---:|---|---|---:|---|
| 0 | constructor(3) | [] | 0 | — |
| 1 | push(10) | [10] | 1 | — |
| 2 | push(20) | [10,20] | 2 | — |
| 3 | push(30) | [10,20,30] | 3 | — |
| 4 | peek() | [10,20,30] | 3 | 30 |
| 5 | pop() | [10,20] | 2 | 30 |
| 6 | pop() | [10] | 1 | 20 |
| 7 | push(-7) | [10,-7] | 2 | — |
| 8 | pop() | [10] | 1 | -7 |
| 9 | pop() | [] | 0 | 10 |

### Java 17 implementation

~~~java
import java.util.NoSuchElementException;

public final class ArrayStack {
    private final int[] data;
    private int size;

    public ArrayStack(int capacity) {
        if (capacity < 0) throw new IllegalArgumentException("capacity must be nonnegative");
        data = new int[capacity];
    }
    public void push(int value) {
        if (size == data.length) throw new IllegalStateException("stack full");
        data[size++] = value;
    }
    public int pop() {
        if (size == 0) throw new NoSuchElementException("stack empty");
        return data[--size];
    }
    public int peek() {
        if (size == 0) throw new NoSuchElementException("stack empty");
        return data[size - 1];
    }
    public int size() { return size; }
    public boolean isEmpty() { return size == 0; }
}
~~~

**Key lines:** **data[size++] = value** stores in the next free slot then increments the count; **data[--size]** first decreases the count then reads the former top. Using data[size] for peek or pre-incrementing push causes an off-by-one error.

### Correctness proof

Initially size=0, so the live prefix is empty. If a push succeeds, it writes after all previous live values and increments size, making the new value the top. A successful pop decreases size by one and returns precisely the last live value. Peek reads that same value without changing state. By induction, every legal operation preserves the invariant and LIFO ordering.

### Complexity, boundaries, and variations

**push/pop/peek/size/isEmpty:** O(1) worst-case time and O(1) extra space per call. **Constructor:** O(capacity) array initialization and O(capacity) storage. Check capacity 0/negative; full push leaves size unchanged; empty pop/peek; duplicate and extreme int values; reuse a freed slot after pop. A dynamically resized array provides amortized O(1) push but worst-case O(n) when growing. **Common mistakes:** mixing top index with element count, returning -1 as an empty sentinel, or assuming old physical values after pop remain live. **Interview extensions:** geometric resizing, minimum-stack augmentation, array vs linked list locality, and thread-safety.

> **Memory trick:** **SIZE IS THE NEXT FREE INDEX; TOP IS SIZE - 1.**

[↑ Back to Index](#navigation-and-index)

---

<a id="s2-queue-using-circular-array"></a>
## S2. Queue Using Circular Array

[← S1](#s1-stack-using-array) · [Index](#navigation-and-index) · [S3 →](#s3-stack-using-one-queue)

### Problem statement and API contract

Implement a **fixed-capacity FIFO queue** using a single integer array. The constructor accepts a **positive** capacity. Provide `offer(x)`, `poll()`, `peek()`, `size()` and `isEmpty()`. Offering when full throws `IllegalStateException`; polling/peeking when empty throws `NoSuchElementException`. If a platform uses return-value sentinels, adapt only the error-handling contract, not the circular invariant.

### Four concrete input/output examples

| # | Setup and operations | Exact output / behavior |
|---:|---|---|
| 1 | Capacity 3; `offer(10), offer(20), offer(30), peek(), poll(), offer(40), poll(), poll(), poll()` | `peek=10`; polls return `10,20,30,40` |
| 2 | Capacity 1; `offer(-7), poll(), offer(11), peek()` | `poll=-7`; `peek=11` |
| 3 | Capacity 2; `offer(5), offer(5), offer(9)` | Third offer throws `IllegalStateException`; size stays 2 |
| 4 | Capacity 3; `poll(), peek()` before any offer | Both throw `NoSuchElementException` |

### Pattern recognition, brute force, and optimization

**Clues:** FIFO, bounded capacity, and repeated insertions/removals without shifting. A simple linear array shifts all remaining values left on each poll: correct but **O(n) per poll**. A non-wrapping head/tail scheme eventually exhausts the array despite empty slots. The circular design reuses those slots with modulo arithmetic.

Keep `front` = physical index of oldest live element and `size` = count of live elements. For an insertion, the next free index is `(front + size) % capacity`. For removal, return `values[front]` and advance `front = (front + 1) % capacity`. Detect full using `size == capacity`, not `front == rear` (ambiguous between full and empty without extra state).

### Diagram — physical array versus logical FIFO

```mermaid
flowchart LR
 A["offer 10,20,30<br/>array [10,20,30]<br/>front=0"] --> B["poll gives 10<br/>front=1"]
 B --> C["offer 40 writes slot (1+2)%3 = 0<br/>array [40,20,30]"]
 C --> D["Logical FIFO from front=1<br/>20 → 30 → 40"]
```

**Invariant:** for each `0 <= k < size`, the k-th oldest live element is `values[(front + k) % capacity]`. Also `0 <= front < capacity` and `0 <= size <= capacity`.

### Detailed dry run (capacity 3)

| Action | Return | front | size | Physical array | Logical queue |
|---|---:|---:|---:|---|---|
| start | — | 0 | 0 | `[_,_,_]` | `[]` |
| offer(10) | — | 0 | 1 | `[10,_,_]` | `[10]` |
| offer(20) | — | 0 | 2 | `[10,20,_]` | `[10,20]` |
| offer(30) | — | 0 | 3 | `[10,20,30]` | `[10,20,30]` |
| poll() | 10 | 1 | 2 | `[10,20,30]` | `[20,30]` |
| offer(40) | — | 1 | 3 | `[40,20,30]` | `[20,30,40]` |
| poll() | 20 | 2 | 2 | `[40,20,30]` | `[30,40]` |
| poll() | 30 | 0 | 1 | `[40,20,30]` | `[40]` |
| poll() | 40 | 1 | 0 | `[40,20,30]` | `[]` |

Stale array entries do not count as live data; `size` defines the live segment.

### Java 17 implementation (standalone source)

```java
import java.util.NoSuchElementException;

public final class CircularQueue {
    private final int[] values;
    private int front = 0, size = 0;

    public CircularQueue(int capacity) {
        if (capacity <= 0) throw new IllegalArgumentException("capacity must be positive");
        values = new int[capacity];
    }
    public void offer(int value) {
        if (size == values.length) throw new IllegalStateException("queue full");
        int rear = (front + size) % values.length; // next free slot
        values[rear] = value;
        size++;
    }
    public int poll() {
        if (size == 0) throw new NoSuchElementException("queue empty");
        int answer = values[front];
        front = (front + 1) % values.length; // discard exactly the oldest
        size--;
        return answer;
    }
    public int peek() {
        if (size == 0) throw new NoSuchElementException("queue empty");
        return values[front];
    }
    public int size() { return size; }
    public boolean isEmpty() { return size == 0; }
}
```

### Key lines, correctness proof, complexity

**Key lines:** `rear = (front + size) % values.length` identifies the first free circular slot; `front = (front + 1) % values.length` moves to the next oldest without shifting elements.

**Proof:** Initially `size=0`, so the invariant holds. If not full, an offer writes at logical offset `size`, after all existing elements; it does not overwrite any live cell. If not empty, poll returns logical offset 0; incrementing `front` makes former offset 1 the new oldest and preserves the relative order of all remaining cells. Thus FIFO and index bounds are maintained by induction. Peek and size queries do not change state.

**Time:** O(1) per operation (constant arithmetic and array accesses); constructing the backing array is O(C). **Space:** O(C) total for capacity C and O(1) per-operation auxiliary space.

### Boundary cases, mistakes, follow-ups

- Capacity 1 repeatedly wraps to index 0; duplicates and negative values work without sentinels.
- `front == rear` alone cannot distinguish empty from full; use `size`.
- Avoid shifting the array on poll; that destroys O(1) performance.
- The implementation is **not thread-safe**. For concurrency use appropriate synchronization or a concurrent queue.
- For theoretically huge Java arrays, `front + size` may overflow `int`; use `front >= values.length - size ? front - (values.length - size) : front + size` when constraints require overflow-safe indexing.
- **Follow-ups:** dynamically growing circular queue (copy live elements in logical order, reset front to 0), overwrite-oldest ring buffer, deque, and generic queue clearing stale references.

> **MEMORY TRICK —** FRONT = next OUT; FRONT + SIZE = next IN.

[↑ Back to Index](#navigation-and-index)

---

<a id="s3-stack-using-one-queue"></a>
## S3. Stack Using One Queue

[← S2](#s2-queue-using-circular-array) · [Index](#navigation-and-index) · [S4 →](#s4-queue-using-two-stacks)

### Problem statement and API contract

Implement a **LIFO stack using exactly one FIFO queue**, with only O(1) scalar bookkeeping. Support `push(x)`, `pop()`, `top()`, `size()`, `isEmpty()`. An empty `pop` or `top` throws `NoSuchElementException`. You may use Java's `Queue<Integer>` backed by `ArrayDeque`; do not use a second queue or stack to reorder elements.

### Four concrete examples

| # | Operations | Exact outputs |
|---:|---|---|
| 1 | `push(1), push(2), push(3), top(), pop(), top()` | `3,3,2` |
| 2 | `push(8), pop(), push(9), pop()` | `8,9` |
| 3 | `push(5), push(5), pop(), pop()` | `5,5` |
| 4 | `pop(), top()` on a new stack | Both throw `NoSuchElementException` |

### Recognize the pattern: reverse the access order

A queue removes its **oldest** element; a stack removes its **newest**. A baseline uses a second temporary queue to move n−1 elements aside and retrieve the last one, costing O(n) per pop and violating the one-queue restriction.

**One-queue strategy:** after enqueueing the new value, rotate exactly the *previous* n elements from the front to the rear. The newest element moves to the front. Thus `push` takes O(n), while `pop` and `top` take O(1).

### Visual — one queue, front shown on the left

```mermaid
flowchart LR
 A["Before push(3)<br/>front → [2,1]"] --> B["offer(3)<br/>[2,1,3]"]
 B --> C["rotate old 2<br/>[1,3,2]"]
 C --> D["rotate old 1<br/>front → [3,2,1]"]
 D --> E["pop returns 3<br/>[2,1]"]
```

**Invariant:** from queue **front to rear**, elements are ordered from **stack top to bottom**. Each completed push restores that invariant; transient rotation states need not satisfy it.

### Detailed dry run

| Operation / substep | Queue (front → rear) | Observation |
|---|---|---|
| start | `[]` | empty |
| push(1) | `[1]` | zero rotations |
| push(2): offer | `[1,2]` | new element temporarily at rear |
| rotate 1 | `[2,1]` | new top 2 |
| push(3): offer | `[2,1,3]` | transient |
| rotate old 2 | `[1,3,2]` | transient |
| rotate old 1 | `[3,2,1]` | new top 3 |
| top() | `[3,2,1]` | returns 3, unchanged |
| pop() | `[2,1]` | returns 3 |
| push(4): offer + 2 rotations | `[4,2,1]` | top 4 |
| pop(), pop(), pop() | `[]` | returns 4,2,1 |

### Java 17 implementation (standalone source)

```java
import java.util.ArrayDeque;
import java.util.NoSuchElementException;
import java.util.Queue;

public final class StackWithOneQueue {
    private final Queue<Integer> q = new ArrayDeque<>();

    public void push(int value) {
        q.offer(value);
        int oldCount = q.size() - 1;          // exclude new value
        for (int i = 0; i < oldCount; i++) {
            q.offer(q.remove());             // old front moves behind new
        }
    }
    public int pop() {
        if (q.isEmpty()) throw new NoSuchElementException("stack empty");
        return q.remove();                   // queue front is stack top
    }
    public int top() {
        if (q.isEmpty()) throw new NoSuchElementException("stack empty");
        return q.element();
    }
    public int size() { return q.size(); }
    public boolean isEmpty() { return q.isEmpty(); }
}
```

### Key line, correctness proof and derived complexity

**Key line:** `oldCount = q.size() - 1`. Rotating all `q.size()` elements would restore the old front, making the stack incorrect. Rotate **only** the old elements.

**Proof:** Initially the empty queue matches the empty stack. Assume its front-to-rear order equals stack top-to-bottom. On push, append the new item and rotate each of the n old items to the rear. The new item reaches the front while old items retain their relative order, preserving the invariant. Pop removes the front, exactly the most recently pushed surviving item. Top reads that item without mutation. Therefore all operations implement LIFO.

**Time:** pushing into n elements performs one enqueue plus n dequeue/enqueue rotations, O(n); pop/top/size/isEmpty are O(1). n pushes from empty take `0+1+...+(n−1)=n(n−1)/2` rotations, **O(n²)** total. **Space:** O(n) in the single underlying queue and O(1) auxiliary bookkeeping.

### Boundary cases, common errors, variations

- Empty stack: throw rather than returning an ambiguous integer sentinel.
- One element: zero rotations; duplicate values require no special treatment.
- Never rotate `q.size()` times: it would undo the rearrangement.
- Do not assume `Queue.remove()` removes the rear: it removes the front.
- **Alternative trade-off:** O(1) push and O(n) pop by rotating n−1 items just before removing the rear-most old item; useful when pushes greatly outnumber pops.
- **Interview follow-ups:** stack using two queues; queue using two stacks with amortized O(1) operations; discuss how operation frequency determines the best trade-off.

> **MEMORY TRICK —** Insert newest LAST, rotate OLD behind it, remove newest FIRST.

[↑ Back to Index](#navigation-and-index)

---

<a id="s4-queue-using-two-stacks"></a>
## S4. Queue Using Two Stacks

[← S3](#s3-stack-using-one-queue) · [Index](#navigation-and-index) · [S5 →](#s5-stack-using-linked-list)

### Detailed question understanding

Implement a **FIFO queue** using only the normal operations of **two LIFO stacks**.

The queue must support:

```text
push(x) / offer(x)  -> add x at the back
pop()               -> remove and return the front
peek()              -> return the front without removing it
empty()             -> whether no elements remain
```

The central mismatch is:

```text
Queue wants oldest item first.
Stack exposes newest item first.
```

The problem therefore asks us to use **one reversal of order to cancel another reversal**.

Canonical problem statements such as LeetCode 232 allow only stack-style operations: push to top, peek/pop from top, size, and emptiness checks. Take U Forward presents the same FIFO API with two stacks.

### Four examples before the algorithm

| # | Operations | Important states | Result |
|---:|---|---|---|
| 1 | push(4), push(8), pop(), peek() | oldest value 4 must leave before 8 | pop = 4, peek = 8 |
| 2 | push(1), push(2), push(3), pop(), pop() | one transfer should serve multiple pops | 1, then 2 |
| 3 | push(1), push(2), pop(), push(3), peek() | new pushes must not jump ahead of older items already waiting in output stack | peek = 2 |
| 4 | empty() on a new queue | both stacks are empty | true |

### Visual model

Use two stacks:

```text
          NEW ITEMS                           OLD ITEMS READY TO LEAVE
             in                                      out

push(1)      1
push(2)      2
             1
push(3)      3
             2
             1

When front is requested and out is empty:

move all in -> out

in: []                                      out:
                                               1  <- queue front
                                               2
                                               3

The transfer reverses the LIFO order,
so the oldest queue element becomes the stack top.
```

### Pattern recognition

Use this pattern when:

- a FIFO interface must be built from LIFO primitives;
- a costly reversal can be **deferred until needed**;
- after one expensive rebuild, many future operations can reuse the rebuilt state;
- the interviewer asks for **amortized O(1)** queue operations.

> **KEY INTUITION —** Keep new elements in one stack. Move them to the second stack **only when the second stack is empty**. Never disturb older elements that are already in dequeue order.

### Brute-force / eager two-stack approach

A straightforward two-stack solution can make every push expensive:

1. move every element from stack A to stack B;
2. push the new value into A;
3. move everything from B back to A.

Then A's top is always the queue front.

That works, but every push may move O(n) elements.

For n pushes:

```text
1 + 2 + 3 + ... + n = O(n^2)
```

The repeated work is obvious: the same old elements are moved back and forth after every insertion.

### Optimized lazy-transfer approach

Maintain:

```text
in  = newly pushed elements, newest on top
out = elements already reversed into queue-removal order
```

Rules:

1. **push(x)** -> push only into `in`.
2. **peek/pop**:
   - if `out` is nonempty, use it directly;
   - otherwise move every element from `in` to `out` once.
3. **empty()** -> both stacks must be empty.

### Invariant

At all times:

- every element in `out` is **older** than every element in `in`;
- the top of `out`, when it exists, is the queue front;
- transfer happens only when `out` is empty, so newly pushed items can never overtake older waiting items.

### Detailed dry run

Operations:

```text
push(10)
push(20)
push(30)
pop()
push(40)
peek()
pop()
pop()
peek()
```

| Step | Operation | in stack (top first) | out stack (top first) | What happens | Result |
|---:|---|---|---|---|---|
| 1 | push(10) | [10] | [] | append new item to in | — |
| 2 | push(20) | [20,10] | [] | append new item to in | — |
| 3 | push(30) | [30,20,10] | [] | append new item to in | — |
| 4 | pop() | [] | [10,20,30] -> [20,30] | out empty, transfer all once; pop oldest | 10 |
| 5 | push(40) | [40] | [20,30] | do **not** transfer; 20 is still older | — |
| 6 | peek() | [40] | [20,30] | read out top | 20 |
| 7 | pop() | [40] | [30] | pop out top | 20 |
| 8 | pop() | [40] | [] | pop out top | 30 |
| 9 | peek() | [] | [40] | out empty now, transfer in -> out | 40 |

The most important state is step 5:

```text
in  = [40]
out = [20,30]
```

It is **wrong** to transfer 40 into out here. Queue order says 20 and 30 must leave before 40.

### Java implementation

Use `ArrayDeque` as a stack through only stack operations `push/pop/peek`.

```java
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.NoSuchElementException;

final class MyQueue {
    private final Deque<Integer> in = new ArrayDeque<>();
    private final Deque<Integer> out = new ArrayDeque<>();

    public void push(int x) {
        in.push(x);
    }

    public int pop() {
        moveIfNeeded();

        if (out.isEmpty()) {
            throw new NoSuchElementException("queue is empty");
        }

        return out.pop();
    }

    public int peek() {
        moveIfNeeded();

        if (out.isEmpty()) {
            throw new NoSuchElementException("queue is empty");
        }

        return out.peek();
    }

    public boolean empty() {
        return in.isEmpty() && out.isEmpty();
    }

    private void moveIfNeeded() {
        if (!out.isEmpty()) {
            return;
        }

        while (!in.isEmpty()) {
            out.push(in.pop());
        }
    }
}
```

### Key lines to highlight

```java
if (!out.isEmpty()) {
    return;
}
```

This is what makes the implementation **lazy**.

If we transferred whenever `in` contained something, a newly pushed value could interfere with older elements already in `out`.

And:

```java
while (!in.isEmpty()) {
    out.push(in.pop());
}
```

This reverses arrival order exactly once for that batch.

### Correctness reasoning

**Invariant:** if `out` is nonempty, its top is the oldest element in the entire queue.

- Initially both stacks are empty, so the invariant is true.
- `push(x)` places x in `in`. Because x is newest, it must come after every element already in `out`; the invariant remains true.
- If `out` is empty, transferring all of `in` reverses LIFO order. The oldest element from `in` becomes the top of `out`.
- `pop` removes exactly that oldest element.
- Since transfer occurs only when `out` is empty, no newer element can be placed ahead of an older element already waiting in `out`.

Therefore every pop/peek observes FIFO order.

### Complexity — amortized, not just worst-case

A single `pop()` may trigger a transfer of k elements, so **one call can be O(k)**.

But examine one element x over its entire lifetime:

```text
push into in       -> once
pop from in        -> at most once
push into out      -> at most once
pop from out       -> once
```

No element ever moves from `out` back to `in`.

Across m queue operations, each element pays for a constant number of stack operations.

Therefore:

```text
push       O(1) worst-case
peek/pop   O(1) amortized, O(n) worst-case for one transfer-triggering call
empty      O(1)
space      O(n)
```

### Boundary conditions

- **new queue** -> `empty() == true`;
- **one element** -> first pop returns it and both stacks become empty;
- **interleaved push after pop** -> do not transfer while `out` still contains older values;
- **peek followed by pop** -> both must return the same front value unless another mutation occurs;
- **empty pop/peek** -> decide API contract explicitly; this implementation throws `NoSuchElementException`;
- `ArrayDeque` does not permit null elements, which fits integer interview versions naturally.

### Common wrong approaches

1. **Move elements on every pop and move them back afterward.**  
   Correct but repeatedly re-reverses the same items.

2. **Transfer from in while out is nonempty.**  
   Breaks FIFO order because new elements can overtake old ones.

3. **Use queue operations on the deques.**  
   That violates the spirit of the problem; treat both deques strictly as stacks.

4. **Claim every pop is O(1) worst-case.**  
   The correct statement is O(1) **amortized**.

### Memory trick

> **IN collects. OUT serves. Transfer only when OUT is empty.**

Or even shorter:

```text
NEW -> IN
OLD -> OUT
EMPTY OUT? FLIP ONCE
```

### Interview follow-ups

- Implement the opposite transformation: **stack using queues**.
- Add `size()` in O(1) using `in.size() + out.size()`.
- Explain why this is an example of amortized analysis.
- Compare with a real `ArrayDeque` queue and explain why the two-stack version is educational rather than the preferred production queue representation.

[↑ Back to Index](#navigation-and-index)

---

<a id="s5-stack-using-linked-list"></a>
## S5. Stack Using Linked List

[← S4](#s4-queue-using-two-stacks) · [Index](#navigation-and-index) · [S6 →](#s6-queue-using-linked-list)

### Exact problem and API contract

Implement an integer **LIFO stack** using a **singly linked list**, without a fixed capacity. Provide **push(int)**, **pop()**, **peek()**, **size()**, **isEmpty()**. Pop and peek on empty throw **NoSuchElementException**. Each push allocates one node (subject to available memory).

### Four concrete input/output examples

| # | Operations | Output / behavior |
|---:|---|---|
| 1 | push(10), push(20), push(30), peek(), pop(), pop(), pop() | peek=30; pops **30,20,10** |
| 2 | pop(), peek() on new stack | both throw **NoSuchElementException** |
| 3 | push(-7), push(-7), size(), pop(), pop() | size=2; pops **-7,-7** |
| 4 | push(2147483647), pop(), push(5), peek(), isEmpty() | pop=2147483647; peek=5; false |

### Pattern recognition, baseline, and optimized invariant

**Clue:** dynamic size and newest-first removal. A naive singly linked list that pushes at its tail and keeps only a head pointer must scan for the tail's predecessor when popping: O(n). Instead treat **head as top**. Push **prepends** a node, pop **removes head**: both O(1). **Invariant:** following head → next → ... → null visits every live stack value in **top-to-bottom order**; size equals the number of reachable nodes, and head is null exactly when size is zero.

### Pointer diagram

~~~mermaid
flowchart LR
  H["head"] --> A["30"] --> B["20"] --> C["10"] --> N["null"]
  X["pop returns 30"] --> Y["head = old head.next"] --> Z["20 → 10 → null"]
~~~

### Detailed state trace

| Step | Operation | Head → tail | size | Result |
|---:|---|---|---:|---|
| 0 | initialize | null | 0 | — |
| 1 | push(10) | 10 → null | 1 | — |
| 2 | push(20) | 20 → 10 → null | 2 | — |
| 3 | push(30) | 30 → 20 → 10 → null | 3 | — |
| 4 | peek() | 30 → 20 → 10 → null | 3 | 30 |
| 5 | pop() | 20 → 10 → null | 2 | 30 |
| 6 | pop() | 10 → null | 1 | 20 |
| 7 | push(-7) | -7 → 10 → null | 2 | — |
| 8 | pop() | 10 → null | 1 | -7 |
| 9 | pop() | null | 0 | 10 |

### Java 17 implementation

~~~java
import java.util.NoSuchElementException;

public final class LinkedStack {
    private static final class Node {
        final int value;
        Node next;
        Node(int value, Node next) {
            this.value = value;
            this.next = next;
        }
    }
    private Node head;
    private int size;

    public void push(int value) {
        head = new Node(value, head);
        size++;
    }
    public int pop() {
        if (head == null) throw new NoSuchElementException("stack empty");
        int result = head.value;
        head = head.next;
        size--;
        return result;
    }
    public int peek() {
        if (head == null) throw new NoSuchElementException("stack empty");
        return head.value;
    }
    public int size() { return size; }
    public boolean isEmpty() { return head == null; }
}
~~~

**Key line:** **head = new Node(value, head)** retains the entire prior chain. On pop, save the value before moving head. The old node becomes garbage-collectible when no longer referenced.

### Correctness proof

Initially head=null and size=0 represent the empty stack. Prepending a node makes the latest value the head while preserving every prior value and its order. Removing head returns exactly that latest value and exposes the next newest node. Peek changes nothing. Updating size by one on push/pop maintains the count. Induction over the operation sequence proves LIFO behavior.

### Complexity, boundaries, and follow-ups

**push:** O(1) time and one O(1)-size node allocation; **pop/peek/size/isEmpty:** O(1) time and O(1) extra space; **n live elements:** O(n) memory. Check empty operations; one-node pop; repeated values; extreme integers; push after empty; memory exhaustion. **Common mistakes:** appending at tail then trying to pop in O(1) with only a singly linked head; dropping the old chain by assigning head without linking; decrementing size without moving head. Compared with S1, a linked stack grows without array resizing but incurs per-node allocations and pointer overhead. **Interview extensions:** generic stack, ArrayDeque comparison, lock-free stack ABA concerns, and why tail removal is not O(1) in a singly linked list without predecessor information.

> **Memory trick:** **HEAD IS TOP; PUSH PREPENDS, POP DETACHES.**

[↑ Back to Index](#navigation-and-index)

---

<a id="s6-queue-using-linked-list"></a>
## S6. Queue Using Linked List

[← S5](#s5-stack-using-linked-list) · [Index](#navigation-and-index) · [S7 →](#s7-balanced-parentheses)

### Exact problem and API contract

Implement a dynamically sized **FIFO queue of integers** using a **singly linked list**, without calling a built-in queue. Expose **offer(int)**, **poll()**, **peek()**, **size()**, and **isEmpty()**. In this guide, polling or peeking an empty queue throws **NoSuchElementException**. Unlike the bounded circular-array queue in S2, there is no fixed capacity; allocation can still fail if memory is exhausted.

**What the interviewer checks:** A new value goes at the **tail**, and the oldest value leaves from the **head**. Crucially, removing the final node must clear **both** pointers, or the next insertion can corrupt the queue.

### Four concrete examples

| # | Operations | Expected result | Why it matters |
|---:|---|---|---|
| 1 | offer(10), offer(20), offer(30), peek(), poll(), offer(40), poll(), poll(), poll() | peek=10; polls **10,20,30,40**; final size=0 | FIFO with interleaving |
| 2 | poll(), peek() on a new queue | both throw **NoSuchElementException** | Empty contract |
| 3 | offer(-7), offer(-7), size(), poll(), poll(), isEmpty() | size=2; polls **-7,-7**; true | Duplicates and one-node transition |
| 4 | offer(-2147483648), poll(), offer(2147483647), peek(), poll() | -2147483648; peek=2147483647; poll=2147483647 | Extreme integers and reuse after empty |

### Pattern recognition: baseline versus optimized representation

A correct but inefficient singly linked queue can keep only **head** and append each new node by traversing the list: **offer O(n)**, poll O(1). Alternatively, append at the head and remove from the tail, but removing a singly linked tail requires finding its predecessor: **poll O(n)**. Both approaches repeatedly scan nodes.

**Optimization:** retain **head and tail**. The head is the next node to remove; tail is the final node to append after. No traversal is required for either operation.

**Invariant:** If size=0, **head == null and tail == null**. If size>0, head is the oldest live node, tail is the newest, **tail.next == null**, and following next from head visits exactly size nodes in arrival order.

### Visual: pointer ownership, not array indices

~~~mermaid
flowchart LR
  H["head: oldest"] --> A["10"] --> B["20"] --> C["30"] --> N["null"]
  T["tail: newest"] -.-> C
  P["poll returns 10"] --> H2["head moves to 20"]
  O["offer 40"] --> T2["old tail.next = 40; tail = 40"]
~~~

**One-element special case:** after poll, head becomes null, so set tail=null too. For the next offer, set head and tail to the same new node.

### Detailed state trace

| Step | Operation | Head → tail | head | tail | size | Output |
|---:|---|---|---|---|---:|---|
| 0 | initialize | [] | null | null | 0 | — |
| 1 | offer(10) | 10 | 10 | 10 | 1 | — |
| 2 | offer(20) | 10 → 20 | 10 | 20 | 2 | — |
| 3 | offer(30) | 10 → 20 → 30 | 10 | 30 | 3 | — |
| 4 | peek() | 10 → 20 → 30 | 10 | 30 | 3 | 10 |
| 5 | poll() | 20 → 30 | 20 | 30 | 2 | 10 |
| 6 | offer(40) | 20 → 30 → 40 | 20 | 40 | 3 | — |
| 7 | poll() | 30 → 40 | 30 | 40 | 2 | 20 |
| 8 | poll() | 40 | 40 | 40 | 1 | 30 |
| 9 | poll() | [] | null | null | 0 | 40 |
| 10 | offer(50) | 50 | 50 | 50 | 1 | — |

At step 9, leaving tail pointing to the removed node is a bug: step 10 would attach a node to a stale tail while head might still be null.

### Java 17 implementation — standalone LinkedQueue.java

~~~java
import java.util.NoSuchElementException;

public final class LinkedQueue {
    private static final class Node {
        final int value;
        Node next;
        Node(int value) { this.value = value; }
    }
    private Node head, tail;
    private int size;

    public void offer(int value) {
        Node node = new Node(value);
        if (tail == null) head = node;
        else tail.next = node;
        tail = node;
        size++;
    }
    public int poll() {
        if (head == null) throw new NoSuchElementException("queue empty");
        int answer = head.value;
        head = head.next;
        size--;
        if (head == null) tail = null;
        return answer;
    }
    public int peek() {
        if (head == null) throw new NoSuchElementException("queue empty");
        return head.value;
    }
    public int size() { return size; }
    public boolean isEmpty() { return head == null; }
}
~~~

**Key lines:** **tail.next = node** links the new node after all earlier arrivals; **tail = node** moves the append endpoint. **if (head == null) tail = null** restores the empty-state invariant. Do not detach head before saving its value.

### Correctness proof and complexity

**Base:** both pointers null and size zero describe the empty FIFO sequence. **Offer:** appends exactly one new node after the previous tail (or creates the only node), so all existing nodes stay in order and the new value is newest. **Poll:** returns the head value and advances to the next oldest; if no nodes remain, both endpoints become null. **Peek:** observes head without changing the sequence. Induction over operations proves FIFO behavior and the pointer invariant.

**Complexity:** offer/poll/peek/size/isEmpty are **O(1) worst-case** pointer/count work per call; each offer allocates one O(1)-size node. Total live storage **O(n)** for n enqueued elements. This is unlike an array-based queue: no resizing/copying is needed, but each node adds allocation and pointer overhead.

### Boundaries, mistakes, and follow-ups

Check empty poll/peek; single-node insertion and removal; append after becoming empty; duplicate values; extreme integers; long alternating offer/poll sequences; and garbage collection after dropping the last reference. Common errors: updating tail but not head on first insert; forgetting tail=null on last removal; using tail.next before testing tail; returning tail rather than head; or claiming a singly linked tail can be removed in O(1) without predecessor information.

**Interview variations:** generic queue with type parameter; compare circular array locality versus linked nodes; implement a deque using a doubly linked list; discuss why a non-thread-safe two-pointer queue is not suitable for concurrent producers/consumers without synchronization.

> **Memory trick:** **ENQUEUE AT TAIL, DEQUEUE AT HEAD; EMPTY MEANS BOTH NULL.**

[↑ Back to Index](#navigation-and-index)

---

<a id="s7-balanced-parentheses"></a>
## S7. Balanced Parentheses

[← S6](#s6-queue-using-linked-list) · [Index](#navigation-and-index) · [S8 →](#s8-min-stack)

### Detailed question understanding

**What is the problem/lesson asking?** Balanced Parentheses. Rewrite the exact input state, legal operation/relationship, and requested output before choosing a technique. Similar titles often hide materially different variants—for example nearest greater versus count of all greater values, four-direction versus eight-direction grid adjacency, or node identity versus equal node values.

### Three requirement-clarifying examples

| # | Input / setup | Output / observation | Why it matters |
|---:|---|---|---|
| 1 | `{[()]}` | `valid` | proper nesting |
| 2 | `([)]` | `invalid` | order matters |
| 3 | `(((` | `invalid` | unfinished openings |

### Pattern recognition

**Primary pattern:** Stack parsing

> **KEY INTUITION —** Nesting and precedence require remembering the most recent unresolved opening/operator/expression.

### Brute-force / straightforward baseline

Start with the direct simulation/repeated scan that mirrors the statement. It is valuable because it exposes **what is being recomputed**. Then ask: *what exact fact from earlier work would make the next decision immediate?*

### Optimized reasoning

Name the invariant before code. The optimized solution for this family is derived from the intuition above. Every state change must preserve the invariant; if you cannot say what the working structure means after processing the first *i* items/nodes/states, the implementation is not ready.

### Detailed dry run

Write index/value, stack/queue/deque before, all pops/removals, insertion, and answer after each iteration.

For interview practice, include at least four snapshots: initial state, first nontrivial state change, the state where the central invariant does real work, and final state. Explain **why each mutation occurred**, not merely what the values became.

### Java implementation / template

```java
static boolean valid(String s) {
    Deque<Character> st = new ArrayDeque<>();
    for (char c : s.toCharArray()) {
        if (c=='(' || c=='[' || c=='{') st.push(c);
        else {
            if (st.isEmpty()) return false;
            char o=st.pop();
            if ((c==')'&&o!='(')||(c==']'&&o!='[')||(c=='}'&&o!='{')) return false;
        }
    }
    return st.isEmpty();
}
```

### Key line to highlight

Find the line that changes the invariant: the monotonic `while`, a graph relaxation `if`, a pointer splice, a recursive return combination, or a HashMap lookup/update. Explain what would break if its comparison or ordering changed.

### Correctness checklist

1. State the invariant before the loop/recursion.
2. Show it is true initially.
3. Show every transition preserves it.
4. Explain why the terminal state implies the requested answer.
5. For greedy algorithms, identify the exchange/cut/monotonic argument that makes the local choice safe.

### Boundary conditions

- empty/null input when the platform permits it;
- one element/node/state;
- duplicates and strict-versus-nonstrict comparisons;
- monotone/skewed/disconnected shape where relevant;
- overflow in sums, products, distances and path counts—prefer `long` when needed;
- value vs index vs node identity;
- online/streaming input versus offline preprocessing;
- repeated queries may justify preprocessing that a one-shot query does not.

### Complexity — derive it instead of memorizing it

**O(n) time and O(n) stack.**

Count how many times each element/node/edge can enter and leave the working structure, then multiply by the operation cost: array/index O(1), expected hash O(1), balanced tree/heap O(log n), full edge scan O(E).

### Memoization / repeated-work note

Memoization is useful only when the same logical state can be solved repeatedly. In many one-pass stack/list/tree problems there are no overlapping subproblems; the data structure itself stores the minimal reusable history. In graph, prefix-hash, and repeated-query problems, `visited`, `dist`, prefix maps, parent maps, or cached subtree metadata play the “do not recompute solved state” role.

> **MEMORY TRICK —** MOST RECENT UNRESOLVED ITEM GOES ON THE STACK.

### Interview follow-ups and variations

1. Can auxiliary space be reduced, and what invariant replaces it?
2. What changes if equality/duplicate semantics change?
3. What changes if input arrives online?
4. What changes for many repeated queries?
5. Name two related problems that reuse this invariant with only one comparison or one state dimension changed.

[↑ Back to Index](#navigation-and-index)

---

<a id="s8-min-stack"></a>
## S8. Min Stack

[← S7](#s7-balanced-parentheses) · [Index](#navigation-and-index) · [S9 →](#s9-infix-to-postfix)

### Exact problem and API contract

Design an integer **LIFO stack** supporting **push(int)**, **pop()**, **top()**, and **getMin()** in **O(1) worst-case time per operation**, even after arbitrary pushes and pops. This study version also exposes size() and isEmpty(); pop/top/getMin on an empty stack throw **NoSuchElementException**. The minimum is among **currently present** elements, not all values ever pushed.

### Four concrete examples

| # | Operations | Expected result | What it tests |
|---:|---|---|---|
| 1 | push(-2), push(0), push(-3), getMin(), pop(), top(), getMin() | **-3, -3, 0, -2** | Minimum must restore after pop |
| 2 | push(5), push(5), getMin(), pop(), getMin(), pop(), isEmpty() | **5, 5, 5, true** | Duplicate minima |
| 3 | push(8), push(3), push(7), getMin(), pop(), getMin(), pop(), getMin() | **3, 7, 3, 3, 8** | Removing nonminimum then minimum |
| 4 | push(2147483647), push(-2147483648), getMin(), pop(), getMin() | **-2147483648, -2147483648, 2147483647** | Integer extremes |

**Empty:** pop(), top(), getMin() each throw NoSuchElementException. An empty string or sentinel integer is not an acceptable stand-in for the minimum of an empty stack.

### Pattern recognition, baseline, and optimization

**Clue:** ordinary stack operations are already O(1), but the interviewer also requires the minimum **after undoing the latest push**.

**Baseline:** keep a normal stack and scan all live values for getMin(): O(n) per query. Maintaining one global minimum variable fails when the minimum is popped: its predecessor minimum is lost. A second minimum stack works, but a **pair per stack entry** is especially easy to reason about.

**Optimized invariant:** store **(value, minSoFar)** for each entry. The minSoFar at the top is exactly the minimum of all live entries. On push(x), newMin = min(x, previousTop.minSoFar), or x for the first push. Pop removes both the value and its saved minimum, automatically revealing the prior minimum.

### Mermaid: saved minima travel with stack history

~~~mermaid
flowchart LR
  A["push 5 → (5,5)"] --> B["push 2 → (2,2) on top"]
  B --> C["push 8 → (8,2) on top"]
  C --> D["pop 8 → top (2,2)"]
  D --> E["pop 2 → top (5,5)"]
~~~

The **second number** is not the current input value; it is the minimum of the stack prefix ending at that entry.

### Detailed dry run

| Step | Operation | Stack top → bottom: (value,minSoFar) | Returned | Current min |
|---:|---|---|---|---|
| 0 | new MinStack | [] | — | undefined |
| 1 | push(5) | [(5,5)] | — | 5 |
| 2 | push(2) | [(2,2), (5,5)] | — | 2 |
| 3 | push(8) | [(8,2), (2,2), (5,5)] | — | 2 |
| 4 | getMin() | unchanged | 2 | 2 |
| 5 | pop() | [(2,2), (5,5)] | 8 | 2 |
| 6 | pop() | [(5,5)] | 2 | 5 |
| 7 | push(1) | [(1,1), (5,5)] | — | 1 |
| 8 | top() | unchanged | 1 | 1 |
| 9 | pop() | [(5,5)] | 1 | 5 |
| 10 | pop() | [] | 5 | undefined |

**Why duplicates matter:** pushing 5 twice stores [(5,5),(5,5)]. After popping one, getMin remains 5. A minimum stack that only records strictly decreasing minima must carefully preserve counts.

### Java 17 implementation — standalone MinStack.java

~~~java
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.NoSuchElementException;

public final class MinStack {
    private static final class Entry {
        final int value, minSoFar;
        Entry(int value, int minSoFar) {
            this.value = value;
            this.minSoFar = minSoFar;
        }
    }
    private final Deque<Entry> stack = new ArrayDeque<>();

    public void push(int value) {
        int nextMin = stack.isEmpty() ? value : Math.min(value, stack.peek().minSoFar);
        stack.push(new Entry(value, nextMin));
    }
    public int pop() {
        if (stack.isEmpty()) throw new NoSuchElementException("stack empty");
        return stack.pop().value;
    }
    public int top() {
        if (stack.isEmpty()) throw new NoSuchElementException("stack empty");
        return stack.peek().value;
    }
    public int getMin() {
        if (stack.isEmpty()) throw new NoSuchElementException("stack empty");
        return stack.peek().minSoFar;
    }
    public int size() { return stack.size(); }
    public boolean isEmpty() { return stack.isEmpty(); }
}
~~~

**Key line:** **Math.min(value, stack.peek().minSoFar)** makes every entry carry the minimum for its whole surviving prefix. **Pop removes the stored minimum with the value**, so no rescan or recomputation is needed.

### Correctness proof and complexity

For the empty stack the invariant is vacuous. Suppose the top entry stores the minimum of all n current values. After pushing x, min(x, oldMin) is exactly the minimum of the n+1 values; storing it with x preserves the invariant. Pop removes that entry and exposes the previous top, whose saved min was computed before the removed push, so it remains correct. Therefore getMin reads the true live minimum at every nonempty state. Top and pop obey ordinary LIFO semantics.

**push:** O(1) amortized time because ArrayDeque may resize; **pop, top, getMin, size, isEmpty:** O(1) time. One O(1)-size Entry is created per push. **Space:** O(n) for n live entries (value plus minimum metadata), O(1) extra work space per call. Using a balanced tree or sorting would add unnecessary log n or n work.

### Advanced variant: reversible encoded minimum (optional)

An older project workbook describes a **single stack of long values plus one current minimum**. When a new x is below min, push a marker **2*x - min** and update min=x. The marker is smaller than the new minimum, so it is distinguishable from ordinary values. On pop of a marker, the real popped value is min and the old minimum is **2*min - marker**. This uses **O(1) auxiliary metadata beyond the stored stack** and O(1) operations, but the algebra is less intuitive than entry pairs.

**Use long internally for all encoded arithmetic.** With int values at both extremes, 2*x-min can overflow int. The entry-pair implementation above avoids this pitfall and is the recommended first interview explanation.

### Boundaries, common mistakes, and follow-ups

Test empty operations; one value; duplicate minimum values; removing a minimum and restoring the previous one; negative and extreme int values; interleaved top/getMin; and pushes after the stack becomes empty. Mistakes include retaining only a global minimum, forgetting to save the previous minimum, comparing against the wrong prefix, confusing top with min, and incorrectly claiming O(1) getMin while scanning.

**Interview variations:** two-stack minima with duplicate counts; reversible encoded markers; max stack; minimum queue using two min-stacks; sliding-window minimum using a monotonic deque. For the **minimum queue**, each transfer must preserve the minimum summaries on both stacks.

> **Memory trick:** **EACH ENTRY REMEMBERS THE MINIMUM AT ITS PUSH TIME.**

[↑ Back to Index](#navigation-and-index)

---

<a id="s9-infix-to-postfix"></a>
## S9. Infix to Postfix

[← S8](#s8-min-stack) · [Index](#navigation-and-index) · [S10 →](#s10-prefix-to-infix)

### Exact problem and input contract

Convert a **valid infix arithmetic expression** (operators between operands) into equivalent **postfix / Reverse Polish notation** (operators after operands), without evaluating it. Accept identifiers such as `total_1`, unsigned integers such as `12`, parentheses and binary `+ - * / ^`. Whitespace is optional. Operators `^` (highest), `* /`, `+ -` have descending precedence; `^` is **right-associative**, all others left-associative. Unary minus, implicit multiplication and function calls are **not supported**. Return space-separated tokens, which avoids ambiguity for multi-character operands. Malformed expressions throw `IllegalArgumentException`.

### Verified input/output examples

| Infix input | Postfix output | Key point |
|---|---|---|
| `A+B*C` | `A B C * +` | Multiply before add |
| `A^B^C` | `A B C ^ ^` | Right-associative exponent: `A^(B^C)` |
| `(12+3)*4` | `12 3 + 4 *` | Parentheses override precedence |
| `a-b-c` | `a b - c -` | Left-associative subtraction |
| `foo + bar*2` | `foo bar 2 * +` | Multi-character identifiers |

Invalid: `A+`, `A B`, `A+*B`, `()`, `A(B)`, `(A+B`, `A+B)`, `A$B`.

### Baseline and why a stack improves it

A baseline is to parse the expression into an expression tree, then perform postorder traversal. That is O(n) time and O(n) space but builds a whole tree. The **shunting-yard** stack method emits postfix directly with one operator stack and output list; each token enters/exits the stack at most once.

### Invariant and optimized algorithm

After scanning the first `i` tokens, the output contains completed postfix subexpressions in left-to-right evaluation order; the operator stack holds **pending operators and unmatched opening parentheses**. When a new binary operator `op` arrives, pop operators of **higher precedence**, or of **equal precedence when `op` is left-associative**. For `^`, do not pop an equal-precedence `^`. A closing parenthesis drains operators until its matching opening parenthesis; neither parenthesis appears in the output. `needOperand` checks alternation between operands/operators and rejects invalid syntax.

### Detailed trace — `(12+3)*4`

Operator stack shown **bottom → top**.

| Read | Output tokens | Operator stack | Why |
|---|---|---|---|
| start | — | — | Nothing consumed |
| `(` | — | `(` | New grouping boundary |
| `12` | `12` | `(` | Operand emitted immediately |
| `+` | `12` | `( +` | Pending binary operator |
| `3` | `12 3` | `( +` | Operand emitted |
| `)` | `12 3 +` | — | Drain through matching `(` |
| `*` | `12 3 +` | `*` | Pending multiplication |
| `4` | `12 3 + 4` | `*` | Operand emitted |
| end | `12 3 + 4 *` | — | Drain remaining operators |

```mermaid
flowchart LR
    A["Infix tokens"] --> B{"Operand?"}
    B -->|yes| C["Append to output"]
    B -->|no| D{"Parenthesis?"}
    D -->|opening| E["Push '('"]
    D -->|closing| F["Pop operators until '('"]
    D -->|operator| G["Pop higher/equal precedence as associativity allows; push operator"]
    C --> H["Next token"]
    E --> H
    F --> H
    G --> H
```

### Java 17 — standalone implementation

```java
import java.util.*;
public final class InfixToPostfix {
    private static boolean operand(String t) {
        return t.matches("[A-Za-z_][A-Za-z0-9_]*|[0-9]+");
    }
    private static int precedence(String op) {
        return switch (op) {
            case "+", "-" -> 1;
            case "*", "/" -> 2;
            case "^" -> 3;
            default -> throw new IllegalArgumentException("Operator: " + op);
        };
    }
    private static List<String> tokenize(String input) {
        if (input == null) throw new IllegalArgumentException("null expression");
        List<String> tokens = new ArrayList<>();
        for (int i = 0; i < input.length();) {
            char c = input.charAt(i);
            if (Character.isWhitespace(c)) { i++; continue; }
            if (Character.isLetter(c) || c == '_' || Character.isDigit(c)) {
                int start = i++;
                if (Character.isDigit(c)) {
                    while (i < input.length() && Character.isDigit(input.charAt(i))) i++;
                } else {
                    while (i < input.length() && (Character.isLetterOrDigit(input.charAt(i))
                            || input.charAt(i) == '_')) i++;
                }
                String token = input.substring(start, i);
                if (!operand(token)) throw new IllegalArgumentException("Invalid operand: " + token);
                tokens.add(token);
            } else if ("()+-*/^".indexOf(c) >= 0) {
                tokens.add(String.valueOf(c)); i++;
            } else throw new IllegalArgumentException("Unexpected character: " + c);
        }
        return tokens;
    }
    public static String convert(String expression) {
        List<String> tokens = tokenize(expression);
        if (tokens.isEmpty()) throw new IllegalArgumentException("Empty expression");
        Deque<String> ops = new ArrayDeque<>();
        List<String> out = new ArrayList<>();
        boolean needOperand = true;
        for (String t : tokens) {
            if (operand(t)) {
                if (!needOperand) throw new IllegalArgumentException("Missing operator");
                out.add(t); needOperand = false;
            } else if (t.equals("(")) {
                if (!needOperand) throw new IllegalArgumentException("Missing operator before (");
                ops.push(t);
            } else if (t.equals(")")) {
                if (needOperand) throw new IllegalArgumentException("Missing operand before )");
                while (!ops.isEmpty() && !ops.peek().equals("(")) out.add(ops.pop());
                if (ops.isEmpty()) throw new IllegalArgumentException("Unmatched )");
                ops.pop(); needOperand = false;
            } else {
                if (needOperand) throw new IllegalArgumentException("Missing left operand");
                while (!ops.isEmpty() && !ops.peek().equals("(") &&
                       (precedence(ops.peek()) > precedence(t) ||
                        (precedence(ops.peek()) == precedence(t) && !t.equals("^")))) {
                    out.add(ops.pop());
                }
                ops.push(t); needOperand = true;
            }
        }
        if (needOperand) throw new IllegalArgumentException("Trailing operator or empty ()");
        while (!ops.isEmpty()) {
            String t = ops.pop();
            if (t.equals("(")) throw new IllegalArgumentException("Unmatched (");
            out.add(t);
        }
        return String.join(" ", out);
    }
    public static void main(String[] args) {
        System.out.println(convert("(12+3)*4")); // 12 3 + 4 *
    }
}
```

**Key line:** `precedence(top) == precedence(incoming) && !incoming.equals("^")` implements left-associativity while preserving right-associative exponentiation. Reversing this condition changes `A^B^C` incorrectly.

### Correctness and complexity

By induction over consumed tokens, emitting an operand preserves postfix operand order; pushing a pending operator delays it until all operands of higher-precedence operations have been emitted. Popping on lower/equal incoming precedence (except equal right-associative `^`) ensures each operator appears after its operands and in the correct association order. Parentheses isolate grouped operations. Final stack draining emits every remaining operator exactly once, giving equivalent postfix.

**Time O(n)** for n input characters: lexical scanning is linear, and each token is pushed/popped at most once. **Auxiliary space O(n)** for output and operator stack (excluding output, operator stack O(n)). Parenthesis depth can reach n. No arithmetic is evaluated, so operand magnitude cannot overflow conversion.

### Common mistakes, boundaries and follow-ups

- Do not pop equal-precedence `^`; `A^B^C` must represent `A^(B^C)`.
- Reject unary `-A` under this binary-only contract; a unary-aware tokenizer needs an explicit unary operator.
- Never emit parentheses into postfix; reject unmatched or empty parentheses.
- Test one operand, deep nesting, long identifiers, whitespace, repeated equal-precedence operators and malformed syntax.
- Interview follow-ups: add unary operators/functions, build an AST, evaluate postfix, or convert postfix back to infix. For `n` tokens, repeated string concatenation may be quadratic; use a token list and `String.join`.

> **MEMORY TRICK:** **Operands go OUT; operators WAIT until precedence/parentheses permit.**

[↑ Back to Index](#navigation-and-index)

---


<a id="s10-prefix-to-infix"></a>
## S10. Prefix to Infix

[← S9](#s9-infix-to-postfix) · [Index](#navigation-and-index) · [S11 →](#s11-prefix-to-postfix)

### Exact problem and input contract

Convert a **prefix / Polish notation** expression (operator before its two operands) into an equivalent **fully parenthesized infix** expression. Input tokens must be **whitespace-separated**, including single-character operands; this allows multi-digit numbers and variable names. Supported operands: unsigned decimal integers or identifiers; supported binary operators: `+ - * / ^`. No unary operators. Return fully parenthesized infix preserving the exact expression tree. Malformed inputs throw `IllegalArgumentException`.

### Verified input/output examples

| Prefix input | Infix output | Interpretation |
|---|---|---|
| `* + A B C` | `((A+B)*C)` | Multiply the sum by C |
| `- A / B C` | `(A-(B/C))` | Operand order matters for subtraction/division |
| `^ A ^ B C` | `(A^(B^C))` | Explicit right grouping |
| `+ 12 * x 3` | `(12+(x*3))` | Multi-token identifiers/numbers |
| `A` | `A` | Single operand needs no parentheses |

Invalid: `+ A` (missing operand), `A B` (extra operand), `+ A B C` (extra operand), `? A B` (invalid token). Compact `*+ABC` is intentionally not accepted; write `* + A B C`.

### Baseline vs optimized stack

A direct recursive parser reads prefix **left-to-right**, recursively parses left and right subtrees, then returns `(left op right)`; it uses O(n) stack depth and builds an AST or nested strings. A reverse scan is iterative and uses a stack: read tokens **right-to-left**, push operands; on an operator pop the **left operand first**, then the **right**, combine, and push the parenthesized result.

### Invariant and detailed trace

After scanning a suffix of the prefix tokens right-to-left, each stack entry is the fully parenthesized infix form of **one complete subtree** of that suffix. The top two subtrees are the left/right children required by the next encountered operator.

Trace `* + A B C` (stack **bottom → top**):

| Token (reverse order) | Stack after operation | Explanation |
|---|---|---|
| start | — | Empty |
| `C` | `C` | Push operand |
| `B` | `C, B` | Push operand |
| `A` | `C, B, A` | Push operand |
| `+` | `C, (A+B)` | Pop left A, right B |
| `*` | `((A+B)*C)` | Pop left (A+B), right C |

```mermaid
flowchart LR
    A["Read prefix tokens from RIGHT to LEFT"] --> B{"Operand?"}
    B -->|yes| C["Push operand string"]
    B -->|no, operator| D["Pop LEFT, then RIGHT"]
    D --> E["Push (LEFT op RIGHT)"]
    C --> F["Continue"]
    E --> F
    F --> G["End: exactly one expression"]
```

### Java 17 — standalone implementation

```java
import java.util.*;
public final class PrefixToInfix {
    private static boolean operand(String t) {
        return t.matches("[A-Za-z_][A-Za-z0-9_]*|[0-9]+");
    }
    private static boolean operator(String t) {
        return t.length() == 1 && "+-*/^".contains(t);
    }
    public static String convert(String expression) {
        if (expression == null || expression.isBlank())
            throw new IllegalArgumentException("Empty expression");
        String[] tokens = expression.trim().split("\\s+");
        Deque<String> stack = new ArrayDeque<>();
        for (int i = tokens.length - 1; i >= 0; i--) {
            String t = tokens[i];
            if (operand(t)) stack.push(t);
            else if (operator(t)) {
                if (stack.size() < 2)
                    throw new IllegalArgumentException("Missing operand for " + t);
                String left = stack.pop(), right = stack.pop();
                stack.push("(" + left + t + right + ")");
            } else throw new IllegalArgumentException("Invalid token: " + t);
        }
        if (stack.size() != 1) throw new IllegalArgumentException("Extra operands");
        return stack.pop();
    }
    public static void main(String[] args) {
        System.out.println(convert("* + A B C")); // ((A+B)*C)
    }
}
```

**Key lines:** `String left = stack.pop(), right = stack.pop();` — **do not reverse them**. For `- A / B C`, reversing changes the meaning. Full parentheses make associativity unambiguous.

### Correctness and complexity

Base case: a pushed operand is a correct infix representation of a one-node subtree. Inductive step: when scanning an operator right-to-left, the two top entries are already-correct representations of its left and right operand subtrees. Combining `(left op right)` preserves their exact tree and operation order. After consuming the entire valid prefix expression, one entry represents the whole tree; any other stack size indicates invalid arity.

**Time O(n + L)** for n tokens and total produced string length L if concatenation cost is accounted for; repeated immutable string concatenation can make a deeply nested expression **O(n²) time** in the worst case. **Space O(n + L)** for stack and strings (and intermediate copies may increase peak memory). For strict linear-time construction, build an AST and serialize once with `StringBuilder`. This simple stack version prioritizes clarity.

### Mistakes, boundaries and interview follow-ups

- Reverse scan, not forward scan, for the simple operand stack.
- Pop **left before right**; subtraction and division expose the error.
- Input `A` is valid; empty input and insufficient/excess operands are invalid.
- Operators are binary; `- 5` is invalid, while `- 0 5` is valid.
- Ask: how to support unary operators, reconstruct an AST, remove redundant parentheses, or convert prefix to postfix?

> **MEMORY TRICK:** **Scan BACKWARD; pop LEFT first; wrap every binary combination.**

[↑ Back to Index](#navigation-and-index)

---


<a id="s11-prefix-to-postfix"></a>
## S11. Prefix to Postfix

[← S10](#s10-prefix-to-infix) · [Index](#navigation-and-index) · [S12 →](#s12-postfix-to-prefix)

### Detailed question understanding

**What is the problem/lesson asking?** Prefix to Postfix. Rewrite the exact input state, legal operation/relationship, and requested output before choosing a technique. Similar titles often hide materially different variants—for example nearest greater versus count of all greater values, four-direction versus eight-direction grid adjacency, or node identity versus equal node values.

### Three requirement-clarifying examples

| # | Input / setup | Output / observation | Why it matters |
|---:|---|---|---|
| 1 | `smallest valid input` | `trace base state` | base/boundary semantics |
| 2 | `typical nontrivial input` | `trace expected result` | core invariant |
| 3 | `boundary-shaped input` | `verify edge behavior` | protect implementation |

### Pattern recognition

**Primary pattern:** Stack parsing

> **KEY INTUITION —** Nesting and precedence require remembering the most recent unresolved opening/operator/expression.

### Brute-force / straightforward baseline

Start with the direct simulation/repeated scan that mirrors the statement. It is valuable because it exposes **what is being recomputed**. Then ask: *what exact fact from earlier work would make the next decision immediate?*

### Optimized reasoning

Name the invariant before code. The optimized solution for this family is derived from the intuition above. Every state change must preserve the invariant; if you cannot say what the working structure means after processing the first *i* items/nodes/states, the implementation is not ready.

### Detailed dry run

Write index/value, stack/queue/deque before, all pops/removals, insertion, and answer after each iteration.

For interview practice, include at least four snapshots: initial state, first nontrivial state change, the state where the central invariant does real work, and final state. Explain **why each mutation occurred**, not merely what the values became.

### Java implementation / template

```java
// Card-specific Java implementation is expanded during the scheduled deepening pass.
// Interview discipline:
// 1) define the state/invariant,
// 2) implement exactly one valid transition,
// 3) test empty/single/duplicate/extreme cases,
// 4) use long for sums/distances when constraints can overflow int.
```

### Key line to highlight

Find the line that changes the invariant: the monotonic `while`, a graph relaxation `if`, a pointer splice, a recursive return combination, or a HashMap lookup/update. Explain what would break if its comparison or ordering changed.

### Correctness checklist

1. State the invariant before the loop/recursion.
2. Show it is true initially.
3. Show every transition preserves it.
4. Explain why the terminal state implies the requested answer.
5. For greedy algorithms, identify the exchange/cut/monotonic argument that makes the local choice safe.

### Boundary conditions

- empty/null input when the platform permits it;
- one element/node/state;
- duplicates and strict-versus-nonstrict comparisons;
- monotone/skewed/disconnected shape where relevant;
- overflow in sums, products, distances and path counts—prefer `long` when needed;
- value vs index vs node identity;
- online/streaming input versus offline preprocessing;
- repeated queries may justify preprocessing that a one-shot query does not.

### Complexity — derive it instead of memorizing it

**O(n) time and O(n) stack.**

Count how many times each element/node/edge can enter and leave the working structure, then multiply by the operation cost: array/index O(1), expected hash O(1), balanced tree/heap O(log n), full edge scan O(E).

### Memoization / repeated-work note

Memoization is useful only when the same logical state can be solved repeatedly. In many one-pass stack/list/tree problems there are no overlapping subproblems; the data structure itself stores the minimal reusable history. In graph, prefix-hash, and repeated-query problems, `visited`, `dist`, prefix maps, parent maps, or cached subtree metadata play the “do not recompute solved state” role.

> **MEMORY TRICK —** MOST RECENT UNRESOLVED ITEM GOES ON THE STACK.

### Interview follow-ups and variations

1. Can auxiliary space be reduced, and what invariant replaces it?
2. What changes if equality/duplicate semantics change?
3. What changes if input arrives online?
4. What changes for many repeated queries?
5. Name two related problems that reuse this invariant with only one comparison or one state dimension changed.

[↑ Back to Index](#navigation-and-index)

---

<a id="s12-postfix-to-prefix"></a>
## S12. Postfix to Prefix

[← S11](#s11-prefix-to-postfix) · [Index](#navigation-and-index) · [S13 →](#s13-postfix-to-infix)

### Detailed question understanding

**What is the problem/lesson asking?** Postfix to Prefix. Rewrite the exact input state, legal operation/relationship, and requested output before choosing a technique. Similar titles often hide materially different variants—for example nearest greater versus count of all greater values, four-direction versus eight-direction grid adjacency, or node identity versus equal node values.

### Three requirement-clarifying examples

| # | Input / setup | Output / observation | Why it matters |
|---:|---|---|---|
| 1 | `smallest valid input` | `trace base state` | base/boundary semantics |
| 2 | `typical nontrivial input` | `trace expected result` | core invariant |
| 3 | `boundary-shaped input` | `verify edge behavior` | protect implementation |

### Pattern recognition

**Primary pattern:** Stack parsing

> **KEY INTUITION —** Nesting and precedence require remembering the most recent unresolved opening/operator/expression.

### Brute-force / straightforward baseline

Start with the direct simulation/repeated scan that mirrors the statement. It is valuable because it exposes **what is being recomputed**. Then ask: *what exact fact from earlier work would make the next decision immediate?*

### Optimized reasoning

Name the invariant before code. The optimized solution for this family is derived from the intuition above. Every state change must preserve the invariant; if you cannot say what the working structure means after processing the first *i* items/nodes/states, the implementation is not ready.

### Detailed dry run

Write index/value, stack/queue/deque before, all pops/removals, insertion, and answer after each iteration.

For interview practice, include at least four snapshots: initial state, first nontrivial state change, the state where the central invariant does real work, and final state. Explain **why each mutation occurred**, not merely what the values became.

### Java implementation / template

```java
// Card-specific Java implementation is expanded during the scheduled deepening pass.
// Interview discipline:
// 1) define the state/invariant,
// 2) implement exactly one valid transition,
// 3) test empty/single/duplicate/extreme cases,
// 4) use long for sums/distances when constraints can overflow int.
```

### Key line to highlight

Find the line that changes the invariant: the monotonic `while`, a graph relaxation `if`, a pointer splice, a recursive return combination, or a HashMap lookup/update. Explain what would break if its comparison or ordering changed.

### Correctness checklist

1. State the invariant before the loop/recursion.
2. Show it is true initially.
3. Show every transition preserves it.
4. Explain why the terminal state implies the requested answer.
5. For greedy algorithms, identify the exchange/cut/monotonic argument that makes the local choice safe.

### Boundary conditions

- empty/null input when the platform permits it;
- one element/node/state;
- duplicates and strict-versus-nonstrict comparisons;
- monotone/skewed/disconnected shape where relevant;
- overflow in sums, products, distances and path counts—prefer `long` when needed;
- value vs index vs node identity;
- online/streaming input versus offline preprocessing;
- repeated queries may justify preprocessing that a one-shot query does not.

### Complexity — derive it instead of memorizing it

**O(n) time and O(n) stack.**

Count how many times each element/node/edge can enter and leave the working structure, then multiply by the operation cost: array/index O(1), expected hash O(1), balanced tree/heap O(log n), full edge scan O(E).

### Memoization / repeated-work note

Memoization is useful only when the same logical state can be solved repeatedly. In many one-pass stack/list/tree problems there are no overlapping subproblems; the data structure itself stores the minimal reusable history. In graph, prefix-hash, and repeated-query problems, `visited`, `dist`, prefix maps, parent maps, or cached subtree metadata play the “do not recompute solved state” role.

> **MEMORY TRICK —** MOST RECENT UNRESOLVED ITEM GOES ON THE STACK.

### Interview follow-ups and variations

1. Can auxiliary space be reduced, and what invariant replaces it?
2. What changes if equality/duplicate semantics change?
3. What changes if input arrives online?
4. What changes for many repeated queries?
5. Name two related problems that reuse this invariant with only one comparison or one state dimension changed.

[↑ Back to Index](#navigation-and-index)

---

<a id="s13-postfix-to-infix"></a>
## S13. Postfix to Infix

[← S12](#s12-postfix-to-prefix) · [Index](#navigation-and-index) · [S14 →](#s14-infix-to-prefix)

### Detailed question understanding

**What is the problem/lesson asking?** Postfix to Infix. Rewrite the exact input state, legal operation/relationship, and requested output before choosing a technique. Similar titles often hide materially different variants—for example nearest greater versus count of all greater values, four-direction versus eight-direction grid adjacency, or node identity versus equal node values.

### Three requirement-clarifying examples

| # | Input / setup | Output / observation | Why it matters |
|---:|---|---|---|
| 1 | `smallest valid input` | `trace base state` | base/boundary semantics |
| 2 | `typical nontrivial input` | `trace expected result` | core invariant |
| 3 | `boundary-shaped input` | `verify edge behavior` | protect implementation |

### Pattern recognition

**Primary pattern:** Stack parsing

> **KEY INTUITION —** Nesting and precedence require remembering the most recent unresolved opening/operator/expression.

### Brute-force / straightforward baseline

Start with the direct simulation/repeated scan that mirrors the statement. It is valuable because it exposes **what is being recomputed**. Then ask: *what exact fact from earlier work would make the next decision immediate?*

### Optimized reasoning

Name the invariant before code. The optimized solution for this family is derived from the intuition above. Every state change must preserve the invariant; if you cannot say what the working structure means after processing the first *i* items/nodes/states, the implementation is not ready.

### Detailed dry run

Write index/value, stack/queue/deque before, all pops/removals, insertion, and answer after each iteration.

For interview practice, include at least four snapshots: initial state, first nontrivial state change, the state where the central invariant does real work, and final state. Explain **why each mutation occurred**, not merely what the values became.

### Java implementation / template

```java
// Card-specific Java implementation is expanded during the scheduled deepening pass.
// Interview discipline:
// 1) define the state/invariant,
// 2) implement exactly one valid transition,
// 3) test empty/single/duplicate/extreme cases,
// 4) use long for sums/distances when constraints can overflow int.
```

### Key line to highlight

Find the line that changes the invariant: the monotonic `while`, a graph relaxation `if`, a pointer splice, a recursive return combination, or a HashMap lookup/update. Explain what would break if its comparison or ordering changed.

### Correctness checklist

1. State the invariant before the loop/recursion.
2. Show it is true initially.
3. Show every transition preserves it.
4. Explain why the terminal state implies the requested answer.
5. For greedy algorithms, identify the exchange/cut/monotonic argument that makes the local choice safe.

### Boundary conditions

- empty/null input when the platform permits it;
- one element/node/state;
- duplicates and strict-versus-nonstrict comparisons;
- monotone/skewed/disconnected shape where relevant;
- overflow in sums, products, distances and path counts—prefer `long` when needed;
- value vs index vs node identity;
- online/streaming input versus offline preprocessing;
- repeated queries may justify preprocessing that a one-shot query does not.

### Complexity — derive it instead of memorizing it

**O(n) time and O(n) stack.**

Count how many times each element/node/edge can enter and leave the working structure, then multiply by the operation cost: array/index O(1), expected hash O(1), balanced tree/heap O(log n), full edge scan O(E).

### Memoization / repeated-work note

Memoization is useful only when the same logical state can be solved repeatedly. In many one-pass stack/list/tree problems there are no overlapping subproblems; the data structure itself stores the minimal reusable history. In graph, prefix-hash, and repeated-query problems, `visited`, `dist`, prefix maps, parent maps, or cached subtree metadata play the “do not recompute solved state” role.

> **MEMORY TRICK —** MOST RECENT UNRESOLVED ITEM GOES ON THE STACK.

### Interview follow-ups and variations

1. Can auxiliary space be reduced, and what invariant replaces it?
2. What changes if equality/duplicate semantics change?
3. What changes if input arrives online?
4. What changes for many repeated queries?
5. Name two related problems that reuse this invariant with only one comparison or one state dimension changed.

[↑ Back to Index](#navigation-and-index)

---

<a id="s14-infix-to-prefix"></a>
## S14. Infix to Prefix

[← S13](#s13-postfix-to-infix) · [Index](#navigation-and-index) · [S15 →](#s15-next-greater-element)

### Detailed question understanding

**What is the problem/lesson asking?** Infix to Prefix. Rewrite the exact input state, legal operation/relationship, and requested output before choosing a technique. Similar titles often hide materially different variants—for example nearest greater versus count of all greater values, four-direction versus eight-direction grid adjacency, or node identity versus equal node values.

### Three requirement-clarifying examples

| # | Input / setup | Output / observation | Why it matters |
|---:|---|---|---|
| 1 | `smallest valid input` | `trace base state` | base/boundary semantics |
| 2 | `typical nontrivial input` | `trace expected result` | core invariant |
| 3 | `boundary-shaped input` | `verify edge behavior` | protect implementation |

### Pattern recognition

**Primary pattern:** Stack parsing

> **KEY INTUITION —** Nesting and precedence require remembering the most recent unresolved opening/operator/expression.

### Brute-force / straightforward baseline

Start with the direct simulation/repeated scan that mirrors the statement. It is valuable because it exposes **what is being recomputed**. Then ask: *what exact fact from earlier work would make the next decision immediate?*

### Optimized reasoning

Name the invariant before code. The optimized solution for this family is derived from the intuition above. Every state change must preserve the invariant; if you cannot say what the working structure means after processing the first *i* items/nodes/states, the implementation is not ready.

### Detailed dry run

Write index/value, stack/queue/deque before, all pops/removals, insertion, and answer after each iteration.

For interview practice, include at least four snapshots: initial state, first nontrivial state change, the state where the central invariant does real work, and final state. Explain **why each mutation occurred**, not merely what the values became.

### Java implementation / template

```java
// Card-specific Java implementation is expanded during the scheduled deepening pass.
// Interview discipline:
// 1) define the state/invariant,
// 2) implement exactly one valid transition,
// 3) test empty/single/duplicate/extreme cases,
// 4) use long for sums/distances when constraints can overflow int.
```

### Key line to highlight

Find the line that changes the invariant: the monotonic `while`, a graph relaxation `if`, a pointer splice, a recursive return combination, or a HashMap lookup/update. Explain what would break if its comparison or ordering changed.

### Correctness checklist

1. State the invariant before the loop/recursion.
2. Show it is true initially.
3. Show every transition preserves it.
4. Explain why the terminal state implies the requested answer.
5. For greedy algorithms, identify the exchange/cut/monotonic argument that makes the local choice safe.

### Boundary conditions

- empty/null input when the platform permits it;
- one element/node/state;
- duplicates and strict-versus-nonstrict comparisons;
- monotone/skewed/disconnected shape where relevant;
- overflow in sums, products, distances and path counts—prefer `long` when needed;
- value vs index vs node identity;
- online/streaming input versus offline preprocessing;
- repeated queries may justify preprocessing that a one-shot query does not.

### Complexity — derive it instead of memorizing it

**O(n) time and O(n) stack.**

Count how many times each element/node/edge can enter and leave the working structure, then multiply by the operation cost: array/index O(1), expected hash O(1), balanced tree/heap O(log n), full edge scan O(E).

### Memoization / repeated-work note

Memoization is useful only when the same logical state can be solved repeatedly. In many one-pass stack/list/tree problems there are no overlapping subproblems; the data structure itself stores the minimal reusable history. In graph, prefix-hash, and repeated-query problems, `visited`, `dist`, prefix maps, parent maps, or cached subtree metadata play the “do not recompute solved state” role.

> **MEMORY TRICK —** MOST RECENT UNRESOLVED ITEM GOES ON THE STACK.

### Interview follow-ups and variations

1. Can auxiliary space be reduced, and what invariant replaces it?
2. What changes if equality/duplicate semantics change?
3. What changes if input arrives online?
4. What changes for many repeated queries?
5. Name two related problems that reuse this invariant with only one comparison or one state dimension changed.

[↑ Back to Index](#navigation-and-index)

---

<a id="s15-next-greater-element"></a>
## S15. Next Greater Element

[← S14](#s14-infix-to-prefix) · [Index](#navigation-and-index) · [S16 →](#s16-next-greater-element-ii-circular)

### Detailed question understanding

**What is the problem/lesson asking?** Next Greater Element. Rewrite the exact input state, legal operation/relationship, and requested output before choosing a technique. Similar titles often hide materially different variants—for example nearest greater versus count of all greater values, four-direction versus eight-direction grid adjacency, or node identity versus equal node values.

### Three requirement-clarifying examples

| # | Input / setup | Output / observation | Why it matters |
|---:|---|---|---|
| 1 | `[4,5,2,10]` | `[5,10,10,-1]` | first greater |
| 2 | `[3,2,1]` | `[-1,-1,-1]` | decreasing |
| 3 | `[1,3,2,4]` | `[3,4,4,-1]` | nearest qualifier |

### Pattern recognition

**Primary pattern:** Monotonic stack

> **KEY INTUITION —** Discard dominated candidates permanently. The surviving stack is exactly the set of unresolved candidates.

### Brute-force / straightforward baseline

Start with the direct simulation/repeated scan that mirrors the statement. It is valuable because it exposes **what is being recomputed**. Then ask: *what exact fact from earlier work would make the next decision immediate?*

### Optimized reasoning

Name the invariant before code. The optimized solution for this family is derived from the intuition above. Every state change must preserve the invariant; if you cannot say what the working structure means after processing the first *i* items/nodes/states, the implementation is not ready.

### Detailed dry run

Write index/value, stack/queue/deque before, all pops/removals, insertion, and answer after each iteration.

For interview practice, include at least four snapshots: initial state, first nontrivial state change, the state where the central invariant does real work, and final state. Explain **why each mutation occurred**, not merely what the values became.

### Java implementation / template

```java
static int[] nextGreater(int[] a) {
    int[] ans=new int[a.length]; Arrays.fill(ans,-1);
    Deque<Integer> st=new ArrayDeque<>();
    for(int i=a.length-1;i>=0;i--){
        while(!st.isEmpty() && st.peek()<=a[i]) st.pop();
        if(!st.isEmpty()) ans[i]=st.peek();
        st.push(a[i]);
    }
    return ans;
}
```

### Key line to highlight

Find the line that changes the invariant: the monotonic `while`, a graph relaxation `if`, a pointer splice, a recursive return combination, or a HashMap lookup/update. Explain what would break if its comparison or ordering changed.

### Correctness checklist

1. State the invariant before the loop/recursion.
2. Show it is true initially.
3. Show every transition preserves it.
4. Explain why the terminal state implies the requested answer.
5. For greedy algorithms, identify the exchange/cut/monotonic argument that makes the local choice safe.

### Boundary conditions

- empty/null input when the platform permits it;
- one element/node/state;
- duplicates and strict-versus-nonstrict comparisons;
- monotone/skewed/disconnected shape where relevant;
- overflow in sums, products, distances and path counts—prefer `long` when needed;
- value vs index vs node identity;
- online/streaming input versus offline preprocessing;
- repeated queries may justify preprocessing that a one-shot query does not.

### Complexity — derive it instead of memorizing it

**Each index is pushed once and popped at most once → O(n) amortized; O(n) stack.**

Count how many times each element/node/edge can enter and leave the working structure, then multiply by the operation cost: array/index O(1), expected hash O(1), balanced tree/heap O(log n), full edge scan O(E).

### Memoization / repeated-work note

Memoization is useful only when the same logical state can be solved repeatedly. In many one-pass stack/list/tree problems there are no overlapping subproblems; the data structure itself stores the minimal reusable history. In graph, prefix-hash, and repeated-query problems, `visited`, `dist`, prefix maps, parent maps, or cached subtree metadata play the “do not recompute solved state” role.

> **MEMORY TRICK —** DOMINATED CANDIDATES NEVER NEED TO RETURN.

### Interview follow-ups and variations

1. Can auxiliary space be reduced, and what invariant replaces it?
2. What changes if equality/duplicate semantics change?
3. What changes if input arrives online?
4. What changes for many repeated queries?
5. Name two related problems that reuse this invariant with only one comparison or one state dimension changed.

[↑ Back to Index](#navigation-and-index)

---

<a id="s16-next-greater-element-ii-circular"></a>
## S16. Next Greater Element II — Circular

[← S15](#s15-next-greater-element) · [Index](#navigation-and-index) · [S17 →](#s17-next-smaller-element)

### Detailed question understanding

**What is the problem/lesson asking?** Next Greater Element II — Circular. Rewrite the exact input state, legal operation/relationship, and requested output before choosing a technique. Similar titles often hide materially different variants—for example nearest greater versus count of all greater values, four-direction versus eight-direction grid adjacency, or node identity versus equal node values.

### Three requirement-clarifying examples

| # | Input / setup | Output / observation | Why it matters |
|---:|---|---|---|
| 1 | `smallest valid input` | `trace base state` | base/boundary semantics |
| 2 | `typical nontrivial input` | `trace expected result` | core invariant |
| 3 | `boundary-shaped input` | `verify edge behavior` | protect implementation |

### Pattern recognition

**Primary pattern:** Monotonic stack

> **KEY INTUITION —** Discard dominated candidates permanently. The surviving stack is exactly the set of unresolved candidates.

### Brute-force / straightforward baseline

Start with the direct simulation/repeated scan that mirrors the statement. It is valuable because it exposes **what is being recomputed**. Then ask: *what exact fact from earlier work would make the next decision immediate?*

### Optimized reasoning

Name the invariant before code. The optimized solution for this family is derived from the intuition above. Every state change must preserve the invariant; if you cannot say what the working structure means after processing the first *i* items/nodes/states, the implementation is not ready.

### Detailed dry run

Write index/value, stack/queue/deque before, all pops/removals, insertion, and answer after each iteration.

For interview practice, include at least four snapshots: initial state, first nontrivial state change, the state where the central invariant does real work, and final state. Explain **why each mutation occurred**, not merely what the values became.

### Java implementation / template

```java
// Card-specific Java implementation is expanded during the scheduled deepening pass.
// Interview discipline:
// 1) define the state/invariant,
// 2) implement exactly one valid transition,
// 3) test empty/single/duplicate/extreme cases,
// 4) use long for sums/distances when constraints can overflow int.
```

### Key line to highlight

Find the line that changes the invariant: the monotonic `while`, a graph relaxation `if`, a pointer splice, a recursive return combination, or a HashMap lookup/update. Explain what would break if its comparison or ordering changed.

### Correctness checklist

1. State the invariant before the loop/recursion.
2. Show it is true initially.
3. Show every transition preserves it.
4. Explain why the terminal state implies the requested answer.
5. For greedy algorithms, identify the exchange/cut/monotonic argument that makes the local choice safe.

### Boundary conditions

- empty/null input when the platform permits it;
- one element/node/state;
- duplicates and strict-versus-nonstrict comparisons;
- monotone/skewed/disconnected shape where relevant;
- overflow in sums, products, distances and path counts—prefer `long` when needed;
- value vs index vs node identity;
- online/streaming input versus offline preprocessing;
- repeated queries may justify preprocessing that a one-shot query does not.

### Complexity — derive it instead of memorizing it

**Each index is pushed once and popped at most once → O(n) amortized; O(n) stack.**

Count how many times each element/node/edge can enter and leave the working structure, then multiply by the operation cost: array/index O(1), expected hash O(1), balanced tree/heap O(log n), full edge scan O(E).

### Memoization / repeated-work note

Memoization is useful only when the same logical state can be solved repeatedly. In many one-pass stack/list/tree problems there are no overlapping subproblems; the data structure itself stores the minimal reusable history. In graph, prefix-hash, and repeated-query problems, `visited`, `dist`, prefix maps, parent maps, or cached subtree metadata play the “do not recompute solved state” role.

> **MEMORY TRICK —** DOMINATED CANDIDATES NEVER NEED TO RETURN.

### Interview follow-ups and variations

1. Can auxiliary space be reduced, and what invariant replaces it?
2. What changes if equality/duplicate semantics change?
3. What changes if input arrives online?
4. What changes for many repeated queries?
5. Name two related problems that reuse this invariant with only one comparison or one state dimension changed.

[↑ Back to Index](#navigation-and-index)

---

<a id="s17-next-smaller-element"></a>
## S17. Next Smaller Element

[← S16](#s16-next-greater-element-ii-circular) · [Index](#navigation-and-index) · [S18 →](#s18-count-greater-elements-to-the-right-for-queries)

### Detailed question understanding

**What is the problem/lesson asking?** Next Smaller Element. Rewrite the exact input state, legal operation/relationship, and requested output before choosing a technique. Similar titles often hide materially different variants—for example nearest greater versus count of all greater values, four-direction versus eight-direction grid adjacency, or node identity versus equal node values.

### Three requirement-clarifying examples

| # | Input / setup | Output / observation | Why it matters |
|---:|---|---|---|
| 1 | `smallest valid input` | `trace base state` | base/boundary semantics |
| 2 | `typical nontrivial input` | `trace expected result` | core invariant |
| 3 | `boundary-shaped input` | `verify edge behavior` | protect implementation |

### Pattern recognition

**Primary pattern:** Monotonic stack

> **KEY INTUITION —** Discard dominated candidates permanently. The surviving stack is exactly the set of unresolved candidates.

### Brute-force / straightforward baseline

Start with the direct simulation/repeated scan that mirrors the statement. It is valuable because it exposes **what is being recomputed**. Then ask: *what exact fact from earlier work would make the next decision immediate?*

### Optimized reasoning

Name the invariant before code. The optimized solution for this family is derived from the intuition above. Every state change must preserve the invariant; if you cannot say what the working structure means after processing the first *i* items/nodes/states, the implementation is not ready.

### Detailed dry run

Write index/value, stack/queue/deque before, all pops/removals, insertion, and answer after each iteration.

For interview practice, include at least four snapshots: initial state, first nontrivial state change, the state where the central invariant does real work, and final state. Explain **why each mutation occurred**, not merely what the values became.

### Java implementation / template

```java
// Card-specific Java implementation is expanded during the scheduled deepening pass.
// Interview discipline:
// 1) define the state/invariant,
// 2) implement exactly one valid transition,
// 3) test empty/single/duplicate/extreme cases,
// 4) use long for sums/distances when constraints can overflow int.
```

### Key line to highlight

Find the line that changes the invariant: the monotonic `while`, a graph relaxation `if`, a pointer splice, a recursive return combination, or a HashMap lookup/update. Explain what would break if its comparison or ordering changed.

### Correctness checklist

1. State the invariant before the loop/recursion.
2. Show it is true initially.
3. Show every transition preserves it.
4. Explain why the terminal state implies the requested answer.
5. For greedy algorithms, identify the exchange/cut/monotonic argument that makes the local choice safe.

### Boundary conditions

- empty/null input when the platform permits it;
- one element/node/state;
- duplicates and strict-versus-nonstrict comparisons;
- monotone/skewed/disconnected shape where relevant;
- overflow in sums, products, distances and path counts—prefer `long` when needed;
- value vs index vs node identity;
- online/streaming input versus offline preprocessing;
- repeated queries may justify preprocessing that a one-shot query does not.

### Complexity — derive it instead of memorizing it

**Each index is pushed once and popped at most once → O(n) amortized; O(n) stack.**

Count how many times each element/node/edge can enter and leave the working structure, then multiply by the operation cost: array/index O(1), expected hash O(1), balanced tree/heap O(log n), full edge scan O(E).

### Memoization / repeated-work note

Memoization is useful only when the same logical state can be solved repeatedly. In many one-pass stack/list/tree problems there are no overlapping subproblems; the data structure itself stores the minimal reusable history. In graph, prefix-hash, and repeated-query problems, `visited`, `dist`, prefix maps, parent maps, or cached subtree metadata play the “do not recompute solved state” role.

> **MEMORY TRICK —** DOMINATED CANDIDATES NEVER NEED TO RETURN.

### Interview follow-ups and variations

1. Can auxiliary space be reduced, and what invariant replaces it?
2. What changes if equality/duplicate semantics change?
3. What changes if input arrives online?
4. What changes for many repeated queries?
5. Name two related problems that reuse this invariant with only one comparison or one state dimension changed.

[↑ Back to Index](#navigation-and-index)

---

<a id="s18-count-greater-elements-to-the-right-for-queries"></a>
## S18. Count Greater Elements to the Right for Queries

[← S17](#s17-next-smaller-element) · [Index](#navigation-and-index) · [S19 →](#s19-trapping-rainwater)

### Detailed question understanding

**What is the problem/lesson asking?** Count Greater Elements to the Right for Queries. Rewrite the exact input state, legal operation/relationship, and requested output before choosing a technique. Similar titles often hide materially different variants—for example nearest greater versus count of all greater values, four-direction versus eight-direction grid adjacency, or node identity versus equal node values.

### Three requirement-clarifying examples

| # | Input / setup | Output / observation | Why it matters |
|---:|---|---|---|
| 1 | `smallest valid input` | `trace base state` | base/boundary semantics |
| 2 | `typical nontrivial input` | `trace expected result` | core invariant |
| 3 | `boundary-shaped input` | `verify edge behavior` | protect implementation |

### Pattern recognition

**Primary pattern:** LIFO/FIFO invariant

> **KEY INTUITION —** Choose the representation whose natural endpoint matches the API operation.

### Brute-force / straightforward baseline

Start with the direct simulation/repeated scan that mirrors the statement. It is valuable because it exposes **what is being recomputed**. Then ask: *what exact fact from earlier work would make the next decision immediate?*

### Optimized reasoning

Name the invariant before code. The optimized solution for this family is derived from the intuition above. Every state change must preserve the invariant; if you cannot say what the working structure means after processing the first *i* items/nodes/states, the implementation is not ready.

### Detailed dry run

Write index/value, stack/queue/deque before, all pops/removals, insertion, and answer after each iteration.

For interview practice, include at least four snapshots: initial state, first nontrivial state change, the state where the central invariant does real work, and final state. Explain **why each mutation occurred**, not merely what the values became.

### Java implementation / template

```java
// Card-specific Java implementation is expanded during the scheduled deepening pass.
// Interview discipline:
// 1) define the state/invariant,
// 2) implement exactly one valid transition,
// 3) test empty/single/duplicate/extreme cases,
// 4) use long for sums/distances when constraints can overflow int.
```

### Key line to highlight

Find the line that changes the invariant: the monotonic `while`, a graph relaxation `if`, a pointer splice, a recursive return combination, or a HashMap lookup/update. Explain what would break if its comparison or ordering changed.

### Correctness checklist

1. State the invariant before the loop/recursion.
2. Show it is true initially.
3. Show every transition preserves it.
4. Explain why the terminal state implies the requested answer.
5. For greedy algorithms, identify the exchange/cut/monotonic argument that makes the local choice safe.

### Boundary conditions

- empty/null input when the platform permits it;
- one element/node/state;
- duplicates and strict-versus-nonstrict comparisons;
- monotone/skewed/disconnected shape where relevant;
- overflow in sums, products, distances and path counts—prefer `long` when needed;
- value vs index vs node identity;
- online/streaming input versus offline preprocessing;
- repeated queries may justify preprocessing that a one-shot query does not.

### Complexity — derive it instead of memorizing it

**Primitive operations O(1), total storage O(n) unless an emulation intentionally shifts work.**

Count how many times each element/node/edge can enter and leave the working structure, then multiply by the operation cost: array/index O(1), expected hash O(1), balanced tree/heap O(log n), full edge scan O(E).

### Memoization / repeated-work note

Memoization is useful only when the same logical state can be solved repeatedly. In many one-pass stack/list/tree problems there are no overlapping subproblems; the data structure itself stores the minimal reusable history. In graph, prefix-hash, and repeated-query problems, `visited`, `dist`, prefix maps, parent maps, or cached subtree metadata play the “do not recompute solved state” role.

> **MEMORY TRICK —** NAME THE ENDPOINT INVARIANT.

### Interview follow-ups and variations

1. Can auxiliary space be reduced, and what invariant replaces it?
2. What changes if equality/duplicate semantics change?
3. What changes if input arrives online?
4. What changes for many repeated queries?
5. Name two related problems that reuse this invariant with only one comparison or one state dimension changed.

[↑ Back to Index](#navigation-and-index)

---

<a id="s19-trapping-rainwater"></a>
## S19. Trapping Rainwater

[← S18](#s18-count-greater-elements-to-the-right-for-queries) · [Index](#navigation-and-index) · [S20 →](#s20-sum-of-subarray-minimums)

### Detailed question understanding

**What is the problem/lesson asking?** Trapping Rainwater. Rewrite the exact input state, legal operation/relationship, and requested output before choosing a technique. Similar titles often hide materially different variants—for example nearest greater versus count of all greater values, four-direction versus eight-direction grid adjacency, or node identity versus equal node values.

### Three requirement-clarifying examples

| # | Input / setup | Output / observation | Why it matters |
|---:|---|---|---|
| 1 | `[3,0,2,0,4]` | `7` | multiple basins |
| 2 | `[4,2,0,3,2,5]` | `9` | different depths |
| 3 | `[1,2,3]` | `0` | no basin |

### Pattern recognition

**Primary pattern:** LIFO/FIFO invariant

> **KEY INTUITION —** Choose the representation whose natural endpoint matches the API operation.

### Brute-force / straightforward baseline

Start with the direct simulation/repeated scan that mirrors the statement. It is valuable because it exposes **what is being recomputed**. Then ask: *what exact fact from earlier work would make the next decision immediate?*

### Optimized reasoning

Name the invariant before code. The optimized solution for this family is derived from the intuition above. Every state change must preserve the invariant; if you cannot say what the working structure means after processing the first *i* items/nodes/states, the implementation is not ready.

### Detailed dry run

Write index/value, stack/queue/deque before, all pops/removals, insertion, and answer after each iteration.

For interview practice, include at least four snapshots: initial state, first nontrivial state change, the state where the central invariant does real work, and final state. Explain **why each mutation occurred**, not merely what the values became.

### Java implementation / template

```java
// Card-specific Java implementation is expanded during the scheduled deepening pass.
// Interview discipline:
// 1) define the state/invariant,
// 2) implement exactly one valid transition,
// 3) test empty/single/duplicate/extreme cases,
// 4) use long for sums/distances when constraints can overflow int.
```

### Key line to highlight

Find the line that changes the invariant: the monotonic `while`, a graph relaxation `if`, a pointer splice, a recursive return combination, or a HashMap lookup/update. Explain what would break if its comparison or ordering changed.

### Correctness checklist

1. State the invariant before the loop/recursion.
2. Show it is true initially.
3. Show every transition preserves it.
4. Explain why the terminal state implies the requested answer.
5. For greedy algorithms, identify the exchange/cut/monotonic argument that makes the local choice safe.

### Boundary conditions

- empty/null input when the platform permits it;
- one element/node/state;
- duplicates and strict-versus-nonstrict comparisons;
- monotone/skewed/disconnected shape where relevant;
- overflow in sums, products, distances and path counts—prefer `long` when needed;
- value vs index vs node identity;
- online/streaming input versus offline preprocessing;
- repeated queries may justify preprocessing that a one-shot query does not.

### Complexity — derive it instead of memorizing it

**Primitive operations O(1), total storage O(n) unless an emulation intentionally shifts work.**

Count how many times each element/node/edge can enter and leave the working structure, then multiply by the operation cost: array/index O(1), expected hash O(1), balanced tree/heap O(log n), full edge scan O(E).

### Memoization / repeated-work note

Memoization is useful only when the same logical state can be solved repeatedly. In many one-pass stack/list/tree problems there are no overlapping subproblems; the data structure itself stores the minimal reusable history. In graph, prefix-hash, and repeated-query problems, `visited`, `dist`, prefix maps, parent maps, or cached subtree metadata play the “do not recompute solved state” role.

> **MEMORY TRICK —** NAME THE ENDPOINT INVARIANT.

### Interview follow-ups and variations

1. Can auxiliary space be reduced, and what invariant replaces it?
2. What changes if equality/duplicate semantics change?
3. What changes if input arrives online?
4. What changes for many repeated queries?
5. Name two related problems that reuse this invariant with only one comparison or one state dimension changed.

[↑ Back to Index](#navigation-and-index)

---

<a id="s20-sum-of-subarray-minimums"></a>
## S20. Sum of Subarray Minimums

[← S19](#s19-trapping-rainwater) · [Index](#navigation-and-index) · [S21 →](#s21-asteroid-collision)

### Detailed question understanding

**What is the problem/lesson asking?** Sum of Subarray Minimums. Rewrite the exact input state, legal operation/relationship, and requested output before choosing a technique. Similar titles often hide materially different variants—for example nearest greater versus count of all greater values, four-direction versus eight-direction grid adjacency, or node identity versus equal node values.

### Three requirement-clarifying examples

| # | Input / setup | Output / observation | Why it matters |
|---:|---|---|---|
| 1 | `smallest valid input` | `trace base state` | base/boundary semantics |
| 2 | `typical nontrivial input` | `trace expected result` | core invariant |
| 3 | `boundary-shaped input` | `verify edge behavior` | protect implementation |

### Pattern recognition

**Primary pattern:** Contribution counting

> **KEY INTUITION —** Count how many subarrays choose each index as min/max instead of enumerating all subarrays.

### Brute-force / straightforward baseline

Start with the direct simulation/repeated scan that mirrors the statement. It is valuable because it exposes **what is being recomputed**. Then ask: *what exact fact from earlier work would make the next decision immediate?*

### Optimized reasoning

Name the invariant before code. The optimized solution for this family is derived from the intuition above. Every state change must preserve the invariant; if you cannot say what the working structure means after processing the first *i* items/nodes/states, the implementation is not ready.

### Detailed dry run

Write index/value, stack/queue/deque before, all pops/removals, insertion, and answer after each iteration.

For interview practice, include at least four snapshots: initial state, first nontrivial state change, the state where the central invariant does real work, and final state. Explain **why each mutation occurred**, not merely what the values became.

### Java implementation / template

```java
// Card-specific Java implementation is expanded during the scheduled deepening pass.
// Interview discipline:
// 1) define the state/invariant,
// 2) implement exactly one valid transition,
// 3) test empty/single/duplicate/extreme cases,
// 4) use long for sums/distances when constraints can overflow int.
```

### Key line to highlight

Find the line that changes the invariant: the monotonic `while`, a graph relaxation `if`, a pointer splice, a recursive return combination, or a HashMap lookup/update. Explain what would break if its comparison or ordering changed.

### Correctness checklist

1. State the invariant before the loop/recursion.
2. Show it is true initially.
3. Show every transition preserves it.
4. Explain why the terminal state implies the requested answer.
5. For greedy algorithms, identify the exchange/cut/monotonic argument that makes the local choice safe.

### Boundary conditions

- empty/null input when the platform permits it;
- one element/node/state;
- duplicates and strict-versus-nonstrict comparisons;
- monotone/skewed/disconnected shape where relevant;
- overflow in sums, products, distances and path counts—prefer `long` when needed;
- value vs index vs node identity;
- online/streaming input versus offline preprocessing;
- repeated queries may justify preprocessing that a one-shot query does not.

### Complexity — derive it instead of memorizing it

**O(n) monotonic-boundary passes; O(n) stack/arrays.**

Count how many times each element/node/edge can enter and leave the working structure, then multiply by the operation cost: array/index O(1), expected hash O(1), balanced tree/heap O(log n), full edge scan O(E).

### Memoization / repeated-work note

Memoization is useful only when the same logical state can be solved repeatedly. In many one-pass stack/list/tree problems there are no overlapping subproblems; the data structure itself stores the minimal reusable history. In graph, prefix-hash, and repeated-query problems, `visited`, `dist`, prefix maps, parent maps, or cached subtree metadata play the “do not recompute solved state” role.

> **MEMORY TRICK —** COUNT OWNERSHIP, NOT SUBARRAYS.

### Interview follow-ups and variations

1. Can auxiliary space be reduced, and what invariant replaces it?
2. What changes if equality/duplicate semantics change?
3. What changes if input arrives online?
4. What changes for many repeated queries?
5. Name two related problems that reuse this invariant with only one comparison or one state dimension changed.

[↑ Back to Index](#navigation-and-index)

---

<a id="s21-asteroid-collision"></a>
## S21. Asteroid Collision

[← S20](#s20-sum-of-subarray-minimums) · [Index](#navigation-and-index) · [S22 →](#s22-sum-of-subarray-ranges)

### Detailed question understanding

**What is the problem/lesson asking?** Asteroid Collision. Rewrite the exact input state, legal operation/relationship, and requested output before choosing a technique. Similar titles often hide materially different variants—for example nearest greater versus count of all greater values, four-direction versus eight-direction grid adjacency, or node identity versus equal node values.

### Three requirement-clarifying examples

| # | Input / setup | Output / observation | Why it matters |
|---:|---|---|---|
| 1 | `smallest valid input` | `trace base state` | base/boundary semantics |
| 2 | `typical nontrivial input` | `trace expected result` | core invariant |
| 3 | `boundary-shaped input` | `verify edge behavior` | protect implementation |

### Pattern recognition

**Primary pattern:** LIFO/FIFO invariant

> **KEY INTUITION —** Choose the representation whose natural endpoint matches the API operation.

### Brute-force / straightforward baseline

Start with the direct simulation/repeated scan that mirrors the statement. It is valuable because it exposes **what is being recomputed**. Then ask: *what exact fact from earlier work would make the next decision immediate?*

### Optimized reasoning

Name the invariant before code. The optimized solution for this family is derived from the intuition above. Every state change must preserve the invariant; if you cannot say what the working structure means after processing the first *i* items/nodes/states, the implementation is not ready.

### Detailed dry run

Write index/value, stack/queue/deque before, all pops/removals, insertion, and answer after each iteration.

For interview practice, include at least four snapshots: initial state, first nontrivial state change, the state where the central invariant does real work, and final state. Explain **why each mutation occurred**, not merely what the values became.

### Java implementation / template

```java
// Card-specific Java implementation is expanded during the scheduled deepening pass.
// Interview discipline:
// 1) define the state/invariant,
// 2) implement exactly one valid transition,
// 3) test empty/single/duplicate/extreme cases,
// 4) use long for sums/distances when constraints can overflow int.
```

### Key line to highlight

Find the line that changes the invariant: the monotonic `while`, a graph relaxation `if`, a pointer splice, a recursive return combination, or a HashMap lookup/update. Explain what would break if its comparison or ordering changed.

### Correctness checklist

1. State the invariant before the loop/recursion.
2. Show it is true initially.
3. Show every transition preserves it.
4. Explain why the terminal state implies the requested answer.
5. For greedy algorithms, identify the exchange/cut/monotonic argument that makes the local choice safe.

### Boundary conditions

- empty/null input when the platform permits it;
- one element/node/state;
- duplicates and strict-versus-nonstrict comparisons;
- monotone/skewed/disconnected shape where relevant;
- overflow in sums, products, distances and path counts—prefer `long` when needed;
- value vs index vs node identity;
- online/streaming input versus offline preprocessing;
- repeated queries may justify preprocessing that a one-shot query does not.

### Complexity — derive it instead of memorizing it

**Primitive operations O(1), total storage O(n) unless an emulation intentionally shifts work.**

Count how many times each element/node/edge can enter and leave the working structure, then multiply by the operation cost: array/index O(1), expected hash O(1), balanced tree/heap O(log n), full edge scan O(E).

### Memoization / repeated-work note

Memoization is useful only when the same logical state can be solved repeatedly. In many one-pass stack/list/tree problems there are no overlapping subproblems; the data structure itself stores the minimal reusable history. In graph, prefix-hash, and repeated-query problems, `visited`, `dist`, prefix maps, parent maps, or cached subtree metadata play the “do not recompute solved state” role.

> **MEMORY TRICK —** NAME THE ENDPOINT INVARIANT.

### Interview follow-ups and variations

1. Can auxiliary space be reduced, and what invariant replaces it?
2. What changes if equality/duplicate semantics change?
3. What changes if input arrives online?
4. What changes for many repeated queries?
5. Name two related problems that reuse this invariant with only one comparison or one state dimension changed.

[↑ Back to Index](#navigation-and-index)

---

<a id="s22-sum-of-subarray-ranges"></a>
## S22. Sum of Subarray Ranges

[← S21](#s21-asteroid-collision) · [Index](#navigation-and-index) · [S23 →](#s23-remove-k-digits)

### Detailed question understanding

**What is the problem/lesson asking?** Sum of Subarray Ranges. Rewrite the exact input state, legal operation/relationship, and requested output before choosing a technique. Similar titles often hide materially different variants—for example nearest greater versus count of all greater values, four-direction versus eight-direction grid adjacency, or node identity versus equal node values.

### Three requirement-clarifying examples

| # | Input / setup | Output / observation | Why it matters |
|---:|---|---|---|
| 1 | `smallest valid input` | `trace base state` | base/boundary semantics |
| 2 | `typical nontrivial input` | `trace expected result` | core invariant |
| 3 | `boundary-shaped input` | `verify edge behavior` | protect implementation |

### Pattern recognition

**Primary pattern:** Contribution counting

> **KEY INTUITION —** Count how many subarrays choose each index as min/max instead of enumerating all subarrays.

### Brute-force / straightforward baseline

Start with the direct simulation/repeated scan that mirrors the statement. It is valuable because it exposes **what is being recomputed**. Then ask: *what exact fact from earlier work would make the next decision immediate?*

### Optimized reasoning

Name the invariant before code. The optimized solution for this family is derived from the intuition above. Every state change must preserve the invariant; if you cannot say what the working structure means after processing the first *i* items/nodes/states, the implementation is not ready.

### Detailed dry run

Write index/value, stack/queue/deque before, all pops/removals, insertion, and answer after each iteration.

For interview practice, include at least four snapshots: initial state, first nontrivial state change, the state where the central invariant does real work, and final state. Explain **why each mutation occurred**, not merely what the values became.

### Java implementation / template

```java
// Card-specific Java implementation is expanded during the scheduled deepening pass.
// Interview discipline:
// 1) define the state/invariant,
// 2) implement exactly one valid transition,
// 3) test empty/single/duplicate/extreme cases,
// 4) use long for sums/distances when constraints can overflow int.
```

### Key line to highlight

Find the line that changes the invariant: the monotonic `while`, a graph relaxation `if`, a pointer splice, a recursive return combination, or a HashMap lookup/update. Explain what would break if its comparison or ordering changed.

### Correctness checklist

1. State the invariant before the loop/recursion.
2. Show it is true initially.
3. Show every transition preserves it.
4. Explain why the terminal state implies the requested answer.
5. For greedy algorithms, identify the exchange/cut/monotonic argument that makes the local choice safe.

### Boundary conditions

- empty/null input when the platform permits it;
- one element/node/state;
- duplicates and strict-versus-nonstrict comparisons;
- monotone/skewed/disconnected shape where relevant;
- overflow in sums, products, distances and path counts—prefer `long` when needed;
- value vs index vs node identity;
- online/streaming input versus offline preprocessing;
- repeated queries may justify preprocessing that a one-shot query does not.

### Complexity — derive it instead of memorizing it

**O(n) monotonic-boundary passes; O(n) stack/arrays.**

Count how many times each element/node/edge can enter and leave the working structure, then multiply by the operation cost: array/index O(1), expected hash O(1), balanced tree/heap O(log n), full edge scan O(E).

### Memoization / repeated-work note

Memoization is useful only when the same logical state can be solved repeatedly. In many one-pass stack/list/tree problems there are no overlapping subproblems; the data structure itself stores the minimal reusable history. In graph, prefix-hash, and repeated-query problems, `visited`, `dist`, prefix maps, parent maps, or cached subtree metadata play the “do not recompute solved state” role.

> **MEMORY TRICK —** COUNT OWNERSHIP, NOT SUBARRAYS.

### Interview follow-ups and variations

1. Can auxiliary space be reduced, and what invariant replaces it?
2. What changes if equality/duplicate semantics change?
3. What changes if input arrives online?
4. What changes for many repeated queries?
5. Name two related problems that reuse this invariant with only one comparison or one state dimension changed.

[↑ Back to Index](#navigation-and-index)

---

<a id="s23-remove-k-digits"></a>
## S23. Remove K Digits

[← S22](#s22-sum-of-subarray-ranges) · [Index](#navigation-and-index) · [S24 →](#s24-largest-rectangle-in-histogram)

### Detailed question understanding

**What is the problem/lesson asking?** Remove K Digits. Rewrite the exact input state, legal operation/relationship, and requested output before choosing a technique. Similar titles often hide materially different variants—for example nearest greater versus count of all greater values, four-direction versus eight-direction grid adjacency, or node identity versus equal node values.

### Three requirement-clarifying examples

| # | Input / setup | Output / observation | Why it matters |
|---:|---|---|---|
| 1 | `smallest valid input` | `trace base state` | base/boundary semantics |
| 2 | `typical nontrivial input` | `trace expected result` | core invariant |
| 3 | `boundary-shaped input` | `verify edge behavior` | protect implementation |

### Pattern recognition

**Primary pattern:** LIFO/FIFO invariant

> **KEY INTUITION —** Choose the representation whose natural endpoint matches the API operation.

### Brute-force / straightforward baseline

Start with the direct simulation/repeated scan that mirrors the statement. It is valuable because it exposes **what is being recomputed**. Then ask: *what exact fact from earlier work would make the next decision immediate?*

### Optimized reasoning

Name the invariant before code. The optimized solution for this family is derived from the intuition above. Every state change must preserve the invariant; if you cannot say what the working structure means after processing the first *i* items/nodes/states, the implementation is not ready.

### Detailed dry run

Write index/value, stack/queue/deque before, all pops/removals, insertion, and answer after each iteration.

For interview practice, include at least four snapshots: initial state, first nontrivial state change, the state where the central invariant does real work, and final state. Explain **why each mutation occurred**, not merely what the values became.

### Java implementation / template

```java
// Card-specific Java implementation is expanded during the scheduled deepening pass.
// Interview discipline:
// 1) define the state/invariant,
// 2) implement exactly one valid transition,
// 3) test empty/single/duplicate/extreme cases,
// 4) use long for sums/distances when constraints can overflow int.
```

### Key line to highlight

Find the line that changes the invariant: the monotonic `while`, a graph relaxation `if`, a pointer splice, a recursive return combination, or a HashMap lookup/update. Explain what would break if its comparison or ordering changed.

### Correctness checklist

1. State the invariant before the loop/recursion.
2. Show it is true initially.
3. Show every transition preserves it.
4. Explain why the terminal state implies the requested answer.
5. For greedy algorithms, identify the exchange/cut/monotonic argument that makes the local choice safe.

### Boundary conditions

- empty/null input when the platform permits it;
- one element/node/state;
- duplicates and strict-versus-nonstrict comparisons;
- monotone/skewed/disconnected shape where relevant;
- overflow in sums, products, distances and path counts—prefer `long` when needed;
- value vs index vs node identity;
- online/streaming input versus offline preprocessing;
- repeated queries may justify preprocessing that a one-shot query does not.

### Complexity — derive it instead of memorizing it

**Primitive operations O(1), total storage O(n) unless an emulation intentionally shifts work.**

Count how many times each element/node/edge can enter and leave the working structure, then multiply by the operation cost: array/index O(1), expected hash O(1), balanced tree/heap O(log n), full edge scan O(E).

### Memoization / repeated-work note

Memoization is useful only when the same logical state can be solved repeatedly. In many one-pass stack/list/tree problems there are no overlapping subproblems; the data structure itself stores the minimal reusable history. In graph, prefix-hash, and repeated-query problems, `visited`, `dist`, prefix maps, parent maps, or cached subtree metadata play the “do not recompute solved state” role.

> **MEMORY TRICK —** NAME THE ENDPOINT INVARIANT.

### Interview follow-ups and variations

1. Can auxiliary space be reduced, and what invariant replaces it?
2. What changes if equality/duplicate semantics change?
3. What changes if input arrives online?
4. What changes for many repeated queries?
5. Name two related problems that reuse this invariant with only one comparison or one state dimension changed.

[↑ Back to Index](#navigation-and-index)

---

<a id="s24-largest-rectangle-in-histogram"></a>
## S24. Largest Rectangle in Histogram

[← S23](#s23-remove-k-digits) · [Index](#navigation-and-index) · [S25 →](#s25-maximal-rectangle)

### Detailed question understanding

**What is the problem/lesson asking?** Largest Rectangle in Histogram. Rewrite the exact input state, legal operation/relationship, and requested output before choosing a technique. Similar titles often hide materially different variants—for example nearest greater versus count of all greater values, four-direction versus eight-direction grid adjacency, or node identity versus equal node values.

### Three requirement-clarifying examples

| # | Input / setup | Output / observation | Why it matters |
|---:|---|---|---|
| 1 | `[2,1,5,6,2,3]` | `10` | classic boundary pop |
| 2 | `[2,4]` | `4` | whole width |
| 3 | `[1]` | `1` | single bar |

### Pattern recognition

**Primary pattern:** Monotonic stack

> **KEY INTUITION —** Discard dominated candidates permanently. The surviving stack is exactly the set of unresolved candidates.

### Brute-force / straightforward baseline

Start with the direct simulation/repeated scan that mirrors the statement. It is valuable because it exposes **what is being recomputed**. Then ask: *what exact fact from earlier work would make the next decision immediate?*

### Optimized reasoning

Name the invariant before code. The optimized solution for this family is derived from the intuition above. Every state change must preserve the invariant; if you cannot say what the working structure means after processing the first *i* items/nodes/states, the implementation is not ready.

### Detailed dry run

Write index/value, stack/queue/deque before, all pops/removals, insertion, and answer after each iteration.

For interview practice, include at least four snapshots: initial state, first nontrivial state change, the state where the central invariant does real work, and final state. Explain **why each mutation occurred**, not merely what the values became.

### Java implementation / template

```java
// Card-specific Java implementation is expanded during the scheduled deepening pass.
// Interview discipline:
// 1) define the state/invariant,
// 2) implement exactly one valid transition,
// 3) test empty/single/duplicate/extreme cases,
// 4) use long for sums/distances when constraints can overflow int.
```

### Key line to highlight

Find the line that changes the invariant: the monotonic `while`, a graph relaxation `if`, a pointer splice, a recursive return combination, or a HashMap lookup/update. Explain what would break if its comparison or ordering changed.

### Correctness checklist

1. State the invariant before the loop/recursion.
2. Show it is true initially.
3. Show every transition preserves it.
4. Explain why the terminal state implies the requested answer.
5. For greedy algorithms, identify the exchange/cut/monotonic argument that makes the local choice safe.

### Boundary conditions

- empty/null input when the platform permits it;
- one element/node/state;
- duplicates and strict-versus-nonstrict comparisons;
- monotone/skewed/disconnected shape where relevant;
- overflow in sums, products, distances and path counts—prefer `long` when needed;
- value vs index vs node identity;
- online/streaming input versus offline preprocessing;
- repeated queries may justify preprocessing that a one-shot query does not.

### Complexity — derive it instead of memorizing it

**Each index is pushed once and popped at most once → O(n) amortized; O(n) stack.**

Count how many times each element/node/edge can enter and leave the working structure, then multiply by the operation cost: array/index O(1), expected hash O(1), balanced tree/heap O(log n), full edge scan O(E).

### Memoization / repeated-work note

Memoization is useful only when the same logical state can be solved repeatedly. In many one-pass stack/list/tree problems there are no overlapping subproblems; the data structure itself stores the minimal reusable history. In graph, prefix-hash, and repeated-query problems, `visited`, `dist`, prefix maps, parent maps, or cached subtree metadata play the “do not recompute solved state” role.

> **MEMORY TRICK —** DOMINATED CANDIDATES NEVER NEED TO RETURN.

### Interview follow-ups and variations

1. Can auxiliary space be reduced, and what invariant replaces it?
2. What changes if equality/duplicate semantics change?
3. What changes if input arrives online?
4. What changes for many repeated queries?
5. Name two related problems that reuse this invariant with only one comparison or one state dimension changed.

[↑ Back to Index](#navigation-and-index)

---

<a id="s25-maximal-rectangle"></a>
## S25. Maximal Rectangle

[← S24](#s24-largest-rectangle-in-histogram) · [Index](#navigation-and-index) · [S26 →](#s26-sliding-window-maximum)

### Detailed question understanding

**What is the problem/lesson asking?** Maximal Rectangle. Rewrite the exact input state, legal operation/relationship, and requested output before choosing a technique. Similar titles often hide materially different variants—for example nearest greater versus count of all greater values, four-direction versus eight-direction grid adjacency, or node identity versus equal node values.

### Three requirement-clarifying examples

| # | Input / setup | Output / observation | Why it matters |
|---:|---|---|---|
| 1 | `smallest valid input` | `trace base state` | base/boundary semantics |
| 2 | `typical nontrivial input` | `trace expected result` | core invariant |
| 3 | `boundary-shaped input` | `verify edge behavior` | protect implementation |

### Pattern recognition

**Primary pattern:** LIFO/FIFO invariant

> **KEY INTUITION —** Choose the representation whose natural endpoint matches the API operation.

### Brute-force / straightforward baseline

Start with the direct simulation/repeated scan that mirrors the statement. It is valuable because it exposes **what is being recomputed**. Then ask: *what exact fact from earlier work would make the next decision immediate?*

### Optimized reasoning

Name the invariant before code. The optimized solution for this family is derived from the intuition above. Every state change must preserve the invariant; if you cannot say what the working structure means after processing the first *i* items/nodes/states, the implementation is not ready.

### Detailed dry run

Write index/value, stack/queue/deque before, all pops/removals, insertion, and answer after each iteration.

For interview practice, include at least four snapshots: initial state, first nontrivial state change, the state where the central invariant does real work, and final state. Explain **why each mutation occurred**, not merely what the values became.

### Java implementation / template

```java
// Card-specific Java implementation is expanded during the scheduled deepening pass.
// Interview discipline:
// 1) define the state/invariant,
// 2) implement exactly one valid transition,
// 3) test empty/single/duplicate/extreme cases,
// 4) use long for sums/distances when constraints can overflow int.
```

### Key line to highlight

Find the line that changes the invariant: the monotonic `while`, a graph relaxation `if`, a pointer splice, a recursive return combination, or a HashMap lookup/update. Explain what would break if its comparison or ordering changed.

### Correctness checklist

1. State the invariant before the loop/recursion.
2. Show it is true initially.
3. Show every transition preserves it.
4. Explain why the terminal state implies the requested answer.
5. For greedy algorithms, identify the exchange/cut/monotonic argument that makes the local choice safe.

### Boundary conditions

- empty/null input when the platform permits it;
- one element/node/state;
- duplicates and strict-versus-nonstrict comparisons;
- monotone/skewed/disconnected shape where relevant;
- overflow in sums, products, distances and path counts—prefer `long` when needed;
- value vs index vs node identity;
- online/streaming input versus offline preprocessing;
- repeated queries may justify preprocessing that a one-shot query does not.

### Complexity — derive it instead of memorizing it

**Primitive operations O(1), total storage O(n) unless an emulation intentionally shifts work.**

Count how many times each element/node/edge can enter and leave the working structure, then multiply by the operation cost: array/index O(1), expected hash O(1), balanced tree/heap O(log n), full edge scan O(E).

### Memoization / repeated-work note

Memoization is useful only when the same logical state can be solved repeatedly. In many one-pass stack/list/tree problems there are no overlapping subproblems; the data structure itself stores the minimal reusable history. In graph, prefix-hash, and repeated-query problems, `visited`, `dist`, prefix maps, parent maps, or cached subtree metadata play the “do not recompute solved state” role.

> **MEMORY TRICK —** NAME THE ENDPOINT INVARIANT.

### Interview follow-ups and variations

1. Can auxiliary space be reduced, and what invariant replaces it?
2. What changes if equality/duplicate semantics change?
3. What changes if input arrives online?
4. What changes for many repeated queries?
5. Name two related problems that reuse this invariant with only one comparison or one state dimension changed.

[↑ Back to Index](#navigation-and-index)

---

<a id="s26-sliding-window-maximum"></a>
## S26. Sliding Window Maximum

### Requirement and examples

Given an integer array and a valid window length `k`, return one maximum for each contiguous window, ordered by its starting index. Windows overlap; elements are not removed from the input. This method rejects null arrays and `k < 1` or `k > n`.

| Input | k | Result | Reason |
|---|---:|---|---|
| `[1,3,-1,-3,5,3,6,7]` | 3 | `[3,3,5,5,6,7]` | Six overlapping windows |
| `[2,2,2]` | 2 | `[2,2]` | Equal maxima are valid |
| `[4,3,2,1]` | 2 | `[4,3,2]` | Old maxima expire |
| `[-4,-2,-5]` | 1 | `[-4,-2,-5]` | Each element is its own window |

### Brute force and the work to reuse

Scanning every window costs `O((n-k+1)k)`. Adjacent windows share `k-1` values. Store only candidates that can still become a maximum. A later value at least as large as an earlier candidate dominates it: it is better or equal and expires later.

### Invariant, dominance proof, and key lines

The deque stores **indices**, increasing from front to back; their values strictly decrease. Every index belongs to the current window. Any discarded live index has a later, at-least-as-large candidate, so it cannot change the maximum. Thus the front is the answer. First expire indices `<= i-k`, then remove dominated values from the back, then append `i`.

`a[dq.peekLast()] <= a[i]` retains the newer equal value. Using `<` is also correct but keeps equal candidates. Storing just values makes expiry ambiguous when duplicates occur.

### Detailed dry run

For `[1,3,-1,-3,5,3,6,7]`, `k=3`, entries below are `index:value` after insertion.

| i | Expired / dominated | Deque | Answer emitted |
|---:|---|---|---:|
| 0 | none | `[0:1]` | — |
| 1 | dominate `0:1` | `[1:3]` | — |
| 2 | none | `[1:3,2:-1]` | 3 |
| 3 | none | `[1:3,2:-1,3:-3]` | 3 |
| 4 | expire `1:3`; dominate `3:-3,2:-1` | `[4:5]` | 5 |
| 5 | none | `[4:5,5:3]` | 5 |
| 6 | dominate `5:3,4:5` | `[6:6]` | 6 |
| 7 | dominate `6:6` | `[7:7]` | 7 |

### Complete Java implementation

```java
import java.util.ArrayDeque;
import java.util.Deque;

public final class WindowMaximum {
    public static int[] maxSlidingWindow(int[] a, int k) {
        if (a == null || k < 1 || k > a.length)
            throw new IllegalArgumentException("Require 1 <= k <= length");
        int[] result = new int[a.length - k + 1];
        Deque<Integer> dq = new ArrayDeque<>();
        for (int i = 0; i < a.length; i++) {
            while (!dq.isEmpty() && dq.peekFirst() <= i - k)
                dq.removeFirst();
            while (!dq.isEmpty() && a[dq.peekLast()] <= a[i])
                dq.removeLast();
            dq.addLast(i);
            if (i >= k - 1) result[i - k + 1] = a[dq.peekFirst()];
        }
        return result;
    }
}
```

### Complexity, boundaries, and interview variations

Each index enters once and leaves at most once, so the nested `while` loops total `O(n)` deque operations. Java deque endpoint operations are amortized constant time. Auxiliary space is `O(k)`; the result uses `O(n-k+1)` additional space. No memoization table is needed: the deque retains exactly the reusable candidates.

Test increasing/decreasing data, equal values, negative values, `k=1`, and `k=n`. Do not emit answers before the first complete window. A lazy-deletion heap is an alternative, but stale entries can accumulate to `O(n)` storage and give `O(n log n)` time; claiming `O(k)` needs explicit deletion or compaction. Follow-ups: window minimum, bounded-range windows using two deques, and online streams. Memory cue: **expire front, dominate back, read front**.

Sources: [canonical problem](https://leetcode.com/problems/sliding-window-maximum/), [Oracle ArrayDeque](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/ArrayDeque.html). Dry run and proof are added study explanations.

[↑ Back to Index](#navigation-and-index)

---

<a id="s27-stock-span"></a>
## S27. Stock Span

[← S26](#s26-sliding-window-maximum) · [Index](#navigation-and-index) · [S28 →](#s28-celebrity-problem)

### Detailed question understanding

**What is the problem/lesson asking?** Stock Span. Rewrite the exact input state, legal operation/relationship, and requested output before choosing a technique. Similar titles often hide materially different variants—for example nearest greater versus count of all greater values, four-direction versus eight-direction grid adjacency, or node identity versus equal node values.

### Three requirement-clarifying examples

| # | Input / setup | Output / observation | Why it matters |
|---:|---|---|---|
| 1 | `smallest valid input` | `trace base state` | base/boundary semantics |
| 2 | `typical nontrivial input` | `trace expected result` | core invariant |
| 3 | `boundary-shaped input` | `verify edge behavior` | protect implementation |

### Pattern recognition

**Primary pattern:** Monotonic stack

> **KEY INTUITION —** Discard dominated candidates permanently. The surviving stack is exactly the set of unresolved candidates.

### Brute-force / straightforward baseline

Start with the direct simulation/repeated scan that mirrors the statement. It is valuable because it exposes **what is being recomputed**. Then ask: *what exact fact from earlier work would make the next decision immediate?*

### Optimized reasoning

Name the invariant before code. The optimized solution for this family is derived from the intuition above. Every state change must preserve the invariant; if you cannot say what the working structure means after processing the first *i* items/nodes/states, the implementation is not ready.

### Detailed dry run

Write index/value, stack/queue/deque before, all pops/removals, insertion, and answer after each iteration.

For interview practice, include at least four snapshots: initial state, first nontrivial state change, the state where the central invariant does real work, and final state. Explain **why each mutation occurred**, not merely what the values became.

### Java implementation / template

```java
// Card-specific Java implementation is expanded during the scheduled deepening pass.
// Interview discipline:
// 1) define the state/invariant,
// 2) implement exactly one valid transition,
// 3) test empty/single/duplicate/extreme cases,
// 4) use long for sums/distances when constraints can overflow int.
```

### Key line to highlight

Find the line that changes the invariant: the monotonic `while`, a graph relaxation `if`, a pointer splice, a recursive return combination, or a HashMap lookup/update. Explain what would break if its comparison or ordering changed.

### Correctness checklist

1. State the invariant before the loop/recursion.
2. Show it is true initially.
3. Show every transition preserves it.
4. Explain why the terminal state implies the requested answer.
5. For greedy algorithms, identify the exchange/cut/monotonic argument that makes the local choice safe.

### Boundary conditions

- empty/null input when the platform permits it;
- one element/node/state;
- duplicates and strict-versus-nonstrict comparisons;
- monotone/skewed/disconnected shape where relevant;
- overflow in sums, products, distances and path counts—prefer `long` when needed;
- value vs index vs node identity;
- online/streaming input versus offline preprocessing;
- repeated queries may justify preprocessing that a one-shot query does not.

### Complexity — derive it instead of memorizing it

**Each index is pushed once and popped at most once → O(n) amortized; O(n) stack.**

Count how many times each element/node/edge can enter and leave the working structure, then multiply by the operation cost: array/index O(1), expected hash O(1), balanced tree/heap O(log n), full edge scan O(E).

### Memoization / repeated-work note

Memoization is useful only when the same logical state can be solved repeatedly. In many one-pass stack/list/tree problems there are no overlapping subproblems; the data structure itself stores the minimal reusable history. In graph, prefix-hash, and repeated-query problems, `visited`, `dist`, prefix maps, parent maps, or cached subtree metadata play the “do not recompute solved state” role.

> **MEMORY TRICK —** DOMINATED CANDIDATES NEVER NEED TO RETURN.

### Interview follow-ups and variations

1. Can auxiliary space be reduced, and what invariant replaces it?
2. What changes if equality/duplicate semantics change?
3. What changes if input arrives online?
4. What changes for many repeated queries?
5. Name two related problems that reuse this invariant with only one comparison or one state dimension changed.

[↑ Back to Index](#navigation-and-index)

---

<a id="s28-celebrity-problem"></a>
## S28. Celebrity Problem

[← S27](#s27-stock-span) · [Index](#navigation-and-index) · [S29 →](#s29-lru-cache)

### Detailed question understanding

**What is the problem/lesson asking?** Celebrity Problem. Rewrite the exact input state, legal operation/relationship, and requested output before choosing a technique. Similar titles often hide materially different variants—for example nearest greater versus count of all greater values, four-direction versus eight-direction grid adjacency, or node identity versus equal node values.

### Three requirement-clarifying examples

| # | Input / setup | Output / observation | Why it matters |
|---:|---|---|---|
| 1 | `smallest valid input` | `trace base state` | base/boundary semantics |
| 2 | `typical nontrivial input` | `trace expected result` | core invariant |
| 3 | `boundary-shaped input` | `verify edge behavior` | protect implementation |

### Pattern recognition

**Primary pattern:** LIFO/FIFO invariant

> **KEY INTUITION —** Choose the representation whose natural endpoint matches the API operation.

### Brute-force / straightforward baseline

Start with the direct simulation/repeated scan that mirrors the statement. It is valuable because it exposes **what is being recomputed**. Then ask: *what exact fact from earlier work would make the next decision immediate?*

### Optimized reasoning

Name the invariant before code. The optimized solution for this family is derived from the intuition above. Every state change must preserve the invariant; if you cannot say what the working structure means after processing the first *i* items/nodes/states, the implementation is not ready.

### Detailed dry run

Write index/value, stack/queue/deque before, all pops/removals, insertion, and answer after each iteration.

For interview practice, include at least four snapshots: initial state, first nontrivial state change, the state where the central invariant does real work, and final state. Explain **why each mutation occurred**, not merely what the values became.

### Java implementation / template

```java
// Card-specific Java implementation is expanded during the scheduled deepening pass.
// Interview discipline:
// 1) define the state/invariant,
// 2) implement exactly one valid transition,
// 3) test empty/single/duplicate/extreme cases,
// 4) use long for sums/distances when constraints can overflow int.
```

### Key line to highlight

Find the line that changes the invariant: the monotonic `while`, a graph relaxation `if`, a pointer splice, a recursive return combination, or a HashMap lookup/update. Explain what would break if its comparison or ordering changed.

### Correctness checklist

1. State the invariant before the loop/recursion.
2. Show it is true initially.
3. Show every transition preserves it.
4. Explain why the terminal state implies the requested answer.
5. For greedy algorithms, identify the exchange/cut/monotonic argument that makes the local choice safe.

### Boundary conditions

- empty/null input when the platform permits it;
- one element/node/state;
- duplicates and strict-versus-nonstrict comparisons;
- monotone/skewed/disconnected shape where relevant;
- overflow in sums, products, distances and path counts—prefer `long` when needed;
- value vs index vs node identity;
- online/streaming input versus offline preprocessing;
- repeated queries may justify preprocessing that a one-shot query does not.

### Complexity — derive it instead of memorizing it

**Primitive operations O(1), total storage O(n) unless an emulation intentionally shifts work.**

Count how many times each element/node/edge can enter and leave the working structure, then multiply by the operation cost: array/index O(1), expected hash O(1), balanced tree/heap O(log n), full edge scan O(E).

### Memoization / repeated-work note

Memoization is useful only when the same logical state can be solved repeatedly. In many one-pass stack/list/tree problems there are no overlapping subproblems; the data structure itself stores the minimal reusable history. In graph, prefix-hash, and repeated-query problems, `visited`, `dist`, prefix maps, parent maps, or cached subtree metadata play the “do not recompute solved state” role.

> **MEMORY TRICK —** NAME THE ENDPOINT INVARIANT.

### Interview follow-ups and variations

1. Can auxiliary space be reduced, and what invariant replaces it?
2. What changes if equality/duplicate semantics change?
3. What changes if input arrives online?
4. What changes for many repeated queries?
5. Name two related problems that reuse this invariant with only one comparison or one state dimension changed.

[↑ Back to Index](#navigation-and-index)

---

<a id="s29-lru-cache"></a>
## S29. LRU Cache

[← S28](#s28-celebrity-problem) · [Index](#navigation-and-index) · [S30 →](#s30-lfu-cache)

### Detailed question understanding

**What is the problem/lesson asking?** LRU Cache. Rewrite the exact input state, legal operation/relationship, and requested output before choosing a technique. Similar titles often hide materially different variants—for example nearest greater versus count of all greater values, four-direction versus eight-direction grid adjacency, or node identity versus equal node values.

### Three requirement-clarifying examples

| # | Input / setup | Output / observation | Why it matters |
|---:|---|---|---|
| 1 | `smallest valid input` | `trace base state` | base/boundary semantics |
| 2 | `typical nontrivial input` | `trace expected result` | core invariant |
| 3 | `boundary-shaped input` | `verify edge behavior` | protect implementation |

### Pattern recognition

**Primary pattern:** Multi-index design

> **KEY INTUITION —** HashMap locates entries; linked frequency/recency structures maintain eviction order in O(1).

### Brute-force / straightforward baseline

Start with the direct simulation/repeated scan that mirrors the statement. It is valuable because it exposes **what is being recomputed**. Then ask: *what exact fact from earlier work would make the next decision immediate?*

### Optimized reasoning

Name the invariant before code. The optimized solution for this family is derived from the intuition above. Every state change must preserve the invariant; if you cannot say what the working structure means after processing the first *i* items/nodes/states, the implementation is not ready.

### Detailed dry run

Write index/value, stack/queue/deque before, all pops/removals, insertion, and answer after each iteration.

For interview practice, include at least four snapshots: initial state, first nontrivial state change, the state where the central invariant does real work, and final state. Explain **why each mutation occurred**, not merely what the values became.

### Java implementation / template

```java
// Card-specific Java implementation is expanded during the scheduled deepening pass.
// Interview discipline:
// 1) define the state/invariant,
// 2) implement exactly one valid transition,
// 3) test empty/single/duplicate/extreme cases,
// 4) use long for sums/distances when constraints can overflow int.
```

### Key line to highlight

Find the line that changes the invariant: the monotonic `while`, a graph relaxation `if`, a pointer splice, a recursive return combination, or a HashMap lookup/update. Explain what would break if its comparison or ordering changed.

### Correctness checklist

1. State the invariant before the loop/recursion.
2. Show it is true initially.
3. Show every transition preserves it.
4. Explain why the terminal state implies the requested answer.
5. For greedy algorithms, identify the exchange/cut/monotonic argument that makes the local choice safe.

### Boundary conditions

- empty/null input when the platform permits it;
- one element/node/state;
- duplicates and strict-versus-nonstrict comparisons;
- monotone/skewed/disconnected shape where relevant;
- overflow in sums, products, distances and path counts—prefer `long` when needed;
- value vs index vs node identity;
- online/streaming input versus offline preprocessing;
- repeated queries may justify preprocessing that a one-shot query does not.

### Complexity — derive it instead of memorizing it

**Expected O(1) get/put; O(capacity) memory.**

Count how many times each element/node/edge can enter and leave the working structure, then multiply by the operation cost: array/index O(1), expected hash O(1), balanced tree/heap O(log n), full edge scan O(E).

### Memoization / repeated-work note

Memoization is useful only when the same logical state can be solved repeatedly. In many one-pass stack/list/tree problems there are no overlapping subproblems; the data structure itself stores the minimal reusable history. In graph, prefix-hash, and repeated-query problems, `visited`, `dist`, prefix maps, parent maps, or cached subtree metadata play the “do not recompute solved state” role.

> **MEMORY TRICK —** MAP FINDS; LINKED STRUCTURE ORDERS.

### Interview follow-ups and variations

1. Can auxiliary space be reduced, and what invariant replaces it?
2. What changes if equality/duplicate semantics change?
3. What changes if input arrives online?
4. What changes for many repeated queries?
5. Name two related problems that reuse this invariant with only one comparison or one state dimension changed.

[↑ Back to Index](#navigation-and-index)

---

<a id="s30-lfu-cache"></a>
## S30. LFU Cache

[← S29](#s29-lru-cache) · [Index](#navigation-and-index)

### Detailed question understanding

**What is the problem/lesson asking?** LFU Cache. Rewrite the exact input state, legal operation/relationship, and requested output before choosing a technique. Similar titles often hide materially different variants—for example nearest greater versus count of all greater values, four-direction versus eight-direction grid adjacency, or node identity versus equal node values.

### Three requirement-clarifying examples

| # | Input / setup | Output / observation | Why it matters |
|---:|---|---|---|
| 1 | `smallest valid input` | `trace base state` | base/boundary semantics |
| 2 | `typical nontrivial input` | `trace expected result` | core invariant |
| 3 | `boundary-shaped input` | `verify edge behavior` | protect implementation |

### Pattern recognition

**Primary pattern:** Multi-index design

> **KEY INTUITION —** HashMap locates entries; linked frequency/recency structures maintain eviction order in O(1).

### Brute-force / straightforward baseline

Start with the direct simulation/repeated scan that mirrors the statement. It is valuable because it exposes **what is being recomputed**. Then ask: *what exact fact from earlier work would make the next decision immediate?*

### Optimized reasoning

Name the invariant before code. The optimized solution for this family is derived from the intuition above. Every state change must preserve the invariant; if you cannot say what the working structure means after processing the first *i* items/nodes/states, the implementation is not ready.

### Detailed dry run

Write index/value, stack/queue/deque before, all pops/removals, insertion, and answer after each iteration.

For interview practice, include at least four snapshots: initial state, first nontrivial state change, the state where the central invariant does real work, and final state. Explain **why each mutation occurred**, not merely what the values became.

### Java implementation / template

```java
// Card-specific Java implementation is expanded during the scheduled deepening pass.
// Interview discipline:
// 1) define the state/invariant,
// 2) implement exactly one valid transition,
// 3) test empty/single/duplicate/extreme cases,
// 4) use long for sums/distances when constraints can overflow int.
```

### Key line to highlight

Find the line that changes the invariant: the monotonic `while`, a graph relaxation `if`, a pointer splice, a recursive return combination, or a HashMap lookup/update. Explain what would break if its comparison or ordering changed.

### Correctness checklist

1. State the invariant before the loop/recursion.
2. Show it is true initially.
3. Show every transition preserves it.
4. Explain why the terminal state implies the requested answer.
5. For greedy algorithms, identify the exchange/cut/monotonic argument that makes the local choice safe.

### Boundary conditions

- empty/null input when the platform permits it;
- one element/node/state;
- duplicates and strict-versus-nonstrict comparisons;
- monotone/skewed/disconnected shape where relevant;
- overflow in sums, products, distances and path counts—prefer `long` when needed;
- value vs index vs node identity;
- online/streaming input versus offline preprocessing;
- repeated queries may justify preprocessing that a one-shot query does not.

### Complexity — derive it instead of memorizing it

**Expected O(1) get/put; O(capacity) memory.**

Count how many times each element/node/edge can enter and leave the working structure, then multiply by the operation cost: array/index O(1), expected hash O(1), balanced tree/heap O(log n), full edge scan O(E).

### Memoization / repeated-work note

Memoization is useful only when the same logical state can be solved repeatedly. In many one-pass stack/list/tree problems there are no overlapping subproblems; the data structure itself stores the minimal reusable history. In graph, prefix-hash, and repeated-query problems, `visited`, `dist`, prefix maps, parent maps, or cached subtree metadata play the “do not recompute solved state” role.

> **MEMORY TRICK —** MAP FINDS; LINKED STRUCTURE ORDERS.

### Interview follow-ups and variations

1. Can auxiliary space be reduced, and what invariant replaces it?
2. What changes if equality/duplicate semantics change?
3. What changes if input arrives online?
4. What changes for many repeated queries?
5. Name two related problems that reuse this invariant with only one comparison or one state dimension changed.

[↑ Back to Index](#navigation-and-index)

---

## Final Revision

### What I must be able to do without notes

1. Explain the requirement before naming the algorithm.
2. State the invariant in one sentence.
3. Produce a dry run with visible state transitions.
4. Explain exactly why brute force repeats work.
5. Derive time/space complexity from operation counts.
6. Name edge cases that change strictness, ownership or reachability.
7. Explain whether memoization is useful and what the memo key would be.
8. Give at least two related problems using the same pattern.

### Source hierarchy used for continued enrichment

1. Take U Forward / Striver A2Z and official topic pages for course spine.
2. CP-Algorithms for graph/data-structure invariants, proofs and complexity.
3. Oracle/OpenJDK for Java collection behavior.
4. Canonical problem statements (e.g. LeetCode/GFG) for variants and test semantics.
5. Clearly labeled added study explanations for intuition, memory tricks and extra dry runs.

[↑ Navigation and Index](#navigation-and-index)
