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
package me.champeau.a4j.jsolex.processing.event;

public interface ProcessingEventListener {

    default void onVideoMetadataAvailable(VideoMetadataEvent event) {

    }

    default void onImageGenerated(ImageGeneratedEvent event) {
    }

    default void onFileGenerated(FileGeneratedEvent event) {
    }

    default void onPartialReconstruction(PartialReconstructionEvent event) {
    }

    default void onOutputImageDimensionsDetermined(OutputImageDimensionsDeterminedEvent event) {
    }

    default void onNotification(NotificationEvent e) {
    }

    default void onSuggestion(SuggestionEvent e) {

    }

    default void onProcessingStart(ProcessingStartEvent e) {

    }

    default void onProcessingDone(ProcessingDoneEvent e) {

    }

    default void onEllipseFittingRequest(EllipseFittingRequestEvent e) {

    }

    default void onProgress(ProgressEvent e) {

    }

    default void onGenericMessage(GenericMessage<?> e) {

    }

    default void onScriptExecutionResult(ScriptExecutionResultEvent e) {

    }

    default void onAverageImageComputed(AverageImageComputedEvent e) {

    }

    default void onReconstructionDone(ReconstructionDoneEvent e) {

    }

    default void onTrimmingParametersDetermined(TrimmingParametersDeterminedEvent e) {

    }
}
