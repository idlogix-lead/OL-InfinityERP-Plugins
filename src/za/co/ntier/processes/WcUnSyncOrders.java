package za.co.ntier.processes;

import org.compiere.model.MOrder;
import org.compiere.model.PO;
import org.compiere.model.Query;
import org.compiere.process.ProcessInfoParameter;
import org.compiere.process.SvrProcess;
import org.compiere.util.Env;

import java.net.URLEncoder;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import com.icoderman.woocommerce.ApiVersionType;
import com.icoderman.woocommerce.EndpointBaseType;
import com.icoderman.woocommerce.Woocommerce;
import com.icoderman.woocommerce.WooCommerceAPI;
import com.icoderman.woocommerce.oauth.OAuthConfig;

import za.co.ntier.model.X_zz_woocommerce;
import za.co.ntier.woocommerce.WcOrder;

/**
 *
 * Start a thread to collect unsynchronised orders from WooCommerce website
 *
 * @author yogan naidoo
 */

public class WcUnSyncOrders extends SvrProcess {

	
	int order_ID=0;
	Timestamp StartDate;
	Timestamp EndDate;
	Woocommerce wooCommerce;
	PO wcDefaults;
	@Override
	protected void prepare() {
		
		ProcessInfoParameter[] para = getParameter();
		for (int i = 0; i < para.length; i++)
		{
			String name = para[i].getParameterName();
			if (para[i].getParameter() == null)
				;
			else if (name.equals("OrderID"))
				order_ID = para[i].getParameterAsInt();
			else if (name.equals("StartDate"))
				StartDate = para[i].getParameterAsTimestamp();
			else if (name.equals("EndDate"))
				EndDate = para[i].getParameterAsTimestamp();
			else
				log.log(Level.SEVERE, "Unknown Parameter: " + name);
		}
	}

	@Override
	protected String doIt() throws Exception {
		
		SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
		StartDate = Timestamp.valueOf(StartDate.toLocalDateTime().withHour(00).withMinute(00).withSecond(00));
		EndDate = Timestamp.valueOf(EndDate.toLocalDateTime().withHour(23).withMinute(59).withSecond(59));
		String after = dateFormat.format(StartDate);
		String before = dateFormat.format(EndDate);
		
//		Thread thread = new Thread(new MyRunnable());
//		thread.start();

		// Get WooCommerce defaults
					
					String whereClause = " isactive = 'Y' AND AD_Client_ID = ?";
					wcDefaults = new Query(getCtx(), X_zz_woocommerce.Table_Name, whereClause, null)
							.setParameters(new Object[] { Env.getAD_Client_ID(getCtx()) }).firstOnly();
					if (wcDefaults == null)
						throw new IllegalStateException("/nWooCommerce Defaults need to be set on iDempiere /n");

					// Setup client
					OAuthConfig config = new OAuthConfig((String) wcDefaults.get_Value("url"),
							(String) wcDefaults.get_Value("consumerkey"), (String) wcDefaults.get_Value("consumersecret"));
					wooCommerce = new WooCommerceAPI(config, ApiVersionType.V3);

					// Get all with request parameters
					Map<String, String> params = new HashMap<>();	
					params.put("per_page", "100");
					params.put("offset", "0");
					params.put("meta_key", "syncedToIdempiere");
					params.put("meta_value", "no");
					params.put("status", "completed");
					params.put("after", URLEncoder.encode(after));
					params.put("before", URLEncoder.encode(before));
					
					if(order_ID>0) {
						Map<?,?> order = wooCommerce.get(EndpointBaseType.ORDERS.getValue(), order_ID);
						unSyncOrder(order);
					}
					else {
						List<?> wcOrders = wooCommerce.getAll(EndpointBaseType.ORDERS.getValue(), params);
						for (int i = 0; i < wcOrders.size(); i++) {
							Map<?, ?> order = (Map<?, ?>) wcOrders.get(i);
							unSyncOrder(order);
						}
					}

		return "";
	}
	
	private void unSyncOrder(Map<?,?> order) {
		
		int id = (int) order.get("id");
		Map<String, Object> body = new HashMap<>();
		List<Map<String, String>> listOfMetaData = new ArrayList();
		Map<String, String> metaData = new HashMap<>();
		metaData.put("key", "syncedToIdempiere");
		metaData.put("value", "no");
		listOfMetaData.add(metaData);
		body.put("meta_data", listOfMetaData);
		Map<?, ?> response = wooCommerce.update(EndpointBaseType.ORDERS.getValue(), id, body);
		System.out.println(response.toString());
		
	}
}
