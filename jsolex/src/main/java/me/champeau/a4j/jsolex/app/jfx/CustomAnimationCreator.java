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
package me.champeau.a4j.jsolex.app.jfx;

import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.util.converter.DoubleStringConverter;
import javafx.util.converter.IntegerStringConverter;
import me.champeau.a4j.jsolex.app.AlertFactory;
import me.champeau.a4j.jsolex.app.listeners.RedshiftImagesProcessor;
import me.champeau.a4j.jsolex.processing.params.ProcessParams;
import me.champeau.a4j.jsolex.processing.sun.workflow.PixelShiftRange;
import me.champeau.a4j.jsolex.processing.util.BackgroundOperations;
import me.champeau.a4j.jsolex.processing.util.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Files;

import static me.champeau.a4j.jsolex.app.JSolEx.message;

public class CustomAnimationCreator {
    public static final String DEFAULT_DELAY = "25";

    @FXML
    public TextField width;
    @FXML
    public TextField height;
    @FXML
    public TextField minShift;
    @FXML
    public TextField maxShift;
    @FXML
    public TextField title;
    @FXML
    public CheckBox generateAnim;
    @FXML
    public CheckBox generatePanel;
    @FXML
    public CheckBox annotateAnim;
    @FXML
    public Label minShiftHint;
    @FXML
    public Label maxShiftHint;
    @FXML
    public TextField delay;
    @FXML
    public Label estimatedDiskSpace;
    @FXML
    public ColorPicker annotationColor;

    private int imageWidth;
    private int imageHeight;
    private int x;
    private int y;
    private RedshiftImagesProcessor redshiftProcessor;

    private Stage stage;

    public void setup(Stage stage,
                      ProcessParams processParams,
                      PixelShiftRange range,
                      int imageWidth,
                      int imageHeight,
                      int x,
                      int y,
                      int w,
                      int h,
                      RedshiftImagesProcessor redshiftProcessor,
                      int id) {
        this.stage = stage;
        this.x = x;
        this.y = y;
        this.imageWidth = imageWidth;
        this.imageHeight = imageHeight;
        this.redshiftProcessor = redshiftProcessor;
        width.setTextFormatter(createDimensionFormatter());
        height.setTextFormatter(createDimensionFormatter());
        width.setText(Integer.toString(w));
        height.setText(Integer.toString(h));
        var minPixelShift = range.minPixelShift();
        var maxPixelShift = range.maxPixelShift();
        var minAbsShift = Math.min(Math.abs(minPixelShift), Math.abs(maxPixelShift));
        if (minAbsShift == 0) {
            minAbsShift = 1;
        }
        minPixelShift = -minAbsShift;
        maxPixelShift = minAbsShift;
        minShift.setTextFormatter(createShiftFormatter(range.minPixelShift(), range.maxPixelShift()));
        maxShift.setTextFormatter(createShiftFormatter(range.minPixelShift(), range.maxPixelShift()));
        minShift.setText(Double.toString(minPixelShift));
        maxShift.setText(Double.toString(maxPixelShift));
        annotateAnim.disableProperty().bind(generateAnim.selectedProperty().not());
        delay.disableProperty().bind(generateAnim.selectedProperty().not());
        if (processParams.spectrumParams().ray().wavelength() > 0) {
            minShiftHint.textProperty().bind(minShift.textProperty().map(s -> redshiftProcessor.toAngstroms(safeParseDouble(s))));
            maxShiftHint.textProperty().bind(maxShift.textProperty().map(s -> redshiftProcessor.toAngstroms(safeParseDouble(s))));
        } else {
            minShiftHint.setText(message("shift.hint"));
            maxShiftHint.setText(message("shift.hint"));
        }
        estimatedDiskSpace.textProperty().bind(Bindings.subtract(
            Bindings.createDoubleBinding(() -> Double.parseDouble(maxShift.getText()), maxShift.textProperty()),
            Bindings.createDoubleBinding(() -> Double.parseDouble(minShift.getText()), minShift.textProperty())
        ).map(n -> redshiftProcessor.estimateRequiredDiskSpace(n.doubleValue())));
        title.setText(String.format(message("custom.animation"), id));
        delay.setTextFormatter(new TextFormatter<>(new IntegerStringConverter() {
            @Override
            public Integer fromString(String s) {
                var i = super.fromString(s);
                if (i == null || i < 1) {
                    return 25;
                }
                return i;
            }
        }));
        delay.setText(DEFAULT_DELAY);
        annotationColor.setValue(Color.YELLOW);
        annotationColor.disableProperty().bind(annotateAnim.selectedProperty().not());
    }

    private static double safeParseDouble(String s) {
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static TextFormatter<Integer> createDimensionFormatter() {
        return new TextFormatter<>(new IntegerStringConverter() {
            @Override
            public Integer fromString(String s) {
                var i = super.fromString(s);
                if (i == null || i < 64) {
                    return 64;
                }
                // round to a multiple of 8
                return (i + 7) & ~7;
            }
        });
    }

    private static TextFormatter<Double> createShiftFormatter(double minPixelShift, double maxPixelShift) {
        return new TextFormatter<>(new DoubleStringConverter() {
            @Override
            public Double fromString(String s) {
                var i = super.fromString(s);
                if (i == null || i < minPixelShift) {
                    return minPixelShift;
                }
                if (i > maxPixelShift) {
                    return maxPixelShift;
                }
                return i;
            }

            @Override
            public String toString(Double aDouble) {
                Double value = aDouble;
                if (value != null) {
                    if (value < minPixelShift) {
                        return Double.toString(minPixelShift);
                    }
                    if (value > maxPixelShift) {
                        return Double.toString(maxPixelShift);
                    }
                }
                return super.toString(aDouble);
            }
        });
    }

    private int width() {
        return Integer.parseInt(width.getText());
    }

    private int height() {
        return Integer.parseInt(height.getText());
    }

    private double minPixelShift() {
        return safeParseDouble(minShift.getText());
    }

    private double maxPixelShift() {
        return safeParseDouble(maxShift.getText());
    }

    @FXML
    private void close() {
        stage.close();
    }

    @FXML
    private void generate() {
        double imageCount = Double.parseDouble(maxShift.getText())-Double.parseDouble(minShift.getText());
        try {
            if (Files.getFileStore(TemporaryFolder.tempDir()).getUsableSpace() < redshiftProcessor.estimateRequiredBytesForProcessing(imageCount)) {
                var alert = AlertFactory.error(message("disk.space.error"));
                alert.showAndWait();
                return;
            }
        } catch (IOException e) {
            // ignore
        }
        stage.close();
        if (generateAnim.isSelected()) {
            BackgroundOperations.async(() -> redshiftProcessor.generateStandaloneAnimation(x, y, width(), height(), minPixelShift(), maxPixelShift(), title.getText(), "custom-anim", annotateAnim.isSelected(), Integer.parseInt(delay.getText()), asRGB(annotationColor.getValue())));
        }
        if (generatePanel.isSelected()) {
            BackgroundOperations.async(() -> redshiftProcessor.generateStandalonePanel(x, y, width(), height(), minPixelShift(), maxPixelShift(), title.getText(), "custom-panel", asRGB(annotationColor.getValue())));
        }
    }

    private static int[] asRGB(Color color) {
        return new int[] {
            (int) (color.getRed() * 255),
            (int) (color.getGreen() * 255),
            (int) (color.getBlue() * 255)
        };
    }
}
