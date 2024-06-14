package za.co.ntier.woocommerce;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;

import org.adempiere.base.Core;
import org.adempiere.base.IProductPricing;
import org.compiere.model.MBPartner;
import org.compiere.model.MBPartnerLocation;
import org.compiere.model.MLocation;
import org.compiere.model.MOrder;
import org.compiere.model.MOrderLine;
import org.compiere.model.MProduct;
import org.compiere.model.MRefList;
import org.compiere.model.MReference;
import org.compiere.model.MUser;
import org.compiere.model.PO;
import org.compiere.model.Query;
import org.compiere.process.DocAction;
import org.compiere.util.CLogger;
import org.compiere.util.DB;
import org.compiere.util.Env;
import org.compiere.util.KeyNamePair;
import org.compiere.util.ValueNamePair;

import za.co.ntier.model.MCourierCompany;
/**
 *
 * Create Order and lines on iDempiere as received from WooCommerce
 *
 * @author yogan naidoo
 */

public final class WcOrder {
	private final Properties ctx;
	private final String trxName;
	private final int POSTENDERTYPE_ID = 1000000;
	private final int POS_ORDER = 1000041;
	private final int CREDIT_ORDER = 1000033;
	private boolean IS_COD;
	List<Integer> openItems = new ArrayList<Integer>();
	// private final int priceList_ID = 101;
	final String PAYMENT_RULE_CREDIT = "P";
	final String PAYMENT_RULE_CASH = "B";
	// final String PAYMENT_RULE = "P";
	private final MOrder order;
	private Boolean isTaxInclusive;
	private static CLogger log = CLogger.getCLogger(WcOrder.class);
	private PO wcDefaults;
	ArrayList<MetaDataObject> metadata = new ArrayList<>();
	String courierCompany="";
	List<Bundle> bdls = new ArrayList<Bundle>();
	public WcOrder(Properties ctx, String trxName, PO wcDefaults) {
		this.ctx = ctx;
		this.trxName = trxName;
		this.wcDefaults = wcDefaults;
		order = new MOrder(ctx, 0, trxName);
	
	}

	public MOrder createOrder(Map<?, ?> orderWc) {
		inflateMetaData(orderWc);
		order.setPOReference((orderWc.get("id").toString()));
		order.setDateOrdered(getDateOrdered((String)orderWc.get("date_completed")));
		order.setAD_Org_ID((int) wcDefaults.get_Value("ad_org_id"));
		int BP_Id =getCBPartner(orderWc,order);
		order.setC_BPartner_ID(BP_Id);
		order.set_ValueOfColumn("couriercode", getCN(orderWc));	
		order.set_ValueOfColumn("OrderReferer", getOrderReferer(orderWc));
		int BPLocationId = getBPLocationId(BP_Id);
		order.setC_BPartner_Location_ID(BPLocationId); // order.setAD_User_ID(101);
		order.setBill_BPartner_ID(BP_Id);
		order.setBill_Location_ID(BPLocationId);
		isTaxInclusive = (orderWc.get("prices_include_tax").toString().equals("true")) ? true : false;
		order.setM_PriceList_ID(getPriceList(orderWc));
		order.setIsSOTrx(true);
		order.setM_Warehouse_ID((int) wcDefaults.get_Value("m_warehouse_id"));
		order.setC_DocTypeTarget_ID(IS_COD?CREDIT_ORDER:POS_ORDER);
		order.setPaymentRule(IS_COD?PAYMENT_RULE_CREDIT:PAYMENT_RULE_CASH);
		order.setDeliveryRule("F");
		order.setInvoiceRule("D");
		setPaymentMethod(order);
		String coupons = getcouponCodes(orderWc);
		if(coupons.length()>0)
			order.set_ValueOfColumn("couponcode", coupons);		
		
		if (!order.save()) {
			throw new IllegalStateException("Could not create order");
		}
		return order;
	}

	public String getWcCustomerEmail(Map<?, ?> orderWc) {
		Map<?, ?> billing = (Map<?, ?>) orderWc.get("billing");
		return (String) billing.get("email");
	}

