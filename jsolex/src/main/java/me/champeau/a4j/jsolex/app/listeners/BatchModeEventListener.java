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

import javafx.application.Platform;
import javafx.scene.control.ButtonType;
import me.champeau.a4j.jsolex.app.AlertFactory;
import me.champeau.a4j.jsolex.app.jfx.BatchItem;
import me.champeau.a4j.jsolex.app.jfx.BatchOperations;
import me.champeau.a4j.jsolex.app.jfx.CandidateImageDescriptor;
import me.champeau.a4j.jsolex.app.jfx.Corrector;
import me.champeau.a4j.jsolex.app.jfx.ImageInspectorController;
import me.champeau.a4j.jsolex.app.script.JSolExScriptExecutor;
import me.champeau.a4j.jsolex.processing.event.AverageImageComputedEvent;
import me.champeau.a4j.jsolex.processing.event.FileGeneratedEvent;
import me.champeau.a4j.jsolex.processing.event.GeneratedImage;
import me.champeau.a4j.jsolex.processing.event.ImageGeneratedEvent;
import me.champeau.a4j.jsolex.processing.event.Notification;
import me.champeau.a4j.jsolex.processing.event.NotificationEvent;
import me.champeau.a4j.jsolex.processing.event.OutputImageDimensionsDeterminedEvent;
import me.champeau.a4j.jsolex.processing.event.PartialReconstructionEvent;
import me.champeau.a4j.jsolex.processing.event.ProcessingDoneEvent;
import me.champeau.a4j.jsolex.processing.event.ProcessingEventListener;
import me.champeau.a4j.jsolex.processing.event.ProcessingStartEvent;
import me.champeau.a4j.jsolex.processing.event.ScriptExecutionResultEvent;
import me.champeau.a4j.jsolex.processing.event.TrimmingParametersDeterminedEvent;
import me.champeau.a4j.jsolex.processing.event.VideoMetadataEvent;
import me.champeau.a4j.jsolex.processing.expr.BestImages;
import me.champeau.a4j.jsolex.processing.expr.DefaultImageScriptExecutor;
import me.champeau.a4j.jsolex.processing.expr.ImageMathScriptExecutor;
import me.champeau.a4j.jsolex.processing.expr.ImageMathScriptResult;
import me.champeau.a4j.jsolex.processing.file.FileNamingStrategy;
import me.champeau.a4j.jsolex.processing.params.AutocropMode;
import me.champeau.a4j.jsolex.processing.params.ProcessParams;
import me.champeau.a4j.jsolex.processing.params.RotationKind;
import me.champeau.a4j.jsolex.processing.stretching.RangeExpansionStrategy;
import me.champeau.a4j.jsolex.processing.sun.detection.RedshiftArea;
import me.champeau.a4j.jsolex.processing.sun.workflow.DefaultImageEmitter;
import me.champeau.a4j.jsolex.processing.sun.workflow.GeneratedImageKind;
import me.champeau.a4j.jsolex.processing.sun.workflow.ImageEmitter;
import me.champeau.a4j.jsolex.processing.sun.workflow.NamingStrategyAwareImageEmitter;
import me.champeau.a4j.jsolex.processing.sun.workflow.PixelShift;
import me.champeau.a4j.jsolex.processing.sun.workflow.RenamingImageEmitter;
import me.champeau.a4j.jsolex.processing.sun.workflow.SourceInfo;
import me.champeau.a4j.jsolex.processing.util.Constants;
import me.champeau.a4j.jsolex.processing.util.ImageSaver;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper;
import me.champeau.a4j.jsolex.processing.util.ProcessingException;
import me.champeau.a4j.jsolex.processing.util.SolarParametersUtils;
import me.champeau.a4j.ser.Header;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static me.champeau.a4j.jsolex.app.JSolEx.message;
import static me.champeau.a4j.jsolex.processing.sun.CaptureSoftwareMetadataHelper.computeSerFileBasename;
import static me.champeau.a4j.jsolex.processing.util.LoggingSupport.LOGGER;

public class BatchModeEventListener implements ProcessingEventListener, ImageMathScriptExecutor {

