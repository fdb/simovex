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

import junit.framework.TestCase;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MovieTest extends TestCase {

    List<File> filesToDelete = new ArrayList<File>();

    @Override
    protected void setUp() throws Exception {
        filesToDelete = new ArrayList<File>();
    }

    @Override
    protected void tearDown() throws Exception {
        for (File f : filesToDelete) {
            f.delete();
        }
    }

    /**
     * Test if the movie can be successfully created.
     */
    public void testSave() {
        Movie m = createMovie("test.mp4", 2);
        m.save();
        assertFalse(m.temporaryFileForFrame(0).exists());
        assertFalse(m.temporaryFileForFrame(1).exists());
        assertTrue(m.getMovieFile().exists());
    }

    /**
     * Test if all files are cleaned up.
     */
    public void testCleanup() {
        Movie m = createMovie("test.mp4", 2);
        m.cleanup();
        assertFalse(m.temporaryFileForFrame(0).exists());
        assertFalse(m.temporaryFileForFrame(1).exists());
        assertFalse(m.getMovieFile().exists());
    }

    private Movie createMovie(String filename, int frameCount) {
        String movieFile = markForDeletion(filename);
        int size = 100;
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Movie m = new Movie(movieFile, size, size);
        for (int i = 0; i < frameCount; i++) {
            m.addFrame(img);
            assertTrue(m.temporaryFileForFrame(i).exists());
        }
        return m;
    }

    private String markForDeletion(String filename) {
        filesToDelete.add(new File(filename));
        return filename;
    }


}
