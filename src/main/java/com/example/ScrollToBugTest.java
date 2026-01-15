package com.example;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Bounds;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.skin.VirtualFlow;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.Arrays;
import java.util.Timer;
import java.util.TimerTask;

public class ScrollToBugTest extends Application {

    private TableView<Item> tableView;
    private ObservableList<Item> items;
    private Label statusLabel;
    private int testNumber = 0;
    private int passCount = 0;
    private int failCount = 0;

    private static boolean useDoubleRunLaterHack = false;

    public static void main(String[] args) {
        var argList = Arrays.asList(args);

        if (argList.contains("--fix")) {
            System.out.println("Fix ENABLED: VirtualFlow.ENABLE_SCROLLTO_VISIBILITY_FIX = true");
            VirtualFlow.ENABLE_SCROLLTO_VISIBILITY_FIX = true;
        } else if (argList.contains("--hack-double-run-later")) {
            System.out.println("HACK ENABLED: Using double Platform.runLater() workaround");
            useDoubleRunLaterHack = true;
        } else {
            System.out.println("Fix DISABLED (default). Run with --fix or --hack-double-run-later to enable workarounds.");
        }
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        tableView = new TableView<>();
        items = FXCollections.observableArrayList();
        tableView.setItems(items);

        // Create columns
        TableColumn<Item, String> idCol = new TableColumn<>("ID");
        idCol.setCellValueFactory(data -> data.getValue().idProperty());
        idCol.setPrefWidth(100);

        TableColumn<Item, String> nameCol = new TableColumn<>("Name");
        nameCol.setCellValueFactory(data -> data.getValue().nameProperty());
        nameCol.setPrefWidth(200);

        TableColumn<Item, String> descCol = new TableColumn<>("Description");
        descCol.setCellValueFactory(data -> data.getValue().descriptionProperty());
        descCol.setPrefWidth(300);

        tableView.getColumns().addAll(idCol, nameCol, descCol);
        tableView.setPrefHeight(400);

        statusLabel = new Label("Starting tests...");
        statusLabel.setStyle("-fx-font-size: 14px; -fx-padding: 10px;");

        VBox root = new VBox(10, statusLabel, tableView);
        root.setStyle("-fx-padding: 10px;");

        Scene scene = new Scene(root, 650, 500);
        primaryStage.setTitle("TableView scrollTo Bug Test");
        primaryStage.setScene(scene);
        primaryStage.show();

        // Start tests after a short delay to let UI initialize
        Timer timer = new Timer(true);
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                Platform.runLater(() -> runNextTest());
            }
        }, 500);
    }

    private void runNextTest() {
        testNumber++;
        if (testNumber > 3) {
            statusLabel.setText(String.format("All tests complete! Passed: %d, Failed: %d", passCount, failCount));
            return;
        }

        statusLabel.setText(String.format("Running test %d/3...", testNumber));

        // Clear and add new items
        items.clear();
        int itemCount = 50 + (testNumber * 20); // Different counts for each test

        for (int i = 0; i < itemCount; i++) {
            items.add(new Item(
                String.valueOf(i + 1),
                "Item " + (i + 1),
                "Description for item " + (i + 1)
            ));
        }

        // Get the last item
        Item lastItem = items.get(items.size() - 1);

        // Scroll to last item
        if (useDoubleRunLaterHack) {
            // Hack: Use double Platform.runLater to ensure layout completes before scrolling
            // First runLater runs after current event processing
            // Second runLater runs after layout pass triggered by first
            Platform.runLater(() -> {
                Platform.runLater(() -> {
                    tableView.scrollTo(lastItem);
                });
            });
        } else {
            tableView.scrollTo(lastItem);
        }

        // Check visibility after a delay to allow scroll to complete
        Timer timer = new Timer(true);
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                Platform.runLater(() -> checkVisibility(lastItem, testNumber));
            }
        }, 300);
    }

    private void checkVisibility(Item lastItem, int currentTest) {
        boolean isVisible = isItemVisible(lastItem);

        String result;
        if (isVisible) {
            passCount++;
            result = "PASS";
        } else {
            failCount++;
            result = "FAIL";
        }

        int lastIndex = items.size() - 1;
        statusLabel.setText(String.format(
            "Test %d: scrollTo(item[%d]) - %s (Item %s visible in viewport)\nPassed: %d, Failed: %d",
            currentTest, lastIndex, result, isVisible ? "IS" : "is NOT", passCount, failCount
        ));

        System.out.println(String.format(
            "Test %d: scrollTo(item[%d]) - %s",
            currentTest, lastIndex, result
        ));

        // Stop on failure and exit
        if (!isVisible) {
            System.out.println("BUG REPRODUCED - Exiting.");
            Platform.exit();
            return;
        }

        // Run next test after a delay
        Timer timer = new Timer(true);
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                Platform.runLater(() -> runNextTest());
            }
        }, 1500);
    }

    private boolean isItemVisible(Item item) {
        // Get the table's viewport bounds
        Bounds tableBounds = tableView.localToScene(tableView.getBoundsInLocal());

        // Find the row for this item
        int index = items.indexOf(item);
        if (index < 0) {
            System.out.println("Item not found in list");
            return false;
        }

        // Try to find the actual row node
        TableRow<?> targetRow = null;
        for (var node : tableView.lookupAll(".table-row-cell")) {
            if (node instanceof TableRow<?> row) {
                if (row.getIndex() == index && row.getItem() != null) {
                    targetRow = row;
                    break;
                }
            }
        }

        if (targetRow == null) {
            System.out.println("Could not find row for item at index " + index);
            // The row isn't even rendered, so it's definitely not visible
            return false;
        }

        // Get row bounds in scene coordinates
        Bounds rowBounds = targetRow.localToScene(targetRow.getBoundsInLocal());

        System.out.println(String.format(
            "  Table bounds: minY=%.1f, maxY=%.1f",
            tableBounds.getMinY(), tableBounds.getMaxY()
        ));
        System.out.println(String.format(
            "  Row[%d] bounds: minY=%.1f, maxY=%.1f",
            index, rowBounds.getMinY(), rowBounds.getMaxY()
        ));

        // Check if row is fully within table bounds (accounting for header)
        // The header is typically about 25-30 pixels
        double headerHeight = 25;
        double tableContentMinY = tableBounds.getMinY() + headerHeight;
        double tableContentMaxY = tableBounds.getMaxY();

        boolean fullyVisible = rowBounds.getMinY() >= tableContentMinY &&
                               rowBounds.getMaxY() <= tableContentMaxY;

        boolean partiallyVisible = rowBounds.getMaxY() > tableContentMinY &&
                                   rowBounds.getMinY() < tableContentMaxY;

        System.out.println(String.format(
            "  Fully visible: %s, Partially visible: %s",
            fullyVisible, partiallyVisible
        ));

        // For this test, we'll consider it a pass only if fully visible
        return fullyVisible;
    }

    public static class Item {
        private final StringProperty id;
        private final StringProperty name;
        private final StringProperty description;

        public Item(String id, String name, String description) {
            this.id = new SimpleStringProperty(id);
            this.name = new SimpleStringProperty(name);
            this.description = new SimpleStringProperty(description);
        }

        public StringProperty idProperty() { return id; }
        public StringProperty nameProperty() { return name; }
        public StringProperty descriptionProperty() { return description; }
    }
}