    private final JSolExInterface owner;
    private final SingleModeProcessingEventListener delegate;
    private final ProcessParams processParams;
    private final BatchItem item;
    private final Set<Integer> completed;
    private final AtomicBoolean batchFinished;
    private final Set<Integer> errors;
    private final double totalItems;
    private final File outputDirectory;
    private final LocalDateTime processingDate;
    private final AtomicBoolean hasCustomImages = new AtomicBoolean();

    private final Header referenceHeader;

    private final int sequenceNumber;
    private DefaultImageScriptExecutor batchScriptExecutor;

    private Header header;
    private final List<BatchItem> allItems;
    private final Map<String, List<ImageWrapper>> imagesByLabel;
    private final Map<Integer, List<ImageWrapper>> imageWrappersByIndex;
    private final Map<Integer, List<CandidateImageDescriptor>> imagesByIndex;
    private final Map<Integer, List<File>> filesByIndex;
    private final Map<Integer, File> serFilesByIndex;
    private ProcessParams adjustedParams;

    public BatchModeEventListener(JSolExInterface owner,
                                  SingleModeProcessingEventListener delegate,
                                  int sequenceNumber,
                                  BatchProcessingContext context,
                                  ProcessParams processParams) {
        this.owner = owner;
        this.delegate = delegate;
        this.processParams = processParams;
        this.completed = context.progress();
        this.errors = context.errors();
        this.batchFinished = context.batchFinished();
        this.outputDirectory = context.outputDirectory();
        this.processingDate = context.processingDate();
        this.imagesByLabel = context.imagesByLabel();
        this.imageWrappersByIndex = context.imageWrappersByIndex();
        this.imagesByIndex = context.imagesByIndex();
        this.filesByIndex = context.filesByIndex();
        this.serFilesByIndex = context.serFilesByIndex();
        this.referenceHeader = context.referenceHeader();
        this.item = context.items().stream().filter(batchItem -> batchItem.id() == sequenceNumber).findFirst().get();
        this.totalItems = context.items().size();
        this.allItems = context.items();
        this.sequenceNumber = sequenceNumber;
    }

    @Override
    public void onAverageImageComputed(AverageImageComputedEvent e) {
        this.adjustedParams = e.getPayload().adjustedParams();
    }

    @Override
    public void onImageGenerated(ImageGeneratedEvent event) {
        var payload = event.getPayload();
        var image = payload.image();
        var kind = payload.kind();
        var target = payload.path().toFile();
        var params = adjustedParams != null ? adjustedParams : processParams;
        var img = image;
        double correction = 0;
        if (!kind.cannotPerformManualRotation()) {
            correction = image.findMetadata(RotationKind.class).orElseGet(() -> params.geometryParams().rotation()).angle();
            if (params.geometryParams().isAutocorrectAngleP()) {
                correction += SolarParametersUtils.computeSolarParams(params.observationDetails().date().toLocalDateTime()).p();
            }
        }
        if (correction != 0) {
            img = Corrector.rotate(img, correction, params.geometryParams().autocropMode() == AutocropMode.OFF);
        }
        var saved = new ImageSaver(RangeExpansionStrategy.DEFAULT, params).save(img, target);
        for (var file : saved) {
            item.generatedFiles().add(file);
            filesByIndex.computeIfAbsent(sequenceNumber, unused -> new ArrayList<>()).add(file);
        }
        imagesByIndex.computeIfAbsent(sequenceNumber, unused -> new ArrayList<>()).add(new CandidateImageDescriptor(
            payload.kind(),
            payload.title(),
            payload.path(),
            payload.image().findMetadata(PixelShift.class).map(PixelShift::pixelShift).orElse(0d)
        ));
    }

    @Override
    public void onFileGenerated(FileGeneratedEvent event) {
        item.generatedFiles().add(event.getPayload().path().toFile());
    }

    @Override
    public void onPartialReconstruction(PartialReconstructionEvent event) {
        var payload = event.getPayload();
        item.reconstructionProgress().setValue(payload.line() / (double) payload.totalLines());
    }

    @Override
    public void onTrimmingParametersDetermined(TrimmingParametersDeterminedEvent e) {
        owner.setTrimmingParameters(e.getPayload());
    }

    @Override
    public void onOutputImageDimensionsDetermined(OutputImageDimensionsDeterminedEvent event) {
        LOGGER.info(message("dimensions.determined"), event.getLabel(), event.getWidth(), event.getHeight());
        item.reconstructionProgress().setValue(1.0);
    }

