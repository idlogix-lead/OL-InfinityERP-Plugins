package za.co.ntier.validators;

import org.compiere.model.MClient;
import org.compiere.model.MInvoice;
import org.compiere.model.MOrder;
import org.compiere.model.MPayment;
import org.compiere.model.ModelValidationEngine;
import org.compiere.model.ModelValidator;
import org.compiere.model.PO;
import org.compiere.util.DB;

public class PaymentValidator implements ModelValidator {

	@Override
	public void initialize(ModelValidationEngine engine, MClient client) {
		// TODO Auto-generated method stub
		
		engine.addDocValidate(MPayment.Table_Name, this);
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

		return null;
	}

	@Override
	public String docValidate(PO po, int timing) {
		// TODO Auto-generated method stub 
	
		if(po.get_Table_ID()==MPayment.Table_ID && timing==TIMING_BEFORE_COMPLETE) {
			int bankAccID = 0; 
			int docTypeID = 0;
			int invoiceID = po.get_ValueAsInt("C_Invoice_ID");
			if(invoiceID>0) {  
				MInvoice inv = new MInvoice(po.getCtx(), invoiceID, po.get_TrxName());
				 docTypeID = inv.getC_DocType_ID();
				 bankAccID = inv.get_ValueAsInt("C_BankAccount_ID");
				if(bankAccID>0 && docTypeID == 1000050) {
					po.set_ValueOfColumn("C_BankAccount_ID",bankAccID);
					return null;
				}					
					bankAccID = 0;
					MOrder order = new MOrder(po.getCtx(), inv.getC_Order_ID(), null);
					
					String paymentMethod = order.get_ValueAsString("PaymentMethod");
					String sql = "SELECT C_BankAccount_ID FROM C_BankAccount ba "
							+ " WHERE PaymentMethod = ? Order by created desc limit 1 ";
					bankAccID = DB.getSQLValue(po.get_TrxName(),sql,paymentMethod);
					if(bankAccID>0)
						po.set_ValueOfColumn("C_BankAccount_ID", bankAccID);
			}
			
			
		}
		return null;
	}

}
