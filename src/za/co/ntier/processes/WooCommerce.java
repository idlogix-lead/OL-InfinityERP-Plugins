package za.co.ntier.processes;

import org.compiere.model.MOrder;
import org.compiere.model.PO;
import org.compiere.model.Query;
import org.compiere.process.ProcessInfoParameter;
import org.compiere.process.SvrProcess;
import org.compiere.util.DB;
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
import java.time.LocalDateTime;
/**
 *
 * Start a thread to collect unsynchronised orders from WooCommerce website
 *
 * @author yogan naidoo
 */

public class WooCommerce extends SvrProcess {

	
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
					params.put("status", "completed");
					
					if(order_ID>0) {
						Map<?,?> order = wooCommerce.get(EndpointBaseType.ORDERS.getValue(), order_ID);
						processOrder(order);
					}
					else {
						params.put("after", URLEncoder.encode(after));
						params.put("before", URLEncoder.encode(before));
						params.put("orderby", "id");
						
						int page=0;
							while(true) {
							page+=1;
							params.put("page", String.valueOf(page));
							List<?> wcOrders; wcOrders =wooCommerce.getAll(EndpointBaseType.ORDERS.getValue(), params);
								for (int i = 0; i < wcOrders.size(); i++) {
									Map<?, ?> order = (Map<?, ?>) wcOrders.get(i);
									System.out.println(order.get("id"));
									processOrder(order);
								}
								if(wcOrders.size()==0)
									break;
								
							}						
					}
					
		return "";
	}
	
	private void processOrder(Map<?,?> order) {
		if(order.get("status")==null || !(order.get("status").toString().equalsIgnoreCase("completed")))
		{
			System.out.println("Print # 1");
		return;
		}
		boolean isdeleted = false;
		int id = (int) order.get("id");
		boolean isExisting = isExistingOrder(String.valueOf(id));
		MOrder oldOrder = ExistingOrder(String.valueOf(id));
		if(oldOrder != null ) {
//			if(order_ID>0) {
//			MOrder oldOrder;
			String whereClause = "poreference = ?";
//			oldOrder = new Query(getCtx(), MOrder.Table_Name, whereClause, null)
//					.setParameters(new Object[] { Integer.toString(id) }).firstOnly();
			if(oldOrder!=null) {
				try {
					String docStatus = oldOrder.get_ValueAsString(MOrder.COLUMNNAME_DocStatus);
					if(List.of("IN","CO","CL").contains(docStatus)) {
						System.out.println("Print # 2");
						return;
					}
						if(oldOrder.delete(false)==false) {
						addBufferLog(oldOrder.getC_Order_ID(), oldOrder.getDateOrdered(),
								null, "Could not update Order ---------------->"+oldOrder.getDocumentNo(),
								MOrder.Table_ID, oldOrder.getC_Order_ID());
					return;
					}
					else
						isdeleted=true;
				} catch (Exception e) {
					// TODO: handle exception
					addBufferLog(oldOrder.getC_Order_ID(), oldOrder.getDateOrdered(),
							null, e.getMessage()+" "+oldOrder.getDocumentNo(),
							MOrder.Table_ID, oldOrder.getC_Order_ID());
					return;
				}
			}
				
//			}
//			else {
//				System.out.println("Print # 3");
//				return;
//			}
			
			}
		
		System.out.println("Order- " + order.get("id") + ": " + order);
		WcOrder wcOrder = new WcOrder(getCtx(), get_TrxName(), wcDefaults);
		MOrder morder=wcOrder.createOrder(order);
		addBufferLog(morder.getC_Order_ID(), morder.getDateOrdered(),
				null, (isdeleted?"Updated Order ---------------->":"")+ morder.getDocumentNo(),
				MOrder.Table_ID, morder.getC_Order_ID());
		
		// Iterate through each order Line
		List<?> lines = (List<?>) order.get("line_items");
		wcOrder.filterbundles(lines);
		for (int j = 0; j < lines.size(); j++) {
			Map<?, ?> line = (Map<?, ?>) lines.get(j);
			wcOrder.createOrderLine(line, order);
			Object name = line.get("name");
			System.out.println("Name of Product = " + name.toString());
		}
		wcOrder.applyDiscount();
		wcOrder.addShippingCharges(order);
//		wcOrder.addCoupon(order);
		Map<String, Object> body = new HashMap<>();
		List<Map<String, String>> listOfMetaData = new ArrayList();
		Map<String, String> metaData = new HashMap<>();
		metaData.put("key", "syncedToIdempiere");
		metaData.put("value", "yes");
		listOfMetaData.add(metaData);

		body.put("meta_data", listOfMetaData);
//		Map<?, ?> response = wooCommerce.update(EndpointBaseType.ORDERS.getValue(), id, body);
//		System.out.println(response.toString());
		
	}
	
	private boolean isExistingOrder(String id) {
		int c_order_id = 0;
		String sql = "SELECT c_order_id FROM c_order "
				+ "WHERE poreference=? AND Docstatus IN ('DR','IN','CO') AND issotrx = 'Y' "
				+ "ORDER BY c_order_id DESC limit 1";
		c_order_id = DB.getSQLValue(get_TrxName(), sql,id);
			return c_order_id>0;
	}
	private MOrder ExistingOrder(String id) {
		int c_order_id = 0;
		String sql = "SELECT c_order_id FROM c_order "
				+ "WHERE poreference=? AND Docstatus IN ('DR','IN','CO') AND issotrx = 'Y' "
				+ "ORDER BY c_order_id DESC limit 1";
		c_order_id = DB.getSQLValue(get_TrxName(), sql,id);
			if (c_order_id>0)
				return new MOrder(getCtx(),c_order_id,get_TrxName());
		return null;
	}
	

}
