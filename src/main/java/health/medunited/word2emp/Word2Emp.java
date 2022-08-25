package health.medunited.word2emp;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.util.GregorianCalendar;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.datatype.DatatypeFactory;

import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.hwpf.usermodel.Paragraph;
import org.apache.poi.hwpf.usermodel.Range;
import org.apache.poi.hwpf.usermodel.Table;
import org.apache.poi.hwpf.usermodel.TableRow;

import health.medunited.bmp.Block;
import health.medunited.bmp.Ersteller;
import health.medunited.bmp.Medikation;
import health.medunited.bmp.MedikationsPlan;
import health.medunited.bmp.Patient;

public class Word2Emp {

    private static final Pattern NAME_PATTERN = Pattern.compile("Name, Vorname:.(.*), (.*).Geburtsdatum:.(\\d?\\d)\\.(\\d?\\d)\\.(\\d\\d\\d\\d).Seite:.*", Pattern.DOTALL);
    public static void main(String[] args) {
        try (DirectoryStream<Path> paths = Files.newDirectoryStream(Paths.get("../secret-medications-plans"), "*.doc")) {
            for (Path entry: paths) {
                System.out.println(entry.toString());
                HWPFDocument document=new HWPFDocument(new FileInputStream(entry.toFile()));

                MedikationsPlan medikationsPlan = new MedikationsPlan();
                medikationsPlan.setVersionsnummer("026");
                medikationsPlan.setLccs("ISO 3166-1");
                medikationsPlan.setInstanzId(UUID.randomUUID().toString().replaceAll("-", "").toUpperCase());
                medikationsPlan.setSprachLaenderkennzeichen("de-DE");
                medikationsPlan.setSo("eGK");
                medikationsPlan.setLlcs("ISO 639-1");
                medikationsPlan.setIv("1.6");
                medikationsPlan.setOid("1.2.276.0.76.7.7");
                medikationsPlan.setOn("eMP/AMTS");

                Ersteller ersteller = new Ersteller();
                ersteller.setName("Manuel Blechschmidt");
                ersteller.setEMail("manuel.blechschmidt@incentergy.de");
                ersteller.setStrasse("Achenseeweg 50");
                ersteller.setTelefon("01736322621");
                ersteller.setPostleitzahl("12209");
                ersteller.setOrt("Berlin");
                ersteller.setErstelldatum(DatatypeFactory.newDefaultInstance().newXMLGregorianCalendar(new GregorianCalendar()));
                medikationsPlan.setErsteller(ersteller);

                Patient patient = new Patient();
                medikationsPlan.setPatient(patient);
                document.getTextTable().getText();

                
                WordExtractor extractor = new WordExtractor(document);
                String footerText = extractor.getFooterText();
                Matcher m = NAME_PATTERN.matcher(footerText);
                DecimalFormat df = new DecimalFormat("00");

                if(m.matches()) {
                    patient.setVorname(m.group(2));
                    patient.setNachname(m.group(1));
                    patient.setGeburtsdatum(df.format(Integer.parseInt(m.group(5)))+df.format(Integer.parseInt(m.group(4)))+m.group(3));
                }
                patient.setGeschlecht("X");
                patient.setSd(false);
                patient.setVersichertenId("A000000000");

                Range range = document.getRange();
                int numParagraphs = range.numParagraphs();
                Table t = null;
                for(int i=0;i<numParagraphs;i++) {
                    Paragraph p = range.getParagraph(i);
                    if(p.isInTable()) {
                        t = range.getTable(p);
                        extractTableDataIntoMedicationPlan(t, medikationsPlan, "Dauermedikation");
                        break;
                    }
                }
                if(t != null) {
                    for(int i=t.getEndOffset();i<numParagraphs;i++) {
                        Paragraph p = range.getParagraph(i);
                        if(p.isInTable()) {
                            t = range.getTable(p);
                            extractTableDataIntoMedicationPlan(t, medikationsPlan, "Bedarfsmedikation");
                            break;
                        }
                    }
                }



                extractor.close();
                ByteArrayOutputStream stream = new ByteArrayOutputStream();
                JAXBContext.newInstance(MedikationsPlan.class).createMarshaller().marshal(medikationsPlan, stream);
                
                String s = new String(stream.toByteArray());
                Files.write(Paths.get(patient.getNachname()+"-"+patient.getVorname()+".xml"), s.getBytes());
                // System.out.println(s);
                HttpClient client = HttpClient.newBuilder().build();
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create("https://medicationplan.med-united.health/medicationPlanPdf"))
                        .header("Content-Type", "application/xml; charset=UTF-8")
                        .header("Accept", "application/pdf")
                        .POST(BodyPublishers.ofString(s))
                        .build();

                HttpResponse<?> response = client.send(request, BodyHandlers.ofFile(Paths.get(patient.getNachname()+"-"+patient.getVorname()+".pdf")));
                System.out.println(response.statusCode());

                break;
            }
        } catch (IOException | JAXBException | InterruptedException e) {
            e.printStackTrace();
        }
    }
    private static void extractTableDataIntoMedicationPlan(Table t, MedikationsPlan medikationsPlan, String blockName) {
        Block dauermedikation = new Block();
        dauermedikation.setZwischenueberschriftFreitext(blockName);
        medikationsPlan.getBlock().add(dauermedikation);
        for(int i = 0;i<t.numRows();i++) {
            if(i==0) {
                continue;
            }
            TableRow tableRow = t.getRow(i);
            Medikation medikation = new Medikation();

            String verordnungsDatum = tableRow.getCell(0).text();
            if(!verordnungsDatum.matches("\\d?\\d\\.\\d?\\d\\.\\d\\d\\d\\d.*")) {
                continue;
            }

            String medicationText = tableRow.getCell(1).text();
            if(medicationText == null || medicationText.equals("")) {
                continue;
            }

            medikation.setA(medicationText.replaceAll("[\\x{0}-\\x{8}]|[\\x{B}-\\x{C}]|[\\x{E}-\\x{1F}]|[\\x{D800}-\\x{DFFF}]|[\\x{FFFE}-\\x{FFFF}]", ""));
            medikation.setM(tableRow.getCell(3).text().replaceAll("[\\x{0}-\\x{8}]|[\\x{B}-\\x{C}]|[\\x{E}-\\x{1F}]|[\\x{D800}-\\x{DFFF}]|[\\x{FFFE}-\\x{FFFF}]", ""));
            medikation.setD(tableRow.getCell(4).text().replaceAll("[\\x{0}-\\x{8}]|[\\x{B}-\\x{C}]|[\\x{E}-\\x{1F}]|[\\x{D800}-\\x{DFFF}]|[\\x{FFFE}-\\x{FFFF}]", ""));
            medikation.setV(tableRow.getCell(5).text().replaceAll("[\\x{0}-\\x{8}]|[\\x{B}-\\x{C}]|[\\x{E}-\\x{1F}]|[\\x{D800}-\\x{DFFF}]|[\\x{FFFE}-\\x{FFFF}]", ""));
            medikation.setV(tableRow.getCell(6).text().replaceAll("[\\x{0}-\\x{8}]|[\\x{B}-\\x{C}]|[\\x{E}-\\x{1F}]|[\\x{D800}-\\x{DFFF}]|[\\x{FFFE}-\\x{FFFF}]", ""));
            dauermedikation.getMedikationFreitextRezeptur().add(medikation);
        }
    }
}
