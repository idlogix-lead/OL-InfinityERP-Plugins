package za.co.ntier.factories;

import org.adempiere.base.IModelValidatorFactory;
import org.compiere.model.ModelValidator;

import za.co.ntier.validators.PaymentValidator;
import za.co.ntier.validators.ProductValidator;

public class OrganicModelValidatorFactory implements IModelValidatorFactory {

	@Override
	public ModelValidator newModelValidatorInstance(String className) {
		// TODO Auto-generated method stub
		
		if(className.equals("za.co.ntier.validators.PaymentValidator"))
			return new PaymentValidator();
		if(className.equals("za.co.ntier.validators.ProductValidator"))
			return new ProductValidator();
		
		return null;
	}

}