    @Override
    public void onVideoMetadataAvailable(VideoMetadataEvent event) {
        var payload = event.getPayload();
        if (payload != null) {
            this.header = payload;
        }
    }

    @Override
    public void onProcessingStart(ProcessingStartEvent e) {
        item.status().set(message("batch.started"));
        updateProgressStatus(false);
        serFilesByIndex.put(sequenceNumber, item.file());
    }

    @Override
    public void onProcessingDone(ProcessingDoneEvent e) {
        updateProgressStatus(true);
        maybeWriteLogs();
        item.detectedActiveRegions().set(e.getPayload().detectedActiveRegions());
        item.maxRedshiftKmPerSec().set(e.getPayload().redshifts().stream().map(RedshiftArea::kmPerSec).max(Double::compareTo).orElse(0.0));
        if (item.status().get().equals(message("batch.error"))) {
            return;
        }
        item.status().set(message("batch.ok"));
        maybeExecuteEndOfBatch();
    }

    private void maybeFilterImages(Consumer<? super FilteringResult> onClose) {
        if (processParams.extraParams().reviewImagesAfterBatch()) {
            Platform.runLater(() -> ImageInspectorController.create(processParams, imagesByIndex, filesByIndex, serFilesByIndex, outputDirectory, controller -> {
                var deletedFiles = controller.getDeletedFiles();
                var movedFiles = controller.getMovedFiles();
                adjustDeletedAndMovedFilesList(deletedFiles, movedFiles);
                Thread.startVirtualThread(() -> onClose.accept(new FilteringResult(controller.getDiscardedImages(), controller.getBestImage().orElse(null))));
            }));
        } else {
            Thread.startVirtualThread(() -> onClose.accept(new FilteringResult(List.of(), null)));
        }
    }

    /**
     * After the user has filtered images, we need to adjust the list of generated files because the links
     * in the UI are based on the original list of files.
     *
     * @param deletedFiles the list of deleted files
     * @param movedFiles the list of moved files
     */
    private void adjustDeletedAndMovedFilesList(Set<File> deletedFiles, Map<File, File> movedFiles) {
        for (var curItem : allItems) {
            curItem.generatedFiles().removeIf(deletedFiles::contains);
        }
        var newFileList = new ArrayList<File>();
        for (var curItem : allItems) {
            for (var file : curItem.generatedFiles()) {
                newFileList.add(movedFiles.getOrDefault(file, file));
            }
        }
        item.generatedFiles().setAll(newFileList);
    }

    private void maybeExecuteEndOfBatch() {
        if (completed.size() == totalItems && batchFinished.compareAndSet(false, true)) {
            var success = completed.size() - errors.size();
            if (success > 0 && !errors.isEmpty() && hasBatchScriptExpressions()) {
                Platform.runLater(() -> {
                    var alert = AlertFactory.warning(message("incomplete.batch.message"));
                    alert.setTitle(message("incomplete.batch"));
                    alert.getButtonTypes().clear();
                    alert.getButtonTypes().addAll(ButtonType.CANCEL, ButtonType.YES);
                    alert.showAndWait().ifPresent(response -> {
                        if (response == ButtonType.YES) {
                            maybeFilterImages(this::executeBatchScriptExpressions);
                        } else {
                            Platform.runLater(() -> owner.updateProgress(1, String.format(message("batch.finished"))));
                        }
                    });
                });
            } else if (!errors.isEmpty()) {
                Platform.runLater(() -> {
                    var alert = AlertFactory.warning(message("incomplete.batch.error"));
                    alert.setTitle(message("incomplete.batch"));
                    alert.showAndWait();
                    Platform.runLater(() -> owner.updateProgress(1, String.format(message("batch.finished"))));
                });
            } else {
                maybeFilterImages(this::executeBatchScriptExpressions);
            }
        }
    }

    private boolean hasBatchScriptExpressions() {
        var scriptFiles = processParams.requestedImages().mathImages().scriptFiles();
        if (scriptFiles.isEmpty()) {
            return false;
        }
        return scriptFiles.stream().anyMatch(file -> {
            try {
                return Files.readString(file.toPath()).contains("[[batch]]");
            } catch (IOException e) {
                return false;
            }
        });
    }