	private int getPriceList(Map<?, ?> orderWc) {
		String wcCurrency = (String) orderWc.get("currency");
		String localCurrency = DB.getSQLValueString(trxName,
				"select iso_code from C_Currency " + "where C_Currency_ID = " + "(select C_Currency_ID "
						+ "from M_PriceList " + "where M_PriceList_id = ?) ",
				(int) wcDefaults.get_Value("local_incl_pricelist_id"));

		Boolean local = (wcCurrency.equals(localCurrency)) ? true : false;

		int priceList;
		if (local) {
			priceList = (isTaxInclusive) ? (int) wcDefaults.get_Value("local_incl_pricelist_id")
					: (int) wcDefaults.get_Value("local_excl_pricelist_id");
		} else {
			priceList = (isTaxInclusive) ? (int) wcDefaults.get_Value("intl_incl_pricelist_id")
					: (int) wcDefaults.get_Value("intl_excl_pricelist_id");
		}
		return (priceList);
	}

	public int getBPId(String email, Map<?, ?> orderWc) {
		int c_bpartner_id = DB.getSQLValue(trxName, "select c_bpartner_id from ad_user " + "where email like ?", email);
		if (c_bpartner_id < 0) {
			log.severe("BP with email : " + email + " does not exist on iDempiere");
			c_bpartner_id = (Integer) wcDefaults.get_Value("C_BPartner_ID");
		}
		return c_bpartner_id;
	}

	int createBP(Map<?, ?> orderWc) {
		Map<?, ?> billing = (Map<?, ?>) orderWc.get("billing");
		String name = (String) billing.get("first_name");
		String name2 = (String) billing.get("last_name");
		String phone = (String) billing.get("phone");
		String email = getWcCustomerEmail(orderWc);
		MBPartner businessPartner = new MBPartner(ctx, -1, trxName);
		businessPartner.setAD_Org_ID(0);
		businessPartner.setName(name);
		businessPartner.setName2(name2);
		businessPartner.setIsCustomer(true);
		businessPartner.setIsProspect(false);
		businessPartner.setIsVendor(false);
		businessPartner.saveEx();
		int C_Location_ID = createLocation(orderWc);
		int C_BPartner_Location_ID = createBPLocation(businessPartner.getC_BPartner_ID(), C_Location_ID);
		createUser(businessPartner, email, phone, C_BPartner_Location_ID);

		return businessPartner.get_ID();

	}

	private void createUser(MBPartner businessPartner, String email, String phone, int C_BPartner_Location_ID) {
		MUser user = new MUser(ctx, 0, trxName);
		user.setAD_Org_ID(0);
		user.setC_BPartner_ID(businessPartner.getC_BPartner_ID());
		user.setC_BPartner_Location_ID(C_BPartner_Location_ID);
		user.setName(businessPartner.getName());
		user.setEMail(email);
		user.setPhone(phone);
		user.saveEx();
	}

	private int createLocation(Map<?, ?> orderWc) {
		Map<?, ?> billing = (Map<?, ?>) orderWc.get("billing");
		String countryCode = (String) billing.get("country");
		int c_country_id;
		if (isBlankOrNull(countryCode))
			c_country_id = (int) wcDefaults.get_Value("c_country_id");
		else
			c_country_id = DB.getSQLValue(trxName, "select c_country_id " + "from c_country " + "where countrycode = ?",
					countryCode);
		String address1 = (String) billing.get("address_1");
		if (isBlankOrNull(address1))
			address1 = (String) wcDefaults.get_Value("c_country_id");
		String address2 = (String) billing.get("address_2");
		String city = (String) billing.get("city");
		if (isBlankOrNull(city))
			city = (String) wcDefaults.get_Value("city");
		String postal = (String) billing.get("postcode");
		MLocation location = new MLocation(ctx, c_country_id, 0, city, trxName);
		location.setAD_Org_ID(0);
		location.setAddress1(address1);
		location.setAddress2(address2);
		location.setPostal(postal);
		location.saveEx();
		return location.get_ID();
	}

