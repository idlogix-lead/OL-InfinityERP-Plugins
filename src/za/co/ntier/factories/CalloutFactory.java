package za.co.ntier.factories;

import java.util.ArrayList;
import java.util.List;

import org.adempiere.base.IColumnCallout;
import org.adempiere.base.IColumnCalloutFactory;
import org.compiere.model.MInvoice;
import org.compiere.model.MProduct;

import za.co.ntier.callouts.GenerateProductCode;
import za.co.ntier.callouts.InvoiceCallout;

public class CalloutFactory implements IColumnCalloutFactory{

	@Override
	public IColumnCallout[] getColumnCallouts(String tableName, String columnName) {
		// TODO Auto-generated method stub
		
		List<IColumnCallout> list = new ArrayList<IColumnCallout>();
		
		if(tableName.equals(MProduct.Table_Name) && columnName.equalsIgnoreCase("M_Product_Category_ID"))
			list.add(new GenerateProductCode());
		if(tableName.equals(MInvoice.Table_Name) && columnName.equalsIgnoreCase("C_DocType_ID"))
			list.add(new InvoiceCallout());
		
		return list!=null? list.toArray(new IColumnCallout[0]):new IColumnCallout[0];
	}

}