    private void executeBatchScriptExpressions(FilteringResult result) {
        try {
            var scriptFiles = processParams.requestedImages().mathImages().scriptFiles();
            if (scriptFiles.isEmpty() || result.discarded().size() == totalItems) {
                return;
            }
            var imageEmitter = new NamingStrategyAwareImageEmitter(new RenamingImageEmitter(new DefaultImageEmitter(delegate, outputDirectory), name -> name, name -> name), createNamingStrategy(), sequenceNumber, computeSerFileBasename(item.file()));
            var ctx = new HashMap<Class, Object>();
            ctx.put(ImageEmitter.class, imageEmitter);
            batchScriptExecutor = new JSolExScriptExecutor(
                idx -> {
                    throw new IllegalStateException("Cannot call img() in batch outputs. Use variables to store images instead");
                },
                ctx,
                delegate,
                null
            );
            var discarded = new HashSet<ImageWrapper>();
            for (var index : result.discarded()) {
                discarded.addAll(imageWrappersByIndex.get(index));
            }
            for (Map.Entry<String, List<ImageWrapper>> entry : imagesByLabel.entrySet()) {
                var images = entry.getValue().stream().filter(img -> !discarded.contains(img)).toList();
                batchScriptExecutor.putVariable(entry.getKey(), images);
            }
            if (result.best != null) {
                var bestSource = imageWrappersByIndex.get(result.best)
                    .stream()
                    .findFirst()
                    .flatMap(i -> i.findMetadata(SourceInfo.class))
                    .orElse(null);
                batchScriptExecutor.putInContext(BestImages.class, new BestImages(bestSource));
            }
            var namingStrategy = createNamingStrategy();
            boolean initial = true;
            for (File scriptFile : scriptFiles) {
                if (initial) {
                    owner.prepareForScriptExecution(this, processParams);
                    initial = false;
                }
                executeBatchScript(namingStrategy, scriptFile);
            }
        } finally {
            Platform.runLater(() -> owner.updateProgress(1, String.format(message("batch.finished"))));
            if (hasCustomImages.get()) {
                owner.showImages();
            }
        }
    }