	private int createBPLocation(int C_BPartner_ID, int C_Location_ID) {
		MBPartnerLocation BPartnerLocation = new MBPartnerLocation(ctx, 0, trxName);
		BPartnerLocation.setAD_Org_ID(0);
		BPartnerLocation.setC_BPartner_ID(C_BPartner_ID);
		BPartnerLocation.setC_Location_ID(C_Location_ID);
		BPartnerLocation.setIsBillTo(true);
		BPartnerLocation.setIsShipTo(true);
		BPartnerLocation.saveEx();
		return BPartnerLocation.getC_BPartner_Location_ID();
	}

	public int getBPLocationId(int bp_Id) {
		int c_bpartner_location_id = DB.getSQLValue(trxName,
				"select c_bpartner_location_id " + "from C_BPartner_Location " + "where c_bpartner_id = ?", bp_Id);
		if (c_bpartner_location_id < 0) {
			log.severe("BP with id : " + bp_Id + " does not have a C_BPartner_Location on iDempiere");
			int c_bpartner_id = (int) wcDefaults.get_Value("c_bpartner_id");
			c_bpartner_location_id = DB.getSQLValue(trxName,
					"select c_bpartner_location_id " + "from C_BPartner_Location " + "where c_bpartner_id = ?",
					c_bpartner_id);
		}
		return c_bpartner_location_id;
	}

	public void completeOrder() {
		order.setDateOrdered(new Timestamp(System.currentTimeMillis()));
		order.setDateAcct(new Timestamp(System.currentTimeMillis()));
		order.setDocAction(DocAction.ACTION_Complete);
		if (order.processIt(DocAction.ACTION_Complete)) {
			if (log.isLoggable(Level.FINE))
				log.fine("Order: " + order.getDocumentNo() + " completed fine");
		} else
			throw new IllegalStateException("Order: " + order.getDocumentNo() + " Did not complete");

		order.saveEx();
	}

	public void createOrderLine(Map<?, ?> line, Map<?, ?> orderWc) {
		MathContext mc = new MathContext(0);
		MOrderLine orderLine = new MOrderLine(order);
		orderLine.setAD_Org_ID(order.getAD_Org_ID());
		if(isBundle((String)line.get("name"))) {
			return;
			}
		else
		orderLine.setM_Product_ID(getProductId(line.get("name").toString()));
		String bundleName = getBundleName((int)line.get("id"));
		if(bundleName.length()>0)
			orderLine.set_ValueOfColumn("BundleID", bundleName);
//		if(bundleName.length()>0)
//		orderLine.setDescription("Sold as a part of Bundle ["+bundleName+"]");
		orderLine.setM_Warehouse_ID(order.getM_Warehouse_ID());
		orderLine.setC_Tax_ID(getTaxRate(orderWc));
		long qty = ((Number) line.get("quantity")).longValue();
		orderLine.setQty(BigDecimal.valueOf((long) qty));
//		orderLine.setPrice(calcOrderLineUnitPrice(line));
		if(order.get_ValueAsString("OrderKind").trim().equalsIgnoreCase("pr")) {
		orderLine.setPrice(Env.ZERO);
		orderLine.setPriceList(Env.ZERO);
		}
		else
		{
//		orderLine.setPrice(getUnitPrice(line));
//		orderLine.setPriceList(getListPrice(line));
			setLinePricing(orderLine);
			BigDecimal linePrice = new BigDecimal(line.get("total").toString());
			if(linePrice.compareTo(Env.ZERO)>0 && linePrice.compareTo(orderLine.getPriceEntered().multiply(orderLine.getQtyEntered(),mc))<0) {
				linePrice = linePrice.divide(BigDecimal.valueOf((long) qty),mc);
				orderLine.setPriceEntered(linePrice);	
				orderLine.setPriceActual(linePrice);
				orderLine.setDiscount();
				}
		}
		System.out.println("*********************Unit Price: " + orderLine.getPriceActual());

		if (!orderLine.save()) {
			throw new IllegalStateException("Could not create Order Line");
		}
		
	}

	public int getProductId(String name) {
		int m_Product_ID = DB.getSQLValue(trxName, "select m_product_id " + "from m_product mp " + "where lower(name) = lower(?)",
				name.trim());
		if (m_Product_ID < 0) {
			log.severe("Product : " + name + " does not exist on iDempiere");
			m_Product_ID = (int) wcDefaults.get_Value("m_product_id");
		}
		return m_Product_ID;
	}



