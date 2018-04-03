package uk.gov.dvla.osg.checkCompliance;

import java.util.ArrayList;
import java.util.HashMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import uk.gov.dvla.osg.common.classes.Customer;
import uk.gov.dvla.osg.common.classes.Envelope;
import uk.gov.dvla.osg.common.classes.Insert;
import uk.gov.dvla.osg.common.classes.PaperSize;
import uk.gov.dvla.osg.common.classes.Stationery;
import uk.gov.dvla.osg.common.config.EnvelopeLookup;
import uk.gov.dvla.osg.common.config.InsertLookup;
import uk.gov.dvla.osg.common.config.PapersizeLookup;
import uk.gov.dvla.osg.common.config.ProductionConfiguration;
import uk.gov.dvla.osg.common.config.StationeryLookup;

public class CalculateWeightsAndSizes {
	private static final Logger LOGGER = LogManager.getLogger();

	// Input variables
	HashMap<String, Insert> insertLookup;
	HashMap<String, Envelope> envelopeLookup;
	private HashMap<String, Stationery> stationeryLookup;
	
	ArrayList<Customer> customers;
	//private String envelopeType;
	
	//INSERTS, ENVELOPE, PAPER all in mm
	private double insertSize;
	private double insertWeight;
	private double paperSize;
	private double paperWeight;
	private double envelopeSize;
	private double envelopeWeight;
	private double totalSize;
	private double totalWeight;
	private int pageInGroupCount;
	
	private ArrayList<Customer> group = new ArrayList<Customer>();

	private HashMap<String, PaperSize> paperSizeLookup;

	private ProductionConfiguration productionConfiguration;

	public CalculateWeightsAndSizes(ArrayList<Customer> customers) {
		this.customers = customers;
		this.envelopeLookup = EnvelopeLookup.getInstance().getLookup();
		this.insertLookup = InsertLookup.getInstance().getLookup();
		this.stationeryLookup = StationeryLookup.getInstance().getLookup();
		this.paperSizeLookup = PapersizeLookup.getInstance().getLookup();
		this.productionConfiguration = ProductionConfiguration.getInstance();
		//this.envelopeType = ProductionConfiguration.getInstance().getEnvelopeType();
	}

	public void calculate() {
		// Only on multi & sorted
		for (Customer customer : customers) {
			try {
				// Insert size & weight
				calcInserts(customer);
				// Envelope size & weight
				calcEnvelope();
				// Paper size & weight
				calcPaper(customer);
				// Set totalsize and totalweight - final customer in group includes envelope & inserts
				calcTotals(customer.isEog());
				// Set weight and thickness for customer
				customer.setWeight(totalWeight);
				customer.setSize(totalSize);
				//Calculate total pages in group
				pageInGroupCount += customer.getNoOfPages();
				group.add(customer);
				calcTotalPagesInGroup(customer.isEog());
				
			} catch (NullPointerException e) {
				LOGGER.fatal("Looking up insert '{}', stationery '{}'", customer.getInsertRef(),
						customer.getStationery());
				LOGGER.fatal("Envelope, Insert or Stationery lookup failed: '{}'", e.getMessage());
				System.exit(1);
			}
		}
	}

	private void calcTotalPagesInGroup(boolean isEog) {
		if (isEog) {
			group.forEach(customer -> customer.setTotalPagesInGroup(pageInGroupCount));
			pageInGroupCount = 0;
			group.clear();
		}
	}

	private void calcInserts(Customer customer) {
		if (!customer.getInsertRef().isEmpty()) {
			insertSize = insertLookup.get(customer.getInsertRef()).getThickness();
			insertWeight = insertLookup.get(customer.getInsertRef()).getWeight();
		} else {
			insertSize = 0;
			insertWeight = 0;
		}
	}
	
	private void calcEnvelope() {
		envelopeSize = envelopeLookup.get(productionConfiguration.getEnvelopeType()).getThickness();
		envelopeWeight = envelopeLookup.get(productionConfiguration.getEnvelopeType()).getWeight();
		
	}
	
	private void calcPaper(Customer cus) {
		int divisor = 0;
		
		if (paperSizeLookup.containsKey(cus.getPaperSize())) {
			divisor = (int) paperSizeLookup.get(cus.getPaperSize()).getMultiplier();
		}
		
		double thickness = stationeryLookup.get(cus.getStationery()).getThickness();
		int foldMultiplier = envelopeLookup.get(productionConfiguration.getEnvelopeType()).getFoldMultiplier();
		
		if(divisor != 0) {
			paperSize = (thickness / divisor) * foldMultiplier * cus.getNoOfPages();
		} else {
			paperSize = thickness * foldMultiplier * cus.getNoOfPages();					
		}
		
		paperWeight = stationeryLookup.get(cus.getStationery()).getWeight() * cus.getNoOfPages();
		
	}
	
	/**
	 * Set weight and thickness for product
	 * Last customer in group -> add the insert and envelope 
	 * @param isEog customer is final customer in group
	 */
	private void calcTotals(boolean isEog) {
		if (isEog) {
			totalSize = paperSize + insertSize + envelopeSize;
			totalWeight = paperWeight + insertWeight + envelopeWeight;
		} else {
			totalSize = paperSize;
			totalWeight = paperWeight;
		}
	}

}
