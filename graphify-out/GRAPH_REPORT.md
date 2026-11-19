# Graph Report - MAry  (2026-11-19)

## Corpus Check
- 20 files · ~8,611 words
- Verdict: corpus is large enough that graph structure adds value.
- Unclassified: 19 file(s) not represented in the graph (top: .xml 10, .properties 3, (none) 2)

## Summary
- 234 nodes · 457 edges · 20 communities (4 shown, 16 thin omitted)
- Extraction: 98% EXTRACTED · 2% INFERRED · 0% AMBIGUOUS · INFERRED: 8 edges (avg confidence: 0.85)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `170ee2a7`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- ConversationManager.kt
- MaryNotificationStore
- .onCreate
- MaryNotificationListener
- BluetoothAudioRouter.kt
- detectMaryCommand
- Theme.kt
- ExampleInstrumentedTest.kt
- ExampleUnitTest.kt
- VoiceAssistantController
- VoiceAssistantController.kt
- MainActivity

## God Nodes (most connected - your core abstractions)
1. `VoiceAssistantController` - 24 edges
2. `MaryNotificationStore` - 24 edges
3. `executeMaryAction()` - 16 edges
4. `MaryNotificationListener` - 14 edges
5. `RecognitionListener` - 13 edges
6. `WhatsAppMessage` - 13 edges
7. `detectMaryCommand()` - 12 edges
8. `VoiceAssistantUnitTest` - 10 edges
9. `MaryReadingSession` - 10 edges
10. `MaryConversationMemory` - 8 edges

## Surprising Connections (you probably didn't know these)
- `MainActivity` --references--> `VoiceAssistantController`  [EXTRACTED]
  app/src/main/java/com/sohail/myassistant/MainActivity.kt → app/src/main/java/com/sohail/myassistant/VoiceAssistantController.kt
- `MaryHomeScreen()` --references--> `VoiceAssistantController`  [EXTRACTED]
  app/src/main/java/com/sohail/myassistant/MainActivity.kt → app/src/main/java/com/sohail/myassistant/VoiceAssistantController.kt
- `MaryNotificationStore` --references--> `MaryNotificationListener`  [EXTRACTED]
  app/src/main/java/com/sohail/myassistant/WhatsAppMessageStore.kt → app/src/main/java/com/sohail/myassistant/MaryNotificationListener.kt
- `MaryReadingSession` --references--> `WhatsAppMessage`  [EXTRACTED]
  app/src/main/java/com/sohail/myassistant/ReadingSession.kt → app/src/main/java/com/sohail/myassistant/WhatsAppMessage.kt

## Import Cycles
- None detected.

## Communities (20 total, 16 thin omitted)

### Community 1 - "ConversationManager.kt"
Cohesion: 0.13
Nodes (13): countWhatsAppMessages(), executeMaryAction(), handleAutoListenTimeout(), listWhatsAppSenders(), MaryConversationMemory, openNotificationAccessSettings(), openWhatsApp(), PendingChatOption (+5 more)

### Community 2 - "MaryNotificationStore"
Cohesion: 0.17
Nodes (4): readSelectedChatMessages(), updateConversationState(), WhatsAppMessage, MaryNotificationStore

### Community 6 - "detectMaryCommand"
Cohesion: 0.17
Nodes (4): detectMaryCommand(), extractPossiblePersonWords(), MaryCommand, VoiceAssistantUnitTest

### Community 14 - "VoiceAssistantController"
Cohesion: 0.17
Nodes (3): VoiceAssistantController, RecognitionCallbacks, UtteranceProgressListener

## Knowledge Gaps
- **16 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `VoiceAssistantController` connect `VoiceAssistantController` to `MainActivity`, `.onCreate`, `detectMaryCommand`, `VoiceAssistantController.kt`?**
  _High betweenness centrality (0.155) - this node is a cross-community bridge._
- **Should `MainActivity.kt` be split into smaller, more focused modules?**
  _Cohesion score 0.07407407407407407 - nodes in this community are weakly interconnected._
- **Why does `MaryNotificationStore` connect `MaryNotificationStore` to `MaryNotificationListener`, `VoiceAssistantController.kt`?**
  _High betweenness centrality (0.096) - this node is a cross-community bridge._
- **Should `ConversationManager.kt` be split into smaller, more focused modules?**
  _Cohesion score 0.1268939393939394 - nodes in this community are weakly interconnected._
- **Why does `RecognitionListener` connect `VoiceAssistantController.kt` to `VoiceAssistantController`?**
  _High betweenness centrality (0.061) - this node is a cross-community bridge._
- **Should `VoiceAssistantController.kt` be split into smaller, more focused modules?**
  _Cohesion score 0.10952380952380952 - nodes in this community are weakly interconnected._