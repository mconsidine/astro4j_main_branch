/*
 * Copyright 2023-2023 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package me.champeau.a4j.jsolex.processing.sun;

import me.champeau.a4j.jsolex.processing.sun.workflow.WorkflowResults;
import me.champeau.a4j.jsolex.processing.util.FileBackedImage;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumMap;
import java.util.Optional;

public final class WorkflowState {
    private static final Logger LOGGER = LoggerFactory.getLogger(WorkflowState.class);

    private final double pixelShift;
    private final EnumMap<WorkflowResults, Object> outcomes = new EnumMap<>(WorkflowResults.class);
    private final int width;
    private final int height;
    private boolean internal;
    private TruncationDetails truncationDetails = new TruncationDetails();

    public WorkflowState(
            int width,
            int height,
            double pixelShift
    ) {
        this.width = width;
        this.height = height;
        this.pixelShift = pixelShift;
    }

    public void setTruncationDetails(TruncationDetails details) {
        this.truncationDetails = details;
    }

    public TruncationDetails getTruncationDetails() {
        return truncationDetails;
    }

    public boolean isInternal() {
        return internal;
    }

    public void setInternal(boolean internal) {
        this.internal = internal;
    }

    public static WorkflowState prepare(int width, int height, double pixelShift) {
        return new WorkflowState(width, height, pixelShift);
    }

    public void recordResult(WorkflowResults step, Object result) {
        if (result instanceof ImageWrapper img) {
            result = FileBackedImage.wrap(img);
        }
        outcomes.put(step, result);
    }

    public void discardResult(WorkflowResults step) {
        outcomes.remove(step);
    }

    public <T> Optional<T> findResult(WorkflowResults step) {
        //noinspection unchecked
        var obj = outcomes.get(step);
        if (obj instanceof FileBackedImage img) {
            obj = img.unwrapToMemory();
        }
        return Optional.ofNullable((T) obj);
    }

    public int width() {
        return image().width();
    }

    public int height() {
        return image().height();
    }

    public double pixelShift() {
        return pixelShift;
    }

    public void setImage(ImageWrapper32 image) {
        recordResult(WorkflowResults.ROTATED, image.wrap());
        discardResult(WorkflowResults.RECONSTRUCTED);
    }

    public ImageWrapper32 image() {
        return findResult(WorkflowResults.ROTATED).map(ImageWrapper32.class::cast).orElse(null);
    }

    public ImageWrapper32 reconstructed() {
        return this.<ImageWrapper32>findResult(WorkflowResults.RECONSTRUCTED).orElseThrow();
    }
}
