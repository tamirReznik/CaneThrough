package org.tensorflow.lite.examples.detection.caneThroughManager;

import androidx.annotation.Nullable;

import java.util.concurrent.atomic.AtomicReference;

public class MyAtomicRef extends AtomicReference<MyDetectedObject> {
    public MyAtomicRef() {
    }

    public MyAtomicRef(MyDetectedObject initialValue) {
        super(initialValue);
    }

    @Override
    public int hashCode() {
        return this.get().hashCode();
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        assert obj != null;
        if (getClass() != obj.getClass())
            return false;
        return this.get().equals(((MyAtomicRef) obj).get());
    }
}
