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
package me.champeau.a4j.jsolex.processing.spectrum;

import me.champeau.a4j.jsolex.processing.params.SpectralRay;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.DoubleUnaryOperator;

public class SpectrumAnalyzer {
    private static final Logger LOGGER = LoggerFactory.getLogger(SpectrumAnalyzer.class);

    private static final double TOTAL_ANGLE = Math.toRadians(34);
    public static final int DEFAULT_ORDER = 1;
    public static final int DEFAULT_DENSITY = 2400;
    public static final int DEFAULT_FOCAL_LEN = 125;

    /**
     * Computes the beta angle
     *
     * @param order the grating order
     * @param density the grating density, in lines/mm
     * @param lambda0 the wavelength in nanometers
     * @return the beta angle (in radians)
     */
    private static double computeAngleBeta(int order, int density, double lambda0) {
        return computeAlphaAngle(order, density, lambda0) - TOTAL_ANGLE;
    }

    /**
     * Computes the alpha angle
     *
     * @param order the grating order
     * @param density the grating density, in lines/mm
     * @param lambda0 the wavelength in nanometers
     * @return the alpha angle (in radians)
     */
    private static double computeAlphaAngle(int order, int density, double lambda0) {
        return Math.asin(order * density * lambda0 / (2_000_000 * Math.cos(TOTAL_ANGLE / 2))) + TOTAL_ANGLE / 2;
    }

    /**
     * Returns the spectral dispersion, in nanometers/pixel
     *
     * @param order the grating order
     * @param density the grating density, in lines/mm
     * @param lambda0 the wavelength in nanometers
     * @param pixelSize the pixel size, in micrometers
     * @param focalLength the lens focal length in mm
     * @return the spectral dispersion, in nanometers/pixel
     */
    public static double computeSpectralDispersion(int order, int density, double lambda0, double pixelSize, double focalLength) {
        var beta = computeAngleBeta(order, density, lambda0);
        return 1000 * pixelSize * Math.cos(beta) / density / focalLength;
    }

    /**
     * Returns the spectral dispersion, in nanometers/pixel, using the default
     * Sol'Ex parameters.
     *
     * @param lambda0 the wavelength in nanometers
     * @param pixelSize the pixel size, in micrometers
     * @return the spectral dispersion, in nanometers/pixel
     */
    public static double computeSpectralDispersion(double lambda0, double pixelSize) {
        return computeSpectralDispersion(DEFAULT_ORDER, DEFAULT_DENSITY, lambda0, pixelSize, DEFAULT_FOCAL_LEN);
    }

    /**
     * Returns, for a list of measured data points, the reference data points from BASS2000.
     * The resulting datapoints are normalized according to the maximum intensity of the measured data points.
     *
     * @param measuredDataPoints the measured data points
     * @return the reference data points
     */
    public static List<DataPoint> findReferenceDatapoints(List<DataPoint> measuredDataPoints) {
        var referenceDataPoints = findReferenceDatapointsNoNormalization(measuredDataPoints);
        double maxVal = measuredDataPoints.stream().mapToDouble(DataPoint::intensity).max().orElse(0);
        var maxRef = referenceDataPoints.stream().mapToDouble(DataPoint::intensity).max().orElse(0);
        return referenceDataPoints.stream().map(dataPoint -> new DataPoint(dataPoint.wavelen(), dataPoint.pixelShift(), dataPoint.intensity() * maxVal / maxRef)).toList();
    }

    private static List<DataPoint> findReferenceDatapointsNoNormalization(List<DataPoint> measuredDataPoints) {
        var referenceDataPoints = new ArrayList<DataPoint>();
        for (var dataPoint : measuredDataPoints) {
            referenceDataPoints.add(new DataPoint(dataPoint.wavelen(), dataPoint.pixelShift(), ReferenceIntensities.intensityAt(dataPoint.wavelen())));
        }
        return referenceDataPoints;
    }

    /**
     * Computes the data points for a given average image, using the given query details.
     *
     * @param details the query details
     * @param start the start column
     * @param end the end column
     * @param height the image height
     * @param width the image width
     * @param data the image data
     * @return the data points
     */
    public static List<DataPoint> computeDataPoints(QueryDetails details, DoubleUnaryOperator polynomial, int start, int end, int width, int height, float[] data) {
        var lambda0 = details.line().wavelength();
        var pixelSize = details.pixelSize();
        var binning = details.binning();
        double dispersion = computeSpectralDispersion(lambda0, pixelSize * binning);
        var dataPoints = new ArrayList<DataPoint>();
        double min = Double.MAX_VALUE;
        double max = Double.MIN_VALUE;
        for (int x = start; x < end; x++) {
            var v = polynomial.applyAsDouble(x);
            min = Math.min(v, min);
            max = Math.max(v, max);
        }
        if (min == Double.MAX_VALUE) {
            min = 0;
        }
        if (max == Double.MIN_VALUE) {
            max = height;
        }
        min = Math.max(0, min);
        max = Math.min(height, max);
        double mid = (max + min) / 2.0;
        double range = (max - min) / 2.0;
        for (int y = (int) range; y < height - range; y++) {
            double cpt = 0;
            double val = 0;
            for (int x = start; x < end; x++) {
                var v = polynomial.applyAsDouble(x);
                var shift = v - mid;
                int ny = (int) Math.round(y + shift);
                if (ny >= 0 && ny < height) {
                    val += data[width * ny + x];
                    cpt++;
                }
            }
            if (cpt > 0) {
                var pixelShift = y - mid;
                var wl = computeWavelength(pixelShift, lambda0, dispersion);
                dataPoints.add(new DataPoint(wl, pixelShift, val / cpt));
            }
        }
        return dataPoints;
    }

