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

import javafx.application.HostServices;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.stage.Stage;
import me.champeau.a4j.jsolex.app.jfx.ApplyUserRotation;
import me.champeau.a4j.jsolex.app.jfx.MultipleImagesViewer;
import me.champeau.a4j.jsolex.processing.event.ProgressOperation;
import me.champeau.a4j.jsolex.processing.expr.ImageMathScriptExecutor;
import me.champeau.a4j.jsolex.processing.params.ProcessParams;
import me.champeau.a4j.jsolex.processing.sun.TrimmingParameters;

public interface JSolExInterface {
    MultipleImagesViewer getImagesViewer();

    Stage getMainStage();

    Tab getStatsTab();

    Tab getProfileTab();

    Tab getMetadataTab();

    Tab getRedshiftTab();

    Tab getImagesViewerTab();

    TabPane getTabs();

    void showProgress();

    void hideProgress();

    void updateProgress(double progress, String text);

    void prepareForScriptExecution(ImageMathScriptExecutor executor, ProcessParams params, ProgressOperation rootOperation, ImageMathScriptExecutor.SectionKind sectionKind);

    HostServices getHostServices();

    void newSession();

    void prepareForRedshiftImages(RedshiftImagesProcessor processor);

    void prepareForGongImageDownload(ProcessParams processParams);

    void applyUserRotation(ApplyUserRotation params);

    void setTrimmingParameters(TrimmingParameters payload);

    ImageMathScriptExecutor getScriptExecutor();

}
