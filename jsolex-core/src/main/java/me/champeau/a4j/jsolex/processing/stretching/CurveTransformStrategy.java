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
package me.champeau.a4j.jsolex.processing.stretching;

import me.champeau.a4j.jsolex.processing.color.ColorCurve;
import me.champeau.a4j.jsolex.processing.util.Constants;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;

public final class CurveTransformStrategy implements StretchingStrategy {
    private final double in;
    private final double out;
    private final double protectLo;
    private final double protectHi;

    public CurveTransformStrategy(double in, double out) {
        this(in, out, 0, Constants.MAX_PIXEL_VALUE);
    }

    public CurveTransformStrategy(double in, double out, double protectLo, double protectHi) {
        this.in = in;
        this.out = out;
        this.protectLo = protectLo;
        this.protectHi = protectHi;
    }

    public double getIn() {
        return in;
    }

    public double getOut() {
        return out;
    }

    @Override
    public void stretch(ImageWrapper32 image) {
        var curve = ColorCurve.cachedPolynomial((int) in, (int) out);
        var data = image.data();
        int width = image.width();
        int height = image.height();
        for (int y = 0; y < height; y++) {
            var line = data[y];
            for (int x = 0; x < width; x++) {
                var v = line[x];
                if (v>protectLo && v < protectHi) {
                    line[x] = (float) Math.clamp(curve.applyAsDouble(v), 0, Constants.MAX_PIXEL_VALUE);
                }
            }
        }
    }
}
