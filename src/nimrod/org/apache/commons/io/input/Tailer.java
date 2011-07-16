/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package nimrod.org.apache.commons.io.input;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.TailerListener;
import org.apache.commons.io.input.TailerListenerAdapter;

/**
 * Simple implementation of the unix "tail -f" functionality.
 * <p>
 * <h2>1. Create a TailerListener implementation</h3>
 * <p>
 * First you need to create a {@link TailerListener} implementation
 * ({@link TailerListenerAdapter} is provided for convenience so that you don't have to
 * implement every method).
 * </p>
 *
 * <p>For example:</p>
 * <pre>
 *  public class MyTailerListener extends TailerListenerAdapter {
 *      public void handle(String line) {
 *          System.out.println(line);
 *      }
 *  }
 * </pre>
 *
 * <h2>2. Using a Tailer</h2>
 *
 * You can create and use a Tailer in one of three ways:
 * <ul>
 *   <li>Using one of the static helper methods:
 *     <ul>
 *       <li>{@link Tailer#create(File, TailerListener)}</li>
 *       <li>{@link Tailer#create(File, TailerListener, long)}</li>
 *       <li>{@link Tailer#create(File, TailerListener, long, boolean)}</li>
 *       <li>{@link Tailer#create(File, TailerListener, long, boolean, int)}</li>
 *     </ul>
 *   </li>
 *   <li>Using an {@link java.util.concurrent.Executor}</li>
 *   <li>Using an {@link Thread}</li>
 * </ul>
 *
 * An example of each of these is shown below.
 *
 * <h3>2.1 Using the static helper method</h3>
 *
 * <pre>
 *      TailerListener listener = new MyTailerListener();
 *      Tailer tailer = Tailer.create(file, listener, delay);
 * </pre>
 *
 * <h3>2.2 Use an Executor</h3>
 *
 * <pre>
 *      TailerListener listener = new MyTailerListener();
 *      Tailer tailer = new Tailer(file, listener, delay);
 *
 *      // stupid executor impl. for demo purposes
 *      Executor executor = new Executor() {
 *          public void execute(Runnable command) {
 *              command.run();
 *           }
 *      };
 *
 *      executor.execute(tailer);
 * </pre>
 *
 *
 * <h3>2.3 Use a Thread</h3>
 * <pre>
 *      TailerListener listener = new MyTailerListener();
 *      Tailer tailer = new Tailer(file, listener, delay);
 *      Thread thread = new Thread(tailer);
 *      thread.setDaemon(true); // optional
 *      thread.start();
 * </pre>
 *
 * <h2>3. Stop Tailing</h3>
 * <p>Remember to stop the tailer when you have done with it:</p>
 * <pre>
 *      tailer.stop();
 * </pre>
 *
 * @see TailerListener
 * @see TailerListenerAdapter
 * @version $Id: Tailer.java 1127854 2011-05-26 10:03:42Z sebb $
 * @since Commons IO 2.0
 */
public class Tailer implements Runnable {

    /**
     * The file which will be tailed.
     */
    private final File file;
    /**
     * The amount of time to wait for the file to be updated.
     */
    private final long delay;
    /**
     * Whether to tail from the end or start of file
     */
    private final boolean end;
    /**
     * The listener to notify of events when tailing.
     */
    private final TailerListener listener;
    /**
     * The tailer will run as long as this value is true.
     */
    private volatile boolean run = true;
    /**
     * The "truncated" line from buffered reads (when buffer ends prior to reaching eol or eof).
     */
    private volatile String remaind = "";
    /**
     * The buffer size for buffered reads.
     */
    private final int bufferSize;
    /**
     * The recycled buffer for buffered reads.
     */
    private final byte[] buffer;

    /**
     * Creates a Tailer for the given file, starting from the beginning, with the default delay of 1.0s.
     * @param file The file to follow.
     * @param listener the TailerListener to use.
     */
    public Tailer(File file, TailerListener listener) {
        this(file, listener, 1000);
    }

