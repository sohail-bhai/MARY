# Graph Report - chatgpt  (2026-11-19)

## Corpus Check
- 18 files · ~6,700 words
- Verdict: corpus is large enough that graph structure adds value.
- Unclassified: 20 file(s) not represented in the graph (top: .xml 10, (none) 3, .properties 3)
- WARNING: 20 of 49 files seen (41%) have no supported extractor and are missing from this graph entirely. It may look complete while representing only part of the codebase.

## Summary
- 183 nodes · 330 edges · 15 communities (3 shown, 12 thin omitted)
- Extraction: 100% EXTRACTED · 0% INFERRED · 0% AMBIGUOUS
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `3fb991d1`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- ConversationManager.kt
- MaryNotificationStore
- MainActivity
- MaryNotificationListener
- BluetoothAudioRouter.kt
- MaryConversationMemory
- Theme.kt
- ExampleInstrumentedTest.kt
- ExampleUnitTest.kt

## God Nodes (most connected - your core abstractions)
1. `MaryNotificationStore` - 24 edges
2. `executeMaryAction()` - 16 edges
3. `MaryNotificationListener` - 14 edges
4. `WhatsAppMessage` - 12 edges
5. `MaryReadingSession` - 10 edges
6. `MainActivity` - 9 edges
7. `MaryConversationMemory` - 8 edges
8. `readPersonWhatsAppMessage()` - 8 edges
9. `UtteranceProgressListener` - 7 edges
10. `detectMaryCommand()` - 7 edges

## Surprising Connections (you probably didn't know these)
- `MaryNotificationStore` --references--> `MaryNotificationListener`  [EXTRACTED]
  app/src/main/java/com/sohail/myassistant/WhatsAppMessageStore.kt → app/src/main/java/com/sohail/myassistant/MaryNotificationListener.kt
- `MaryReadingSession` --references--> `WhatsAppMessage`  [EXTRACTED]
  app/src/main/java/com/sohail/myassistant/ReadingSession.kt → app/src/main/java/com/sohail/myassistant/WhatsAppMessage.kt

## Import Cycles
- None detected.

## Communities (15 total, 12 thin omitted)

### Community 1 - "ConversationManager.kt"
Cohesion: 0.14
Nodes (13): countWhatsAppMessages(), executeMaryAction(), handleAutoListenTimeout(), listWhatsAppSenders(), openNotificationAccessSettings(), openWhatsApp(), readAllPendingPersonMessages(), readLatestWhatsAppNotification() (+5 more)

### Community 3 - "MainActivity"
Cohesion: 0.14
Nodes (3): MainActivity, UtteranceProgressListener, MaryHomeScreen()

### Community 6 - "MaryConversationMemory"
Cohesion: 0.26
Nodes (5): detectMaryCommand(), extractPossiblePersonWords(), MaryCommand, MaryConversationMemory, PendingChatOption

## Knowledge Gaps
- **12 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `MaryNotificationStore` connect `MaryNotificationStore` to `MaryNotificationListener`, `MaryConversationMemory`?**
  _High betweenness centrality (0.119) - this node is a cross-community bridge._
- **Should `MainActivity.kt` be split into smaller, more focused modules?**
  _Cohesion score 0.05263157894736842 - nodes in this community are weakly interconnected._
- **Why does `WhatsAppMessage` connect `MaryNotificationListener` to `ConversationManager.kt`, `MaryNotificationStore`?**
  _High betweenness centrality (0.066) - this node is a cross-community bridge._
- **Should `ConversationManager.kt` be split into smaller, more focused modules?**
  _Cohesion score 0.14285714285714285 - nodes in this community are weakly interconnected._
- **Why does `MaryNotificationListener` connect `MaryNotificationListener` to `MaryNotificationStore`?**
  _High betweenness centrality (0.061) - this node is a cross-community bridge._
- **Should `MainActivity` be split into smaller, more focused modules?**
  _Cohesion score 0.13970588235294118 - nodes in this community are weakly interconnected._