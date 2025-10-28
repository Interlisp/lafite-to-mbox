# Convert Laurel/Lafite mail files to Unix mbox format

The [Xerox PARC Laurel mailer](https://xeroxalto.computerhistory.org/Indigo/DMS/Laurel/6/Manual/.Laurel6.press!1.pdf)
stored messages in files in an idiosyncratic format.  The format is discussed informally [here](https://github.com/orgs/Interlisp/discussions/1551) and
realized in Interlisp-D code [here](https://xeroxparcarchive.computerhistory.org/eros/speech/mailreader/.PROSE-LAFITE-MESSAGES!4.html) (search for
`PARSEMAILFOLDER1`).  There's a sample [here](https://xeroxparcarchive.computerhistory.org/_cd8_/laurel/Tutorial.mail!1).

The format of the stamp field of a Laurel message is
*stamp* <c.r.> <length.of.message.in.5.ascii.chars> <sp> <length.of.stamp.in.5.ascii.chars> <sp> <the.char.U.or.D> 
<the.char.S.or.U> <any.char> <c.r.>

U.or.D is Undeleted or Deleted 
S.or.U is Seen or Unseen

The Interlisp-D Lafite mail client retained the Laurel file format.

[The `mbox` format](https://en.wikipedia.org/wiki/Mbox) came later.  It's widely implemented in mail clients and email
archive and forensics tools like [ePADD](https://www.epaddproject.org).

This software will convert Laurel files into mbox format.

To run it, you need Java (JRE) 21 or later installed.

## Convert a single Laurel file to mbox format

Run the program with arguments `--laurel` for the Laurel file and `--mbox` for the output MBox file,
as in this example.  The Java app is in the JAR file `LafiteToMBox-1.0-SNAPSHOT-fat.jar`, assumed to be in the
current directory.

```bash
$ java -jar LafiteToMBox-1.0-SNAPSHOT-fat.jar --laurel data/laurel/Tutorial.mail --mbox /tmp/Tutorial.mbox
```

You'll see output like
```
14:34:45.348 [main] INFO org.interlisp.lafite_to_mbox.Main -- Converting data/laurel/Tutorial.mail to /tmp/Tutorial.mbox
14:34:45.386 [main] INFO org.interlisp.lafite_to_mbox.Main -- Processed 17 message(s)
```
The result is written to the file `/tmp/Tutorial.mbox`.

## Convert all Laurel files in a directory to mbox format

Run the program with arguments `--indir` for the directory holding the Laurel files and `--outdir` for the
directory which is to hold the output mbox files, as in this example.

The program will attempt to convert every Laurel/Lafite file ending in '.mail' residing in `data/toBeConverted` into an
mbox file in `out/converted` with the same name plus `.mbox`.  For example, an input file
`data/toBeConverted/Sample.mail` will be converted to `out/converted/Sample.mail.mbox`.

```bash
$ java -jar LafiteToMBox-1.0-SNAPSHOT-fat.jar --indir data/toBeConverted --outdir out/converted
```

## Debugging output

You can induce the software to write low-level progress messages to the console with the `--debug`
argument.  `--debug headers` will log information about the message headers, `--debug body` will log
information about message bodies, and `--debug flags` will log any undocumented message status flags
encountered.  You can combine the options, e.g., `-- debug body,flags`.

## Building the software

Execute the Gradle `fatJar` task.  For example:

```
$ ./gradlew faatJar
```

The result will be `build/libs/LafiteToMBox-1.0-SNAPSHOT.jar`.
