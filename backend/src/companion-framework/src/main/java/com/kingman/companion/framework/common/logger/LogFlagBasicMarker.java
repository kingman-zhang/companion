package com.kingman.companion.framework.common.logger;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.slf4j.Marker;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Vector;

/**
 * @author kingman
 */
@Getter
@AllArgsConstructor
@SuppressWarnings("ALL")
public class LogFlagBasicMarker implements Marker {

    private final LogFlag logFlag;

    private List<Marker> references;

    LogFlagBasicMarker(LogFlag logFlag) {
        if (logFlag == null) {
            throw new IllegalArgumentException("A marker name cannot be null");
        }
        this.logFlag = logFlag;
    }

    @Override
    public String getName() {
        return logFlag.getName();
    }

    @Override
    public synchronized void add(Marker reference) {
        if (reference == null) {
            throw new IllegalArgumentException(
                    "A null value cannot be added to a Marker as reference.");
        }

        // no point in adding the reference multiple times
        if (this.contains(reference)) {
            //noinspection UnnecessaryReturnStatement
            return;

        }
        // avoid recursion
        else if (reference.contains(this)) {
            // a potential reference should not its future "parent" as a reference
            //noinspection UnnecessaryReturnStatement
            return;
        } else {
            // let's add the reference
            if (references == null) {
                references = new Vector<>();
            }
            references.add(reference);
        }

    }

    @Override
    public synchronized boolean hasReferences() {
        return ((references != null) && (references.size() > 0));
    }

    @Override
    public boolean hasChildren() {
        return hasReferences();
    }

    @Override
    public synchronized Iterator<Marker> iterator() {
        if (references != null) {
            return references.iterator();
        } else {
            return Collections.emptyIterator();
        }
    }

    @Override
    public synchronized boolean remove(Marker referenceToRemove) {
        if (references == null) {
            return false;
        }

        int size = references.size();
        for (int i = 0; i < size; i++) {
            Marker m = references.get(i);
            if (referenceToRemove.equals(m)) {
                references.remove(i);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean contains(Marker other) {
        if (other == null) {
            throw new IllegalArgumentException("Other cannot be null");
        }

        if (this.equals(other)) {
            return true;
        }

        if (hasReferences()) {
            for (Marker marker : references) {
                if (marker.contains(other)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean contains(String name) {
        if (name == null) {
            throw new IllegalArgumentException("Other cannot be null");
        }

        if (this.logFlag.getName().equals(name)) {
            return true;
        }

        if (hasReferences()) {
            for (Marker reference : references) {
                if (reference.contains(name)) {
                    return true;
                }
            }
        }
        return false;
    }
}
