package health.medunited.word2emp;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
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
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;

import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.hwpf.usermodel.Paragraph;
import org.apache.poi.hwpf.usermodel.Range;
import org.apache.poi.hwpf.usermodel.Table;
import org.apache.poi.hwpf.usermodel.TableRow;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import health.medunited.bmp.Block;
import health.medunited.bmp.Ersteller;
import health.medunited.bmp.MeTyp;
import health.medunited.bmp.Medikation;
import health.medunited.bmp.MedikationsPlan;
import health.medunited.bmp.Patient;

public class Word2Emp {
	
	private static Logger log = Logger.getLogger(Word2Emp.class.getName());

    private static final Pattern NAME_PATTERN = Pattern.compile("Name, Vorname:.(.*), (.*).Geburtsdatum:.(\\d?\\d)\\.(\\d?\\d)\\.(\\d\\d\\d\\d).Seite:.*", Pattern.DOTALL);
    
    private static final SimpleDateFormat GERMAN_DATE_FORMAT = new SimpleDateFormat("dd.MM.yyyy");
    private static final SimpleDateFormat GERMAN_DATE_FORMAT_SHORT = new SimpleDateFormat("dd.MM.yy");
    private static final SimpleDateFormat MEDIKATIONSPLAN_DATE_FORMAT = new SimpleDateFormat("yyyyMMdd");

