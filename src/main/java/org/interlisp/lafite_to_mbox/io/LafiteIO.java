/*
 *
 * Copyright 2025 by Herb Jellinek.  All rights reserved.
 *
 */
package org.interlisp.lafite_to_mbox.io;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Read and write lines from/to a Laurel file.
 */
public class LafiteIO {

    public static final int NEWLINE = 10;

    public static final int CR = 13;

    private final InputStream is;

    private final OutputStream os;

    public LafiteIO(InputStream is, OutputStream os) throws IOException {
        this.is = is;
        this.os = os;
    }

    /**
     * Read a line of text from a Laurel file {@link InputStream} into a {@link StringBuffer} supplied by the caller.
     * Consume and discard any end-of-line character that may follow.
     * Return a {@link LineStatus} that includes the number of chars read
     *
     * @param noMoreThan limit on the number of characters read
     * @return the number of characters read
     */
    public LineStatus readLine(int noMoreThan) throws IOException {
        final StringBuffer sb = new StringBuffer();
        int charsRead = 0;
        while (charsRead <= noMoreThan) {
            final int ch = is.read();
            if (ch == -1) {
                return new LineStatus(true, sb);
            } else if (isLineEnd(ch)) {
                return new LineStatus(false, sb);
            } else {
                sb.appendCodePoint(ch);
                charsRead++;
            }
        }
        return new LineStatus(true, sb);
    }

    /**
     * Read a line of text from a Laurel file {@link InputStream} into a {@link StringBuffer} supplied by the caller.
     * Consume and discard any end-of-line character that may follow.
     * Return a {@link LineStatus} that includes the number of chars read
     *
     * @return the number of characters read
     */
    public LineStatus readLine() throws IOException {
        return readLine(Integer.MAX_VALUE);
    }

    /**
     * Read ahead this many bytes.
     *
     * @param numBytes how many bytes to read and discard
     * @throws IOException              if the bad thing happens
     * @throws IllegalArgumentException if we ask for a position behind the current one, or past the EOF
     */
    public void skipAhead(int numBytes) throws IOException {
        for (int i = 0; i < numBytes; i++) {
            final int byteRead = is.read();
            if (byteRead == -1) {
                throw new IOException("Read past EOF");
            }
        }
    }

    /**
     * Write the string to the {@link OutputStream} and follow it by a newline.
     *
     * @param s the string
     * @throws IOException if there was an output problem
     */
    public void writeLine(String s) throws IOException {
        write(s);
        os.write(NEWLINE);
    }

    /**
     * Write a string without a newline.
     *
     * @param s the string
     * @throws IOException if there was an output problem
     */
    public void write(String s) throws IOException {
        os.write(s.getBytes(), 0, s.length());
    }

    /**
     * Write a newline to the {@link OutputStream}.
     *
     * @throws IOException if there was an output problem
     */
    public void writeLine() throws IOException {
        os.write(NEWLINE);
    }

    private static boolean isLineEnd(int ch) {
        return ch == NEWLINE || ch == CR;
    }

    public static class LineStatus {
        private final boolean eof;

        private final String chars;

        public LineStatus(boolean eof, StringBuffer chars) {
            this.eof = eof;
            this.chars = chars.toString();
        }

        public boolean isEof() {
            return eof;
        }

        public String getChars() {
            return chars;
        }

        public int getCharsRead() {
            return chars.length();
        }

        @Override
        public String toString() {
            return "LineStatus{" +
                    "eof=" + eof +
                    ", chars='" + chars + '\'' +
                    ", charsRead=" + chars.length() +
                    '}';
        }
    }

}
