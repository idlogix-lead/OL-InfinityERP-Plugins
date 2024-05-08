package za.co.ntier.callouts;

import java.util.Properties;

import org.adempiere.base.IColumnCallout;
import org.compiere.model.GridField;
import org.compiere.model.GridTab;

public class InvoiceCallout implements IColumnCallout{

	@Override
	public String start(Properties ctx, int WindowNo, GridTab mTab, GridField mField, Object value, Object oldValue) {
		// TODO Auto-generated method stub
		if(mTab.getAD_Tab_ID()==1000001 && (Integer)value == 1000050) {
			mTab.setValue("PaymentRule", "B");
		}
		return null;
	}

}
