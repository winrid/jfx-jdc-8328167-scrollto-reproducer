# JDK-8328167: TableView.scrollTo() not ensuring full visibility

This repository reproduces a bug in JavaFX where `TableView.scrollTo()` (and `ListView.scrollTo()`) can leave the target row partially cut off at the bottom of the viewport.

**Bug Report:** https://bugs.openjdk.org/browse/JDK-8328167

## The Problem

When `scrollTo()` is called on items near the end of a list, the target row may be partially visible (cut off at the bottom) instead of fully visible within the viewport.

### Why This Happens

JavaFX processes frames in distinct phases during each pulse:

1. **Events / Runnables** - Input events and `Platform.runLater()` callbacks execute
2. **Animation** - Timeline and animation updates
3. **CSS** - Style resolution
4. **Layout** - `layoutChildren()` is called, viewport dimensions are finalized
5. **Rendering** - Scene is painted

When `scrollTo()` is called (during phase 1), `VirtualFlow.scrollToTop()` calculates the scroll position before layout has run. For newly added items, the viewport dimensions may be stale or zero, causing incorrect position calculations. By the time layout runs (phase 4), the position has already been set incorrectly.

## How to Reproduce

### Prerequisites
- Java 21+
- Maven

### Run the reproducer (bug visible)

```bash
mvn clean compile exec:java -Dexec.mainClass="com.example.ScrollToBugTest"
```

### Run with fix enabled

```bash
mvn compile exec:java -Dexec.mainClass="com.example.ScrollToBugTest" -Dexec.args="--fix"
```

### Run with double Platform.runLater() workaround

```bash
mvn compile exec:java -Dexec.mainClass="com.example.ScrollToBugTest" -Dexec.args="--hack-double-run-later"
```

This demonstrates an alternative workaround using nested `Platform.runLater()` calls to delay the `scrollTo()` until after layout completes.

The test will:
1. Create a TableView
2. Add items
3. Call `scrollTo()` on the last item
4. Check if the row is fully visible

**Expected:** All tests PASS (row fully visible)
**Actual (without fix):** Tests FAIL (row partially cut off)

## The Fix

This repository includes a patched `VirtualFlow.java` with a fix that can be toggled on/off.

### Enable the fix

In your code, before creating any TableView/ListView:

```java
import javafx.scene.control.skin.VirtualFlow;

VirtualFlow.ENABLE_SCROLLTO_VISIBILITY_FIX = true;
```

### How the fix works

The fix tracks the pending scroll target index and verifies full visibility at the end of `layoutChildren()`. If the cell extends beyond the viewport (and is smaller than the viewport), it adjusts the scroll position to ensure the cell is fully visible.

## Files

- `src/main/java/com/example/ScrollToBugTest.java` - Reproducer application
- `src/main/java/javafx/scene/control/skin/VirtualFlow.java` - Patched VirtualFlow with fix
