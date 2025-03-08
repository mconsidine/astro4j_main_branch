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
package me.champeau.a4j.ser;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Optional;

/**
 * A ser file reader. The reader is backed with in-memory
 * mapped file, making it possible to process very large
 * ser files. This means that this should usually be used
 * in a try-with-resources block, in order for the backing
 * file to be automatically closed.
 */
public class SerFileReader implements AutoCloseable {
    private static final ZoneId UTC = ZoneId.of("UTC");
    public static final String JSOLEX_RECORDER = "JSOLEX-TRIMMED";

    private final File backingFile;
    private final RandomAccessFile accessFile;
    private final ByteBuffer[] imageBuffers;
    private final ByteBuffer[] timestampsBuffer;
    private final Header header;
    private final byte[] frameBuffer;
    private final int bytesPerFrame;
    private final int maxFramesPerBuffer;

    private int previousFrame = -1;
    private int currentFrame = 0;
    private ZonedDateTime currentTimestamp;
    private volatile boolean closed = false;

    private SerFileReader(File backingFile, RandomAccessFile accessFile, ByteBuffer[] imageBuffers, int maxFramesPerBuffer, ByteBuffer timestampsBuffer, Header header) {
        this.backingFile = backingFile;
        this.accessFile = accessFile;
        this.imageBuffers = imageBuffers;
        this.maxFramesPerBuffer = maxFramesPerBuffer;
        this.timestampsBuffer = new ByteBuffer[]{timestampsBuffer};
        this.header = header;
        this.bytesPerFrame = header.geometry().getBytesPerFrame();
        this.frameBuffer = new byte[bytesPerFrame];
    }

    public Header header() {
        return header;
    }

    public Frame currentFrame() {
        assertNotClosed();
        if (previousFrame != currentFrame) {
            findBuffer().slice().limit(bytesPerFrame).get(frameBuffer);
            if (timestampsBuffer[0] != null) {
                currentTimestamp = TimestampConverter.of(timestampsBuffer[0].getLong()).map(c -> c.atZone(UTC)).orElse(null);
            }
            previousFrame = currentFrame;
        }
        return new Frame(currentFrame, ByteBuffer.wrap(frameBuffer), Optional.ofNullable(currentTimestamp));
    }

    private void assertNotClosed() {
        if (closed) {
            throw new IllegalStateException("Ser file was closed");
        }
    }

    public void nextFrame() {
        assertNotClosed();
        currentFrame = (currentFrame + 1) % header.frameCount();
        positionBuffers();
    }

    private ByteBuffer findBuffer() {
        return imageBuffers[currentFrame / maxFramesPerBuffer];
    }

    private void positionBuffers() {
        findBuffer().position((currentFrame % maxFramesPerBuffer) * bytesPerFrame);
        if (timestampsBuffer[0] != null) {
            timestampsBuffer[0].position(8 * currentFrame);
        }
    }

    public void seekFrame(int frameNb) {
        assertNotClosed();
        currentFrame = frameNb % header.frameCount();
        positionBuffers();
    }

    public void seekLast() {
        seekFrame(header.frameCount() - 1);
    }

    public void seekFirst() {
        seekFrame(0);
    }

    public static SerFileReader of(File file) throws IOException {
        var tmpReader = createBaseReader(file);
        return fixReader(tmpReader);
    }