	public int getTaxRate(Map<?, ?> orderWc) {
		return  (int) wcDefaults.get_Value("standard_tax_id");
	}

	public BigDecimal getShippingCost(Map<?, ?> orderWc) {
		List<?> shippingLines = (List<?>) orderWc.get("shipping_lines");
		Map<?, ?> shippingLine = (Map<?, ?>) shippingLines.get(0);
		Double total = Double.parseDouble((String) shippingLine.get("total"));
		Double totalTax = Double.parseDouble((String) shippingLine.get("total_tax"));
		BigDecimal shippingCost = isTaxInclusive ? BigDecimal.valueOf((Double) total + totalTax)
				: BigDecimal.valueOf((Double) total);
		return (shippingCost.setScale(4, RoundingMode.HALF_EVEN));
	}

	public BigDecimal calcOrderLineUnitPrice(Map<?, ?> line) {
		Double price = ((Number) line.get("price")).doubleValue();
		return new BigDecimal(price);

	}
	public BigDecimal getUnitPrice(Map<?, ?> line) {
		Double price = ((Number) Double.parseDouble(line.get("total").toString())).doubleValue();
		Double qty = ((Number) line.get("quantity")).doubleValue();
		qty=qty==0?1:qty;
		return new BigDecimal(price/qty);

	}
	public BigDecimal getListPrice(Map<?, ?> line) {
		Double price = ((Number) Double.parseDouble(line.get("subtotal").toString())).doubleValue();
		Double qty = ((Number) line.get("quantity")).doubleValue();
		qty=qty==0?1:qty;
		return new BigDecimal(price/qty);

	}
	

//	public void createPosPayment(Map<?, ?> orderWc) {
//		X_C_POSPayment posPayment = new X_C_POSPayment(ctx, null, trxName);
//		posPayment.setC_Order_ID(order.getC_Order_ID());
//		posPayment.setAD_Org_ID(order.getAD_Org_ID());
//		posPayment.setPayAmt(new BigDecimal(orderWc.get("total").toString()));
//		posPayment.setC_POSTenderType_ID(POSTENDERTYPE_ID); // credit card
//		posPayment.setTenderType(X_C_Payment.TENDERTYPE_CreditCard); // credit card
//		if (!posPayment.save())
//			throw new IllegalStateException("Could not create POSPayment");
//	}

	public static boolean isBlankOrNull(String str) {
		return (str == null || "".equals(str.trim()));
	}
	
	public void filterbundles(List<?> lines){
		for (int j = 0; j < lines.size(); j++) {
			Map<?, ?> line = (Map<?, ?>) lines.get(j);
			if(((List<?>)line.get("bundled_items")).size()>0) {
				
				List<Integer> bundled_items = (List<Integer>)line.get("bundled_items");
				String bundleName = (String)line.get("name");
				String bundleID = String.valueOf((Integer)line.get("id"));
				BigDecimal total = new BigDecimal((String)line.get("total"));
				BigDecimal subtotal = new BigDecimal((String)line.get("subtotal"));
				Bundle bundle =new Bundle(bundleID,bundleName,bundled_items,subtotal,total);
				bdls.add(bundle);
			}
		}		
	}

	public String getBundleName(int prodID){
		for(Bundle bundle:bdls) {
			for(int id:bundle.getBundleItems()) {
				if(id==prodID)
					return bundle.getId();
				}
		}
		return "";
	}
	

	
	public boolean isBundle(String prodName) {
		for(Bundle bundle:bdls) {
			if(bundle.getName().equalsIgnoreCase(prodName))
				return true;
		}
		
		return  false;
	}
	
