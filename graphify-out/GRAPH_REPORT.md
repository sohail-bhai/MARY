# Graph Report - MAry  (2026-11-19)

## Corpus Check
- cluster-only mode — file stats not available

## Summary
- 169 nodes · 278 edges · 14 communities (4 shown, 10 thin omitted)
- Extraction: 100% EXTRACTED · 0% INFERRED · 0% AMBIGUOUS
- Token cost: 0 input · 0 output

## Community Hubs (Navigation)
- Community 1
- Community 2
- Community 3
- Community 4
- Community 5
- Community 6
- Community 7
- Community 8
- Community 9

## God Nodes (most connected - your core abstractions)
1. `MaryNotificationStore` - 18 edges
2. `executeMaryAction()` - 16 edges
3. `MaryReadingSession` - 10 edges
4. `MainActivity` - 9 edges
5. `MaryNotificationListener` - 9 edges
6. `MaryConversationMemory` - 8 edges
7. `readPersonWhatsAppMessage()` - 8 edges
8. `UtteranceProgressListener` - 7 edges
9. `detectMaryCommand()` - 7 edges
10. `WhatsAppMessage` - 6 edges

## Surprising Connections (you probably didn't know these)
- `MaryReadingSession` --references--> `WhatsAppMessage`  [EXTRACTED]
  app/src/main/java/com/sohail/myassistant/ReadingSession.kt → app/src/main/java/com/sohail/myassistant/WhatsAppMessage.kt
- `MaryNotificationStore` --references--> `MaryNotificationListener`  [EXTRACTED]
  app/src/main/java/com/sohail/myassistant/WhatsAppMessageStore.kt → app/src/main/java/com/sohail/myassistant/MaryNotificationListener.kt

## Import Cycles
- None detected.

## Communities (14 total, 10 thin omitted)

### Community 1 - "Community 1"
Cohesion: 0.12
Nodes (10): countWhatsAppMessages(), executeMaryAction(), handleAutoListenTimeout(), listWhatsAppSenders(), openNotificationAccessSettings(), openWhatsApp(), readLatestWhatsAppNotification(), sendMediaButton() (+2 more)

### Community 2 - "Community 2"
Cohesion: 0.27
Nodes (5): readAllPendingPersonMessages(), readPersonWhatsAppMessage(), readSelectedChatMessages(), WhatsAppMessage, MaryNotificationStore

### Community 3 - "Community 3"
Cohesion: 0.15
Nodes (3): MainActivity, UtteranceProgressListener, MaryHomeScreen()

### Community 6 - "Community 6"
Cohesion: 0.26
Nodes (5): detectMaryCommand(), extractPossiblePersonWords(), MaryCommand, MaryConversationMemory, PendingChatOption

## Knowledge Gaps
- **10 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `MaryNotificationStore` connect `Community 2` to `Community 1`, `Community 4`, `Community 6`?**
  _High betweenness centrality (0.122) - this node is a cross-community bridge._
- **Should `Community 0` be split into smaller, more focused modules?**
  _Cohesion score 0.05405405405405406 - nodes in this community are weakly interconnected._
- **Why does `MaryNotificationListener` connect `Community 4` to `Community 2`?**
  _High betweenness centrality (0.068) - this node is a cross-community bridge._
- **Should `Community 1` be split into smaller, more focused modules?**
  _Cohesion score 0.11954022988505747 - nodes in this community are weakly interconnected._
- **Why does `executeMaryAction()` connect `Community 1` to `Community 2`?**
  _High betweenness centrality (0.055) - this node is a cross-community bridge._