    private static SerFileReader createBaseReader(File file) throws IOException {
        RandomAccessFile raf = new RandomAccessFile(file, "r");
        FileChannel channel = raf.getChannel();
        var headerBuffer = channel
            .map(FileChannel.MapMode.READ_ONLY, 0, Math.min(65536, channel.size()));
        var fileId = readAsciiString(headerBuffer, 14);
        headerBuffer.order(ByteOrder.LITTLE_ENDIAN);
        Header header = readHeader(fileId, headerBuffer);
        long headerLength = headerBuffer.position();
        var bytesPerFrame = header.geometry().getBytesPerFrame();
        long maxFramesInBuffer = (long) Math.floor(Integer.MAX_VALUE / (double) bytesPerFrame);
        int numBuffers = (int) Math.ceil(header.frameCount() / (double) maxFramesInBuffer);
        ByteBuffer[] imageBuffers = new ByteBuffer[numBuffers];
        long remainingFrameCount = header.frameCount();
        for (int i = 0; i < numBuffers; i++) {
            long nFramesInBuffer = Math.min(remainingFrameCount, maxFramesInBuffer);
            long offset = headerLength + i * maxFramesInBuffer * bytesPerFrame;
            long size = nFramesInBuffer * bytesPerFrame;
            imageBuffers[i] = channel.map(FileChannel.MapMode.READ_ONLY, offset, size).order(header.geometry().imageEndian());
            remainingFrameCount -= maxFramesInBuffer;
        }
        boolean hasTimestamps = header.metadata().localDateTime() != null;
        long dataLength = header.frameCount() * (long) bytesPerFrame;
        if (headerLength + dataLength + 8L * header.frameCount() > file.length()) {
            // Workaround for some videos where timestamps are truncated
            hasTimestamps = false;
            header = new Header(fileId, header.camera(), header.geometry(), header.frameCount(), header.metadata().withoutTimestamps());
        }
        ByteBuffer timestampsBuffer = hasTimestamps ? channel.map(FileChannel.MapMode.READ_ONLY, headerLength + dataLength, 8L * header.frameCount()).order(ByteOrder.LITTLE_ENDIAN) : null;
        var tmpReader = new SerFileReader(file, raf, imageBuffers, (int) maxFramesInBuffer, timestampsBuffer, header);
        return tmpReader;
    }

    /**
     * It appears that some software lie about the true pixel depth of the images,
     * which means we cannot rely on the pixel depth provided in the header.
     * This will select a few frames in the middle of the video and determine the
     * true pixel depth by looking at the maximum pixel value.
     *
     * @param tmpReader the reader to fix
     * @return a fixed reader
     */
    private static SerFileReader fixReader(SerFileReader tmpReader) {
        if (JSOLEX_RECORDER.equals(tmpReader.header.fileId())) {
            // Trust JSol'Ex rewritten files
            return tmpReader;
        }
        var frameCount = tmpReader.header.frameCount();
        var geometry = tmpReader.header.geometry();
        var width = geometry.width();
        var height = geometry.height();
        var sampling = Math.max(10, 10 * frameCount / 100);
        int bytesPerPixel = geometry.getBytesPerPixel();
        if (bytesPerPixel > 1) {
            int numPlanes = geometry.colorMode().getNumberOfPlanes();
            int bitsToDiscard = 16 - geometry.pixelDepthPerPlane();
            int maxPixel = 0;
            int minPixel = Integer.MAX_VALUE;
            int pixelDepth = 0;
            int mid = frameCount / 2;
            boolean goLeft = true;

            outer:
            for (int step = 0; step <= mid; step += goLeft ? 0 : sampling) {
                int i = goLeft ? mid - step : mid + step;
                goLeft = !goLeft;

                if (i < 0 || i >= frameCount) {
                    continue;
                }

                tmpReader.seekFrame(i);
                var data = tmpReader.currentFrame().data();
                var dataLen = width * height * numPlanes;

                for (int j = 0; j < dataLen; j += 16) {
                    int pixelValue = readColor(data, bytesPerPixel, bitsToDiscard);
                    maxPixel = Math.max(maxPixel, pixelValue);
                    minPixel = Math.min(minPixel, pixelValue);
                    pixelDepth = (int) Math.ceil(Math.log(maxPixel) / Math.log(2));

                    if (pixelDepth == 16) {
                        break outer;
                    }
                }
            }

            pixelDepth = (int) Math.ceil(Math.log(maxPixel) / Math.log(2));
            if (pixelDepth < 1) {
                return tmpReader;
            }
            var newGeometry = new ImageGeometry(geometry.colorMode(), geometry.width(), geometry.height(), pixelDepth, geometry.imageEndian());
            return new SerFileReader(tmpReader.backingFile, tmpReader.accessFile, tmpReader.imageBuffers, tmpReader.maxFramesPerBuffer, tmpReader.timestampsBuffer[0],
                new Header(tmpReader.header.fileId(), tmpReader.header.camera(), newGeometry, tmpReader.header.frameCount(), tmpReader.header.metadata()));
        }
        return tmpReader;
    }

