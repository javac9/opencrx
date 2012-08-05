/*
 * ====================================================================
 * Project:     openCRX/Store, http://www.opencrx.org/
 * Name:        $Id: OrderManager.java,v 1.17 2008/02/12 19:57:20 wfro Exp $
 * Description: ProductManager
 * Revision:    $Revision: 1.17 $
 * Owner:       CRIXP AG, Switzerland, http://www.crixp.com
 * Date:        $Date: 2008/02/12 19:57:20 $
 * ====================================================================
 *
 * This software is published under the BSD license
 * as listed below.
 * 
 * Copyright (c) 2007, CRIXP Corp., Switzerland
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without 
 * modification, are permitted provided that the following conditions 
 * are met:
 * 
 * * Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 * 
 * * Redistributions in binary form must reproduce the above copyright
 * notice, this list of conditions and the following disclaimer in
 * the documentation and/or other materials provided with the
 * distribution.
 * 
 * * Neither the name of CRIXP Corp. nor the names of the contributors
 * to openCRX may be used to endorse or promote products derived
 * from this software without specific prior written permission
 * 
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
 * CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED
 * TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY
 * OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * 
 * ------------------
 * 
 * This product includes software developed by the Apache Software
 * Foundation (http://www.apache.org/).
 * 
 * This product includes software developed by contributors to
 * openMDX (http://www.openmdx.org/)
 */
package org.opencrx.store.manager;

import java.util.Collection;
import java.util.Date;
import java.util.Iterator;

import javax.jdo.Transaction;

import org.opencrx.kernel.contract1.cci2.SalesOrderQuery;
import org.opencrx.store.common.ObjectCollection;
import org.opencrx.store.common.PrimaryKey;
import org.opencrx.store.common.util.OpenCrxContext;
import org.opencrx.store.objects.Order;
import org.opencrx.store.objects.User;

/**
 * Manager for Order subsystem
 * 
 * @author OAZM (initial implementation)
 * @author WFRO (port to openCRX)
 */
public final class OrderManager
{
    //-----------------------------------------------------------------------
    public OrderManager(
        OpenCrxContext context
    ) {
        this.context = context;
    }
    
    //-----------------------------------------------------------------------
    public final Order get(
       final PrimaryKey orderID
    ) {
        if(orderID.toString().length() > 0) {
            System.out.println("orderId=" + orderID.getUuid());
            org.opencrx.kernel.contract1.jmi1.SalesOrder salesOrder = 
                this.context.getContractSegment().getSalesOrder(orderID.getUuid());     
            System.out.println("sales order=" + salesOrder);
            return new Order(salesOrder);
        }
        else { 
            return null;
        }
    }

    //-----------------------------------------------------------------------
    public final Order startNew(
        final PrimaryKey userID
    ) {
        final Order order = new Order();

        order.setUserID(userID);
        order.setStatus(Order.STATUS_BUYING);
        order.setStartDate( new Date(System.currentTimeMillis()));
        order.setEndDate( new Date(System.currentTimeMillis()));

        // Get user's default address
        final UserManager userManager = new UserManager(this.context);
        final User user = userManager.get( userID );
        order.setAddress( user.getAddress() );

        Transaction tx = this.context.getPersistenceManager().currentTransaction();       
        tx.begin();
        org.opencrx.kernel.contract1.jmi1.SalesOrder salesOrder = 
            this.context.getContractPackage().getSalesOrder().createSalesOrder();
        salesOrder.refInitialize(false, true);
        order.update(
            salesOrder,
            this.context
        );
        this.context.getContractSegment().addSalesOrder(
            false,
            order.getKey().getUuid(),
            salesOrder
        );
        tx.commit();
        return this.get(order.getKey());
    }

    //-----------------------------------------------------------------------
    public final void update(
        final Order newValue
    ) {
        Transaction tx = this.context.getPersistenceManager().currentTransaction();       
        tx.begin();
        org.opencrx.kernel.contract1.jmi1.SalesOrder salesOrder = 
            this.context.getContractSegment().getSalesOrder(newValue.getKey().getUuid());
        newValue.update(
            salesOrder,
            this.context
        );
        tx.commit();
    }

    //-----------------------------------------------------------------------
    public final Order getLastOrder(
        final PrimaryKey userID
    ) {
        org.opencrx.kernel.account1.jmi1.Account customer =
            this.context.getAccountSegment().getAccount(userID.getUuid());
        SalesOrderQuery query = this.context.getContractPackage().createSalesOrderQuery();
        query.thereExistsCustomer().equalTo(customer);
        query.contractState().equalTo(new Short((short)430)/* on hold */);        
        query.orderByCreatedAt().descending();
        Collection salesOrders = this.context.getContractSegment().getSalesOrder(query);
        if(!salesOrders.isEmpty()) {
            org.opencrx.kernel.contract1.jmi1.SalesOrder salesOrder = 
                (org.opencrx.kernel.contract1.jmi1.SalesOrder)salesOrders.iterator().next();
            return new Order(salesOrder);
        }
        else {
            return null;
        }
    }

    //-----------------------------------------------------------------------
    public final Order checkout(
        final PrimaryKey orderID, 
        final String shippingAddress
    ) {
        final Order order = this.get( orderID );
        order.setAddress( shippingAddress );
        order.setStatus( Order.STATUS_PENDING );
        this.update( order );

        return this.get( orderID );
    }

    //-----------------------------------------------------------------------
    public final void discardOrder(
        final PrimaryKey orderID
    ) {
        final Order order = this.get( orderID );
        order.setStatus( Order.STATUS_CANCELLED );
        this.update( order );
    }

    //-----------------------------------------------------------------------
    public final void deliverOrder(
        final PrimaryKey orderID
    ) {
        final Order order = this.get( orderID );
        order.setStatus( Order.STATUS_DELIVERED );
        this.update( order );
    }

    //-----------------------------------------------------------------------
    public final ObjectCollection getPendingOrders( 
    ) {
        SalesOrderQuery query = this.context.getContractPackage().createSalesOrderQuery();
        query.contractState().equalTo(new Short((short)430)/* on hold */);
        query.name().like(OpenCrxContext.STORE_SCHEMA + ".*");
        ObjectCollection salesOrders = new ObjectCollection();
        for(
            Iterator i = this.context.getContractSegment().getSalesOrder(query).iterator();
            i.hasNext();
        ) {
            org.opencrx.kernel.contract1.jmi1.SalesOrder salesOrder = (org.opencrx.kernel.contract1.jmi1.SalesOrder)i.next();
            Order order = new Order(salesOrder);
            salesOrders.put(
                order.getKey().toString(),
                order
            );            
        }
        return salesOrders;
    }

    //-----------------------------------------------------------------------
    // Members
    //-----------------------------------------------------------------------
    private final OpenCrxContext context;
    
}
