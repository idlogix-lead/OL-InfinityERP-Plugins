package za.co.ntier.callouts;

import java.util.Properties;

import org.adempiere.base.IColumnCallout;
import org.compiere.model.GridField;
import org.compiere.model.GridTab;
import org.compiere.model.MProduct;
import org.compiere.model.MProductCategory;
import org.compiere.model.MSequence;
import org.compiere.util.DB;
import org.compiere.util.Env;

public class GenerateProductCode implements IColumnCallout {

	@Override
	public String start(Properties ctx, int WindowNo, GridTab mTab, GridField mField, Object value, Object oldValue) {
		// TODO Auto-generated method stub
		
		if(mTab.getValue("M_Product_Category_ID") !=null) {
			int catID =Integer.parseInt(mTab.get_ValueAsString("M_Product_Category_ID"));
			MProductCategory category = new MProductCategory(ctx, catID, null);
			boolean isSequence = category.get_ValueAsBoolean("IsSequence");
			if(isSequence) {
				int currentNext = category.get_ValueAsInt("CurrentNext");
				String searchKeyFormat = "%s-%s";
				String description  = category.getDescription();
				String searchKey = String.format(searchKeyFormat, description,currentNext);
				mTab.setValue("value", searchKey);
			}
			
		}
		
		
		
		
		return null;
	}
	
	private int getNextProdID() {
		int prodID = 0;
		String sql = "select currentnext\n"
				+ "from adempiere.ad_sequence\n"
				+ "where name='M_Product'";
		prodID = DB.getSQLValue(null, sql);
			return prodID;
	}

}
