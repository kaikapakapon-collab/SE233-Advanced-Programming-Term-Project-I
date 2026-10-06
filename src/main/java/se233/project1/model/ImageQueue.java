package se233.project1.model;

import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.binding.ObjectBinding;
import javafx.beans.binding.StringBinding;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.Collection;

/**
 * Ordered list of dropped images plus a "current image" cursor for the Back / Next buttons.
 * FX-thread confined, like {@link ImageItem}.
 */
public final class ImageQueue {

    private final ObservableList<ImageItem> items = FXCollections.observableArrayList();
    private final ObservableList<ImageItem> readOnlyItems = FXCollections.unmodifiableObservableList(items);
    private final IntegerProperty currentIndex = new SimpleIntegerProperty(-1);

    private final ObjectBinding<ImageItem> current;
    private final BooleanBinding hasNext;
    private final BooleanBinding hasPrevious;
    private final BooleanBinding empty;
    private final StringBinding positionText;

    public ImageQueue() {
        current = Bindings.createObjectBinding(() -> {
            int i = currentIndex.get();
            return i >= 0 && i < items.size() ? items.get(i) : null;
        }, currentIndex, items);
        hasNext = Bindings.createBooleanBinding(() -> currentIndex.get() < items.size() - 1,
                currentIndex, items);
        hasPrevious = Bindings.createBooleanBinding(() -> currentIndex.get() > 0, currentIndex, items);
        empty = Bindings.createBooleanBinding(items::isEmpty, items);
        positionText = Bindings.createStringBinding(
                () -> items.isEmpty() ? "0 / 0" : (currentIndex.get() + 1) + " / " + items.size(),
                currentIndex, items);
    }

    /** Replaces the whole queue and selects the first image. */
    public void setAll(Collection<ImageItem> newItems) {
        items.setAll(newItems);
        currentIndex.set(items.isEmpty() ? -1 : 0);
    }

    /** Appends images; selects the first one if the queue was empty. */
    public void addAll(Collection<ImageItem> newItems) {
        boolean wasEmpty = items.isEmpty();
        items.addAll(newItems);
        if (wasEmpty && !items.isEmpty()) {
            currentIndex.set(0);
        }
    }

    public void clear() {
        items.clear();
        currentIndex.set(-1);
    }

    public void next() {
        if (hasNext.get()) {
            currentIndex.set(currentIndex.get() + 1);
        }
    }

    public void previous() {
        if (hasPrevious.get()) {
            currentIndex.set(currentIndex.get() - 1);
        }
    }

    public void select(int index) {
        if (index < 0 || index >= items.size()) {
            throw new IndexOutOfBoundsException("index " + index + ", size " + items.size());
        }
        currentIndex.set(index);
    }

    public ImageItem get(int index) { return items.get(index); }
    public int size() { return items.size(); }
    public boolean isEmpty() { return items.isEmpty(); }
    public ObservableList<ImageItem> items() { return readOnlyItems; }

    public ReadOnlyIntegerProperty currentIndexProperty() { return currentIndex; }
    public ObjectBinding<ImageItem> currentProperty() { return current; }
    public ImageItem getCurrent() { return current.get(); }
    public BooleanBinding hasNextProperty() { return hasNext; }
    public BooleanBinding hasPreviousProperty() { return hasPrevious; }
    public BooleanBinding emptyProperty() { return empty; }
    /** Text such as "2 / 7" for the navigation label. */
    public StringBinding positionTextProperty() { return positionText; }
}
