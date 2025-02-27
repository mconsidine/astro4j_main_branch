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
package me.champeau.a4j.jsolex.app.listeners;

import me.champeau.a4j.jsolex.app.jfx.BatchItem;
import me.champeau.a4j.jsolex.app.jfx.CandidateImageDescriptor;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper;
import me.champeau.a4j.ser.Header;

import java.io.File;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public record BatchProcessingContext(
    List<BatchItem> items,
    Set<Integer> progress,
    Set<Integer> errors,
    AtomicBoolean batchFinished,
    File outputDirectory,
    LocalDateTime processingDate,
    Map<String, List<ImageWrapper>> imagesByLabel,
    Map<Integer, List<ImageWrapper>> imageWrappersByIndex,
    Map<Integer, List<CandidateImageDescriptor>> imagesByIndex,
    Map<Integer, List<File>> filesByIndex,
    Map<Integer, File> serFilesByIndex,
    Header referenceHeader
) {
}
