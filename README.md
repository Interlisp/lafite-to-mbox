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