    public static void main(String[] args) {
        List<MedikationsPlan> medikationsPlaene = new ArrayList<>();
    	try (DirectoryStream<Path> paths = Files.newDirectoryStream(Paths.get("../secret-medications-plans"), "*.doc")) {
            for (Path entry: paths) {
                try {
	            	log.info(entry.toString());
	                MedikationsPlan medikationsPlan = createMedikationPlanForPath(entry);
	                String fileNameWithouSuffix = medikationsPlan.getPatient().getNachname()+"-"+medikationsPlan.getPatient().getVorname();
	                
	                medikationsPlaene.add(medikationsPlan);
	
	        		String s = medikationsPlan2XmlString(medikationsPlan);
	        		
					Files.write(Paths.get(fileNameWithouSuffix+".xml"), s.getBytes());
	        		// log.info(s);
	        		medikationsplanXml2PdfFile(fileNameWithouSuffix, s);
	        		
	
	                // break;
                } catch (IOException | JAXBException | InterruptedException e) {
        			log.log(Level.SEVERE, "Could not convert Medikationsplan", e);
                }
            }
        } catch (IOException e) {
			log.log(Level.SEVERE, "Could not convert Medikationsplan", e);
        }
    	createExcelFromMedikationsPlaene(medikationsPlaene);
    }
	private static void createExcelFromMedikationsPlaene(List<MedikationsPlan> medikationsPlaene) {
		XSSFWorkbook workbook = new XSSFWorkbook();
		XSSFSheet spreadsheet = workbook.createSheet("Medikamente");
		
		CellStyle dateStyle = workbook.createCellStyle();
		CreationHelper createHelper = workbook.getCreationHelper();
		dateStyle.setDataFormat(
		    createHelper.createDataFormat().getFormat("d.m.yyyy"));
		
		int rowid = 0;
		int cellid = 0;
		Row row = spreadsheet.createRow(rowid++);
		
		Cell cell = row.createCell(cellid++);
        cell.setCellValue("Vorordnungsdatum");
        cell = row.createCell(cellid++);
        cell.setCellValue("Patient-Nachname");
        cell = row.createCell(cellid++);
        cell.setCellValue("Patient-Vorname");
        cell = row.createCell(cellid++);
        cell.setCellValue("Patient-Geburtsdatum");
        cell = row.createCell(cellid++);
        cell.setCellValue("Wirkstoff");
        cell = row.createCell(cellid++);
        cell.setCellValue("Morgens");
        cell = row.createCell(cellid++);
        cell.setCellValue("Mittags");
        cell = row.createCell(cellid++);
        cell.setCellValue("Abends");
        cell = row.createCell(cellid++);
        cell.setCellValue("Nachts");
        cell = row.createCell(cellid++);
        cell.setCellValue("Darreichungsform");
        cell = row.createCell(cellid++);
        cell.setCellValue("Hinweis");
        cell = row.createCell(cellid++);
        cell.setCellValue("Grund");
        cell = row.createCell(cellid++);
        cell.setCellValue("Gruppe");
        
        for(MedikationsPlan medikationsPlan : medikationsPlaene) {
        	for(Block block : medikationsPlan.getBlock()) {
        		for(MeTyp meUntyp : block.getMedikationFreitextRezeptur()) {
        			Medikation meTyp = (Medikation) meUntyp;
        			cellid = 0;
        			row = spreadsheet.createRow(rowid++);
        			cell = row.createCell(cellid++);
        			if(meTyp.getIed()!= null) {
        				cell.setCellValue(meTyp.getIed().toGregorianCalendar());
        			}
					cell.setCellStyle(dateStyle);
        	        cell = row.createCell(cellid++);
        	        cell.setCellValue(medikationsPlan.getPatient().getNachname());
        	        cell = row.createCell(cellid++);
        	        cell.setCellValue(medikationsPlan.getPatient().getVorname());
        	        cell = row.createCell(cellid++);
        	        try {
        	        	Date birthdate = MEDIKATIONSPLAN_DATE_FORMAT.parse(medikationsPlan.getPatient().getGeburtsdatum());
						cell.setCellValue(birthdate);
						cell.setCellStyle(dateStyle);
					
					} catch (ParseException e) {
						log.log(Level.SEVERE, "Could not parse geburtsdatum of patient file", e);
					}
        	        cell = row.createCell(cellid++);
        	        cell.setCellValue(meTyp.getA());
        	        cell = row.createCell(cellid++);
        	        cell.setCellValue(meTyp.getM());
        	        cell = row.createCell(cellid++);
        	        cell.setCellValue(meTyp.getD());
        	        cell = row.createCell(cellid++);
        	        cell.setCellValue(meTyp.getV());
        	        cell = row.createCell(cellid++);
        	        cell.setCellValue(meTyp.getH());
        	        cell = row.createCell(cellid++);
        	        cell.setCellValue(meTyp.getFd());
        	        cell = row.createCell(cellid++);
        	        cell.setCellValue(meTyp.getI());
        	        cell = row.createCell(cellid++);
        	        cell.setCellValue(meTyp.getR());
        	        cell = row.createCell(cellid++);
        	        cell.setCellValue(block.getZwischenueberschriftFreitext());
        		}
        	}
        }
        
		
		try (FileOutputStream out = new FileOutputStream(
	            new File("Medikamente.xlsx"))) {			
			workbook.write(out);
		} catch (IOException e) {
			log.log(Level.SEVERE, "Could not write file", e);
		}
 

		
	}
	private static void medikationsplanXml2PdfFile(String fileNameWithouSuffix, String s)
			throws IOException, InterruptedException {
		HttpClient client = HttpClient.newBuilder().build();
		HttpRequest request = HttpRequest.newBuilder()
		        .uri(URI.create("https://medicationplan.med-united.health/medicationPlanPdf"))
		        .header("Content-Type", "application/xml; charset=UTF-8")
		        .header("Accept", "application/pdf")
		        .POST(BodyPublishers.ofString(s))
		        .build();

		HttpResponse<?> response = client.send(request, BodyHandlers.ofFile(Paths.get(fileNameWithouSuffix+".pdf")));
		log.info(""+response.statusCode());
	}
	private static String medikationsPlan2XmlString(MedikationsPlan medikationsPlan) throws JAXBException {
		ByteArrayOutputStream stream = new ByteArrayOutputStream();
		Marshaller marshaller = JAXBContext.newInstance(MedikationsPlan.class).createMarshaller();
		marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
		marshaller.marshal(medikationsPlan, stream);
		
		String s = new String(stream.toByteArray());
		return s;
	}
	private static MedikationsPlan createMedikationPlanForPath(Path entry)
			throws IOException, FileNotFoundException, JAXBException, InterruptedException {
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
		int startOfTable = 0;
		for(int i=0;i<numParagraphs;i++) {
			startOfTable = i;
		    Paragraph p = range.getParagraph(i);
		    if(p.isInTable()) {
		        t = range.getTable(p);
		        extractTableDataIntoMedicationPlan(t, medikationsPlan, "Dauermedikation");
		        break;
		    }
		}
		if(t != null) {
		    for(int i=t.numParagraphs()+startOfTable;i<numParagraphs;i++) {
		        Paragraph p = range.getParagraph(i);
		        if(p.isInTable()) {
		            t = range.getTable(p);
		            extractTableDataIntoMedicationPlan(t, medikationsPlan, "Bedarfsmedikation");
		            break;
		        }
		    }
		}

		extractor.close();
		return medikationsPlan;
		
	}
    private static void extractTableDataIntoMedicationPlan(Table t, MedikationsPlan medikationsPlan, String blockName) {
        Block block = new Block();
        block.setZwischenueberschriftFreitext(blockName);
        
        if("Dauermedikation".equals(blockName)) {
        	block.setZwischenueberschrift("412");
        }
        if("Bedarfsmedikation".equals(blockName)) {
        	block.setZwischenueberschrift("411");
        }
        medikationsPlan.getBlock().add(block);
        for(int i = 0;i<t.numRows();i++) {
            if(i==0) {
                continue;
            }
            TableRow tableRow = t.getRow(i);
            Medikation medikation = new Medikation();

            String verordnungsDatum = tableRow.getCell(0).text();
            Date ied = null;
            if(!verordnungsDatum.matches("\\d?\\d\\.\\d?\\d\\.\\d?\\d?\\d\\d.*")) {
                continue;
            } else {
            	try {
            		if(verordnungsDatum.matches("\\d?\\d\\.\\d?\\d\\.\\d\\d\\d\\d.*")) {
            			ied = GERMAN_DATE_FORMAT.parse(verordnungsDatum);
            		} else if(verordnungsDatum.matches("\\d?\\d\\.\\d?\\d\\.\\d\\d.*")) {
            			ied = GERMAN_DATE_FORMAT_SHORT.parse(verordnungsDatum);            			
            		}
				} catch (ParseException e) {
					log.log(Level.SEVERE, "Could not parse date", e);
				}
            }

            String medicationText = tableRow.getCell(1).text();
            if(medicationText == null || medicationText.equals("")) {
                continue;
            }

            medikation.setA(medicationText.replaceAll("[\\x{0}-\\x{8}]|[\\x{B}-\\x{C}]|[\\x{E}-\\x{1F}]|[\\x{D800}-\\x{DFFF}]|[\\x{FFFE}-\\x{FFFF}]", ""));
            if("Dauermedikation".equals(blockName)) {
	            medikation.setFd(tableRow.getCell(2).text().replaceAll("[\\x{0}-\\x{8}]|[\\x{B}-\\x{C}]|[\\x{E}-\\x{1F}]|[\\x{D800}-\\x{DFFF}]|[\\x{FFFE}-\\x{FFFF}]", ""));
	            medikation.setM(tableRow.getCell(3).text().replaceAll("[\\x{0}-\\x{8}]|[\\x{B}-\\x{C}]|[\\x{E}-\\x{1F}]|[\\x{D800}-\\x{DFFF}]|[\\x{FFFE}-\\x{FFFF}]", ""));
	            medikation.setD(tableRow.getCell(4).text().replaceAll("[\\x{0}-\\x{8}]|[\\x{B}-\\x{C}]|[\\x{E}-\\x{1F}]|[\\x{D800}-\\x{DFFF}]|[\\x{FFFE}-\\x{FFFF}]", ""));
	            medikation.setV(tableRow.getCell(5).text().replaceAll("[\\x{0}-\\x{8}]|[\\x{B}-\\x{C}]|[\\x{E}-\\x{1F}]|[\\x{D800}-\\x{DFFF}]|[\\x{FFFE}-\\x{FFFF}]", ""));
	            medikation.setH(tableRow.getCell(6).text().replaceAll("[\\x{0}-\\x{8}]|[\\x{B}-\\x{C}]|[\\x{E}-\\x{1F}]|[\\x{D800}-\\x{DFFF}]|[\\x{FFFE}-\\x{FFFF}]", ""));
            	if(tableRow.numCells() > 9) {            		
            		String absetzdatum = tableRow.getCell(9).text().replaceAll("[\\x{0}-\\x{8}]|[\\x{B}-\\x{C}]|[\\x{E}-\\x{1F}]|[\\x{D800}-\\x{DFFF}]|[\\x{FFFE}-\\x{FFFF}]", "");
            		medikation.setI((!"".equals(absetzdatum) ? ". Absetzdatum: "+absetzdatum+"." : null));
            	}
            }
            if("Bedarfsmedikation".equals(blockName)) {
            	String einzeldosis = tableRow.getCell(2).text().replaceAll("[\\x{0}-\\x{8}]|[\\x{B}-\\x{C}]|[\\x{E}-\\x{1F}]|[\\x{D800}-\\x{DFFF}]|[\\x{FFFE}-\\x{FFFF}]", "");
            	String maxDosis24h = tableRow.getCell(3).text().replaceAll("[\\x{0}-\\x{8}]|[\\x{B}-\\x{C}]|[\\x{E}-\\x{1F}]|[\\x{D800}-\\x{DFFF}]|[\\x{FFFE}-\\x{FFFF}]", "");
            	String absetzdatum = tableRow.getCell(7).text().replaceAll("[\\x{0}-\\x{8}]|[\\x{B}-\\x{C}]|[\\x{E}-\\x{1F}]|[\\x{D800}-\\x{DFFF}]|[\\x{FFFE}-\\x{FFFF}]", "");
            	medikation.setI("Einzeldosis: "+einzeldosis+". Max Dosis 24 Stunden: "+maxDosis24h+(!"".equals(absetzdatum) ? ". Absetzdatum: "+absetzdatum+"." : ""));
            }
            if(ied != null) {
	            XMLGregorianCalendar iedCalendar = DatatypeFactory.newDefaultInstance().newXMLGregorianCalendar(dateToCalendar(ied));
	            medikation.setIed(iedCalendar);
            }
            block.getMedikationFreitextRezeptur().add(medikation);
        }
    }
	
        
    private static GregorianCalendar dateToCalendar(Date date) {

		Calendar calendar = Calendar.getInstance();
		calendar.setTime(date);
		return (GregorianCalendar) calendar;
	}
}
