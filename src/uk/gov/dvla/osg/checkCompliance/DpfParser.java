package uk.gov.dvla.osg.checkCompliance;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.univocity.parsers.common.processor.ConcurrentRowProcessor;
import com.univocity.parsers.common.processor.RowListProcessor;
import com.univocity.parsers.tsv.TsvParser;
import com.univocity.parsers.tsv.TsvParserSettings;
import com.univocity.parsers.tsv.TsvWriter;
import com.univocity.parsers.tsv.TsvWriterSettings;

import uk.gov.dvla.osg.common.classes.Customer;
import uk.gov.dvla.osg.common.config.InsertLookup;

public class DpfParser {
	private static final Logger LOGGER = LogManager.getLogger();
	//Input variables
	private String inputFile;
	private String outputFile;
	private AppConfig appConfig;
	// Throughput variables
	private String[] headers;

	/**
	 * Extracts DocumentProperties from a dpf data file.
	 * @param inputFile dpf input file
	 * @param outputFile dpf output file
	 * @param appConfig supplies column names for mapping
	 */
	public DpfParser (String inputFile, String outputFile) {
		this.inputFile = inputFile;
		this.outputFile = outputFile;
		this.appConfig = AppConfig.getInstance();
	}
	
	/**
	 * Reads the input file and maps each row to a customer object.
	 * @return a list of customers
	 */
	public ArrayList<Customer> Load() {
		ArrayList<Customer> customers = new ArrayList<>();
		
		// Keep track of customer number so we can output in same order
		AtomicInteger counter = new AtomicInteger(0);
		TsvParser parser = createParser();
		parser.parseAllRecords(new File(inputFile)).forEach(record -> {
			
			Customer customer = new Customer(counter.getAndIncrement());
			customer.setDocRef(record.getString(appConfig.getDocumentReference()));
			customer.setSortField(record.getString(appConfig.getSortField()));
			customer.setSelectorRef(record.getString(appConfig.getAppNameField()));
			customer.setLang(record.getString(appConfig.getLanguageField()));
			customer.setStationery(record.getString(appConfig.getStationeryField()));
			customer.setBatchType(record.getString(appConfig.getBatchTypeField()));
			customer.setSubBatch(record.getString(appConfig.getSubBatchTypeField()));
			customer.setFleetNo(record.getString(appConfig.getFleetNoField()));
			Integer groupId = record.getString(appConfig.getGroupIdField()).equals("")?null:record.getInt(appConfig.getGroupIdField());
			customer.setGroupId(groupId);
			customer.setPaperSize(record.getString(appConfig.getPaperSizeField()));
			customer.setMsc(record.getString(appConfig.getMscField()));
			
			customer.setName1(record.getString(appConfig.getName1Field()));
			customer.setName2(record.getString(appConfig.getName2Field()));
			customer.setAdd1(record.getString(appConfig.getAddress1Field()));
			customer.setAdd2(record.getString(appConfig.getAddress2Field()));
			customer.setAdd3(record.getString(appConfig.getAddress3Field()));
			customer.setAdd4(record.getString(appConfig.getAddress4Field()));
			customer.setAdd5(record.getString(appConfig.getAddress5Field()));
			customer.setPostcode(record.getString(appConfig.getPostCodeField()));
			
			customer.setDps(record.getString(appConfig.getDpsField()));
			customer.setInsertRef(record.getString(appConfig.getInsertField()));
			customer.setMmCustomerContent(record.getString(appConfig.getMailMarkBarcodeCustomerContent()));
			customer.setNoOfPages(record.getInt(appConfig.getNoOfPagesField()));
			customer.setSot(record.getString(appConfig.getSotField()));
			customer.setEog(record.getString(appConfig.getEogField()));
			customer.setPresentationPriority(record.getInt(appConfig.getPresentationPriorityField()));
			customers.add(customer);
		});
		headers = parser.getRecordMetadata().headers();
		return customers;
	}
	
