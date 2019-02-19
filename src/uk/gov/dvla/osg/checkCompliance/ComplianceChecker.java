package uk.gov.dvla.osg.checkCompliance;

import static uk.gov.dvla.osg.common.enums.BatchType.*;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import uk.gov.dvla.osg.common.classes.Customer;
import uk.gov.dvla.osg.common.config.PostageConfiguration;
import uk.gov.dvla.osg.common.config.PresentationConfiguration;
import uk.gov.dvla.osg.common.config.ProductionConfiguration;
import uk.gov.dvla.osg.common.enums.BatchType;
import uk.gov.dvla.osg.common.enums.Product;

public class ComplianceChecker {
	private static final Logger LOGGER = LogManager.getLogger();
	// Input variables
	private ArrayList<Customer> customers;
	private final List<BatchType> batchTypesToUkm;
	private PresentationConfiguration presentationConfig;
	private ProductionConfiguration prodConfig;
	private final Product mailsortProduct;
	private final int ukmMinimumCompliance;
	private final int minimumMailSort;
	private int ukmMinimumTrayVolume;
	private String runNo;
	private String selectorRef;
	// Throughput variables
	private int badDpsCount = 0;
	private int goodDpsCount = 0;
	// Output variables
	private double compliance = 0;
	private double maxBadDps = 0;
	private int totalMailsortCount = 0;
	private PostageConfiguration postConfig;

	public ComplianceChecker(ArrayList<Customer> customers, String runNo) {
		this.runNo = runNo;
		this.selectorRef = customers.get(0).getSelectorRef();
		this.customers = customers;

		this.presentationConfig = PresentationConfiguration.getInstance();
		this.prodConfig = ProductionConfiguration.getInstance();
		this.mailsortProduct = prodConfig.getMailsortProduct();
		this.minimumMailSort = prodConfig.getMinimumMailsort();

		this.postConfig = PostageConfiguration.getInstance();
		this.batchTypesToUkm = postConfig.getUkmBatchTypes();
		this.ukmMinimumCompliance = postConfig.getUkmMinimumCompliance();
		this.ukmMinimumTrayVolume = postConfig.getUkmMinimumTrayVolume();
	}

	/**
	 * Check Mailsortcode groups are over the 25 limit, if under change to unsorted
	 */
	public void checkMscGroups() {

		ArrayList<String> mscsToAdjust = new ArrayList<String>();

		if (!mailsortProduct.equals(Product.UNSORTED)) {

		List<String> mscs = customers.stream()
	                     	.filter(c -> batchTypesToUkm.contains(c.getBatchType()) && c.isEog())
	                     	.map(c -> c.getLang().name() + c.getBatchName() + c.getSubBatch() + c.getMsc())
	                     	.collect(Collectors.toList());
			
				mscs.stream().distinct().forEach(msc -> {
					int occurrences = Collections.frequency(mscs, msc);
					if (occurrences < ukmMinimumTrayVolume) {
						mscsToAdjust.add(msc);
						LOGGER.info("MSC '{}' has only {} items, minimum volume {}", msc, occurrences, ukmMinimumTrayVolume);
					}
				});

			// If below 25 in tray, change to unsorted batch & set EOG
			if (mscsToAdjust.size() > 0) {
				LOGGER.info("Adjusting {} mscs", mscsToAdjust.size());
				customers.forEach(cus -> {
					if (mscsToAdjust.contains(cus.getLang().name() + cus.getBatchType().name() + cus.getSubBatch() + cus.getMsc())) {
						cus.updateBatchType(UNSORTED, presentationConfig.lookupRunOrder(UNSORTED));
						// PB 19/02/19 : Switch Multis to singles when set in production config, otherwise leave as MULTIs
						if (!prodConfig.isMultiUnsorted()) {
						    cus.setGroupId(null);
						    cus.setTotalPagesInGroup(cus.getNoOfPages());
						    cus.setEog();
						}
					}
				});
			}
		}
	}

	//This calculates DPS compliance and the maximum default DPS permitted
	public void calculateDPSCompliance() {
		if (!mailsortProduct.equals(Product.UNSORTED)) {
			customers.forEach(cus -> {
				if (batchTypesToUkm.contains(cus.getBatchType())) {
					totalMailsortCount++;
					if (StringUtils.isEmpty(cus.getDps()) || cus.getDps().equals("9Z")) {
						badDpsCount++;
					} else {
						goodDpsCount++;
					}
				}
			});
			
			int percentage = 100 - ukmMinimumCompliance;
			maxBadDps = (((double) goodDpsCount / 100) * percentage) - 1;
			compliance = 100 - (((double) badDpsCount / (double) goodDpsCount) * 100);
			LOGGER.info(
					"Run total={}, total mailsort count={}, \n\tgood DPS count={}, bad DPS count={}, \n\tmaximum permitted default DPS={}, compliance level={} minimum compliance set to {}",
					customers.size(), totalMailsortCount, goodDpsCount, badDpsCount, maxBadDps, compliance,
					ukmMinimumCompliance);
		
		} else {
			LOGGER.info("Mailsort product set to UNSORTED in config returning 0");
			compliance = 0;
			maxBadDps = 0;
		}
	}

	/**
	 * Calculate how we are actually going to send this run, UNSORTED, SORTED via MM, SORTED via OCR
	 */
	public void calculateActualMailProduct() {
		ActualMailProduct amp = new ActualMailProduct(customers);

		if (mailsortProduct.equals(Product.UNSORTED) || totalMailsortCount < minimumMailSort) {
			amp.doUnsorted();
		} else if (mailsortProduct.equals(Product.OCR)) {
			amp.doOCR();
		} else if (mailsortProduct.equals(Product.MM) && compliance < ukmMinimumCompliance) {
			amp.mmUnderCompliance();
		} else if (mailsortProduct.equals(Product.MM)) {
			amp.mmOverCompliance();
		} else {
			LOGGER.fatal("Failed to determine mailing product. Mailsort product set to '{}'", mailsortProduct);
		}
		LOGGER.info("Run will be sent via {} product.", amp.getActualMailProduct());
	}

	public void writeComplianceReportFile(String filepath) {
		LOGGER.trace("Writing Compliance Report to: {}", filepath);

		String dateStamp = new SimpleDateFormat("dd/MM/YY").format(new Date());
		//Wrapped "0" in String.format() - PB 13/04
		String compliaceStr = Double.isFinite(compliance) ? String.format("%.4g%n", compliance) : String.format("%d%n", 0);
		Collection<String> elements = Arrays.asList(runNo, selectorRef, dateStamp, compliaceStr);
		String str = String.join(",", elements);
		LOGGER.trace(str.trim());
		if (!new File(filepath).exists()) {
			try {
				new File(filepath).createNewFile();
			} catch (IOException ex) {
				LOGGER.fatal("Unable to create compliance report file {}", filepath);
				System.exit(1);
			}
		}

		try (BufferedWriter bw = new BufferedWriter(new FileWriter(new File(filepath).getAbsoluteFile(), true))) {
			bw.write(str);
		} catch (IOException e) {
			LOGGER.fatal("Unable to create compliance report file {}", filepath);
			System.exit(1);
		}
	}

	public double getDpsAccuracy() {
		return this.compliance;
	}

	public double getMaximumDefaultDps() {
		return this.maxBadDps;
	}

	public int getTotalMailsortCount() {
		return this.totalMailsortCount;
	}
}
