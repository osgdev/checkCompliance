package uk.gov.dvla.osg.checkCompliance;

import static org.apache.commons.lang3.StringUtils.*;

import java.io.IOException;
import java.util.ArrayList;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import uk.gov.dvla.osg.common.classes.Customer;
import uk.gov.dvla.osg.common.classes.Selector;
import uk.gov.dvla.osg.common.config.*;

public class Main {

	private static final Logger LOGGER = LogManager.getLogger();

	private static final int EXPECTED_NUMBER_OF_ARGS = 4;
	// Input variables
	private static String inputFile;
	private static String outputFile;
	private static String propsFile;
	private static String runNo;

	public static void main(String[] args) throws Exception {
		
		LOGGER.debug("Check Compliance Started");
		try {
			// Process args
			setArgs(args);
			// Load files
			LOGGER.debug("Load config file...");
			AppConfig.init(propsFile);
			LOGGER.debug("Load DPF records...");
			DpfParser dpf = new DpfParser(inputFile, outputFile);
			ArrayList<Customer> customers = dpf.Load();
			LOGGER.debug("Load lookup files...");
			loadLookupFiles(customers);
			// Set presentation priority for all records
			LOGGER.debug("Set presentation priorities...");
			setPresentationPriorities(customers);
			
			// set batch types
			ComplianceChecker cc = new ComplianceChecker(customers, runNo);
			cc.checkMscGroups();
			cc.calculateDPSCompliance();
			cc.calculateActualMailProduct();
			cc.writeComplianceReportFile(AppConfig.getInstance().getMailmarkCompliancePath());
			
			// Check weights and sizes
			LOGGER.debug("Calculating Weights & Sizes...");
			CalculateWeightsAndSizes cws = new CalculateWeightsAndSizes(customers);
			cws.calculate();
			
			// save to new file
			LOGGER.debug("Saving DPF as {}", outputFile);
			dpf.Save(customers);
		} catch (IOException e) {
			LOGGER.fatal(e.getMessage());
			System.exit(1);
		}

	}

	/**
	 * Validate and set command-line arguments
	 * @param args
	 */
	private static void setArgs(String[] args) {

		if (args.length != EXPECTED_NUMBER_OF_ARGS) {
			LOGGER.fatal("Incorrect number of args parsed {} expected {}", args.length, EXPECTED_NUMBER_OF_ARGS);
			System.exit(1);
		}
		inputFile = args[0];
		outputFile = args[1];
		propsFile = args[2];
		runNo = args[3];
	}
	
/*	private static AppConfig loadPropertiesFile() throws Exception {
		ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
		// Tell mapper to set private variables
		mapper.setVisibility(PropertyAccessor.FIELD, Visibility.ANY);
		return mapper.readValue(new File(propsFile), AppConfig.class);
	}*/
	
	/**
	 * Set Production Config using the Selector Lookup file and the selector type that was set in the dpf.
	 * @param appConfig
	 * @param customers
	 * @throws IOException
	 */
	private static void loadLookupFiles(ArrayList<Customer> customers) throws IOException {
			
			AppConfig appConfig = AppConfig.getInstance();
			
			SelectorLookup.init(AppConfig.getInstance().getLookupFile());
			Selector selector = SelectorLookup.getInstance().getLookup().get(customers.get(0).getSelectorRef());
						
			ProductionConfiguration.init(appConfig.getProductionConfigPath() 
					+ selector.getProductionConfig()
					+ appConfig.getProductionFileSuffix());	
			
			PostageConfiguration.init(appConfig.getPostageConfigPath()
					+ selector.getPostageConfig() 
					+ appConfig.getPostageFileSuffix());
			
			PresentationConfiguration.init(appConfig.getPresentationPriorityConfigPath()
					+ selector.getPresentationConfig()
					+ appConfig.getPresentationPriorityFileSuffix());			
			
			InsertLookup.init(appConfig.getInsertLookup());
			StationeryLookup.init(appConfig.getStationeryLookup());
			EnvelopeLookup.init(appConfig.getEnvelopeLookup());
			PapersizeLookup.init(appConfig.getPapersizeLookup());
	}

	/**
	 * Sets the presentation priority for each customer. Looks up the batch type in the configuration file and returns the sequence number. If batch type is not present the priority is set to 999.
	 * @param customers
	 */
	private static void setPresentationPriorities(ArrayList<Customer> customers) {
		customers.forEach(customer -> {
			String batchComparator = isBlank(customer.getSubBatch()) 
					? customer.getBatchName()
					: customer.getBatchName() + "_" + customer.getSubBatch();
			customer.setPresentationPriority(PresentationConfiguration.getInstance().lookupRunOrder(batchComparator));
		});
	}

}
