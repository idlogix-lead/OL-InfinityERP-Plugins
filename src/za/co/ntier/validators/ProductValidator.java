package za.co.ntier.validators;

import org.compiere.model.MClient;
import org.compiere.model.MProduct;
import org.compiere.model.MProductCategory;
import org.compiere.model.ModelValidationEngine;
import org.compiere.model.ModelValidator;
import org.compiere.model.PO;

public class ProductValidator  implements ModelValidator{

	@Override
	public void initialize(ModelValidationEngine engine, MClient client) {
		// TODO Auto-generated method stub
		engine.addModelChange(MProduct.Table_Name, this);
	}

	@Override
	public int getAD_Client_ID() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public String login(int AD_Org_ID, int AD_Role_ID, int AD_User_ID) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String modelChange(PO po, int type) throws Exception {
		// TODO Auto-generated method stub
		if(po.get_Table_ID()==MProduct.Table_ID && type==TYPE_AFTER_NEW) 
		{
			int catID = po.get_ValueAsInt("M_Product_Category_ID");
			MProductCategory category = new MProductCategory(po.getCtx(), catID, null);
			int currentNext = category.get_ValueAsInt("CurrentNext");
			currentNext+=1;
			category.set_ValueOfColumn("CurrentNext", currentNext);
			category.save();
		}
		return null;
	}

	@Override
	public String docValidate(PO po, int timing) {
		// TODO Auto-generated method stub
		return null;
	}

}
