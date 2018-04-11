package uk.gov.dvla.osg.checkCompliance;

import java.util.ArrayList;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import uk.gov.dvla.osg.common.classes.Customer;
import uk.gov.dvla.osg.common.classes.Language;
import uk.gov.dvla.osg.common.config.EnvelopeLookup;
import uk.gov.dvla.osg.common.config.InsertLookup;
import uk.gov.dvla.osg.common.config.PapersizeLookup;
import uk.gov.dvla.osg.common.config.ProductionConfiguration;
import uk.gov.dvla.osg.common.config.StationeryLookup;

public class CalculateWeightsAndSizes {
	private static final Logger LOGGER = LogManager.getLogger();

	// Input variables
	private InsertLookup insertLookup;
	private EnvelopeLookup envelopeLookup;
	private StationeryLookup stationeryLookup;
	private PapersizeLookup paperSizeLookup;
	private ProductionConfiguration productionConfiguration;
	private ArrayList<Customer> customers;
	
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

	public CalculateWeightsAndSizes(ArrayList<Customer> customers) {
		this.customers = customers;
		this.envelopeLookup = EnvelopeLookup.getInstance();
		this.insertLookup = InsertLookup.getInstance();
		this.stationeryLookup = StationeryLookup.getInstance();
		this.paperSizeLookup = PapersizeLookup.getInstance();
		this.productionConfiguration = ProductionConfiguration.getInstance();
	}

	public void calculate() {
		// Only on multi & sorted
		for (Customer customer : customers) {
			try {
				// Insert size & weight
				calcInserts(customer);
				// Envelope size & weight
				calcEnvelope(customer);
				// Paper size & weight
				calcPaper(customer);
				// Set total size and total weight - final customer in group includes envelope & inserts
				calcTotals(customer.isEog());
				// Set weight and thickness for customer
				customer.setWeight(totalWeight);
				customer.setSize(totalSize);
				//Calculate total pages in group
				pageInGroupCount += customer.getNoOfPages();
				System.out.println(customer.getNoOfPages() + " " + customer.isEog());
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
	
	private void calcEnvelope(Customer customer) {
		
		// Added E/W, MP - 04/04
		if (customer.getLang().equals(Language.E)) {
			envelopeSize = envelopeLookup.get(productionConfiguration.getEnvelopeEnglishDefault()).getThickness();
			envelopeWeight = envelopeLookup.get(productionConfiguration.getEnvelopeEnglishDefault()).getWeight();
		} else {
			envelopeSize = envelopeLookup.get(productionConfiguration.getEnvelopeWelshDefault()).getThickness();
			envelopeWeight = envelopeLookup.get(productionConfiguration.getEnvelopeWelshDefault()).getWeight();	
		}
	}
	
	private void calcPaper(Customer customer) {
		int divisor = 0;
		int foldMultiplier = 0;
		
		if (paperSizeLookup.containsKey(customer.getPaperSize())) {
			divisor = (int) paperSizeLookup.get(customer.getPaperSize()).getMultiplier();
		}
		
		double thickness = stationeryLookup.get(customer.getStationery()).getThickness();
		
		// Added E/W, MP - 04/04
		if (customer.getLang().equals(Language.E)) {
			// Could change to customer.getEnvelope() as it is set previously - MP, 04/04
			foldMultiplier = envelopeLookup.get(productionConfiguration.getEnvelopeEnglishDefault()).getFoldMultiplier();
		} else {
			foldMultiplier = envelopeLookup.get(productionConfiguration.getEnvelopeWelshDefault()).getFoldMultiplier();			
		}
		
		if(divisor != 0) {
			paperSize = (thickness / divisor) * foldMultiplier * customer.getNoOfPages();
		} else {
			paperSize = thickness * foldMultiplier * customer.getNoOfPages();					
		}
		
		paperWeight = stationeryLookup.get(customer.getStationery()).getWeight() * customer.getNoOfPages();
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
