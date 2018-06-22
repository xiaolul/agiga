package edu.jhu.agiga;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.zip.GZIPOutputStream;

import edu.jhu.agiga.AgigaConstants.DependencyForm;

/**
 * Command line tools for printing human-readable versions of the XML
 * annotations.
 *
 * @author mgormley
 */
public class AgigaPrinter {

    private static Logger log = Logger.getLogger(AgigaPrinter.class.getName());

    public static void main(String args[]) throws Exception {
        Util.initializeLogging();
        //LogManager.getLogManager().
        // Initialize Log4j
//        String log4jProperty = System.getProperty("log4j.configuration");
//        if (log4jProperty == null) {
//            ConsoleAppender cAppender = new ConsoleAppender(new PatternLayout("%d{HH:mm:ss,SSS} [%t] %p %c %x - %m%n"),
//                    "System.err");
//            BasicConfigurator.configure(cAppender);
//            // Must be Level.TRACE for debug logging
//            Logger.setLevel(Level.INFO);
//        } else {
//            // Ensure that we pick up the log4j.properties file if present
//            PropertyConfigurator.configure(log4jProperty);
//        }

        // Create usage string
        String usage = "\nusage: java " + AgigaPrinter.class.getName() + " <type> <gzipped input file>"
                + "\n  where <type> is one of:";
        String[][] options = new String[][]{
                {"words", "Words only, one sentence per line"},
                {"lemmas", "Lemmas only, one sentence per line"},
                {"pos", "Part-of-speech tags"},
                {"ner", "Named entity types"},
                {"basic-deps", "Basic dependency parses in CONNL-X format"},
                {"col-deps", "Collapsed dependency parses in CONNL-X format"},
                {"col-ccproc-deps", "Collapsed and propagated dependency parses in CONNL-X format"},
                {"phrase-structure", "Phrase structure parses"},
                {"coref", "Coreference resolution as SGML similar to MUC"},
                {"stanford-deps", "toString() methods of Stanford dependency parse annotations"},
                {"stanford-phrase-structure", "toString() method of Stanford phrase structure parses"},
                {"headlines", "Headlines and Datelines"},
                {"for-testing-only", "**For use in testing this API only**"},
                {"trectext", "TREC-format text without anyannotation"}};
        for (String[] pair : options) {
            usage += String.format("\n    %-25s (%s)", pair[0], pair[1]);
        }
        usage += "\n  and where <gzipped input file> is an .xml.gz file";
        usage += "\n  from Annotated Gigaword";

        log.info("Testing");
        // Check for correct args
        if (args.length < 2) {
            log.severe(usage);
            System.exit(1);
        }
        String type = args[0];
        String inputFile = args[1];
        String outPutDir = args[2];

        // Print
        Writer writer = new PrintWriter(System.out, true);
        if (type.equals("words")) {
            printWords(inputFile, writer);
        } else if (type.equals("lemmas")) {
            printLemmas(inputFile, writer);
        } else if (type.equals("pos")) {
            printPos(inputFile, writer);
        } else if (type.equals("ner")) {
            printNer(inputFile, writer);
        } else if (type.equals("basic-deps")) {
            printDeps(inputFile, writer, DependencyForm.BASIC_DEPS);
        } else if (type.equals("col-deps")) {
            printDeps(inputFile, writer, DependencyForm.COL_DEPS);
        } else if (type.equals("col-ccproc-deps")) {
            printDeps(inputFile, writer, DependencyForm.COL_CCPROC_DEPS);
        } else if (type.equals("phrase-structure")) {
            printPhraseStructure(inputFile, writer);
        } else if (type.equals("coref")) {
            printCoref(inputFile, writer);
        } else if (type.equals("stanford-deps")) {
            printStanfordDeps(inputFile);
        } else if (type.equals("stanford-phrase-structure")) {
            printStanfordPhraseStructure(inputFile);
        } else if (type.equals("headlines")) {
            printHeadlineDateline(inputFile, writer);
        } else if (type.equals("for-testing-only")) {
            printForTestingOnly(inputFile, writer);
        } else if (type.equals("trectext")) {
            printTRECText(inputFile, outPutDir);
        } else {
            log.severe("Printer type not recognized: " + type);
            log.severe(usage);
            System.exit(1);
        }
        writer.flush();
    }

