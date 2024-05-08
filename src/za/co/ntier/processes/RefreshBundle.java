package za.co.ntier.processes;

import org.compiere.process.SvrProcess;
import org.osgi.framework.Bundle;
import org.osgi.framework.FrameworkUtil;

public class RefreshBundle extends SvrProcess {

	@Override
	protected void prepare() {
		// TODO Auto-generated method stub
		
	}

	@Override
	protected String doIt() throws Exception {
		// TODO Auto-generated method stub
		Bundle bundle  = FrameworkUtil.getBundle(getClass());
		String name = bundle.getSymbolicName();
		try {
            // Refresh the bundle
            bundle.update();
        } catch (Exception e) {
            // Handle update failure
            e.printStackTrace();
        }
		return null;
	}

}
