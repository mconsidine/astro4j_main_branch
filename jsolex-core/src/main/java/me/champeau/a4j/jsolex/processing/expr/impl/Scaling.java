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

import me.champeau.a4j.jsolex.processing.util.ColorizedImageWrapper;
import me.champeau.a4j.jsolex.processing.util.ForkJoinContext;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.jsolex.processing.util.RGBImage;
import me.champeau.a4j.math.image.Image;
import me.champeau.a4j.math.image.ImageMath;

import java.util.List;
import java.util.Map;

import static me.champeau.a4j.jsolex.processing.expr.impl.ScriptSupport.expandToImageList;

public class Scaling extends AbstractFunctionImpl {
    private final ImageMath imageMath = ImageMath.newInstance();

    public Scaling(ForkJoinContext forkJoinContext, Map<Class<?>, Object> context) {
        super(forkJoinContext, context);
    }

    public Object relativeRescale(List<Object> arguments) {
        assertExpectedArgCount(arguments, "rescale_rel takes 3 arguments (image(s), scaleX, scaleY)", 3, 3);
        var arg = arguments.get(0);
        if (arg instanceof List<?>) {
            return expandToImageList(forkJoinContext, arguments, this::relativeRescale);
        }
        double scaleX = doubleArg(arguments, 1);
        double scaleY = doubleArg(arguments, 2);
        if (scaleX < 0 || scaleY < 0) {
            throw new IllegalArgumentException("scaleX and scaleY must be > 0");
        }
        if (arg instanceof ImageWrapper img) {
            var width = (int) Math.round(img.width() * scaleX);
            var height = (int) Math.round(img.height() * scaleY);
            var result = doRescale(img, width, height);
            if (result != null) {
                return result;
            }
        }
        throw new IllegalArgumentException("Unsupported image type");
    }

    public Object absoluteRescale(List<Object> arguments) {
        assertExpectedArgCount(arguments, "rescale_abs takes 3 arguments (image(s), width, height)", 3, 3);
        var arg = arguments.get(0);
        if (arg instanceof List<?>) {
            return expandToImageList(forkJoinContext, arguments, this::relativeRescale);
        }
        int width = intArg(arguments, 1);
        int height = intArg(arguments, 2);
        if (width < 0 || height < 0) {
            throw new IllegalArgumentException("Width and height must be > 0");
        }
        if (arg instanceof ImageWrapper img) {
            var result = doRescale(img, width, height);
            if (result != null) {
                return result;
            }
        }
        throw new IllegalArgumentException("Unsupported image type");
    }

    private ImageWrapper doRescale(ImageWrapper img, int width, int height) {
        if (img instanceof ImageWrapper32 mono) {
            return ImageWrapper32.fromImage(
                    imageMath.rescale(mono.asImage(),
                            width,
                            height
                    ));
        } else if (img instanceof ColorizedImageWrapper colorized) {
            var mono = colorized.mono();
            return new ColorizedImageWrapper(
                    ImageWrapper32.fromImage(
                            imageMath.rescale(mono.asImage(),
                                    width,
                                    height
                            )), colorized.converter()
            );
        } else if (img instanceof RGBImage rgb) {
            var r = new Image(rgb.width(), rgb.height(), rgb.r());
            var g = new Image(rgb.width(), rgb.height(), rgb.r());
            var b = new Image(rgb.width(), rgb.height(), rgb.r());
            return new RGBImage(
                    width,
                    height,
                    imageMath.rescale(r, width, height).data(),
                    imageMath.rescale(g, width, height).data(),
                    imageMath.rescale(b, width, height).data()
            );
        }
        return null;
    }
}