    private void executeBatchScript(FileNamingStrategy namingStrategy, File scriptFile) {
        Platform.runLater(() -> owner.updateProgress(0, String.format(message("executing.script"), scriptFile)));
        ImageMathScriptResult result;
        var progressThread = new Thread() {
            private final AtomicInteger step = new AtomicInteger();

            @Override
            public void run() {
                while (!Thread.currentThread().isInterrupted()) {
                    var pg = Math.abs(Math.sin(Math.PI * step.incrementAndGet() / 50));
                    Platform.runLater(() -> owner.updateProgress(pg, String.format(message("executing.script"), scriptFile)));
                    try {
                        Thread.sleep(25);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        };
        progressThread.start();
        try {
            result = batchScriptExecutor.execute(scriptFile.toPath(), ImageMathScriptExecutor.SectionKind.BATCH);
        } catch (IOException e) {
            throw new ProcessingException(e);
        }
        try {
            processScriptErrors(result);
            renderBatchOutputs(namingStrategy, result);
        } finally {
            progressThread.interrupt();
            Platform.runLater(() -> owner.updateProgress(1, String.format(message("executing.script"), scriptFile)));
        }
    }

    private void renderBatchOutputs(FileNamingStrategy namingStrategy, ImageMathScriptResult result) {
        result.imagesByLabel().entrySet().stream().parallel().forEach(entry -> {
            var name = namingStrategy.render(0, null, Constants.TYPE_PROCESSED, entry.getKey(), "batch");
            var outputFile = new File(outputDirectory, name);
            delegate.onImageGenerated(new ImageGeneratedEvent(
                new GeneratedImage(GeneratedImageKind.IMAGE_MATH, entry.getKey(), outputFile.toPath(), entry.getValue())
            ));
            hasCustomImages.set(true);
        });
        result.filesByLabel().entrySet().stream().parallel().forEach(entry -> {
            var name = namingStrategy.render(0, null, Constants.TYPE_PROCESSED, entry.getKey(), "batch");
            try {
                var fileName = entry.getValue().toFile().getName();
                var ext = fileName.substring(fileName.lastIndexOf("."));
                var targetPath = new File(outputDirectory, name + ext).toPath();
                Files.createDirectories(targetPath.getParent());
                Files.move(entry.getValue(), targetPath, StandardCopyOption.REPLACE_EXISTING);
                delegate.onFileGenerated(FileGeneratedEvent.of(GeneratedImageKind.IMAGE_MATH, entry.getKey(), targetPath));
            } catch (IOException e) {
                throw new ProcessingException(e);
            }
        });
    }

    @Override
    public void onScriptExecutionResult(ScriptExecutionResultEvent e) {
        synchronized (imagesByLabel) {
            var images = e.getPayload().imagesByLabel();
            for (Map.Entry<String, ImageWrapper> entry : images.entrySet()) {
                imagesByLabel.computeIfAbsent(entry.getKey(), unused -> new ArrayList<>())
                    .add(entry.getValue());
                imageWrappersByIndex.computeIfAbsent(sequenceNumber, unused -> new ArrayList<>())
                    .add(entry.getValue());
            }
        }
    }

    private void maybeWriteLogs() {
        if (item.log().length() > 0 && header != null) {
            var namingStrategy = createNamingStrategy();
            var fileName = item.file().getName();
            var logFileName = namingStrategy.render(sequenceNumber, null, "log", "notifications", fileName.substring(0, fileName.lastIndexOf("."))) + ".txt";
            try {
                var logFilePath = outputDirectory.toPath().resolve(logFileName);
                Files.writeString(logFilePath, item.log().toString(), Charset.defaultCharset());
                item.generatedFiles().add(logFilePath.toFile());
            } catch (IOException e) {
                // ignore
            }
        }
    }

    private FileNamingStrategy createNamingStrategy() {
        return new FileNamingStrategy(
            processParams.extraParams().fileNamePattern(),
            processParams.extraParams().datetimeFormat(),
            processParams.extraParams().dateFormat(),
            processingDate,
            header != null ? header : referenceHeader
        );
    }

    private void updateProgressStatus(boolean increment) {
        if (increment) {
            completed.add(sequenceNumber);
        }
        var done = completed.size();
        BatchOperations.submitOneOfAKind("progress", () -> {
            var prog = done / totalItems;
            if (completed.size() == (int) totalItems) {
                owner.showProgress();
            } else {
                owner.showProgress();
                owner.updateProgress(prog, String.format(message("batch.progress"), done, (int) totalItems));
            }
        });
    }

    @Override
    public void onNotification(NotificationEvent e) {
        synchronized (item) {
            item.log()
                .append(e.type()).append(": ")
                .append(e.title()).append(" ")
                .append(e.header()).append(" ")
                .append(e.message()).append("\n");
        }
        if (e.type() == Notification.AlertType.ERROR) {
            item.status().set(message("batch.error"));
            errors.add(sequenceNumber);
            updateProgressStatus(true);
            maybeExecuteEndOfBatch();
        }
    }

    @Override
    public ImageMathScriptResult execute(String script, SectionKind kind) {
        var result = batchScriptExecutor.execute(script, SectionKind.BATCH);
        processScriptErrors(result);
        renderBatchOutputs(createNamingStrategy(), result);
        return result;
    }

    private void processScriptErrors(ImageMathScriptResult result) {
        var invalidExpressions = result.invalidExpressions();
        var errorCount = invalidExpressions.size();
        if (errorCount > 0) {
            String message = invalidExpressions.stream()
                .map(invalidExpression -> "Expression '" + invalidExpression.label() + "' (" + invalidExpression.expression() + ") : " + invalidExpression.error().getMessage())
                .collect(Collectors.joining(System.lineSeparator()));
            String details = invalidExpressions.stream()
                .map(invalidExpression -> {
                    var sb = new StringWriter();
                    invalidExpression.error().printStackTrace(new java.io.PrintWriter(sb));
                    return sb.toString();
                })
                .collect(Collectors.joining("\n"));
            delegate.onNotification(new NotificationEvent(new Notification(
                Notification.AlertType.ERROR,
                message("error.processing.script"),
                message("script.errors." + (errorCount == 1 ? "single" : "many")),
                message
            )));
        }
    }

    private record FilteringResult(
        List<Integer> discarded,
        Integer best
    ) {

    }
}
