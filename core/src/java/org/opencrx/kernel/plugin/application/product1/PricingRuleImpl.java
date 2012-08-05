package org.opencrx.kernel.plugin.application.product1;

import org.opencrx.kernel.backend.Backend;
import org.opencrx.kernel.product1.jmi1.GetPriceLevelResult;
import org.openmdx.base.accessor.jmi.cci.RefPackage_1_3;
import org.openmdx.base.accessor.jmi.spi.RefException_1;
import org.openmdx.base.exception.ServiceException;

public class PricingRuleImpl {
    
    //-----------------------------------------------------------------------
    public PricingRuleImpl(
        org.opencrx.kernel.product1.jmi1.PricingRule current,
        org.opencrx.kernel.product1.cci2.PricingRule next
    ) {
        this.current = current;
        this.next = next;
    }

    //-----------------------------------------------------------------------
    public Backend getBackend(
    ) {
        return (Backend)((RefPackage_1_3)this.current.refOutermostPackage()).refUserContext();
    }
    
    //-----------------------------------------------------------------------
    public org.opencrx.kernel.product1.jmi1.GetPriceLevelResult getPriceLevel(
        org.opencrx.kernel.product1.jmi1.GetPriceLevelParams params
    ) throws javax.jmi.reflect.RefException  {
        try {        
            GetPriceLevelResult result = this.getBackend().getProducts().getPriceLevel(
                this.current,
                params.getContract(),
                params.getProduct(),
                params.getPriceUom(),
                params.getQuantity(),
                params.getPricingDate()
            );
            return result;
        }
        catch(ServiceException e) {
            throw new RefException_1(e);
        }             
    }
    
    //-----------------------------------------------------------------------
    // Members
    //-----------------------------------------------------------------------
    protected final org.opencrx.kernel.product1.jmi1.PricingRule current;
    protected final org.opencrx.kernel.product1.cci2.PricingRule next;    

}
