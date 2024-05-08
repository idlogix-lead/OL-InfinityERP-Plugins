package za.co.ntier.factories;

import org.adempiere.base.IProcessFactory;
import org.compiere.process.ProcessCall;

import za.co.ntier.processes.ProductFinder;
import za.co.ntier.processes.RefreshBundle;
import za.co.ntier.processes.WcUnSyncOrders;
import za.co.ntier.processes.WooCommerce;

public class WooCommerceFactory implements IProcessFactory{

	@Override
	public ProcessCall newProcessInstance(String className) {
		if (className.equals("za.co.ntier.processes.WooCommerce"))
		return  new WooCommerce();
		if (className.equals("za.co.ntier.processes.WcUnSyncOrders"))
			return  new WcUnSyncOrders();
		if (className.equals("za.co.ntier.processes.ProductFinder"))
			return  new ProductFinder();
		if (className.equals("za.co.ntier.processes.RefreshBundle"))
			return  new RefreshBundle();
		
		
		return null;
	}

}