	private Timestamp getDateOrdered(String dateCompleted) {
		Timestamp dateOrdered;
		SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
		Date parsedDate;
		try {
			parsedDate = dateFormat.parse(dateCompleted);
			dateOrdered = new Timestamp(parsedDate.getTime());
		} catch (ParseException e) {
			// TODO Auto-generated catch block
			dateOrdered = new Timestamp(System.currentTimeMillis());
			
		}
		return dateOrdered;
		
		
	}
	public void addShippingCharges(Map<?, ?> orderWc) {
		MOrderLine orderLine = new MOrderLine(order);
		orderLine.setAD_Org_ID(order.getAD_Org_ID());
		
		orderLine.setC_Charge_ID(wcDefaults.get_ValueAsInt("C_Charge_ID"));
		orderLine.setDescription("Shipping Charges");
		
		orderLine.setM_Warehouse_ID(order.getM_Warehouse_ID());
		orderLine.setC_Tax_ID(getTaxRate(orderWc));
		
		orderLine.setQty(Env.ONE);
		
		orderLine.setPrice(new BigDecimal((String)orderWc.get("shipping_total")));
		System.out.println("*********************Unit Price: " + orderLine.getPriceActual());

		if (!orderLine.save()) {
			throw new IllegalStateException("Could not create Order Line");
		}
	}

	
	private int getCBPartner(Map<?, ?> orderWc,MOrder order) 
	{
		
		
		
		List<MCourierCompany> companies = new Query(ctx, MCourierCompany.Table_Name, " isactive = 'Y' ", null).setOrderBy(" lineno ").list();
		
		for(MetaDataObject obj :metadata) {
			if(obj.getKey().equalsIgnoreCase("_selected_booked_with")) {
				for(MCourierCompany company:companies) {
//					if(company.getValue().trim().equalsIgnoreCase(obj.getKey()))
					if(company.getValue().trim().equalsIgnoreCase(obj.getValue()))
						if(company.getC_BPartner_ID()>0) {
//							courierCompany = obj.getKey();
							courierCompany = company.getTrackingMetaKey();
							return company.getC_BPartner_ID();
							
						}
				}
			}
			
		}
		
		

		return (Integer) wcDefaults.get_Value("C_BPartner_ID");
	}
	private void setPaymentMethod(MOrder order) 
	{
		for(MetaDataObject obj :metadata) {
			if(obj.getKey().equalsIgnoreCase("_selected_payment_method"))
				order.set_ValueOfColumn("PaymentMethod", obj.getValue());
		}
	}
	
	
	private String getCN(Map<?, ?> orderWc) 
	{
		
		
		String CN ="";
		for(MetaDataObject obj :metadata) {
			if(obj.getKey().equalsIgnoreCase(courierCompany))
				CN= obj.getValue();
			if(obj.getKey().equalsIgnoreCase("_selected_payment_method")&&  Arrays.asList("cod", "pr").contains(obj.getValue())  )
				IS_COD = true;
		}
		
		
		return CN;
	}
	private String getOrderReferer(Map<?, ?> orderWc) 
	{
		
		
		String OR ="";
		for(MetaDataObject obj :metadata) {
			if(obj.getKey().equalsIgnoreCase("_wc_order_attribution_utm_source"))
				OR= obj.getValue();
		}
		return OR;
	}

	
	private void inflateMetaData(Map<?, ?> orderWc) {
		
		for(Object item:(ArrayList)orderWc.get("meta_data")) {
				if(((Map<?,?>)item).get("value").toString().length()>0) {
				String id = ((Map<?,?>)item).get("id").toString();
				String key = ((Map<?,?>)item).get("key").toString();
				String value = ((Map<?,?>)item).get("value").toString();
				MetaDataObject object = new MetaDataObject(id,key,value);
				metadata.add(object);
			}
		}
		
		
	}
	
void setLinePricing(MOrderLine oline) {
		
		MOrder order = (MOrder)oline.getC_Order();
		MBPartner customer = (MBPartner) order.getC_BPartner();
		MProduct product = (MProduct) oline.getProduct();
		
		IProductPricing pp = Core.getProductPricing();
		pp.setInitialValues(product.getM_Product_ID(), customer.getC_BPartner_ID(), Env.ONE,true, null);
		Timestamp orderDate = (Timestamp)order.getDateOrdered();
		pp.setPriceDate(orderDate);
		pp.setOrderLine(oline, null);
		int M_PriceList_ID = order.getM_PriceList_ID();
		pp.setM_PriceList_ID(M_PriceList_ID);
		String sql = "SELECT plv.M_PriceList_Version_ID "
				+ "FROM M_PriceList_Version plv "
				+ "WHERE plv.M_PriceList_ID=? "						//	1
				+ " AND plv.ValidFrom <= ? "
				+ "ORDER BY plv.ValidFrom DESC";
			//	Use newest price list - may not be future

		int M_PriceList_Version_ID = DB.getSQLValueEx(null, sql, M_PriceList_ID, orderDate);
		pp.setM_PriceList_Version_ID(M_PriceList_Version_ID);
		oline.setPriceList(pp.getPriceList());
		oline.setPriceLimit(pp.getPriceLimit());
		oline.setPriceActual(pp.getPriceStd());
		oline.setPriceEntered(pp.getPriceStd());
		oline.setC_Currency_ID(Integer.valueOf(pp.getC_Currency_ID()));
		oline.setDiscount(pp.getDiscount());
		oline.setC_UOM_ID(Integer.valueOf(pp.getC_UOM_ID()));
		
	}

