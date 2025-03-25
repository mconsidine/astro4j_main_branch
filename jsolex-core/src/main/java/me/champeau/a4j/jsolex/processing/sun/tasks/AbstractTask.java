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
package me.champeau.a4j.jsolex.processing.sun.tasks;

import me.champeau.a4j.jsolex.processing.event.ProgressOperation;
import me.champeau.a4j.jsolex.processing.sun.Broadcaster;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.jsolex.processing.util.ProcessingException;

import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

public abstract class AbstractTask<T> implements Callable<T>, Supplier<T> {
    protected final ProgressOperation operation;
    protected final Supplier<ImageWrapper32> workImageSupplier;
    protected final Broadcaster broadcaster;
    protected ImageWrapper32 workImage;
    protected int width;
    protected int height;

    /**
     * Creates an abstract task
     *
     * @param imageSupplier the current image. A copy will be created in the
     * constructor, so that this task works with its own buffer
     */
    protected AbstractTask(Broadcaster broadcaster,
                           ProgressOperation operation,
                           Supplier<ImageWrapper32> imageSupplier) {
        this.broadcaster = broadcaster;
        this.operation = operation;
        this.workImageSupplier = imageSupplier;
    }

    @Override
    public final T call() throws Exception {
        prepareImage();
        return doCall();
    }

    protected void prepareImage() {
        var image = workImageSupplier.get();
        this.workImage = image.copy();
        this.width = image.width();
        this.height = image.height();
    }

    protected abstract T doCall() throws Exception ;

    @Override
    public final T get() {
        try {
            return call();
        } catch (Exception e) {
            throw ProcessingException.wrap(e);
        }
    }

    /**
     * Returns this task image buffer
     */
    public final float[][] getBuffer() {
        return workImage.data();
    }

    public Map<Class<?>, Object> getMetadata() {
        return workImage.metadata();
    }

}
