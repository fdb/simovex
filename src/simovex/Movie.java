/**
 * Copyright 2009 Frederik De Bleser
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package simovex;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Main class used for movie export.
 * <p/>
 * To export a movie, use the class like this:
 * <pre>
 * Movie m = new Movie("hello.mp4", 640, 480);
 * for (RenderedImage img: listOfImages) {
 *     m.addFrame(img);
 * }
 * m.save();
 * </pre>
 */
public class Movie {

    public static enum CodecType {
        ANIMATION, FLV, H263, H264, MPEG4, RAW, THEORA, WMV
    }

    public static enum CompressionQuality {
        LOW, MEDIUM, HIGH, BEST
    }


    private static final File FFMPEG_BINARY;
    private static final String TEMPORARY_FILE_PREFIX = "sme";
    private static final String FFMPEG_PRESET_TEMPLATE = "res/ffpresets/libx264-%s.ffpreset";
    private static final Map<CodecType, String> codecTypeMap;
    private static final Map<CompressionQuality, String> compressionQualityMap;


    static {
        String osName = System.getProperty("os.name").split("\\s")[0];
        // If we provide a binary for this system, use it. Otherwise, see if a default "ffmpeg"  binary exists.
        File packagedBinary = new File(String.format("platform/%s/bin/ffmpeg", osName));
        if (packagedBinary.exists()) {
            FFMPEG_BINARY = packagedBinary;
        } else {
            FFMPEG_BINARY = new File("/usr/bin/ffmpeg");
        }
        codecTypeMap = new HashMap<CodecType, String>(CodecType.values().length);
        codecTypeMap.put(CodecType.ANIMATION, "qtrle");
        codecTypeMap.put(CodecType.FLV, "flv");
        codecTypeMap.put(CodecType.H263, "h263");
        codecTypeMap.put(CodecType.H264, "libx264");
        codecTypeMap.put(CodecType.MPEG4, "mpeg4");
        codecTypeMap.put(CodecType.RAW, "rawvideo");
        codecTypeMap.put(CodecType.WMV, "wmv");
        compressionQualityMap = new HashMap<CompressionQuality, String>(CompressionQuality.values().length);
        compressionQualityMap.put(CompressionQuality.LOW, "baseline");
        compressionQualityMap.put(CompressionQuality.MEDIUM, "default");
        compressionQualityMap.put(CompressionQuality.HIGH, "hq");
        compressionQualityMap.put(CompressionQuality.BEST, "lossless_max");
    }

    private String movieFilename;
    private int width, height;
    private CodecType codecType;
    private CompressionQuality compressionQuality;
    private boolean verbose;
    private int frameCount = 0;
    private String temporaryFileTemplate;

    public Movie(String movieFilename, int width, int height) {
        this(movieFilename, width, height, CodecType.H264, CompressionQuality.BEST, false);
    }

    public Movie(String movieFilename, int width, int height, CodecType codecType, CompressionQuality compressionQuality, boolean verbose) {
        this.movieFilename = movieFilename;
        this.width = width;
        this.height = height;
        this.codecType = codecType;
        this.compressionQuality = compressionQuality;
        this.verbose = verbose;
        // Generate the prefix for a temporary file.
        // We generate a temporary file, then use that as the prefix for our own files.
        try {
            File tempFile = File.createTempFile(TEMPORARY_FILE_PREFIX, "");
            temporaryFileTemplate = tempFile.getPath() + "-%05d.png";
            tempFile.delete();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean isVerbose() {
        return verbose;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    public int getFrameCount() {
        return frameCount;
    }

    public String getMovieFilename() {
        return movieFilename;
    }

    public File getMovieFile() {
        return new File(movieFilename);
    }

    public File temporaryFileForFrame(int frame) {
        return new File(String.format(temporaryFileTemplate, frame));
    }

    /**
     * Add the image to the movie.
     * <p/>
     * The image size needs to be exactly the same size as the movie.
     * <p/>
     * Internally, this saves the image to a temporary image and increases the frame counter. Temporary images are
     * cleaned up when calling save() or if an error occurs.
     *
     * @param img the image to add to the movie.
     */
    public void addFrame(RenderedImage img) {
        if (img.getWidth() != width || img.getHeight() != height) {
            throw new RuntimeException("Given image does not have the same size as the movie.");
        }
        try {
            ImageIO.write(img, "png", temporaryFileForFrame(frameCount));
            frameCount++;
        } catch (IOException e) {
            cleanupAndThrowException(e);
        }
    }

    /**
     * Finishes the export and save the movie.
     */
    public void save() {
        StringWriter sw = new StringWriter();
        PrintWriter out = new PrintWriter(sw, true);

        String type = codecTypeMap.get(codecType);
        int bitRate = bitRateForSize(width, height);
        String quality = compressionQualityMap.get(compressionQuality);


        ArrayList<String> commandList = new ArrayList<String>();
        commandList.add(FFMPEG_BINARY.getAbsolutePath());
        commandList.add("-y"); // Overwrite target if exists
        commandList.add("-i");
        commandList.add(temporaryFileTemplate); // Input images
        commandList.add("-vcodec");
        commandList.add(type); // Target video codec
        if (codecType == CodecType.H264) {
            commandList.add("-fpre");
            commandList.add(String.format(FFMPEG_PRESET_TEMPLATE, quality));
        } else {
            commandList.add("-b");
            commandList.add(bitRate + "k"); // Target bit rate
        }
        commandList.add(movieFilename); // Target file name

        ProcessBuilder pb = new ProcessBuilder(commandList);
        if (verbose) {
            for (String cmd : pb.command()) {
                System.out.print(cmd + " ");
            }
            System.out.println();
        }
        pb.redirectErrorStream(true);
        Process p;
        try {
            p = pb.start();
            p.getOutputStream().close();
            BufferedReader in = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;
            while ((line = in.readLine()) != null)
                out.println(line);
            p.waitFor();
            if (verbose) {
                System.out.println(sw.toString());
            }
        } catch (IOException e) {
            cleanupAndThrowException(e);
        } catch (InterruptedException e) {
            cleanupAndThrowException(e);
        }
        cleanup();
    }

    private int bitRateForSize(int width, int height) {
        return 1000;
    }

    /**
     * Cleans up the temporary images.
     * <p/>
     * Normally you should not call this method as it is called automatically when running finish() or if an error
     * occurred. The only reason to call it is if you have added images and then decide you don't want to generate
     * a movie. In that case, instead of calling finish(), call cleanup().
     *
     * @see #save()
     */
    public void cleanup() {
        for (int i = 0; i < frameCount; i++) {
            temporaryFileForFrame(i).delete();
        }
    }

    private void cleanupAndThrowException(Throwable t) {
        cleanup();
        throw new RuntimeException(t);
    }

    public static void main(String[] args) {
        int width = 640;
        int height = 480;
        // Create a new movie.
        Movie movie = new Movie("test.mov", width, height);
        movie.setVerbose(true);
        /// Initialize an image to draw on.
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = (Graphics2D) img.getGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        for (int frame = 0; frame < 20; frame++) {
            System.out.println("frame = " + frame);
            // Clear the canvas and draw some simple circles.
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, width, height);
            Random r = new Random(0);
            for (int j = 0; j < 100; j++) {
                g.setColor(new Color(r.nextInt(255), 255, r.nextInt(255)));
                g.fillOval(r.nextInt(width) + frame, r.nextInt(height) + frame, 30, 30);
            }
            // Add the image to the movie.
            movie.addFrame(img);
        }
        // Export the movie.
        movie.save();
    }
}

