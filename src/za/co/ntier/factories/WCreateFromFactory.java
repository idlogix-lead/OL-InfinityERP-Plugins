package za.co.ntier.factories;

import org.compiere.grid.ICreateFrom;
import org.compiere.grid.ICreateFromFactory;
import org.compiere.model.GridTab;
import org.compiere.model.MBankStatement;

import za.co.ntier.forms.WCreateFromStatementUIOL;

public class WCreateFromFactory implements ICreateFromFactory{

	@Override
	public ICreateFrom create(GridTab mTab) {
		// TODO Auto-generated method stub
		
		if(mTab.getTableName().equals(MBankStatement.Table_Name)) {
			return new WCreateFromStatementUIOL(mTab);
		}
		return null;
	}

}
