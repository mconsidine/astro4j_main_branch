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
package me.champeau.a4j.jsolex.processing.expr.impl;

import me.champeau.a4j.jsolex.processing.event.ProgressEvent;
import me.champeau.a4j.jsolex.processing.event.ProgressOperation;
import me.champeau.a4j.jsolex.processing.sun.Broadcaster;
import me.champeau.a4j.jsolex.processing.util.FileBackedImage;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.jsolex.processing.util.RGBImage;
import me.champeau.a4j.math.regression.Ellipse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.DoubleBinaryOperator;
import java.util.function.DoubleUnaryOperator;
import java.util.function.Function;
import java.util.stream.IntStream;

class AbstractFunctionImpl {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractFunctionImpl.class);

    protected final Map<Class<?>, Object> context;
    protected final Broadcaster broadcaster;
    private final ProgressOperation operation;

    protected AbstractFunctionImpl(Map<Class<?>, Object> context, Broadcaster broadcaster) {
        this.context = context;
        this.broadcaster = broadcaster;
        this.operation = newProgress("");
    }

    private ProgressOperation newProgress(String name) {
        var parent = (ProgressOperation) context.get(ProgressOperation.class);
        if (parent == null) {
            return ProgressOperation.root(name, e -> {});
        }
        return parent.createChild(name);
    }

    protected ProgressOperation newOperation() {
        return operation.createChild(operation.task());
    }

    protected Optional<Ellipse> getEllipse(List<Object> arguments, int index) {
        if (index >= 0) {
            return getArgument(Ellipse.class, arguments, index)
                .or(() -> findEllipseInArguments(arguments))
                .or(() -> getFromContext(Ellipse.class));
        }
        return findEllipseInArguments(arguments)
            .or(() -> getFromContext(Ellipse.class));
    }

    private static Optional<Ellipse> findEllipseInArguments(List<Object> arguments) {
        if (arguments.isEmpty()) {
            return Optional.empty();
        }
        var first = arguments.getFirst();
        if (first instanceof ImageWrapper img) {
            return img.findMetadata(Ellipse.class);
        }
        return Optional.empty();
    }

    protected <T> Optional<T> getFromContext(Class<T> type) {
        return (Optional<T>) Optional.ofNullable(context.get(type));
    }

    protected void assertExpectedArgCount(List<Object> arguments, String help, int min, int max) {
        var size = arguments.size();
        if (size < min || size > max) {
            throw new IllegalArgumentException(help);
        }
    }

    protected <T> Optional<T> getArgument(Class<T> clazz, List<Object> args, int position) {
        if (position < args.size()) {
            return Optional.of((T) args.get(position));
        }
        return Optional.empty();
    }

    protected double doubleArg(List<Object> arguments, int position) {
        return getAsNumber(arguments, position).doubleValue();
    }

    protected float floatArg(List<Object> arguments, int position) {
        return getAsNumber(arguments, position).floatValue();
    }

    protected int intArg(List<Object> arguments, int position) {
        return getAsNumber(arguments, position).intValue();
    }

    protected String stringArg(List<Object> arguments, int position) {
        return getArgument(String.class, arguments, position).orElseThrow();
    }

    protected Number getAsNumber(List<Object> arguments, int position) {
        if (position < arguments.size()) {
            var obj = arguments.get(position);
            if (obj instanceof Number num) {
                return num;
            } else if (obj instanceof CharSequence str) {
                return Double.parseDouble(str.toString());
            }
            throw new IllegalStateException("Expected to find a number argument at position " + position + " but it as a " + obj.getClass());
        }
        throw new IllegalStateException("Not enough arguments to select a number at position " + position);
    }

    @SuppressWarnings("unchecked")
    public List<Object> expandToImageList(String currentFunction, List<Object> arguments, Function<List<Object>, Object> function) {
        record IndexedObject(Object image, int idx) {
        }
        var listOfImages = (List) arguments.getFirst();
        var params = arguments.subList(1, arguments.size());
        var array = listOfImages.toArray(new Object[0]);
        var itemsToProcess = IntStream.range(0, array.length)
            .mapToObj(i -> new IndexedObject(array[i], i))
            .toList();
        var progress = new AtomicInteger(0);
        var parallelOperation = operation.createChild("ImageMath: " + currentFunction);
        var processed = itemsToProcess.stream()
            .parallel()
            .map(o -> {
                var idx = o.idx;
                var image = o.image;
                var allArgs = new ArrayList<>();
                allArgs.add(image);
                allArgs.addAll(params);
                var p = progress.incrementAndGet();
                broadcaster.broadcast(parallelOperation.update(p / (double) array.length));
                var result = function.apply(allArgs);
                if (result instanceof ImageWrapper img) {
                    // save memory!
                    result = FileBackedImage.wrap(img);
                }
                return new IndexedObject(result, idx);
            }).toList();
        // iterate on keys to preserve order
        broadcaster.broadcast(parallelOperation.complete());
        return processed.stream().sorted(Comparator.comparingInt(IndexedObject::idx)).map(IndexedObject::image).toList();
    }

    private static void applyFunction(int width, int height, float[][] data, DoubleUnaryOperator function) {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                data[y][x] = (float) function.applyAsDouble(data[y][x]);
            }
        }
    }

    public Object monoToMonoImageTransformer(String name, int maxArgCount, List<Object> arguments, ImageConsumer consumer) {
        if (arguments.size() > maxArgCount) {
            throw new IllegalArgumentException("Invalid number of arguments on '" + name + "' call");
        }
        var arg = arguments.getFirst();
        if (arg instanceof List<?>) {
            return expandToImageList(name, arguments, e -> monoToMonoImageTransformer(name, maxArgCount, e, consumer));
        } else if (arg instanceof ImageWrapper image) {
            var copy = image.unwrapToMemory().copy();
            consumer.accept(copy);
            return copy;
        }
        throw new IllegalArgumentException(name + " first argument must be a mono image or a list of images");
    }

    protected Object applyUnary(List<Object> arguments, String name, DoubleUnaryOperator function) {
        if (arguments.size() != 1) {
            throw new IllegalArgumentException(name + " takes 1 argument (image(s))");
        }
        var arg = arguments.getFirst();
        if (arg instanceof List<?>) {
            return expandToImageList(name, arguments, list -> applyUnary(list, name, function));
        }
        var img = arguments.getFirst();
        return applyUnary(img, function);
    }

    protected Object applyUnary(List<Object> arguments, String name, MonoImageTransformer transformer) {
        if (arguments.size() != 1) {
            throw new IllegalArgumentException(name + " takes 1 argument (image(s))");
        }
        var arg = arguments.getFirst();
        if (arg instanceof List<?>) {
            return expandToImageList(name, arguments, list -> applyUnary(list, name, transformer));
        }
        var img = arguments.getFirst();
        return applyUnary(img, transformer);
    }

    protected Object applyBinary(List<Object> arguments, String name, String argName, DoubleBinaryOperator function) {
        if (arguments.size() != 2) {
            throw new IllegalArgumentException(name + " takes 2 arguments (image(s), " + argName + ")");
        }
        var arg = arguments.getFirst();
        if (arg instanceof List<?>) {
            return expandToImageList(name, arguments, list -> applyBinary(list, name, argName, function));
        }
        var img = arguments.getFirst();
        var argument = doubleArg(arguments, 1);
        var unary = (DoubleUnaryOperator) v -> function.applyAsDouble(v, argument);
        return applyUnary(img, unary);
    }

    private Object applyUnary(Object img, DoubleUnaryOperator unary) {
        if (img instanceof FileBackedImage fileBackedImage) {
            img = fileBackedImage.unwrapToMemory();
        }
        if (img instanceof ImageWrapper32 mono) {
            var copy = mono.copy();
            applyFunction(mono.width(), mono.height(), copy.data(), unary);
            return copy;
        } else if (img instanceof RGBImage rgb) {
            var copy = rgb.copy();
            applyFunction(rgb.width(), rgb.height(), copy.r(), unary);
            applyFunction(rgb.width(), rgb.height(), copy.g(), unary);
            applyFunction(rgb.width(), rgb.height(), copy.b(), unary);
            return copy;
        } else if (img instanceof Number number) {
            return unary.applyAsDouble(number.doubleValue());
        }
        throw new IllegalStateException("Unexpected image type " + img);
    }

    private Object applyUnary(Object img, MonoImageTransformer transformer) {
        if (img instanceof FileBackedImage fileBackedImage) {
            img = fileBackedImage.unwrapToMemory();
        }
        if (img instanceof ImageWrapper32 mono) {
            var copy = mono.copy();
            transformer.transform(mono.width(), mono.height(), copy.data());
            return copy;
        } else if (img instanceof RGBImage rgb) {
            var copy = rgb.copy();
            transformer.transform(rgb.width(), rgb.height(), copy.r());
            transformer.transform(rgb.width(), rgb.height(), copy.g());
            transformer.transform(rgb.width(), rgb.height(), copy.b());
            return copy;
        }
        throw new IllegalStateException("Unexpected image type " + img);
    }

    @FunctionalInterface
    public interface ImageConsumer {
        void accept(ImageWrapper image);
    }

    @FunctionalInterface
    public interface MonoImageTransformer {
        void transform(int width, int height, float[][] data);
    }
}