    private static int readColor(ByteBuffer frameData, int bytesPerPixel, int bitsToDiscard) {
        int next;
        if (bytesPerPixel == 1) {
            // Data of between 1 and 8 bits should be stored aligned with the most significant bit
            int v = frameData.get() >> bitsToDiscard;
            next = (v & 0xFF) << 8;
        } else {
            next = (frameData.getShort() << 8 << bitsToDiscard) & 0xFFFF;
        }
        return next;
    }

    public Optional<Double> estimateFps() {
        Optional<Double> value = Optional.empty();
        if (header.metadata().telescope().contains("fps=")) {
            var i = header.metadata().telescope().indexOf("fps=");
            // last index is either the end of the string or the first letter after the fps value
            var end = i + 4;
            while (end < header.metadata().telescope().length() && !Character.isLetter(header.metadata().telescope().charAt(end))) {
                end++;
            }
            String fps = header.metadata().telescope().substring(i + 4, end);
            try {
                return Optional.of(Double.parseDouble(fps));
            } catch (NumberFormatException e) {
                // ignore
            }
        }
        if (header.metadata().hasTimestamps()) {
            seekLast();
            ZonedDateTime lastFrameTimestamp = currentFrame().timestamp().orElseThrow();
            seekFirst();
            ZonedDateTime firstFrameTimestamp = currentFrame().timestamp().orElseThrow();
            Duration sequenceDuration = Duration.between(firstFrameTimestamp, lastFrameTimestamp);
            long seconds = sequenceDuration.getSeconds();
            value = Optional.ofNullable(seconds > 0 ? (double) header.frameCount() / seconds : null);
        }
        return value;
    }

    private static Header readHeader(String fileId, ByteBuffer buffer) throws IOException {
        Camera camera = new Camera(buffer.getInt());
        Optional<ColorMode> colorMode = ColorMode.of(buffer.getInt());
        if (colorMode.isEmpty()) {
            throw new IOException("Invalid color mode");
        }
        ByteOrder imageByteOrder = readEndian(buffer);
        int imageWidth = buffer.getInt();
        int imageHeight = buffer.getInt();
        int pixelDepthPerPlane = buffer.getInt();
        int frameCount = buffer.getInt();
        String observer = readAsciiString(buffer, 40);
        String instrument = readAsciiString(buffer, 40);
        String telescope = readAsciiString(buffer, 40);
        LocalDateTime localDate = TimestampConverter.of(buffer.getLong()).orElse(null);
        ZonedDateTime utcDate = TimestampConverter.of(buffer.getLong()).map(e -> e.atZone(UTC)).orElseGet(() -> {
            if (localDate != null) {
                return localDate.atZone(UTC);
            }
            return LocalDateTime.now().atZone(UTC);
        });
        return new Header(
            fileId,
            camera,
            new ImageGeometry(colorMode.get(), imageWidth, imageHeight, pixelDepthPerPlane, imageByteOrder),
            frameCount,
            new ImageMetadata(
                observer,
                instrument,
                telescope,
                localDate != null,
                localDate,
                utcDate
            )
        );
    }


    private static ByteOrder readEndian(ByteBuffer buffer) throws IOException {
        return switch (buffer.getInt()) {
            case 0 -> ByteOrder.BIG_ENDIAN;
            case 1 -> ByteOrder.LITTLE_ENDIAN;
            default -> throw new IOException("Invalid endian mode");
        };
    }

    private static String readAsciiString(ByteBuffer buffer, int len) {
        byte[] str = new byte[len];
        buffer.get(str);
        return new String(str);
    }

    @Override
    public void close() throws Exception {
        closed = true;
        accessFile.close();
        Arrays.fill(imageBuffers, null);
        timestampsBuffer[0] = null;
        System.gc();
    }

    public boolean isClosed() {
        return closed;
    }

    public SerFileReader reopen() throws IOException {
        var baseReader = createBaseReader(backingFile);
        return new SerFileReader(
            baseReader.backingFile,
            baseReader.accessFile,
            baseReader.imageBuffers,
            baseReader.maxFramesPerBuffer,
            baseReader.timestampsBuffer[0],
            header
        );
    }
}
