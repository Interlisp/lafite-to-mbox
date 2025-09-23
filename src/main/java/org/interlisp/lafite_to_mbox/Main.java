package org.interlisp.lafite_to_mbox;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import org.interlisp.lafite_to_mbox.io.LafiteIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/*
 * Convert Laurel/Lafite mail files to mbox format.
 *
 * Copyright 2025, Interlisp.org.  All rights reserved.
 */
public class Main {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private static final boolean DEBUG = false;

    private static final String PROGRAM_NAME = Main.class.getPackageName();

    private static final String START = "*start*";

    /**
     * The format of the line that contains the message length and seen and deleted flags.
     */
    private static final Pattern LENGTHS_AND_FLAGS_PATTERN = Pattern.compile("(\\d{5}) (\\d{5}) ([UD])([SU]).$");

    /**
     * Extract the Format: header's value.
     */
    private static final Pattern FORMAT_PATTERN = Pattern.compile("Format: *(.*)$", Pattern.CASE_INSENSITIVE);

    private static final String TEDIT_MIME_TYPE = "application/vnd.xerox.tedit";

    private static final String UNKNOWN_MIME_TYPE = "application/octet-stream";

    private static final String PLAiN_TEXT_MIME_TYPE = "text/plain";

    private static final String TEDIT_FORMAT_LC = "tedit";

    private static final String TEXT_FORMAT = "text";


    /**
     * How many characters terminate an input line?
     */
    private static final int NEWLINE_LENGTH = 1;

    /**
     * Control-J, AKA linefeed.
     */
    private static final int NEWLINE = 10;

    private static class Args {
        @Parameter(names = {"--laurel"})
        private String laurelFile;

        @Parameter(names = {"--mbox"})
        private String mboxFile;

        @Parameter(names = {"--dir"})
        private String dir;
    }

    /**
     * Record containing a messages lengths and flags.
     *
     * @param messageLength the message length
     * @param stampLength   the stamp length (included in <tt>messageLength</tt>
     * @param deleted       is the message marked as deleted?
     * @param seen          is the message marked as having been seen?
     */
    private record LengthsAndFlags(int messageLength, int stampLength, char deleted, char seen) {

        /**
         * Convert native formats to Java primitive types.
         *
         * @param messageLengthStr the message length as a string
         * @param stampLengthStr   the stamp length as a string
         * @param deletedStr       is the message marked as deleted? as a string
         * @param seenStr          is the message marked as having been seen? as a string
         */
        private LengthsAndFlags(String messageLengthStr, String stampLengthStr, String deletedStr, String seenStr) {
            this(Integer.parseInt(messageLengthStr), Integer.parseInt(stampLengthStr),
                    deletedStr.charAt(0), seenStr.charAt(0));
        }

    }

    /**
     * Do the work.
     *
     * @param args raw command line arguments
     */
    public static void main(String[] args) {
        final Args programArgs = new Args();
        JCommander.newBuilder().addObject(programArgs).build().parse(args);

        final Main main = new Main();
        main.process(programArgs);
    }

    private void process(Args programArgs) {
        if (programArgs.laurelFile != null && programArgs.dir != null) {
            log.error("Can't handle both 'dir' and 'laurel'. Pick one.");
            System.exit(1);
        }

        if (programArgs.laurelFile != null && programArgs.mboxFile != null) {
            processFile(programArgs.laurelFile, programArgs.mboxFile);
        } else if (programArgs.dir != null) {
            log.warn("Not implemented yet");
        } else {
            log.warn("Nothing to do");
        }
    }

