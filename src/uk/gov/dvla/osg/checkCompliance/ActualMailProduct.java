package uk.gov.dvla.osg.checkCompliance;

import static org.apache.commons.lang3.StringUtils.*;
import static uk.gov.dvla.osg.common.classes.Language.*;

import java.util.ArrayList;

import uk.gov.dvla.osg.common.classes.BatchType;
import uk.gov.dvla.osg.common.classes.Customer;
import uk.gov.dvla.osg.common.classes.Product;
import uk.gov.dvla.osg.common.config.PostageConfiguration;
import uk.gov.dvla.osg.common.config.PresentationConfiguration;
import uk.gov.dvla.osg.common.config.ProductionConfiguration;

public class ActualMailProduct {
	// Input variables
	ArrayList<Customer> customers;
	PostageConfiguration postageConfig;
	ProductionConfiguration productionConfig;
	// Output variables
	private Product actualMailProduct;

	public ActualMailProduct(ArrayList<Customer> customers) {
		this.customers = customers;
		this.postageConfig = PostageConfiguration.getInstance();
		this.productionConfig = ProductionConfiguration.getInstance();
	}

	public void doUnsorted() {
	    actualMailProduct = Product.UNSORTED;
		customers.forEach(customer -> {
		    
		    switch (customer.getBatchType()) {
            case UNSORTED:
                if (customer.getLang().equals(E)) {
                    customer.setEnvelope(productionConfig.getEnvelopeEnglishUnsorted());
                } else {
                    customer.setEnvelope(productionConfig.getEnvelopeWelshUnsorted());
                }
                customer.setProduct(Product.UNSORTED);
                break;
		    case SORTED:
		        customer.updateBatchType(BatchType.UNSORTED, PresentationConfiguration.getInstance().lookupRunOrder(BatchType.UNSORTED));
	            if (customer.getLang().equals(E)) {
	                customer.setEnvelope(productionConfig.getEnvelopeEnglishUnsorted());
	            } else {
	                customer.setEnvelope(productionConfig.getEnvelopeWelshUnsorted());
	            }
	            customer.setProduct(Product.UNSORTED);
                break;
		    case MULTI:
		        if (productionConfig.isMultiInUnsorted()) {
		            customer.updateBatchType(BatchType.UNSORTED, PresentationConfiguration.getInstance().lookupRunOrder(BatchType.UNSORTED));		            
		        } else {
		            customer.updateBatchType(BatchType.MULTI, PresentationConfiguration.getInstance().lookupRunOrder(BatchType.MULTI));
		        }
                if (customer.getLang().equals(E)) {
                    customer.setEnvelope(productionConfig.getEnvelopeEnglishUnsorted());
                } else {
                    customer.setEnvelope(productionConfig.getEnvelopeWelshUnsorted());
                }
                customer.setProduct(Product.UNSORTED);
                break;
            case UNCODED:
                if (customer.getLang().equals(E)) {
                    customer.setEnvelope(productionConfig.getEnvelopeEnglishUncoded());
                } else {
                    customer.setEnvelope(productionConfig.getEnvelopeWelshUncoded());
                }
                customer.setProduct(Product.UNCODED);
                break;
            case CLERICAL: case FLEET: case REJECT:
                customer.setEnvelope("");
                customer.setProduct("");
                break;
            case REPRINT: case SORTING:
                break;
            default:
                break;
		    }
		});
	}

	public void doOCR() {
		actualMailProduct = Product.OCR;
		customers.forEach(customer -> {
			//SET FINAL ENVELOPE
			switch (customer.getBatchType()) {
			case SORTED: case MULTI:
				if (customer.getLang().equals(E)) {
					customer.setEnvelope(productionConfig.getEnvelopeEnglishOcr());
				} else {
					customer.setEnvelope(productionConfig.getEnvelopeWelshOcr());
				}
				customer.setProduct(actualMailProduct);
				break;
			case UNSORTED:
				if (customer.getLang().equals(E)) {
					customer.setEnvelope(productionConfig.getEnvelopeEnglishUnsorted());
				} else {
					customer.setEnvelope(productionConfig.getEnvelopeWelshUnsorted());
				}
				customer.setProduct(Product.UNSORTED);
				break;
			case UNCODED:
				if (customer.getLang().equals(E)) {
					customer.setEnvelope(productionConfig.getEnvelopeEnglishUncoded());
				} else {
					customer.setEnvelope(productionConfig.getEnvelopeWelshUncoded());
				}
				customer.setProduct(Product.UNCODED);
				break;					
			case CLERICAL: case FLEET: case REJECT:
				customer.setEnvelope("");
				customer.setProduct("");
				break;
			default:
				break;
			}
		});
	}

	public void mmUnderCompliance() {
		actualMailProduct = Product.OCR;
		customers.forEach(customer -> {
			switch (customer.getBatchType()) {
			case SORTED: case MULTI:
				if (customer.getLang().equals(E)) {
					customer.setEnvelope(productionConfig.getEnvelopeEnglishOcr());
				} else {
					customer.setEnvelope(productionConfig.getEnvelopeWelshOcr());
				}
				customer.setProduct(actualMailProduct);
				break;
			case UNSORTED:
				if (customer.getLang().equals(E)) {
					customer.setEnvelope(productionConfig.getEnvelopeEnglishUnsorted());
				} else {
					customer.setEnvelope(productionConfig.getEnvelopeWelshUnsorted());
				}
				customer.setProduct(Product.UNSORTED);
				break;
			case UNCODED:
				if (customer.getLang().equals(E)) {
					customer.setEnvelope(productionConfig.getEnvelopeEnglishUncoded());
				} else {
					customer.setEnvelope(productionConfig.getEnvelopeWelshUncoded());
				}
				customer.setProduct(Product.UNCODED);
				break;				
			case CLERICAL: case FLEET: case REJECT:
				customer.setEnvelope("");
				customer.setProduct("");
				break;
			default:
				break;
			}
		});
	}

	public void mmOverCompliance() {
		actualMailProduct = Product.MM;
		customers.forEach(customer -> {
			//SET DEFAULT DPS IF APPLICABLE
			if (postageConfig.getUkmBatchTypes().contains(customer.getBatchType()) 
					&& isBlank(customer.getDps())) {
				customer.setDps("9Z");
			}
			//SET FINAL ENVELOPE
			switch (customer.getBatchType()) {
			case SORTED: case MULTI:
				if (customer.getLang().equals(E)) {
					customer.setEnvelope(productionConfig.getEnvelopeEnglishMm());
				} else {
					customer.setEnvelope(productionConfig.getEnvelopeWelshMm());
				}
				customer.setProduct(actualMailProduct);
				break;
			case UNSORTED:
				if (customer.getLang().equals(E)) {
					customer.setEnvelope(productionConfig.getEnvelopeEnglishUnsorted());
				} else {
					customer.setEnvelope(productionConfig.getEnvelopeWelshUnsorted());
				}
				customer.setProduct(Product.UNSORTED);
				break;
			case UNCODED:
				if (customer.getLang().equals(E)) {
					customer.setEnvelope(productionConfig.getEnvelopeEnglishUncoded());
				} else {
					customer.setEnvelope(productionConfig.getEnvelopeWelshUncoded());
				}
				customer.setProduct(Product.UNCODED);
				break;					
			case CLERICAL: case FLEET: case REJECT:
				customer.setEnvelope("");
				customer.setProduct("");
				break;
			default:
				break;
			}
		});
	}

	public String getActualMailProduct() {
		return actualMailProduct.name();
	}
}
