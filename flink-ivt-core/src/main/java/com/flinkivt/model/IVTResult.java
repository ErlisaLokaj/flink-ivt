package com.flinkivt.model;

import java.util.List;

/**
 * Result of a single IVT detection pass over a 2000ms window.
 * Contains all detected fixations and saccades for that window.
 */
public class IVTResult {

    public final List<FixationEvent> fixations;
    public final List<SaccadeEvent> saccades;

    public IVTResult(List<FixationEvent> fixations, List<SaccadeEvent> saccades) {
        this.fixations = fixations;
        this.saccades  = saccades;
    }
}