    /**
     * Convert a Lafite mail file tto mbox format.  The mbox file will have the same name
     * as the Lafite file, with the ".mbox" suffix.
     * <p>
     * The file looks like
     * </p>
     * <pre>
     * *start*
     * 01659 00024 UU
     * headers
     * body
     * </pre>
     *
     * @param lafiteFile the name of the Lafite input file
     * @param mboxFile   the name of the mbox output file
     */
    private void processFile(String lafiteFile, String mboxFile) {
        log.info("Converting {} to {}", lafiteFile, mboxFile);

        try (final FileInputStream fis = new FileInputStream(lafiteFile);
             final FileOutputStream fos = new FileOutputStream(mboxFile)) {

            final LafiteIO io = new LafiteIO(fis, fos);

            int messages = 0;

            while (true) {
                LafiteIO.LineStatus status = io.readLine();
                if (status.isEof()) {
                    // we're done!
                    log.info("Processed {} message(s)", messages);
                    return;
                }
                if (!START.equals(status.getChars())) {
                    log.error("Expected {}, got {}", START, status.getChars());
                    throw new IllegalStateException(START + " not found");
                }

                status = io.readLine();
                final Matcher lengthsAndFlagsMatcher = LENGTHS_AND_FLAGS_PATTERN.matcher(status.getChars());
                if (!lengthsAndFlagsMatcher.matches()) {
                    log.error("Expected lengths and flags, got {}", status.getChars());
                    throw new IllegalStateException("Lengths and flags not found: " + status.getChars());
                }
                final LengthsAndFlags lengthsAndFlags = new LengthsAndFlags(lengthsAndFlagsMatcher.group(1), lengthsAndFlagsMatcher.group(2),
                        lengthsAndFlagsMatcher.group(3), lengthsAndFlagsMatcher.group(4));

                checkDeleted(lengthsAndFlags);
                checkSeen(lengthsAndFlags);

                int charsRead = lengthsAndFlags.stampLength; // for the two stamp lines

                String bodyFormat = TEXT_FORMAT;

                io.writeLine("From " + PROGRAM_NAME + " " + new Date());

                // read the headers
                while (charsRead < lengthsAndFlags.messageLength) {
                    status = io.readLine();
                    charsRead += status.getCharsRead() + NEWLINE_LENGTH;
                    final String line = status.getChars();
                    final Matcher formatMatch = FORMAT_PATTERN.matcher(line);
                    if (formatMatch.matches()) {
                        log.info("Format is {}", formatMatch.group(1));
                        bodyFormat = formatMatch.group(1).toLowerCase();
                        writeContentHeader(fos, bodyFormat);
                    } else if (status.getCharsRead() == 0) {
                        // finished reading the headers
                        if (TEDIT_FORMAT_LC.equals(bodyFormat)) {
                            // the message body contains binary data so copy it verbatim
                            copyMessageRemainder(fis, fos, lengthsAndFlags.messageLength - charsRead);
                            io.writeLine();
                            break;
                        }
                    } else {
                        io.writeLine(line);
                    }

                    if (DEBUG) {
                        log.info("> '{}'", line);
                    }
                }

                io.writeLine();

                if (charsRead > lengthsAndFlags.messageLength + 1) { // don't count the final newline
                    log.error("Read too far: charsRead = {}, should be {}", charsRead, lengthsAndFlags.messageLength);
                    throw new IllegalStateException("Read too far");
                }

                messages++;
            }

        } catch (IOException ie) {
            log.error("", ie);
        }
    }

    private void copyMessageRemainder(FileInputStream fis, FileOutputStream fos, int byteCount) throws IOException {
        final byte[] copyBuffer = new byte[byteCount];
        final int bytesRead = fis.read(copyBuffer);
        if (bytesRead != byteCount) {
            throw new IOException("Expected "+byteCount+" bytes, read "+bytesRead+" bytws");
        }
        fos.write(copyBuffer);
    }

    private void writeContentHeader(FileOutputStream os, String formatName) throws IOException {
        os.write("Content-Type: ".getBytes());
        final String contentType =
                switch (formatName.toLowerCase()) {
                    case TEDIT_FORMAT_LC -> TEDIT_MIME_TYPE;
                    case TEXT_FORMAT -> PLAiN_TEXT_MIME_TYPE;
                    default -> UNKNOWN_MIME_TYPE;
                };
        os.write(contentType.getBytes());
        os.write(NEWLINE);
    }

    private void checkSeen(LengthsAndFlags lengthsAndFlags) {
        if (lengthsAndFlags.seen != 'S' && lengthsAndFlags.seen != 'U') {
            log.warn("Seen flag = '{}'", lengthsAndFlags.seen);
        }
    }

    private void checkDeleted(LengthsAndFlags lengthsAndFlags) {
        if (lengthsAndFlags.deleted != 'D' && lengthsAndFlags.deleted != 'U') {
            log.warn("Deleted flag = '{}'", lengthsAndFlags.deleted);
        }
    }
}