	public void applyDiscount() {
		MathContext mc = new MathContext(5);
		for(Bundle bundle: bdls) {
			List<MOrderLine> list = new Query(Env.getCtx(), MOrderLine.Table_Name, " C_Order_ID = ? and bundleid = ? ", order.get_TrxName())
					.setParameters(order.getC_Order_ID(),bundle.getId()).setOrderBy(" created")
					.list();
			BigDecimal linesTotal = Env.ZERO;
			BigDecimal prodCount=Env.ZERO;
			for(MOrderLine oline:list) {
				prodCount = prodCount.add(Env.ONE);
				linesTotal=linesTotal.add(oline.getPriceEntered());
			}
			BigDecimal bundlePrice = bundle.getTotal();
			if(linesTotal.compareTo(Env.ZERO)==0 || bundlePrice.compareTo(Env.ZERO)==0 || linesTotal.compareTo(bundlePrice)<=0)
				return;
			BigDecimal totalDiscount = linesTotal.subtract(bundlePrice).setScale(0, BigDecimal.ROUND_DOWN);
			BigDecimal runningSum = Env.ZERO;
			for(MOrderLine oline:list) {
				BigDecimal discount = Env.ZERO;
				BigDecimal proportionalDisc = oline.getPriceEntered().divide(linesTotal,mc).multiply(totalDiscount, mc);
				runningSum = runningSum.add(proportionalDisc);
				if(totalDiscount.subtract(runningSum).compareTo(Env.ONE)<=0) {
					discount = totalDiscount.subtract(runningSum.subtract(proportionalDisc));
				}
				else {
					discount = proportionalDisc;
				}
				  oline.setPriceEntered(oline.getPriceEntered().subtract(discount));
				  oline.setPriceActual(oline.getPriceEntered());
				  oline.setDiscount();
				  oline.save();
			}
			
			
		}
	}

	String getcouponCodes(Map<?,?> wcOrder) {
		MReference reference = new MReference(ctx, "1bc246ff-9c1f-4390-b4a7-f248dda300bb", trxName);
		List<String> existing = new ArrayList<String>();
		String sql ="select value,name from ad_ref_list where ad_reference_id = ?";
		ValueNamePair[] pairs = DB.getValueNamePairs(sql, true, Arrays.asList(reference.get_ID()));
		for(ValueNamePair code:pairs) {
			existing.add(code.getID());
		}
		List<String> codes = new ArrayList<String>();
		if(wcOrder.get("coupon_lines")!=null) 
		{
			List<?> couponLines = (List<?>)wcOrder.get("coupon_lines");
			for(int i=0;i<couponLines.size();i++) {
				Map<?,?> line = (Map<?,?>)couponLines.get(i);
				String ccode = (String)line.get("code");
				if(existing.contains(ccode))
					codes.add(ccode);
				else 
				{
					MRefList coupon = new MRefList(ctx, 0, trxName);
					coupon.setAD_Reference_ID(reference.get_ID());
					coupon.setValue(ccode);
					coupon.setName(ccode);
					coupon.setAD_Org_ID(0);
					
					try {
						coupon.saveEx();
						codes.add(ccode);
					} catch (Exception e) {
						// TODO: handle exception
					};
				}
			}
		}
		return String.join(",", codes);
		
	}
}