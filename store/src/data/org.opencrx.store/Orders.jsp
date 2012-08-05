<%@ page import="org.opencrx.store.common.util.RequestHelper,
                 org.opencrx.store.common.util.ResponseHelper,
                 org.opencrx.store.manager.OrderManager,
                 org.opencrx.store.common.ObjectCollection,
                 java.util.Iterator,
                 org.opencrx.store.objects.Order,
                 org.opencrx.store.manager.UserManager,
                 org.opencrx.store.objects.User,
                 org.opencrx.store.common.util.Converter,
                 org.opencrx.store.common.util.SessionHelper"%>
 <%--
  Show pending orders

  User: Omar Al Zabir
  Date: Nov 6, 2004
  Time: 2:40:00 PM
--%>
<%@ page contentType="text/html;charset=UTF-8" language="java" %>

<%
    final RequestHelper requestHelper = new RequestHelper( request );
    final ResponseHelper responseHelper = new ResponseHelper( response );
    final SessionHelper sessionHelper = new SessionHelper( request );

    if( sessionHelper.isAdmin() )
    {

%>
<h2 class="sidebar-title">Pending Orders</h2>
<div class="sidebar-block">
<%
        // 1. Get pending orders
        final OrderManager orderManager = new OrderManager(sessionHelper.getOpenCrxContext());
        final ObjectCollection pendingOrders = orderManager.getPendingOrders();

        final UserManager userManager = new UserManager(sessionHelper.getOpenCrxContext());

        // 2. Render pending orders where each order is a link for details

        final Iterator iterator = pendingOrders.objects().iterator();
        while( iterator.hasNext() )
        {
            Order order = (Order) iterator.next();

            // 3. Get the buyer
            User buyer = userManager.get( order.getUserID() );

            // 4. Show buyer name as hyper link and link to details page
            String title = buyer.getName() + " " + Converter.getString( order.getEndDate() );
            out.println("<li>");
            out.println( responseHelper.makeHyperLink( responseHelper.orderDetailsUrl( order.getKey() ), title ) );
            out.println("</li>");
        }
%>
</div>
<%
    }
%>