    /**
     * Creates a Tailer for the given file, starting from the beginning.
     * @param file the file to follow.
     * @param listener the TailerListener to use.
     * @param delay the delay between checks of the file for new content in milliseconds.
     */
    public Tailer(File file, TailerListener listener, long delay) {
        this(file, listener, delay, false);
    }

    /**
     * Creates a Tailer for the given file, with a delay other than the default 1.0s.
     * @param file the file to follow.
     * @param listener the TailerListener to use.
     * @param delay the delay between checks of the file for new content in milliseconds.
     * @param end Set to true to tail from the end of the file, false to tail from the beginning of the file.
     */
    public Tailer(File file, TailerListener listener, long delay, boolean end) {
        this(file, listener, delay, end, 1024);
    }

    /**
     * Creates a Tailer for the given file, with a delay other than the default 1.0s.
     * @param file the file to follow.
     * @param listener the TailerListener to use.
     * @param delay the delay between checks of the file for new content in milliseconds.
     * @param end Set to true to tail from the end of the file, false to tail from the beginning of the file.
     * @param bufferSize Buffer size for buffered reads from the tailed file.
     */
    public Tailer(File file, TailerListener listener, long delay, boolean end, int bufferSize) {
        this.file = file;
        this.delay = delay;
        this.end = end;

        this.bufferSize = bufferSize;
        this.buffer = new byte[bufferSize];

        // Save and prepare the listener
        this.listener = listener;
        
        // NOT NEEDED IN NIMROD: listener.init(this);
    }

    /**
     * Creates and starts a Tailer for the given file.
     *
     * @param file the file to follow.
     * @param listener the TailerListener to use.
     * @param delay the delay between checks of the file for new content in milliseconds.
     * @param end Set to true to tail from the end of the file, false to tail from the beginning of the file.
     * @param bufferSize Buffer size for buffered reads from the tailed file.
     * @return The new tailer
     */
    public static Tailer create(File file, TailerListener listener, long delay, boolean end, int bufferSize) {
        Tailer tailer = new Tailer(file, listener, delay, end, bufferSize);
        Thread thread = new Thread(tailer);
        thread.setDaemon(true);
        thread.start();
        return tailer;
    }

    /**
     * Creates and starts a Tailer for the given file.
     *
     * @param file the file to follow.
     * @param listener the TailerListener to use.
     * @param delay the delay between checks of the file for new content in milliseconds.
     * @param end Set to true to tail from the end of the file, false to tail from the beginning of the file.
     * @return The new tailer
     */
    public static Tailer create(File file, TailerListener listener, long delay, boolean end) {
        return create(file, listener, delay, end, 1024);
    }

    /**
     * Creates and starts a Tailer for the given file, starting at the beginning of the file
     *
     * @param file the file to follow.
     * @param listener the TailerListener to use.
     * @param delay the delay between checks of the file for new content in milliseconds.
     * @return The new tailer
     */
    public static Tailer create(File file, TailerListener listener, long delay) {
        return create(file, listener, delay, false, 1024);
    }

    /**
     * Creates and starts a Tailer for the given file, starting at the beginning of the file
     * with the default delay of 1.0s
     *
     * @param file the file to follow.
     * @param listener the TailerListener to use.
     * @return The new tailer
     */
    public static Tailer create(File file, TailerListener listener) {
        return create(file, listener, 1000, false, 1024);
    }

    /**
     * Return the file.
     *
     * @return the file
     */
    public File getFile() {
        return file;
    }

    /**
     * Return the delay.
     *
     * @return the delay
     */
    public long getDelay() {
        return delay;
    }

