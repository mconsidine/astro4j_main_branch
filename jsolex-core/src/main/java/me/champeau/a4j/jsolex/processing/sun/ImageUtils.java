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
package me.champeau.a4j.jsolex.processing.sun;

import me.champeau.a4j.jsolex.processing.util.ProcessingException;
import me.champeau.a4j.ser.ColorMode;
import me.champeau.a4j.ser.EightBitConversionSupport;
import me.champeau.a4j.ser.bayer.BilinearDemosaicingStrategy;
import me.champeau.a4j.ser.bayer.ChannelExtractingConverter;
import me.champeau.a4j.ser.bayer.DemosaicingRGBImageConverter;
import me.champeau.a4j.ser.bayer.FloatPrecisionImageConverter;
import me.champeau.a4j.ser.bayer.ImageConverter;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import static me.champeau.a4j.ser.bayer.BayerMatrixSupport.GREEN;

public class ImageUtils {
    private ImageUtils() {

    }

    public static void writeMonoImage(
            int width,
            int height,
            float[] data,
            File outputFile
    ) {
        var image = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
        byte[] converted = EightBitConversionSupport.to8BitImage(data);
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                int value = converted[y * width + x] & 0xFF;
                image.setRGB(x, y, value << 16 | value << 8 | value);
            }
        }
        try {
            createDirectoryFor(outputFile);
            ImageIO.write(image, "png", outputFile);
        } catch (IOException e) {
            throw new ProcessingException(e);
        }
    }

    private static void createDirectoryFor(File outputFile) throws IOException {
        var path = outputFile.getParentFile().toPath();
        Files.createDirectories(path);
    }

    public static void writeRgbImage(
            int width,
            int height,
            float[] r,
            float[] g,
            float[] b,
            File outputFile
    ) {
        var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                int rv = Math.round(r[y * width + x]);
                int gv = Math.round(g[y * width + x]);
                int bv = Math.round(b[y * width + x]);
                rv = (rv >> 8) & 0xFF;
                gv = (gv >> 8) & 0xFF;
                bv = (bv >> 8) & 0xFF;
                image.setRGB(x, y, rv << 16 | gv << 8 | bv);
            }
        }
        try {
            createDirectoryFor(outputFile);
            ImageIO.write(image, "png", outputFile);
        } catch (IOException e) {
            throw new ProcessingException(e);
        }
    }

    public static ImageConverter<float[]> createImageConverter(ColorMode colorMode) {
        return new FloatPrecisionImageConverter(
                new ChannelExtractingConverter(
                        new DemosaicingRGBImageConverter(
                                new BilinearDemosaicingStrategy(),
                                colorMode
                        ),
                        GREEN
                )
        );
    }
}