    public static QueryDetails findBestMatch(Map<QueryDetails, List<DataPoint>> measurements) {
        record PartialSolution(QueryDetails query, double... distances) {
        }
        record Solution(QueryDetails query, double distance) {
        }

        QueryDetails best = null;

        if (!measurements.isEmpty()) {
            var partialSolutions = measurements.entrySet().stream().parallel().map(entry -> {
                var query = entry.getKey();
                var dataPoints = entry.getValue();
                var referenceDataPoints = findReferenceDatapointsNoNormalization(dataPoints);

                var intensities = dataPoints.stream().mapToDouble(DataPoint::intensity).toArray();
                var refIntensities = referenceDataPoints.stream().mapToDouble(DataPoint::intensity).toArray();
                var variation = variationCoef(intensities);
                var refVariation = variationCoef(refIntensities);

                double d1 = calculateAreaBetweenCurves(zScoreNormalize(intensities), zScoreNormalize(refIntensities));
                double d2 = Math.abs(1 / variation - 1 / refVariation);
                return new PartialSolution(query, d1, d2);
            }).toList();
            // normalize scores to 0-1
            var maxDists = partialSolutions.stream().map(PartialSolution::distances).map(Arrays::stream).mapToDouble(stream -> stream.max().orElse(0)).toArray();
            var weights = new double[]{0.7, 0.3};
            best = partialSolutions.stream().map(partialSolution -> {
                double[] normalizedDists = new double[partialSolution.distances.length];
                for (int j = 0; j < normalizedDists.length; j++) {
                    normalizedDists[j] = partialSolution.distances[j] / maxDists[j];
                }
                // Combine normalized distances using weighted sum
                double combinedDistance = 0;
                for (int j = 0; j < normalizedDists.length; j++) {
                    combinedDistance += normalizedDists[j] * weights[j];
                }

                LOGGER.info("Line {} binning {} has distance {}", partialSolution.query().line(), partialSolution.query().binning(), combinedDistance);
                return new Solution(partialSolution.query(), combinedDistance);
            }).min(Comparator.comparingDouble(Solution::distance)).map(Solution::query).orElse(null);
        }

        if (best == null) {
            // Not a good solution, so we'll assume H-alpha or the closest to H-alpha
            double min = Double.MAX_VALUE;
            var ha = SpectralRay.H_ALPHA.wavelength();
            for (var entry : measurements.entrySet()) {
                var query = entry.getKey();
                var diff = Math.abs(query.line().wavelength() - ha);
                if (diff < min) {
                    min = diff;
                    best = query;
                }
            }
        }
        return best;
    }

    private static double variationCoef(double[] values) {
        double mean = 0;
        for (double value : values) {
            mean += value;
        }
        mean /= values.length;
        double stddev = 0;
        for (double value : values) {
            stddev += (value - mean) * (value - mean);
        }
        return Math.sqrt(stddev / values.length) / (1e-6 + mean);
    }

    private static double[] zScoreNormalize(double[] values) {
        double mean = Arrays.stream(values).average().orElse(0);
        double stddev = Math.sqrt(Arrays.stream(values).map(i -> (i - mean) * (i - mean)).average().orElse(0));
        return Arrays.stream(values).map(i -> (i - mean) / stddev).toArray();
    }

    private static double calculateAreaBetweenCurves(double[] normalizedIntensities, double[] normalizedRefIntensities) {
        double area = 0;
        for (int i = 0; i < normalizedIntensities.length; i++) {
            var v1 = normalizedIntensities[i];
            var v2 = normalizedRefIntensities[i];
            area += Math.abs(v1 - v2);
        }
        return area;
    }

    private static double computeWavelength(double pixelShift, double lambda, double dispersion) {
        return 10 * (lambda + pixelShift * dispersion);
    }

    public record QueryDetails(SpectralRay line, double pixelSize, int binning) {
    }

    public record DataPoint(double wavelen, double pixelShift, double intensity) {
    }

}