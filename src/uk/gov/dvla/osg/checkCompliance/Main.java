package uk.gov.dvla.osg.checkCompliance;

import static org.apache.commons.lang3.StringUtils.*;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
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

        LOGGER.debug("---- Check Compliance Started ----");
        try {
            // Process args
            setArgs(args);
            // Load files
            LOGGER.trace("Load config file...");
            AppConfig.init(propsFile);
            AppConfig appConfig = AppConfig.getInstance();
            String mailmarkCompliancePath = appConfig.getMailmarkCompliancePath();
            
            LOGGER.trace("Load DPF records...");
            DpfParser dpf = new DpfParser(inputFile, outputFile);
            ArrayList<Customer> customers = dpf.Load();
            
            LOGGER.trace("Load lookup files...");
            String selRef = customers.get(0).getSelectorRef();
            loadLookupFiles(selRef);
            // Set presentation priority for all records
            LOGGER.trace("Set presentation priorities...");
            setPresentationPriorities(customers);

            // set product types
            ComplianceChecker cc = new ComplianceChecker(customers, runNo);
            cc.checkMscGroups();
            cc.calculateDPSCompliance();
            cc.calculateActualMailProduct();
            cc.writeComplianceReportFile(mailmarkCompliancePath);

            // Check weights and sizes
            LOGGER.trace("Calculating Weights & Sizes...");
            CalculateWeightsAndSizes cws = new CalculateWeightsAndSizes(customers);
            cws.calculate();

            // save to new file
            LOGGER.debug("Saving DPF as {}", outputFile);
            dpf.Save(customers);
            
            String summary = summaryPrint(customers);
            LOGGER.debug(summary);
        } catch (Exception ex) {
            LOGGER.fatal(ExceptionUtils.getStackTrace(ex));
            System.exit(1);
        }
        LOGGER.debug("---- Check Compliance Finished ----");
    }

    /**
     * Validate and set command-line arguments
     * 
     * @param args
     */
    private static void setArgs(String[] args) {

        if (args.length != EXPECTED_NUMBER_OF_ARGS) {
            LOGGER.fatal("Incorrect number of args parsed '{}' expecting '{}'. " + "Args are " + "1. Props file, " + "2. Input file, " + "3. Output file, " + "4. Run No", args.length, EXPECTED_NUMBER_OF_ARGS);
            System.exit(1);
        }

        inputFile = args[0];
        boolean inputFileExists = new File(inputFile).exists();
        if (!inputFileExists) {
            LOGGER.fatal("Input File [{}] doesn't exist on the filepath.", inputFile);
            System.exit(1);
        }

        outputFile = args[1];

        propsFile = args[2];
        boolean propsFileExists = new File(propsFile).exists();
        if (!propsFileExists) {
            LOGGER.fatal("Properties File [{}] doesn't exist on the filepath.", propsFile);
            System.exit(1);
        }

        runNo = args[3];
        boolean runNoIsNumeric = StringUtils.isNumeric(runNo);
        if (!runNoIsNumeric) {
            LOGGER.fatal("Invalid character in Run No. [{}]", runNo);
            System.exit(1);
        }

    }

    /**
     * Set Production Config using the Selector Lookup file and the selector type
     * that was set in the dpf.
     * 
     * @param appConfig
     * @param customers
     * @throws IOException
     */
    private static void loadLookupFiles(String selRef) throws IOException {

        AppConfig appConfig = AppConfig.getInstance();
        SelectorLookup.init(appConfig.getLookupFile());

        if (!SelectorLookup.getInstance().isPresent(selRef)) {
            LOGGER.fatal("Selector [{}] is not present in the lookupFile.", selRef);
            System.exit(1);
        }
        
        Selector selector = SelectorLookup.getInstance().getSelector(selRef);
        ProductionConfiguration.init(appConfig.getProductionConfigPath() + selector.getProductionConfig() + appConfig.getProductionFileSuffix());
        PostageConfiguration.init(appConfig.getPostageConfigPath() + selector.getPostageConfig() + appConfig.getPostageFileSuffix());
        PresentationConfiguration.init(appConfig.getPresentationPriorityConfigPath() + selector.getPresentationConfig() + appConfig.getPresentationPriorityFileSuffix());
        InsertLookup.init(appConfig.getInsertLookup());
        StationeryLookup.init(appConfig.getStationeryLookup());
        EnvelopeLookup.init(appConfig.getEnvelopeLookup());
        PapersizeLookup.init(appConfig.getPapersizeLookup());
        
    }

    /**
     * Sets the presentation priority for each customer. Looks up the batch type in
     * the configuration file and returns the sequence number. If batch type is not
     * present the priority is set to 999.
     * 
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

    /**
     * Prints a summary of the number of items for each batch type.
     * 
     * @param docProps
     */
    private static String summaryPrint(ArrayList<Customer> customers) {
        return customers.stream().collect(Collectors.groupingBy(Customer::getFullBatchName, Collectors.counting())).toString();
    }
}
