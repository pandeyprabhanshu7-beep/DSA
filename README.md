# DSA Study Repository

This repository contains the canonical study-guide editions promoted from the **Youtube series Study document** project.

## Striver DSA library

- [Stack & Queue](study-guides/striver-dsa/stack-queue/)
- [Graph DSA](study-guides/striver-dsa/graph-dsa/)
- [Linked List](study-guides/striver-dsa/linked-list/)
- [Trees & BST](study-guides/striver-dsa/trees-bst/)
- [HashMap & Hashing](study-guides/striver-dsa/hashmap-hashing/)

Each topic keeps only the current canonical edition:

```text
topic/
├── markdown/   # detailed source study guide
├── html/       # responsive/mobile reading version
└── assets/     # diagrams used by the topic
```

Historical V2/V3/FINAL/ULTIMATE duplicates are intentionally not promoted as separate competing files. Future enrichment updates the canonical paths.

## Study standard

For important questions the intended reading flow is:

1. detailed problem understanding
2. 3–4 examples
3. concrete data/diagram
4. pattern recognition
5. brute force
6. repeated-work diagnosis
7. key intuition/invariant
8. optimized algorithm
9. detailed dry run
10. Java implementation
11. key-line explanation
12. correctness reasoning
13. time/space derivation
14. boundary cases
15. common mistakes
16. interview follow-ups
17. related variations

See [promotion status](study-guides/striver-dsa/STATUS.md).


## Continuous study-guide enrichment

The hourly pipeline covers the full technical study collection, including DSA, AWS/cloud, AI, system design, databases, DevOps/backend and frontend. It processes 1–2 sections per run and maintains matching Markdown/HTML editions.

- [Pipeline rules](study-guides/ENHANCEMENT_PIPELINE.md)
- [Source inventory](study-guides/SOURCE_INVENTORY.md)
- [Tracked enrichment queue](study-guides/ENRICHMENT_QUEUE.json)
- [AWS networking bootcamp](study-guides/aws/networking/) and [continuation status](study-guides/aws/STATUS.md)

Queued sources are not automatically complete or published; the inventory distinguishes existing canonical guides, discovered source candidates and sources still to locate.