    private static void printWords(String inputFile, Writer writer) throws IOException {
        // Only read the words
        AgigaPrefs prefs = new AgigaPrefs();
        prefs.setAll(false);
        prefs.setWord(true);
        // Iterate through the sentences, printing each one to stdout
        StreamingSentenceReader reader = new StreamingSentenceReader(inputFile, prefs);
        log.info("Parsing XML for file: " + reader.getFileId());
        for (AgigaSentence sent : reader) {
            sent.writeWords(writer);
        }
        log.info("Number of docs: " + reader.getNumDocs());
        log.info("Number of sentences: " + reader.getNumSents());
    }

    private static String checkAvialbility(String content) {
        if (content == null ||
                content.trim().equalsIgnoreCase("") ||
                content.trim().equalsIgnoreCase("null"))
            return "";
        else
            return content.trim();
    }

    private static void printTRECText(String inputDir, String outputDir) throws IOException {
        //Create Gzip writer
        List<File> files =
                Files.walk(Paths.get(inputDir)).filter(Files::isRegularFile).
                        map(Path::toFile).collect(Collectors.toList());
        ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(16);
        for (File f : files) {
            //start multi-threading
            Runnable parser = () -> {
                FileOutputStream output = null;
                Writer writer = null;
                try {
                    output = new FileOutputStream(outputDir + Paths.get(f.getName()).getFileName());
                    writer = new OutputStreamWriter(new GZIPOutputStream(output), "UTF-8");
                } catch (IOException e) {
                    e.printStackTrace();
                }
                if (writer != null) {
                    //
                    AgigaPrefs prefs = new AgigaPrefs();
                    prefs.setAll(false);
                    prefs.setWord(true);
                    StreamingDocumentReader reader = new StreamingDocumentReader(f.getAbsolutePath(), prefs);
                    StringBuilder sb = new StringBuilder();
                    for (AgigaDocument doc : reader) {
                        String title = checkAvialbility(doc.getHeadline());
                        String date = checkAvialbility(doc.getDateline());
                        String type = checkAvialbility(doc.getType());
                        sb.append("<DOC>\n").
                                append("<DOCNO>").
                                append(doc.getDocId()).
                                append("</DOCNO>\n").
                                append("<TITLE>").
                                append(title).
                                append("</TITLE>\n").
                                append("<DATE>").
                                append(date).
                                append("</DATE>\n").
                                append("<TYPE>").
                                append(type).
                                append("</TYPE>\n").
                                append("<TEXT>\n");
                        for (AgigaSentence sent : doc.getSents()) {
                            for (AgigaToken token : sent.getTokens()) {
                                if (token.getWord().equalsIgnoreCase("-LRB-"))
                                    sb.append("( ");
                                else if (token.getWord().equalsIgnoreCase("-RRB-"))
                                    sb.append(") ");
                                else {
                                    String currToken = token.getWord();
                                    currToken = currToken.replaceAll("\\\\/"," ");
                                    sb.append(currToken).append(" ");
                                }
                            }
                        }
                        sb.append("\n</TEXT>\n").append("</DOC>\n\n");
                        try {
                            writer.write(sb.toString());
                        } catch (IOException e) {
                            e.printStackTrace();
                        }

                        sb.setLength(0);
                    }
                    try {
                        writer.close();
                        output.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            };
            //
            executor.submit(parser);
        }
        System.out.println("Maximum threads inside pool " + executor.getMaximumPoolSize());
        executor.shutdown();
    }

    private static void printLemmas(String inputFile, Writer writer) throws IOException {
        // Only read the lemmas
        AgigaPrefs prefs = new AgigaPrefs();
        prefs.setAll(false);
        prefs.setLemma(true);
        // Iterate through the sentences, printing each one to stdout
        StreamingSentenceReader reader = new StreamingSentenceReader(inputFile, prefs);
        log.info("Parsing XML for file: " + reader.getFileId());
        for (AgigaSentence sent : reader) {
            sent.writeLemmas(writer);
        }
        log.info("Number of docs: " + reader.getNumDocs());
        log.info("Number of sentences: " + reader.getNumSents());
    }

    private static void printPos(String inputFile, Writer writer) throws IOException {
        // Only read the words and POS tags
        AgigaPrefs prefs = new AgigaPrefs();
        prefs.setAll(false);
        prefs.setWord(true);
        prefs.setPos(true);
        // Iterate through the sentences, printing each one to stdout
        StreamingSentenceReader reader = new StreamingSentenceReader(inputFile, prefs);
        log.info("Parsing XML for file: " + reader.getFileId());
        for (AgigaSentence sent : reader) {
            sent.writePosTags(writer);
        }
        log.info("Number of sentences: " + reader.getNumSents());
    }

    private static void printNer(String inputFile, Writer writer) throws IOException {
        // Only read the words and NER tags
        AgigaPrefs prefs = new AgigaPrefs();
        prefs.setAll(false);
        prefs.setWord(true);
        prefs.setNer(true);
        // Iterate through the sentences, printing each one to stdout
        StreamingDocumentReader reader = new StreamingDocumentReader(inputFile, prefs);
        log.info("Parsing XML for file: " + reader.getFileId());
        for (AgigaDocument doc : reader) {
            for (AgigaSentence sent : doc.getSents()) {
                sent.writeNerTags(writer);
            }
        }
        log.info("Number of sentences: " + reader.getNumSents());
    }

    private static void printDeps(String inputFile, Writer writer, DependencyForm form) throws IOException {
        // Only read what's needed for CONNL-X style output
        AgigaPrefs prefs = new AgigaPrefs();
        prefs.setForConnlStyleDeps(form);
        // Iterate through the sentences, printing each one to stdout
        StreamingSentenceReader reader = new StreamingSentenceReader(inputFile, prefs);
        log.info("Parsing XML");
        for (AgigaSentence sent : reader) {
            sent.writeConnlStyleDeps(writer, form);
        }
        log.info("Number of sentences: " + reader.getNumSents());
    }

    private static void printPhraseStructure(String inputFile, Writer writer) throws IOException {
        // Only read the parse text
        AgigaPrefs prefs = new AgigaPrefs();
        prefs.setAll(false);
        prefs.setParse(true);
        // Iterate through the sentences, printing each one to stdout
        StreamingSentenceReader reader = new StreamingSentenceReader(inputFile, prefs);
        log.info("Parsing XML");
        for (AgigaSentence sent : reader) {
            sent.writeParseText(writer);
        }
        log.info("Number of sentences: " + reader.getNumSents());
    }

    private static void printCoref(String inputFile, Writer writer) throws IOException {
        // Only read the coref and the words
        AgigaPrefs prefs = new AgigaPrefs();
        prefs.setAll(false);
        prefs.setWord(true);
        prefs.setCoref(true);
        // Iterate through the sentences, printing each one to stdout
        StreamingDocumentReader reader = new StreamingDocumentReader(inputFile, prefs);
        log.info("Parsing XML");
        for (AgigaDocument doc : reader) {
            doc.writeMucStyleCoref(writer);
        }
        log.info("Number of docs: " + reader.getNumDocs());
    }

    private static void printStanfordDeps(String inputFile) {
        // Only read the words, lemmas, tags
        AgigaPrefs prefs = new AgigaPrefs();
        prefs.setAll(false);
        prefs.setWord(true);
        prefs.setLemma(true);
        prefs.setPos(true);
        prefs.setDeps(DependencyForm.BASIC_DEPS);
        prefs.setDeps(DependencyForm.COL_DEPS);
        prefs.setDeps(DependencyForm.COL_CCPROC_DEPS);
        // Iterate through the sentences, printing each one to stdout
        StreamingSentenceReader reader = new StreamingSentenceReader(inputFile, prefs);
        log.info("Parsing XML");
        for (AgigaSentence sent : reader) {
            // Print out all the dependency forms
            System.out.println(sent.getStanfordWordLemmaTags());
            System.out.println("---");
            System.out.println(sent.getStanfordTreeGraphNodes(DependencyForm.BASIC_DEPS));
            System.out.println(sent.getStanfordTreeGraphNodes(DependencyForm.COL_DEPS));
            System.out.println(sent.getStanfordTreeGraphNodes(DependencyForm.COL_CCPROC_DEPS));
            System.out.println("---");
            System.out.println(sent.getStanfordTypedDependencies(DependencyForm.BASIC_DEPS));
            System.out.println(sent.getStanfordTypedDependencies(DependencyForm.COL_DEPS));
            System.out.println(sent.getStanfordTypedDependencies(DependencyForm.COL_CCPROC_DEPS));
            System.out.println();
        }
        log.info("Number of sentences: " + reader.getNumSents());
    }

    private static void printStanfordPhraseStructure(String inputFile) {
        // Only read the words, lemmas, tags
        AgigaPrefs prefs = new AgigaPrefs();
        prefs.setAll(false);
        prefs.setParse(true);
        // Iterate through the sentences, printing each one to stdout
        StreamingSentenceReader reader = new StreamingSentenceReader(inputFile, prefs);
        log.info("Parsing XML");
        for (AgigaSentence sent : reader) {
            System.out.println(sent.getStanfordContituencyTree());
        }
        log.info("Number of sentences: " + reader.getNumSents());
    }

    private static void printHeadlineDateline(String inputFile, Writer writer) throws IOException {
        // Read everything
        AgigaPrefs prefs = new AgigaPrefs();
        prefs.setAll(false);
        prefs.setHeadline(true);
        prefs.setDateline(true);
        // Iterate through the docs, printing each one to stdout
        StreamingDocumentReader dReader = new StreamingDocumentReader(inputFile, prefs);
        log.info("Parsing XML for file: " + dReader.getFileId());
        for (AgigaDocument doc : dReader) {
            log.info("Parsing doc: id=" + doc.getDocId() + " type=" + doc.getType());
            if (doc.getHeadline() != null) {
                System.out.println("HEADLINE: " + doc.getHeadline());
            }
            if (doc.getDateline() != null) {
                System.out.println("DATELINE: " + doc.getDateline());
            }
        }
        log.info("Number of docs: " + dReader.getNumDocs());
    }

    private static void printForTestingOnly(String inputFile, Writer writer) throws IOException {
        // Read everything
        AgigaPrefs prefs = new AgigaPrefs();
        prefs.setAll(true);
        // Iterate through the sentences, printing each one to stdout
        StreamingSentenceReader sReader = new StreamingSentenceReader(inputFile, prefs);
        log.info("Parsing XML for file: " + sReader.getFileId());
        for (AgigaSentence sent : sReader) {
            printAllSentenceAnnotations(writer, sent);
        }
        log.info("Number of sentences: " + sReader.getNumDocs());
        log.info("Number of sentences: " + sReader.getNumSents());

        // Iterate through the docs, printing each one to stdout
        StreamingDocumentReader dReader = new StreamingDocumentReader(inputFile, prefs);
        log.info("Parsing XML for file: " + dReader.getFileId());
        for (AgigaDocument doc : dReader) {
            log.info("Parsing doc: id=" + doc.getDocId() + " type=" + doc.getType());
            if (doc.getHeadline() != null) {
                log.info("Found headline: " + doc.getHeadline());
            }
            if (doc.getDateline() != null) {
                log.info("Found dateline: " + doc.getDateline());
            }
            for (AgigaSentence sent : doc.getSents()) {
                printAllSentenceAnnotations(writer, sent);
            }
            doc.writeMucStyleCoref(writer);
        }
        log.info("Number of docs: " + dReader.getNumDocs());
    }

    private static void printAllSentenceAnnotations(Writer writer, AgigaSentence sent) throws IOException {
        log.info("Printing sent: id=" + sent.getSentIdx());
        sent.writeWords(writer);
        sent.writePosTags(writer);
        sent.writeNerTags(writer);
        sent.writeTags(writer, true, true, true);
        for (DependencyForm df : DependencyForm.values()) {
            sent.writeConnlStyleDeps(writer, df);
        }
        sent.writeParseText(writer);

        for (AgigaToken tok : sent.getTokens()) {
            System.out.print(tok.getTokIdx() + " ");
        }
        System.out.println();

        System.out.println(sent.getStanfordWordLemmaTags());
        for (DependencyForm df : DependencyForm.values()) {
            System.out.println(sent.getStanfordTreeGraphNodes(df));
            System.out.println(sent.getStanfordTypedDependencies(df));
        }
        System.out.println(sent.getStanfordContituencyTree());
    }
}
