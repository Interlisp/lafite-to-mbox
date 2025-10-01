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

    private static final boolean DEBUG_HEADERS = false;

    private static final boolean DEBUG_BODY = false;

    private static final boolean DEBUG_UNDOCUMENTED_FLAGS = false;

    private static final String PROGRAM_NAME = Main.class.getPackageName();

    private static final String START = "*start*";

    /**
     * The format of the line that contains the message length and seen and deleted flags.
     */
    private static final Pattern LENGTHS_AND_FLAGS_PATTERN = Pattern.compile("(\\d{5}) (\\d{5}) ([UD])([SU])(.)$");

    /**
     * Extract the Format: header's value.
     */
    private static final Pattern FORMAT_PATTERN = Pattern.compile("Format: *(.*)$", Pattern.CASE_INSENSITIVE);

    private static final String TEDIT_MIME_TYPE = "application/vnd.interlisp.tedit";

    private static final String UNKNOWN_MIME_TYPE = "application/octet-stream";

    private static final String PLAiN_TEXT_MIME_TYPE = "text/plain; charset=x-xerox-xccs";

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
     * @param fixed         did we manually fix the message?  Or another, undocumented value.
     */
    private record LengthsAndFlags(int messageLength, int stampLength, char deleted, char seen, char fixed) {

        /**
         * Convert native formats to Java primitive types.
         *
         * @param messageLengthStr the message length as a string
         * @param stampLengthStr   the stamp length as a string
         * @param deletedStr       is the message marked as deleted? as a string
         * @param seenStr          is the message marked as having been seen? as a string
         * @param fixedStr         did we manually fix the message?  Or another, undocumented value.
         */
        private LengthsAndFlags(String messageLengthStr, String stampLengthStr, String deletedStr, String seenStr,
                                String fixedStr) {
            this(Integer.parseInt(messageLengthStr), Integer.parseInt(stampLengthStr),
                    deletedStr.charAt(0), seenStr.charAt(0), fixedStr.charAt(0));
        }

        /**
         * Return true if the flag, supposedly always a space character, has a value that's not space
         * or 'F' for "fixed."
         *
         * @return true if the flag has an undocumented value
         */
        private boolean isUndocumentedFlag() {
            return fixed != 'F' && fixed != ' ';
        }

        /**
         * Return true if the "fixed" flag is set.
         *
         * @return true if the "fixed" flag is set
         */
        private boolean isFixed() {
            return fixed == 'F';
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
            try {
                processFile(programArgs.laurelFile, programArgs.mboxFile);
            } catch (Throwable t) {
                log.error("Error converting " + programArgs.laurelFile, t);
            }
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
     * 01659 00024 UUF
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

            // loop over all messages in the Lafite file
            while (true) {
                // read the stamp
                LafiteIO.LineStatus status = io.readLine();
                if (status.isEof()) {
                    // we're done!
                    log.info("Processed {} message(s)", messages);
                    return;
                }

                if (!START.equals(status.getChars())) {
                    log.error("Expected '{}', got '{}'", START, status.getChars());
                    throw new IllegalStateException(START + " not found");
                }
                status = io.readLine();
                final Matcher lengthsAndFlagsMatcher = LENGTHS_AND_FLAGS_PATTERN.matcher(status.getChars());
                if (!lengthsAndFlagsMatcher.matches()) {
                    log.error("Expected lengths and flags, got '{}'", status.getChars());
                    throw new IllegalStateException("Lengths and flags not found: " + status.getChars());
                }
                final LengthsAndFlags lengthsAndFlags = new LengthsAndFlags(lengthsAndFlagsMatcher.group(1), lengthsAndFlagsMatcher.group(2),
                        lengthsAndFlagsMatcher.group(3), lengthsAndFlagsMatcher.group(4), lengthsAndFlagsMatcher.group(5));

                checkDeleted(lengthsAndFlags);
                checkSeen(lengthsAndFlags);
                if (DEBUG_UNDOCUMENTED_FLAGS && lengthsAndFlags.isUndocumentedFlag()) {
                    log.info("Message {} has undocumented flag '{}'", messages, lengthsAndFlags.fixed);
                }
                final int messageLength = lengthsAndFlags.messageLength;

                int charsRead = lengthsAndFlags.stampLength; // for the two stamp lines

                // the default body format is text
                String bodyFormat = TEXT_FORMAT;

                // write the mandatory mbox message delimiter
                io.writeLine("From " + PROGRAM_NAME + " " + new Date());

                // read the headers and copy them to the output. replace the TEdit format line if found.
                while (charsRead < messageLength) {
                    status = io.readLine(messageLength - charsRead - 1);
                    charsRead += status.getCharsRead() + NEWLINE_LENGTH;
                    final String line = status.getChars();
                    if (DEBUG_HEADERS) {
                        log.info("Header> '{}'", line);
                    }
                    final Matcher formatMatch = FORMAT_PATTERN.matcher(line);
                    if (formatMatch.matches()) {
                        if (DEBUG_HEADERS) {
                            log.info("Format is {}", formatMatch.group(1));
                        }
                        bodyFormat = formatMatch.group(1).toLowerCase();
                    } else if (status.getCharsRead() == 0) {
                        // we've finished reading the headers. if not TEdit, write the text content header
                        writeContentHeader(fos, bodyFormat);
                        writeFixedHeader(fos, lengthsAndFlags.isFixed());
                        break;
                    } else {
                        // copy the header, whatever it is
                        io.writeLine(line);
                    }
                }

                // write a blank line between headers and body
                io.writeLine();

                // read the body.  If format = TEdit, copy the message body verbatim.
                // if not TEdit, read the text and terminate on reading up to the length
                if (isBinary(bodyFormat)) {
                    copyMessageRemainder(fis, fos, messageLength - charsRead);
                } else {
                    // copy a line at a time, converting the end-of-line characters
                    while (charsRead < messageLength) {
                        status = io.readLine(messageLength - charsRead - 1);
                        charsRead += status.getCharsRead() + NEWLINE_LENGTH;
                        final String line = status.getChars();
                        if (DEBUG_BODY) {
                            log.info("> '{}'", line);
                        }

                        io.writeLine(line);
                    }
                }

                if (charsRead > messageLength + 1) { // don't count the final newline
                    log.error("Read too far: charsRead = {}, should be {}", charsRead, messageLength);
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
            throw new IOException("Expected " + byteCount + " bytes, read " + bytesRead + " bytws");
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

    private void writeFixedHeader(FileOutputStream os, boolean messageWasFixed) throws IOException {
        if (messageWasFixed) {
            os.write("X-Message-Repaired: true".getBytes());
            os.write(NEWLINE);
        }
    }

    /**
     * Is the format non-text?
     *
     * @param format the format, as found in the Format: header
     * @return true if it's a binary format, false otherwise
     */
    private boolean isBinary(String format) {
        return !TEXT_FORMAT.equals(format);
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