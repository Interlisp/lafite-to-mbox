package org.interlisp;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/*
 * Convert Lafite mail files to mbox format.
 *
 * Copyright 2025 by Herb Jellinek.  All rights reserved.
 */
public class Main {

    private static final String START = "*start*";

    private static final Pattern LENGTHS_AND_FLAGS_PATTERN = Pattern.compile("(\\d{5}) (\\d{5}) ([UD])([SU]).$");

    private static final int NL_LENGTH = 1;

    private final Logger log = LoggerFactory.getLogger(getClass());

    private static class Args {
        @Parameter(names = {"--file"})
        private String file;

        @Parameter(names = {"--dir"})
        private String dir;
    }

    private record LengthsAndFlags(int messageLength, int stampLength, char deleted, char seen) {

        private LengthsAndFlags(String messageLengthStr, String stampLengthStr, String deletedStr, String seenStr) {
            this(Integer.parseInt(messageLengthStr), Integer.parseInt(stampLengthStr),
                    deletedStr.charAt(0), seenStr.charAt(0));
        }

    }

    public static void main(String[] args) {
        final Args programArgs = new Args();
        JCommander.newBuilder().addObject(programArgs).build().parse(args);

        final Main main = new Main();
        main.process(programArgs);
    }

    private void process(Args programArgs) {
        if (programArgs.file != null && programArgs.dir != null) {
            log.error("Can't handle both 'dir' and 'file'. Pick one.");
            System.exit(1);
        }

        if (programArgs.file != null) {
            processFile(programArgs.file);
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
     *     The file looks like
     * </p>
     * <pre>
     * *start*
     * 01659 00024 UU
     * headers
     * body
     * </pre>
     *
     * @param lafiteFile the name of the Lafite input file
     */
    private void processFile(String lafiteFile) {
        log.info("Converting {}", lafiteFile);

        try (final BufferedReader br =
                     new BufferedReader(new InputStreamReader(new FileInputStream(lafiteFile), StandardCharsets.US_ASCII))) {

            int messages = 0;
            while (true) {
                String line = br.readLine();
                if (line == null) {
                    // we're done!
                    log.info("Processed {} message(s)", messages);
                    return;
                }
                if (!START.equals(line)) {
                    log.error("Expected {}, got {}", START, line);
                    throw new IllegalStateException(START+" not found");
                }

                line = br.readLine();
                final Matcher lengthsAndFlagsMatcher = LENGTHS_AND_FLAGS_PATTERN.matcher(line);
                if (!lengthsAndFlagsMatcher.matches()) {
                    log.error("Expected lengths and flags, got {}", line);
                    throw new IllegalStateException("Lengths and flags not found: "+line);
                }
                final LengthsAndFlags lengthsAndFlags = new LengthsAndFlags(lengthsAndFlagsMatcher.group(1), lengthsAndFlagsMatcher.group(2),
                        lengthsAndFlagsMatcher.group(3), lengthsAndFlagsMatcher.group(4));

                checkDeleted(lengthsAndFlags);
                checkSeen(lengthsAndFlags);

                int charsRead = lengthsAndFlags.stampLength + 2 * NL_LENGTH; // for the two stamp lines
                while (charsRead < lengthsAndFlags.messageLength) {
                    line = br.readLine();
                    charsRead += line.length() + NL_LENGTH;
                    if (false) {
                        log.info("> '{}'", line);
                    }
                }

                br.readLine(); // skip the final line

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