    /**
     * Follows changes in the file, calling the TailerListener's handle method for each new line.
     */
    public void run() {
        RandomAccessFile reader = null;
        try {
            long last = 0; // The last time the file was checked for changes
            long position = 0; // position within the file
            // Open the file
            while (run && reader == null) {
                try {
                    reader = new RandomAccessFile(file, "r");
                } catch (FileNotFoundException e) {
                    listener.fileNotFound();
                }

                if (reader == null) {
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException e) {
                    }
                } else {
                    // The current position in the file
                    position = end ? file.length() : 0;
                    last = System.currentTimeMillis();
                    reader.seek(position);
                }
            }


            while (run) {

                // Check the file length to see if it was rotated
                long length = file.length();

                if (length < position) {

                    // File was rotated
                    listener.fileRotated();

                    // Reopen the reader after rotation
                    try {
                        // Ensure that the old file is closed iff we re-open it successfully
                        RandomAccessFile save = reader;
                        reader = new RandomAccessFile(file, "r");
                        position = 0;
                        // close old file explicitly rather than relying on GC picking up previous RAF
                        IOUtils.closeQuietly(save);
                    } catch (FileNotFoundException e) {
                        // in this case we continue to use the previous reader and position values
                        listener.fileNotFound();
                    }
                    continue;
                } else {

                    // File was not rotated

                    // See if the file needs to be read again
                    if (length > position) {

                        // The file has more content than it did last time
                        last = System.currentTimeMillis();
                        position = readLines(reader);

                    } else if (FileUtils.isFileNewer(file, last)) {

                        /* This can happen if the file is truncated or overwritten
                         * with the exact same length of information. In cases like
                         * this, the file position needs to be reset
                         */
                        position = 0;
                        reader.seek(position); // cannot be null here

                        // Now we can read new lines
                        last = System.currentTimeMillis();
                        position = readLines(reader);
                    }
                }
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException e) {
                }
            }

        } catch (Exception e) {

            listener.handle(e);

        } finally {
            IOUtils.closeQuietly(reader);
        }
    }

    /**
     * Allows the tailer to complete its current loop and return.
     */
    public void stop() {
        this.run = false;
    }

    /**
     * Read new lines.
     *
     * @param reader The file to read
     * @return The new position after the lines have been read
     * @throws java.io.IOException if an I/O error occurs.
     */
    private long readLines(RandomAccessFile reader) throws IOException {
        int read = reader.read(buffer);
        boolean eof = read < bufferSize;
        readLinesFromBuffer(read);
        while (!eof) {
            read = reader.read(buffer);
            eof = read < bufferSize;
            readLinesFromBuffer(read);
        }
        return reader.getFilePointer();
    }

    /**
     * Read lines from the buffer of given size.
     *
     * @param size The buffer size.
     * @throws java.io.IOException if an I/O error occurs.
     */
    private void readLinesFromBuffer(int size) throws IOException {
        int read = readLineFromBuffer(0, size);
        int pos = read;
        while (pos < size) {
            read = readLineFromBuffer(pos, size);
            pos += read;
        }
    }

    /**
     * Version of readline() that reads from the buffer and returns null on EOF rather than a partial line.
     *
     * @param start the buffer starting index.
     * @param size The buffer size.
     * @return Number of bytes read.
     * @throws java.io.IOException if an I/O error occurs.
     */
    private int readLineFromBuffer(int start, int size) throws IOException {
        StringBuilder builder = new StringBuilder(remaind);
        int read = 0;
        int current = start;
        int ch = 0;
        boolean eol = false;
        boolean seenCR = false;
        while (current < size && !eol) {
            ch = buffer[current++];
            read++;
            switch (ch) {
                case '\n':
                    eol = true;
                    break;
                case '\r':
                    seenCR = true;
                    break;
                default:
                    if (seenCR) {
                        builder.append('\r');
                        seenCR = false;
                    }
                    builder.append((char) ch); // add character, not its ascii value
            }
        }
        if (!eol) {
            remaind = builder.toString();
        } else {
            remaind = "";
            listener.handle(builder.toString());
        }
        return read;
    }

}
