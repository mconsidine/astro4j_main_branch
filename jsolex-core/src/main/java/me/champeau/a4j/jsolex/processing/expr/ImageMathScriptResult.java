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
package me.champeau.a4j.jsolex.processing.expr;

import me.champeau.a4j.jsolex.processing.util.ImageWrapper;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record ImageMathScriptResult(
        Map<String, ImageWrapper> imagesByLabel,
        Map<String, Path> filesByLabel,
        List<InvalidExpression> invalidExpressions,
        Set<Double> internalShifts,
        Set<Double> outputShifts,
        Set<Double> requestedWavelenghts,
        boolean autoContinuum) {
    public static final ImageMathScriptResult EMPTY = new ImageMathScriptResult(
            Map.of(),
            Map.of(),
            List.of(),
            Set.of(),
            Set.of(),
            Set.of(),
            false
    );
}