	/**
	 * Saves the dpf file with the amended document properties.
	 * @param customers the amended customer data
	 * @throws IOException unable to write output file to the supplied path
	 */
	/**
	 * @param customers
	 * @throws IOException
	 */
	public void Save(ArrayList<Customer> customers) throws IOException {
		try (FileWriter fw = new FileWriter(new File(outputFile))) {
			// Create an instance of TsvWriter with the default settings
			TsvWriter writer = new TsvWriter(fw, new TsvWriterSettings());
			// Writes the file headers
			writer.writeHeaders(headers);
			// Keep track of which customer we are writing
			AtomicInteger counter = new AtomicInteger(0);
			// Build a parser that loops through the original dpf file
			TsvParser parser = createParser();
			parser.parseAll(new File(inputFile)).forEach(record -> {
				// Write out the original row of data
				writer.addValues((Object[]) record);
				// Replace changed values
				Customer customer = customers.get(counter.getAndIncrement());
			
				try {
					writer.addValue(appConfig.getSiteField(), customer.getSite());
				} catch (Exception ex) {
					LOGGER.fatal("Site Field {}", appConfig.getSiteField());
				}
				
				try {
					writer.addValue(appConfig.getEogField(), customer.getEog());
				} catch (Exception ex) {
					LOGGER.fatal("EOG Field {}", appConfig.getEogField());
				}
				
				try {
					writer.addValue(appConfig.getSotField(), customer.getSot());
				} catch (Exception ex) {
					LOGGER.fatal("SOT Field {}", appConfig.getSotField());
				}
				
				try {
					writer.addValue(appConfig.getMailMarkBarcodeContent(), customer.getMmBarcodeContent());
				} catch (Exception ex) {
					LOGGER.fatal("MailMarkBarcodeContent {}", appConfig.getMailMarkBarcodeContent());
				}
				
				try {
					writer.addValue(appConfig.getSequenceInChild(), customer.getSequenceInChild());
				} catch (Exception ex) {
					LOGGER.fatal("Sequence In Child {}", appConfig.getSequenceInChild());
				}
				
				try {
					writer.addValue(appConfig.getOuterEnvelope(), customer.getEnvelope());
				} catch (Exception ex) {
					LOGGER.fatal("Outer Envelope {}", appConfig.getOuterEnvelope());
				}
				
				try {
					writer.addValue(appConfig.getMailingProduct(), customer.getProduct());
				} catch (Exception ex) {
					LOGGER.fatal("Mailing Product {}", appConfig.getMailingProduct());
				}
				
				try {
					writer.addValue(appConfig.getBatchTypeField(), customer.getBatchName());
				} catch (Exception ex) {
					LOGGER.fatal("Batch Type {}", appConfig.getBatchTypeField());
				}
				try {
					writer.addValue(appConfig.getTotalNumberOfPagesInGroupField(), customer.getTotalPagesInGroup());
				} catch (Exception ex) {
					LOGGER.fatal("Total Number Of Pages In Group {}", appConfig.getTotalNumberOfPagesInGroupField());
				}
				try {
					if (StringUtils.isNotBlank(customer.getInsertRef())) {
						String insertRef = customer.getInsertRef();
						int hopperCode = InsertLookup.getInstance().getLookup().get(insertRef).getHopperCode();						
						writer.addValue(appConfig.getInsertHopperCodeField(), hopperCode);
					}
				} catch (Exception ex) {
					LOGGER.fatal("Insert Hopper Code {}", appConfig.getInsertHopperCodeField());
				}
				try {
					writer.addValue(appConfig.getMscField(), customer.getMsc());
				} catch (Exception ex) {
					LOGGER.fatal("MSC {}", appConfig.getMscField());
				}
				try {
					String weightAndSize = customer.getWeight()+"|"+customer.getThickness();
					writer.addValue(appConfig.getweightAndSizeField(), weightAndSize);
				} catch (Exception ex) {
					LOGGER.fatal("Weight & Size {}", appConfig.getweightAndSizeField());
				}
				try {
					writer.addValue(appConfig.getPresentationPriorityField(), customer.getPresentationPriority());
				} catch (Exception ex) {
					LOGGER.fatal("Presentation Priority {}", appConfig.getPresentationPriorityField());
				}
				try {
					writer.addValue(appConfig.getDpsField(), customer.getDps());
				} catch (Exception ex) {
					LOGGER.fatal("DPS {}", appConfig.getDpsField());
				}
				writer.writeValuesToRow();
			});
			// Flushes and closes the writer
			writer.close();
		}
	}
	
	/**
	 * Create a new instance of a TsvParser
	 * @return A TsvParser set to handle header rows
	 */
	private TsvParser createParser() {
		TsvParserSettings parserSettings = new TsvParserSettings();
		parserSettings.setNullValue("");
		parserSettings.setProcessor(new ConcurrentRowProcessor(new RowListProcessor()));
		parserSettings.setLineSeparatorDetectionEnabled(true);
		parserSettings.setHeaderExtractionEnabled(true);
		return new TsvParser(parserSettings);
	}
	
	
}
